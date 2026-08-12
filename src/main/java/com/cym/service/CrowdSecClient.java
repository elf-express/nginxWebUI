package com.cym.service;

import java.util.ArrayList;
import java.util.List;

import org.noear.solon.annotation.Component;
import org.noear.solon.annotation.Inject;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * CrowdSec Local API (LAPI) HTTP client.
 * <p>
 * Uses SettingService keys {@code crowdsecUrl} + {@code crowdsecApiKey}.
 * Decision bodies are built via hutool {@link JSONObject} (no string-concat JSON).
 */
@Component
public class CrowdSecClient {

	public static final int DEFAULT_TIMEOUT_MS = 15_000;
	public static final int LIST_PAGE_SIZE = 100;
	/** Cap pages when scanning for delete-by-reason to avoid infinite loops. */
	public static final int LIST_MAX_PAGES = 100;

	@Inject
	SettingService settingService;

	// ── pure helpers (unit-testable) ───────────────────────────────────

	/**
	 * Build a CrowdSec decision POST body as JSON.
	 * Uses JSONObject so reason/value with quotes or control chars are escaped.
	 */
	public static String buildDecisionBody(String duration, String reason, String scope, String value, String type) {
		JSONObject body = new JSONObject();
		body.set("duration", duration);
		body.set("reason", reason);
		body.set("scope", scope);
		body.set("value", value);
		body.set("type", type);
		return body.toString();
	}

	/**
	 * Collect decision ids whose {@code reason} matches prefix and/or equals mode.
	 *
	 * @param decisionsJson raw GET /v1/decisions body (JSON array, or null/blank)
	 * @param reasonPrefix  if non-blank and {@code equalsOnly==false}, match startsWith
	 * @param reasonEquals  if non-blank and {@code equalsOnly==true}, match exact equals
	 * @param equalsOnly    true → exact match on reasonEquals; false → prefix match
	 * @return list of id strings (may be empty)
	 */
	public static List<String> matchDecisionIds(String decisionsJson, String reasonPrefix, String reasonEquals,
			boolean equalsOnly) {
		List<String> ids = new ArrayList<>();
		if (StrUtil.isBlank(decisionsJson) || "null".equalsIgnoreCase(decisionsJson.trim())) {
			return ids;
		}
		if (!JSONUtil.isTypeJSONArray(decisionsJson)) {
			return ids;
		}
		JSONArray arr = JSONUtil.parseArray(decisionsJson);
		for (int i = 0; i < arr.size(); i++) {
			JSONObject o = arr.getJSONObject(i);
			if (o == null) {
				continue;
			}
			String reason = o.getStr("reason");
			if (reason == null) {
				reason = "";
			}
			boolean match;
			if (equalsOnly) {
				match = reasonEquals != null && reasonEquals.equals(reason);
			} else {
				match = reasonPrefix != null && reason.startsWith(reasonPrefix);
			}
			if (!match) {
				continue;
			}
			Object idObj = o.get("id");
			if (idObj == null) {
				continue;
			}
			String id = String.valueOf(idObj);
			if (StrUtil.isNotBlank(id) && !"null".equals(id)) {
				ids.add(id);
			}
		}
		return ids;
	}

	// ── config ─────────────────────────────────────────────────────────

	public boolean isConfigured() {
		return StrUtil.isNotBlank(base()) && StrUtil.isNotBlank(apiKey());
	}

	String base() {
		String url = settingService.get("crowdsecUrl");
		if (url == null) {
			return "";
		}
		// strip trailing slash for consistent path join
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	String apiKey() {
		String key = settingService.get("crowdsecApiKey");
		return key != null ? key : "";
	}

	void ensureConfigured() {
		if (!isConfigured()) {
			throw new IllegalStateException("notConfigured");
		}
	}

	// ── ban / whitelist ────────────────────────────────────────────────

	/**
	 * Ban a CIDR range ({@code scope=range}).
	 */
	public void banRange(String cidr, String duration, String reason) {
		postDecision(duration, reason, "range", cidr, "ban");
	}

	/**
	 * Ban a single IP ({@code scope=ip}) — same shape as legacy addDecision.
	 */
	public void banIp(String ip, String duration, String reason) {
		postDecision(duration, reason, "ip", ip, "ban");
	}

	/**
	 * Whitelist a single IP via LAPI decision {@code type=whitelist}, {@code scope=ip}.
	 * CrowdSec LAPI accepts whitelist decisions the same way as ban.
	 */
	public void whitelistIp(String ip, String duration, String reason) {
		postDecision(duration, reason, "ip", ip, "whitelist");
	}

	void postDecision(String duration, String reason, String scope, String value, String type) {
		ensureConfigured();
		if (StrUtil.isBlank(value)) {
			throw new IllegalArgumentException("empty value");
		}
		String jsonBody = buildDecisionBody(duration, reason, scope, value, type);
		HttpResponse resp = HttpRequest.post(base() + "/v1/decisions")
				.header("X-Api-Key", apiKey())
				.header("Content-Type", "application/json")
				.body(jsonBody)
				.timeout(DEFAULT_TIMEOUT_MS)
				.execute();
		if (!resp.isOk()) {
			throw new IllegalStateException(
					"crowdsec " + type + " " + scope + " HTTP " + resp.getStatus() + " " + resp.body());
		}
	}

	// ── delete by reason ───────────────────────────────────────────────

	/**
	 * Delete all decisions whose reason starts with {@code reasonPrefix}.
	 *
	 * @return number of successfully deleted decisions
	 */
	public int deleteDecisionsByReasonPrefix(String reasonPrefix) {
		if (StrUtil.isBlank(reasonPrefix)) {
			throw new IllegalArgumentException("empty reasonPrefix");
		}
		return deleteMatching(reasonPrefix, null, false);
	}

	/**
	 * Delete all decisions whose reason equals {@code reason} exactly.
	 *
	 * @return number of successfully deleted decisions
	 */
	public int deleteDecisionsByReasonEquals(String reason) {
		if (StrUtil.isBlank(reason)) {
			throw new IllegalArgumentException("empty reason");
		}
		return deleteMatching(null, reason, true);
	}

	/**
	 * Delete a single decision by id (LAPI {@code DELETE /v1/decisions/{id}}).
	 */
	public void deleteDecision(String decisionId) {
		ensureConfigured();
		if (StrUtil.isBlank(decisionId)) {
			throw new IllegalArgumentException("empty decisionId");
		}
		// id is path segment — reject path injection
		if (decisionId.indexOf('/') >= 0 || decisionId.indexOf('?') >= 0 || decisionId.indexOf('#') >= 0) {
			throw new IllegalArgumentException("invalid decisionId");
		}
		HttpResponse resp = HttpRequest.delete(base() + "/v1/decisions/" + decisionId)
				.header("X-Api-Key", apiKey())
				.timeout(DEFAULT_TIMEOUT_MS)
				.execute();
		if (!resp.isOk()) {
			throw new IllegalStateException(
					"crowdsec deleteDecision HTTP " + resp.getStatus() + " " + resp.body());
		}
	}

	/**
	 * GET one page of decisions (raw body). Used by controller + internal scan.
	 */
	public String listDecisionsRaw(int limit, int offset) {
		ensureConfigured();
		if (limit <= 0) {
			limit = LIST_PAGE_SIZE;
		}
		if (offset < 0) {
			offset = 0;
		}
		HttpResponse resp = HttpRequest.get(base() + "/v1/decisions?limit=" + limit + "&offset=" + offset)
				.header("X-Api-Key", apiKey())
				.timeout(DEFAULT_TIMEOUT_MS)
				.execute();
		if (!resp.isOk()) {
			throw new IllegalStateException(
					"crowdsec listDecisions HTTP " + resp.getStatus() + " " + resp.body());
		}
		return resp.body();
	}

	int deleteMatching(String reasonPrefix, String reasonEquals, boolean equalsOnly) {
		ensureConfigured();
		int deleted = 0;
		// Collect ids across pages first, then delete — avoids offset shift while deleting.
		List<String> toDelete = new ArrayList<>();
		for (int page = 0; page < LIST_MAX_PAGES; page++) {
			int offset = page * LIST_PAGE_SIZE;
			String body = listDecisionsRaw(LIST_PAGE_SIZE, offset);
			List<String> pageIds = matchDecisionIds(body, reasonPrefix, reasonEquals, equalsOnly);
			toDelete.addAll(pageIds);
			// stop when page shorter than limit (or empty / non-array)
			if (StrUtil.isBlank(body) || "null".equalsIgnoreCase(body.trim())) {
				break;
			}
			if (!JSONUtil.isTypeJSONArray(body)) {
				break;
			}
			JSONArray arr = JSONUtil.parseArray(body);
			if (arr.size() < LIST_PAGE_SIZE) {
				break;
			}
		}
		for (String id : toDelete) {
			try {
				deleteDecision(id);
				deleted++;
			} catch (IllegalStateException e) {
				// continue remaining; caller sees partial count
			}
		}
		return deleted;
	}
}
