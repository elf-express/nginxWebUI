package com.cym.service;

import java.util.List;
import java.util.Map;

/**
 * WAF 規則的來源與分類代碼。CRS 規則依編號前三碼分類（911、920–922 通訊協定，913 掃描器，930 讀取本機檔案……），
 * 已知漏洞（virtual patching）一律為 vpatch。E-395 的規則分頁沿用同一套代碼。
 */
public final class WafCategories {
	public static final String SOURCE_VPATCH = "vpatch";
	public static final String SOURCE_CRS = "crs";
	public static final String VPATCH = "vpatch";
	public static final String CRS_OTHER = "crs-other";

	private static final Map<String, String> CRS = Map.ofEntries(Map.entry("911", "crs-protocol"),
			Map.entry("913", "crs-scanner"), Map.entry("920", "crs-protocol"), Map.entry("921", "crs-protocol"),
			Map.entry("922", "crs-protocol"), Map.entry("930", "crs-lfi"), Map.entry("931", "crs-rfi"),
			Map.entry("932", "crs-rce"), Map.entry("933", "crs-php"), Map.entry("934", "crs-generic"),
			Map.entry("941", "crs-xss"), Map.entry("942", "crs-sqli"), Map.entry("943", "crs-session"),
			Map.entry("944", "crs-java"));

	/** 畫面「類別」下拉的順序：已知漏洞，再依 CRS 攻擊類型 */
	public static final List<String> ALL = List.of(VPATCH, "crs-scanner", "crs-protocol", "crs-lfi", "crs-rfi",
			"crs-rce", "crs-php", "crs-generic", "crs-xss", "crs-sqli", "crs-session", "crs-java", CRS_OTHER);

	private WafCategories() {
	}

	/** CRS 規則名稱是六位數字的規則編號 */
	public static boolean isCrsRule(String ruleName) {
		return ruleName != null && ruleName.matches("\\d{6}");
	}

	public static String sourceOf(String ruleName) {
		return isCrsRule(ruleName) ? SOURCE_CRS : SOURCE_VPATCH;
	}

	public static String categoryOf(String ruleName) {
		return isCrsRule(ruleName) ? CRS.getOrDefault(ruleName.substring(0, 3), CRS_OTHER) : VPATCH;
	}
}
