package com.cym.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.noear.solon.annotation.Inject;

import com.cym.config.HomeConfig;
import com.cym.utils.CrowdSecCredentials;

import cn.hutool.core.util.StrUtil;

/**
 * 產生 CrowdSec nginx Lua bouncer 的設定：bouncer 設定檔（含金鑰，權限 600）與 http 層 include。
 * 只有金鑰、LAPI 位址、nginx Lua 模組與 image 內的包裝檔都具備時才啟用。
 */
@Component
public class CrowdSecNginxConf {
	public static final String GRACE_KEY = "crowdsec.graceSeconds";
	public static final int DEFAULT_GRACE = 120;
	static final int MIN_GRACE = 10;
	static final int MAX_GRACE = 3600;
	private static final Logger logger = LoggerFactory.getLogger(CrowdSecNginxConf.class);

	static final String MARKER_PATH = "/usr/local/lib/nginxwebui/lua/nwu_crowdsec.lua";
	static final String LUA_PATHS_CONF = "/etc/nginx/nwu/crowdsec_lua_paths.conf";
	static final String PAUSED_PAGE = "/usr/local/lib/nginxwebui/html/protection-paused.html";
	static final String CA_BUNDLE = "/etc/ssl/certs/ca-certificates.crt";

	@Inject
	HomeConfig homeConfig;
	@Inject
	SettingService settingService;
	@Inject
	NginxService nginxService;

	CrowdSecCredentials credentials = CrowdSecCredentials.system();
	String markerPath = MARKER_PATH;

	/** 金鑰存在、LAPI 位址有填、Lua 相關 nginx 模組與包裝檔都在 */
	public boolean isActive() {
		return credentials.bouncerKey().isPresent()
				&& credentials.lapiUrl() != null
				&& nginxService.hasModule("ndk_http_module")
				&& nginxService.hasModule("ngx_http_lua_module")
				&& Files.exists(Paths.get(markerPath));
	}

	/** 寬限秒數：非數字或 ≤ 0 用預設值，下限 10（Lua 每 5 秒探測一次），上限 3600 */
	public int graceSeconds() {
		String text = settingService.get(GRACE_KEY);
		int seconds;
		try {
			seconds = Integer.parseInt(text == null ? "" : text.trim());
		} catch (NumberFormatException e) {
			return DEFAULT_GRACE;
		}
		if (seconds <= 0) {
			return DEFAULT_GRACE;
		}
		return Math.max(MIN_GRACE, Math.min(MAX_GRACE, seconds));
	}

	public String bouncerConfPath() {
		return homeConfig.home + "crowdsec/crowdsec-nginx-bouncer.conf";
	}

	/**
	 * 啟動時清掉過期的 bouncer 設定檔：未啟用（金鑰已移除）但磁碟上的 nginx.conf 仍 include 它時，
	 * 檔案不存在會讓 Lua 包裝判定「未設定」而放行。啟用中絕不動它。IO 失敗只記錄，不中斷啟動。
	 *
	 * @return 有刪除檔案時為 true
	 */
	public boolean removeStaleBouncerConf() {
		try {
			if (isActive()) {
				return false;
			}
			boolean deleted = Files.deleteIfExists(Paths.get(bouncerConfPath()));
			if (deleted) {
				logger.info("CrowdSec bouncer is not configured; removed stale bouncer conf {}", bouncerConfPath());
			}
			return deleted;
		} catch (IOException | RuntimeException e) {
			logger.warn("cannot remove stale CrowdSec bouncer conf: {}", e.getMessage());
			return false;
		}
	}

	String stateFilePath() {
		return homeConfig.home + "crowdsec/state/crowdsec_state.json";
	}

	/** bouncer 設定檔內容，每行一個設定 */
	public String buildBouncerConf() {
		String appsec = credentials.appsecUrl();
		List<String> lines = new ArrayList<>();
		lines.add("API_URL=" + credentials.lapiUrl());
		lines.add("API_KEY=" + credentials.bouncerKey().value());
		lines.add("MODE=stream");
		lines.add("UPDATE_FREQUENCY=10");
		lines.add("BOUNCING_ON_TYPE=ban");
		lines.add("APPSEC_URL=" + (appsec == null ? "" : appsec));
		lines.add("APPSEC_FAILURE_ACTION=passthrough");
		lines.add("ALWAYS_SEND_TO_APPSEC=false");
		lines.add("BAN_TEMPLATE_PATH=/var/lib/crowdsec/lua/templates/ban.html");
		return StrUtil.join("\n", lines) + "\n";
	}

	/** http 層 include 內容（字串清單，不經過 NgxDumper）；金鑰不會出現在這裡 */
	public List<String> buildHttpLines(boolean addResolver) {
		List<String> lines = new ArrayList<>();
		lines.add("include " + LUA_PATHS_CONF + ";");
		lines.add("lua_shared_dict crowdsec_cache 50m;");
		lines.add("lua_shared_dict nwu_crowdsec 1m;");
		if (addResolver) {
			lines.add("resolver " + credentials.resolver() + " valid=30s ipv6=off;");
		}
		if (isHttps(credentials.lapiUrl()) || isHttps(credentials.appsecUrl())) {
			lines.add("lua_ssl_trusted_certificate " + CA_BUNDLE + ";");
		}
		lines.add("init_by_lua_block {");
		lines.add("    require(\"nwu_crowdsec\").init({");
		lines.add("        conf = " + luaString(bouncerConfPath()) + ",");
		lines.add("        grace = " + graceSeconds() + ",");
		lines.add("        state = " + luaString(stateFilePath()) + ",");
		lines.add("        page = " + luaString(PAUSED_PAGE) + ",");
		lines.add("    })");
		lines.add("}");
		lines.add("init_worker_by_lua_block {");
		lines.add("    require(\"nwu_crowdsec\").init_worker()");
		lines.add("}");
		lines.add("access_by_lua_block {");
		lines.add("    require(\"nwu_crowdsec\").access()");
		lines.add("}");
		return lines;
	}

	/**
	 * 寫出 bouncer 設定檔（內容不同才寫，POSIX 上權限 rw-------），並建立 worker 可寫的狀態目錄。
	 */
	public void writeBouncerConf() {
		try {
			Path file = Paths.get(bouncerConfPath());
			Files.createDirectories(file.getParent());
			boolean posix = FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
			String content = buildBouncerConf();

			if (!Files.exists(file)) {
				if (posix) {
					Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
				} else {
					Files.createFile(file);
				}
			} else if (posix) {
				Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
			}
			if (!content.equals(Files.readString(file, StandardCharsets.UTF_8))) {
				Files.writeString(file, content, StandardCharsets.UTF_8);
			}

			// nginx worker 不是 root，狀態檔要先寫 .tmp 再 rename，所以目錄要開放寫入
			Path stateDir = file.getParent().resolve("state");
			Files.createDirectories(stateDir);
			if (posix) {
				Set<PosixFilePermission> all = PosixFilePermissions.fromString("rwxrwxrwx");
				Files.setPosixFilePermissions(stateDir, all);
			}
		} catch (IOException e) {
			throw new UncheckedIOException("cannot write crowdsec bouncer config", e);
		}
	}

	private static boolean isHttps(String url) {
		return url != null && url.regionMatches(true, 0, "https://", 0, 8);
	}

	/** Lua 雙引號字串常值：跳脫反斜線、雙引號與換行 */
	static String luaString(String value) {
		StringBuilder sb = new StringBuilder("\"");
		for (char c : value.toCharArray()) {
			switch (c) {
			case '\\':
				sb.append("\\\\");
				break;
			case '"':
				sb.append("\\\"");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			default:
				sb.append(c);
			}
		}
		return sb.append('"').toString();
	}
}
