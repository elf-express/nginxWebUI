package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.model.Server;

/** access log 的 $host 會落到哪個 HTTP 站台：依 nginx 選 server 的順序（完全相同 → 前置萬用字元 → 後置萬用字元 → 正規式 → 預設站台）。 */
class HttpServerSelectorTest {
	private final List<Server> servers = new ArrayList<>();

	private Server add(String id, String serverName) {
		Server s = new Server();
		s.setId(id);
		s.setServerName(serverName);
		s.setProxyType(0);
		s.setEnable(true);
		s.setDef(0);
		servers.add(s);
		return s;
	}

	private String select(String host) {
		return new HttpServerSelector(servers).select(host);
	}

	@Test
	void exactNamesAreCaseInsensitiveAndIgnoreTrailingDot() {
		add("first", "first.example.com");
		add("a", "A.Example.com  www.a.example.com");
		assertEquals("a", select("a.example.com"));
		assertEquals("a", select("WWW.A.Example.COM."));
	}

	@Test
	void leadingWildcardMatchesSubdomainsOnly() {
		add("first", "first.example.com");
		add("w", "*.example.com");
		assertEquals("w", select("www.example.com"));
		assertEquals("w", select("a.b.example.com"));
		// 不含 example.com 本身，落到預設站台（第一個）
		assertEquals("first", select("example.com"));
	}

	@Test
	void dotPrefixedNameAlsoCoversTheBareDomain() {
		add("first", "first.example.com");
		add("d", ".example.org");
		assertEquals("d", select("example.org"));
		assertEquals("d", select("www.example.org"));
		assertEquals("first", select("badexample.org"));
	}

	@Test
	void trailingWildcardMatchesAnyTopLevel() {
		add("first", "first.example.com");
		add("t", "www.example.*");
		assertEquals("t", select("www.example.net"));
		assertEquals("t", select("www.example.co.uk"));
		assertEquals("first", select("mail.example.net"));
	}

	@Test
	void regexNamesUseFindAndSupportNamedGroups() {
		add("first", "first.example.com");
		add("r", "~^(?P<sub>shop|blog)\\.example\\.net$");
		add("ci", "~*^API\\.example\\.net$");
		assertEquals("r", select("shop.example.net"));
		assertEquals("ci", select("api.example.net"));
		assertEquals("first", select("www.example.net"));
	}

	@Test
	void exactBeatsWildcardAndRegex() {
		add("regex", "~example\\.com$");
		add("wild", "*.example.com");
		add("exact", "www.example.com");
		assertEquals("exact", select("www.example.com"));
	}

	@Test
	void longestLeadingWildcardWins() {
		add("short", "*.example.com");
		add("long", "*.shop.example.com");
		assertEquals("long", select("a.shop.example.com"));
		assertEquals("short", select("a.blog.example.com"));
	}

	@Test
	void leadingWildcardBeatsTrailingWildcardAndTrailingBeatsRegex() {
		add("regex", "~^www\\.");
		add("tail", "www.example.*");
		add("head", "*.example.com");
		assertEquals("head", select("www.example.com"));
		assertEquals("tail", select("www.example.net"));
		assertEquals("regex", select("www.other.net"));
	}

	@Test
	void longestTrailingWildcardWins() {
		add("short", "www.*");
		add("long", "www.example.*");
		assertEquals("long", select("www.example.net"));
		assertEquals("short", select("www.other.net"));
	}

	@Test
	void firstMatchingRegexInServerOrderWins() {
		add("r1", "~\\.example\\.com$");
		add("r2", "~^www\\.");
		assertEquals("r1", select("www.example.com"));
		assertEquals("r2", select("www.example.net"));
	}

	@Test
	void invalidRegexIsSkippedWithoutThrowing() {
		add("bad", "~^(unclosed");
		add("ok", "ok.example.com");
		// 無效的正規式不參與比對；沒有 default_server 時落到第一個站台
		assertEquals("ok", select("ok.example.com"));
		assertEquals("bad", select("(unclosed"));
	}

	@Test
	void unmatchedHostFallsBackToDefaultServer() {
		add("first", "first.example.com");
		Server def = add("def", "_");
		def.setDef(1);
		assertEquals("def", select("nobody.example.com"));
		assertEquals("first", select("first.example.com"));
	}

	@Test
	void withoutDefaultServerTheFirstEnabledServerIsDefault() {
		Server off = add("off", "off.example.com");
		off.setEnable(false);
		add("first", "first.example.com");
		add("second", "second.example.com");
		assertEquals("first", select("nobody.example.com"));
		// 停用的站台不參與比對
		assertEquals("first", select("off.example.com"));
	}

	@Test
	void disabledDefaultServerIsNotDefault() {
		add("first", "first.example.com");
		Server def = add("def", "def.example.com");
		def.setDef(1);
		def.setEnable(false);
		assertEquals("first", select("nobody.example.com"));
	}

	@Test
	void placeholderNamesDoNotParticipate() {
		add("first", "first.example.com");
		add("u", "_ \"\" $hostname");
		assertEquals("first", select("_"));
		assertEquals("first", select("$hostname"));
	}

	@Test
	void invalidWildcardNamesDoNotParticipate() {
		add("first", "first.example.com");
		add("bad", "www.*.example.com * *example.com");
		assertEquals("first", select("www.a.example.com"));
		assertEquals("first", select("aexample.com"));
	}

	@Test
	void noServersSelectNothing() {
		assertNull(select("www.example.com"));
		add("off", "off.example.com").setEnable(false);
		assertNull(select("off.example.com"));
	}

	@Test
	void nullHostSelectsNothing() {
		add("first", "first.example.com");
		assertNull(select(null));
	}
}
