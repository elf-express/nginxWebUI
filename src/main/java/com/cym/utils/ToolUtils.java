package com.cym.utils;

import cn.hutool.core.util.StrUtil;

public class ToolUtils {

	/**
	 * 处理conf一些语法问题
	 * 
	 * @param path
	 * @return
	 */
	public static String handleConf(String path) {
		if (StrUtil.isEmpty(path)) {
			return path;
		}
		return path.replace("};", "  }");
	}

	/**
	 * 按nginx规范重新缩进conf内容，每层嵌套缩进4个空格
	 *
	 * @param conf
	 * @return
	 */
	public static String formatConf(String conf) {
		if (StrUtil.isEmpty(conf)) {
			return conf;
		}

		String[] lines = conf.split("\n");
		StringBuilder sb = new StringBuilder();
		int indent = 0;

		for (String line : lines) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				sb.append("\n");
				continue;
			}

			// 遇到 } 先减少缩进
			if (trimmed.startsWith("}")) {
				indent--;
				if (indent < 0) {
					indent = 0;
				}
			}

			// 添加缩进
			for (int i = 0; i < indent; i++) {
				sb.append("    ");
			}
			sb.append(trimmed).append("\n");

			// 遇到 { 结尾增加缩进
			if (trimmed.endsWith("{")) {
				indent++;
			}
		}

		return sb.toString().trim();
	}

	/**
	 * 处理路径的斜杠和空格
	 * 
	 * @param path
	 * @return
	 */
	public static String handlePath(String path) {
		if (StrUtil.isEmpty(path)) {
			return path;
		}
		return path.replace("\\", "/") // 替换反斜杠
				.replace("//", "/") // 替换双斜杠
				.replaceAll("[\\s?<>|\"#&;'`]", ""); // 删除空格和特殊字符
	}

	/**
	 * 处理目录最后的斜杠
	 * 
	 * @param path
	 * @return
	 */
	public static String endDir(String path) {
		if (StrUtil.isEmpty(path)) {
			return path;
		}
		if (!path.endsWith("/")) {
			path += "/";
		}

		return path;
	}
}
