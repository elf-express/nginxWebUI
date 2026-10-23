package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.utils.NetGuard;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * Pure static helpers for CrowdSecClient — no live LAPI / Solon.
 */
public class CrowdSecClientTest {

	@Test
	public void requireCidr_rejectsBad() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> CrowdSecClient.requireCidr("not-cidr"));
		assertEquals(NetGuard.ERR_INVALID_CIDR, ex.getMessage());
		assertThrows(IllegalArgumentException.class, () -> CrowdSecClient.requireCidr(""));
		assertThrows(IllegalArgumentException.class, () -> CrowdSecClient.requireCidr("all"));
		assertThrows(IllegalArgumentException.class, () -> CrowdSecClient.requireCidr("evil.com"));
	}

	@Test
	public void requireCidr_acceptsIpAndCidr() {
		assertDoesNotThrow(() -> CrowdSecClient.requireCidr("10.0.0.1"));
		assertDoesNotThrow(() -> CrowdSecClient.requireCidr("1.2.3.0/24"));
		assertDoesNotThrow(() -> CrowdSecClient.requireCidr("2001:db8::1"));
	}

	@Test
	public void requireDuration_rejectsBad() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> CrowdSecClient.requireDuration(""));
		assertEquals(NetGuard.ERR_INVALID_DURATION, ex.getMessage());
		assertThrows(IllegalArgumentException.class, () -> CrowdSecClient.requireDuration(null));
		assertThrows(IllegalArgumentException.class, () -> CrowdSecClient.requireDuration("forever"));
		assertThrows(IllegalArgumentException.class, () -> CrowdSecClient.requireDuration("-1h"));
	}

	@Test
	public void requireDuration_acceptsValid() {
		assertDoesNotThrow(() -> CrowdSecClient.requireDuration("24h"));
		assertDoesNotThrow(() -> CrowdSecClient.requireDuration("30m"));
		assertDoesNotThrow(() -> CrowdSecClient.requireDuration("7d"));
	}

	@Test
	public void requireWebuiReason_rejectsBad() {
		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
				() -> CrowdSecClient.requireWebuiReason("manual"));
		assertEquals(NetGuard.ERR_INVALID_REASON, ex.getMessage());
		assertThrows(IllegalArgumentException.class, () -> CrowdSecClient.requireWebuiReason(""));
		assertThrows(IllegalArgumentException.class, () -> CrowdSecClient.requireWebuiReason(null));
		assertThrows(IllegalArgumentException.class, () -> CrowdSecClient.requireWebuiReason("other:prefix"));
	}

	@Test
	public void requireWebuiReason_acceptsPrefix() {
		assertDoesNotThrow(() -> CrowdSecClient.requireWebuiReason("nginxwebui:as-ban:AS51167"));
		assertDoesNotThrow(() -> CrowdSecClient.requireWebuiReason("nginxwebui:manual"));
		assertDoesNotThrow(() -> CrowdSecClient.requireWebuiReason("  nginxwebui:fp-whitelist  "));
	}

	/** Ensures postDecision call-site cannot drop gates without breaking this test. */
	@Test
	public void validatePostDecisionInputs_boundToPostPath() {
		assertDoesNotThrow(() -> CrowdSecClient.validatePostDecisionInputs("24h", "range", "1.2.3.0/24"));
		assertDoesNotThrow(() -> CrowdSecClient.validatePostDecisionInputs("4h", "ip", "10.0.0.1"));
		IllegalArgumentException badCidr = assertThrows(IllegalArgumentException.class,
				() -> CrowdSecClient.validatePostDecisionInputs("24h", "range", "not-a-cidr"));
		assertEquals(NetGuard.ERR_INVALID_CIDR, badCidr.getMessage());
		IllegalArgumentException badDur = assertThrows(IllegalArgumentException.class,
				() -> CrowdSecClient.validatePostDecisionInputs("forever", "ip", "1.1.1.1"));
		assertEquals(NetGuard.ERR_INVALID_DURATION, badDur.getMessage());
	}

	@Test
	public void buildAlertBody_isArrayWithOneAlert() {
		String json = CrowdSecClient.buildAlertBody("24h", "nginxwebui:as-ban:AS51167", "range", "1.2.3.0/24", "ban",
				Instant.parse("2026-10-22T09:00:00.123Z"));
		assertTrue(JSONUtil.isTypeJSONArray(json));
		JSONObject alert = JSONUtil.parseArray(json).getJSONObject(0);
		assertEquals("2026-10-22T09:00:00Z", alert.getStr("start_at"));
		JSONObject d = alert.getJSONArray("decisions").getJSONObject(0);
		assertEquals("24h", d.getStr("duration"));
		assertEquals("nginxwebui:as-ban:AS51167", d.getStr("scenario"));
		assertEquals("range", d.getStr("scope"));
		assertEquals("1.2.3.0/24", d.getStr("value"));
		assertEquals("ban", d.getStr("type"));
	}

	@Test
	public void buildAlertBody_escapesQuotesInReason() {
		String json = CrowdSecClient.buildAlertBody("4h", "say \"hi\" \\and\\", "ip", "10.0.0.1", "ban", Instant.EPOCH);
		JSONObject alert = JSONUtil.parseArray(json).getJSONObject(0);
		String expected = "nginxwebui:manual:say \"hi\" \\and\\";
		assertEquals(expected, alert.getStr("scenario"));
		assertEquals(expected, alert.getJSONArray("decisions").getJSONObject(0).getStr("scenario"));
	}

	@Test
	public void buildAlertBody_blankReasonUsesDefaultScenario() {
		for (String reason : new String[] { null, "", "   " }) {
			String json = CrowdSecClient.buildAlertBody("4h", reason, "ip", "10.0.0.1", "ban", Instant.EPOCH);
			JSONObject alert = JSONUtil.parseArray(json).getJSONObject(0);
			assertEquals("nginxwebui:manual", alert.getStr("scenario"));
			assertEquals("nginxwebui:manual", alert.getJSONArray("decisions").getJSONObject(0).getStr("scenario"));
		}
	}

	@Test
	public void buildAlertBody_keepsWebuiPrefixedReason() {
		String json = CrowdSecClient.buildAlertBody("4h", "  nginxwebui:as-ban:AS1  ", "range", "10.0.0.0/8", "ban",
				Instant.EPOCH);
		JSONObject alert = JSONUtil.parseArray(json).getJSONObject(0);
		assertEquals("nginxwebui:as-ban:AS1", alert.getStr("scenario"));
		assertEquals("nginxwebui:as-ban:AS1", alert.getJSONArray("decisions").getJSONObject(0).getStr("scenario"));
	}

	@Test
	public void buildAlertBody_prefixesCustomReason() {
		String json = CrowdSecClient.buildAlertBody("4h", " spam bot ", "ip", "10.0.0.1", "ban", Instant.EPOCH);
		JSONObject alert = JSONUtil.parseArray(json).getJSONObject(0);
		assertEquals("nginxwebui:manual:spam bot", alert.getStr("scenario"));
		assertEquals("nginxwebui:manual:spam bot", alert.getStr("message"));
		assertEquals("nginxwebui:manual:spam bot",
				alert.getJSONArray("decisions").getJSONObject(0).getStr("scenario"));
	}

	@Test
	public void buildAlertBody_whitelistType() {
		String json = CrowdSecClient.buildAlertBody("1h", "nginxwebui:fp-whitelist", "ip", "8.8.8.8", "whitelist", Instant.EPOCH);
		JSONObject d = JSONUtil.parseArray(json).getJSONObject(0).getJSONArray("decisions").getJSONObject(0);
		assertEquals("whitelist", d.getStr("type"));
		assertEquals("ip", d.getStr("scope"));
	}

	@Test
	public void parseExpire_readsRfc3339AndFallsBack() {
		assertEquals(Instant.parse("2026-10-22T10:00:00Z").toEpochMilli(),
				CrowdSecClient.parseExpire("2026-10-22T10:00:00Z", 0));
		assertEquals(Instant.parse("2026-10-22T02:00:00Z").toEpochMilli(),
				CrowdSecClient.parseExpire("2026-10-22T10:00:00+08:00", 0));
		assertEquals(1000 + CrowdSecClient.TOKEN_FALLBACK_TTL_MS, CrowdSecClient.parseExpire("garbage", 1000));
		assertEquals(1000 + CrowdSecClient.TOKEN_FALLBACK_TTL_MS, CrowdSecClient.parseExpire(null, 1000));
	}

	@Test
	public void parseDeleted_readsStringCount() {
		assertEquals(3, CrowdSecClient.parseDeleted("{\"nbDeleted\":\"3\"}"));
		assertEquals(0, CrowdSecClient.parseDeleted("not json"));
		assertEquals(0, CrowdSecClient.parseDeleted(null));
	}

	@Test
	public void matchDecisionIds_prefix() {
		String body = "["
				+ "{\"id\":11,\"scenario\":\"nginxwebui:as-ban:AS1\"},"
				+ "{\"id\":22,\"scenario\":\"nginxwebui:as-ban:AS2\"},"
				+ "{\"id\":33,\"scenario\":\"other\"}"
				+ "]";
		List<String> ids = CrowdSecClient.matchDecisionIds(body, "nginxwebui:as-ban:", null, false);
		assertEquals(2, ids.size());
		assertTrue(ids.contains("11"));
		assertTrue(ids.contains("22"));
		assertFalse(ids.contains("33"));
	}

	@Test
	public void matchDecisionIds_equalsOnly() {
		String body = "["
				+ "{\"id\":11,\"scenario\":\"nginxwebui:as-ban:AS1\"},"
				+ "{\"id\":22,\"scenario\":\"nginxwebui:as-ban:AS11\"}"
				+ "]";
		List<String> ids = CrowdSecClient.matchDecisionIds(body, null, "nginxwebui:as-ban:AS1", true);
		assertEquals(1, ids.size());
		assertEquals("11", ids.get(0));
	}

	@Test
	public void matchDecisionIds_emptyOrNullBody() {
		assertTrue(CrowdSecClient.matchDecisionIds(null, "x", null, false).isEmpty());
		assertTrue(CrowdSecClient.matchDecisionIds("", "x", null, false).isEmpty());
		assertTrue(CrowdSecClient.matchDecisionIds("null", "x", null, false).isEmpty());
		assertTrue(CrowdSecClient.matchDecisionIds("{}", "x", null, false).isEmpty());
	}
}
