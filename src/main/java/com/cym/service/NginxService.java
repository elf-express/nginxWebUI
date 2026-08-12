package com.cym.service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.Module;
import com.cym.sqlhelper.bean.Sort;
import com.cym.sqlhelper.bean.Sort.Direction;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.SystemTool;

import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;

@Component
public class NginxService {
	private static final Logger logger = LoggerFactory.getLogger(NginxService.class);

	private static final String MODULE_DIR = "/usr/lib/nginx/modules";

	/**
	 * 模組目錄：{ .so 檔名, i18n descrKey 後綴 }。
	 * 順序 = load_module 唯一真相（NDK/Lua → stream/mail/rtmp → geoip2 → njs/keyval → 壓縮 → 過濾 → 動態 upstream → 功能模組）。
	 * 已排除：upstream_fair、legacy geoip、perl、upload*、zip、untar、slowfs、echo、dav、fancyindex、xslt、shibboleth、log_zmq、accounting、redis2。
	 */
	public static final String[][] MODULE_CATALOG = {
		// NDK + Lua 生態（必須最先）
		{ "ndk_http_module.so", "descrNdk" },
		{ "ngx_http_lua_module.so", "descrLua" },
		{ "ngx_http_lua_upstream_module.so", "descrLuaUpstream" },
		{ "ngx_http_set_misc_module.so", "descrSetMisc" },
		{ "ngx_http_array_var_module.so", "descrArrayVar" },
		{ "ngx_http_encrypted_session_module.so", "descrEncryptedSession" },
		// 動態上下文核心
		{ "ngx_stream_module.so", "descrStream" },
		{ "ngx_mail_module.so", "descrMail" },
		{ "ngx_rtmp_module.so", "descrRtmp" },
		// GeoIP2（非 legacy .dat）
		{ "ngx_http_geoip2_module.so", "descrHttpGeoip2" },
		{ "ngx_stream_geoip2_module.so", "descrStreamGeoip2" },
		// njs + keyval
		{ "ngx_http_js_module.so", "descrHttpJs" },
		{ "ngx_stream_js_module.so", "descrStreamJs" },
		{ "ngx_http_keyval_module.so", "descrHttpKeyval" },
		{ "ngx_stream_keyval_module.so", "descrStreamKeyval" },
		// 壓縮
		{ "ngx_http_brotli_filter_module.so", "descrBrotliFilter" },
		{ "ngx_http_brotli_static_module.so", "descrBrotliStatic" },
		{ "ngx_http_zstd_filter_module.so", "descrZstdFilter" },
		{ "ngx_http_zstd_static_module.so", "descrZstdStatic" },
		// headers / cache / filters
		{ "ngx_http_headers_more_filter_module.so", "descrHeadersMore" },
		{ "ngx_http_cache_purge_module.so", "descrCachePurge" },
		{ "ngx_http_cookie_flag_filter_module.so", "descrCookieFlag" },
		{ "ngx_http_image_filter_module.so", "descrImageFilter" },
		// 動態 upstream
		{ "ngx_http_dynamic_upstream_module.so", "descrDynamicUpstream" },
		{ "ngx_http_dynamic_healthcheck_module.so", "descrDynamicHealthcheck" },
		// 功能模組
		{ "ngx_http_auth_jwt_module.so", "descrAuthJwt" },
		{ "ngx_http_naxsi_module.so", "descrNaxsi" },
		{ "ngx_http_vhost_traffic_status_module.so", "descrVts" },
		{ "ngx_nchan_module.so", "descrNchan" },
		{ "ngx_http_vod_module.so", "descrVod" },
		{ "ngx_http_acme_module.so", "descrAcme" },
	};

	/**
	 * 已知安全的模組白名單（按 MODULE_CATALOG 依賴順序）
	 * 只有在此清單中且容器內實際存在的模組才會被載入
	 */
	private static final List<String> SAFE_MODULES;
	static {
		List<String> names = new ArrayList<>();
		for (String[] row : MODULE_CATALOG) {
			names.add(row[0]);
		}
		SAFE_MODULES = names;
	}

	/** Dependency map: key depends on value (value must load first) */
	private static final Map<String, String> DEPENDENCY_MAP = new HashMap<>();

	static {
		DEPENDENCY_MAP.put("ngx_stream_geoip2_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_stream_js_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_stream_keyval_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_http_lua_module.so", "ndk_http_module.so");
		DEPENDENCY_MAP.put("ngx_http_lua_upstream_module.so", "ngx_http_lua_module.so");
		DEPENDENCY_MAP.put("ngx_http_set_misc_module.so", "ndk_http_module.so");
		DEPENDENCY_MAP.put("ngx_http_array_var_module.so", "ndk_http_module.so");
		DEPENDENCY_MAP.put("ngx_http_encrypted_session_module.so", "ndk_http_module.so");
	}

	@Inject
	SettingService settingService;
	@Inject
	SqlHelper sqlHelper;

	/**
	 * Execute nginx -v and parse the version string.
	 * Returns version like "1.28.0", or null on non-Linux or failure.
	 */
	public String getNginxVersion() {
		if (!SystemTool.isLinux()) {
			return null;
		}

		try {
			String nginxExe = settingService.get("nginxExe");
			if (StrUtil.isEmpty(nginxExe)) {
				nginxExe = "nginx";
			}

			// nginx -v 輸出到 stderr，需要 2>&1 重導向
			String result = RuntimeUtil.execForStr("/bin/sh", "-c", nginxExe + " -v 2>&1");
			if (StrUtil.isEmpty(result)) {
				return null;
			}

			// nginx -v outputs: "nginx version: nginx/1.28.0"
			int idx = result.indexOf("nginx/");
			if (idx >= 0) {
				String version = result.substring(idx + 6).trim();
				// Remove trailing newlines or extra text
				int newline = version.indexOf('\n');
				if (newline >= 0) {
					version = version.substring(0, newline).trim();
				}
				return version;
			}
		} catch (Exception e) {
			logger.error("Failed to get nginx version", e);
		}

		return null;
	}

	/**
	 * 回傳容器內實際存在且在白名單中的模組，按依賴順序排列。
	 * 白名單機制避免載入未知模組造成衝突。
	 */
	public List<String> getAvailableModules() {
		List<String> modules = new ArrayList<>();

		if (!SystemTool.isLinux()) {
			return modules;
		}

		File dir = new File(MODULE_DIR);
		if (!dir.exists() || !dir.isDirectory()) {
			return modules;
		}

		// 掃描容器內實際存在的 .so 檔案
		File[] files = dir.listFiles((d, name) -> name.endsWith(".so"));
		if (files == null) {
			return modules;
		}

		Set<String> existingModules = new HashSet<>();
		for (File f : files) {
			existingModules.add(f.getName());
		}

		// 按白名單順序，只載入實際存在的模組（白名單已按依賴順序排列）
		for (String safe : SAFE_MODULES) {
			if (existingModules.contains(safe)) {
				modules.add(safe);
			}
		}

		return modules;
	}

	/**
	 * 回傳容器內所有 .so 模組（含不在白名單的），供 Header 顯示用
	 */
	public List<String> getAllModules() {
		List<String> modules = new ArrayList<>();

		if (!SystemTool.isLinux()) {
			return modules;
		}

		File dir = new File(MODULE_DIR);
		if (!dir.exists() || !dir.isDirectory()) {
			return modules;
		}

		File[] files = dir.listFiles((d, name) -> name.endsWith(".so"));
		if (files == null) {
			return modules;
		}

		for (File f : files) {
			modules.add(f.getName());
		}
		modules.sort(String::compareTo);
		return modules;
	}

	/**
	 * Return full paths of available modules, sorted by dependency order.
	 */
	public List<String> getModulePaths() {
		List<String> names = getAvailableModules();
		List<String> paths = new ArrayList<>();
		for (String name : names) {
			paths.add(MODULE_DIR + "/" + name);
		}
		return paths;
	}

	/**
	 * Check if geoip2 module is available (dynamic .so OR static compiled-in via nginx -V).
	 * 順修:原本只認動態 .so,geoip2 若 static 編譯進 nginx 會誤報未裝;改用雙軌 hasModule。
	 */
	public boolean hasGeoIp2Module() {
		return hasModule("geoip2");
	}

	/**
	 * 回傳資料庫中已啟用且磁碟上存在的模組完整路徑。
	 * 順序以 MODULE_CATALOG 為唯一真相（不依 DB seq，避免 migration 打亂 load_module 依賴序）。
	 */
	public List<String> getEnabledModulePaths() {
		List<String> paths = new ArrayList<>();

		if (!SystemTool.isLinux()) {
			return paths;
		}

		// 磁碟上實際存在的模組
		Set<String> existingModules = new HashSet<>();
		File dir = new File(MODULE_DIR);
		if (dir.exists() && dir.isDirectory()) {
			File[] files = dir.listFiles((d, name) -> name.endsWith(".so"));
			if (files != null) {
				for (File f : files) {
					existingModules.add(f.getName());
				}
			}
		}

		// 已啟用名稱集合
		Set<String> enabledNames = new HashSet<>();
		List<Module> modules = sqlHelper.findAll(Module.class);
		for (Module module : modules) {
			if (module.getEnable() != null && module.getEnable() && module.getName() != null) {
				enabledNames.add(module.getName());
			}
		}

		// 依 catalog 順序輸出（保證 NDK 先於 Lua、stream 先於 stream_* 等）
		for (String[] row : MODULE_CATALOG) {
			String name = row[0];
			if (enabledNames.contains(name) && existingModules.contains(name)) {
				paths.add(MODULE_DIR + "/" + name);
			}
		}

		return paths;
	}

	/**
	 * 通用雙軌 module 偵測:動態(DB 已啟用且磁碟存在的 .so 路徑含 keyword)
	 * OR 靜態(nginx -V configure arguments 含 keyword)。
	 * static 編譯進 nginx binary 的 module 無 .so,只能靠 nginx -V 看到。
	 * 非 Linux 一律回 false(呼叫端須自行做 fallback,見 ServerController)。
	 */
	public boolean hasModule(String keyword) {
		if (!SystemTool.isLinux()) {
			return false;
		}
		// 動態:DB 已啟用且磁碟存在的 .so 路徑含 keyword
		for (String path : getEnabledModulePaths()) {
			if (path.contains(keyword)) {
				return true;
			}
		}
		// 靜態:nginx -V 的 configure arguments 含 keyword(--add-module / --with-http_xxx_module)
		String configureArgs = getNginxConfigureArgs();
		if (configureArgs != null && configureArgs.contains(keyword)) {
			return true;
		}
		return false;
	}

	/**
	 * Check if brotli module is available (dynamic .so or static compiled-in).
	 */
	public boolean hasBrotliModule() {
		return hasModule("brotli");
	}

	/**
	 * Execute nginx -V and return full output (includes configure arguments —
	 * the only way to detect statically compiled-in modules). Null on non-Linux / failure.
	 */
	public String getNginxConfigureArgs() {
		if (!SystemTool.isLinux()) {
			return null;
		}
		try {
			String nginxExe = settingService.get("nginxExe");
			if (StrUtil.isEmpty(nginxExe)) {
				nginxExe = "nginx";
			}
			// 直接以 argv 呼叫 binary(不經 shell,杜絕 command injection);
			// nginx -V 輸出到 stderr,用 redirectErrorStream 併入 stdout 再讀。
			Process process = new ProcessBuilder(nginxExe, "-V").redirectErrorStream(true).start();
			return RuntimeUtil.getResult(process);
		} catch (Exception e) {
			logger.error("Failed to get nginx -V", e);
		}
		return null;
	}

}
