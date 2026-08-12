package com.cym.controller.adminPage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Mapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.DenyAllow;
import com.cym.service.CrowdSecClient;
import com.cym.service.DenyAllowService;
import com.cym.service.SettingService;
import com.cym.utils.BaseController;
import com.cym.utils.JsonResult;
import com.cym.utils.NetGuard;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

@Controller
@Mapping("/adminPage/crowdsec")
public class CrowdSecController extends BaseController {
	Logger logger = LoggerFactory.getLogger(this.getClass());

	/** Default duration for ban / whitelist when client omits it. */
	private static final String DEFAULT_DURATION = "4h";

	@Inject
	SettingService settingService;
	@Inject
	CrowdSecClient crowdSecClient;
	@Inject
	DenyAllowService denyAllowService;

	@Mapping("getConfig")
	public JsonResult getConfig() {
		String url = settingService.get("crowdsecUrl");
		String apiKey = settingService.get("crowdsecApiKey");
		Map<String, String> config = new LinkedHashMap<String, String>();
		config.put("url", url != null ? url : "");
		if (StrUtil.isNotBlank(apiKey) && apiKey.length() > 8) {
			config.put("apiKey", apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4));
		} else {
			config.put("apiKey", "");
		}
		config.put("configured", crowdSecClient.isConfigured() ? "true" : "false");
		return renderSuccess(config);
	}

	@Mapping("saveConfig")
	public JsonResult saveConfig(String url, String apiKey) {
		settingService.set("crowdsecUrl", url);
		if (StrUtil.isNotBlank(apiKey) && apiKey.contains("****")) {
			return renderSuccess();
		}
		settingService.set("crowdsecApiKey", apiKey);
		return renderSuccess();
	}

	@Mapping("status")
	public JsonResult status() {
		String url = settingService.get("crowdsecUrl");
		if (StrUtil.isBlank(url)) {
			return renderSuccess("notConfigured");
		}
		try {
			HttpResponse resp = HttpRequest.get(trimBase(url) + "/v1/health").timeout(5000).execute();
			if (resp.isOk()) {
				return renderSuccess("connected");
			} else {
				return renderSuccess("disconnected");
			}
		} catch (Exception e) {
			return renderSuccess("disconnected");
		}
	}

	@Mapping("alerts")
	public JsonResult alerts(int limit, int page) {
		String url = settingService.get("crowdsecUrl");
		String apiKey = settingService.get("crowdsecApiKey");
		if (StrUtil.isBlank(url) || StrUtil.isBlank(apiKey)) {
			return renderError("notConfigured");
		}
		try {
			int offset = (page - 1) * limit;
			HttpResponse resp = HttpRequest.get(trimBase(url) + "/v1/alerts?limit=" + limit + "&offset=" + offset)
					.header("X-Api-Key", apiKey).timeout(10000).execute();
			return renderSuccess(resp.body());
		} catch (Exception e) {
			return fail(e);
		}
	}

	@Mapping("decisions")
	public JsonResult decisions(int limit, int page) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		try {
			int offset = (page - 1) * limit;
			return renderSuccess(crowdSecClient.listDecisionsRaw(limit, offset));
		} catch (Exception e) {
			return fail(e);
		}
	}

	/**
	 * Ban a single IP — delegates to {@link CrowdSecClient#banIp}.
	 * Keeps legacy response shape (success with empty obj on OK).
	 * Client-side gate: blank duration → {@code 4h}; IP via NetGuard.
	 */
	@Mapping("addDecision")
	public JsonResult addDecision(String ip, String duration, String reason) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		JsonResult gate = gateIpAndDuration(ip, duration);
		if (gate != null) {
			return gate;
		}
		if (StrUtil.isBlank(duration)) {
			duration = DEFAULT_DURATION;
		} else {
			duration = duration.trim();
		}
		try {
			crowdSecClient.banIp(ip.trim(), duration, reason);
			return renderSuccess();
		} catch (Exception e) {
			return fail(e);
		}
	}

	/**
	 * Ban a CIDR range ({@code scope=range}).
	 *
	 * @param range CIDR e.g. {@code 1.2.3.0/24}
	 */
	@Mapping("addRangeDecision")
	public JsonResult addRangeDecision(String range, String duration, String reason) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		JsonResult gate = gateIpAndDuration(range, duration);
		if (gate != null) {
			return gate;
		}
		if (StrUtil.isBlank(duration)) {
			duration = DEFAULT_DURATION;
		} else {
			duration = duration.trim();
		}
		try {
			crowdSecClient.banRange(range.trim(), duration, reason);
			return renderSuccess();
		} catch (Exception e) {
			return fail(e);
		}
	}

	/**
	 * Whitelist a single IP via LAPI {@code type=whitelist}.
	 * Optional {@code syncDenyAllow=true} also inserts a site-wide DenyAllow type=allow
	 * for false-positive recovery (does not fail the CS whitelist if DenyAllow conflicts).
	 *
	 * @param duration default {@code 4h} when blank
	 * @param reason default {@code nginxwebui:fp-whitelist} when blank
	 * @param syncDenyAllow when true/1, create DenyAllow allow entry for the IP
	 */
	@Mapping("whitelistIp")
	public JsonResult whitelistIp(String ip, String duration, String reason, String syncDenyAllow) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		if (StrUtil.isBlank(ip)) {
			return renderError("ip_required");
		}
		ip = ip.trim();
		if (StrUtil.isBlank(duration)) {
			duration = DEFAULT_DURATION;
		} else {
			duration = duration.trim();
		}
		if (!NetGuard.isValidCidr(ip)) {
			return renderError(msgOr("crowdsecStr.invalidCidr", NetGuard.ERR_INVALID_CIDR));
		}
		if (!NetGuard.isValidDuration(duration)) {
			return renderError(msgOr("crowdsecStr.invalidDuration", NetGuard.ERR_INVALID_DURATION));
		}
		if (StrUtil.isBlank(reason)) {
			reason = "nginxwebui:fp-whitelist";
		}
		try {
			crowdSecClient.whitelistIp(ip, duration, reason);
		} catch (Exception e) {
			return fail(e);
		}

		boolean sync = "true".equalsIgnoreCase(syncDenyAllow) || "1".equals(syncDenyAllow)
				|| "on".equalsIgnoreCase(syncDenyAllow);
		if (sync) {
			try {
				syncDenyAllowIp(ip);
			} catch (Exception e) {
				logger.warn("CrowdSec whitelist ok but DenyAllow sync failed for {}: {}", ip, e.getMessage());
				return renderSuccess("whitelist_ok_denyallow_failed");
			}
		}
		return renderSuccess();
	}

	/**
	 * Insert a DenyAllow type=allow for a single IP if no cross-type conflict.
	 * Skips insert when IP already present on an allow list.
	 */
	private void syncDenyAllowIp(String ip) {
		// already on an allow list?
		List<DenyAllow> allows = sqlHelper.findListByQuery(
				new com.cym.sqlhelper.utils.ConditionAndWrapper().eq("type", "allow"), DenyAllow.class);
		for (DenyAllow a : allows) {
			if (a.getIp() == null) {
				continue;
			}
			for (String line : a.getIp().split("\n")) {
				if (ip.equals(line.trim())) {
					return;
				}
			}
		}

		DenyAllow da = new DenyAllow();
		da.setName("CrowdSec FP " + ip);
		da.setIp(ip);
		da.setType("allow");
		List<String> conflicts = denyAllowService.findConflictIps(da, "allow");
		if (!conflicts.isEmpty()) {
			throw new IllegalStateException("denyallow_type_conflict");
		}
		denyAllowService.removeSame(da);
		sqlHelper.insertOrUpdate(da);
	}

	/**
	 * Delete decisions whose reason starts with {@code reasonPrefix}.
	 * Returns deleted count in {@code obj}.
	 */
	@Mapping("deleteByReason")
	public JsonResult deleteByReason(String reasonPrefix) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		try {
			int n = crowdSecClient.deleteDecisionsByReasonPrefix(reasonPrefix);
			return renderSuccess(n);
		} catch (Exception e) {
			return fail(e);
		}
	}

	@Mapping("deleteDecision")
	public JsonResult deleteDecision(String decisionId) {
		if (!crowdSecClient.isConfigured()) {
			return renderError("notConfigured");
		}
		try {
			crowdSecClient.deleteDecision(decisionId);
			return renderSuccess();
		} catch (Exception e) {
			return fail(e);
		}
	}

	// ── validation / error helpers ──────────────────────────────────────

	/**
	 * Shared pre-check for addDecision / addRangeDecision: CIDR + duration (blank → default later).
	 *
	 * @return null if OK; otherwise a renderError JsonResult
	 */
	private JsonResult gateIpAndDuration(String ipOrRange, String duration) {
		if (StrUtil.isBlank(ipOrRange) || !NetGuard.isValidCidr(ipOrRange.trim())) {
			return renderError(msgOr("crowdsecStr.invalidCidr", NetGuard.ERR_INVALID_CIDR));
		}
		String dur = duration;
		if (StrUtil.isBlank(dur)) {
			dur = DEFAULT_DURATION;
		}
		if (!NetGuard.isValidDuration(dur)) {
			return renderError(msgOr("crowdsecStr.invalidDuration", NetGuard.ERR_INVALID_DURATION));
		}
		return null;
	}

	/**
	 * Log full exception; map known NetGuard codes to i18n; never return e.getMessage() for unknown.
	 */
	private JsonResult fail(Exception e) {
		logger.error("crowdsec api failed", e);
		if (e instanceof IllegalArgumentException) {
			String msg = e.getMessage();
			if (NetGuard.ERR_INVALID_CIDR.equals(msg)) {
				return renderError(msgOr("crowdsecStr.invalidCidr", msg));
			}
			if (NetGuard.ERR_INVALID_DURATION.equals(msg)) {
				return renderError(msgOr("crowdsecStr.invalidDuration", msg));
			}
			if (NetGuard.ERR_INVALID_REASON.equals(msg)) {
				return renderError(msgOr("crowdsecStr.invalidReason", msg));
			}
		}
		return renderError(msgOr("crowdsecStr.error", "crowdsec_error"));
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

	private static String trimBase(String url) {
		if (url == null) {
			return "";
		}
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}
}
