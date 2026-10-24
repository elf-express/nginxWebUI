package com.cym.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.Server;

import cn.hutool.core.util.StrUtil;

/**
 * 依 nginx 選 server 的規則，判斷一個 Host 會落到哪個 HTTP 站台。
 * <p>
 * 比對順序：完全相同的名稱 → 最長的前置萬用字元（{@code *.example.com}；{@code .example.com} 另涵蓋 {@code example.com}）
 * → 最長的後置萬用字元（{@code www.example.*}）→ 依站台順序第一個符合的正規式（{@code ~}，{@code ~*} 不分大小寫）
 * → 預設站台（{@code def == 1} 的第一個站台，沒有就用第一個站台）。
 * 只看啟用的站台；不區分 listen 埠（access log 只有 $host）。無效的正規式與萬用字元、{@code _}、空字串、變數不參與比對。
 */
public final class HttpServerSelector {
	private static final Logger logger = LoggerFactory.getLogger(HttpServerSelector.class);

	/** coversBare：.example.com 形式，另外涵蓋 example.com 本身 */
	private record Wildcard(String part, String serverId, boolean coversBare) {
	}

	private record Regex(Pattern pattern, String serverId) {
	}

	private final Map<String, String> exact = new HashMap<>();
	private final List<Wildcard> heads = new ArrayList<>();
	private final List<Wildcard> tails = new ArrayList<>();
	private final List<Regex> regexes = new ArrayList<>();
	private final String defaultId;
	private final Map<String, String> cache = new HashMap<>();

	/** @param servers HTTP 站台，順序與 ConfService 產生設定時相同（ServerService.getListByProxyType） */
	public HttpServerSelector(List<Server> servers) {
		String first = null;
		String def = null;
		for (Server s : servers) {
			if (s == null || s.getEnable() == null || !s.getEnable()) {
				continue;
			}
			if (first == null) {
				first = s.getId();
			}
			if (def == null && s.getDef() != null && s.getDef() == 1) {
				def = s.getId();
			}
			if (StrUtil.isNotBlank(s.getServerName())) {
				for (String name : s.getServerName().trim().split("\\s+")) {
					addName(unquote(name), s.getId());
				}
			}
		}
		defaultId = def != null ? def : first;
	}

	private static String unquote(String name) {
		if (name.length() >= 2 && (name.startsWith("\"") && name.endsWith("\"") || name.startsWith("'") && name.endsWith("'"))) {
			return name.substring(1, name.length() - 1);
		}
		return name;
	}

	private void addName(String raw, String serverId) {
		if (raw.startsWith("~")) {
			addRegex(raw, serverId);
			return;
		}
		if (raw.isEmpty() || "_".equals(raw) || raw.indexOf('$') >= 0) {
			return;
		}
		String name = normalize(raw);
		int star = name.indexOf('*');
		if (star < 0) {
			if (name.startsWith(".")) {
				if (name.length() > 1) {
					heads.add(new Wildcard(name, serverId, true));
				}
			} else if (!name.isEmpty()) {
				exact.putIfAbsent(name, serverId);
			}
			return;
		}
		if (star != name.lastIndexOf('*')) {
			return;
		}
		if (name.startsWith("*.") && name.length() > 2) {
			heads.add(new Wildcard(name.substring(1), serverId, false));
		} else if (name.endsWith(".*") && name.length() > 2) {
			tails.add(new Wildcard(name.substring(0, name.length() - 1), serverId, false));
		}
	}

	private void addRegex(String raw, String serverId) {
		boolean insensitive = raw.startsWith("~*");
		String expr = raw.substring(insensitive ? 2 : 1).replace("(?P<", "(?<");
		if (expr.isEmpty()) {
			return;
		}
		try {
			regexes.add(new Regex(Pattern.compile(expr, insensitive ? Pattern.CASE_INSENSITIVE : 0), serverId));
		} catch (PatternSyntaxException e) {
			logger.warn("server_name regex skipped (server {}): {}", serverId, e.getDescription());
		}
	}

	private static String normalize(String host) {
		String h = host.toLowerCase(Locale.ROOT);
		while (h.endsWith(".") && h.length() > 1) {
			h = h.substring(0, h.length() - 1);
		}
		return h;
	}

	/** 這個 Host 落到的站台 id；沒有任何啟用站台或 host 為 null 時回 null */
	public String select(String host) {
		if (host == null || defaultId == null) {
			return null;
		}
		return cache.computeIfAbsent(normalize(host.trim()), this::resolve);
	}

	private String resolve(String host) {
		String id = exact.get(host);
		if (id != null) {
			return id;
		}
		id = longest(heads, host, true);
		if (id != null) {
			return id;
		}
		id = longest(tails, host, false);
		if (id != null) {
			return id;
		}
		for (Regex r : regexes) {
			if (r.pattern().matcher(host).find()) {
				return r.serverId();
			}
		}
		return defaultId;
	}

	/** 前置萬用字元 part 為 ".example.com"，後置萬用字元 part 為 "www.example."；同長度時先出現的站台勝出 */
	private static String longest(List<Wildcard> list, String host, boolean head) {
		Wildcard best = null;
		for (Wildcard w : list) {
			String p = w.part();
			boolean match = head
					? host.endsWith(p) && host.length() > p.length() || w.coversBare() && host.equals(p.substring(1))
					: host.startsWith(p) && host.length() > p.length();
			if (match && (best == null || p.length() > best.part().length())) {
				best = w;
			}
		}
		return best == null ? null : best.serverId();
	}
}
