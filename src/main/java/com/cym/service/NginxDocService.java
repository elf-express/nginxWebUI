package com.cym.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.noear.solon.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.NginxDirective;
import com.cym.utils.NginxDocParser;

/**
 * nginx 官方文件的記憶體索引。
 *
 * 啟動時解析打包在 jar 裡的 markdown,而不是讀預先產生的 JSON:md 是單一真實來源,
 * 預解析的索引一旦忘了重跑就會悄悄過期,而過期的索引會讓 AI 引用錯誤的指令定義。
 */
@Component
public class NginxDocService {
	private static final Logger logger = LoggerFactory.getLogger(NginxDocService.class);

	/** 語料在 classpath 的位置(由 pom 的 <resources> 打包)。 */
	private static final String RESOURCE_DIR = "nginxdocumentation/";

	private final Map<String, List<NginxDirective>> byName = new LinkedHashMap<>();
	private final Map<String, List<NginxDirective>> byContext = new LinkedHashMap<>();
	private final Map<String, List<NginxDirective>> byModule = new LinkedHashMap<>();
	private final List<String> pages = new ArrayList<>();

	/** 從頁面內容建索引。與載入來源解耦,測試可直接餵字串。 */
	public void load(List<String> pageContents) {
		byName.clear();
		byContext.clear();
		byModule.clear();
		pages.clear();
		pages.addAll(pageContents);

		for (String md : pageContents) {
			for (NginxDirective d : NginxDocParser.parsePage(md)) {
				byName.computeIfAbsent(d.name(), k -> new ArrayList<>()).add(d);
				for (String c : d.contexts()) {
					byContext.computeIfAbsent(c, k -> new ArrayList<>()).add(d);
				}
				if (!d.module().isEmpty()) {
					byModule.computeIfAbsent(d.module(), k -> new ArrayList<>()).add(d);
				}
			}
		}
		logger.info("nginx 文件索引:{} 條指令({} 個相異名稱) / {} 個模組 / {} 個 context",
				size(), byName.size(), byModule.size(), byContext.size());
		if (byName.isEmpty()) {
			logger.error("nginx 文件索引是空的！語料沒有被載入 —— 若這是從 jar 啟動,八成是 pom 的 <resources> 掉了 nginxdocumentation");
		}
	}

	/** 從 classpath 載入打包的語料。啟動時由 InitConfig 呼叫。 */
	public void loadFromClasspath() {
		List<String> contents = new ArrayList<>();
		for (int i = 1; i <= 200; i++) {
			// Locale.ROOT:阿拉伯／波斯語系的 JVM 會把 %03d 格式成非 ASCII 數字,
			// 檔名對不上、getResourceAsStream 全回 null,索引就這樣無聲地載入 0 頁。
			String name = RESOURCE_DIR + String.format(Locale.ROOT, "%03d", i) + "page.md";
			try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
				if (in != null) {
					contents.add(new String(in.readAllBytes(), StandardCharsets.UTF_8));
				}
			} catch (IOException e) {
				logger.warn("讀取 {} 失敗:{}", name, e.getMessage());
			}
		}
		load(contents);
	}

	/** 指令總條數(不是相異名稱數)——這個數字才看得出語料有沒有完整載入。 */
	public int size() {
		return byName.values().stream().mapToInt(List::size).sum();
	}

	/**
	 * 同名指令可能存在於多個模組(語料有 122 個這種名字,例如 proxy_pass 同時在
	 * http 與 stream 底下且 context 完全不同)。回傳全部,讓呼叫端決定怎麼呈現 ——
	 * 先到先贏會讓 AI 拿到另一個模組的 context 而毫無察覺。
	 */
	public List<NginxDirective> directive(String name) {
		return name == null ? List.of() : List.copyOf(byName.getOrDefault(name.trim(), List.of()));
	}

	public List<NginxDirective> byContext(String ctx) {
		return List.copyOf(byContext.getOrDefault(ctx == null ? "" : ctx.trim(), List.of()));
	}

	/** 模組查詢:完整名稱優先;否則回所有名稱含該片段的模組。 */
	public List<String> byModule(String query) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		String q = query.trim();
		if (byModule.containsKey(q)) {
			return List.of(q);
		}
		List<String> hits = new ArrayList<>();
		for (String m : byModule.keySet()) {
			if (m.contains(q)) {
				hits.add(m);
			}
		}
		return hits;
	}

	public List<NginxDirective> directivesOfModule(String module) {
		return List.copyOf(byModule.getOrDefault(module == null ? "" : module.trim(), List.of()));
	}

	/**
	 * 全文搜尋:回「頁面標題 + 命中片段 + 來源連結」。
	 *
	 * 整句子字串比對。語料是部分翻譯的 zh-TW 版,英文多詞片語命中率低(例如 reverse proxy
	 * 已譯成「反向代理」),但拆詞 AND 只要求兩個詞出現在同一頁的任何角落,對數千行的頁面
	 * 幾乎等於不篩選——寧可少而準。
	 */
	public List<String> search(String query, int limit) {
		List<String> hits = new ArrayList<>();
		if (query == null || query.isBlank() || limit <= 0) {
			return hits;
		}
		String q = query.toLowerCase(Locale.ROOT);
		for (String md : pages) {
			int idx = md.toLowerCase(Locale.ROOT).indexOf(q);
			if (idx < 0) {
				continue;
			}
			int from = Math.max(0, idx - 120);
			int to = Math.min(md.length(), idx + 240);
			String snippet = md.substring(from, to).replaceAll("\\s+", " ").trim();
			hits.add(pageTitle(md) + "\n" + snippet + "\n" + pageSource(md));
			if (hits.size() >= limit) {
				break;
			}
		}
		return hits;
	}

	private String pageTitle(String md) {
		for (String line : md.split("\n", 40)) {
			if (line.startsWith("## ")) {
				return NginxDocParser.stripHtml(line.substring(3));
			}
		}
		return "(untitled)";
	}

	private String pageSource(String md) {
		for (String line : md.split("\n", 10)) {
			if (line.startsWith("> Source:")) {
				return line.substring("> Source:".length()).trim();
			}
		}
		return "";
	}

	/** 所有已知的 context 名稱,供查無時提示。 */
	public Set<String> knownContexts() {
		return new LinkedHashSet<>(byContext.keySet());
	}

	/**
	 * 候選建議:前綴相符、子字串相符、編輯距離 <= 2,依此優先序取前 8 筆。
	 *
	 * 走 byName 的 key 而不是 value:122 個名字同時存在於多個模組,列 value 會讓同一個名字
	 * 重複出現三次,把 8 筆額度吃光。
	 *
	 * 前綴要排在子字串前面,否則 8 筆額度會被別的模組吃光:ssl_certificat 的子字串命中
	 * 從 grpc_ssl_certificate 開始有十幾個,使用者要的 ssl_certificate_key 反而被擠掉。
	 *
	 * 前綴 bucket 內要短的排前面。語料順序下 user 排在 9 個 userid_* 之後,直接被 8 筆的
	 * 額度切掉 —— 而 directive() 大小寫敏感、這裡不敏感,所以查 "User" 落到這裡時,
	 * 唯一想要的答案 user 反而是回不來的那一個。
	 */
	public List<String> suggest(String name) {
		if (name == null || name.isBlank()) {
			return List.of();
		}
		String q = name.trim().toLowerCase(Locale.ROOT);
		List<String> prefix = new ArrayList<>();
		List<String> substring = new ArrayList<>();
		List<String> near = new ArrayList<>();
		for (String key : byName.keySet()) {
			String k = key.toLowerCase(Locale.ROOT);
			if (k.startsWith(q)) {
				prefix.add(key);
			} else if (k.contains(q)) {
				substring.add(key);
			} else if (editDistance(k, q) <= 2) {
				near.add(key);
			}
		}
		prefix.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
		// 三個 bucket 互斥(if/else if),接起來不會有重複
		List<String> out = new ArrayList<>(prefix);
		out.addAll(substring);
		out.addAll(near);
		return List.copyOf(out.size() > 8 ? out.subList(0, 8) : out);
	}

	/** Levenshtein 距離,只用於候選建議,語料規模(947)下的 O(n*m) 完全足夠。 */
	private static int editDistance(String a, String b) {
		int[] prev = new int[b.length() + 1];
		int[] cur = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= a.length(); i++) {
			cur[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
			}
			int[] t = prev;
			prev = cur;
			cur = t;
		}
		return prev[b.length()];
	}
}
