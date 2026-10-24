package com.cym.testsupport;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 測試用的 LAPI alert，格式照 crowdsec v1.8.1 實測（E-394 Plan「實測」）：
 * alert meta 的值是 JSON 字串陣列；站台在 events[].meta 的 target_fqdn；CRS 規則名稱為 native_rule:&lt;編號&gt;。
 */
public final class WafAlertFixtures {
	public static final String UA = "Mozilla/5.0 e394-test";
	public static final String SQLI_URI = "/search?q=1%27%20UNION%20SELECT%20password%20FROM%20users--";

	private WafAlertFixtures() {
	}

	/** vpatch in-band 擋下 GET /.env */
	public static JSONObject vpatch(long id, String ip, Instant at, String fqdn) {
		return alert(id, "waf", "WAF block: crowdsecurity/vpatch-env-access from " + ip + " (127.0.0.1)", ip, at,
				meta("msg", list("Detect access to .env files"), "method", list("GET"), "target_uri", list("/.env"),
						"name", list("crowdsecurity/vpatch-env-access"), "user_agent", list(UA)),
				event("crowdsecurity/vpatch-env-access", "Detect access to .env files", "/.env", fqdn));
	}

	/** CRS out-of-band 命中同一個 /.env 請求（930130），附 CRS 內部規則 901340、949110、980170 */
	public static JSONObject crsLfi(long id, String ip, Instant at, String fqdn) {
		return alert(id, "waf", "WAF out-of-band match: anomaly score out-of-band: lfi: 5, anomaly: 5,  from " + ip + " (127.0.0.1)",
				ip, at,
				meta("method", list("GET"), "msg", list("Restricted File Access Attempt"), "target_uri", list("/.env"),
						"name", list("native_rule:930130"), "user_agent", list(UA)),
				event("native_rule:901340", "Enabling body inspection", "/.env", fqdn),
				event("native_rule:930130", "Restricted File Access Attempt", "/.env", fqdn),
				event("native_rule:949110", "Inbound Anomaly Score Exceeded (Total Score: 5)", "/.env", fqdn),
				event("native_rule:980170", "Anomaly Scores: (Inbound Scores: blocking=5)", "/.env", fqdn));
	}

	/** CRS out-of-band：SQL 注入（942100、942190），不擋 */
	public static JSONObject crsSqli(long id, String ip, Instant at, String fqdn) {
		return alert(id, "waf", "WAF out-of-band match: anomaly score out-of-band: sql_injection: 10, anomaly: 10,  from " + ip
				+ " (127.0.0.1)", ip, at,
				meta("target_uri", list(SQLI_URI), "msg",
						list("SQL Injection Attack Detected via libinjection", "Detects MSSQL code execution and information gathering attempts"),
						"name", list("native_rule:942100", "native_rule:942190"), "method", list("GET"), "user_agent", list(UA)),
				event("native_rule:901340", "Enabling body inspection", SQLI_URI, fqdn),
				event("native_rule:942100", "SQL Injection Attack Detected via libinjection", SQLI_URI, fqdn),
				event("native_rule:942190", "Detects MSSQL code execution and information gathering attempts", SQLI_URI, fqdn),
				event("native_rule:949110", "Inbound Anomaly Score Exceeded (Total Score: 10)", SQLI_URI, fqdn));
	}

	/** 不是 WAF 的 alert（nginx 日誌情境）：沒有 kind */
	public static JSONObject scenario(long id, String ip, Instant at) {
		JSONObject a = alert(id, null, "Ip " + ip + " performed 'crowdsecurity/http-probing' (11 events over 1s)", ip, at,
				meta("target_uri", list("/a", "/b")));
		a.set("scenario", "crowdsecurity/http-probing");
		return a;
	}

	public static String array(JSONObject... alerts) {
		JSONArray arr = new JSONArray();
		for (JSONObject a : alerts) {
			arr.add(a);
		}
		return arr.toString();
	}

	public static String iso(Instant at) {
		return DateTimeFormatter.ISO_INSTANT.format(at.truncatedTo(ChronoUnit.SECONDS));
	}

	public static String list(String... values) {
		return JSONUtil.toJsonStr(values);
	}

	public static JSONArray meta(String... keyValues) {
		JSONArray arr = new JSONArray();
		for (int i = 0; i < keyValues.length; i += 2) {
			arr.add(new JSONObject().set("key", keyValues[i]).set("value", keyValues[i + 1]));
		}
		return arr;
	}

	public static JSONObject event(String rule, String message, String uri, String fqdn) {
		return new JSONObject().set("meta", meta("rule_name", rule, "message", message, "uri", uri, "target_fqdn", fqdn))
				.set("timestamp", "2026-10-24 02:50:17 +0000 UTC");
	}

	public static JSONObject alert(long id, String kind, String message, String ip, Instant at, JSONArray meta,
			JSONObject... events) {
		JSONArray ev = new JSONArray();
		for (JSONObject e : events) {
			ev.add(e);
		}
		JSONObject a = new JSONObject().set("id", id).set("message", message).set("created_at", iso(at))
				.set("start_at", iso(at)).set("stop_at", iso(at))
				.set("source", new JSONObject().set("ip", ip).set("value", ip).set("scope", "Ip"))
				.set("meta", meta).set("events", ev);
		if (kind != null) {
			a.set("kind", kind);
		}
		return a;
	}
}
