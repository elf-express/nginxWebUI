package com.cym.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.noear.solon.annotation.Component;

import com.cym.utils.CrowdSecCredentials;
import com.cym.utils.NetGuard;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * CrowdSec Local API (LAPI) client.
 * <p>
 * 寫入（新增封鎖 POST /v1/alerts、刪除 DELETE /v1/decisions）用 machine 帳號登入取得的 JWT；
 * 讀封鎖名單（GET /v1/decisions）用 bouncer 金鑰。位址與憑證來自 {@link CrowdSecCredentials}，不讀資料庫。
 * <p>
 * Hard gates (NetGuard) run before any HTTP: duration/CIDR on post, webui reason on delete-by-reason.
 */
@Component
public class CrowdSecClient {

	public static final int DEFAULT_TIMEOUT_MS = 15_000;
	public static final int PROBE_TIMEOUT_MS = 3_000;
	public static final int LIST_PAGE_SIZE = 100;
	/** Cap pages when scanning for delete-by-reason to avoid infinite loops. */
	public static final int LIST_MAX_PAGES = 100;
	/** 與 cscli decisions add 相同的 decision origin */
	public static final String ORIGIN = "cscli";
	/** reason 空白時使用的 scenario */
	public static final String DEFAULT_REASON = "nginxwebui:manual";
	/** reason 不是 nginxwebui: 開頭時，scenario 加上的前綴 */
	public static final String CUSTOM_REASON_PREFIX = "nginxwebui:manual:";
	/** JWT 在 expire 前多久就重新登入 */
	static final long TOKEN_RENEW_BEFORE_MS = 60_000;
	/** 登入回應的 expire 無法解析時，token 視為有效的時間 */
	static final long TOKEN_FALLBACK_TTL_MS = 10 * 60_000;

	public static final String OK = "ok";
	public static final String INVALID = "invalid";
	public static final String UNREACHABLE = "unreachable";
	public static final String NOT_CONFIGURED = "notConfigured";
	public static final String UNCHECKED = "unchecked";

	/** machine 帳號登入失敗，或重新登入後 LAPI 仍拒絕 JWT */
	public static class MachineAuthException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		public static final String CODE = "crowdsec_machine_unauthorized";

		public MachineAuthException() {
			super(CODE);
		}
	}

	/** 測試連線結果：每項為 ok / invalid / unreachable / notConfigured / unchecked */
	public record Probe(String lapi, String bouncerKey, String machine) {
		public Map<String, Object> toMap() {
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("lapi", lapi);
			map.put("bouncerKey", bouncerKey);
			map.put("machine", machine);
			return map;
		}
	}

	CrowdSecCredentials credentials = CrowdSecCredentials.system();
	LongSupplier clock = System::currentTimeMillis;

	private final Object tokenLock = new Object();
	private String token;
	private long tokenExpiresAt;

	// ── pure helpers (unit-testable) ───────────────────────────────────

	/** Reject non-CIDR / non-IP values (NetGuard). Package-visible for unit tests. */
	static void requireCidr(String value) {
		if (!NetGuard.isValidCidr(value)) {
			throw new IllegalArgumentException(NetGuard.ERR_INVALID_CIDR);
		}
	}

	/** Reject blank or non-duration strings (NetGuard). Package-visible for unit tests. */
	static void requireDuration(String duration) {
		if (!NetGuard.isValidDuration(duration)) {
			throw new IllegalArgumentException(NetGuard.ERR_INVALID_DURATION);
		}
	}

	/**
	 * Reject reasons that are not {@code nginxwebui:*} (NetGuard).
	 * Used only on delete-by-reason paths — ban reason stays free for manual add.
	 */
	static void requireWebuiReason(String reason) {
		if (!NetGuard.isAllowedWebuiReason(reason)) {
			throw new IllegalArgumentException(NetGuard.ERR_INVALID_REASON);
		}
	}

	/**
	 * POST /v1/alerts 的 body：一個 alert 帶一個 decision，欄位值與 cscli decisions add 相同。
	 * scenario 規則：reason 空白 → {@code nginxwebui:manual}；trim 後已以 {@code nginxwebui:} 開頭 → 照用；
	 * 其他 → {@code nginxwebui:manual:<reason>}（讓自訂 reason 的手動封鎖也帶 nginxwebui: 前綴）。
	 * 用 JSONObject 組裝，reason 內的引號會正確跳脫。
	 */
	public static String buildAlertBody(String duration, String reason, String scope, String value, String type,
			Instant now) {
		String scenario = scenarioOf(reason);
		String time = DateTimeFormatter.ISO_INSTANT.format(now.truncatedTo(ChronoUnit.SECONDS));

		JSONObject decision = new JSONObject();
		decision.set("duration", duration);
		decision.set("origin", ORIGIN);
		decision.set("scenario", scenario);
		decision.set("scope", scope);
		decision.set("type", type);
		decision.set("value", value);
		JSONArray decisions = new JSONArray();
		decisions.add(decision);

		JSONObject source = new JSONObject();
		source.set("scope", scope);
		source.set("value", value);
		source.set("ip", value);

		JSONObject alert = new JSONObject();
		alert.set("scenario", scenario);
		alert.set("scenario_hash", "");
		alert.set("scenario_version", "");
		alert.set("message", scenario);
		alert.set("events_count", 1);
		alert.set("events", new JSONArray());
		alert.set("capacity", 0);
		alert.set("leakspeed", "0");
		alert.set("simulated", false);
		alert.set("remediation", true);
		alert.set("start_at", time);
		alert.set("stop_at", time);
		alert.set("source", source);
		alert.set("decisions", decisions);

		JSONArray body = new JSONArray();
		body.add(alert);
		return body.toString();
	}

	/** reason 轉成 scenario（規則見 {@link #buildAlertBody}） */
	static String scenarioOf(String reason) {
		if (StrUtil.isBlank(reason)) {
			return DEFAULT_REASON;
		}
		String trimmed = reason.trim();
		return trimmed.startsWith("nginxwebui:") ? trimmed : CUSTOM_REASON_PREFIX + trimmed;
	}

	/**
	 * Collect decision ids whose {@code scenario} (LAPI 的 reason 欄位) matches prefix and/or equals mode.
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
			String scenario = o.getStr("scenario");
			if (scenario == null) {
				scenario = "";
			}
			boolean match;
			if (equalsOnly) {
				match = reasonEquals != null && reasonEquals.equals(scenario);
			} else {
				match = reasonPrefix != null && scenario.startsWith(reasonPrefix);
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

	/** watchers/login 回應的 expire（RFC 3339）轉 epoch 毫秒；無法解析時 now + 10 分鐘 */
	static long parseExpire(String expire, long now) {
		try {
			return OffsetDateTime.parse(expire).toInstant().toEpochMilli();
		} catch (RuntimeException e) {
			return now + TOKEN_FALLBACK_TTL_MS;
		}
	}

	/** DeleteDecisionResponse 的 nbDeleted（字串）轉整數；無法解析時 0 */
	static int parseDeleted(String body) {
		try {
			return Integer.parseInt(JSONUtil.parseObj(body).getStr("nbDeleted", "0").trim());
		} catch (RuntimeException e) {
			return 0;
		}
	}

	// ── config ─────────────────────────────────────────────────────────

	/** 可以寫入（新增／刪除封鎖）：LAPI 位址與 machine 密碼都有值 */
	public boolean isConfigured() {
		return credentials.lapiUrl() != null && credentials.machinePassword().isPresent();
	}

	String base() {
		String url = credentials.lapiUrl();
		return url == null ? "" : url;
	}

	void ensureConfigured() {
		if (!isConfigured()) {
			throw new IllegalStateException("notConfigured");
		}
	}

	void ensureBouncerKey() {
		if (credentials.lapiUrl() == null || !credentials.bouncerKey().isPresent()) {
			throw new IllegalStateException("notConfigured");
		}
	}

	// ── machine JWT ────────────────────────────────────────────────────

	String token() {
		return token(DEFAULT_TIMEOUT_MS);
	}

	/** 快取中的 JWT 離 expire 超過 60 秒就沿用，否則 POST /v1/watchers/login 重新取得 */
	String token(int timeoutMs) {
		synchronized (tokenLock) {
			long now = clock.getAsLong();
			if (token != null && now < tokenExpiresAt - TOKEN_RENEW_BEFORE_MS) {
				return token;
			}
			token = null;
			JSONObject body = new JSONObject();
			body.set("machine_id", credentials.machineId());
			body.set("password", credentials.machinePassword().value());
			try (HttpResponse resp = HttpRequest.post(base() + "/v1/watchers/login")
					.header("Content-Type", "application/json")
					.body(body.toString())
					.timeout(timeoutMs)
					.execute()) {
				if (resp.getStatus() != 200) {
					throw new MachineAuthException();
				}
				String text = resp.body();
				JSONObject o = JSONUtil.isTypeJSONObject(text) ? JSONUtil.parseObj(text) : new JSONObject();
				String t = o.getStr("token");
				if (StrUtil.isBlank(t)) {
					throw new MachineAuthException();
				}
				token = t;
				tokenExpiresAt = parseExpire(o.getStr("expire"), now);
				return token;
			}
		}
	}

	void invalidateToken() {
		synchronized (tokenLock) {
			token = null;
			tokenExpiresAt = 0;
		}
	}

	/** 帶 JWT 送出；回 401 時重新登入並重送一次，仍 401 就丟 MachineAuthException */
	private HttpResponse sendWithJwt(Function<String, HttpRequest> request) {
		HttpResponse resp = request.apply(token()).execute();
		if (resp.getStatus() != 401) {
			return resp;
		}
		resp.close();
		invalidateToken();
		resp = request.apply(token()).execute();
		if (resp.getStatus() == 401) {
			resp.close();
			invalidateToken();
			throw new MachineAuthException();
		}
		return resp;
	}

	// ── ban / whitelist ────────────────────────────────────────────────

	/**
	 * Ban a CIDR range ({@code scope=range}).
	 */
	public void banRange(String cidr, String duration, String reason) {
		postDecision(duration, reason, "range", cidr, "ban");
	}

	/**
	 * Ban a single IP ({@code scope=ip}).
	 */
	public void banIp(String ip, String duration, String reason) {
		postDecision(duration, reason, "ip", ip, "ban");
	}

	/**
	 * Whitelist a single IP via a decision {@code type=whitelist}, {@code scope=ip}.
	 */
	public void whitelistIp(String ip, String duration, String reason) {
		postDecision(duration, reason, "ip", ip, "whitelist");
	}

	/**
	 * Pure input gates for {@link #postDecision} — unit-tested so removing call-site gates fails CI.
	 * Ban reason is free text; duration always; CIDR for ip/range.
	 */
	static void validatePostDecisionInputs(String duration, String scope, String value) {
		requireDuration(duration);
		if ("range".equals(scope) || "ip".equals(scope)) {
			requireCidr(value);
		} else if (StrUtil.isBlank(value)) {
			throw new IllegalArgumentException("empty value");
		}
	}

	void postDecision(String duration, String reason, String scope, String value, String type) {
		ensureConfigured();
		// Hard gates before HTTP — ban reason is free text (manual add OK); duration/CIDR always.
		validatePostDecisionInputs(duration, scope, value);
		String jsonBody = buildAlertBody(duration, reason, scope, value, type, Instant.ofEpochMilli(clock.getAsLong()));
		try (HttpResponse resp = sendWithJwt(t -> HttpRequest.post(base() + "/v1/alerts")
				.bearerAuth(t)
				.header("Content-Type", "application/json")
				.body(jsonBody)
				.timeout(DEFAULT_TIMEOUT_MS))) {
			if (!resp.isOk()) {
				throw new IllegalStateException(
						"crowdsec " + type + " " + scope + " HTTP " + resp.getStatus() + " " + resp.body());
			}
		}
	}

	// ── delete ─────────────────────────────────────────────────────────

	/**
	 * Delete all decisions whose reason (scenario) starts with {@code reasonPrefix}:
	 * 用 bouncer 金鑰列出名單，再以 JWT 逐筆刪除。Prefix must be {@code nginxwebui:*}.
	 *
	 * @return number of successfully deleted decisions
	 */
	public int deleteDecisionsByReasonPrefix(String reasonPrefix) {
		requireWebuiReason(reasonPrefix);
		return deleteMatching(reasonPrefix.trim());
	}

	/**
	 * Delete all decisions whose reason (scenario) equals {@code reason}:
	 * {@code DELETE /v1/decisions?scenario=<reason>}. Reason must be {@code nginxwebui:*}.
	 *
	 * @return nbDeleted reported by LAPI
	 */
	public int deleteDecisionsByReasonEquals(String reason) {
		requireWebuiReason(reason);
		ensureConfigured();
		String url = base() + "/v1/decisions?scenario=" + URLEncoder.encode(reason.trim(), StandardCharsets.UTF_8);
		try (HttpResponse resp = sendWithJwt(t -> HttpRequest.delete(url).bearerAuth(t).timeout(DEFAULT_TIMEOUT_MS))) {
			if (!resp.isOk()) {
				throw new IllegalStateException("crowdsec deleteDecisions HTTP " + resp.getStatus() + " " + resp.body());
			}
			return parseDeleted(resp.body());
		}
	}

	/**
	 * Delete a single decision by id (LAPI {@code DELETE /v1/decisions/{id}}, JWT).
	 */
	public void deleteDecision(String decisionId) {
		ensureConfigured();
		if (StrUtil.isBlank(decisionId) || !decisionId.trim().matches("\\d+")) {
			throw new IllegalArgumentException("invalid decisionId");
		}
		String id = decisionId.trim();
		try (HttpResponse resp = sendWithJwt(t -> HttpRequest.delete(base() + "/v1/decisions/" + id)
				.bearerAuth(t)
				.timeout(DEFAULT_TIMEOUT_MS))) {
			if (!resp.isOk()) {
				throw new IllegalStateException(
						"crowdsec deleteDecision HTTP " + resp.getStatus() + " " + resp.body());
			}
		}
	}

	// ── read ───────────────────────────────────────────────────────────

	/**
	 * GET one page of decisions with the bouncer key (raw body). Used by controller + internal scan.
	 */
	public String listDecisionsRaw(int limit, int offset) {
		return listDecisions(limit, offset, null);
	}

	String listDecisions(int limit, int offset, String scenariosContaining) {
		ensureBouncerKey();
		if (limit <= 0) {
			limit = LIST_PAGE_SIZE;
		}
		if (offset < 0) {
			offset = 0;
		}
		String url = base() + "/v1/decisions?limit=" + limit + "&offset=" + offset;
		if (StrUtil.isNotBlank(scenariosContaining)) {
			url += "&scenarios_containing=" + URLEncoder.encode(scenariosContaining, StandardCharsets.UTF_8);
		}
		try (HttpResponse resp = HttpRequest.get(url)
				.header("X-Api-Key", credentials.bouncerKey().value())
				.timeout(DEFAULT_TIMEOUT_MS)
				.execute()) {
			if (!resp.isOk()) {
				throw new IllegalStateException("crowdsec listDecisions HTTP " + resp.getStatus() + " " + resp.body());
			}
			return resp.body();
		}
	}

	/** GET /v1/alerts?limit= with JWT (raw body). */
	public String listAlertsRaw(int limit) {
		ensureConfigured();
		int size = limit <= 0 ? LIST_PAGE_SIZE : limit;
		try (HttpResponse resp = sendWithJwt(t -> HttpRequest.get(base() + "/v1/alerts?limit=" + size)
				.bearerAuth(t)
				.timeout(DEFAULT_TIMEOUT_MS))) {
			if (!resp.isOk()) {
				throw new IllegalStateException("crowdsec listAlerts HTTP " + resp.getStatus() + " " + resp.body());
			}
			return resp.body();
		}
	}

	int deleteMatching(String reasonPrefix) {
		ensureConfigured();
		// Collect ids across pages first, then delete — avoids offset shift while deleting.
		List<String> toDelete = new ArrayList<>();
		for (int page = 0; page < LIST_MAX_PAGES; page++) {
			String body = listDecisions(LIST_PAGE_SIZE, page * LIST_PAGE_SIZE, reasonPrefix);
			toDelete.addAll(matchDecisionIds(body, reasonPrefix, null, false));
			if (StrUtil.isBlank(body) || "null".equalsIgnoreCase(body.trim()) || !JSONUtil.isTypeJSONArray(body)) {
				break;
			}
			if (JSONUtil.parseArray(body).size() < LIST_PAGE_SIZE) {
				break;
			}
		}
		int deleted = 0;
		for (String id : toDelete) {
			try {
				deleteDecision(id);
				deleted++;
			} catch (MachineAuthException e) {
				throw e;
			} catch (IllegalStateException e) {
				// continue remaining; caller sees partial count
			}
		}
		return deleted;
	}

	// ── probe ──────────────────────────────────────────────────────────

	/**
	 * 測試連線：有 bouncer 金鑰就 GET /v1/decisions?ip=127.0.0.1（200 為 ok，其他狀態碼為 invalid）；
	 * 有 machine 密碼就重新登入一次。只要收到任何 HTTP 回應就算 LAPI 連得到。
	 */
	public Probe probe() {
		String lapi = credentials.lapiUrl();
		boolean hasKey = credentials.bouncerKey().isPresent();
		boolean hasMachine = credentials.machinePassword().isPresent();
		if (lapi == null) {
			return new Probe(NOT_CONFIGURED, hasKey ? UNCHECKED : NOT_CONFIGURED, hasMachine ? UNCHECKED : NOT_CONFIGURED);
		}
		boolean reached = false;
		String key = NOT_CONFIGURED;
		if (hasKey) {
			Integer status = statusOf(HttpRequest.get(lapi + "/v1/decisions?ip=127.0.0.1")
					.header("X-Api-Key", credentials.bouncerKey().value()));
			if (status == null) {
				key = UNREACHABLE;
			} else {
				reached = true;
				key = status == 200 ? OK : INVALID;
			}
		}
		String machine = NOT_CONFIGURED;
		if (hasMachine) {
			invalidateToken();
			try {
				token(PROBE_TIMEOUT_MS);
				machine = OK;
				reached = true;
			} catch (MachineAuthException e) {
				machine = INVALID;
				reached = true;
			} catch (RuntimeException e) {
				machine = UNREACHABLE;
			}
		}
		if (!hasKey && !hasMachine) {
			reached = statusOf(HttpRequest.get(lapi + "/v1/decisions")) != null;
		}
		return new Probe(reached ? OK : UNREACHABLE, key, machine);
	}

	/** 回 HTTP 狀態碼；連不上或逾時回 null */
	private static Integer statusOf(HttpRequest request) {
		try (HttpResponse resp = request.timeout(PROBE_TIMEOUT_MS).execute()) {
			return resp.getStatus();
		} catch (RuntimeException e) {
			return null;
		}
	}
}
