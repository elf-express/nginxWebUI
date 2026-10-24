package com.cym.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/** WAF 規則目錄：crowdsec 容器匯出到共用 volume 的 catalog/vpatch.json、crs.tsv、meta.json（E-395） */
public final class WafRuleCatalog {
	private static final Logger logger = LoggerFactory.getLogger(WafRuleCatalog.class);
	public static final String SOURCE_VPATCH = WafCategories.SOURCE_VPATCH;
	public static final String SOURCE_CRS = WafCategories.SOURCE_CRS;
	/** 已知漏洞沒有「產品 - 」時的分類（規則分頁專用） */
	public static final String OTHER = "_other";

	private WafRuleCatalog() {
	}

	/** key：vpatch 為規則名稱、CRS 為 6 位數編號；category：分類代碼 */
	public record Rule(String key, String source, String category, String label, String description, List<String> cves) {
	}

	public record Catalog(List<Rule> rules, String generatedAt, String crowdsec) {
		public Map<String, Rule> byKey() {
			Map<String, Rule> map = new LinkedHashMap<>();
			for (Rule r : rules) {
				map.put(r.key(), r);
			}
			return map;
		}

		public Set<String> categories() {
			Set<String> set = new LinkedHashSet<>();
			for (Rule r : rules) {
				set.add(r.category());
			}
			return set;
		}
	}

	/** label 中「 - 」之前的文字是產品；沒有分隔符號歸「其他」 */
	static String vpatchCategory(String label) {
		int i = label == null ? -1 : label.indexOf(" - ");
		return SOURCE_VPATCH + ":" + (i > 0 ? label.substring(0, i).trim() : OTHER);
	}

	static String crsCategory(String id) {
		return WafCategories.categoryOf(id);
	}

	public static Catalog parse(String vpatchJson, String crsTsv, String metaJson) {
		List<Rule> rules = new ArrayList<>();
		JSONArray vpatch = JSONUtil.parseArray(vpatchJson);
		for (int i = 0; i < vpatch.size(); i++) {
			JSONObject o = vpatch.getJSONObject(i);
			String name = o == null ? null : o.getStr("name");
			if (StrUtil.isBlank(name)) {
				continue;
			}
			JSONArray cves = o.getJSONArray("cves");
			String label = o.getStr("label", "");
			rules.add(new Rule(name, SOURCE_VPATCH, vpatchCategory(label), label, o.getStr("description", ""),
					cves == null ? List.of() : List.copyOf(cves.toList(String.class))));
		}
		Set<String> seen = new LinkedHashSet<>();
		for (String line : crsTsv.split("\r?\n")) {
			String[] f = line.split("\t", -1);
			if (f.length < 3 || !f[0].matches("\\d{6}") || f[2].isBlank() || !(f[1].isEmpty() || "1".equals(f[1])) || !seen.add(f[0])) {
				continue;
			}
			rules.add(new Rule(f[0], SOURCE_CRS, crsCategory(f[0]), f[2].trim(), "", List.of()));
		}
		JSONObject meta = StrUtil.isBlank(metaJson) ? new JSONObject() : JSONUtil.parseObj(metaJson);
		return new Catalog(List.copyOf(rules), meta.getStr("generatedAt"), meta.getStr("crowdsec"));
	}

	/** vpatch.json 或 crs.tsv 不存在、讀不到或格式錯誤時回 null（畫面顯示「CrowdSec 未提供規則清單」） */
	public static Catalog load(Path dir) {
		Path vpatch = dir.resolve("vpatch.json");
		Path crs = dir.resolve("crs.tsv");
		Path meta = dir.resolve("meta.json");
		if (!Files.isRegularFile(vpatch) || !Files.isRegularFile(crs)) {
			return null;
		}
		try {
			return parse(Files.readString(vpatch, StandardCharsets.UTF_8), Files.readString(crs, StandardCharsets.UTF_8),
					Files.isRegularFile(meta) ? Files.readString(meta, StandardCharsets.UTF_8) : null);
		} catch (IOException | RuntimeException e) {
			logger.warn("WAF rule catalog unreadable: {}", e.getMessage());
			return null;
		}
	}
}
