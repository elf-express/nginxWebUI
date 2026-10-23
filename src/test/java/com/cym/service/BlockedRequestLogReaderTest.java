package com.cym.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockedRequestLogReaderTest {
	@TempDir
	Path tmp;

	static String line(String ip, String time, int status, String host) {
		String base = ip + " - - [" + time + "] \"GET /a?b=1 HTTP/1.1\" " + status + " 0 \"-\" \"curl/8.0\""
				+ " \"TW\" \"Taipei\" \"3462\" \"Chunghwa Telecom\"";
		return host == null ? base : base + " \"" + host + "\"";
	}

	private Path write(String... lines) throws Exception {
		Path f = tmp.resolve("access.log");
		Files.write(f, List.of(lines), StandardCharsets.UTF_8);
		return f;
	}

	@Test
	void parsesLineWithHost() {
		BlockedRequestLogReader.Line l = BlockedRequestLogReader.parse(line("203.0.113.5", "23/Oct/2026:10:45:46 +0000", 444, "shop.example.com"));
		assertEquals("203.0.113.5", l.ip());
		assertEquals(444, l.status());
		assertEquals("shop.example.com", l.host());
		assertEquals(Instant.parse("2026-10-23T10:45:46Z"), l.time());
	}

	@Test
	void parsesLineWithoutHost() {
		BlockedRequestLogReader.Line l = BlockedRequestLogReader.parse(line("2001:db8::1", "23/Oct/2026:18:45:46 +0800", 403, null));
		assertEquals("2001:db8::1", l.ip());
		assertNull(l.host());
		assertEquals(Instant.parse("2026-10-23T10:45:46Z"), l.time());
	}

	@Test
	void garbageLineReturnsNull() {
		assertNull(BlockedRequestLogReader.parse("not a log line"));
	}

	@Test
	void aggregatesOnlyBlockedStatuses() throws Exception {
		Path f = write(
				line("203.0.113.5", "23/Oct/2026:10:00:00 +0000", 444, "a.example.com"),
				line("203.0.113.5", "23/Oct/2026:10:01:00 +0000", 444, "a.example.com"),
				line("203.0.113.5", "23/Oct/2026:10:02:00 +0000", 403, "a.example.com"),
				line("198.51.100.7", "23/Oct/2026:10:03:00 +0000", 503, "b.example.com"),
				line("198.51.100.8", "23/Oct/2026:10:04:00 +0000", 200, "b.example.com"));

		BlockedRequestLogReader.Result r = BlockedRequestLogReader.read(f, 1 << 20, null, null);

		assertTrue(r.hostSupported());
		assertEquals(2, r.rows().size());
		BlockedRequestLogReader.Row top = r.rows().get(0);
		assertEquals("203.0.113.5", top.ip());
		assertEquals(3, top.count());
		assertEquals(2, top.statuses().get(444));
		assertEquals(1, top.statuses().get(403));
		assertEquals(Instant.parse("2026-10-23T10:02:00Z"), top.lastTime());
		assertEquals(Instant.parse("2026-10-23T10:00:00Z"), r.coverFrom());
		assertEquals(Instant.parse("2026-10-23T10:04:00Z"), r.coverTo());
		assertFalse(r.truncated());
	}

	@Test
	void filtersByHostAndSince() throws Exception {
		Path f = write(
				line("203.0.113.5", "23/Oct/2026:10:00:00 +0000", 444, "a.example.com"),
				line("203.0.113.6", "23/Oct/2026:11:00:00 +0000", 444, "A.Example.com"),
				line("198.51.100.7", "23/Oct/2026:11:00:00 +0000", 403, "b.example.com"));

		BlockedRequestLogReader.Result r = BlockedRequestLogReader.read(f, 1 << 20, Set.of("a.example.com"),
				Instant.parse("2026-10-23T10:30:00Z"));

		assertEquals(1, r.rows().size());
		assertEquals("203.0.113.6", r.rows().get(0).ip());
	}

	@Test
	void logWithoutHostColumnReportsUnsupported() throws Exception {
		Path f = write(line("203.0.113.5", "23/Oct/2026:10:00:00 +0000", 444, null));

		BlockedRequestLogReader.Result r = BlockedRequestLogReader.read(f, 1 << 20, null, null);

		assertFalse(r.hostSupported());
		assertEquals(1, r.rows().size());
	}

	@Test
	void readsOnlyTailAndSkipsPartialFirstLine() throws Exception {
		String old = line("192.0.2.1", "23/Oct/2026:09:00:00 +0000", 444, "a.example.com");
		String recent = line("203.0.113.9", "23/Oct/2026:10:00:00 +0000", 444, "a.example.com");
		Path f = write(old, recent);

		BlockedRequestLogReader.Result r = BlockedRequestLogReader.read(f, recent.length() + 10, null, null);

		assertTrue(r.truncated());
		assertEquals(1, r.rows().size());
		assertEquals("203.0.113.9", r.rows().get(0).ip());
	}

	@Test
	void missingFileReturnsEmpty() {
		BlockedRequestLogReader.Result r = BlockedRequestLogReader.read(tmp.resolve("nope.log"), 1 << 20, null, null);
		assertTrue(r.rows().isEmpty());
	}
}
