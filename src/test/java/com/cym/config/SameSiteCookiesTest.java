package com.cym.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.testsupport.FakeContext;

import io.undertow.server.handlers.Cookie;
import io.undertow.server.handlers.CookieImpl;

/** session cookie 與 CSRF token cookie 送出前要加上 SameSite=Strict，其他 cookie 不動 */
class SameSiteCookiesTest {

	private static final String SESSION = "SOLONID";

	@Test
	void targetsSessionAndCsrfCookies() {
		assertTrue(SameSiteCookies.names(SESSION).contains(SESSION));
		assertTrue(SameSiteCookies.names(SESSION).contains(AdminCsrfGuard.COOKIE));
	}

	@Test
	void hardensOnlyTargetCookies() {
		Cookie session = new CookieImpl(SESSION, "s");
		Cookie csrf = new CookieImpl(AdminCsrfGuard.COOKIE, "t");
		Cookie other = new CookieImpl("other", "o");

		SameSiteCookies.harden(List.of(session, csrf, other), SameSiteCookies.names(SESSION));

		assertEquals("Strict", session.getSameSiteMode());
		assertEquals("Strict", csrf.getSameSiteMode());
		assertNull(other.getSameSiteMode());
	}

	@Test
	void ignoresNonUndertowRequests() {
		// 單元測試的假 Context 沒有 Undertow exchange，不應丟例外
		SameSiteCookies.install(new FakeContext("/adminPage/server"));
	}
}
