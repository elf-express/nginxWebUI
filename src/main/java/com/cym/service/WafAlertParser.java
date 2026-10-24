package com.cym.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.cym.utils.IpMatch;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * LAPI GET /v1/alerts 的回應轉成 WAF 命中紀錄。只取 kind=waf（AppSec 產生）的 alert，其他（nginx 日誌情境、手動封鎖）略過。
 * <p>
 * 欄位來源（crowdsec v1.8.1）：alert meta 的 name／msg／method／target_uri／user_agent 是 JSON 字串陣列，
 * name 只列真正命中的規則；站台在 events[].meta 的 target_fqdn（可能帶埠）；
 * CRS 規則名稱為 native_rule:942100，vpatch 為 crowdsecurity/vpatch-*；in-band 命中的 message 以「WAF block」開頭。
 */
public final class WafAlertParser {
	static final String IN_BAND_PREFIX = "WAF block";
	static final String NATIVE_PREFIX = "native_rule:";
	static final int MAX_URI = 2048;
	static final int MAX_USER_AGENT = 512;
	static final int MAX_MESSAGE = 500;

	/** 命中的一條規則：name 是 CRS 規則編號（942100）或 vpatch 規則名稱（crowdsecurity/vpatch-env-access） */
	public record Rule(String name, String source, String category, String message) {
	}

	/** 一個 WAF alert；host 已去掉連接埠並轉小寫，沒有時為 null */
	public record Hit(String alertId, Instant time, String ip, String host, String method, String uri, String userAgent,
			boolean inBand, List<Rule> rules) {
	}

	/** 一頁 alert：hits 只含 WAF；size 是這頁所有 alert 的筆數；oldest 是這頁所有 alert 最舊的建立時間，空頁為 null */
	public record Batch(List<Hit> hits, int size, Instant oldest) {
	}

	private WafAlertParser() {
	}

	public static Batch parseBatch(String body) {
		JSONArray arr = CrowdSecClient.parseArray(body);
		List<Hit> hits = new ArrayList<>();
		Instant oldest = null;
		for (Object item : arr) {
			if (!(item instanceof JSONObject alert)) {
				continue;
			}
			Instant time = timeOf(alert);
			if (time != null && (oldest == null || time.isBefore(oldest))) {
				oldest = time;
			}
			Hit hit = parseAlert(alert, time);
			if (hit != null) {
				hits.add(hit);
			}
		}
		return new Batch(hits, arr.size(), oldest);
	}

	static Hit parseAlert(JSONObject alert, Instant time) {
		if (time == null || !"waf".equals(alert.getStr("kind"))) {
			return null;
		}
		String id = alert.getStr("id");
		JSONObject source = alert.getJSONObject("source");
		String ip = source == null ? null : StrUtil.blankToDefault(source.getStr("ip"), source.getStr("value"));
		if (StrUtil.isBlank(id) || !IpMatch.isSingleIp(ip)) {
			return null;
		}
		Map<String, List<String>> meta = metaLists(alert.getJSONArray("meta"));
		Map<String, String> eventMessages = new LinkedHashMap<>();
		String fqdn = null;
		String eventUri = null;
		JSONArray events = alert.getJSONArray("events");
		if (events != null) {
			for (Object item : events) {
				if (!(item instanceof JSONObject event)) {
					continue;
				}
				Map<String, String> m = metaMap(event.getJSONArray("meta"));
				fqdn = fqdn != null ? fqdn : m.get("target_fqdn");
				eventUri = eventUri != null ? eventUri : m.get("uri");
				String rule = ruleName(m.get("rule_name"));
				if (rule != null && !eventMessages.containsKey(rule)) {
					eventMessages.put(rule, m.get("message"));
				}
			}
		}
		List<Rule> rules = rules(meta.get("name"), meta.get("msg"), eventMessages);
		if (rules.isEmpty()) {
			return null;
		}
		String uri = first(meta.get("target_uri"));
		String method = first(meta.get("method"));
		return new Hit(id, time, ip.trim(), hostOf(fqdn), method == null ? null : method.toUpperCase(Locale.ROOT),
				cut(uri != null ? uri : eventUri, MAX_URI), cut(first(meta.get("user_agent")), MAX_USER_AGENT),
				StrUtil.startWith(alert.getStr("message"), IN_BAND_PREFIX), rules);
	}

	/**
	 * 規則清單：alert meta 的 name 優先（已不含 CRS 內部規則），說明取 events 中同名規則的 message，沒有時用 meta msg 同位置的值；
	 * meta 沒有 name 時改用 events 的 rule_name，並略過 CRS 內部規則（編號小於 911000 或 949000 以上，例 901340、949110、980170）。
	 */
	static List<Rule> rules(List<String> names, List<String> msgs, Map<String, String> eventMessages) {
		boolean fromEvents = names == null || names.isEmpty();
		List<String> source = fromEvents ? new ArrayList<>(eventMessages.keySet()) : names;
		Map<String, Rule> rules = new LinkedHashMap<>();
		for (int i = 0; i < source.size(); i++) {
			String name = ruleName(source.get(i));
			if (name == null || rules.containsKey(name) || (fromEvents && isCrsInternal(name))) {
				continue;
			}
			String message = eventMessages.get(name);
			if (message == null && !fromEvents && msgs != null && msgs.size() == names.size()) {
				message = msgs.get(i);
			}
			rules.put(name, new Rule(name, WafCategories.sourceOf(name), WafCategories.categoryOf(name), cut(message, MAX_MESSAGE)));
		}
		return new ArrayList<>(rules.values());
	}

	static boolean isCrsInternal(String name) {
		if (!WafCategories.isCrsRule(name)) {
			return false;
		}
		int id = Integer.parseInt(name);
		return id < 911000 || id >= 949000;
	}

	/** native_rule:942100 → 942100；其他名稱原樣；空白回 null */
	static String ruleName(String raw) {
		if (StrUtil.isBlank(raw)) {
			return null;
		}
		String name = raw.trim();
		return name.startsWith(NATIVE_PREFIX) ? StrUtil.emptyToNull(name.substring(NATIVE_PREFIX.length()).trim()) : name;
	}

	/** target_fqdn 去掉連接埠（含 [IPv6]:埠）、結尾的點並轉小寫；空白回 null */
	static String hostOf(String fqdn) {
		if (StrUtil.isBlank(fqdn)) {
			return null;
		}
		String h = fqdn.trim();
		if (h.startsWith("[")) {
			int end = h.indexOf(']');
			h = end > 0 ? h.substring(1, end) : h.substring(1);
		} else if (h.indexOf(':') > 0 && h.indexOf(':') == h.lastIndexOf(':')) {
			h = h.substring(0, h.indexOf(':'));
		}
		h = h.toLowerCase(Locale.ROOT);
		while (h.endsWith(".") && h.length() > 1) {
			h = h.substring(0, h.length() - 1);
		}
		return StrUtil.isBlank(h) ? null : h;
	}

	static Instant timeOf(JSONObject alert) {
		for (String key : new String[] { "created_at", "start_at" }) {
			String v = alert.getStr(key);
			if (StrUtil.isNotBlank(v)) {
				try {
					return Instant.parse(v.trim());
				} catch (DateTimeParseException e) {
					// 換下一個欄位
				}
			}
		}
		return null;
	}

	/** alert meta：每個值是 JSON 字串陣列（不是陣列時當成一個值） */
	static Map<String, List<String>> metaLists(JSONArray meta) {
		Map<String, List<String>> map = new LinkedHashMap<>();
		if (meta == null) {
			return map;
		}
		for (Object item : meta) {
			if (!(item instanceof JSONObject o) || StrUtil.isBlank(o.getStr("key")) || o.getStr("value") == null) {
				continue;
			}
			String v = o.getStr("value");
			List<String> values = new ArrayList<>();
			if (JSONUtil.isTypeJSONArray(v)) {
				for (Object x : JSONUtil.parseArray(v)) {
					if (x != null) {
						values.add(String.valueOf(x));
					}
				}
			} else {
				values.add(v);
			}
			map.putIfAbsent(o.getStr("key"), values);
		}
		return map;
	}

	/** event meta：key → value（同 key 取第一個） */
	static Map<String, String> metaMap(JSONArray meta) {
		Map<String, String> map = new LinkedHashMap<>();
		if (meta == null) {
			return map;
		}
		for (Object item : meta) {
			if (item instanceof JSONObject o && StrUtil.isNotBlank(o.getStr("key")) && o.getStr("value") != null) {
				map.putIfAbsent(o.getStr("key"), o.getStr("value"));
			}
		}
		return map;
	}

	private static String first(List<String> values) {
		if (values == null) {
			return null;
		}
		for (String v : values) {
			if (StrUtil.isNotBlank(v)) {
				return v;
			}
		}
		return null;
	}

	private static String cut(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}
