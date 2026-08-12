package com.cym.service;

import java.util.ArrayList;
import java.util.List;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.AsBlockIntent;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.AsnSourceUrls;
import com.cym.utils.NetGuard;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

/**
 * ASN big-block ban intents: protection profile gates, reasonTag, intent CRUD,
 * prefix fetch via ipverse as-ip-blocks, CrowdSec range ban push/revoke.
 */
@Component
public class AsnBlockService {

	private static final Logger logger = LoggerFactory.getLogger(AsnBlockService.class);

	/** CrowdSec decision reason prefix for all nginxWebUI ASN bans. */
	public static final String WEBUI_ASN_REASON_PREFIX = "nginxwebui:as-ban:AS";

	public static final String SETTING_PROFILE = "protection.profile";
	public static final String PROFILE_LIGHT = "light";
	public static final String PROFILE_MANUAL = "manual";
	public static final String PROFILE_STRICT = "strict";

	@Inject
	SqlHelper sqlHelper;
	@Inject
	SettingService settingService;
	@Inject
	CrowdSecClient crowdSecClient;

	// ── pure static helpers (unit-testable without DB) ─────────────────

	/**
	 * manual or strict may create ban intents.
	 */
	public static boolean canCreateIntentForProfile(String p) {
		return PROFILE_MANUAL.equals(p) || PROFILE_STRICT.equals(p);
	}

	/**
	 * Only strict may auto-suggest candidates.
	 */
	public static boolean canSuggestCandidatesForProfile(String p) {
		return PROFILE_STRICT.equals(p);
	}

	/**
	 * Exact match light|manual|strict (case-sensitive). For write-path validation.
	 */
	public static boolean isValidProfile(String p) {
		return PROFILE_LIGHT.equals(p) || PROFILE_MANUAL.equals(p) || PROFILE_STRICT.equals(p);
	}

	/**
	 * Normalize to light|manual|strict; anything else → light.
	 * For read/get paths only — write path must use {@link #isValidProfile} and reject.
	 */
	public static String normalizeProfile(String p) {
		if (isValidProfile(p)) {
			return p;
		}
		return PROFILE_LIGHT;
	}

	/**
	 * Stable CrowdSec decision reason tag: {@code nginxwebui:as-ban:AS{digits}}.
	 * Accepts pure digits or {@code AS}-prefixed form via {@link AsnSourceUrls#digits}.
	 *
	 * @throws IllegalArgumentException {@code invalid_asn} if asn cannot be normalized
	 */
	public static String reasonTagForAsn(String asn) {
		try {
			String a = AsnSourceUrls.digits(asn);
			return WEBUI_ASN_REASON_PREFIX + a;
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("invalid_asn");
		}
	}

	// ── profile settings ───────────────────────────────────────────────

	/** Current profile from settings; default {@code light}. */
	public String getProfile() {
		return normalizeProfile(settingService.get(SETTING_PROFILE));
	}

	/**
	 * Persist profile; only exact light|manual|strict accepted.
	 *
	 * @throws IllegalArgumentException invalid_profile if blank or not an allowed value
	 */
	public void setProfile(String profile) {
		if (!isValidProfile(profile)) {
			throw new IllegalArgumentException("invalid_profile");
		}
		settingService.set(SETTING_PROFILE, profile);
	}

	public boolean canCreateIntent() {
		return canCreateIntentForProfile(getProfile());
	}

	public boolean canSuggestCandidates() {
		return canSuggestCandidatesForProfile(getProfile());
	}

	/**
	 * Push allowed when profile is manual|strict and CrowdSec LAPI is configured.
	 */
	public boolean canPush() {
		return canCreateIntentForProfile(getProfile()) && isCrowdSecConfigured();
	}

	/**
	 * CrowdSec configured = LAPI URL + API key present ({@link CrowdSecClient#isConfigured()}).
	 */
	public boolean isCrowdSecConfigured() {
		return crowdSecClient != null && crowdSecClient.isConfigured();
	}

	// ── intent CRUD ────────────────────────────────────────────────────

	/**
	 * Create a pending ASN ban intent. Profile must allow create.
	 *
	 * @throws IllegalStateException if profile is light
	 * @throws IllegalArgumentException {@code invalid_asn} or {@code invalid_duration}
	 */
	public AsBlockIntent addIntent(String asn, String duration, String note) {
		String profile = getProfile();
		if (!canCreateIntentForProfile(profile)) {
			throw new IllegalStateException("profile_disallows_intent");
		}
		try {
			asn = AsnSourceUrls.digits(asn);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("invalid_asn");
		}
		String dur;
		if (StrUtil.isBlank(duration)) {
			dur = "24h";
		} else if (!NetGuard.isValidDuration(duration)) {
			throw new IllegalArgumentException("invalid_duration");
		} else {
			dur = duration.trim();
		}
		AsBlockIntent i = new AsBlockIntent();
		i.setAsn(asn);
		i.setStatus(AsBlockIntent.STATUS_PENDING);
		i.setDuration(dur);
		i.setReasonTag(reasonTagForAsn(asn));
		i.setCreatedByProfile(profile);
		i.setNote(note);
		sqlHelper.insert(i);
		return i;
	}

	public List<AsBlockIntent> listIntents() {
		return sqlHelper.findAll(AsBlockIntent.class);
	}

	// ── prefix fetch (ipverse as-ip-blocks only) ───────────────────────

	/**
	 * Fetch aggregated IPv4 + IPv6 CIDRs for an ASN from ipverse as-ip-blocks.
	 * Uses {@link AsnSourceUrls#prefixIpv4Url} / {@link AsnSourceUrls#prefixIpv6Url} only
	 * — never hardcode hosts/paths, never legacy asn-ip.
	 *
	 * @param asn digits or AS-prefixed (normalized by AsnSourceUrls)
	 * @return list of CIDR lines (may be empty if both sources fail or empty)
	 */
	List<String> fetchAggregatedCidrs(String asn) {
		List<String> out = new ArrayList<>();
		// MUST use AsnSourceUrls — exact hosts/paths from plan Source URLs table
		String[] urls = new String[] {
				AsnSourceUrls.prefixIpv4Url(asn),
				AsnSourceUrls.prefixIpv6Url(asn)
		};
		for (String url : urls) {
			try {
				HttpResponse resp = HttpRequest.get(url)
						.timeout(30_000)
						.header("User-Agent", "nginxWebUI/AsnBlock")
						.setMaxRedirectCount(5)
						.execute();
				if (!resp.isOk()) {
					continue;
				}
				String body = resp.body();
				if (body == null) {
					continue;
				}
				for (String line : body.split("\r?\n")) {
					String s = line.trim();
					if (s.isEmpty() || s.startsWith("#")) {
						continue;
					}
					// only real CIDR / IP lines — skip garbage feed rows
					if (NetGuard.isValidCidr(s)) {
						out.add(s);
					}
				}
			} catch (Exception e) {
				// one family fail must not block the other
			}
		}
		return out;
	}

	// ── push / revoke ──────────────────────────────────────────────────

	/**
	 * Fetch prefixes for the intent's ASN and ban each range on CrowdSec LAPI.
	 * Profile must be manual|strict; CrowdSec must be configured.
	 *
	 * @throws IllegalStateException profile_disallows_push | crowdsec_not_configured
	 * @throws IllegalArgumentException intent_not_found
	 */
	public void pushIntent(String intentId) {
		if (!canCreateIntentForProfile(getProfile())) {
			throw new IllegalStateException("profile_disallows_push");
		}
		if (!crowdSecClient.isConfigured()) {
			throw new IllegalStateException("crowdsec_not_configured");
		}
		AsBlockIntent intent = sqlHelper.findById(intentId, AsBlockIntent.class);
		if (intent == null) {
			throw new IllegalArgumentException("intent_not_found");
		}
		intent.setStatus(AsBlockIntent.STATUS_PENDING);
		// insertOrUpdate skips null columns — use "" so prior errors clear on DB write
		intent.setLastError("");
		sqlHelper.updateAllColumnById(intent);

		String duration = intent.getDuration();
		if (!NetGuard.isValidDuration(duration)) {
			intent.setStatus(AsBlockIntent.STATUS_FAILED);
			intent.setLastError("invalid_duration");
			sqlHelper.updateAllColumnById(intent);
			return;
		}

		String batchId = String.valueOf(System.currentTimeMillis());
		List<String> cidrs = fetchAggregatedCidrs(intent.getAsn());
		if (cidrs.isEmpty()) {
			intent.setStatus(AsBlockIntent.STATUS_FAILED);
			intent.setLastError("no_prefixes");
			sqlHelper.updateAllColumnById(intent);
			return;
		}
		String reason = intent.getReasonTag();
		int ok = 0;
		int fail = 0;
		for (String cidr : cidrs) {
			// double-guard: feed already filtered; skip if anything slips through
			if (!NetGuard.isValidCidr(cidr)) {
				continue;
			}
			try {
				crowdSecClient.banRange(cidr, duration, reason);
				ok++;
			} catch (Exception e) {
				fail++;
				// Log full LAPI detail; never store raw body in lastError (admin UI surface)
				logger.warn("banRange failed asn={} cidr={}: {}", intent.getAsn(), cidr, e.getMessage());
			}
		}
		intent.setPushBatchId(batchId);
		intent.setLastPushAt(System.currentTimeMillis());
		if (ok == 0) {
			intent.setStatus(AsBlockIntent.STATUS_FAILED);
			intent.setLastError("all_failed");
		} else {
			intent.setStatus(AsBlockIntent.STATUS_ACTIVE);
			// fixed codes only — no LAPI message leakage
			intent.setLastError(fail > 0 ? "partial:" + ok + "/" + (ok + fail) : "");
		}
		sqlHelper.updateAllColumnById(intent);
	}

	/**
	 * Delete CrowdSec decisions with exact {@code reasonTag}, mark matching intents revoked.
	 *
	 * @return number of decisions deleted on LAPI
	 */
	public int revokeByReasonTag(String reasonTag) {
		int n = crowdSecClient.deleteDecisionsByReasonEquals(reasonTag);
		List<AsBlockIntent> list = sqlHelper.findListByQuery(
				new ConditionAndWrapper().eq("reasonTag", reasonTag), AsBlockIntent.class);
		for (AsBlockIntent i : list) {
			i.setStatus(AsBlockIntent.STATUS_REVOKED);
			sqlHelper.updateById(i);
		}
		return n;
	}

	/**
	 * Delete all CrowdSec decisions whose reason starts with
	 * {@code nginxwebui:as-ban:AS}, and mark all matching local intents revoked.
	 *
	 * @return number of decisions deleted on LAPI
	 */
	public int revokeAllWebuiAsnBans() {
		int n = crowdSecClient.deleteDecisionsByReasonPrefix(WEBUI_ASN_REASON_PREFIX);
		List<AsBlockIntent> list = sqlHelper.findAll(AsBlockIntent.class);
		for (AsBlockIntent i : list) {
			if (i.getReasonTag() != null && i.getReasonTag().startsWith(WEBUI_ASN_REASON_PREFIX)) {
				i.setStatus(AsBlockIntent.STATUS_REVOKED);
				sqlHelper.updateById(i);
			}
		}
		return n;
	}
}
