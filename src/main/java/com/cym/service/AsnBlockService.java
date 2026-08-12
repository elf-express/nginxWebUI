package com.cym.service;

import java.util.ArrayList;
import java.util.List;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

import com.cym.model.AsBlockIntent;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;
import com.cym.utils.AsnSourceUrls;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

/**
 * ASN big-block ban intents: protection profile gates, reasonTag, intent CRUD,
 * prefix fetch via ipverse as-ip-blocks, CrowdSec range ban push/revoke.
 */
@Component
public class AsnBlockService {

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
	 * Normalize to light|manual|strict; anything else → light.
	 */
	public static String normalizeProfile(String p) {
		if (PROFILE_MANUAL.equals(p) || PROFILE_STRICT.equals(p) || PROFILE_LIGHT.equals(p)) {
			return p;
		}
		return PROFILE_LIGHT;
	}

	/**
	 * Stable CrowdSec decision reason tag: {@code nginxwebui:as-ban:AS{digits}}.
	 *
	 * @throws IllegalArgumentException if asn is not pure digits
	 */
	public static String reasonTagForAsn(String asn) {
		String a = asn == null ? "" : asn.trim();
		if (!a.matches("\\d+")) {
			throw new IllegalArgumentException("invalid asn");
		}
		return WEBUI_ASN_REASON_PREFIX + a;
	}

	// ── profile settings ───────────────────────────────────────────────

	/** Current profile from settings; default {@code light}. */
	public String getProfile() {
		return normalizeProfile(settingService.get(SETTING_PROFILE));
	}

	/** Persist profile; only light|manual|strict accepted (else light). */
	public void setProfile(String profile) {
		settingService.set(SETTING_PROFILE, normalizeProfile(profile));
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
	 * @throws IllegalArgumentException if asn is not digits
	 */
	public AsBlockIntent addIntent(String asn, String duration, String note) {
		String profile = getProfile();
		if (!canCreateIntentForProfile(profile)) {
			throw new IllegalStateException("profile_disallows_intent");
		}
		if (asn == null) {
			throw new IllegalArgumentException("invalid_asn");
		}
		asn = asn.trim();
		if (!asn.matches("\\d+")) {
			throw new IllegalArgumentException("invalid_asn");
		}
		AsBlockIntent i = new AsBlockIntent();
		i.setAsn(asn);
		i.setStatus(AsBlockIntent.STATUS_PENDING);
		i.setDuration(StrUtil.blankToDefault(duration, "24h"));
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
					out.add(s);
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
		intent.setLastError(null);
		sqlHelper.updateById(intent);

		String batchId = String.valueOf(System.currentTimeMillis());
		List<String> cidrs = fetchAggregatedCidrs(intent.getAsn());
		if (cidrs.isEmpty()) {
			intent.setStatus(AsBlockIntent.STATUS_FAILED);
			intent.setLastError("no_prefixes");
			sqlHelper.updateById(intent);
			return;
		}
		String reason = intent.getReasonTag();
		int ok = 0;
		String lastErr = null;
		for (String cidr : cidrs) {
			try {
				crowdSecClient.banRange(cidr, intent.getDuration(), reason);
				ok++;
			} catch (Exception e) {
				lastErr = e.getMessage();
			}
		}
		intent.setPushBatchId(batchId);
		intent.setLastPushAt(System.currentTimeMillis());
		if (ok == 0) {
			intent.setStatus(AsBlockIntent.STATUS_FAILED);
			intent.setLastError(lastErr);
		} else {
			intent.setStatus(AsBlockIntent.STATUS_ACTIVE);
			intent.setLastError(ok < cidrs.size()
					? "partial:" + ok + "/" + cidrs.size() + " " + lastErr
					: null);
		}
		sqlHelper.updateById(intent);
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
