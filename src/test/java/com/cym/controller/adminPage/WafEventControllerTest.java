package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cym.model.WafEvent;
import com.cym.model.WafEventRule;
import com.cym.service.WafEventService;

/** 給畫面的事件列（欄位與規則）、篩選值只接受已知的類別與結果 */
class WafEventControllerTest {

	@Test
	@SuppressWarnings("unchecked")
	void rowsCarryEventFieldsAndRulesButNotInternalKeys() {
		WafEvent e = new WafEvent();
		e.setId("10");
		e.setEventKey("198.51.100.9|GET|/.env|1792810217");
		e.setEventTime(1792810217000L);
		e.setIp("198.51.100.9");
		e.setIpKey("198.51.100.9");
		e.setCountry("TW");
		e.setHost("shop.example.test");
		e.setMethod("GET");
		e.setUri("/.env");
		e.setUserAgent("Mozilla/5.0");
		e.setRuleSource("crs,vpatch");
		e.setCategories(",vpatch,crs-lfi,");
		e.setAction("block");
		e.setMessage("Detect access to .env files");
		WafEventRule r = new WafEventRule();
		r.setEventId("10");
		r.setAlertId("1");
		r.setRuleName("crowdsecurity/vpatch-env-access");
		r.setRuleSource("vpatch");
		r.setCategory("vpatch");
		r.setMessage("Detect access to .env files");

		List<Map<String, Object>> rows = WafEventController.rows(List.of(new WafEventService.Row(e, List.of(r))));
		Map<String, Object> row = rows.get(0);
		assertEquals(List.of("id", "eventTime", "ip", "country", "host", "method", "uri", "userAgent", "ruleSource", "action",
				"message", "rules"), new ArrayList<>(row.keySet()));
		assertEquals(1792810217000L, row.get("eventTime"));
		assertEquals("block", row.get("action"));
		List<Map<String, Object>> rules = (List<Map<String, Object>>) row.get("rules");
		assertEquals(List.of("ruleName", "ruleSource", "category", "message"), new ArrayList<>(rules.get(0).keySet()));
		assertEquals("crowdsecurity/vpatch-env-access", rules.get(0).get("ruleName"));
	}

	@Test
	void onlyKnownCategoriesAndActionsBecomeFilters() {
		assertEquals("crs-sqli", WafEventController.knownCategory(" crs-sqli "));
		assertEquals("vpatch", WafEventController.knownCategory("vpatch"));
		assertNull(WafEventController.knownCategory("%"));
		assertNull(WafEventController.knownCategory(""));
		assertNull(WafEventController.knownCategory(null));
		assertEquals("log", WafEventController.knownAction(" log "));
		assertEquals("block", WafEventController.knownAction("block"));
		assertNull(WafEventController.knownAction("drop"));
		assertNull(WafEventController.knownAction(null));
	}
}
