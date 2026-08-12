package com.cym.model;

import java.util.List;

/**
 * 一條 nginx 指令的官方定義。
 * defaultValue 為 null 表示官方文件的 Default 欄寫「—」（無預設值），語料中有 359 條。
 */
public record NginxDirective(
		String name,
		String syntax,
		String defaultValue,
		List<String> contexts,
		String module,
		String sourceUrl,
		String description) {
}
