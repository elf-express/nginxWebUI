package com.cym.config;

import java.util.Set;

import org.noear.solon.core.handle.Context;
import org.noear.solon.server.handle.SessionProps;

import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.spec.HttpServletRequestImpl;

/**
 * 替 session cookie 與 CSRF token cookie 加上 {@code SameSite=Strict}。
 *
 * Solon 的 {@code Cookie} 沒有 SameSite 屬性，這裡在 Undertow 送出回應標頭前（response commit）
 * 改寫同名 cookie。不是跑在 Undertow 上的請求（例如單元測試的假 Context）不做任何事。
 */
public final class SameSiteCookies {
	static final String STRICT = "Strict";

	private SameSiteCookies() {
	}

	/** 要加上 SameSite=Strict 的 cookie 名稱：session cookie 與 CSRF token cookie */
	static Set<String> names(String sessionCookieName) {
		return Set.of(sessionCookieName, AdminCsrfGuard.COOKIE);
	}

	/** 在這個請求的回應送出前，把目標 cookie 設成 SameSite=Strict */
	public static void install(Context ctx) {
		Object request = ctx.request();
		if (!(request instanceof HttpServletRequestImpl)) {
			return;
		}
		HttpServerExchange exchange = ((HttpServletRequestImpl) request).getExchange();
		Set<String> names = names(SessionProps.session_cookieName);
		exchange.addResponseCommitListener(ex -> harden(ex.responseCookies(), names));
	}

	/** 把名稱在 names 內的 cookie 設成 SameSite=Strict */
	static void harden(Iterable<Cookie> cookies, Set<String> names) {
		for (Cookie cookie : cookies) {
			if (names.contains(cookie.getName())) {
				cookie.setSameSiteMode(STRICT);
			}
		}
	}
}
