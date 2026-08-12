package com.cym.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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

	private final Map<String, NginxDirective> directives = new LinkedHashMap<>();
	private final Map<String, List<NginxDirective>> byContext = new LinkedHashMap<>();
	private final Map<String, List<NginxDirective>> byModule = new LinkedHashMap<>();
	private final List<String> pages = new ArrayList<>();

	/** 從頁面內容建索引。與載入來源解耦,測試可直接餵字串。 */
	public void load(List<String> pageContents) {
		directives.clear();
		byContext.clear();
		byModule.clear();
		pages.clear();
		pages.addAll(pageContents);

		for (String md : pageContents) {
			for (NginxDirective d : NginxDocParser.parsePage(md)) {
				directives.putIfAbsent(d.name(), d);
				for (String c : d.contexts()) {
					byContext.computeIfAbsent(c, k -> new ArrayList<>()).add(d);
				}
				if (!d.module().isEmpty()) {
					byModule.computeIfAbsent(d.module(), k -> new ArrayList<>()).add(d);
				}
			}
		}
		logger.info("nginx 文件索引:{} 條指令 / {} 個模組 / {} 個 context", directives.size(), byModule.size(), byContext.size());
	}

	/** 從 classpath 載入打包的語料。啟動時由 InitConfig 呼叫。 */
	public void loadFromClasspath() {
		List<String> contents = new ArrayList<>();
		for (int i = 1; i <= 200; i++) {
			String name = RESOURCE_DIR + String.format("%03d", i) + "page.md";
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

	public int size() {
		return directives.size();
	}

	public NginxDirective directive(String name) {
		return name == null ? null : directives.get(name.trim());
	}

	public List<NginxDirective> byContext(String ctx) {
		return byContext.getOrDefault(ctx == null ? "" : ctx.trim(), List.of());
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
		return byModule.getOrDefault(module, List.of());
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
		String q = query.toLowerCase();
		for (String md : pages) {
			int idx = md.toLowerCase().indexOf(q);
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
}
