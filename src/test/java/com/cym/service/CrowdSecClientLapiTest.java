package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.testsupport.FakeLapi;
import com.cym.utils.CrowdSecCredentials;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/** CrowdSecClient 對假 LAPI：machine JWT 登入與快取、POST /v1/alerts、DELETE /v1/decisions、bouncer 金鑰讀名單、測試連線。 */
class CrowdSecClientLapiTest {
	private static final String MACHINE = "nginxwebui";
	private static final String PASSWORD = "machine-pass-0123456789";
	private static final String KEY = "bouncer-key-0123456789";

	@TempDir
	Path tmp;

	private FakeLapi lapi;
	private final Map<String, String> env = new HashMap<>();
	private long now = Instant.parse("2026-10-22T09:00:00Z").toEpochMilli();

	@BeforeEach
	void setUp() throws Exception {
		lapi = new FakeLapi(MACHINE, PASSWORD, KEY);
		env.clear();
		env.put("CROWDSEC_LAPI_URL", lapi.url() + "/");
		env.put("CROWDSEC_MACHINE_PASSWORD", PASSWORD);
		env.put("CROWDSEC_BOUNCER_KEY", KEY);
	}

	@AfterEach
	void tearDown() {
		lapi.close();
	}

	private CrowdSecClient client() {
		CrowdSecClient c = new CrowdSecClient();
		c.credentials = new CrowdSecCredentials(env::get, tmp.resolve("no-secrets"));
		c.clock = () -> now;
		return c;
	}

	@Test
	void banRangePostsAlertWithOneDecision() {
		client().banRange("198.51.100.0/24", "24h", "nginxwebui:as-ban:AS64500");

		assertEquals(1, lapi.alertBodies.size());
		JSONArray alerts = JSONUtil.parseArray(lapi.alertBodies.get(0));
		assertEquals(1, alerts.size());
		JSONObject alert = alerts.getJSONObject(0);
		assertEquals("nginxwebui:as-ban:AS64500", alert.getStr("scenario"));
		assertEquals("nginxwebui:as-ban:AS64500", alert.getStr("message"));
		assertEquals("", alert.getStr("scenario_hash"));
		assertEquals("", alert.getStr("scenario_version"));
		assertEquals(1, alert.getInt("events_count"));
		assertEquals(0, alert.getJSONArray("events").size());
		assertEquals(0, alert.getInt("capacity"));
		assertEquals("0", alert.getStr("leakspeed"));
		assertFalse(alert.getBool("simulated"));
		assertTrue(alert.getBool("remediation"));
		assertEquals("2026-10-22T09:00:00Z", alert.getStr("start_at"));
		assertEquals("2026-10-22T09:00:00Z", alert.getStr("stop_at"));
		JSONObject source = alert.getJSONObject("source");
		assertEquals("range", source.getStr("scope"));
		assertEquals("198.51.100.0/24", source.getStr("value"));
		assertEquals("198.51.100.0/24", source.getStr("ip"));
		JSONArray decisions = alert.getJSONArray("decisions");
		assertEquals(1, decisions.size());
		JSONObject d = decisions.getJSONObject(0);
		assertEquals("cscli", d.getStr("origin"));
		assertEquals("ban", d.getStr("type"));
		assertEquals("range", d.getStr("scope"));
		assertEquals("198.51.100.0/24", d.getStr("value"));
		assertEquals("24h", d.getStr("duration"));
		assertEquals("nginxwebui:as-ban:AS64500", d.getStr("scenario"));
	}

	@Test
	void blankReasonUsesDefaultScenario() {
		client().banIp("192.0.2.1", "4h", "  ");

		JSONObject d = JSONUtil.parseArray(lapi.alertBodies.get(0)).getJSONObject(0)
				.getJSONArray("decisions").getJSONObject(0);
		assertEquals("nginxwebui:manual", d.getStr("scenario"));
		assertEquals("ip", d.getStr("scope"));
	}

	@Test
	void tokenIsCachedUntilShortlyBeforeExpiry() {
		lapi.expire = "2026-10-22T10:00:00Z";
		CrowdSecClient c = client();

		c.banIp("192.0.2.1", "4h", "nginxwebui:t");
		now = Instant.parse("2026-10-22T09:58:00Z").toEpochMilli();
		c.banIp("192.0.2.2", "4h", "nginxwebui:t");
		assertEquals(1, lapi.logins.get());

		now = Instant.parse("2026-10-22T09:59:30Z").toEpochMilli();
		c.banIp("192.0.2.3", "4h", "nginxwebui:t");
		assertEquals(2, lapi.logins.get());
		assertEquals(3, lapi.alertBodies.size());
	}

	@Test
	void unauthorizedRetriesOnceWithNewToken() {
		CrowdSecClient c = client();
		c.banIp("192.0.2.1", "4h", "nginxwebui:t");
		lapi.revokeTokens();

		c.banIp("192.0.2.2", "4h", "nginxwebui:t");

		assertEquals(2, lapi.logins.get());
		assertEquals(2, lapi.alertBodies.size());
	}

	@Test
	void wrongPasswordThrowsMachineAuth() {
		env.put("CROWDSEC_MACHINE_PASSWORD", "wrong-password");

		CrowdSecClient.MachineAuthException e = assertThrows(CrowdSecClient.MachineAuthException.class,
				() -> client().banIp("192.0.2.1", "4h", "nginxwebui:t"));

		assertEquals("crowdsec_machine_unauthorized", e.getMessage());
		assertEquals(1, lapi.loginAttempts.get());
		assertTrue(lapi.alertBodies.isEmpty());
	}

	@Test
	void persistentUnauthorizedAfterRelogin() {
		lapi.rejectAllJwt = true;

		CrowdSecClient.MachineAuthException e = assertThrows(CrowdSecClient.MachineAuthException.class,
				() -> client().banIp("192.0.2.1", "4h", "nginxwebui:t"));

		assertEquals("crowdsec_machine_unauthorized", e.getMessage());
		assertEquals(2, lapi.loginAttempts.get());
		assertTrue(lapi.alertBodies.isEmpty());
	}

	@Test
	void deleteByReasonPrefixDedupesWhenLapiIgnoresPaging() {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 1; i <= 150; i++) {
			sb.append(i > 1 ? "," : "").append("{\"id\":").append(i).append(",\"scenario\":\"nginxwebui:as-ban:AS")
					.append(i).append("\"}");
		}
		lapi.decisionsJson = sb.append("]").toString();

		int n = client().deleteDecisionsByReasonPrefix("nginxwebui:as-ban:AS");

		assertEquals(150, n);
		assertEquals(150, lapi.deletes.size());
		assertEquals(150, lapi.deletes.stream().distinct().count());
		assertTrue(lapi.decisionQueries.size() <= 2, lapi.decisionQueries.toString());
	}

	@Test
	void deleteDecisionUsesJwt() {
		client().deleteDecision("42");

		assertEquals(List.of("/v1/decisions/42"), lapi.deletes);
		assertEquals(1, lapi.logins.get());
	}

	@Test
	void deleteDecisionRejectsNonNumericId() {
		assertThrows(IllegalArgumentException.class, () -> client().deleteDecision("1/../2"));
		assertThrows(IllegalArgumentException.class, () -> client().deleteDecision("abc"));
		assertTrue(lapi.deletes.isEmpty());
	}

	@Test
	void deleteByReasonEqualsUsesScenarioFilter() {
		int n = client().deleteDecisionsByReasonEquals("nginxwebui:as-ban:AS64500");

		assertEquals(2, n);
		assertEquals(List.of("/v1/decisions?scenario=nginxwebui:as-ban:AS64500"), lapi.deletes);
	}

	@Test
	void deleteByReasonPrefixListsWithBouncerKeyThenDeletesById() {
		lapi.decisionsJson = "[{\"id\":11,\"scenario\":\"nginxwebui:as-ban:AS1\"},"
				+ "{\"id\":22,\"scenario\":\"crowdsecurity/http-probing\"},"
				+ "{\"id\":33,\"scenario\":\"nginxwebui:as-ban:AS2\"}]";

		int n = client().deleteDecisionsByReasonPrefix("nginxwebui:as-ban:AS");

		assertEquals(2, n);
		assertEquals(List.of("/v1/decisions/11", "/v1/decisions/33"), lapi.deletes);
		assertTrue(lapi.decisionQueries.get(0).contains("scenarios_containing=nginxwebui:as-ban:AS"),
				lapi.decisionQueries.toString());
	}

	@Test
	void loginAndJwtRequestsCarryNginxWebuiUserAgent() {
		lapi.decisionsJson = "[{\"id\":11,\"scenario\":\"nginxwebui:as-ban:AS1\"}]";
		CrowdSecClient c = client();

		c.banIp("192.0.2.1", "4h", "nginxwebui:t");
		c.listAlertsRaw(5);
		c.deleteDecision("11");
		c.deleteDecisionsByReasonEquals("nginxwebui:t");
		c.listDecisionsRaw(10, 0);
		c.deleteDecisionsByReasonPrefix("nginxwebui:as-ban:AS");
		c.probe();

		assertFalse(lapi.loginUserAgents.isEmpty());
		assertFalse(lapi.userAgents.isEmpty());
		for (String ua : lapi.userAgents) {
			assertTrue(ua.matches("^nginxWebUI/\\S+"), "User-Agent: " + ua);
			assertTrue(FakeLapi.VALID_USER_AGENT.matcher(ua).matches(), "User-Agent: " + ua);
		}
	}

	@Test
	void fakeLapiLoginRejectsUserAgentWithoutNameAndVersion() throws Exception {
		for (String ua : new String[] { "Hutool", "Wget", "crowdsecfoo",
				"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.142 Safari/537.36 Hutool" }) {
			try (var resp = cn.hutool.http.HttpRequest.post(lapi.url() + "/v1/watchers/login")
					.header("User-Agent", ua)
					.header("Content-Type", "application/json")
					.body("{\"machine_id\":\"" + MACHINE + "\",\"password\":\"" + PASSWORD + "\"}")
					.execute()) {
				assertEquals(401, resp.getStatus(), ua);
			}
		}
		try (var resp = cn.hutool.http.HttpRequest.post(lapi.url() + "/v1/watchers/login")
				.header("User-Agent", "crowdsec/v1.8.1")
				.header("Content-Type", "application/json")
				.body("{\"machine_id\":\"" + MACHINE + "\",\"password\":\"" + PASSWORD + "\"}")
				.execute()) {
			assertEquals(200, resp.getStatus());
		}
	}

	@Test
	void listDecisionsNeedsBouncerKey() {
		env.remove("CROWDSEC_BOUNCER_KEY");

		assertThrows(IllegalStateException.class, () -> client().listDecisionsRaw(10, 0));
	}

	@Test
	void isConfiguredNeedsLapiUrlAndMachinePassword() {
		assertTrue(client().isConfigured());
		env.remove("CROWDSEC_MACHINE_PASSWORD");
		assertFalse(client().isConfigured());
		env.put("CROWDSEC_MACHINE_PASSWORD", PASSWORD);
		env.remove("CROWDSEC_LAPI_URL");
		assertFalse(client().isConfigured());
	}

	@Test
	void probeAllOk() {
		CrowdSecClient.Probe p = client().probe();

		assertEquals(new CrowdSecClient.Probe("ok", "ok", "ok"), p);
	}

	@Test
	void probeReportsInvalidCredentials() {
		env.put("CROWDSEC_MACHINE_PASSWORD", "wrong-password");
		env.put("CROWDSEC_BOUNCER_KEY", "wrong-key");

		assertEquals(new CrowdSecClient.Probe("ok", "invalid", "invalid"), client().probe());
	}

	@Test
	void loginServerErrorIsNotMachineAuthFailure() {
		for (int status : new int[] { 503, 500, 429 }) {
			lapi.loginStatus = status;

			IllegalStateException e = assertThrows(IllegalStateException.class, () -> client().token());

			assertFalse(e instanceof CrowdSecClient.MachineAuthException, "status " + status);
			assertTrue(e.getMessage().contains(String.valueOf(status)), e.getMessage());
			assertFalse(e.getMessage().contains(PASSWORD), e.getMessage());
			assertFalse(e.getMessage().contains("unavailable"), e.getMessage());
		}
	}

	@Test
	void loginForbiddenIsMachineAuthFailure() {
		lapi.loginStatus = 403;

		assertThrows(CrowdSecClient.MachineAuthException.class, () -> client().token());
	}

	@Test
	void probeReportsMachineUnreachableWhenLoginFailsServerSide() {
		lapi.loginStatus = 503;

		assertEquals(new CrowdSecClient.Probe("ok", "ok", "unreachable"), client().probe());
	}

	@Test
	void probeUnreachable() throws Exception {
		int closedPort;
		try (ServerSocket socket = new ServerSocket(0)) {
			closedPort = socket.getLocalPort();
		}
		env.put("CROWDSEC_LAPI_URL", "http://127.0.0.1:" + closedPort);

		CrowdSecClient.Probe p = client().probe();

		assertEquals("unreachable", p.lapi());
		assertEquals("unreachable", p.machine());
	}

	@Test
	void probeWithoutLapiUrl() {
		env.remove("CROWDSEC_LAPI_URL");

		assertEquals(new CrowdSecClient.Probe("notConfigured", "unchecked", "unchecked"), client().probe());
		env.clear();
		assertEquals(new CrowdSecClient.Probe("notConfigured", "notConfigured", "notConfigured"), client().probe());
	}

	@Test
	void probeMapHasThreeKeys() {
		Map<String, Object> map = new CrowdSecClient.Probe("ok", "invalid", "notConfigured").toMap();

		assertEquals("ok", map.get("lapi"));
		assertEquals("invalid", map.get("bouncerKey"));
		assertEquals("notConfigured", map.get("machine"));
		assertEquals(3, map.size());
	}

	@Test
	void listAlertsUsesJwt() {
		assertEquals("[]", client().listAlertsRaw(5));
		assertEquals(1, lapi.logins.get());
	}
}
