package com.cym.service;

import static com.cym.testsupport.WafAlertFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import cn.hutool.json.JSONObject;

/** LAPI alert → WAF 命中：只取 kind=waf、規則名稱與說明、站台去埠、in-band 判斷、CRS 分類 */
class WafAlertParserTest {
	private static final Instant AT = Instant.parse("2026-10-24T02:50:17Z");

	@Test
	void vpatchAlertIsInBandWithItsRuleAndRequestFields() {
		WafAlertParser.Batch b = WafAlertParser.parseBatch(array(vpatch(1, "198.51.100.9", AT, "Shop.Example.TEST:8443")));
		assertEquals(1, b.size());
		assertEquals(AT, b.oldest());
		WafAlertParser.Hit h = b.hits().get(0);
		assertEquals("1", h.alertId());
		assertEquals(AT, h.time());
		assertEquals("198.51.100.9", h.ip());
		assertEquals("shop.example.test", h.host());
		assertEquals("GET", h.method());
		assertEquals("/.env", h.uri());
		assertEquals(UA, h.userAgent());
		assertTrue(h.inBand());
		assertEquals(List.of(new WafAlertParser.Rule("crowdsecurity/vpatch-env-access", "vpatch", "vpatch", "Detect access to .env files")),
				h.rules());
	}

	@Test
	void crsAlertUsesMetaNamesAndSkipsInternalRules() {
		WafAlertParser.Hit h = WafAlertParser.parseBatch(array(crsSqli(3, "198.51.100.10", AT, "shop.example.test"))).hits().get(0);
		assertFalse(h.inBand());
		assertEquals(SQLI_URI, h.uri());
		assertEquals(List.of("942100", "942190"), h.rules().stream().map(WafAlertParser.Rule::name).toList());
		assertEquals("crs", h.rules().get(0).source());
		assertEquals("crs-sqli", h.rules().get(0).category());
		assertEquals("SQL Injection Attack Detected via libinjection", h.rules().get(0).message());
	}

	@Test
	void withoutMetaNamesFallsBackToEventRulesWithoutCrsInternals() {
		JSONObject a = crsLfi(2, "198.51.100.9", AT, "shop.example.test");
		a.set("meta", meta("method", list("GET")));
		WafAlertParser.Hit h = WafAlertParser.parseBatch(array(a)).hits().get(0);
		assertEquals(List.of("930130"), h.rules().stream().map(WafAlertParser.Rule::name).toList());
		assertEquals("crs-lfi", h.rules().get(0).category());
		// target_uri 不在 meta 時改用 events 的 uri
		assertEquals("/.env", h.uri());
		assertNull(h.userAgent());
	}

	@Test
	void nonWafAlertsAreSkippedButCountedForPagingAndOldest() {
		Instant older = AT.minusSeconds(120);
		WafAlertParser.Batch b = WafAlertParser.parseBatch(array(vpatch(5, "198.51.100.9", AT, "a.test"), scenario(4, "198.51.100.20", older)));
		assertEquals(2, b.size());
		assertEquals(1, b.hits().size());
		assertEquals(older, b.oldest());
	}

	@Test
	void badBodiesGiveEmptyBatches() {
		for (String body : new String[] { null, "", "null", "{}", "[1,\"x\"]" }) {
			WafAlertParser.Batch b = WafAlertParser.parseBatch(body);
			assertTrue(b.hits().isEmpty(), String.valueOf(body));
			assertNull(b.oldest());
		}
	}

	@Test
	void alertsWithoutIdIpTimeOrRulesAreSkipped() {
		JSONObject noIp = vpatch(1, "198.51.100.9", AT, "a.test");
		noIp.set("source", new JSONObject().set("scope", "Ip"));
		JSONObject badTime = vpatch(2, "198.51.100.9", AT, "a.test");
		badTime.set("created_at", "yesterday").set("start_at", "yesterday");
		JSONObject noRules = vpatch(3, "198.51.100.9", AT, "a.test");
		noRules.set("meta", meta("method", list("GET"))).set("events", new cn.hutool.json.JSONArray());
		assertTrue(WafAlertParser.parseBatch(array(noIp, badTime, noRules)).hits().isEmpty());
	}

	@Test
	void longUriIsCut() {
		String uri = "/" + "a".repeat(5000);
		JSONObject a = vpatch(1, "198.51.100.9", AT, "a.test");
		a.set("meta", meta("name", list("crowdsecurity/vpatch-env-access"), "target_uri", list(uri)));
		assertEquals(WafAlertParser.MAX_URI, WafAlertParser.parseBatch(array(a)).hits().get(0).uri().length());
	}

	@Test
	void hostDropsPortAndTrailingDot() {
		assertEquals("shop.example.test", WafAlertParser.hostOf("Shop.Example.Test.:8443"));
		assertEquals("2001:db8::1", WafAlertParser.hostOf("[2001:db8::1]:8443"));
		assertEquals("2001:db8::1", WafAlertParser.hostOf("2001:db8::1"));
		assertEquals("203.0.113.5", WafAlertParser.hostOf("203.0.113.5:80"));
		assertNull(WafAlertParser.hostOf(null));
		assertNull(WafAlertParser.hostOf("  "));
	}

	@Test
	void crsCategoriesFollowTheFirstThreeDigits() {
		Map<String, String> expected = Map.of("911100", "crs-protocol", "913100", "crs-scanner", "920440", "crs-protocol",
				"930130", "crs-lfi", "931100", "crs-rfi", "932100", "crs-rce", "933100", "crs-php", "941100", "crs-xss",
				"942100", "crs-sqli", "950100", "crs-other");
		expected.forEach((rule, cat) -> assertEquals(cat, WafCategories.categoryOf(rule), rule));
		assertEquals("vpatch", WafCategories.categoryOf("crowdsecurity/vpatch-env-access"));
		assertEquals("crs", WafCategories.sourceOf("942100"));
		assertEquals("vpatch", WafCategories.sourceOf("crowdsecurity/vpatch-env-access"));
		assertTrue(WafCategories.ALL.containsAll(expected.values()));
		assertTrue(WafCategories.ALL.contains("vpatch"));
	}
}
