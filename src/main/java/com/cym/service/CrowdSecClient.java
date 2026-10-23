package com.cym.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;

import org.noear.solon.annotation.Component;

import com.cym.utils.CrowdSecCredentials;
import com.cym.utils.IpMatch;
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
	/**
	 * 對 LAPI 所有請求帶的 User-Agent。LAPI 的 /v1/watchers/login 會拒絕不是「名稱/版本」形式的 User-Agent（回 401），
	 * hutool 預設的 User-Agent 不符；命名沿用倉庫其他外部請求的 {@code nginxWebUI/<用途>}。
	 */
	public static final String USER_AGENT = "nginxWebUI/CrowdSec";
	/** JWT 在 expire 前多久就重新登入 */
	static final long TOKEN_RENEW_BEFORE_MS = 60_000;
	/** 登入回應的 expire 無法解析時，token 視為有效的時間 */
	static final long TOKEN_FALLBACK_TTL_MS = 10 * 60_000;

	public static final String OK = "ok";
	public static final String INVALID = "invalid";
	public static final String UNREACHABLE = "unreachable";
	public static final String NOT_CONFIGURED = "notConfigured";
	public static final String UNCHECKED = "unchecked";

	/** machine 帳號登入被 LAPI 拒絕（401／403），或重新登入後 LAPI 仍拒絕 JWT */
	public static class MachineAuthException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		public static final String CODE = "crowdsec_machine_unauthorized";

		public MachineAuthException() {
			super(CODE);
		}
	}

	/** LAPI 暫時無法使用：登入回 401／403 以外的狀態碼（5xx、429 等）或連線失敗。訊息只含狀態碼，不含密碼與回應內容 */
	public static class LapiUnavailableException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		public static final String CODE = "crowdsec_lapi_unavailable";

		public LapiUnavailableException(String message) {
			super(message);
		}

		public LapiUnavailableException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	/** 端點需要的憑證沒設定；訊息是固定錯誤碼，指出缺的是哪一種 */
	public static class NotConfiguredException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		/** 缺 LAPI 位址或 bouncer 金鑰（讀名單） */
		public static final String BOUNCER_KEY = "notConfigured.bouncerKey";
		/** 缺 LAPI 位址或 machine 密碼（新增、刪除封鎖） */
		public static final String MACHINE = "notConfigured.machine";

		public NotConfiguredException(String code) {
			super(code);
		}
	}

	/** 本機封鎖的 decision origin（行為偵測、cscli／後台、console、WAF） */
	public static final String LOCAL_ORIGINS = "crowdsec,cscli,cscli-import,console,appsec";
	/** 社群名單的 decision origin */
	public static final String COMMUNITY_ORIGINS = "CAPI,lists";

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

	/** 可以讀名單：LAPI 位址與 bouncer 金鑰都有值 */
	public boolean canRead() {
		return credentials.lapiUrl() != null && credentials.bouncerKey().isPresent();
	}

	void ensureConfigured() {
		if (!isConfigured()) {
			throw new NotConfiguredException(NotConfiguredException.MACHINE);
		}
	}

	void ensureBouncerKey() {
		if (!canRead()) {
			throw new NotConfiguredException(NotConfiguredException.BOUNCER_KEY);
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
			HttpResponse loginResp;
			try {
				loginResp = HttpRequest.post(base() + "/v1/watchers/login")
						.header("User-Agent", USER_AGENT)
						.header("Content-Type", "application/json")
						.body(body.toString())
						.timeout(timeoutMs)
						.execute();
			} catch (RuntimeException e) {
				throw new LapiUnavailableException("crowdsec machine login unreachable", e);
			}
			try (HttpResponse resp = loginResp) {
				int status = resp.getStatus();
				if (status == 401 || status == 403) {
					throw new MachineAuthException();
				}
				if (status != 200) {
					throw new LapiUnavailableException("crowdsec machine login HTTP " + status);
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
				.header("User-Agent", USER_AGENT)
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
		try (HttpResponse resp = sendWithJwt(t -> HttpRequest.delete(url)
				.header("User-Agent", USER_AGENT)
				.bearerAuth(t)
				.timeout(DEFAULT_TIMEOUT_MS))) {
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
				.header("User-Agent", USER_AGENT)
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
				.header("User-Agent", USER_AGENT)
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
				.header("User-Agent", USER_AGENT)
				.bearerAuth(t)
				.timeout(DEFAULT_TIMEOUT_MS))) {
			if (!resp.isOk()) {
				throw new IllegalStateException("crowdsec listAlerts HTTP " + resp.getStatus() + " " + resp.body());
			}
			return resp.body();
		}
	}

	/** 涵蓋這個 IP 的有效 decisions（含網段），bouncer 金鑰 */
	public String decisionsForIpRaw(String ip) {
		ensureBouncerKey();
		if (!IpMatch.isSingleIp(ip)) {
			throw new IllegalArgumentException(NetGuard.ERR_INVALID_CIDR);
		}
		return getDecisions("ip=" + URLEncoder.encode(ip.trim(), StandardCharsets.UTF_8));
	}

	/** 封鎖名單：只列 ban 類型；community=false 為本機，true 為社群名單 */
	public String listBansRaw(boolean community) {
		ensureBouncerKey();
		return getDecisions("type=ban&origins=" + (community ? COMMUNITY_ORIGINS : LOCAL_ORIGINS));
	}

	/** 有有效 decision 的 alert（含 created_at 與 decisions[].id），用來補封鎖時間；machine JWT */
	public String activeDecisionAlertsRaw() {
		ensureConfigured();
		String url = base() + "/v1/alerts?has_active_decision=true&include_capi=false&limit=1000";
		try (HttpResponse resp = sendWithJwt(t -> HttpRequest.get(url)
				.header("User-Agent", USER_AGENT)
				.bearerAuth(t)
				.timeout(DEFAULT_TIMEOUT_MS))) {
			if (!resp.isOk()) {
				throw new IllegalStateException("crowdsec activeDecisionAlerts HTTP " + resp.getStatus());
			}
			return resp.body();
		}
	}

	/** 刪除某 IP 的結果：刪掉的筆數、保留下來的網段封鎖筆數（網段可能是 ASN 推送，不因單一 IP 解除） */
	public record DeleteResult(int deleted, int rangesKept) {
	}

	/** 刪除 scope=Ip 且 value 等於這個 IP 的所有 decisions；涵蓋它的網段 decision 保留並計數 */
	public DeleteResult deleteIpDecisions(String ip) {
		ensureConfigured();
		String target = ip == null ? "" : ip.trim();
		JSONArray arr = parseArray(decisionsForIpRaw(target));
		int deleted = 0;
		int ranges = 0;
		for (int i = 0; i < arr.size(); i++) {
			JSONObject o = arr.getJSONObject(i);
			if (o == null) {
				continue;
			}
			if (!"ip".equalsIgnoreCase(o.getStr("scope", "")) || !target.equals(o.getStr("value"))) {
				ranges++;
				continue;
			}
			deleteDecision(String.valueOf(o.get("id")));
			deleted++;
		}
		return new DeleteResult(deleted, ranges);
	}

	/** 清除舊版後台建立的 whitelist decision（scenario 以 nginxwebui: 開頭）；回刪除筆數 */
	public int deleteLegacyWhitelistDecisions() {
		ensureConfigured();
		ensureBouncerKey();
		JSONArray arr = parseArray(getDecisions("type=whitelist"));
		int deleted = 0;
		for (int i = 0; i < arr.size(); i++) {
			JSONObject o = arr.getJSONObject(i);
			if (o != null && NetGuard.isAllowedWebuiReason(o.getStr("scenario"))) {
				deleteDecision(String.valueOf(o.get("id")));
				deleted++;
			}
		}
		return deleted;
	}

	/** 連線失敗（連不上、逾時）丟 LapiUnavailableException，訊息不含金鑰；LAPI 有回應但狀態碼不是 2xx 仍丟 IllegalStateException */
	private String getDecisions(String query) {
		HttpResponse response;
		try {
			response = HttpRequest.get(base() + "/v1/decisions?" + query)
					.header("User-Agent", USER_AGENT)
					.header("X-Api-Key", credentials.bouncerKey().value())
					.timeout(DEFAULT_TIMEOUT_MS)
					.execute();
		} catch (RuntimeException e) {
			throw new LapiUnavailableException("crowdsec decisions unreachable", e);
		}
		try (HttpResponse resp = response) {
			if (!resp.isOk()) {
				throw new IllegalStateException("crowdsec decisions HTTP " + resp.getStatus());
			}
			return resp.body();
		}
	}

	/** JSON 陣列字串轉 JSONArray；空白、null 或非陣列回空陣列。Task 3 的 controller 也會用，故為 public */
	public static JSONArray parseArray(String body) {
		return StrUtil.isNotBlank(body) && JSONUtil.isTypeJSONArray(body) ? JSONUtil.parseArray(body) : new JSONArray();
	}

	int deleteMatching(String reasonPrefix) {
		// 列名單要 bouncer 金鑰、刪除要 machine JWT：先確認兩者都有，缺的在送出任何請求前就回報
		ensureConfigured();
		ensureBouncerKey();
		// Collect ids across pages first, then delete — avoids offset shift while deleting.
		// 用 LinkedHashSet 去重；LAPI 若忽略 limit/offset 而每頁回同一份清單，某頁沒有新 id 就停止。
		Set<String> toDelete = new LinkedHashSet<>();
		Set<String> seen = new HashSet<>();
		for (int page = 0; page < LIST_MAX_PAGES; page++) {
			String body = listDecisions(LIST_PAGE_SIZE, page * LIST_PAGE_SIZE, reasonPrefix);
			toDelete.addAll(matchDecisionIds(body, reasonPrefix, null, false));
			if (!seen.addAll(matchDecisionIds(body, "", null, false))) {
				break;
			}
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
			} catch (MachineAuthException | LapiUnavailableException e) {
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
					.header("User-Agent", USER_AGENT)
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
			reached = statusOf(HttpRequest.get(lapi + "/v1/decisions").header("User-Agent", USER_AGENT)) != null;
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
