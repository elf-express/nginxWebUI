package com.cym.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.AsMeta;
import com.cym.sqlhelper.bean.Page;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.ConditionOrWrapper;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.AsnSourceUrls;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * AS metadata catalog (ipverse as-metadata as.json): parse, remote sync, search.
 * Full AsMeta only — no prefix catalog dual-storage.
 * <p>
 * Phase 1 (Light + catalog): shared single-flight sync for manual + schedule.
 * Streaming/batch upsert remains post-merge hardening for very large heaps.
 */
@Component
public class AsnMetaService {

	private static final Logger logger = LoggerFactory.getLogger(AsnMetaService.class);

	/** Catalog page size hard cap (UI/API). */
	public static final int MAX_CATALOG_LIMIT = 100;

	/** Shared by ScheduleTask and AsnController so concurrent syncs never stack. */
	private final AtomicBoolean syncing = new AtomicBoolean(false);

	@Inject
	SqlHelper sqlHelper;
	@Inject
	SettingService settingService;

	/** @return true if this caller acquired the lock and must call {@link #endSync()}. */
	public boolean tryBeginSync() {
		return syncing.compareAndSet(false, true);
	}

	public void endSync() {
		syncing.set(false);
	}

	public boolean isSyncing() {
		return syncing.get();
	}

	/** Clamp page.limit into 1..MAX_CATALOG_LIMIT; fix curr if missing. */
	public static void normalizeCatalogPage(Page page) {
		if (page == null) {
			return;
		}
		if (page.getCurr() == null || page.getCurr() < 1) {
			page.setCurr(1);
		}
		if (page.getLimit() == null || page.getLimit() < 1) {
			page.setLimit(10);
		} else if (page.getLimit() > MAX_CATALOG_LIMIT) {
			page.setLimit(MAX_CATALOG_LIMIT);
		}
	}

	/**
	 * Parse ipverse as-metadata JSON array into flat rows.
	 * <ul>
	 * <li>asn may be number → String.valueOf</li>
	 * <li>null metadata fields → null columns</li>
	 * <li>blank asn entries skipped</li>
	 * </ul>
	 */
	public static List<AsMetaRow> parseAsJson(String json) {
		List<AsMetaRow> out = new ArrayList<>();
		if (StrUtil.isBlank(json)) {
			return out;
		}
		JSONArray arr = JSONUtil.parseArray(json);
		for (int i = 0; i < arr.size(); i++) {
			JSONObject o = arr.getJSONObject(i);
			if (o == null) {
				continue;
			}
			Object asnObj = o.get("asn");
			if (asnObj == null) {
				continue;
			}
			String asn = String.valueOf(asnObj).trim();
			// hutool may stringify integers cleanly; strip trailing .0 if any
			if (asn.endsWith(".0") && asn.matches("\\d+\\.0")) {
				asn = asn.substring(0, asn.length() - 2);
			}
			if (StrUtil.isBlank(asn)) {
				continue;
			}

			AsMetaRow row = new AsMetaRow();
			row.asn = asn;
			row.lastAnnounced = o.getStr("lastAnnounced");

			JSONObject meta = o.getJSONObject("metadata");
			if (meta != null) {
				row.handle = emptyToNull(meta.getStr("handle"));
				row.description = emptyToNull(meta.getStr("description"));
				row.countryCode = emptyToNull(meta.getStr("countryCode"));
				row.category = emptyToNull(meta.getStr("category"));
				row.networkRole = emptyToNull(meta.getStr("networkRole"));
				row.origin = emptyToNull(meta.getStr("origin"));
			}
			out.add(row);
		}
		return out;
	}

	private static String emptyToNull(String s) {
		return StrUtil.isBlank(s) ? null : s;
	}

	/**
	 * Download full catalog from {@link AsnSourceUrls#META_JSON_URL}, upsert by asn.
	 * Sets {@code asn.meta.lastSyncAt} / {@code asn.meta.lastSyncError}.
	 *
	 * @return number of rows upserted
	 */
	public int syncFromRemote() {
		// MUST use AsnSourceUrls.META_JSON_URL — never hardcode / never asn-ip legacy
		HttpResponse resp;
		try {
			resp = HttpRequest.get(AsnSourceUrls.META_JSON_URL)
					.timeout(120_000)
					.header("User-Agent", "nginxWebUI/AsnMeta-sync")
					.setMaxRedirectCount(5)
					.execute();
		} catch (Exception e) {
			String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			settingService.set("asn.meta.lastSyncError", msg);
			logger.error("AsMeta sync request failed: {}", msg);
			throw new IllegalStateException("AsMeta sync request failed url=" + AsnSourceUrls.META_JSON_URL, e);
		}
		if (!resp.isOk()) {
			settingService.set("asn.meta.lastSyncError", "HTTP " + resp.getStatus());
			throw new IllegalStateException("AsMeta sync HTTP " + resp.getStatus()
					+ " url=" + AsnSourceUrls.META_JSON_URL);
		}
		List<AsMetaRow> rows;
		try {
			rows = parseAsJson(resp.body());
		} catch (Exception e) {
			String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
			settingService.set("asn.meta.lastSyncError", "parse: " + msg);
			throw new IllegalStateException("AsMeta sync parse failed", e);
		}
		long now = System.currentTimeMillis();
		int n = 0;
		for (AsMetaRow row : rows) {
			AsMeta existing = sqlHelper.findOneByQuery(
					new ConditionAndWrapper().eq("asn", row.asn), AsMeta.class);
			AsMeta m = existing != null ? existing : new AsMeta();
			m.setAsn(row.asn);
			m.setHandle(row.handle);
			m.setDescription(row.description);
			m.setCountryCode(row.countryCode);
			m.setCategory(row.category);
			m.setNetworkRole(row.networkRole);
			m.setOrigin(row.origin);
			m.setLastAnnounced(row.lastAnnounced);
			m.setSyncedAt(now);
			sqlHelper.insertOrUpdate(m);
			n++;
		}
		settingService.set("asn.meta.lastSyncAt", String.valueOf(now));
		settingService.set("asn.meta.lastSyncError", "");
		logger.info("AsMeta sync done: {} rows from {}", n, AsnSourceUrls.META_JSON_URL);
		return n;
	}

	/**
	 * Search AsMeta with optional free-text, category, and country filters.
	 * <p>
	 * Free-text: digits → exact asn match; otherwise OR like on handle + description.
	 */
	public Page search(Page page, String q, String category, String countryCode) {
		ConditionAndWrapper c = new ConditionAndWrapper();
		if (StrUtil.isNotBlank(category)) {
			c.eq("category", category);
		}
		if (StrUtil.isNotBlank(countryCode)) {
			c.eq("countryCode", countryCode.trim().toUpperCase());
		}
		if (StrUtil.isNotBlank(q)) {
			String qq = q.trim();
			if (qq.matches("\\d+")) {
				c.eq("asn", qq);
			} else {
				// handle OR description like (ConditionOrWrapper)
				c.and(new ConditionOrWrapper().like("handle", qq).like("description", qq));
			}
		}
		return sqlHelper.findPage(c, page, AsMeta.class);
	}

	/** Package-visible DTO for parse results (also used by unit tests). */
	public static class AsMetaRow {
		public String asn;
		public String handle;
		public String description;
		public String countryCode;
		public String category;
		public String networkRole;
		public String origin;
		public String lastAnnounced;
	}
}
