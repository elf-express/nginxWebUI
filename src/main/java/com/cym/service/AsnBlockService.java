package com.cym.service;

import java.util.List;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

import com.cym.model.AsBlockIntent;
import com.cym.sqlhelper.utils.SqlHelper;

import cn.hutool.core.util.StrUtil;

/**
 * ASN big-block ban intents: protection profile gates, reasonTag, intent CRUD.
 * CrowdSec push is Task 5; this service only prepares gates + intents.
 */
@Component
public class AsnBlockService {

	public static final String SETTING_PROFILE = "protection.profile";
	public static final String PROFILE_LIGHT = "light";
	public static final String PROFILE_MANUAL = "manual";
	public static final String PROFILE_STRICT = "strict";

	@Inject
	SqlHelper sqlHelper;
	@Inject
	SettingService settingService;

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
		return "nginxwebui:as-ban:AS" + a;
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
	 * (Does not depend on CrowdSecClient yet — Task 4; checks setting keys directly.)
	 */
	public boolean canPush() {
		return canCreateIntentForProfile(getProfile()) && isCrowdSecConfigured();
	}

	/**
	 * CrowdSec configured = both URL and API key present.
	 * Matches CrowdSecController alerts/decisions gate.
	 */
	public boolean isCrowdSecConfigured() {
		return StrUtil.isNotBlank(settingService.get("crowdsecUrl"))
				&& StrUtil.isNotBlank(settingService.get("crowdsecApiKey"));
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
}
