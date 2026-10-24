package com.cym.service;

import static com.cym.testsupport.WafAlertFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import cn.hutool.json.JSONObject;

import com.cym.model.Server;
import com.cym.model.WafEvent;
import com.cym.model.WafEventRule;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.testsupport.InMemorySqlHelper;

/** WAF 事件存入（合併、去重、結果）、查詢（站台、類別、結果、IP、期間、分頁）、最近事件與清理 */
class WafEventServiceTest {
	private static final Instant AT = Instant.parse("2026-10-24T02:50:17Z");
	private static final long NOW = AT.plusSeconds(60).toEpochMilli();
	private static final String SHOP = "shop.example.test:8443";
	private static final String BLOG = "blog.example.test";

	private InMemorySqlHelper sql;
	private WafEventService service;
	private final List<Server> servers = new ArrayList<>();

	@BeforeEach
	void setUp() {
		sql = new InMemorySqlHelper();
		service = new WafEventService();
		service.sqlHelper = sql;
		service.countryOf = ip -> "TW";
		service.distinctHosts = from -> sql.findPropertiesByQuery(
				from == null ? new ConditionAndWrapper().isNotNull("host") : new ConditionAndWrapper().isNotNull("host").gte("eventTime", from),
				WafEvent.class, "host");
		servers.clear();
		server("s-shop", "shop.example.test");
		server("s-blog", "blog.example.test");
	}

	private void server(String id, String name) {
		Server s = new Server();
		s.setId(id);
		s.setServerName(name);
		s.setProxyType(0);
		s.setEnable(true);
		s.setDef(0);
		s.setListen("80");
		servers.add(s);
	}

	private static List<WafAlertParser.Hit> hits(String body) {
		return WafAlertParser.parseBatch(body).hits();
	}

	private WafEventService.Result list(String serverId, String category, String action, String ip, Integer days) {
		return service.list(new WafEventService.Query(serverId, category, action, ip, days, 1, 20), NOW, servers);
	}

	private void seed() {
		service.store(hits(array(vpatch(1, "198.51.100.9", AT, SHOP), crsLfi(2, "198.51.100.9", AT, SHOP),
				crsSqli(3, "198.51.100.10", AT.minusSeconds(30), BLOG))));
	}

	@Test
	void vpatchAndCrsAlertsOfOneRequestBecomeOneBlockedEvent() {
		int added = service.store(hits(array(vpatch(1, "198.51.100.9", AT, SHOP), crsLfi(2, "198.51.100.9", AT, SHOP))));
		assertEquals(1, added);
		List<WafEvent> events = sql.findAll(WafEvent.class);
		assertEquals(1, events.size());
		WafEvent e = events.get(0);
		assertEquals(AT.toEpochMilli(), e.getEventTime());
		assertEquals("198.51.100.9", e.getIp());
		assertEquals("198.51.100.9", e.getIpKey());
		assertEquals("TW", e.getCountry());
		assertEquals("shop.example.test", e.getHost());
		assertEquals("GET", e.getMethod());
		assertEquals("/.env", e.getUri());
		assertEquals(UA, e.getUserAgent());
		assertEquals("crs,vpatch", e.getRuleSource());
		assertEquals(",vpatch,crs-lfi,", e.getCategories());
		assertEquals(WafEventService.ACTION_BLOCK, e.getAction());
		assertEquals("Detect access to .env files", e.getMessage());
		List<WafEventRule> rules = sql.findAll(WafEventRule.class);
		assertEquals(List.of("crowdsecurity/vpatch-env-access", "930130"), rules.stream().map(WafEventRule::getRuleName).toList());
		long sec = AT.getEpochSecond();
		assertEquals(List.of("1@" + sec, "2@" + sec), rules.stream().map(WafEventRule::getAlertId).toList());
		assertTrue(rules.stream().allMatch(r -> e.getId().equals(r.getEventId()) && AT.toEpochMilli() == r.getEventTime()));
	}

	@Test
	void crsOnlyEventIsLogged() {
		service.store(hits(array(crsSqli(3, "198.51.100.10", AT, BLOG))));
		assertEquals(WafEventService.ACTION_LOG, sql.findAll(WafEvent.class).get(0).getAction());
	}

	@Test
	void alertsAlreadyStoredAreSkippedAcrossCallsAndWithinOneCall() {
		seed();
		assertEquals(0, service.store(hits(array(vpatch(1, "198.51.100.9", AT, SHOP), crsSqli(3, "198.51.100.10", AT.minusSeconds(30), BLOG)))));
		assertEquals(0, service.store(hits(array(crsLfi(9, "198.51.100.9", AT, SHOP), crsLfi(9, "198.51.100.9", AT, SHOP)))));
		assertEquals(2, sql.findAll(WafEvent.class).size());
		// seed 有 4 筆規則；alert 9 併進既有的 /.env 事件，同一批重複的只算一次，多 1 筆
		assertEquals(5, sql.findAll(WafEventRule.class).size());
	}

	@Test
	void laterAlertOfTheSameRequestMergesIntoTheStoredEvent() {
		service.store(hits(array(crsLfi(2, "198.51.100.9", AT, SHOP))));
		assertEquals(WafEventService.ACTION_LOG, sql.findAll(WafEvent.class).get(0).getAction());
		assertEquals(0, service.store(hits(array(vpatch(1, "198.51.100.9", AT, SHOP)))));
		WafEvent e = sql.findAll(WafEvent.class).get(0);
		assertEquals(WafEventService.ACTION_BLOCK, e.getAction());
		assertEquals("crs,vpatch", e.getRuleSource());
		assertEquals(",crs-lfi,vpatch,", e.getCategories());
	}

	@Test
	void listIsNewestFirstWithRules() {
		seed();
		WafEventService.Result r = list(null, null, null, null, null);
		assertEquals(2, r.count());
		assertEquals(List.of("198.51.100.9", "198.51.100.10"), r.rows().stream().map(x -> x.event().getIp()).toList());
		assertEquals(List.of("crowdsecurity/vpatch-env-access", "930130"),
				r.rows().get(0).rules().stream().map(WafEventRule::getRuleName).toList());
		assertEquals(List.of("942100", "942190"), r.rows().get(1).rules().stream().map(WafEventRule::getRuleName).toList());
	}

	@Test
	void filtersByCategoryActionAndIp() {
		seed();
		assertEquals(List.of("198.51.100.10"), ips(list(null, "crs-sqli", null, null, null)));
		assertEquals(List.of("198.51.100.9"), ips(list(null, "vpatch", null, null, null)));
		assertEquals(List.of("198.51.100.10"), ips(list(null, null, "log", null, null)));
		assertEquals(List.of("198.51.100.9"), ips(list(null, null, "block", null, null)));
		assertEquals(List.of("198.51.100.9"), ips(list(null, null, null, "198.51.100.9", null)));
		assertEquals(2, list(null, null, null, "198.51.100", null).count());
	}

	@Test
	void ipv6SearchMatchesAnySpelling() {
		service.store(hits(array(vpatch(1, "2001:db8::5", AT, SHOP))));
		assertEquals(1, list(null, null, null, "2001:0db8:0:0:0:0:0:5", null).count());
	}

	@Test
	void filtersBySiteWithNginxServerSelection() {
		seed();
		assertEquals(List.of("198.51.100.9"), ips(list("s-shop", null, null, null, null)));
		assertEquals(List.of("198.51.100.10"), ips(list("s-blog", null, null, null, null)));
		assertEquals(0, list("missing", null, null, null, null).count());
	}

	@Test
	void filtersByDays() {
		seed();
		service.store(hits(array(vpatch(7, "198.51.100.30", AT.minusSeconds(3 * 86400), SHOP))));
		assertEquals(3, list(null, null, null, null, 7).count());
		assertEquals(2, list(null, null, null, null, 1).count());
		assertEquals(3, list(null, null, null, null, 0).count());
	}

	@Test
	void pagesAreCut() {
		for (int i = 0; i < 25; i++) {
			service.store(hits(array(vpatch(100 + i, "198.51.100." + (i + 1), AT.minusSeconds(i), SHOP))));
		}
		WafEventService.Result p2 = service.list(new WafEventService.Query(null, null, null, null, null, 2, 20), NOW, servers);
		assertEquals(25, p2.count());
		assertEquals(5, p2.rows().size());
		assertEquals("198.51.100.21", p2.rows().get(0).event().getIp());
	}

	@Test
	void recentForIpGivesTheLatestEventsOfThatIp() {
		seed();
		service.store(hits(array(crsSqli(4, "198.51.100.9", AT.minusSeconds(10), SHOP))));
		List<WafEventService.Row> rows = service.recentForIp("198.51.100.9", 1);
		assertEquals(1, rows.size());
		assertEquals("/.env", rows.get(0).event().getUri());
		assertEquals(2, service.recentForIp("198.51.100.9", 10).size());
		assertTrue(service.recentForIp("not-an-ip", 10).isEmpty());
	}

	@Test
	void cleanupRemovesOldEventsAndTheirRules() {
		seed();
		service.store(hits(array(vpatch(7, "198.51.100.30", AT.minusSeconds(40 * 86400), SHOP))));
		assertEquals(1, service.cleanup(AT.minusSeconds(30 * 86400).toEpochMilli()));
		assertEquals(2, sql.findAll(WafEvent.class).size());
		assertTrue(sql.findAll(WafEventRule.class).stream().noneMatch(r -> r.getAlertId().startsWith("7@")));
	}

	@Test
	void sameAlertIdWithAnotherCreationTimeIsNew() {
		// CrowdSec 資料庫重建後 alert id 從 1 重來
		assertEquals(1, service.store(hits(array(vpatch(1, "198.51.100.9", AT, SHOP)))));
		assertEquals(0, service.store(hits(array(vpatch(1, "198.51.100.9", AT, SHOP)))));
		assertEquals(1, service.store(hits(array(vpatch(1, "198.51.100.9", AT.plusSeconds(3600), SHOP)))));
		assertEquals(2, sql.findAll(WafEvent.class).size());
		assertEquals(List.of("1@" + AT.getEpochSecond(), "1@" + AT.plusSeconds(3600).getEpochSecond()),
				sql.findAll(WafEventRule.class).stream().map(WafEventRule::getAlertId).toList());
	}

	/** 超過一個分塊（500）的批次：已存過的 alert 與既有事件都要查得到 */
	@Test
	void storesMoreThanOneChunkAndSkipsThemAgain() {
		JSONObject[] alerts = new JSONObject[600];
		for (int i = 0; i < alerts.length; i++) {
			alerts[i] = vpatch(1000 + i, "198.51.100.9", AT.minusSeconds(i), SHOP);
		}
		assertEquals(600, service.store(hits(array(alerts))));
		assertEquals(0, service.store(hits(array(alerts))));
		assertEquals(600, sql.findAll(WafEvent.class).size());
		assertEquals(600, sql.findAll(WafEventRule.class).size());
		// 同一批請求再來一個不同 alert：併進既有事件，不新增
		JSONObject[] again = new JSONObject[600];
		for (int i = 0; i < again.length; i++) {
			again[i] = crsLfi(5000 + i, "198.51.100.9", AT.minusSeconds(i), SHOP);
		}
		assertEquals(0, service.store(hits(array(again))));
		assertEquals(600, sql.findAll(WafEvent.class).size());
		assertEquals(1200, sql.findAll(WafEventRule.class).size());
	}

	/** 站台的 host 超過一個分塊時以 OR 串起各塊的 IN */
	@Test
	void siteFilterWorksWithMoreHostsThanOneChunk() {
		server("s-wild", "*.wild.test");
		JSONObject[] alerts = new JSONObject[600];
		for (int i = 0; i < alerts.length; i++) {
			alerts[i] = vpatch(1000 + i, "198.51.100.9", AT.minusSeconds(i), "h" + i + ".wild.test");
		}
		service.store(hits(array(alerts)));
		seed();
		assertEquals(600, list("s-wild", null, null, null, null).count());
		assertEquals(1, list("s-blog", null, null, null, null).count());
	}

	@Test
	void siteHostsOnlyComeFromTheRequestedPeriod() {
		service.store(hits(array(vpatch(1, "198.51.100.9", AT.minusSeconds(5 * 86400), "old.wild.test"))));
		server("s-wild", "*.wild.test");
		assertEquals(1, list("s-wild", null, null, null, 30).count());
		assertEquals(0, list("s-wild", null, null, null, 1).count());
	}

	@Test
	void nestedOrConditionsAreEvaluatedByTheInMemoryHelper() {
		seed();
		com.cym.sqlhelper.utils.ConditionOrWrapper any = new com.cym.sqlhelper.utils.ConditionOrWrapper();
		any.or(new com.cym.sqlhelper.utils.ConditionAndWrapper().in("ip", List.of("198.51.100.10")));
		any.or(new com.cym.sqlhelper.utils.ConditionAndWrapper().in("ip", List.of("203.0.113.1")));
		assertEquals(1L, sql.findCountByQuery(new com.cym.sqlhelper.utils.ConditionAndWrapper().and(any), WafEvent.class));
		assertEquals(0L, sql.findCountByQuery(new com.cym.sqlhelper.utils.ConditionAndWrapper().eq("action", "log").and(
				new com.cym.sqlhelper.utils.ConditionOrWrapper().or(new com.cym.sqlhelper.utils.ConditionAndWrapper().in("ip", List.of("198.51.100.9")))), WafEvent.class));
	}

	@Test
	void daysAndPageAreClamped() {
		seed();
		// days 最多回溯 3650 天
		service.store(hits(array(vpatch(8, "198.51.100.40", AT.minusSeconds(4000L * 86400), SHOP))));
		assertEquals(3, list(null, null, null, null, 0).count());
		assertEquals(2, list(null, null, null, null, Integer.MAX_VALUE).count());
		WafEventService.Result r = service.list(new WafEventService.Query(null, null, null, null, null, Integer.MAX_VALUE, 20), NOW, servers);
		assertEquals(0, r.rows().size());
	}

	private static List<String> ips(WafEventService.Result r) {
		return r.rows().stream().map(x -> x.event().getIp()).toList();
	}
}
