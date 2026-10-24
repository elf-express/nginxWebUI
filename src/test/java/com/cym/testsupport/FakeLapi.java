package com.cym.testsupport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * 測試用的假 CrowdSec LAPI（JDK HttpServer，綁 127.0.0.1 隨機埠）。
 * 端點與驗證照 LAPI swagger：watchers/login 回 token＋expire；alerts 與 DELETE decisions 要 Bearer JWT；
 * GET decisions 要 X-Api-Key。
 * 真實 LAPI 的 watchers/login 會拒絕 User-Agent 不是「名稱/版本」形式的請求（回 401），這裡照樣模擬。
 */
public class FakeLapi implements AutoCloseable {
	private final String machineId;
	private final String password;
	private final String bouncerKey;
	private final HttpServer server;
	private final Set<String> validTokens = ConcurrentHashMap.newKeySet();

	/** 真實 LAPI 接受的 User-Agent 形式：整串為「名稱/版本」，不含空白（hutool 預設的 Mozilla/5.0 (...) Hutool 也會被拒） */
	public static final Pattern VALID_USER_AGENT = Pattern.compile("^[A-Za-z0-9._-]+/\\S+");

	/** 每個請求收到的 User-Agent（含登入與其他端點，依到達順序） */
	public final List<String> userAgents = new CopyOnWriteArrayList<>();
	/** watchers/login 請求收到的 User-Agent */
	public final List<String> loginUserAgents = new CopyOnWriteArrayList<>();
	public final AtomicInteger loginAttempts = new AtomicInteger();
	public final AtomicInteger logins = new AtomicInteger();
	public final List<String> alertBodies = new CopyOnWriteArrayList<>();
	/** DELETE 的「路徑?解碼後查詢字串」 */
	public final List<String> deletes = new CopyOnWriteArrayList<>();
	/** GET /v1/decisions 的解碼後查詢字串 */
	public final List<String> decisionQueries = new CopyOnWriteArrayList<>();
	/** GET /v1/decisions 的回應內容 */
	public volatile String decisionsJson = "null";
	/** GET /v1/alerts 的解碼後查詢字串 */
	public final List<String> alertQueries = new CopyOnWriteArrayList<>();
	/** GET /v1/alerts 的回應內容 */
	public volatile String alertsJson = "[]";
	/** 設定後 GET /v1/alerts 改由它依解碼後的查詢字串回應（翻頁、缺口測試用）；null 時回 alertsJson */
	public volatile java.util.function.Function<String, String> alertsResponder = null;
	/** 非 0 時 GET /v1/alerts 通過 JWT 檢查後一律回這個狀態碼（模擬 LAPI 錯誤） */
	public volatile int alertsStatus = 0;
	/** 登入回應的 expire（RFC 3339） */
	public volatile String expire = "2099-01-01T00:00:00Z";
	/** 為 true 時登入照常成功，但所有需要 JWT 的端點一律回 401 */
	public volatile boolean rejectAllJwt = false;
	/** 非 0 時 watchers/login 一律回這個狀態碼（模擬 LAPI 故障或限流，如 503、429） */
	public volatile int loginStatus = 0;

	public FakeLapi(String machineId, String password, String bouncerKey) throws IOException {
		this.machineId = machineId;
		this.password = password;
		this.bouncerKey = bouncerKey;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/watchers/login", ex -> {
			recordUserAgent(ex, loginUserAgents);
			login(ex);
		});
		server.createContext("/v1/alerts", ex -> {
			recordUserAgent(ex, null);
			alerts(ex);
		});
		server.createContext("/v1/decisions", ex -> {
			recordUserAgent(ex, null);
			decisions(ex);
		});
		server.start();
	}

	public String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	/** 讓已發出的 JWT 全部失效（模擬 LAPI 端過期） */
	public void revokeTokens() {
		validTokens.clear();
	}

	@Override
	public void close() {
		server.stop(0);
	}

	private void recordUserAgent(HttpExchange ex, List<String> also) {
		String ua = ex.getRequestHeaders().getFirst("User-Agent");
		String value = ua == null ? "" : ua;
		userAgents.add(value);
		if (also != null) {
			also.add(value);
		}
	}

	private void login(HttpExchange ex) throws IOException {
		loginAttempts.incrementAndGet();
		String text = read(ex);
		if (loginStatus != 0) {
			send(ex, loginStatus, "{\"code\":" + loginStatus + ",\"message\":\"unavailable\"}");
			return;
		}
		String ua = ex.getRequestHeaders().getFirst("User-Agent");
		if (ua == null || !VALID_USER_AGENT.matcher(ua).matches()) {
			send(ex, 401, "{\"code\":401,\"message\":\"incorrect Username or Password\"}");
			return;
		}
		JSONObject body = JSONUtil.isTypeJSONObject(text) ? JSONUtil.parseObj(text) : new JSONObject();
		if (!"POST".equals(ex.getRequestMethod()) || !machineId.equals(body.getStr("machine_id"))
				|| !password.equals(body.getStr("password"))) {
			send(ex, 401, "{\"code\":401,\"message\":\"incorrect Username or Password\"}");
			return;
		}
		String token = "tok-" + logins.incrementAndGet();
		validTokens.add(token);
		send(ex, 200, "{\"code\":200,\"expire\":\"" + expire + "\",\"token\":\"" + token + "\"}");
	}

	private void alerts(HttpExchange ex) throws IOException {
		String text = read(ex);
		if (!jwtOk(ex)) {
			send(ex, 401, "{\"code\":401,\"message\":\"Token is expired\"}");
			return;
		}
		if ("POST".equals(ex.getRequestMethod())) {
			alertBodies.add(text);
			send(ex, 201, "[\"1\"]");
			return;
		}
		String raw = ex.getRequestURI().getRawQuery();
		String query = raw == null ? "" : URLDecoder.decode(raw, StandardCharsets.UTF_8);
		alertQueries.add(query);
		if (alertsStatus != 0) {
			send(ex, alertsStatus, "{\"message\":\"error\"}");
			return;
		}
		java.util.function.Function<String, String> responder = alertsResponder;
		send(ex, 200, responder != null ? responder.apply(query) : alertsJson);
	}

	private void decisions(HttpExchange ex) throws IOException {
		read(ex);
		String path = ex.getRequestURI().getPath();
		String raw = ex.getRequestURI().getRawQuery();
		String query = raw == null ? "" : URLDecoder.decode(raw, StandardCharsets.UTF_8);
		if ("GET".equals(ex.getRequestMethod())) {
			if (!bouncerKey.equals(ex.getRequestHeaders().getFirst("X-Api-Key"))) {
				send(ex, 403, "{\"message\":\"access forbidden\"}");
				return;
			}
			decisionQueries.add(query);
			send(ex, 200, decisionsJson);
			return;
		}
		if (!jwtOk(ex)) {
			send(ex, 401, "{\"code\":401,\"message\":\"Token is expired\"}");
			return;
		}
		deletes.add(query.isEmpty() ? path : path + "?" + query);
		send(ex, 200, "/v1/decisions".equals(path) ? "{\"nbDeleted\":\"2\"}" : "{\"nbDeleted\":\"1\"}");
	}

	private boolean jwtOk(HttpExchange ex) {
		if (rejectAllJwt) {
			return false;
		}
		String auth = ex.getRequestHeaders().getFirst("Authorization");
		return auth != null && auth.startsWith("Bearer ") && validTokens.contains(auth.substring(7));
	}

	private static String read(HttpExchange ex) throws IOException {
		return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
	}

	private static void send(HttpExchange ex, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().set("Content-Type", "application/json");
		ex.sendResponseHeaders(status, bytes.length);
		try (OutputStream out = ex.getResponseBody()) {
			out.write(bytes);
		}
	}
}
