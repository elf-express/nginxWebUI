package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.config.HomeConfig;
import com.cym.ext.ConfExt;
import com.cym.ext.ConfFile;
import com.cym.model.Http;
import com.cym.model.Location;
import com.cym.model.Param;
import com.cym.model.Server;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.utils.CrowdSecCredentials;

import cn.hutool.core.util.ReflectUtil;

/** CrowdSec bouncer 設定檔與 http include 只在完整設定時才產生。 */
class CrowdSecNginxConfTest {
	private static final String KEY = "s3cr3t-bouncer-key-123456";

	@TempDir
	Path tmp;

	private InMemorySqlHelper sql;
	private ConfService conf;
	private CrowdSecNginxConf crowdsec;
	private final Map<String, String> env = new HashMap<>();
	private final Map<String, String> settings = new HashMap<>();
	private final Set<String> modules = new HashSet<>(Set.of("ndk_http_module", "ngx_http_lua_module"));
	private final List<Server> httpServers = new ArrayList<>();
	private Path marker;
	private String home;

	@BeforeEach
	void setUp() throws Exception {
		env.clear();
		settings.clear();
		httpServers.clear();
		modules.clear();
		modules.addAll(Set.of("ndk_http_module", "ngx_http_lua_module"));
		env.put("CROWDSEC_BOUNCER_KEY", KEY);
		env.put("CROWDSEC_LAPI_URL", "http://crowdsec:8080/");
		env.put("CROWDSEC_APPSEC_URL", "http://crowdsec:7422");

		home = tmp.resolve("home").toString().replace('\\', '/') + "/";
		Files.createDirectories(tmp.resolve("home"));
		marker = tmp.resolve("nwu_crowdsec.lua");
		Files.writeString(marker, "-- marker");

		HomeConfig homeConfig = new HomeConfig();
		homeConfig.home = home;

		sql = new InMemorySqlHelper();
		SettingService settingService = new SettingService() {
			@Override public String get(String key) { return settings.get(key); }
		};
		NginxService nginxService = new NginxService() {
			@Override public boolean hasModule(String keyword) { return modules.contains(keyword); }
			@Override public List<String> getEnabledModulePaths() { return List.of(); }
		};

		crowdsec = new CrowdSecNginxConf();
		ReflectUtil.setFieldValue(crowdsec, "homeConfig", homeConfig);
		ReflectUtil.setFieldValue(crowdsec, "settingService", settingService);
		ReflectUtil.setFieldValue(crowdsec, "nginxService", nginxService);
		ReflectUtil.setFieldValue(crowdsec, "credentials", new CrowdSecCredentials(env::get, tmp.resolve("no-secrets")));
		ReflectUtil.setFieldValue(crowdsec, "markerPath", marker.toString());

		conf = new ConfService();
		ReflectUtil.setFieldValue(conf, "sqlHelper", sql);
		UpstreamService upstreams = new UpstreamService();
		ReflectUtil.setFieldValue(upstreams, "sqlHelper", sql);
		ReflectUtil.setFieldValue(conf, "upstreamService", upstreams);
		ReflectUtil.setFieldValue(conf, "settingService", settingService);
		ReflectUtil.setFieldValue(conf, "nginxService", nginxService);
		ReflectUtil.setFieldValue(conf, "homeConfig", homeConfig);
		ReflectUtil.setFieldValue(conf, "crowdSecNginxConf", crowdsec);
		ReflectUtil.setFieldValue(conf, "serverService", new ServerService() {
			@Override public List<Server> getListByProxyType(String[] types) {
				return httpServers.stream().filter(server -> List.of(types).contains(String.valueOf(server.getProxyType()))).toList();
			}
			@Override public List<Location> getLocationByServerId(String serverId) { return List.of(); }
		});
		ReflectUtil.setFieldValue(conf, "paramService", new ParamService() {
			@Override public List<Param> getListByTypeId(String id, String type) { return List.of(); }
		});
		TemplateService templates = new TemplateService();
		ReflectUtil.setFieldValue(templates, "sqlHelper", sql);
		ReflectUtil.setFieldValue(conf, "templateService", templates);
		DenyAllowService denyAllow = new DenyAllowService();
		ReflectUtil.setFieldValue(denyAllow, "sqlHelper", sql);
		ReflectUtil.setFieldValue(conf, "denyAllowService", denyAllow);

		Server server = new Server();
		server.setProxyType(0);
		server.setListen("80");
		server.setServerName("example.test");
		server.setEnable(true);
		server.setIpv6(0);
		server.setSsl(0);
		server.setDef(0);
		server.setProxyProtocol(0);
		sql.insertOrUpdate(server);
		httpServers.add(server);
	}

	@Test
	void inactiveWithoutKey() {
		env.remove("CROWDSEC_BOUNCER_KEY");
		assertInactive();
	}

	@Test
	void inactiveWithoutLapiUrl() {
		env.remove("CROWDSEC_LAPI_URL");
		assertInactive();
	}

	@Test
	void inactiveWithoutLuaModule() {
		modules.remove("ngx_http_lua_module");
		assertInactive();
	}

	@Test
	void inactiveWithoutNdkModule() {
		modules.remove("ndk_http_module");
		assertInactive();
	}

	@Test
	void inactiveWithoutMarker() throws Exception {
		Files.delete(marker);
		assertInactive();
	}

	@Test
	void nullCrowdSecFieldMeansInactive() {
		ReflectUtil.setFieldValue(conf, "crowdSecNginxConf", null);

		ConfExt ext = conf.buildConf(true, false);

		assertFalse(ext.getConf().contains("crowdsec_http.conf"), ext.getConf());
	}

	@Test
	void activeBuildsInclude() {
		assertTrue(crowdsec.isActive());

		ConfExt ext = conf.buildConf(true, false);

		assertTrue(ext.getConf().contains("include conf.d/crowdsec_http.conf;"), ext.getConf());
		String inc = file(ext, "crowdsec_http.conf");
		assertTrue(inc.contains("include /etc/nginx/nwu/crowdsec_lua_paths.conf;"), inc);
		assertTrue(inc.contains("lua_shared_dict crowdsec_cache 50m;"), inc);
		assertTrue(inc.contains("lua_shared_dict nwu_crowdsec 1m;"), inc);
		assertTrue(inc.contains("init_by_lua_block"), inc);
		assertTrue(inc.contains("grace = 120"), inc);
		assertTrue(inc.contains(home + "crowdsec/crowdsec-nginx-bouncer.conf"), inc);
		assertTrue(inc.contains(home + "crowdsec/state/crowdsec_state.json"), inc);
		assertTrue(inc.contains("/usr/local/lib/nginxwebui/html/protection-paused.html"), inc);
		assertTrue(inc.contains("init_worker_by_lua_block"), inc);
		assertTrue(inc.contains("access_by_lua_block"), inc);
		assertFalse(inc.contains(KEY), inc);
		assertFalse(ext.getConf().contains(KEY), ext.getConf());
		assertTrue(Files.exists(Path.of(crowdsec.bouncerConfPath())));
	}

	@Test
	void writeFailureSkipsIncludeAndKeepsBuilding() throws Exception {
		Files.writeString(tmp.resolve("home").resolve("crowdsec"), "not a directory");

		ConfExt ext = conf.buildConf(true, false);

		assertNotNull(ext);
		assertFalse(ext.getConf().contains("crowdsec_http.conf"), ext.getConf());
		assertTrue(ext.getFileList().stream().noneMatch(f -> f.getName().equals("crowdsec_http.conf")));
		assertTrue(ext.getConf().contains("include conf.d/allowlist_http.conf;"), ext.getConf());
	}

	@Test
	void includeComesAfterAllowlistInclude() {
		String main = conf.buildConf(true, false).getConf();

		int allow = main.indexOf("include conf.d/allowlist_http.conf;");
		int cs = main.indexOf("include conf.d/crowdsec_http.conf;");
		assertTrue(allow >= 0 && cs > allow, main);
	}

	@Test
	void bouncerConfContent() {
		List<String> lines = crowdsec.buildBouncerConf().lines().toList();

		assertEquals(List.of(
				"API_URL=http://crowdsec:8080",
				"API_KEY=" + KEY,
				"MODE=stream",
				"UPDATE_FREQUENCY=10",
				"BOUNCING_ON_TYPE=ban",
				"APPSEC_URL=http://crowdsec:7422",
				"APPSEC_FAILURE_ACTION=passthrough",
				"ALWAYS_SEND_TO_APPSEC=false",
				"BAN_TEMPLATE_PATH=/var/lib/crowdsec/lua/templates/ban.html"), lines);
	}

	@Test
	void bouncerConfAppsecEmptyWhenUnset() {
		env.remove("CROWDSEC_APPSEC_URL");

		assertTrue(crowdsec.buildBouncerConf().lines().anyMatch("APPSEC_URL="::equals));
	}

	@Test
	void bouncerConfIsOwnerOnly() throws Exception {
		assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));

		crowdsec.writeBouncerConf();

		Path file = Path.of(crowdsec.bouncerConfPath());
		assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file)));
		assertTrue(Files.readString(file).contains("API_KEY=" + KEY));
	}

	@Test
	void stateDirIsWorldWritableAndSticky() throws Exception {
		assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("unix"));

		crowdsec.writeBouncerConf();

		Path state = Path.of(home + "crowdsec/state");
		assertTrue(Files.isDirectory(state));
		int mode = (Integer) Files.getAttribute(state, "unix:mode");
		assertEquals(01777, mode & 07777);
	}

	@Test
	void writeBouncerConfSkipsWhenUnchanged() throws Exception {
		crowdsec.writeBouncerConf();
		Path file = Path.of(crowdsec.bouncerConfPath());
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L));

		crowdsec.writeBouncerConf();

		assertEquals(1_000_000L, Files.getLastModifiedTime(file).toMillis());
	}

	@Test
	void graceFromSetting() {
		assertEquals(120, graceOf(null));
		assertEquals(45, graceOf("45"));
		assertEquals(120, graceOf("abc"));
		assertEquals(120, graceOf("-1"));
		assertEquals(120, graceOf("0"));
		assertEquals(10, graceOf("5"));
		assertEquals(3600, graceOf("99999"));
	}

	@Test
	void graceIsUsedInInclude() {
		settings.put(CrowdSecNginxConf.GRACE_KEY, "45");

		assertTrue(file(conf.buildConf(true, false), "crowdsec_http.conf").contains("grace = 45"));
	}

	@Test
	void existingResolverIsNotDuplicated() {
		String without = file(conf.buildConf(true, false), "crowdsec_http.conf");
		assertTrue(without.contains("resolver 127.0.0.11 valid=30s ipv6=off;"), without);

		Http http = new Http("resolver", "8.8.8.8", 1L);
		http.setEnable(true);
		sql.insertOrUpdate(http);

		String with = file(conf.buildConf(true, false), "crowdsec_http.conf");
		assertFalse(with.contains("resolver"), with);
	}

	@Test
	void disabledResolverDoesNotCount() {
		Http http = new Http("resolver", "8.8.8.8", 1L);
		http.setEnable(false);
		sql.insertOrUpdate(http);

		assertTrue(file(conf.buildConf(true, false), "crowdsec_http.conf").contains("resolver 127.0.0.11"));
	}

	@Test
	void customResolverIsUsed() {
		env.put("CROWDSEC_RESOLVER", "10.0.0.2");

		assertTrue(file(conf.buildConf(true, false), "crowdsec_http.conf").contains("resolver 10.0.0.2 valid=30s ipv6=off;"));
	}

	@Test
	void httpsUrlAddsTrustedCertificate() {
		String plain = file(conf.buildConf(true, false), "crowdsec_http.conf");
		assertFalse(plain.contains("lua_ssl_trusted_certificate"), plain);

		env.put("CROWDSEC_LAPI_URL", "https://crowdsec.example:8080");
		String https = file(conf.buildConf(true, false), "crowdsec_http.conf");
		assertTrue(https.contains("lua_ssl_trusted_certificate /etc/ssl/certs/ca-certificates.crt;"), https);

		env.put("CROWDSEC_LAPI_URL", "http://crowdsec:8080");
		env.put("CROWDSEC_APPSEC_URL", "https://appsec.example:7422");
		assertTrue(file(conf.buildConf(true, false), "crowdsec_http.conf").contains("lua_ssl_trusted_certificate"));
	}

	@Test
	void luaStringsAreEscaped() {
		HomeConfig weird = new HomeConfig();
		weird.home = "C:\\data\\\"x\"\\";
		ReflectUtil.setFieldValue(crowdsec, "homeConfig", weird);

		String inc = String.join("\n", crowdsec.buildHttpLines(false));

		assertTrue(inc.contains("conf = \"C:\\\\data\\\\\\\"x\\\"\\\\crowdsec/crowdsec-nginx-bouncer.conf\""), inc);
	}

	@Test
	void staleConfRemovedWhenInactive() throws Exception {
		Path file = Path.of(crowdsec.bouncerConfPath());
		Files.createDirectories(file.getParent());
		Files.writeString(file, "API_KEY=old-key");
		env.remove("CROWDSEC_BOUNCER_KEY");

		assertTrue(crowdsec.removeStaleBouncerConf());
		assertFalse(Files.exists(file));
	}

	@Test
	void confKeptWhenActive() throws Exception {
		Path file = Path.of(crowdsec.bouncerConfPath());
		Files.createDirectories(file.getParent());
		Files.writeString(file, "API_KEY=current");

		assertTrue(crowdsec.isActive());
		assertFalse(crowdsec.removeStaleBouncerConf());
		assertEquals("API_KEY=current", Files.readString(file));
	}

	@Test
	void noConfNoop() {
		env.remove("CROWDSEC_BOUNCER_KEY");

		assertFalse(Files.exists(Path.of(crowdsec.bouncerConfPath())));
		assertFalse(crowdsec.removeStaleBouncerConf());
	}

	private void assertInactive() {
		assertFalse(crowdsec.isActive());
		ConfExt ext = conf.buildConf(true, false);
		assertFalse(ext.getConf().contains("crowdsec_http.conf"), ext.getConf());
		assertTrue(ext.getFileList().stream().noneMatch(f -> f.getName().equals("crowdsec_http.conf")));
		assertFalse(Files.exists(Path.of(crowdsec.bouncerConfPath())));
	}

	private int graceOf(String setting) {
		if (setting == null) {
			settings.remove(CrowdSecNginxConf.GRACE_KEY);
		} else {
			settings.put(CrowdSecNginxConf.GRACE_KEY, setting);
		}
		return crowdsec.graceSeconds();
	}

	private static String file(ConfExt ext, String name) {
		for (ConfFile f : ext.getFileList()) {
			if (f.getName().equals(name)) {
				return f.getConf();
			}
		}
		return fail("missing conf file " + name + " in " + ext.getFileList().stream().map(ConfFile::getName).toList());
	}
}
