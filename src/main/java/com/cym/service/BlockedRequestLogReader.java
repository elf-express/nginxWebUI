package com.cym.service;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 讀 nginx access log（log_format main）尾端，挑出 444（CrowdSec 封鎖）、403（WAF／黑名單／國家）、
 * 503（防護服務暫停）的請求，依 IP＋站台彙總。只讀最後 maxBytes，避免大檔吃記憶體。
 */
public final class BlockedRequestLogReader {
	public static final long DEFAULT_MAX_BYTES = 8L * 1024 * 1024;
	public static final int MAX_ROWS = 200;
	public static final Set<Integer> BLOCKED = Set.of(444, 403, 503);

	// $remote_addr - $remote_user [$time_local] "$request" $status $bytes "$referer" "$ua" 4 個 GeoIP 欄位 [ "$host" ]
	private static final Pattern MAIN = Pattern.compile("^(\\S+) \\S+ \\S+ \\[([^\\]]+)\\] \"([^\"]*)\" (\\d{3}) \\S+ \"[^\"]*\" \"([^\"]*)\""
			+ "(?: \"[^\"]*\" \"[^\"]*\" \"[^\"]*\" \"[^\"]*\")?(?: \"([^\"]*)\")?\\s*$");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z", Locale.ENGLISH);

	private BlockedRequestLogReader() {
	}

	public record Line(String ip, Instant time, int status, String request, String userAgent, String host) {
	}

	public record Row(String ip, String host, int count, Instant lastTime, Map<Integer, Integer> statuses) {
	}

	public record Result(List<Row> rows, boolean hostSupported, Instant coverFrom, Instant coverTo, boolean truncated) {
	}

	/** 解析一行；格式不符回 null。host 欄位不存在或為 "-" 時 host 為 null */
	public static Line parse(String text) {
		if (text == null) {
			return null;
		}
		Matcher m = MAIN.matcher(text);
		if (!m.matches()) {
			return null;
		}
		Instant time;
		try {
			time = OffsetDateTime.parse(m.group(2), TIME).toInstant();
		} catch (RuntimeException e) {
			return null;
		}
		int status;
		try {
			status = Integer.parseInt(m.group(4));
		} catch (NumberFormatException e) {
			return null;
		}
		String host = m.group(6);
		if (host != null && (host.isEmpty() || "-".equals(host))) {
			host = null;
		}
		return new Line(m.group(1), time, status, m.group(3), m.group(5), host);
	}

	/**
	 * @param hosts 只算這些站台（小寫比對）；null 或空表示不篩
	 * @param since 只算這個時間之後；null 表示不篩
	 */
	public static Result read(Path file, long maxBytes, Set<String> hosts, Instant since) {
		Predicate<String> filter = hosts == null || hosts.isEmpty() ? null : h -> hosts.contains(h.toLowerCase(Locale.ROOT));
		return readMatching(file, maxBytes, filter, since);
	}

	/**
	 * @param hostFilter 只算 host 符合的行（傳入原始 host，不為 null）；null 表示不篩，非 null 時沒有 host 的行一律不算
	 * @param since      只算這個時間之後；null 表示不篩
	 */
	public static Result readMatching(Path file, long maxBytes, Predicate<String> hostFilter, Instant since) {
		List<String> lines = new ArrayList<>();
		boolean truncated = false;
		if (file != null && Files.isRegularFile(file)) {
			try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
				long len = raf.length();
				long start = Math.max(0, len - maxBytes);
				truncated = start > 0;
				byte[] buf = new byte[(int) (len - start)];
				raf.seek(start);
				raf.readFully(buf);
				String[] split = new String(buf, StandardCharsets.UTF_8).split("\r?\n");
				for (int i = truncated ? 1 : 0; i < split.length; i++) {
					lines.add(split[i]);
				}
			} catch (IOException e) {
				lines.clear();
			}
		}
		boolean hostSupported = false;
		Instant from = null;
		Instant to = null;
		Map<String, Agg> agg = new LinkedHashMap<>();
		for (String text : lines) {
			Line l = parse(text);
			if (l == null) {
				continue;
			}
			if (l.host() != null) {
				hostSupported = true;
			}
			from = from == null || l.time().isBefore(from) ? l.time() : from;
			to = to == null || l.time().isAfter(to) ? l.time() : to;
			if (!BLOCKED.contains(l.status()) || (since != null && l.time().isBefore(since))) {
				continue;
			}
			if (hostFilter != null && (l.host() == null || !hostFilter.test(l.host()))) {
				continue;
			}
			String key = l.ip() + "\u0000" + (l.host() == null ? "" : l.host().toLowerCase(Locale.ROOT));
			agg.computeIfAbsent(key, k -> new Agg(l.ip(), l.host())).add(l);
		}
		List<Row> rows = new ArrayList<>();
		for (Agg a : agg.values()) {
			rows.add(new Row(a.ip, a.host, a.count, a.last, a.statuses));
		}
		rows.sort(Comparator.comparingInt(Row::count).reversed().thenComparing(Row::lastTime, Comparator.reverseOrder()));
		if (rows.size() > MAX_ROWS) {
			rows = new ArrayList<>(rows.subList(0, MAX_ROWS));
		}
		return new Result(rows, hostSupported, from, to, truncated);
	}

	private static final class Agg {
		final String ip;
		final String host;
		int count;
		Instant last;
		final Map<Integer, Integer> statuses = new TreeMap<>();

		Agg(String ip, String host) {
			this.ip = ip;
			this.host = host;
		}

		void add(Line l) {
			count++;
			last = last == null || l.time().isAfter(last) ? l.time() : last;
			statuses.merge(l.status(), 1, Integer::sum);
		}
	}
}
