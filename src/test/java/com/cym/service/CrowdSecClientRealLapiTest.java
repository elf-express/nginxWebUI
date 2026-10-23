package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.utils.CrowdSecCredentials;

/**
 * 對真實 CrowdSec LAPI 的冒煙測試，預設略過。
 * 設定環境變數 {@code NWU_TEST_CROWDSEC_LAPI_URL} 與 {@code NWU_TEST_CROWDSEC_MACHINE_PASSWORD}
 * （machine 帳號 nginxwebui）才會執行：確認真實 LAPI 接受 CrowdSecClient 的登入（User-Agent）、寫入與刪除。
 */
class CrowdSecClientRealLapiTest {
	@TempDir
	Path tmp;

	@Test
	void realLapiAcceptsLoginAlertAndDelete() {
		String url = System.getenv("NWU_TEST_CROWDSEC_LAPI_URL");
		String password = System.getenv("NWU_TEST_CROWDSEC_MACHINE_PASSWORD");
		assumeTrue(url != null && !url.isBlank() && password != null && !password.isBlank(),
				"NWU_TEST_CROWDSEC_LAPI_URL / NWU_TEST_CROWDSEC_MACHINE_PASSWORD not set");

		Map<String, String> env = new HashMap<>();
		env.put("CROWDSEC_LAPI_URL", url);
		env.put("CROWDSEC_MACHINE_PASSWORD", password);
		CrowdSecClient c = new CrowdSecClient();
		c.credentials = new CrowdSecCredentials(env::get, tmp.resolve("no-secrets"));

		assertEquals(CrowdSecClient.OK, c.probe().machine());
		c.banIp("192.0.2.77", "5m", "nginxwebui:e390-ua-check");
		assertTrue(c.listAlertsRaw(10).contains("nginxwebui:e390-ua-check"));
		assertTrue(c.deleteDecisionsByReasonEquals("nginxwebui:e390-ua-check") >= 1);
	}
}
