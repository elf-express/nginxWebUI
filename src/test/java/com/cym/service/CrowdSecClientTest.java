package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * Pure static helpers for CrowdSecClient — no live LAPI / Solon.
 */
public class CrowdSecClientTest {

	@Test
	public void buildDecisionBody_basicFields() {
		String json = CrowdSecClient.buildDecisionBody("24h", "nginxwebui:as-ban:AS51167", "range",
				"1.2.3.0/24", "ban");
		JSONObject o = JSONUtil.parseObj(json);
		assertEquals("24h", o.getStr("duration"));
		assertEquals("nginxwebui:as-ban:AS51167", o.getStr("reason"));
		assertEquals("range", o.getStr("scope"));
		assertEquals("1.2.3.0/24", o.getStr("value"));
		assertEquals("ban", o.getStr("type"));
	}

	@Test
	public void buildDecisionBody_escapesQuotesInReason() {
		String json = CrowdSecClient.buildDecisionBody("4h", "say \"hi\" \\and\\", "ip", "10.0.0.1", "ban");
		// Must be valid JSON and round-trip the reason verbatim
		JSONObject o = JSONUtil.parseObj(json);
		assertEquals("say \"hi\" \\and\\", o.getStr("reason"));
		assertEquals("10.0.0.1", o.getStr("value"));
		assertEquals("ip", o.getStr("scope"));
		// raw string must not embed unescaped " that would break JSON
		assertFalse(json.contains("\"reason\":\"say \"hi\""));
	}

	@Test
	public void buildDecisionBody_whitelistType() {
		String json = CrowdSecClient.buildDecisionBody("1h", "false-positive", "ip", "8.8.8.8", "whitelist");
		JSONObject o = JSONUtil.parseObj(json);
		assertEquals("whitelist", o.getStr("type"));
		assertEquals("ip", o.getStr("scope"));
	}

	@Test
	public void matchDecisionIds_prefix() {
		String body = "["
				+ "{\"id\":11,\"reason\":\"nginxwebui:as-ban:AS1\"},"
				+ "{\"id\":22,\"reason\":\"nginxwebui:as-ban:AS2\"},"
				+ "{\"id\":33,\"reason\":\"other\"}"
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
				+ "{\"id\":11,\"reason\":\"nginxwebui:as-ban:AS1\"},"
				+ "{\"id\":22,\"reason\":\"nginxwebui:as-ban:AS11\"}"
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
