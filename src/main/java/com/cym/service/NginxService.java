package com.cym.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
	 * 已知安全的模組白名單（按依賴順序排列）
	 * 只有在此清單中且容器內實際存在的模組才會被載入
	 */
	private static final List<String> SAFE_MODULES = Arrays.asList(
		// stream 核心（必須最先載入，其他 stream 模組依賴它）
		"ngx_stream_module.so",
		// GeoIP2（stream_geoip2 依賴 stream_module）
		"ngx_stream_geoip2_module.so",
		"ngx_http_geoip2_module.so",
		// NDK（必須在 lua 之前，lua 依賴 ndk）
		"ndk_http_module.so",
		// Lua（依賴 ndk）
		"ngx_http_lua_module.so",
		// 壓縮
		"ngx_http_brotli_filter_module.so",
		"ngx_http_brotli_static_module.so",
		"ngx_http_zstd_filter_module.so",
		"ngx_http_zstd_static_module.so",
		// Headers
		"ngx_http_headers_more_filter_module.so",
		// Cache
		"ngx_http_cache_purge_module.so"
	);

	/** Dependency map: key depends on value (value must load first) */
	private static final Map<String, String> DEPENDENCY_MAP = new HashMap<>();

	static {
		DEPENDENCY_MAP.put("ngx_stream_geoip2_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_stream_geoip_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_stream_js_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_stream_keyval_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_http_lua_module.so", "ndk_http_module.so");
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
	 * 回傳資料庫中已啟用且磁碟上存在的模組完整路徑，按 seq 順序排列。
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

		// 從資料庫取得已啟用的模組，按 seq 排序（保證依賴順序）
		List<Module> modules = sqlHelper.findAll(new Sort("seq", Direction.ASC), Module.class);
		for (Module module : modules) {
			if (module.getEnable() != null && module.getEnable() && existingModules.contains(module.getName())) {
				paths.add(MODULE_DIR + "/" + module.getName());
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
