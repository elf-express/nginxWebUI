package com.cym.testsupport;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
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
 */
public class FakeLapi implements AutoCloseable {
	private final String machineId;
	private final String password;
	private final String bouncerKey;
	private final HttpServer server;
	private final Set<String> validTokens = ConcurrentHashMap.newKeySet();

	public final AtomicInteger loginAttempts = new AtomicInteger();
	public final AtomicInteger logins = new AtomicInteger();
	public final List<String> alertBodies = new CopyOnWriteArrayList<>();
	/** DELETE 的「路徑?解碼後查詢字串」 */
	public final List<String> deletes = new CopyOnWriteArrayList<>();
	/** GET /v1/decisions 的解碼後查詢字串 */
	public final List<String> decisionQueries = new CopyOnWriteArrayList<>();
	/** GET /v1/decisions 的回應內容 */
	public volatile String decisionsJson = "null";
	/** 登入回應的 expire（RFC 3339） */
	public volatile String expire = "2099-01-01T00:00:00Z";

	public FakeLapi(String machineId, String password, String bouncerKey) throws IOException {
		this.machineId = machineId;
		this.password = password;
		this.bouncerKey = bouncerKey;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/v1/watchers/login", this::login);
		server.createContext("/v1/alerts", this::alerts);
		server.createContext("/v1/decisions", this::decisions);
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

	private void login(HttpExchange ex) throws IOException {
		loginAttempts.incrementAndGet();
		String text = read(ex);
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
		send(ex, 200, "[]");
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
