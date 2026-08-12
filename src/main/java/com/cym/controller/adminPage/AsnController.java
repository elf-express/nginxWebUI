package com.cym.controller.adminPage;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.AsBlockIntent;
import com.cym.model.AsMeta;
import com.cym.model.AsnRule;
import com.cym.service.AsnBlockService;
import com.cym.service.AsnMetaService;
import com.cym.sqlhelper.bean.Page;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.StrUtil;

@Controller
@Mapping("/adminPage/asn")
public class AsnController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());

	@Inject
	AsnMetaService asnMetaService;
	@Inject
	AsnBlockService asnBlockService;

	/** Manual catalog sync single-flight (optional; schedule has its own flag). */
	private final AtomicBoolean metaSyncing = new AtomicBoolean(false);

	// ── legacy AsnRule CRUD (deprecated; nginx map only if asn.nginxMapEnabled=true) ──
	// Primary ASN big-block path = AsBlockIntent → CrowdSec. Soft-disable: list/del remain
	// for cleanup; addOver/setEnable still persist AsnRule but do not auto-push CS and do
	// not emit map unless the operator explicitly re-enables asn.nginxMapEnabled.

	@Mapping("list")
	public JsonResult list() {
		List<AsnRule> list = sqlHelper.findAll(AsnRule.class);
		return renderSuccess(list);
	}

	/**
	 * @deprecated Prefer {@link #addIntent}; AsnRule map is not the product primary path.
	 */
	@Mapping("addOver")
	public JsonResult addOver(AsnRule asnRule) {
		if (StrUtil.isBlank(asnRule.getAsn()) || !asnRule.getAsn().trim().matches("\\d+")) {
			return renderError(m.get("asnStr.invalidAsn"));
		}
		asnRule.setAsn(asnRule.getAsn().trim());

		// 檢查重複
		if (StrUtil.isEmpty(asnRule.getId())) {
			AsnRule existing = sqlHelper.findOneByQuery(
					new ConditionAndWrapper().eq("asn", asnRule.getAsn()), AsnRule.class);
			if (existing != null) {
				return renderError(m.get("asnStr.duplicate"));
			}
		}

		sqlHelper.insertOrUpdate(asnRule);
		return renderSuccess();
	}

	@Mapping("del")
	public JsonResult del(String id) {
		if (StrUtil.isNotEmpty(id)) {
			String[] ids = id.split(",");
			for (String oneId : ids) {
				sqlHelper.deleteById(oneId.trim(), AsnRule.class);
			}
		}
		return renderSuccess();
	}

	/**
	 * @deprecated Prefer intent revoke/push; map path is opt-in via asn.nginxMapEnabled.
	 */
	@Mapping("setEnable")
	public JsonResult setEnable(String id, Boolean enable) {
		AsnRule rule = sqlHelper.findById(id, AsnRule.class);
		if (rule != null) {
			rule.setEnable(enable);
			sqlHelper.updateById(rule);
		}
		return renderSuccess();
	}

	// ── AsMeta catalog ─────────────────────────────────────────────────

	/**
	 * Paged AsMeta search. Params: curr, limit (Page), q, category, countryCode.
	 */
	@Mapping("catalog")
	public JsonResult catalog(Page page, String q, String category, String countryCode) {
		if (page == null) {
			page = new Page();
		}
		if (page.getCurr() == null || page.getCurr() < 1) {
			page.setCurr(1);
		}
		if (page.getLimit() == null || page.getLimit() < 1) {
			page.setLimit(10);
		}
		return renderSuccess(asnMetaService.search(page, q, category, countryCode));
	}

	/**
	 * Manual AsMeta sync (async thread + single-flight).
	 */
	@Mapping("syncMeta")
	public JsonResult syncMeta() {
		if (!metaSyncing.compareAndSet(false, true)) {
			return renderError(msgOr("asnStr.syncInProgress", "sync_in_progress"));
		}
		new Thread(() -> {
			try {
				asnMetaService.syncFromRemote();
			} catch (Exception e) {
				logger.error("AsMeta manual sync failed", e);
			} finally {
				metaSyncing.set(false);
			}
		}, "as-meta-sync-manual").start();
		return renderSuccess();
	}

	// ── protection profile ─────────────────────────────────────────────

	@Mapping("profile")
	public JsonResult profile() {
		Map<String, Object> map = new HashMap<>();
		map.put("profile", asnBlockService.getProfile());
		map.put("crowdsecConfigured", asnBlockService.isCrowdSecConfigured());
		return renderSuccess(map);
	}

	/**
	 * Set profile=light|manual|strict (exact, case-sensitive). When switching to light,
	 * revokeMode=keep|revoke (Z). Invalid/blank profile is rejected (no silent normalize).
	 */
	@Mapping("setProfile")
	public JsonResult setProfile(String profile, String revokeMode) {
		if (!AsnBlockService.isValidProfile(profile)) {
			return renderError(mapServiceError("invalid_profile"));
		}
		String previous = asnBlockService.getProfile();
		String next = profile;

		// Z: leaving non-light → light, optionally revoke all WebUI ASN range bans
		boolean wantRevoke = AsnBlockService.PROFILE_LIGHT.equals(next)
				&& !AsnBlockService.PROFILE_LIGHT.equals(previous)
				&& "revoke".equalsIgnoreCase(StrUtil.blankToDefault(revokeMode, "keep"));

		// Check CrowdSec before mutating profile when revoke was requested
		if (wantRevoke && !asnBlockService.isCrowdSecConfigured()) {
			return renderError(mapServiceError("crowdsec_not_configured"));
		}

		asnBlockService.setProfile(next);

		if (wantRevoke) {
			try {
				int n = asnBlockService.revokeAllWebuiAsnBans();
				return renderSuccess(n);
			} catch (Exception e) {
				logger.error("revoke on setProfile light failed", e);
				// profile already light — client must loadProfile() to resync
				return renderError(StrUtil.blankToDefault(e.getMessage(), "revoke_failed"));
			}
		}
		return renderSuccess();
	}

	// ── block intents ──────────────────────────────────────────────────

	@Mapping("intents")
	public JsonResult intents() {
		return renderSuccess(asnBlockService.listIntents());
	}

	@Mapping("addIntent")
	public JsonResult addIntent(String asn, String duration, String note) {
		try {
			AsBlockIntent intent = asnBlockService.addIntent(asn, duration, note);
			return renderSuccess(intent);
		} catch (IllegalStateException e) {
			return renderError(mapServiceError(e.getMessage()));
		} catch (IllegalArgumentException e) {
			return renderError(mapServiceError(e.getMessage()));
		} catch (Exception e) {
			logger.error("addIntent failed", e);
			return renderError(StrUtil.blankToDefault(e.getMessage(), "add_intent_failed"));
		}
	}

	@Mapping("pushIntent")
	public JsonResult pushIntent(String id) {
		if (StrUtil.isBlank(id)) {
			return renderError("intent_not_found");
		}
		try {
			asnBlockService.pushIntent(id);
			AsBlockIntent intent = sqlHelper.findById(id, AsBlockIntent.class);
			return renderSuccess(intent);
		} catch (IllegalStateException e) {
			return renderError(mapServiceError(e.getMessage()));
		} catch (IllegalArgumentException e) {
			return renderError(mapServiceError(e.getMessage()));
		} catch (Exception e) {
			logger.error("pushIntent failed", e);
			return renderError(StrUtil.blankToDefault(e.getMessage(), "push_failed"));
		}
	}

	/**
	 * Revoke by intent id (resolve reasonTag) or by reasonTag directly.
	 */
	@Mapping("revokeIntent")
	public JsonResult revokeIntent(String id, String reasonTag) {
		try {
			if (StrUtil.isNotBlank(id)) {
				AsBlockIntent intent = sqlHelper.findById(id, AsBlockIntent.class);
				if (intent == null) {
					return renderError("intent_not_found");
				}
				reasonTag = intent.getReasonTag();
			}
			if (StrUtil.isBlank(reasonTag)) {
				return renderError("reason_tag_required");
			}
			if (!asnBlockService.isCrowdSecConfigured()) {
				return renderError(mapServiceError("crowdsec_not_configured"));
			}
			int n = asnBlockService.revokeByReasonTag(reasonTag);
			return renderSuccess(n);
		} catch (Exception e) {
			logger.error("revokeIntent failed", e);
			return renderError(StrUtil.blankToDefault(e.getMessage(), "revoke_failed"));
		}
	}

	/**
	 * Strict-only: insert candidate intents for AsMeta rows with the given category
	 * when no intent already exists for that asn. Default category=hosting.
	 */
	@Mapping("suggestCandidates")
	public JsonResult suggestCandidates(String category) {
		if (!asnBlockService.canSuggestCandidates()) {
			return renderError(mapServiceError("profile_disallows_suggest"));
		}
		if (StrUtil.isBlank(category)) {
			category = "hosting";
		}
		List<AsMeta> metas = sqlHelper.findListByQuery(
				new ConditionAndWrapper().eq("category", category), AsMeta.class);
		String profile = asnBlockService.getProfile();
		int added = 0;
		for (AsMeta meta : metas) {
			if (meta == null || StrUtil.isBlank(meta.getAsn())) {
				continue;
			}
			AsBlockIntent existing = sqlHelper.findOneByQuery(
					new ConditionAndWrapper().eq("asn", meta.getAsn()), AsBlockIntent.class);
			if (existing != null) {
				continue;
			}
			try {
				AsBlockIntent i = new AsBlockIntent();
				i.setAsn(meta.getAsn());
				i.setStatus(AsBlockIntent.STATUS_CANDIDATE);
				i.setDuration("24h");
				i.setReasonTag(AsnBlockService.reasonTagForAsn(meta.getAsn()));
				i.setCreatedByProfile(profile);
				i.setNote("candidate:" + category);
				sqlHelper.insert(i);
				added++;
			} catch (Exception e) {
				logger.warn("suggestCandidates skip asn={}: {}", meta.getAsn(), e.getMessage());
			}
		}
		return renderSuccess(added);
	}

	// ── error helpers ──────────────────────────────────────────────────

	/**
	 * Prefer i18n key when present; otherwise return English service error code.
	 */
	private String mapServiceError(String code) {
		if (code == null) {
			return "error";
		}
		switch (code) {
		case "profile_disallows_intent":
			return msgOr("asnStr.profileDisallowsIntent", code);
		case "profile_disallows_push":
			return msgOr("asnStr.profileDisallowsPush", code);
		case "profile_disallows_suggest":
			return msgOr("asnStr.profileDisallowsSuggest", code);
		case "crowdsec_not_configured":
			return msgOr("asnStr.crowdsecRequired", code);
		case "invalid_asn":
			return msgOr("asnStr.invalidAsn", code);
		case "invalid_profile":
			return msgOr("asnStr.invalidProfile", code);
		case "intent_not_found":
			return msgOr("asnStr.intentNotFound", code);
		default:
			return code;
		}
	}

	/** MessageUtils if key resolves; else English fallback. */
	private String msgOr(String key, String fallback) {
		try {
			String s = m.get(key);
			if (StrUtil.isNotBlank(s) && !s.equals(key)) {
				return s;
			}
		} catch (Exception ignored) {
			// fall through
		}
		return fallback;
	}
}
