# nginx 文件 MCP 實作計畫

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 `docs/nginxdocumentation/` 的 947 個 nginx 指令定義變成 nginxWebUI 內建的 MCP server，提供五個唯讀查詢工具。

**Architecture:** md 打包進 jar，啟動時由純函式 Parser 解析成記憶體索引，Service 提供查詢，MCP 類只做協定門面。分層的目的是可測試性——Parser 與檢查器都能在不啟動 Solon 的情況下對真實語料跑測試。

**Tech Stack:** Java 17、Solon 3.10.7、`org.noear:solon-ai-mcp`、JUnit 5（透過既有的 `solon-test`）、Playwright（E2E）。

**設計來源：** `docs/superpowers/specs/2026-08-12-nginx-doc-mcp-design.md`

## Global Constraints

- **五個工具全部唯讀、純文字分析。** 不碰檔案系統、不碰資料庫、不執行任何命令。
- **MCP 工具永不向外拋例外**，一律回傳可讀的錯誤字串——協定層的例外對 AI 是不透明的失敗。
- **Parser 用內容特徵判斷，不用檔名**：有 `<table cellspacing="0">` 且檔頭 `> Source:` 指向 nginx.org 的才進指令索引；其他 md 只進全文搜尋。自寫文件永遠不會污染 `nginx_directive` 的答案。
- **認證 opt-in**：未設 `--mcp.token` 時 `/mcp` 一律回 404。既有部署升級後行為零變化。
- **語料是唯讀輸入**，本計畫不得修改 `docs/nginxdocumentation/` 下任何檔案。
- 每個 `@ToolMapping` 方法不超過 20 行；邏輯放 Service。
- 新增使用者可見字串需同步三份 `messages*.properties`——**本計畫不新增任何 UI 字串**，故不涉及。

---

## File Structure

| 檔案 | 責任 |
|---|---|
| `src/main/java/com/cym/model/NginxDirective.java` | 資料模型，record，無邏輯 |
| `src/main/java/com/cym/utils/NginxDocParser.java` | md → NginxDirective。純靜態方法，不依賴 Solon |
| `src/main/java/com/cym/service/NginxDocService.java` | 建索引與查詢，`@Component` |
| `src/main/java/com/cym/utils/NginxConfChecker.java` | conf 文字 → 問題清單。純靜態方法 |
| `src/main/java/com/cym/mcp/NginxDocMcpServer.java` | MCP 協定門面 |
| `src/test/java/com/cym/utils/NginxDocParserTest.java` | Parser 單元測試（跑真實語料） |
| `src/test/java/com/cym/service/NginxDocServiceTest.java` | 查詢與候選建議測試 |
| `src/test/java/com/cym/utils/NginxConfCheckerTest.java` | 設定檢查測試 |
| `tests/e2e/35-mcp.spec.js` | MCP 端點 E2E（JSON-RPC） |
| `.mcp.json` | 專案級 MCP client 設定 |

---

### Task 1: 資料模型與 Parser

**Files:**
- Create: `src/main/java/com/cym/model/NginxDirective.java`
- Create: `src/main/java/com/cym/utils/NginxDocParser.java`
- Create: `src/test/java/com/cym/utils/NginxDocParserTest.java`

**Interfaces:**
- Produces:
  - `record NginxDirective(String name, String syntax, String defaultValue, List<String> contexts, String module, String sourceUrl, String description)` — `defaultValue` 為 `null` 表示官方文件寫 `—`（無預設值）
  - `NginxDocParser.parsePage(String markdown) -> List<NginxDirective>` — 單一頁面的所有指令；非 nginx.org 頁面回空清單
  - `NginxDocParser.stripHtml(String html) -> String` — 移除標籤與還原跳脫底線，供測試直接驗證

- [ ] **Step 1: 寫失敗測試**

```java
// src/test/java/com/cym/utils/NginxDocParserTest.java
package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.cym.model.NginxDirective;

public class NginxDocParserTest {

	private String page(String name) throws Exception {
		return Files.readString(Path.of("docs/nginxdocumentation", name));
	}

	@Test
	public void stripHtml_移除標籤並還原跳脫底線() {
		assertEquals("ngx_http_proxy_module",
				NginxDocParser.stripHtml("ngx\\_http\\_proxy\\_module"));
		assertEquals("smtp_auth method ...;",
				NginxDocParser.stripHtml("<code><strong>smtp_auth</strong> <code><i>method</i></code> ...;</code>"));
	}

	@Test
	public void parsePage_解析出指令的四個欄位() throws Exception {
		List<NginxDirective> list = NginxDocParser.parsePage(page("100page.md"));

		NginxDirective d = list.stream().filter(x -> "smtp_auth".equals(x.name())).findFirst().orElseThrow();
		assertTrue(d.syntax().startsWith("smtp_auth"));
		assertEquals("smtp_auth plain login;", d.defaultValue());
		assertEquals(List.of("mail", "server"), d.contexts());
		assertEquals("ngx_mail_smtp_module", d.module());
		assertEquals("https://nginx.org/en/docs/mail/ngx_mail_smtp_module.html", d.sourceUrl());
	}

	@Test
	public void parsePage_無預設值的指令defaultValue為null() throws Exception {
		List<NginxDirective> list = NginxDocParser.parsePage(page("100page.md"));
		NginxDirective d = list.stream().filter(x -> "smtp_capabilities".equals(x.name())).findFirst().orElseThrow();
		assertNull(d.defaultValue());
	}

	@Test
	public void parsePage_帶出指令說明段落() throws Exception {
		List<NginxDirective> list = NginxDocParser.parsePage(page("100page.md"));
		NginxDirective d = list.stream().filter(x -> "smtp_auth".equals(x.name())).findFirst().orElseThrow();
		assertFalse(d.description().isBlank(), "說明段落不該是空的");
		assertTrue(d.description().length() <= 401, "說明應截斷在 400 字內");
	}

	@Test
	public void parsePage_非nginx官方頁面回空清單() {
		assertTrue(NginxDocParser.parsePage("# 自寫文件\n\n沒有 Source 標頭，也沒有指令表格。").isEmpty());
	}
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `mvn -q test -Dtest=NginxDocParserTest`
Expected: 編譯失敗，`NginxDocParser` 與 `NginxDirective` 不存在

- [ ] **Step 3: 寫資料模型**

```java
// src/main/java/com/cym/model/NginxDirective.java
package com.cym.model;

import java.util.List;

/**
 * 一條 nginx 指令的官方定義。
 * defaultValue 為 null 表示官方文件的 Default 欄寫「—」（無預設值），語料中有 359 條。
 */
public record NginxDirective(
		String name,
		String syntax,
		String defaultValue,
		List<String> contexts,
		String module,
		String sourceUrl,
		String description) {
}
```

- [ ] **Step 4: 寫 Parser**

```java
// src/main/java/com/cym/utils/NginxDocParser.java
package com.cym.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cym.model.NginxDirective;

/**
 * 把抓取自 nginx.org 的 markdown 解析成指令定義。
 *
 * 全語料 947 個指令表格格式零變異,所以這裡不需要處理特例:
 *   <tr><th>Syntax:</th><td><code><strong>NAME</strong> ...;</code></td></tr>
 *   <tr><th>Default:</th><td><pre>VALUE</pre></td></tr>   (無預設值時是 —)
 *   <tr><th>Context:</th><td><code>ctx</code>, <code>ctx</code></td></tr>
 *
 * 判斷「這是不是官方頁面」用內容特徵而非檔名:自寫文件放進同一目錄也不會污染指令索引。
 */
public class NginxDocParser {

	private static final Pattern SOURCE = Pattern.compile("^>\\s*Source:\\s*(https://nginx\\.org/\\S+)", Pattern.MULTILINE);
	private static final Pattern MODULE = Pattern.compile("^##\\s+Module\\s+(\\S+)", Pattern.MULTILINE);
	private static final Pattern TABLE = Pattern.compile("<table cellspacing=\"0\">.*?</table>", Pattern.DOTALL);
	private static final Pattern NAME = Pattern.compile("<strong>([^<]+)</strong>");
	private static final Pattern SYNTAX_CELL = Pattern.compile("<th>Syntax:</th><td>(.*?)</td>", Pattern.DOTALL);
	private static final Pattern DEFAULT_CELL = Pattern.compile("<th>Default:</th><td>(.*?)</td>", Pattern.DOTALL);
	private static final Pattern CONTEXT_CELL = Pattern.compile("<th>Context:</th><td>(.*?)</td>", Pattern.DOTALL);
	private static final Pattern CODE = Pattern.compile("<code>([^<]*)</code>");
	private static final Pattern TAG = Pattern.compile("<[^>]+>");

	private NginxDocParser() {
	}

	/** 移除 HTML 標籤、還原 markdown 跳脫底線與 HTML entity,壓掉多餘空白。 */
	public static String stripHtml(String html) {
		if (html == null) {
			return "";
		}
		String s = TAG.matcher(html).replaceAll("");
		s = s.replace("\\_", "_").replace("\\*", "*");
		s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
		return s.replaceAll("\\s+", " ").trim();
	}

	/** 解析單一頁面。不是 nginx.org 抓來的頁面回空清單。 */
	public static List<NginxDirective> parsePage(String markdown) {
		List<NginxDirective> result = new ArrayList<>();
		if (markdown == null) {
			return result;
		}

		Matcher src = SOURCE.matcher(markdown);
		if (!src.find()) {
			return result; // 沒有官方 Source 標頭 → 不是指令文件
		}
		String sourceUrl = src.group(1);

		Matcher mod = MODULE.matcher(markdown);
		String module = mod.find() ? stripHtml(mod.group(1)) : "";

		Matcher tables = TABLE.matcher(markdown);
		while (tables.find()) {
			String table = tables.group();
			// 表格之後、下一個表格之前的第一段文字就是這條指令的說明
			int tailStart = tables.end();
			int nextTable = markdown.indexOf("<table", tailStart);
			String tail = nextTable > 0 ? markdown.substring(tailStart, nextTable) : markdown.substring(tailStart);
			String description = firstParagraph(tail);

			Matcher syntaxCell = SYNTAX_CELL.matcher(table);
			if (!syntaxCell.find()) {
				continue; // 不是指令定義表格(例如頁面導覽表)
			}
			Matcher name = NAME.matcher(table);
			if (!name.find()) {
				continue;
			}

			String defaultValue = null;
			Matcher defCell = DEFAULT_CELL.matcher(table);
			if (defCell.find()) {
				String raw = stripHtml(defCell.group(1));
				if (!raw.isEmpty() && !"—".equals(raw)) {
					defaultValue = raw;
				}
			}

			List<String> contexts = new ArrayList<>();
			Matcher ctxCell = CONTEXT_CELL.matcher(table);
			if (ctxCell.find()) {
				Matcher code = CODE.matcher(ctxCell.group(1));
				while (code.find()) {
					String c = stripHtml(code.group(1));
					if (!c.isEmpty()) {
						contexts.add(c);
					}
				}
			}

			result.add(new NginxDirective(
					stripHtml(name.group(1)),
					stripHtml(syntaxCell.group(1)),
					defaultValue,
					contexts,
					module,
					sourceUrl,
					description));
		}
		return result;
	}

	/** 取第一段實質文字當說明,跳過標題／引用／表格列。超過 400 字截斷,MCP 回應不需要整段。 */
	private static String firstParagraph(String tail) {
		for (String line : tail.split("\n")) {
			String t = line.trim();
			if (t.isEmpty() || t.startsWith("#") || t.startsWith(">") || t.startsWith("|") || t.startsWith("<table")) {
				continue;
			}
			String s = stripHtml(t);
			if (s.isEmpty()) {
				continue;
			}
			return s.length() > 400 ? s.substring(0, 400) + "…" : s;
		}
		return "";
	}
}
```

- [ ] **Step 5: 執行測試確認通過**

Run: `mvn -q test -Dtest=NginxDocParserTest`
Expected: 4 個測試全綠

- [ ] **Step 6: 對全語料跑一次,確認解析總數**

新增這個測試到同一檔案並執行:

```java
	@Test
	public void parsePage_全語料解析出947條指令() throws Exception {
		int total = 0;
		try (var paths = Files.list(Path.of("docs/nginxdocumentation"))) {
			for (Path p : paths.filter(x -> x.getFileName().toString().endsWith("page.md")).toList()) {
				total += NginxDocParser.parsePage(Files.readString(p)).size();
			}
		}
		assertEquals(947, total, "指令表格數與語料實測值不符,解析器漏了形態");
	}
```

Run: `mvn -q test -Dtest=NginxDocParserTest`
Expected: 5 個測試全綠。若數字不是 947,表示有未預期的表格形態,**停下來回報**,不要調整斷言去遷就。

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/cym/model/NginxDirective.java src/main/java/com/cym/utils/NginxDocParser.java src/test/java/com/cym/utils/NginxDocParserTest.java
git commit -m "feat(mcp): parse nginx directive definitions from the captured docs"
```

---

### Task 2: 索引與基本查詢

**Files:**
- Create: `src/main/java/com/cym/service/NginxDocService.java`
- Create: `src/test/java/com/cym/service/NginxDocServiceTest.java`
- Modify: `pom.xml`（`<build>` 區塊新增 `<resources>`）

**Interfaces:**
- Consumes: `NginxDocParser.parsePage(String)`、`NginxDirective`
- Produces:
  - `NginxDocService.load(List<String> pages) -> void` — 從頁面內容建索引（測試用,不碰 classpath）
  - `NginxDocService.directive(String name) -> NginxDirective`（查無回 null）
  - `NginxDocService.byContext(String ctx) -> List<NginxDirective>`
  - `NginxDocService.byModule(String moduleQuery) -> List<String>` — 回相符的模組**名稱**清單,唯一相符時長度為 1
  - `NginxDocService.directivesOfModule(String module) -> List<NginxDirective>`
  - `NginxDocService.search(String query, int limit) -> List<String>` — 回「頁面標題 + 命中片段 + 來源連結」的格式化字串

- [ ] **Step 1: 寫失敗測試**

```java
// src/test/java/com/cym/service/NginxDocServiceTest.java
package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cym.model.NginxDirective;

public class NginxDocServiceTest {

	private static NginxDocService svc;

	@BeforeAll
	public static void setUp() throws Exception {
		List<String> pages = new ArrayList<>();
		try (var paths = Files.list(Path.of("docs/nginxdocumentation"))) {
			for (Path p : paths.filter(x -> x.getFileName().toString().endsWith("page.md")).toList()) {
				pages.add(Files.readString(p));
			}
		}
		svc = new NginxDocService();
		svc.load(pages);
	}

	@Test
	public void directive_查得到並帶完整欄位() {
		NginxDirective d = svc.directive("proxy_pass");
		assertNotNull(d);
		assertTrue(d.contexts().contains("location"));
		assertTrue(d.sourceUrl().startsWith("https://nginx.org/"));
	}

	@Test
	public void directive_查無回null() {
		assertNull(svc.directive("proxy_pas"));
	}

	@Test
	public void byContext_反查location能用的指令() {
		List<NginxDirective> list = svc.byContext("location");
		assertFalse(list.isEmpty());
		assertTrue(list.stream().anyMatch(d -> "proxy_pass".equals(d.name())));
		assertTrue(list.stream().allMatch(d -> d.contexts().contains("location")));
	}

	@Test
	public void byModule_簡寫命中多個時全部回傳() {
		List<String> hits = svc.byModule("proxy");
		assertTrue(hits.size() > 1, "proxy 應同時命中 http/stream/mail 三個模組");
		assertTrue(hits.contains("ngx_http_proxy_module"));
	}

	@Test
	public void byModule_完整名稱唯一命中() {
		assertEquals(List.of("ngx_http_proxy_module"), svc.byModule("ngx_http_proxy_module"));
	}

	@Test
	public void search_回傳命中片段與來源() {
		List<String> hits = svc.search("reverse proxy", 5);
		assertFalse(hits.isEmpty());
		assertTrue(hits.size() <= 5);
		assertTrue(hits.get(0).contains("https://nginx.org/"));
	}
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `mvn -q test -Dtest=NginxDocServiceTest`
Expected: 編譯失敗,`NginxDocService` 不存在

- [ ] **Step 3: 寫 Service**

```java
// src/main/java/com/cym/service/NginxDocService.java
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

	/** 全文搜尋:回「頁面標題 + 命中片段 + 來源連結」。 */
	public List<String> search(String query, int limit) {
		List<String> hits = new ArrayList<>();
		if (query == null || query.isBlank()) {
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
```

- [ ] **Step 4: 執行測試確認通過**

Run: `mvn -q test -Dtest=NginxDocServiceTest`
Expected: 6 個測試全綠

- [ ] **Step 5: 把語料打包進 jar**

在 `pom.xml` 的 `<build>` 區塊、`<plugins>` 之前插入:

```xml
		<resources>
			<resource>
				<directory>src/main/resources</directory>
			</resource>
			<resource>
				<directory>docs/nginxdocumentation</directory>
				<targetPath>nginxdocumentation</targetPath>
				<includes>
					<include>*page.md</include>
				</includes>
			</resource>
		</resources>
```

第一個 `<resource>` 不能省——一旦顯式宣告 `<resources>`,Maven 的預設值就失效,漏掉它會讓
`messages*.properties` 與 Freemarker 樣板全部不進 jar。

- [ ] **Step 6: 驗證語料真的進了 jar**

Run:
```bash
mvn -q clean package -DskipTests
unzip -l target/nginxWebUI-*.jar | grep -c "nginxdocumentation/.*page.md"
```
Expected: `150`

- [ ] **Step 7: Commit**

```bash
git add pom.xml src/main/java/com/cym/service/NginxDocService.java src/test/java/com/cym/service/NginxDocServiceTest.java
git commit -m "feat(mcp): index nginx directives and package the corpus into the jar"
```

---

### Task 3: 查無時的候選建議

**Files:**
- Modify: `src/main/java/com/cym/service/NginxDocService.java`
- Modify: `src/test/java/com/cym/service/NginxDocServiceTest.java`

**Interfaces:**
- Produces: `NginxDocService.suggest(String name) -> List<String>` — 最多 8 筆候選指令名

指令名又長又容易混淆（`proxy_read_timeout` vs `proxy_send_timeout`），查無時給候選比回「找不到」有用得多。

- [ ] **Step 1: 寫失敗測試**

```java
	@Test
	public void suggest_拼錯時給出正確候選() {
		assertTrue(svc.suggest("proxy_pas").contains("proxy_pass"));
		assertTrue(svc.suggest("proxy_read_timout").contains("proxy_read_timeout"));
	}

	@Test
	public void suggest_前綴相符也算候選() {
		assertTrue(svc.suggest("proxy_read").contains("proxy_read_timeout"));
	}

	@Test
	public void suggest_最多八筆() {
		assertTrue(svc.suggest("proxy").size() <= 8);
	}

	@Test
	public void suggest_完全不相干時回空() {
		assertTrue(svc.suggest("zzzzzzzzzz").isEmpty());
	}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `mvn -q test -Dtest=NginxDocServiceTest`
Expected: 編譯失敗,`suggest` 不存在

- [ ] **Step 3: 實作**

加到 `NginxDocService`:

```java
	/** 候選建議:前綴相符與編輯距離 <= 2 的聯集,最多 8 筆。 */
	public List<String> suggest(String name) {
		if (name == null || name.isBlank()) {
			return List.of();
		}
		String q = name.trim().toLowerCase();
		List<String> prefix = new ArrayList<>();
		List<String> near = new ArrayList<>();
		for (String key : directives.keySet()) {
			String k = key.toLowerCase();
			if (k.startsWith(q) || k.contains(q)) {
				prefix.add(key);
			} else if (editDistance(k, q) <= 2) {
				near.add(key);
			}
		}
		List<String> out = new ArrayList<>(prefix);
		for (String n : near) {
			if (!out.contains(n)) {
				out.add(n);
			}
		}
		return out.size() > 8 ? out.subList(0, 8) : out;
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
```

- [ ] **Step 4: 執行測試確認通過**

Run: `mvn -q test -Dtest=NginxDocServiceTest`
Expected: 10 個測試全綠

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cym/service/NginxDocService.java src/test/java/com/cym/service/NginxDocServiceTest.java
git commit -m "feat(mcp): suggest candidates when a directive lookup misses"
```

---

### Task 4: 設定檢查

**Files:**
- Create: `src/main/java/com/cym/utils/NginxConfChecker.java`
- Create: `src/test/java/com/cym/utils/NginxConfCheckerTest.java`

**Interfaces:**
- Consumes: `NginxDocService.directive(String)`、`NginxDocService.suggest(String)`
- Produces: `NginxConfChecker.check(String conf, NginxDocService svc) -> List<String>` — 每則格式為 `第 N 行: 訊息`;無問題回空清單

**設計取捨（實作前先讀）**：context 判斷只需要知道「目前在哪一層」,不需要完整語法樹,所以用括號與區塊關鍵字的簡易掃描,不引入 `com.github.odiszapc:nginxparser`。該依賴的 API 面向「讀寫既有 conf 檔」,對這裡的單向掃描是過重的工具,且它的解析失敗會讓整個檢查中斷——而檢查器的價值正在於對半成品設定也能給出意見。

**只回報能確定的問題。** 無法判斷的一律不報:誤報會讓使用者不信任整個工具。

- [ ] **Step 1: 寫失敗測試**

```java
// src/test/java/com/cym/utils/NginxConfCheckerTest.java
package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cym.service.NginxDocService;

public class NginxConfCheckerTest {

	private static NginxDocService svc;

	@BeforeAll
	public static void setUp() throws Exception {
		List<String> pages = new ArrayList<>();
		try (var paths = Files.list(Path.of("docs/nginxdocumentation"))) {
			for (Path p : paths.filter(x -> x.getFileName().toString().endsWith("page.md")).toList()) {
				pages.add(Files.readString(p));
			}
		}
		svc = new NginxDocService();
		svc.load(pages);
	}

	@Test
	public void check_合法設定不誤報() {
		String conf = """
				http {
				    server {
				        listen 80;
				        location / {
				            proxy_pass http://backend;
				        }
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}

	@Test
	public void check_指令用在錯誤context要抓到() {
		String conf = """
				http {
				    proxy_pass http://backend;
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size());
		assertTrue(problems.get(0).contains("proxy_pass"));
		assertTrue(problems.get(0).contains("http"));
	}

	@Test
	public void check_拼錯的指令要抓到並給候選() {
		String conf = """
				http {
				    server {
				        proxy_pas http://backend;
				    }
				}
				""";
		List<String> problems = NginxConfChecker.check(conf, svc);
		assertEquals(1, problems.size());
		assertTrue(problems.get(0).contains("proxy_pass"), "應提示正確拼法");
	}

	@Test
	public void check_註解與空行不誤報() {
		assertEquals(List.of(), NginxConfChecker.check("# comment\n\n  # another\n", svc));
	}
}
```

- [ ] **Step 2: 執行測試確認失敗**

Run: `mvn -q test -Dtest=NginxConfCheckerTest`
Expected: 編譯失敗,`NginxConfChecker` 不存在

- [ ] **Step 3: 實作**

```java
// src/main/java/com/cym/utils/NginxConfChecker.java
package com.cym.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.cym.model.NginxDirective;
import com.cym.service.NginxDocService;

/**
 * 拿一段 conf 文字對照官方文件檢查:指令是否存在、是否用在合法 context。
 *
 * 只做單向掃描追蹤區塊巢狀,不建完整語法樹——判斷 context 只需要知道「目前在哪一層」。
 * 也因此對半成品設定同樣能給意見,不會因為語法不完整就整份放棄。
 *
 * 只回報能確定的問題:無法判斷的一律不報,誤報會讓使用者不信任整個工具。
 */
public class NginxConfChecker {

	/** 已知的區塊指令;出現 name {  時推入這個 context。 */
	private static final List<String> BLOCKS = List.of(
			"http", "server", "location", "upstream", "stream", "events", "mail", "map", "geo",
			"types", "limit_except", "if", "charset_map", "split_clients");

	private NginxConfChecker() {
	}

	public static List<String> check(String conf, NginxDocService svc) {
		List<String> problems = new ArrayList<>();
		if (conf == null || conf.isBlank()) {
			return problems;
		}

		Deque<String> stack = new ArrayDeque<>();
		String[] lines = conf.split("\n");

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i].trim();
			int lineNo = i + 1;

			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.equals("}")) {
				stack.pollLast();
				continue;
			}

			String first = line.split("[\\s{;]", 2)[0];

			if (line.endsWith("{")) {
				stack.addLast(BLOCKS.contains(first) ? first : "unknown");
				continue;
			}
			if (!line.endsWith(";")) {
				continue; // 跨行指令,無法確定 → 不報
			}

			NginxDirective d = svc.directive(first);
			if (d == null) {
				List<String> hints = svc.suggest(first);
				if (!hints.isEmpty()) {
					problems.add("第 " + lineNo + " 行: 未知指令 " + first + ",是否想寫 " + String.join(" / ", hints) + " ?");
				}
				continue; // 沒有候選就不報:可能是第三方模組的指令
			}

			String ctx = stack.isEmpty() ? "main" : stack.peekLast();
			if ("unknown".equals(ctx) || d.contexts().isEmpty()) {
				continue; // 無法確定 → 不報
			}
			if (!d.contexts().contains(ctx)) {
				problems.add("第 " + lineNo + " 行: " + first + " 不能用在 " + ctx
						+ ",官方允許的 context 是 " + String.join(", ", d.contexts()) + " — " + d.sourceUrl());
			}
		}
		return problems;
	}
}
```

- [ ] **Step 4: 執行測試確認通過**

Run: `mvn -q test -Dtest=NginxConfCheckerTest`
Expected: 4 個測試全綠

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cym/utils/NginxConfChecker.java src/test/java/com/cym/utils/NginxConfCheckerTest.java
git commit -m "feat(mcp): check a pasted config against the official directive contexts"
```

---

### Task 5: MCP 端點與認證

**Files:**
- Create: `src/main/java/com/cym/mcp/NginxDocMcpServer.java`
- Modify: `pom.xml`（新增 `solon-ai-mcp` 依賴）
- Modify: `src/main/java/com/cym/config/AppFilter.java`（`doFilterDo` 內新增 `/mcp` 檢查）
- Modify: `src/main/java/com/cym/config/InitConfig.java`（啟動時載入索引）

**Interfaces:**
- Consumes: `NginxDocService` 的全部查詢方法、`NginxConfChecker.check`
- Produces: HTTP `/mcp` 端點,五個 MCP 工具

- [ ] **Step 1: 加依賴**

在 `pom.xml` 的 `<dependencies>` 內,`solon-web` 之後加入:

```xml
		<dependency>
			<groupId>org.noear</groupId>
			<artifactId>solon-ai-mcp</artifactId>
		</dependency>
```

版本由 `solon-parent` 3.10.7 管理,不要寫死版本號。

Run: `mvn -q dependency:resolve`
Expected: exit 0

- [ ] **Step 2: 寫 MCP 端點**

```java
// src/main/java/com/cym/mcp/NginxDocMcpServer.java
package com.cym.mcp;

import java.util.List;
import java.util.stream.Collectors;

import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.annotation.McpServerEndpoint;
import org.noear.solon.ai.mcp.annotation.ToolMapping;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;

import com.cym.model.NginxDirective;
import com.cym.service.NginxDocService;
import com.cym.utils.NginxConfChecker;

/**
 * nginx 官方文件的 MCP 端點。
 *
 * 這個類只做協定門面:參數描述、結果轉字串、錯誤訊息。查詢邏輯一律在 NginxDocService,
 * 檢查邏輯在 NginxConfChecker。工具方法保持在 20 行內,日後加「操作 nginxWebUI」那組
 * 工具時才不會互相糾纏。
 *
 * 所有工具唯讀。任何情況都回傳可讀字串,不向外拋例外——協定層的例外對 AI 是不透明的失敗。
 */
@McpServerEndpoint(channel = McpChannel.STREAMABLE, mcpEndpoint = "/mcp", name = "nginx-docs")
public class NginxDocMcpServer {

	@Inject
	NginxDocService docService;

	@ToolMapping(description = "查詢單一 nginx 指令的官方定義:語法、預設值、可用的 context、所屬模組與官方連結。查無時回傳拼法相近的候選。")
	public String nginx_directive(@Param(description = "指令名稱,例如 proxy_pass") String name) {
		NginxDirective d = docService.directive(name);
		if (d == null) {
			List<String> hints = docService.suggest(name);
			return hints.isEmpty()
					? "查無指令 " + name + "。可改用 nginx_search 以關鍵字搜尋。"
					: "查無指令 " + name + "。是否想查:" + String.join(" / ", hints);
		}
		return format(d);
	}

	@ToolMapping(description = "以關鍵字全文搜尋 nginx 官方文件,用於還不知道指令名稱時。回傳命中片段與來源連結。")
	public String nginx_search(@Param(description = "搜尋關鍵字") String query,
			@Param(description = "最多回傳幾筆,預設 10", required = false) Integer limit) {
		int n = (limit == null || limit <= 0) ? 10 : Math.min(limit, 30);
		List<String> hits = docService.search(query, n);
		return hits.isEmpty() ? "查無「" + query + "」的相關內容。" : String.join("\n\n---\n\n", hits);
	}

	@ToolMapping(description = "列出某個 nginx 模組的所有指令。簡寫若對應多個模組會回候選清單要求指定。")
	public String nginx_module(@Param(description = "模組名稱或簡寫,例如 ngx_http_proxy_module 或 proxy") String name) {
		List<String> hits = docService.byModule(name);
		if (hits.isEmpty()) {
			return "查無模組 " + name + "。";
		}
		if (hits.size() > 1) {
			return "「" + name + "」對應到多個模組,請指定其中一個:\n" + String.join("\n", hits);
		}
		String module = hits.get(0);
		String body = docService.directivesOfModule(module).stream()
				.map(d -> "- " + d.name() + " — " + d.syntax())
				.collect(Collectors.joining("\n"));
		return module + "\n\n" + body;
	}

	@ToolMapping(description = "反查某個 context(例如 location、server、http)裡能使用哪些指令。寫設定時用這個確認指令放對地方。")
	public String nginx_context(@Param(description = "context 名稱,例如 location") String context) {
		List<NginxDirective> list = docService.byContext(context);
		if (list.isEmpty()) {
			return "查無 context「" + context + "」。已知的 context:" + String.join(", ", docService.knownContexts());
		}
		return context + " 可用的指令(" + list.size() + " 條):\n"
				+ list.stream().map(d -> "- " + d.name() + " — " + d.syntax()).collect(Collectors.joining("\n"));
	}

	@ToolMapping(description = "拿一段 nginx 設定對照官方文件檢查:指令是否存在、是否用在合法的 context。只回報能確定的問題。")
	public String nginx_check_config(@Param(description = "要檢查的 nginx 設定文字") String conf) {
		List<String> problems = NginxConfChecker.check(conf, docService);
		return problems.isEmpty() ? "未發現問題。" : String.join("\n", problems);
	}

	private String format(NginxDirective d) {
		StringBuilder sb = new StringBuilder();
		sb.append("指令:").append(d.name())
				.append("\n語法:").append(d.syntax())
				.append("\n預設值:").append(d.defaultValue() == null ? "無" : d.defaultValue())
				.append("\n可用 context:").append(String.join(", ", d.contexts()))
				.append("\n模組:").append(d.module());
		if (!d.description().isBlank()) {
			sb.append("\n說明:").append(d.description());
		}
		return sb.append("\n官方文件:").append(d.sourceUrl()).toString();
	}
}
```

- [ ] **Step 3: 啟動時載入索引**

在 `InitConfig` 的 `start()` 方法尾端加入（先 `@Inject NginxDocService nginxDocService;` 到欄位區）：

```java
		// nginx 文件索引:只在啟用 MCP 時才載入,避免沒用到卻付出解析成本
		if (cn.hutool.core.util.StrUtil.isNotEmpty(org.noear.solon.Solon.cfg().get("mcp.token"))) {
			nginxDocService.loadFromClasspath();
		}
```

- [ ] **Step 4: 加認證**

在 `AppFilter.doFilterDo` 的「api过滤器」區塊之後、「分页保存过滤」之前插入：

```java
		// MCP 端點:未設定 --mcp.token 一律 404(opt-in,既有部署升級後行為不變);
		// 設定了則檢查 Authorization: Bearer <token>。
		if (path.startsWith("/mcp")) {
			String expected = org.noear.solon.Solon.cfg().get("mcp.token");
			if (StrUtil.isEmpty(expected)) {
				ctx.status(404);
				ctx.setHandled(true);
				return;
			}
			String auth = ctx.header("Authorization");
			if (auth == null || !auth.equals("Bearer " + expected)) {
				ctx.status(401);
				ctx.setHandled(true);
				return;
			}
		}
```

放在 `path` 已經 `toLowerCase()` 之後,所以用 `startsWith("/mcp")` 比對即可。

- [ ] **Step 5: 手動驗證端點**

先在**沒有** token 的情況下啟動:

```bash
mvn -q clean package -DskipTests
java -jar target/nginxWebUI-5.2.8.jar --server.port=18081 --project.home=./dev-home-mcp/ &
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:18081/mcp
```
Expected: `404`

再帶 token 啟動:

```bash
java -jar target/nginxWebUI-5.2.8.jar --server.port=18081 --project.home=./dev-home-mcp/ --mcp.token=testtoken &
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:18081/mcp
curl -s -X POST http://localhost:18081/mcp -H "Authorization: Bearer testtoken" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}' | head -c 400
```
Expected: 第一個 curl 不是 404（未帶 token 時為 401）；第二個回傳含五個工具名稱的 JSON。

- [ ] **Step 6: Commit**

```bash
git add pom.xml src/main/java/com/cym/mcp/NginxDocMcpServer.java src/main/java/com/cym/config/AppFilter.java src/main/java/com/cym/config/InitConfig.java
git commit -m "feat(mcp): expose the nginx docs as an opt-in MCP endpoint"
```

---

### Task 6: E2E、client 設定與文件

**Files:**
- Create: `tests/e2e/35-mcp.spec.js`
- Create: `.mcp.json`
- Modify: `README.md`、`README_TW.md`（功能清單各加一行）
- Modify: `CLAUDE.md`（Feature Inventory 加一行）

**Interfaces:**
- Consumes: `/mcp` 端點

- [ ] **Step 1: 寫 E2E**

```javascript
// tests/e2e/35-mcp.spec.js
const { test, expect } = require('@playwright/test');

// MCP 端點是 opt-in：測試 server 未帶 --mcp.token，所以端點必須不存在。
// 這條守的是「既有部署升級後行為零變化」這個承諾。
test.describe('MCP 端點', () => {
	test('未設定 token 時 /mcp 回 404', async ({ request, baseURL }) => {
		const res = await request.post(new URL('/mcp', baseURL).toString(), {
			data: { jsonrpc: '2.0', id: 1, method: 'tools/list' },
			failOnStatusCode: false,
		});
		expect(res.status()).toBe(404);
	});

	test('未設定 token 時 GET /mcp 也回 404', async ({ request, baseURL }) => {
		const res = await request.get(new URL('/mcp', baseURL).toString(), { failOnStatusCode: false });
		expect(res.status()).toBe(404);
	});
});
```

- [ ] **Step 2: 執行 E2E**

Run: `npx playwright test tests/e2e/35-mcp.spec.js --config=tests/e2e/playwright.fast.config.js`
Expected: 2 passed

- [ ] **Step 3: 寫 client 設定**

```json
{
  "mcpServers": {
    "nginx-docs": {
      "type": "http",
      "url": "http://localhost:12300/mcp",
      "headers": {
        "Authorization": "Bearer ${NGINX_WEBUI_MCP_TOKEN}"
      }
    }
  }
}
```

存成專案根目錄的 `.mcp.json`。token 走環境變數,不進版控。

codegraph 刻意不放進來:它需要 `.codegraph/` 索引,而該目錄在 `.gitignore` 第 69 行被排除,
是每個人自己建的本機產物;註冊在專案級會讓沒建索引的人一 clone 就遇到查不出原因的連線失敗。

- [ ] **Step 4: 更新三份文件**

`README.md` 的功能清單加一行：

```markdown
- **nginx docs MCP** — the 947 official directive definitions served over MCP: exact lookup, context reverse-lookup, and checking a config draft against the docs. Opt-in via `--mcp.token`.
```

`README_TW.md` 對應位置加一行：

```markdown
- **nginx 文件 MCP** — 947 條官方指令定義以 MCP 提供：精準查詢、context 反查、拿設定草稿對照文件檢查。以 `--mcp.token` opt-in 啟用。
```

`CLAUDE.md` 的 Feature Inventory 加一行：

```markdown
**nginx docs MCP (v5.2.9+):** 947 條指令定義啟動時從打包的 markdown 建索引（`NginxDocService`）· 五個唯讀工具（directive / search / module / context 反查 / check_config）· `@McpServerEndpoint(STREAMABLE, "/mcp")` · 認證走 `--mcp.token` opt-in，未設定則 `AppFilter` 回 404 · client 設定在 `.mcp.json`。
```

- [ ] **Step 5: 跑完整測試**

Run:
```bash
mvn -q test
npx playwright test --config=tests/e2e/playwright.fast.config.js
```
Expected: Java 測試全綠；Playwright 全綠（含新增的 35-mcp）

- [ ] **Step 6: Commit**

```bash
git add tests/e2e/35-mcp.spec.js .mcp.json README.md README_TW.md CLAUDE.md
git commit -m "feat(mcp): add endpoint E2E, client config and documentation"
```

---

## 風險與退路

- **`solon-ai-mcp` 的實際註解行為與文件不符**：`@McpServerEndpoint` 的 `channel`／`mcpEndpoint` 參數已由 context7 查證,但未實際跑過。Task 5 Step 5 的手動驗證就是這道關卡——若端點沒起來,停下回報,不要繞路自行實作 JSON-RPC。
- **947 這個數字對不上**：Task 1 Step 6 會擋下。表示語料有未預期的表格形態,停下回報而不是調整斷言。
- **jar 體積**：預期從 42.6 MB 增至約 44.6 MB。若超過 46 MB,表示 `<resources>` 設定把不該打包的東西也帶進去了。
- **啟動時間**：索引只在設定了 `--mcp.token` 時才建,沒啟用 MCP 的部署完全不受影響。
