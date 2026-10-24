package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.model.Server;
import com.cym.service.BlockedRequestLogReader;

/**
 * 「最近被擋下的 IP」依站台篩選：每一行的 $host 依 nginx 選 server 的規則落到某個站台，只回落到被選站台的行。
 * 站台沒有可比對的名稱、也不是預設站台時不能退化成顯示全部站台。
 */
class CrowdSecGuardControllerTest {
	private static final Instant SINCE = Instant.parse("2026-10-23T00:00:00Z");

	@TempDir
	Path tmp;

	private Path log;
	private final List<Server> servers = new ArrayList<>();

	private static String line(String ip, int status, String host) {
		return ip + " - - [23/Oct/2026:10:00:00 +0000] \"GET /a HTTP/1.1\" " + status + " 0 \"-\" \"curl/8.0\""
				+ " \"TW\" \"Taipei\" \"3462\" \"Chunghwa Telecom\" \"" + host + "\"";
	}

	private Server server(String id, String serverName, String descr) {
		Server s = new Server();
		s.setId(id);
		s.setServerName(serverName);
		s.setDescr(descr);
		s.setProxyType(0);
		s.setEnable(true);
		s.setDef(0);
		servers.add(s);
		return s;
	}

	private BlockedRequestLogReader.Result read(String serverId) {
		Predicate<String> filter = CrowdSecGuardController.hostFilter(servers, serverId);
		return CrowdSecGuardController.readBlocked(log, filter, SINCE);
	}

	private List<String> ips(String serverId) {
		return read(serverId).rows().stream().map(BlockedRequestLogReader.Row::ip).sorted().toList();
	}

	@BeforeEach
	void setUp() throws Exception {
		log = tmp.resolve("access.log");
		Files.write(log, List.of(line("203.0.113.5", 444, "a.example.com"), line("203.0.113.6", 403, "b.example.com")),
				StandardCharsets.UTF_8);
	}

	@Test
	void serverNamesAreSplitAndCaseInsensitive() throws Exception {
		Files.write(log, List.of(line("203.0.113.5", 444, "WWW.A.example.com"), line("203.0.113.6", 403, "b.example.com")),
				StandardCharsets.UTF_8);
		server("b", "b.example.com", null);
		server("a", "A.Example.com  www.a.example.com", null);
		assertEquals(List.of("203.0.113.5"), ips("a"));
	}

	@Test
	void selectedServerWithoutNamesAndNotDefaultReturnsNoRowsButKeepsCoverage() {
		server("a", "a.example.com", null);
		server("b", "b.example.com", null);
		server("c", "", "described only");
		server("d", null, "described only");

		for (String id : List.of("c", "d")) {
			BlockedRequestLogReader.Result r = read(id);
			assertTrue(r.rows().isEmpty());
			assertTrue(r.hostSupported());
			assertNotNull(r.coverFrom());
			assertNotNull(r.coverTo());
		}
	}

	@Test
	void unknownServerReturnsNoRows() {
		server("a", "a.example.com", null);
		assertTrue(read("nope").rows().isEmpty());
	}

	@Test
	void disabledServerReturnsNoRows() {
		server("b", "b.example.com", null);
		server("a", "a.example.com", null).setEnable(false);
		assertTrue(read("a").rows().isEmpty());
	}

	@Test
	void selectedServerWithHostsOnlyReturnsItsRows() {
		server("b", "b.example.com", null);
		server("a", "a.example.com", null);
		assertEquals(List.of("203.0.113.5"), ips("a"));
	}

	@Test
	void wildcardServerGetsItsSubdomains() {
		server("b", "b.example.com", null);
		server("w", "*.example.com", null);
		// b.example.com 完全相同的名稱優先，只剩 a.example.com 落到萬用字元站台
		assertEquals(List.of("203.0.113.5"), ips("w"));
		assertEquals(List.of("203.0.113.6"), ips("b"));
	}

	@Test
	void regexServerGetsMatchingHosts() {
		server("b", "b.example.com", null);
		server("r", "~^a\\.example\\.com$", null);
		assertEquals(List.of("203.0.113.5"), ips("r"));
	}

	@Test
	void defaultServerGetsHostsNoOtherServerClaims() {
		server("a", "a.example.com", null);
		server("def", "_", "catch-all").setDef(1);
		assertEquals(List.of("203.0.113.6"), ips("def"));
		assertEquals(List.of("203.0.113.5"), ips("a"));
	}

	@Test
	void firstServerIsDefaultWithoutDefaultServer() {
		server("first", "", "described only");
		server("a", "a.example.com", null);
		assertEquals(List.of("203.0.113.6"), ips("first"));
	}

	@Test
	void noServerSelectedReturnsAllRows() {
		assertEquals(2, CrowdSecGuardController.readBlocked(log, null, SINCE).rows().size());
	}

	private List<Map<String, Object>> rows(String serverId) {
		List<BlockedRequestLogReader.Row> raw = serverId == null ? CrowdSecGuardController.readBlocked(log, null, SINCE).rows()
				: read(serverId).rows();
		return CrowdSecGuardController.blockedRows(raw, servers);
	}

	private Map<String, Object> row(List<Map<String, Object>> rows, String ip) {
		return rows.stream().filter(r -> ip.equals(r.get("ip"))).findFirst().orElseThrow();
	}

	@Test
	void sameNameOnTwoServersListsTheRowForEitherAndMarksItAmbiguous() {
		// 兩個不同埠的站台都叫 a.example.com：篩選任一個都列出 a.example.com 的行，並標示可能屬於多個站台
		server("a80", "a.example.com", null).setListen("80");
		server("a8080", "a.example.com", null).setListen("8080");
		server("b", "b.example.com", null);
		assertEquals(List.of("203.0.113.5"), ips("a80"));
		assertEquals(List.of("203.0.113.5"), ips("a8080"));
		assertEquals(List.of("203.0.113.6"), ips("b"));

		for (String id : new String[] { "a8080", null }) {
			List<Map<String, Object>> list = rows(id);
			Map<String, Object> a = row(list, "203.0.113.5");
			assertEquals(true, a.get("ambiguous"));
			assertEquals(List.of("a.example.com (80)", "a.example.com (8080)"), a.get("candidates"));
		}
		Map<String, Object> b = row(rows(null), "203.0.113.6");
		assertEquals(false, b.get("ambiguous"));
		assertFalse(b.containsKey("candidates"));
	}

	@Test
	void singleCandidateRowIsNotAmbiguous() {
		server("a", "a.example.com", null);
		server("w", "*.example.com", null);
		Map<String, Object> a = row(rows(null), "203.0.113.5");
		assertEquals(false, a.get("ambiguous"));
		assertFalse(a.containsKey("candidates"));
	}

	@Test
	void recentWafEventsFailureYieldsEmptyListInsteadOfBreakingLookup() {
		assertTrue(CrowdSecGuardController.recentWafEvents(() -> {
			throw new IllegalStateException("db down");
		}).isEmpty());
		assertTrue(CrowdSecGuardController.recentWafEvents(List::of).isEmpty());
	}
}
