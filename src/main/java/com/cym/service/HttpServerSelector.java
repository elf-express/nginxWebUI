package com.cym.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * → 預設站台（{@code def == 1} 的站台，沒有就用第一個站台）。
 * 只看啟用的站台；無效的正規式與萬用字元、{@code _}、空字串、變數不參與比對。
 * <p>
 * access log 只有 $host，分不出 listen 埠：同一優先層級有兩個以上站台都接得到這個 host 時
 * （同名、同長度的萬用字元、同一個正規式、多個 {@code def == 1}），{@link #candidates} 列出全部候選站台，
 * {@link #select} 回第一個。
 */
public final class HttpServerSelector {
	private static final Logger logger = LoggerFactory.getLogger(HttpServerSelector.class);

	/** coversBare：.example.com 形式，另外涵蓋 example.com 本身 */
	private record Wildcard(String part, String serverId, boolean coversBare) {
	}

	/** key：不分大小寫旗標加式子，同一個 key 視為同一個正規式 */
	private record Regex(Pattern pattern, String key, String serverId) {
	}

	private final Map<String, Set<String>> exact = new HashMap<>();
	private final List<Wildcard> heads = new ArrayList<>();
	private final List<Wildcard> tails = new ArrayList<>();
	private final List<Regex> regexes = new ArrayList<>();
	private final List<String> defaultIds;
	private final Map<String, List<String>> cache = new HashMap<>();

	/** @param servers HTTP 站台，順序與 ConfService 產生設定時相同（ServerService.getListByProxyType） */
	public HttpServerSelector(List<Server> servers) {
		String first = null;
		List<String> defs = new ArrayList<>();
		for (Server s : servers) {
			if (s == null || s.getEnable() == null || !s.getEnable()) {
				continue;
			}
			if (first == null) {
				first = s.getId();
			}
			if (s.getDef() != null && s.getDef() == 1) {
				defs.add(s.getId());
			}
			if (StrUtil.isNotBlank(s.getServerName())) {
				for (String name : s.getServerName().trim().split("\\s+")) {
					addName(unquote(name), s.getId());
				}
			}
		}
		defaultIds = !defs.isEmpty() ? List.copyOf(defs) : first != null ? List.of(first) : List.of();
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
				exact.computeIfAbsent(name, k -> new LinkedHashSet<>()).add(serverId);
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
			Pattern pattern = Pattern.compile(expr, insensitive ? Pattern.CASE_INSENSITIVE : 0);
			regexes.add(new Regex(pattern, (insensitive ? "~*" : "~") + expr, serverId));
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

	/** 這個 Host 落到的站台 id（候選有多個時為第一個）；沒有任何啟用站台或 host 為 null 時回 null */
	public String select(String host) {
		List<String> ids = candidates(host);
		return ids.isEmpty() ? null : ids.get(0);
	}

	/** 同一優先層級接得到這個 Host 的站台 id，依站台順序；沒有任何啟用站台或 host 為 null 時回空清單 */
	public List<String> candidates(String host) {
		if (host == null || defaultIds.isEmpty()) {
			return List.of();
		}
		return cache.computeIfAbsent(normalize(host.trim()), this::resolve);
	}

	private List<String> resolve(String host) {
		Set<String> ids = exact.get(host);
		if (ids != null) {
			return List.copyOf(ids);
		}
		List<String> wild = longest(heads, host, true);
		if (!wild.isEmpty()) {
			return wild;
		}
		wild = longest(tails, host, false);
		if (!wild.isEmpty()) {
			return wild;
		}
		for (Regex r : regexes) {
			if (r.pattern().matcher(host).find()) {
				Set<String> same = new LinkedHashSet<>();
				for (Regex o : regexes) {
					if (o.key().equals(r.key())) {
						same.add(o.serverId());
					}
				}
				return List.copyOf(same);
			}
		}
		return defaultIds;
	}

	/** 前置萬用字元 part 為 ".example.com"，後置萬用字元 part 為 "www.example."；最長的 part 勝出，同長度的全部列為候選 */
	private static List<String> longest(List<Wildcard> list, String host, boolean head) {
		int best = -1;
		Set<String> ids = new LinkedHashSet<>();
		for (Wildcard w : list) {
			String p = w.part();
			boolean match = head
					? host.endsWith(p) && host.length() > p.length() || w.coversBare() && host.equals(p.substring(1))
					: host.startsWith(p) && host.length() > p.length();
			if (!match || p.length() < best) {
				continue;
			}
			if (p.length() > best) {
				best = p.length();
				ids.clear();
			}
			ids.add(w.serverId());
		}
		return List.copyOf(ids);
	}
}
