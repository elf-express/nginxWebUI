package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.cym.model.Server;
import com.cym.service.BlockedRequestLogReader;

/** 「最近被擋下的 IP」依站台篩選：站台沒有 server_name 時不能退化成顯示全部站台。 */
class CrowdSecGuardControllerTest {
	private static final Instant SINCE = Instant.parse("2026-10-23T00:00:00Z");

	@TempDir
	Path tmp;

	private Path log;

	private static String line(String ip, int status, String host) {
		return ip + " - - [23/Oct/2026:10:00:00 +0000] \"GET /a HTTP/1.1\" " + status + " 0 \"-\" \"curl/8.0\""
				+ " \"TW\" \"Taipei\" \"3462\" \"Chunghwa Telecom\" \"" + host + "\"";
	}

	private static Server server(String serverName, String descr) {
		Server s = new Server();
		s.setServerName(serverName);
		s.setDescr(descr);
		return s;
	}

	@BeforeEach
	void setUp() throws Exception {
		log = tmp.resolve("access.log");
		Files.write(log, List.of(line("203.0.113.5", 444, "a.example.com"), line("203.0.113.6", 403, "b.example.com")),
				StandardCharsets.UTF_8);
	}

	@Test
	void hostsOfSplitsServerNamesAndLowercases() {
		assertEquals(Set.of("a.example.com", "www.a.example.com"), CrowdSecGuardController.hostsOf(server("A.Example.com  www.a.example.com", null)));
	}

	@Test
	void hostsOfIsEmptyForMissingServerOrBlankServerName() {
		assertTrue(CrowdSecGuardController.hostsOf(null).isEmpty());
		assertTrue(CrowdSecGuardController.hostsOf(server("  ", "described only")).isEmpty());
		assertTrue(CrowdSecGuardController.hostsOf(server(null, "described only")).isEmpty());
	}

	@Test
	void selectedServerWithoutHostsReturnsNoRowsButKeepsCoverage() {
		Set<String> hosts = CrowdSecGuardController.hostsOf(server("", "described only"));

		BlockedRequestLogReader.Result r = CrowdSecGuardController.readBlocked(log, hosts, SINCE);

		assertTrue(r.rows().isEmpty());
		assertTrue(r.hostSupported());
		assertNotNull(r.coverFrom());
		assertNotNull(r.coverTo());
	}

	@Test
	void unknownServerReturnsNoRows() {
		Set<String> hosts = CrowdSecGuardController.hostsOf(null);

		assertTrue(CrowdSecGuardController.readBlocked(log, hosts, SINCE).rows().isEmpty());
	}

	@Test
	void selectedServerWithHostsOnlyReturnsItsRows() {
		Set<String> hosts = CrowdSecGuardController.hostsOf(server("a.example.com", null));

		List<BlockedRequestLogReader.Row> rows = CrowdSecGuardController.readBlocked(log, hosts, SINCE).rows();

		assertEquals(1, rows.size());
		assertEquals("203.0.113.5", rows.get(0).ip());
	}

	@Test
	void noServerSelectedReturnsAllRows() {
		assertEquals(2, CrowdSecGuardController.readBlocked(log, null, SINCE).rows().size());
	}
}
