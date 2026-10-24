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
	static final int MAX_PAGES = 40;

	@Inject
	SettingService settingService;
	@Inject
	CrowdSecClient crowdSecClient;
	@Inject
	WafEventService wafEventService;

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
			List<WafAlertParser.Hit> hits = new ArrayList<>();
			Instant oldest = fetch(now, from, hits);
			if (syncedUntil != null && now - syncedUntil > GAP_CHECK_AFTER_MS) {
				detectGap(now, syncedUntil, oldest);
			}
			int added = wafEventService.store(hits);
			settingService.set(KEY_SYNCED_UNTIL, String.valueOf(now));
			settingService.set(KEY_LAST_SYNC, String.valueOf(now));
			settingService.remove(KEY_LAST_ERROR);
			settingService.remove(KEY_LAST_ERROR_AT);
			return new SyncResult(OK, added, null);
		} catch (RuntimeException e) {
			String code = errorCode(e);
			logger.warn("waf event sync failed: {} ({})", code, e.getMessage());
			settingService.set(KEY_LAST_ERROR, code);
			settingService.set(KEY_LAST_ERROR_AT, String.valueOf(now));
			return new SyncResult(FAILED, 0, code);
		} finally {
			running.set(false);
		}
	}

	/** 拉 from 之後的所有 alert，WAF 命中放進 out；回這段期間所有 alert 最舊的建立時間（沒有 alert 時 null） */
	Instant fetch(long now, long from, List<WafAlertParser.Hit> out) {
		long since = ceilSeconds(now - from);
		Long until = null;
		Instant oldest = null;
		for (int page = 1; page <= MAX_PAGES; page++) {
			WafAlertParser.Batch batch = WafAlertParser.parseBatch(crowdSecClient.alertsRaw(since, until, PAGE_SIZE));
			out.addAll(batch.hits());
			if (batch.oldest() != null && (oldest == null || batch.oldest().isBefore(oldest))) {
				oldest = batch.oldest();
			}
			if (batch.size() < PAGE_SIZE || batch.oldest() == null) {
				return oldest;
			}
			long next = Math.max(1, (now - batch.oldest().toEpochMilli()) / 1000);
			if (until != null && next <= until) {
				logger.warn("waf event sync: more than {} alerts within one second, the rest of that second is skipped", PAGE_SIZE);
				return oldest;
			}
			until = next;
		}
		logger.warn("waf event sync: stopped after {} pages, older alerts in this window are skipped", MAX_PAGES);
		return oldest;
	}

	void detectGap(long now, long syncedUntil, Instant oldestInWindow) {
		long gapTo;
		if (now - syncedUntil > CROWDSEC_MAX_AGE_MS) {
			gapTo = now - CROWDSEC_MAX_AGE_MS;
		} else {
			if (oldestInWindow == null || oldestInWindow.toEpochMilli() <= syncedUntil) {
				return;
			}
			long untilSeconds = Math.max(1, (now - syncedUntil) / 1000);
			long sinceSeconds = untilSeconds + CROWDSEC_MAX_AGE_MS / 1000;
			if (WafAlertParser.parseBatch(crowdSecClient.alertsRaw(sinceSeconds, untilSeconds, 1)).size() > 0) {
				return;
			}
			gapTo = oldestInWindow.toEpochMilli();
		}
		settingService.set(KEY_GAP_FROM, String.valueOf(syncedUntil));
		settingService.set(KEY_GAP_TO, String.valueOf(gapTo));
		logger.warn("waf event sync: CrowdSec no longer has alerts between {} and {}, WAF events of that period may be missing",
				Instant.ofEpochMilli(syncedUntil), Instant.ofEpochMilli(gapTo));
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
