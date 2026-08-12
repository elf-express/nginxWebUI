package com.cym.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

	@Test
	public void parsePage_Source為markdown連結的頁面也要解析() throws Exception {
		List<NginxDirective> list = NginxDocParser.parsePage(page("131page.md"));
		NginxDirective d = list.stream().filter(x -> "pass".equals(x.name())).findFirst().orElseThrow();
		assertEquals("ngx_stream_pass_module", d.module(), "標題底線被剝掉時應從 sourceUrl 推導");
		// sourceUrl 必須是乾淨的 URL:若 SOURCE 的字元類被放寬成 \S+,這裡會抓進 ](...) 尾巴,
		// 而 moduleFromUrl 仍會算出正確模組名 → 沒有這條斷言,髒 URL 會一路流進 MCP 回應。
		assertEquals("https://nginx.org/en/docs/stream/ngx_stream_pass_module.html", d.sourceUrl());
		assertTrue(d.contexts().contains("server"));
	}

	@Test
	public void parsePage_非nginx官方網域的Source要被擋下() {
		String md = "> Source: https://example.com/x\n\n<table cellspacing=\"0\"><tbody><tr><th>Syntax:</th>"
				+ "<td><code><strong>fake</strong> on;</code></td></tr></tbody></table>";
		assertTrue(NginxDocParser.parsePage(md).isEmpty());
	}

	@Test
	public void parsePage_全語料解析出969條指令其中947條來自官方表格() throws Exception {
		int total = 0;
		int official = 0;
		int summary = 0;
		try (var paths = Files.list(Path.of("docs/nginxdocumentation"))) {
			for (Path p : paths.filter(x -> x.getFileName().toString().endsWith("page.md")).toList()) {
				for (NginxDirective d : NginxDocParser.parsePage(Files.readString(p))) {
					total++;
					if (d.origin() == NginxDirective.Origin.OFFICIAL_TABLE) {
						official++;
					} else {
						summary++;
					}
				}
			}
		}
		assertEquals(947, official, "表格流程的行為不該被摘要流程動到,947 就是 947");
		assertEquals(22, summary, "7 頁手寫摘要頁共 22 條指令");
		assertEquals(969, total, "指令數與語料實測值不符,解析器漏了形態");
	}

	// ---- 手寫 zh-TW 摘要頁(沒有 nginx.org 表格) ----

	private List<String> names(String file) throws Exception {
		return NginxDocParser.parsePage(page(file)).stream().map(NginxDirective::name).toList();
	}

	@Test
	public void parsePage_七頁手寫摘要頁的指令名稱與語料一致() throws Exception {
		assertEquals(List.of("allow", "deny"), names("024page.md"));
		assertEquals(List.of("auth_request", "auth_request_set"), names("030page.md"));
		assertEquals(List.of("limit_conn_zone", "limit_conn", "limit_conn_dry_run",
				"limit_conn_log_level", "limit_conn_status"), names("054page.md"));
		assertEquals(List.of("limit_req_zone", "limit_req", "limit_req_dry_run",
				"limit_req_log_level", "limit_req_status"), names("055page.md"));
		// map 的標題是 h3,其餘六頁都是 h4 —— 只認 h4 會漏掉整頁
		assertEquals(List.of("map"), names("057page.md"));
		assertEquals(List.of("set_real_ip_from", "real_ip_header", "real_ip_recursive"), names("067page.md"));
		assertEquals(List.of("limit_conn_zone", "limit_conn", "limit_conn_dry_run",
				"limit_conn_log_level"), names("125page.md"));
	}

	@Test
	public void parsePage_摘要頁的欄位齊全且標記為PROJECT_SUMMARY() throws Exception {
		List<NginxDirective> list = NginxDocParser.parsePage(page("055page.md"));
		NginxDirective d = list.stream().filter(x -> "limit_req".equals(x.name())).findFirst().orElseThrow();

		assertEquals(List.of("http", "server", "location"), d.contexts(), "context 取自標題括號裡的 backtick");
		assertTrue(d.syntax().startsWith("limit_req "), "沒有語法條列時取 fenced block 首行,實際:" + d.syntax());
		assertEquals("ngx_http_limit_req_module", d.module());
		assertEquals("https://nginx.org/en/docs/http/ngx_http_limit_req_module.html", d.sourceUrl());
		assertFalse(d.description().isBlank(), "說明段落不該是空的");
		// null 的意思在兩種來源下不同:表格頁是「官方寫無預設值」,摘要頁只是「沒列出」。
		// 沒有 origin,MCP 會對 limit_req_status 回答「無預設值」,但它實際預設 503。
		assertNull(d.defaultValue());
		assertEquals(NginxDirective.Origin.PROJECT_SUMMARY, d.origin());
	}

	@Test
	public void parsePage_標題括號裡的版本號不可被當成context() throws Exception {
		List<NginxDirective> list = NginxDocParser.parsePage(page("054page.md"));
		NginxDirective d = list.stream().filter(x -> "limit_conn_dry_run".equals(x.name())).findFirst().orElseThrow();
		// 「#### `limit_conn_dry_run`（1.17.6）」的括號裝的是版本號。分辨規則:括號內有 backtick 才是 context。
		assertFalse(d.contexts().contains("1.17.6"), "版本號被當成 context 了:" + d.contexts());
		assertTrue(d.contexts().isEmpty(), "這條沒有任何 context 資訊,實際:" + d.contexts());
	}

	@Test
	public void parsePage_語境寫同上時沿用前一條指令() throws Exception {
		List<NginxDirective> list = NginxDocParser.parsePage(page("024page.md"));
		NginxDirective allow = list.stream().filter(x -> "allow".equals(x.name())).findFirst().orElseThrow();
		NginxDirective deny = list.stream().filter(x -> "deny".equals(x.name())).findFirst().orElseThrow();

		assertEquals(List.of("http", "server", "location", "limit_except"), allow.contexts());
		assertEquals(allow.contexts(), deny.contexts(), "deny 的語境寫「同上」,要反向參照 allow");
		assertEquals("allow address | CIDR | unix: | all;", allow.syntax(), "語法條列要剝掉 backtick");
	}

	@Test
	public void parsePage_摘要頁明文寫出的預設值要照抄() throws Exception {
		List<NginxDirective> realip = NginxDocParser.parsePage(page("067page.md"));
		NginxDirective header = realip.stream().filter(x -> "real_ip_header".equals(x.name())).findFirst().orElseThrow();
		// 照抄語料,不削成 X-Real-IP; —— nginx.org 的 Default 欄本來就印完整指令形式(aio off;),
		// 摘要頁與 92 頁表格頁的 defaultValue 要長得一樣,呼叫端才不用分兩種格式處理。
		assertEquals("real_ip_header X-Real-IP;", header.defaultValue());
		assertEquals(NginxDirective.Origin.PROJECT_SUMMARY, header.origin());

		NginxDirective recursive = realip.stream().filter(x -> "real_ip_recursive".equals(x.name())).findFirst().orElseThrow();
		assertEquals("off", recursive.defaultValue());

		NginxDirective authRequest = NginxDocParser.parsePage(page("030page.md")).stream()
				.filter(x -> "auth_request".equals(x.name())).findFirst().orElseThrow();
		assertEquals("off", authRequest.defaultValue());
		// 預設值歸 defaultValue,說明要跳過那條 bullet 取真正的說明 —— 否則說明會變成「**預設：** off」
		assertEquals("啟用並指定子請求 URI。", authRequest.description());
	}

	@Test
	public void parsePage_摘要頁沒明文寫預設值的仍是null() throws Exception {
		// 語料只在散文裡提到「預設 `503`」,沒有 - **預設：** 條列。不臆測 —— 維持 null,
		// 由 origin=PROJECT_SUMMARY 表達「這是未列出」而不是「確實沒有預設值」。
		NginxDirective d = NginxDocParser.parsePage(page("055page.md")).stream()
				.filter(x -> "limit_req_status".equals(x.name())).findFirst().orElseThrow();
		assertNull(d.defaultValue());
	}

	// ---- 以下三條用合成 markdown。測的正是今日語料裡「還不存在」的形態:
	// 本任務的立論就是使用者之後會用自己的格式新增 151page.md,這些洞要到那時候才會咬人。
	// 真語料測不到它們(用真語料反而會綠得毫無意義),所以這裡不違反「用真語料」的精神。

	private static final String FAKE_PAGE_HEADER = """
			# 模組 ngx_http_fake_module

			> Source: https://nginx.org/en/docs/http/ngx_http_fake_module.html

			""";

	@Test
	public void parsePage_指令段落止於下一個標題不會吃到後面章節的範例() {
		String md = FAKE_PAGE_HEADER + """
				#### `fake_directive`

				- 這條沒有語法條列,也沒有自己的 fenced block。

				### 本專案

				```nginx
				unrelated_example on;
				```
				""";
		NginxDirective d = only(md);
		// body 若以「下一個指令標題」為界(而不是下一個任意標題),這裡會抓到 ### 本專案 的範例,
		// 把毫不相干的 unrelated_example 當成 fake_directive 的語法。
		assertEquals("", d.syntax(), "syntax 不該跨過章節標題去抓範例,實際:" + d.syntax());
		assertEquals("這條沒有語法條列,也沒有自己的 fenced block。", d.description());
	}

	@Test
	public void parsePage_fenced_block裡頂格的nginx註解不可被當成標題() {
		String md = FAKE_PAGE_HEADER + """
				#### `fake_directive`

				```nginx
				fake_directive on;
				# nginx 註解頂格寫是常態,不是 markdown 標題
				```

				- **語境：** `http`, `server`
				- 說明文字。
				""";
		NginxDirective d = only(md);
		// 把註解當標題會讓 body 在那一行就結束,後面的語境條列與說明整段消失 —— 而且是靜默消失。
		assertEquals(List.of("http", "server"), d.contexts(), "fenced block 裡的頂格 # 截斷了 body");
		assertEquals("說明文字。", d.description());
		assertEquals("fake_directive on;", d.syntax());
	}

	@Test
	public void parsePage_fenced_block開頭的註解不可被當成語法() {
		String md = FAKE_PAGE_HEADER + """
				#### `fake_directive`

				```nginx
				# 範例說明:註解寫在範例開頭很常見
				fake_directive on;
				```

				- 說明文字。
				""";
		// MCP 會把 syntax 當成權威的 nginx 語法餵給 AI。顯示成 `# 範例說明` 比顯示成空字串糟得多 ——
		// 空字串至少誠實,錯值會被當真。與上面那條(註解在指令行之後)互補,鎖住 fence 內註解的前後兩種位置。
		assertEquals("fake_directive on;", only(md).syntax());
	}

	@Test
	public void parsePage_標題有多個括號時仍找得到context() {
		String md = FAKE_PAGE_HEADER + """
				#### `fake_directive`（1.3.0）（僅 `http`）

				- 說明文字。
				""";
		// 只看第一個括號的話,（1.3.0）沒有 backtick 就直接放棄,（僅 `http`）整個被丟掉。
		assertEquals(List.of("http"), only(md).contexts());
	}

	/** 合成頁面只有一條指令,直接取出來。順便斷言沒有多抽出東西。 */
	private NginxDirective only(String markdown) {
		List<NginxDirective> list = NginxDocParser.parsePage(markdown);
		assertEquals(1, list.size(), "應該只抽出一條指令,實際:" + list.stream().map(NginxDirective::name).toList());
		assertEquals("fake_directive", list.get(0).name());
		assertEquals(NginxDirective.Origin.PROJECT_SUMMARY, list.get(0).origin());
		return list.get(0);
	}

	@Test
	public void parsePage_contexts不論來源都是不可變的() throws Exception {
		// record 不做防禦性複製。表格頁傳可變 ArrayList、摘要頁傳 List.of,同一個欄位兩種行為 ——
		// 呼叫端只要在其中一種上面測過 add() 就會誤以為到處都能改。
		NginxDirective fromTable = NginxDocParser.parsePage(page("100page.md")).get(0);
		NginxDirective fromSummary = NginxDocParser.parsePage(page("024page.md")).get(0);
		assertEquals(NginxDirective.Origin.OFFICIAL_TABLE, fromTable.origin());
		assertEquals(NginxDirective.Origin.PROJECT_SUMMARY, fromSummary.origin());
		assertThrows(UnsupportedOperationException.class, () -> fromTable.contexts().add("x"));
		assertThrows(UnsupportedOperationException.class, () -> fromSummary.contexts().add("x"));
	}

	@Test
	public void parsePage_摘要流程只在表格抽不到東西時才跑() throws Exception {
		// 觸發條件是「有官方 Source 且表格流程抽出 0 條」,全語料 150 頁裡有 58 頁符合。
		// 其餘 51 頁沒有 `#### `name`` 形態的標題,必須抽出 0 條 ——
		// 否則章節標題會被當成指令灌進索引,Task 4 的設定檢查就會放行不存在的指令。
		List<String> summaryPages = new ArrayList<>();
		try (var paths = Files.list(Path.of("docs/nginxdocumentation"))) {
			for (Path p : paths.filter(x -> x.getFileName().toString().endsWith("page.md")).sorted().toList()) {
				boolean any = NginxDocParser.parsePage(Files.readString(p)).stream()
						.anyMatch(d -> d.origin() == NginxDirective.Origin.PROJECT_SUMMARY);
				if (any) {
					summaryPages.add(p.getFileName().toString());
				}
			}
		}
		assertEquals(List.of("024page.md", "030page.md", "054page.md", "055page.md",
				"057page.md", "067page.md", "125page.md"), summaryPages);
	}
}
