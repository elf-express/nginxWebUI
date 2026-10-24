package com.cym.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.util.StrUtil;

/**
 * 把 CrowdSec AppSec 產生的 alert 抄進 WafEvent／WafEventRule（E-394）。
 * <p>
 * 每次從上次成功同步的時間往前重疊 5 分鐘拉取（第一次拉保留天數內全部），alert id 去重。LAPI 一頁最多 500 筆、新到舊，
 * 滿頁就以這頁最舊的時間當 until 往前翻。成功才推進上次同步時間；失敗時保留，恢復後從那裡補拉。
 * <p>
 * 缺口：距上次成功同步超過 10 分鐘才檢查。CrowdSec 還留著比上次同步更早的 alert 就沒有缺口；一筆都沒有、
 * 且這段期間最舊的 alert 晚於上次同步時間，記缺口 [上次同步, 最舊 alert]；中斷超過 CrowdSec 的 7 天保留期，記 [上次同步, 7 天前]。
 * 以上兩種缺口都要求先前看過 alert（KEY_LAST_ALERT_AT）；另外頁數用完或同一秒的 alert 超過一頁而沒抓完時，記缺口 [本次拉取起點, 已抓到的最舊 alert]。
 */
@Component
public class WafEventSyncService {
	private static final Logger logger = LoggerFactory.getLogger(WafEventSyncService.class);

	public static final String KEY_RETENTION = "crowdsecWafRetentionDays";
	public static final String KEY_SYNCED_UNTIL = "crowdsecWafSyncedUntil";
	public static final String KEY_LAST_SYNC = "crowdsecWafLastSyncAt";
	public static final String KEY_LAST_ERROR = "crowdsecWafLastError";
	public static final String KEY_LAST_ERROR_AT = "crowdsecWafLastErrorAt";
	public static final String KEY_GAP_FROM = "crowdsecWafGapFrom";
	public static final String KEY_GAP_TO = "crowdsecWafGapTo";
	public static final String KEY_LAST_ALERT_AT = "crowdsecWafLastAlertAt";

	public static final int DEFAULT_RETENTION_DAYS = 30;
	public static final int MIN_RETENTION_DAYS = 1;
	public static final int MAX_RETENTION_DAYS = 365;

	public static final String OK = "ok";
	public static final String NOT_CONFIGURED = "notConfigured";
	public static final String BUSY = "busy";
	public static final String FAILED = "failed";

	static final long DAY_MS = 86_400_000L;
	static final long OVERLAP_MS = 5 * 60_000L;
	static final long GAP_CHECK_AFTER_MS = 10 * 60_000L;
	/** CrowdSec db_config.flush.max_age 預設 7 天 */
	static final long CROWDSEC_MAX_AGE_MS = 7 * DAY_MS;
	static final int PAGE_SIZE = 500;
	static final int MAX_PAGES = 120;

	@Inject
	SettingService settingService;
	@Inject
	CrowdSecClient crowdSecClient;
	@Inject
	WafEventService wafEventService;

	/** 翻頁時換算 until 用；LAPI 以收到請求的時間換算，所以每頁送出前現讀 */
	java.util.function.LongSupplier clock = System::currentTimeMillis;

	private final AtomicBoolean running = new AtomicBoolean();

	/** outcome：ok／notConfigured／busy／failed；error 是失敗時的錯誤碼 */
	public record SyncResult(String outcome, int added, String error) {
		public Map<String, Object> toMap() {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("outcome", outcome);
			map.put("added", added);
			map.put("error", error);
			return map;
		}
	}

	/** 時間都是 epoch 毫秒；gapFrom／gapTo 只在缺口仍在保留期內時有值 */
	public record Status(boolean configured, int retentionDays, Long lastSyncAt, Long syncedUntil, String lastError,
			Long lastErrorAt, Long gapFrom, Long gapTo) {
		public Map<String, Object> toMap() {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("configured", configured);
			map.put("retentionDays", retentionDays);
			map.put("lastSyncAt", lastSyncAt);
			map.put("syncedUntil", syncedUntil);
			map.put("lastError", lastError);
			map.put("lastErrorAt", lastErrorAt);
			map.put("gapFrom", gapFrom);
			map.put("gapTo", gapTo);
			return map;
		}
	}

	/** 同步一次；同時只跑一個（排程與「立即同步」可能重疊），另一個回 busy */
	public SyncResult sync(long now) {
		if (!crowdSecClient.isConfigured()) {
			return new SyncResult(NOT_CONFIGURED, 0, null);
		}
		if (!running.compareAndSet(false, true)) {
			return new SyncResult(BUSY, 0, null);
		}
		try {
			Long syncedUntil = longSetting(KEY_SYNCED_UNTIL);
			long retentionStart = now - retentionDays() * DAY_MS;
			long from = syncedUntil == null ? retentionStart : Math.max(retentionStart, syncedUntil - OVERLAP_MS);
			Long lastAlertAt = longSetting(KEY_LAST_ALERT_AT);
			List<WafAlertParser.Hit> hits = new ArrayList<>();
			Fetched fetched = fetch(now, from, hits);
			if (fetched.truncated()) {
				recordGap(from, fetched.oldest().toEpochMilli());
			} else if (syncedUntil != null && lastAlertAt != null && now - syncedUntil > GAP_CHECK_AFTER_MS) {
				detectGap(now, syncedUntil, lastAlertAt, fetched.oldest());
			}
			int added = wafEventService.store(hits);
			if (fetched.newest() != null && (lastAlertAt == null || fetched.newest().toEpochMilli() > lastAlertAt)) {
				settingService.set(KEY_LAST_ALERT_AT, String.valueOf(fetched.newest().toEpochMilli()));
			}
			settingService.set(KEY_SYNCED_UNTIL, String.valueOf(now));
			settingService.set(KEY_LAST_SYNC, String.valueOf(now));
			settingService.remove(KEY_LAST_ERROR);
			settingService.remove(KEY_LAST_ERROR_AT);
			return new SyncResult(OK, added, null);
		} catch (RuntimeException e) {
			String code = errorCode(e);
			// 已知例外的訊息只含錯誤碼或 HTTP 狀態碼；未預期的例外訊息可能帶 LAPI 回應內容，只記類別名稱
			logger.warn("waf event sync failed: {} ({})", code, loggable(e));
			settingService.set(KEY_LAST_ERROR, code);
			settingService.set(KEY_LAST_ERROR_AT, String.valueOf(now));
			return new SyncResult(FAILED, 0, code);
		} finally {
			running.set(false);
		}
	}

	/** fetch 的結果：oldest／newest 是這段期間所有 alert 最舊／最新的建立時間（沒有 alert 時 null）；truncated 表示有 alert 沒抓完 */
	record Fetched(Instant oldest, Instant newest, boolean truncated) {
	}

	/**
	 * 拉 from 之後的所有 alert，WAF 命中放進 out。滿頁就以這頁最舊的時間往前翻，until 在每頁送出前依現在時間換算；
	 * 頁數用完，或同一秒內的 alert 超過一頁時停止並標記 truncated。
	 */
	Fetched fetch(long now, long from, List<WafAlertParser.Hit> out) {
		long since = ceilSeconds(now - from);
		Instant boundary = null;
		Instant oldest = null;
		Instant newest = null;
		for (int page = 1; page <= MAX_PAGES; page++) {
			Long until = boundary == null ? null : Math.max(1, (clock.getAsLong() - boundary.toEpochMilli()) / 1000);
			WafAlertParser.Batch batch = WafAlertParser.parseBatch(crowdSecClient.alertsRaw(since, until, PAGE_SIZE));
			out.addAll(batch.hits());
			if (batch.oldest() != null && (oldest == null || batch.oldest().isBefore(oldest))) {
				oldest = batch.oldest();
			}
			if (batch.newest() != null && (newest == null || batch.newest().isAfter(newest))) {
				newest = batch.newest();
			}
			if (batch.size() < PAGE_SIZE || batch.oldest() == null) {
				return new Fetched(oldest, newest, false);
			}
			if (boundary != null && batch.oldest().getEpochSecond() >= boundary.getEpochSecond()) {
				logger.warn("waf event sync: more than {} alerts within one second, the rest of that second is skipped", PAGE_SIZE);
				return new Fetched(oldest, newest, true);
			}
			boundary = batch.oldest();
		}
		logger.warn("waf event sync: stopped after {} pages, older alerts in this window are skipped", MAX_PAGES);
		return new Fetched(oldest, newest, true);
	}

	/** lastAlertAt：先前看到的最新 alert 時間。探測型缺口要求它還在 CrowdSec 的保留期內，否則 alert 本來就不可能還在 */
	void detectGap(long now, long syncedUntil, long lastAlertAt, Instant oldestInWindow) {
		long gapTo;
		if (now - syncedUntil > CROWDSEC_MAX_AGE_MS) {
			gapTo = now - CROWDSEC_MAX_AGE_MS;
		} else {
			if (lastAlertAt <= now - CROWDSEC_MAX_AGE_MS || oldestInWindow == null || oldestInWindow.toEpochMilli() <= syncedUntil) {
				return;
			}
			long untilSeconds = Math.max(1, (now - syncedUntil) / 1000);
			long sinceSeconds = untilSeconds + CROWDSEC_MAX_AGE_MS / 1000;
			Instant probe = WafAlertParser.parseBatch(crowdSecClient.alertsRaw(sinceSeconds, untilSeconds, 1)).oldest();
			if (probe != null && probe.toEpochMilli() <= syncedUntil) {
				return;
			}
			gapTo = oldestInWindow.toEpochMilli();
		}
		recordGap(syncedUntil, gapTo);
	}

	private void recordGap(long gapFrom, long gapTo) {
		settingService.set(KEY_GAP_FROM, String.valueOf(gapFrom));
		settingService.set(KEY_GAP_TO, String.valueOf(gapTo));
		logger.warn("waf event sync: WAF events between {} and {} may be missing", Instant.ofEpochMilli(gapFrom),
				Instant.ofEpochMilli(gapTo));
	}

	public Status status(long now) {
		Long gapFrom = longSetting(KEY_GAP_FROM);
		Long gapTo = longSetting(KEY_GAP_TO);
		boolean showGap = gapFrom != null && gapTo != null && gapTo > now - retentionDays() * DAY_MS;
		return new Status(crowdSecClient.isConfigured(), retentionDays(), longSetting(KEY_LAST_SYNC),
				longSetting(KEY_SYNCED_UNTIL), settingService.get(KEY_LAST_ERROR), longSetting(KEY_LAST_ERROR_AT),
				showGap ? gapFrom : null, showGap ? gapTo : null);
	}

	/** 刪掉超過保留天數的事件，回刪掉的事件數 */
	public long cleanup(long now) {
		long removed = wafEventService.cleanup(now - retentionDays() * DAY_MS);
		if (removed > 0) {
			logger.info("waf event cleanup: removed {} event(s) older than {} days", removed, retentionDays());
		}
		return removed;
	}

	public int retentionDays() {
		Integer days = parseRetention(settingService.get(KEY_RETENTION));
		return days == null ? DEFAULT_RETENTION_DAYS : days;
	}

	public void setRetentionDays(int days) {
		if (days < MIN_RETENTION_DAYS || days > MAX_RETENTION_DAYS) {
			throw new IllegalArgumentException("retention days out of range: " + days);
		}
		settingService.set(KEY_RETENTION, String.valueOf(days));
	}

	/** 1–365 的整數字串（可有前後空白）轉成天數，其他回 null */
	public static Integer parseRetention(String value) {
		if (value == null || !value.trim().matches("\\d{1,3}")) {
			return null;
		}
		int days = Integer.parseInt(value.trim());
		return days >= MIN_RETENTION_DAYS && days <= MAX_RETENTION_DAYS ? days : null;
	}

	/** 可以安全寫進 log 的描述 */
	static String loggable(RuntimeException e) {
		boolean known = e instanceof CrowdSecClient.MachineAuthException || e instanceof CrowdSecClient.LapiUnavailableException
				|| e instanceof IllegalStateException && String.valueOf(e.getMessage()).startsWith("crowdsec alerts HTTP ");
		return known ? e.getMessage() : e.getClass().getSimpleName();
	}

	static String errorCode(RuntimeException e) {
		if (e instanceof CrowdSecClient.MachineAuthException) {
			return CrowdSecClient.MachineAuthException.CODE;
		}
		if (e instanceof CrowdSecClient.LapiUnavailableException) {
			return CrowdSecClient.LapiUnavailableException.CODE;
		}
		if (e instanceof CrowdSecClient.NotConfiguredException) {
			return e.getMessage();
		}
		return "crowdsec_error";
	}

	private Long longSetting(String key) {
		String v = settingService.get(key);
		if (StrUtil.isBlank(v)) {
			return null;
		}
		try {
			return Long.valueOf(v.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static long ceilSeconds(long ms) {
		return Math.max(1, (ms + 999) / 1000);
	}
}
