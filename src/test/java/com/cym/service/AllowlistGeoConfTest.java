package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.ext.ConfExt;
import com.cym.ext.ConfFile;
import com.cym.model.DenyAllow;
import com.cym.model.GeoRule;
import com.cym.model.Location;
import com.cym.model.Param;
import com.cym.model.Server;
import com.cym.testsupport.InMemorySqlHelper;

import cn.hutool.core.util.ReflectUtil;

/** 白名單 geo 與國家 map 拆成 include 檔，國家封鎖改看合併變數 $geo_deny_*。 */
class AllowlistGeoConfTest {
	private InMemorySqlHelper sql;
	private ConfService conf;
	private final List<Server> httpServers = new ArrayList<>();

	@BeforeEach
	void setUp() {
		httpServers.clear();
		sql = new InMemorySqlHelper();
		conf = new ConfService();
		ReflectUtil.setFieldValue(conf, "sqlHelper", sql);
		UpstreamService upstreams = new UpstreamService();
		ReflectUtil.setFieldValue(upstreams, "sqlHelper", sql);
		ReflectUtil.setFieldValue(conf, "upstreamService", upstreams);
		ReflectUtil.setFieldValue(conf, "settingService", new SettingService() {
			@Override public String get(String key) { return null; }
		});
		ReflectUtil.setFieldValue(conf, "nginxService", new NginxService() {
			@Override public List<String> getEnabledModulePaths() { return List.of(); }
		});
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
	void listsMoveIntoIncludeFiles() {
		addList("allow", "198.51.100.23");
		addGlobalGeo(1, "CN");

		ConfExt ext = conf.buildConf(true, false);
		String main = ext.getConf();

		assertTrue(main.contains("include conf.d/allowlist_http.conf;"), main);
		assertTrue(main.contains("include conf.d/geo_http.conf;"), main);
		assertFalse(main.contains("198.51.100.23"), main);
		assertFalse(main.contains("CN 1"), main);
		assertTrue(file(ext, "allowlist_http.conf").contains("198.51.100.23 1;"));
		String geo = file(ext, "geo_http.conf");
		assertTrue(geo.contains("map \"$nwu_allowlist:$geo_block_global\" $geo_deny_global"), geo);
		assertTrue(geo.contains("\"0:1\" 1;"), geo);
	}

	@Test
	void serverChecksCombinedVariable() {
		addGlobalGeo(1, "CN");

		String output = conf.buildConf(false, false).getConf();

		assertTrue(output.contains("if ($geo_deny_global = 1)"), output);
		assertFalse(output.contains("if ($geo_block_global = 1)"), output);
	}

	@Test
	void allowModeKeepsDefaults() {
		addGlobalGeo(0, "TW,JP");

		String geo = file(conf.buildConf(true, false), "geo_http.conf");

		assertTrue(geo.contains("default 1;"), geo);
		assertTrue(geo.contains("TW 0;"), geo);
		assertTrue(geo.contains("JP 0;"), geo);
	}

	@Test
	void emptyAllowlistStillDefinesVariable() {
		ConfExt ext = conf.buildConf(true, false);

		List<String> lines = file(ext, "allowlist_http.conf").lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
		assertEquals(List.of("geo $nwu_allowlist {", "default 0;", "}"), lines);
		assertTrue(ext.getConf().contains("include conf.d/allowlist_http.conf;"), ext.getConf());
	}

	@Test
	void noGeoRuleMeansNoGeoInclude() {
		ConfExt ext = conf.buildConf(true, false);

		assertFalse(ext.getConf().contains("geo_http.conf"), ext.getConf());
		assertTrue(ext.getFileList().stream().noneMatch(f -> f.getName().equals("geo_http.conf")));
	}

	@Test
	void allowlistSupportsCidrAndIpv6() {
		addList("allow", "203.0.113.0/24\n2001:db8::/32\n999.1.1.1");

		String allowlist = file(conf.buildConf(true, false), "allowlist_http.conf");

		assertTrue(allowlist.contains("203.0.113.0/24 1;"), allowlist);
		assertTrue(allowlist.contains("2001:db8::/32 1;"), allowlist);
		assertFalse(allowlist.contains("999.1.1.1"), allowlist);
	}

	@Test
	void denyIncludeUnchanged() {
		addList("allow", "198.51.100.23");
		addList("deny", "192.0.2.1");

		String deny = file(conf.buildConf(true, false), "deny_http.conf");

		assertEquals("allow 198.51.100.23;\ndeny 192.0.2.1;", deny);
	}

	private void addList(String type, String ips) {
		DenyAllow da = new DenyAllow();
		da.setType(type);
		da.setIp(ips);
		sql.insertOrUpdate(da);
	}

	private void addGlobalGeo(int mode, String countries) {
		GeoRule rule = new GeoRule();
		rule.setMode(mode);
		rule.setCountries(countries);
		rule.setEnable(true);
		sql.insertOrUpdate(rule);
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
