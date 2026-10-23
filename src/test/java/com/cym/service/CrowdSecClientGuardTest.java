package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.testsupport.FakeLapi;
import com.cym.utils.CrowdSecCredentials;

/** CrowdSec 防護頁用到的 LAPI 呼叫：依 IP 查、依來源列封鎖、刪除某 IP 的封鎖、清除舊 whitelist decision。 */
class CrowdSecClientGuardTest {
	private static final String MACHINE = "nginxwebui";
	private static final String PASSWORD = "machine-pass-0123456789";
	private static final String KEY = "bouncer-key-0123456789";

	@TempDir
	Path tmp;
	private FakeLapi lapi;
	private final Map<String, String> env = new HashMap<>();

	@BeforeEach
	void setUp() throws Exception {
		lapi = new FakeLapi(MACHINE, PASSWORD, KEY);
		env.put("CROWDSEC_LAPI_URL", lapi.url());
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
		return c;
	}

	@Test
	void decisionsForIpQueriesByIp() {
		lapi.decisionsJson = "[{\"id\":7,\"type\":\"ban\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\",\"origin\":\"crowdsec\"}]";
		String body = client().decisionsForIpRaw("203.0.113.5");
		assertTrue(body.contains("\"id\":7"));
		assertEquals("ip=203.0.113.5", lapi.decisionQueries.get(0));
	}

	@Test
	void decisionsForIpRejectsCidr() {
		assertThrows(IllegalArgumentException.class, () -> client().decisionsForIpRaw("203.0.113.0/24"));
		assertTrue(lapi.decisionQueries.isEmpty());
	}

	@Test
	void listBansUsesOriginGroups() {
		client().listBansRaw(false);
		client().listBansRaw(true);
		assertEquals("type=ban&origins=" + CrowdSecClient.LOCAL_ORIGINS, lapi.decisionQueries.get(0));
		assertEquals("type=ban&origins=" + CrowdSecClient.COMMUNITY_ORIGINS, lapi.decisionQueries.get(1));
	}

	@Test
	void activeDecisionAlertsUsesJwt() {
		lapi.alertsJson = "[{\"id\":1,\"created_at\":\"2026-10-23T10:00:00Z\",\"decisions\":[{\"id\":9}]}]";
		assertTrue(client().activeDecisionAlertsRaw().contains("\"id\":9"));
		assertEquals("has_active_decision=true&include_capi=false&limit=1000", lapi.alertQueries.get(0));
	}

	@Test
	void deleteIpDecisionsDeletesOnlyExactIpDecisions() {
		lapi.decisionsJson = "[{\"id\":7,\"type\":\"ban\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\"},"
				+ "{\"id\":8,\"type\":\"ban\",\"scope\":\"Range\",\"value\":\"203.0.113.0/24\"},"
				+ "{\"id\":9,\"type\":\"captcha\",\"scope\":\"Ip\",\"value\":\"203.0.113.5\"}]";

		CrowdSecClient.DeleteResult r = client().deleteIpDecisions("203.0.113.5");

		assertEquals(2, r.deleted());
		assertEquals(1, r.rangesKept());
		assertEquals(java.util.List.of("/v1/decisions/7", "/v1/decisions/9"), lapi.deletes);
	}

	@Test
	void deleteLegacyWhitelistDecisionsOnlyTouchesWebuiOnes() {
		lapi.decisionsJson = "[{\"id\":3,\"type\":\"whitelist\",\"scenario\":\"nginxwebui:fp-whitelist\"},"
				+ "{\"id\":4,\"type\":\"whitelist\",\"scenario\":\"manual 'whitelist' from 'localhost'\"}]";

		assertEquals(1, client().deleteLegacyWhitelistDecisions());
		assertEquals("type=whitelist", lapi.decisionQueries.get(0));
		assertEquals(java.util.List.of("/v1/decisions/3"), lapi.deletes);
	}

	@Test
	void deleteIpDecisionsNeedsMachine() {
		env.remove("CROWDSEC_MACHINE_PASSWORD");
		assertThrows(CrowdSecClient.NotConfiguredException.class, () -> client().deleteIpDecisions("203.0.113.5"));
	}
}
