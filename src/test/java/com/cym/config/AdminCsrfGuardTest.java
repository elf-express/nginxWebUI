package com.cym.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.noear.solon.annotation.Controller;
import org.noear.solon.annotation.Mapping;

import com.cym.config.AdminCsrfGuard.Verdict;

import cn.hutool.core.util.ClassUtil;

/**
 * 後台請求的 CSRF 與 HTTP 方法規則：GET/HEAD 只能打唯讀白名單，其餘一律 POST 並帶與 session 綁定的 token。
 */
class AdminCsrfGuardTest {
	private static final String TOKEN = "session-token";

	@Test
	void getToMutatingEndpointIsMethodNotAllowed() {
		assertEquals(Verdict.METHOD_NOT_ALLOWED, AdminCsrfGuard.check("GET", "/adminPage/bak/delAll", false, TOKEN, null));
		assertEquals(Verdict.METHOD_NOT_ALLOWED, AdminCsrfGuard.check("GET", "/adminPage/admin/changePassOver", false, TOKEN, TOKEN));
		assertEquals(Verdict.METHOD_NOT_ALLOWED, AdminCsrfGuard.check("HEAD", "/adminPage/admin/del", false, TOKEN, null));
	}

	@Test
	void getToUnknownAdminPathIsMethodNotAllowed() {
		// 白名單是 fail-closed：沒列入的路徑（含大小寫、結尾斜線以外的變形）一律不收 GET
		assertEquals(Verdict.METHOD_NOT_ALLOWED, AdminCsrfGuard.check("GET", "/adminPage/server/someNewAction", false, TOKEN, null));
		assertEquals(Verdict.METHOD_NOT_ALLOWED, AdminCsrfGuard.check("GET", "/adminPage//bak/delAll", false, TOKEN, null));
	}

	@Test
	void getToReadOnlyEndpointIsAllowedWithoutToken() {
		assertEquals(Verdict.ALLOW, AdminCsrfGuard.check("GET", "/adminPage/server", false, TOKEN, null));
		assertEquals(Verdict.ALLOW, AdminCsrfGuard.check("GET", "/adminPage/server/", false, TOKEN, null));
		assertEquals(Verdict.ALLOW, AdminCsrfGuard.check("GET", "/ADMINPAGE/Server/Detail", false, TOKEN, null));
		assertEquals(Verdict.ALLOW, AdminCsrfGuard.check("HEAD", "/adminPage/cert/download", false, TOKEN, null));
	}

	@Test
	void postWithoutTokenIsRejected() {
		assertEquals(Verdict.TOKEN_REJECTED, AdminCsrfGuard.check("POST", "/adminPage/bak/delAll", false, TOKEN, null));
		assertEquals(Verdict.TOKEN_REJECTED, AdminCsrfGuard.check("POST", "/adminPage/bak/delAll", false, TOKEN, ""));
	}

	@Test
	void postWithWrongTokenIsRejected() {
		assertEquals(Verdict.TOKEN_REJECTED, AdminCsrfGuard.check("POST", "/adminPage/bak/delAll", false, TOKEN, "guess"));
	}

	@Test
	void postWhenSessionHasNoTokenIsRejected() {
		assertEquals(Verdict.TOKEN_REJECTED, AdminCsrfGuard.check("POST", "/adminPage/bak/delAll", false, null, null));
		assertEquals(Verdict.TOKEN_REJECTED, AdminCsrfGuard.check("POST", "/adminPage/bak/delAll", false, "", ""));
	}

	@Test
	void postWithMatchingTokenIsAllowed() {
		assertEquals(Verdict.ALLOW, AdminCsrfGuard.check("POST", "/adminPage/bak/delAll", false, TOKEN, TOKEN));
		// 唯讀端點用 POST 也要帶 token（例如列表頁的搜尋表單）
		assertEquals(Verdict.ALLOW, AdminCsrfGuard.check("POST", "/adminPage/server", false, TOKEN, TOKEN));
		assertEquals(Verdict.TOKEN_REJECTED, AdminCsrfGuard.check("POST", "/adminPage/server", false, TOKEN, null));
	}

	@Test
	void otherUnsafeMethodsNeedTokenToo() {
		assertEquals(Verdict.TOKEN_REJECTED, AdminCsrfGuard.check("PUT", "/adminPage/bak/delAll", false, TOKEN, null));
		assertEquals(Verdict.TOKEN_REJECTED, AdminCsrfGuard.check("DELETE", "/adminPage/bak/delAll", false, TOKEN, null));
	}

	@Test
	void creditKeyRequestSkipsTokenButNotMethodRule() {
		// 主控端轉發給遠端節點一律用 POST 並帶 creditKey，不帶瀏覽器 cookie
		assertEquals(Verdict.ALLOW, AdminCsrfGuard.check("POST", "/adminPage/bak/delAll", true, null, null));
		assertEquals(Verdict.METHOD_NOT_ALLOWED, AdminCsrfGuard.check("GET", "/adminPage/bak/delAll", true, null, null));
	}

	@Test
	void newTokenIsRandomAndUrlSafe() {
		String a = AdminCsrfGuard.newToken();
		String b = AdminCsrfGuard.newToken();
		assertNotEquals(a, b);
		assertTrue(a.length() >= 40, a);
		assertTrue(a.matches("[A-Za-z0-9_-]+"), a);
	}

	/** 白名單裡的每一條都必須是真的後台路由，避免改名後留下沒人用的放行規則 */
	@Test
	void everySafeGetPathIsARealRoute() {
		Set<String> routes = adminRoutes();
		for (String safe : AdminCsrfGuard.SAFE_GET_PATHS) {
			assertTrue(routes.contains(safe), "白名單路徑不是現有路由: " + safe);
		}
	}

	/** 名稱看起來會改資料的端點不得出現在 GET 白名單 */
	@Test
	void safeGetPathsDoNotLookLikeMutations() {
		Pattern verb = Pattern.compile(
				"/(add|del|save|set|update|change|replace|apply|sync|push|revoke|upload|reset|clean|import|run|reload|unban|whitelist|retention|mkdir|test|cmd|asyc|copy|login|logout)[a-z]*$");
		List<String> bad = new ArrayList<>();
		for (String safe : AdminCsrfGuard.SAFE_GET_PATHS) {
			if (verb.matcher(safe).find()) {
				bad.add(safe);
			}
		}
		assertTrue(bad.isEmpty(), "白名單含疑似變更端點: " + bad);
	}

	@Test
	void issueEndpointsAreNotInSafeGetPaths() {
		assertFalse(AdminCsrfGuard.isSafeGetPath("/adminPage/bak/delAll"));
		assertFalse(AdminCsrfGuard.isSafeGetPath("/adminPage/bak/del"));
		assertFalse(AdminCsrfGuard.isSafeGetPath("/adminPage/admin/changePassOver"));
		assertFalse(AdminCsrfGuard.isSafeGetPath("/adminPage/admin/del"));
	}

	/** 以反射列出 adminPage 控制器的全部路由（小寫、無結尾斜線） */
	static Set<String> adminRoutes() {
		Set<String> routes = new TreeSet<>();
		for (Class<?> clz : ClassUtil.scanPackage("com.cym.controller.adminPage")) {
			if (clz.getAnnotation(Controller.class) == null) {
				continue;
			}
			Mapping base = clz.getAnnotation(Mapping.class);
			String prefix = base == null ? "" : base.value();
			for (Method method : clz.getDeclaredMethods()) {
				Mapping mapping = method.getAnnotation(Mapping.class);
				if (mapping == null) {
					continue;
				}
				String sub = mapping.value();
				String full;
				if (sub.isEmpty()) {
					full = prefix;
				} else if (prefix.isEmpty()) {
					full = sub.startsWith("/") ? sub : "/" + sub;
				} else {
					full = prefix + "/" + (sub.startsWith("/") ? sub.substring(1) : sub);
				}
				routes.add(AdminCsrfGuard.normalize(full));
			}
		}
		return routes;
	}
}
