package com.cym.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * 後台（/adminPage/**）請求的 HTTP 方法與 CSRF 規則。
 *
 * <ul>
 * <li>GET / HEAD 只能打 {@link #SAFE_GET_PATHS} 列出的唯讀端點，其餘回 405。</li>
 * <li>其他方法一律要帶與 session 綁定的 token（header {@value #HEADER} 或表單欄位 {@value #PARAM}），
 * 缺少或不符回 403。以 creditKey 認證的請求（主控端轉發給遠端節點）不檢查 token。</li>
 * </ul>
 */
public final class AdminCsrfGuard {
	/** 前端送 token 用的 header */
	public static final String HEADER = "X-CSRF-Token";
	/** 一般表單送 token 用的欄位 */
	public static final String PARAM = "_csrf";
	/** 給前端讀取 token 的 cookie */
	public static final String COOKIE = "NWUI_CSRF";
	/** token 在 session 中的鍵 */
	public static final String SESSION_KEY = "csrfToken";

	/** 判定結果 */
	public enum Verdict {
		ALLOW, METHOD_NOT_ALLOWED, TOKEN_REJECTED
	}

	/**
	 * 可用 GET / HEAD 存取的唯讀端點（小寫、無結尾斜線）：頁面、查詢、下載與圖片。
	 * 新增端點預設只收 POST；要開放 GET 必須確認它不會改資料或觸發動作，再加進這裡。
	 */
	static final Set<String> SAFE_GET_PATHS = Set.of(
			// 頁面
			"/adminpage/admin", "/adminpage/bak", "/adminpage/basic", "/adminpage/cert", "/adminpage/conf",
			"/adminpage/crowdsecguard", "/adminpage/denyallow", "/adminpage/export", "/adminpage/http",
			"/adminpage/log", "/adminpage/log/tail", "/adminpage/monitor", "/adminpage/operatelog",
			"/adminpage/password", "/adminpage/protectioncert", "/adminpage/remote", "/adminpage/server",
			"/adminpage/siteresource", "/adminpage/sso", "/adminpage/stream", "/adminpage/template",
			"/adminpage/upstream", "/adminpage/www",
			// 查詢
			"/adminpage/i18n",
			"/adminpage/admin/detail", "/adminpage/admin/getgrouptree",
			"/adminpage/asn/list", "/adminpage/asn/catalog", "/adminpage/asn/profile", "/adminpage/asn/intents",
			"/adminpage/bak/getcompare", "/adminpage/bak/content",
			"/adminpage/basic/pagedata", "/adminpage/basic/detail",
			"/adminpage/cert/detail",
			"/adminpage/crowdsec/getconfig", "/adminpage/crowdsec/getchallenge", "/adminpage/crowdsec/status",
			"/adminpage/crowdsec/alerts", "/adminpage/crowdsec/decisions",
			"/adminpage/crowdsecguard/servers", "/adminpage/crowdsecguard/iplookup", "/adminpage/crowdsecguard/blocked",
			"/adminpage/crowdsecguard/bans",
			"/adminpage/crowdsecguard/wafrules/list", "/adminpage/crowdsecguard/wafrules/status",
			"/adminpage/crowdsecguard/waf/events", "/adminpage/crowdsecguard/waf/status",
			"/adminpage/denyallow/detail",
			"/adminpage/geo/list", "/adminpage/geo/detail", "/adminpage/geo/hasgeoip2", "/adminpage/geo/countries",
			"/adminpage/geoip/versions",
			"/adminpage/http/detail",
			"/adminpage/log/detail",
			"/adminpage/monitor/nginxinfo",
			"/adminpage/password/detail",
			"/adminpage/remote/alltable", "/adminpage/remote/groupdetail", "/adminpage/remote/getgrouptree",
			"/adminpage/remote/getcmdremote", "/adminpage/remote/detail", "/adminpage/remote/content",
			"/adminpage/remote/readcontent", "/adminpage/remote/version",
			"/adminpage/server/detail", "/adminpage/stream/detail", "/adminpage/template/detail",
			"/adminpage/upstream/detail",
			// 下載與圖片
			"/adminpage/admin/logo", "/adminpage/admin/qr", "/adminpage/cert/download",
			"/adminpage/crowdsec/challengepreview", "/adminpage/crowdsec/challengeasset",
			"/adminpage/export/dataexport", "/adminpage/export/logexport", "/adminpage/log/down",
			"/adminpage/remote/src");

	private static final SecureRandom RANDOM = new SecureRandom();

	private AdminCsrfGuard() {
	}

	/** GET 與 HEAD 視為安全方法 */
	public static boolean isSafeMethod(String method) {
		return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
	}

	/** 轉小寫並去掉結尾斜線 */
	public static String normalize(String path) {
		String p = path == null ? "" : path.toLowerCase();
		while (p.length() > 1 && p.endsWith("/")) {
			p = p.substring(0, p.length() - 1);
		}
		return p;
	}

	/** 這個路徑是否允許用 GET / HEAD 存取 */
	public static boolean isSafeGetPath(String path) {
		return SAFE_GET_PATHS.contains(normalize(path));
	}

	/**
	 * 判定一個已通過登入檢查的後台請求能否放行。
	 *
	 * @param method       HTTP 方法
	 * @param path         請求路徑
	 * @param credit       是否以 creditKey 認證
	 * @param sessionToken session 中的 token
	 * @param requestToken 請求帶的 token（header 或表單欄位）
	 */
	public static Verdict check(String method, String path, boolean credit, String sessionToken, String requestToken) {
		if (isSafeMethod(method)) {
			return isSafeGetPath(path) ? Verdict.ALLOW : Verdict.METHOD_NOT_ALLOWED;
		}
		if (credit) {
			return Verdict.ALLOW;
		}
		return tokenMatches(sessionToken, requestToken) ? Verdict.ALLOW : Verdict.TOKEN_REJECTED;
	}

	/** 常數時間比對；任一邊為空即不符 */
	static boolean tokenMatches(String sessionToken, String requestToken) {
		if (sessionToken == null || sessionToken.isEmpty() || requestToken == null || requestToken.isEmpty()) {
			return false;
		}
		return MessageDigest.isEqual(sessionToken.getBytes(StandardCharsets.UTF_8), requestToken.getBytes(StandardCharsets.UTF_8));
	}

	/** 產生新的 token：32 bytes 亂數，URL-safe Base64 */
	public static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
