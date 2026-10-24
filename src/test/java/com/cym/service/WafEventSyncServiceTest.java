package com.cym.service;

import static com.cym.testsupport.WafAlertFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.cym.model.WafEvent;
import com.cym.testsupport.FakeLapi;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.utils.CrowdSecCredentials;

import cn.hutool.json.JSONObject;

/** WAF 事件同步：第一次與之後的時間區間、alert id 去重、滿頁往前翻、失敗後補拉、錯誤碼、缺口判斷、保留天數與清理 */
class WafEventSyncServiceTest {
	private static final String MACHINE = "nginxwebui";
	private static final String PASSWORD = "machine-pass-0123456789";
	private static final String KEY = "bouncer-key-0123456789";
	private static final String SHOP = "shop.example.test";
	private static final Instant BASE = Instant.parse("2026-10-24T03:00:00Z");
	private static final long MIN = 60_000L;
	private static final long DAY = 86_400_000L;

	@TempDir
	Path tmp;

	private FakeLapi lapi;
	private final Map<String, String> env = new HashMap<>();
	private InMemorySqlHelper sql;
	private SettingService settings;
	private WafEventService events;
	private long now = BASE.toEpochMilli();

	@BeforeEach
	void setUp() throws Exception {
		lapi = new FakeLapi(MACHINE, PASSWORD, KEY);
		env.clear();
		env.put("CROWDSEC_LAPI_URL", lapi.url());
		env.put("CROWDSEC_MACHINE_PASSWORD", PASSWORD);
		env.put("CROWDSEC_BOUNCER_KEY", KEY);
		sql = new InMemorySqlHelper();
		settings = new SettingService();
		settings.sqlHelper = sql;
		events = new WafEventService();
		events.sqlHelper = sql;
		events.countryOf = ip -> "TW";
		events.distinctHosts = from -> new ArrayList<>();
	}

	@AfterEach
	void tearDown() {
		lapi.close();
	}

	private WafEventSyncService sync() {
		CrowdSecClient c = new CrowdSecClient();
		c.credentials = new CrowdSecCredentials(env::get, tmp.resolve("no-secrets"));
		c.clock = () -> now;
		WafEventSyncService s = new WafEventSyncService();
		s.clock = () -> now;
		s.settingService = settings;
		s.crowdSecClient = c;
		s.wafEventService = events;
		return s;
	}

	private Instant ago(long ms) {
		return Instant.ofEpochMilli(now - ms);
	}

	private Long setting(String key) {
		String v = settings.get(key);
		return v == null ? null : Long.valueOf(v);
	}

	@Test
	void withoutMachineCredentialsNothingIsRequested() {
		env.remove("CROWDSEC_MACHINE_PASSWORD");
		WafEventSyncService s = sync();
		assertEquals(WafEventSyncService.NOT_CONFIGURED, s.sync(now).outcome());
		assertTrue(lapi.alertQueries.isEmpty());
		assertFalse(s.status(now).configured());
	}

	@Test
	void firstSyncPullsTheRetentionWindowAndKeepsOnlyWafAlerts() {
		lapi.alertsJson = array(vpatch(1, "198.51.100.9", ago(MIN), SHOP), crsLfi(2, "198.51.100.9", ago(MIN), SHOP),
				scenario(3, "198.51.100.20", ago(2 * MIN)));
		WafEventSyncService.SyncResult r = sync().sync(now);
		assertEquals(WafEventSyncService.OK, r.outcome());
		assertEquals(1, r.added());
		assertEquals(List.of("include_capi=false&since=2592000s&limit=500"), lapi.alertQueries);
		assertEquals(now, setting(WafEventSyncService.KEY_SYNCED_UNTIL));
		assertEquals(now, setting(WafEventSyncService.KEY_LAST_SYNC));
		assertEquals(1, sql.findAll(WafEvent.class).size());
	}

	@Test
	void nextSyncOverlapsFiveMinutesAndSkipsKnownAlerts() {
		lapi.alertsJson = array(vpatch(1, "198.51.100.9", ago(MIN), SHOP));
		WafEventSyncService s = sync();
		s.sync(now);
		now += MIN;
		assertEquals(0, s.sync(now).added());
		assertEquals("include_capi=false&since=360s&limit=500", lapi.alertQueries.get(1));
		assertEquals(1, sql.findAll(WafEvent.class).size());
	}

	@Test
	void fullPageTurnsBackWithTheOldestTimeAsUntil() {
		List<JSONObject> page = new ArrayList<>();
		for (int i = 0; i < 499; i++) {
			page.add(scenario(1000 + i, "198.51.100.20", ago(MIN)));
		}
		page.add(crsSqli(999, "198.51.100.10", ago(2 * MIN), SHOP));
		String first = array(page.toArray(new JSONObject[0]));
		String second = array(vpatch(1, "198.51.100.9", ago(60 * MIN), SHOP));
		lapi.alertsResponder = q -> q.contains("until=") ? second : first;
		assertEquals(2, sync().sync(now).added());
		assertEquals(List.of("include_capi=false&since=2592000s&limit=500", "include_capi=false&since=2592000s&until=120s&limit=500"),
				lapi.alertQueries);
	}

	@Test
	void fullPagesWithinOneSecondStopInsteadOfLooping() {
		List<JSONObject> page = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			page.add(scenario(1000 + i, "198.51.100.20", ago(MIN)));
		}
		lapi.alertsJson = array(page.toArray(new JSONObject[0]));
		assertEquals(WafEventSyncService.OK, sync().sync(now).outcome());
		assertEquals(2, lapi.alertQueries.size());
		// 同一秒超過一頁的部分抓不到，記成缺口
		assertEquals(now - 30 * DAY, setting(WafEventSyncService.KEY_GAP_FROM));
		assertEquals(now - MIN, setting(WafEventSyncService.KEY_GAP_TO));
	}

	@Test
	void failedSyncKeepsTheSyncPointAndTheNextOneBackfillsFromIt() {
		WafEventSyncService s = sync();
		s.sync(now);
		long t0 = now;
		now += MIN;
		lapi.alertsStatus = 500;
		WafEventSyncService.SyncResult failed = s.sync(now);
		assertEquals(WafEventSyncService.FAILED, failed.outcome());
		assertEquals("crowdsec_error", failed.error());
		assertEquals(t0, setting(WafEventSyncService.KEY_SYNCED_UNTIL));
		assertEquals("crowdsec_error", s.status(now).lastError());
		assertEquals(now, s.status(now).lastErrorAt());

		now += 20 * MIN;
		lapi.alertsStatus = 0;
		lapi.alertsJson = array(vpatch(1, "198.51.100.9", ago(10 * MIN), SHOP));
		assertEquals(1, s.sync(now).added());
		// 從上次成功同步（21 分鐘前）再往前 5 分鐘；之後還有一次缺口探測（中斷超過 10 分鐘）
		assertTrue(lapi.alertQueries.contains("include_capi=false&since=1560s&limit=500"), lapi.alertQueries.toString());
		assertNull(s.status(now).lastError());
		assertNull(s.status(now).lastErrorAt());
	}

	@Test
	void repeatedFailureWithTheSameCodeIsLoggedAtDebugAndANewCodeAtWarn() {
		Logger logger = (Logger) LoggerFactory.getLogger(WafEventSyncService.class);
		Level old = logger.getLevel();
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.setLevel(Level.DEBUG);
		logger.addAppender(appender);
		try {
			WafEventSyncService s = sync();
			lapi.alertsStatus = 500;
			s.sync(now);
			now += MIN;
			s.sync(now);
			now += MIN;
			s.sync(now);
			assertEquals(List.of(Level.WARN, Level.DEBUG, Level.DEBUG), failureLevels(appender));
			lapi.alertsStatus = 0;
			assertEquals(WafEventSyncService.OK, s.sync(now).outcome());
			lapi.alertsStatus = 500;
			s.sync(now);
			assertEquals(List.of(Level.WARN, Level.DEBUG, Level.DEBUG, Level.WARN), failureLevels(appender));
		} finally {
			logger.detachAppender(appender);
			logger.setLevel(old);
		}
	}

	private static List<Level> failureLevels(ListAppender<ILoggingEvent> appender) {
		return appender.list.stream().filter(e -> e.getFormattedMessage().startsWith("waf event sync failed")).map(ILoggingEvent::getLevel)
				.toList();
	}

	@Test
	void rejectedMachineAndUnreachableLapiHaveTheirOwnCodes() {
		lapi.rejectAllJwt = true;
		assertEquals(CrowdSecClient.MachineAuthException.CODE, sync().sync(now).error());
		env.put("CROWDSEC_LAPI_URL", "http://127.0.0.1:9");
		assertEquals(CrowdSecClient.LapiUnavailableException.CODE, sync().sync(now).error());
	}

	@Test
	void gapWhenCrowdSecNoLongerHasAlertsOlderThanTheSyncPoint() {
		long t0 = now - 120 * MIN;
		settings.set(WafEventSyncService.KEY_SYNCED_UNTIL, String.valueOf(t0));
		settings.set(WafEventSyncService.KEY_LAST_ALERT_AT, String.valueOf(t0 - MIN));
		Instant oldest = ago(30 * MIN);
		lapi.alertsResponder = q -> q.endsWith("limit=1") ? "[]" : array(vpatch(1, "198.51.100.9", oldest, SHOP));
		WafEventSyncService s = sync();
		s.sync(now);
		// 探測：比上次同步更早、7 天內的 alert
		assertTrue(lapi.alertQueries.contains("include_capi=false&since=612000s&until=7200s&limit=1"), lapi.alertQueries.toString());
		WafEventSyncService.Status st = s.status(now);
		assertEquals(t0, st.gapFrom());
		assertEquals(oldest.toEpochMilli(), st.gapTo());
	}

	@Test
	void noGapWhenOlderAlertsStillExist() {
		settings.set(WafEventSyncService.KEY_SYNCED_UNTIL, String.valueOf(now - 120 * MIN));
		lapi.alertsResponder = q -> q.endsWith("limit=1") ? array(scenario(5, "198.51.100.20", ago(180 * MIN)))
				: array(vpatch(1, "198.51.100.9", ago(30 * MIN), SHOP));
		WafEventSyncService s = sync();
		s.sync(now);
		assertNull(s.status(now).gapFrom());
	}

	@Test
	void shortPausesAreNotCheckedForGaps() {
		settings.set(WafEventSyncService.KEY_SYNCED_UNTIL, String.valueOf(now - 5 * MIN));
		lapi.alertsJson = array(vpatch(1, "198.51.100.9", ago(MIN), SHOP));
		sync().sync(now);
		assertTrue(lapi.alertQueries.stream().noneMatch(q -> q.endsWith("limit=1")));
	}

	@Test
	void downtimeLongerThanCrowdSecKeepsAlertsIsAGap() {
		long t0 = now - 8 * DAY;
		settings.set(WafEventSyncService.KEY_SYNCED_UNTIL, String.valueOf(t0));
		settings.set(WafEventSyncService.KEY_LAST_ALERT_AT, String.valueOf(t0 - MIN));
		WafEventSyncService s = sync();
		s.sync(now);
		assertEquals(t0, s.status(now).gapFrom());
		assertEquals(now - 7 * DAY, s.status(now).gapTo());
	}

	@Test
	void gapsOlderThanTheRetentionAreNotShown() {
		settings.set(WafEventSyncService.KEY_GAP_FROM, String.valueOf(now - 50 * DAY));
		settings.set(WafEventSyncService.KEY_GAP_TO, String.valueOf(now - 40 * DAY));
		assertNull(sync().status(now).gapFrom());
		assertNull(sync().status(now).gapTo());
	}

	@Test
	void retentionIsValidatedAndUsedByCleanup() {
		assertEquals(30, WafEventSyncService.parseRetention(" 30 "));
		assertEquals(1, WafEventSyncService.parseRetention("1"));
		assertEquals(365, WafEventSyncService.parseRetention("365"));
		for (String bad : new String[] { null, "", "0", "366", "abc", "7.5", "-1" }) {
			assertNull(WafEventSyncService.parseRetention(bad), String.valueOf(bad));
		}
		WafEventSyncService s = sync();
		assertEquals(30, s.retentionDays());
		assertThrows(IllegalArgumentException.class, () -> s.setRetentionDays(400));
		s.setRetentionDays(10);
		assertEquals(10, s.status(now).retentionDays());
		events.store(WafAlertParser.parseBatch(array(vpatch(1, "198.51.100.9", ago(11 * DAY), SHOP),
				vpatch(2, "198.51.100.10", ago(DAY), SHOP))).hits());
		assertEquals(1, s.cleanup(now));
		assertEquals(1, sql.findAll(WafEvent.class).size());
	}

	@Test
	void statusMapCarriesEveryField() {
		Map<String, Object> m = sync().status(now).toMap();
		assertEquals(List.of("configured", "retentionDays", "lastSyncAt", "syncedUntil", "lastError", "lastErrorAt", "gapFrom", "gapTo"),
				new ArrayList<>(m.keySet()));
	}

	@Test
	void gapProbeOnlyCountsAlertsAtOrBeforeTheSyncPoint() {
		long t0 = now - 120 * MIN;
		settings.set(WafEventSyncService.KEY_SYNCED_UNTIL, String.valueOf(t0));
		settings.set(WafEventSyncService.KEY_LAST_ALERT_AT, String.valueOf(t0 - MIN));
		// 探測回來的 alert 比上次同步晚（取整造成的邊界），不算「還有更舊的 alert」
		lapi.alertsResponder = q -> q.endsWith("limit=1") ? array(scenario(5, "198.51.100.20", ago(119 * MIN)))
				: array(vpatch(1, "198.51.100.9", ago(30 * MIN), SHOP));
		WafEventSyncService s = sync();
		s.sync(now);
		assertEquals(t0, s.status(now).gapFrom());
	}

	@Test
	void noGapIsRecordedBeforeAnyAlertWasEverSeen() {
		settings.set(WafEventSyncService.KEY_SYNCED_UNTIL, String.valueOf(now - 120 * MIN));
		lapi.alertsResponder = q -> q.endsWith("limit=1") ? "[]" : array(vpatch(1, "198.51.100.9", ago(30 * MIN), SHOP));
		WafEventSyncService s = sync();
		s.sync(now);
		assertNull(s.status(now).gapFrom());
	}

	@Test
	void noGapForLongDowntimeBeforeAnyAlertWasEverSeen() {
		settings.set(WafEventSyncService.KEY_SYNCED_UNTIL, String.valueOf(now - 8 * DAY));
		WafEventSyncService s = sync();
		s.sync(now);
		assertNull(s.status(now).gapFrom());
	}

	@Test
	void newestSeenAlertTimeIsRecordedAndKeptWhenNothingNewComes() {
		lapi.alertsJson = array(vpatch(1, "198.51.100.9", ago(MIN), SHOP), scenario(3, "198.51.100.20", ago(2 * MIN)));
		WafEventSyncService s = sync();
		s.sync(now);
		assertEquals(now - MIN, setting(WafEventSyncService.KEY_LAST_ALERT_AT));
		now += MIN;
		lapi.alertsJson = "[]";
		s.sync(now);
		assertEquals(now - 2 * MIN, setting(WafEventSyncService.KEY_LAST_ALERT_AT));
	}

	/** 模擬 LAPI：until 以收到請求的時間換算；每個請求處理完時鐘前進 5 秒 */
	@Test
	void pagingFollowsTheClockWhileTheSyncIsRunning() {
		long t0 = now;
		List<Long> times = new ArrayList<>();
		List<JSONObject> alerts = new ArrayList<>();
		for (int age = 1; age <= 40; age++) {
			for (int i = 0; i < 100; i++) {
				Instant at = Instant.ofEpochMilli(t0 - age * 1000L);
				times.add(at.toEpochMilli());
				alerts.add(age == 40 && i == 0 ? vpatch(1, "198.51.100.9", at, SHOP) : scenario(1000L * age + i, "198.51.100.20", at));
			}
		}
		Pattern untilParam = Pattern.compile("until=(\\d+)s");
		lapi.alertsResponder = q -> {
			Matcher m = untilParam.matcher(q);
			long cutoff = m.find() ? now - Long.parseLong(m.group(1)) * 1000 : Long.MAX_VALUE;
			List<JSONObject> page = new ArrayList<>();
			for (int i = 0; i < alerts.size() && page.size() < 500; i++) {
				if (times.get(i) <= cutoff) {
					page.add(alerts.get(i));
				}
			}
			now += 5000;
			return array(page.toArray(new JSONObject[0]));
		};
		assertEquals(1, sync().sync(t0).added());
		assertNull(setting(WafEventSyncService.KEY_GAP_FROM));
	}

	@Test
	void stoppingAfterTheLastAllowedPageRecordsAGap() {
		long t0 = now;
		lapi.alertsResponder = q -> {
			int k = lapi.alertQueries.size();
			JSONObject[] page = new JSONObject[500];
			for (int i = 0; i < page.length; i++) {
				page[i] = scenario(k * 1000L + i, "198.51.100.20", Instant.ofEpochMilli(t0 - (10 + k) * 1000L));
			}
			return array(page);
		};
		WafEventSyncService s = sync();
		assertEquals(WafEventSyncService.OK, s.sync(t0).outcome());
		assertEquals(WafEventSyncService.MAX_PAGES, lapi.alertQueries.size());
		assertEquals(t0 - 30 * DAY, s.status(t0).gapFrom());
		assertEquals(t0 - (10 + WafEventSyncService.MAX_PAGES) * 1000L, s.status(t0).gapTo());
	}

	@Test
	void secondSyncWhileRunningIsBusyAndTheLockIsReleasedAfterAFailure() {
		AtomicReference<WafEventSyncService.SyncResult> inner = new AtomicReference<>();
		WafEventSyncService s = sync();
		lapi.alertsResponder = q -> {
			inner.set(s.sync(now));
			return "[]";
		};
		assertEquals(WafEventSyncService.OK, s.sync(now).outcome());
		assertEquals(WafEventSyncService.BUSY, inner.get().outcome());
		lapi.alertsResponder = null;
		lapi.alertsStatus = 500;
		assertEquals(WafEventSyncService.FAILED, s.sync(now).outcome());
		lapi.alertsStatus = 0;
		assertEquals(WafEventSyncService.OK, s.sync(now).outcome());
	}

	@Test
	void noProbeGapWhenTheLastSeenAlertIsOlderThanCrowdSecKeepsAlerts() {
		long t0 = now - 120 * MIN;
		settings.set(WafEventSyncService.KEY_SYNCED_UNTIL, String.valueOf(t0));
		settings.set(WafEventSyncService.KEY_LAST_ALERT_AT, String.valueOf(now - 10 * DAY));
		lapi.alertsResponder = q -> q.endsWith("limit=1") ? "[]" : array(vpatch(1, "198.51.100.9", ago(30 * MIN), SHOP));
		WafEventSyncService s = sync();
		s.sync(now);
		assertNull(s.status(now).gapFrom());
	}

	@Test
	void logTextOnlyCarriesKnownSafeMessages() {
		assertEquals("crowdsec alerts HTTP 500", WafEventSyncService.loggable(new IllegalStateException("crowdsec alerts HTTP 500")));
		assertEquals("IllegalStateException", WafEventSyncService.loggable(new IllegalStateException("secret body")));
		assertEquals("RuntimeException", WafEventSyncService.loggable(new RuntimeException("crowdsec alerts HTTP 500")));
	}
}
