package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.cym.model.WafRuleSetting;
import com.cym.service.WafRuleCatalog.Catalog;
import com.cym.service.WafRuleCatalog.Rule;

class CrowdSecWafControllerTest {
	static final Catalog CAT = new Catalog(List.of(
			new Rule("crowdsecurity/vpatch-env-access", "vpatch", "vpatch:_other", "Generic .env access", "d", List.of()),
			new Rule("942100", "crs", "crs-sqli", "SQLi", "", List.of()),
			new Rule("942200", "crs", "crs-sqli", "SQLi 2", "", List.of())), "t", "v1.8.1");

	static WafRuleSetting s(String scope, String key, String action, String excluded) {
		WafRuleSetting w = new WafRuleSetting();
		w.setScope(scope);
		w.setRuleKey(key);
		w.setAction(action);
		w.setExcludedServerIds(excluded);
		return w;
	}

	@SuppressWarnings("unchecked")
	@Test
	void rulesViewCountsHitsAndShowsEffectiveActions() {
		Map<String, Object> v = CrowdSecWafController.rulesView(CAT,
				List.of(s("category", "crs-sqli", "log", "s1"), s("rule", "942200", "off", ""), s("rule", "crowdsecurity/vpatch-env-access", "inherit", "")),
				Map.of("942100", 3, "942200", 1), Set.of("s1"));
		List<Map<String, Object>> rules = (List<Map<String, Object>>) v.get("rules");
		List<Map<String, Object>> cats = (List<Map<String, Object>>) v.get("categories");
		assertEquals(List.of("vpatch:_other", "crs-sqli"), cats.stream().map(c -> c.get("key")).toList());
		Map<String, Object> sqli = cats.get(1);
		assertEquals(2, sqli.get("ruleCount"));
		assertEquals(4, sqli.get("hits"));
		assertEquals("log", sqli.get("action"));
		assertEquals("log", sqli.get("setting"));
		assertEquals(List.of("s1"), sqli.get("excludedServerIds"));
		Map<String, Object> vp = cats.get(0);
		assertEquals("block", vp.get("action"));
		assertNull(vp.get("setting"));
		Map<String, Object> r942100 = rules.stream().filter(r -> "942100".equals(r.get("key"))).findFirst().orElseThrow();
		assertEquals("log", r942100.get("action"));
		assertNull(r942100.get("setting"));
		assertEquals(3, r942100.get("hits"));
		Map<String, Object> r942200 = rules.stream().filter(r -> "942200".equals(r.get("key"))).findFirst().orElseThrow();
		assertEquals("off", r942200.get("setting"));
		Map<String, Object> env = rules.stream().filter(r -> "crowdsecurity/vpatch-env-access".equals(r.get("key"))).findFirst().orElseThrow();
		assertNull(env.get("setting"));
		assertEquals(0, env.get("hits"));
	}

	@SuppressWarnings("unchecked")
	@Test
	void rulesViewDropsDeletedServersFromTheExclusions() {
		Map<String, Object> v = CrowdSecWafController.rulesView(CAT,
				List.of(s("category", "crs-sqli", "log", "s1,gone"), s("rule", "942200", "log", "gone,s2")), Map.of(), Set.of("s1", "s2"));
		List<Map<String, Object>> rules = (List<Map<String, Object>>) v.get("rules");
		List<Map<String, Object>> cats = (List<Map<String, Object>>) v.get("categories");
		Map<String, Object> sqli = cats.stream().filter(c -> "crs-sqli".equals(c.get("key"))).findFirst().orElseThrow();
		assertEquals(List.of("s1"), sqli.get("excludedServerIds"));
		Map<String, Object> r942200 = rules.stream().filter(r -> "942200".equals(r.get("key"))).findFirst().orElseThrow();
		assertEquals(List.of("s2"), r942200.get("excludedServerIds"));
	}

	@Test
	void parseChangesReadsTheJsonArray() {
		List<WafRuleSetting> list = CrowdSecWafController.parseChanges(
				"[{\"scope\":\"rule\",\"ruleKey\":\"942100\",\"action\":\"off\",\"excludedServerIds\":\"a,b\"}]");
		assertEquals(1, list.size());
		assertEquals("942100", list.get(0).getRuleKey());
		assertEquals("a,b", list.get(0).getExcludedServerIds());
		assertThrows(RuntimeException.class, () -> CrowdSecWafController.parseChanges("{"));
		assertThrows(RuntimeException.class, () -> CrowdSecWafController.parseChanges(null));
	}
}
