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

import com.cym.utils.SystemTool;

import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;

@Component
public class NginxService {
	private static final Logger logger = LoggerFactory.getLogger(NginxService.class);

	private static final String MODULE_DIR = "/usr/lib/nginx/modules";

	/** Dependency map: key depends on value (value must load first) */
	private static final Map<String, String> DEPENDENCY_MAP = new HashMap<>();

	static {
		DEPENDENCY_MAP.put("ngx_stream_geoip2_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_stream_geoip_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_stream_js_module.so", "ngx_stream_module.so");
		DEPENDENCY_MAP.put("ngx_stream_keyval_module.so", "ngx_stream_module.so");
	}

	@Inject
	SettingService settingService;

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

			String result = RuntimeUtil.execForStr(nginxExe, "-v");
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
	 * Scan /usr/lib/nginx/modules/*.so and return sorted list of module filenames.
	 * Sorted using topological dependency ordering.
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

		File[] files = dir.listFiles((d, name) -> name.endsWith(".so"));
		if (files == null) {
			return modules;
		}

		Set<String> moduleSet = new HashSet<>();
		for (File f : files) {
			moduleSet.add(f.getName());
		}

		return topologicalSort(moduleSet);
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
	 * Check if ngx_http_geoip2_module.so exists in available modules.
	 */
	public boolean hasGeoIp2Module() {
		List<String> modules = getAvailableModules();
		return modules.contains("ngx_http_geoip2_module.so");
	}

	/**
	 * Topological sort: dependencies come before dependents, remaining sorted alphabetically.
	 */
	private List<String> topologicalSort(Set<String> moduleSet) {
		List<String> result = new ArrayList<>();
		Set<String> visited = new HashSet<>();

		// Sort alphabetically first, then apply topological ordering
		List<String> sorted = new ArrayList<>(moduleSet);
		sorted.sort(String::compareTo);

		for (String module : sorted) {
			visit(module, moduleSet, visited, result);
		}

		return result;
	}

	private void visit(String module, Set<String> moduleSet, Set<String> visited, List<String> result) {
		if (visited.contains(module)) {
			return;
		}
		visited.add(module);

		// If this module has a dependency, visit the dependency first
		String dependency = DEPENDENCY_MAP.get(module);
		if (dependency != null && moduleSet.contains(dependency)) {
			visit(dependency, moduleSet, visited, result);
		}

		result.add(module);
	}
}
