package com.cym.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cym.service.NginxDocService;

/**
 * 鎖住 MCP 門面層「怎麼把查詢結果講給 AI 聽」的判斷。
 *
 * 這一層的錯誤不會讓任何東西壞掉,只會讓 AI 拿到錯的 nginx 語意 —— 例如把
 * limit_req_status 講成「沒有預設值」(它其實預設 503)。沒有測試的話,日後任何人整理
 * format() 都可能把這件事悄悄改回去而沒人發現。
 *
 * 直接 new + 賦值 docService(package-private),不起 Solon 容器:這五個工具本來就是純函式,
 * 進容器只會讓測試變慢又變脆。
 */
public class NginxDocMcpServerTest {

	private static NginxDocMcpServer server;

	@BeforeAll
	public static void setUp() throws Exception {
		List<String> pages = new ArrayList<>();
		// 與 NginxDocServiceTest 相同:依檔名排序對齊 loadFromClasspath 的 001→200 順序,
		// 否則同名跨模組指令(proxy_pass)的先到先贏順序在不同平台上會不一樣。
		try (var paths = Files.list(Path.of("docs/nginxdocumentation"))) {
			for (Path p : paths.filter(x -> x.getFileName().toString().endsWith("page.md")).sorted().toList()) {
				pages.add(Files.readString(p));
			}
		}
		NginxDocService svc = new NginxDocService();
		svc.load(pages);

		server = new NginxDocMcpServer();
		server.docService = svc;
	}

	// ---- correction box 的四項行為 ----

	/**
	 * 摘要頁來源 + 沒列預設值 → 只能說「文件未列出」。
	 *
	 * 這裡若退回「無」,MCP 就會對 limit_req_status 回答「預設值:無」,而它實際預設 503。
	 * 對 AI 講錯 nginx 語意,正是這整個功能要防的事。
	 */
	@Test
	public void 摘要頁來源的null預設值講成文件未列出() {
		String out = server.nginx_directive("limit_req_status");
		assertTrue(out.contains("預設值:文件未列出"), out);
		assertFalse(out.contains("預設值:無"), out);
		// 同時要標明來源,呼叫端才知道這條的完整度跟官方表格不一樣
		assertTrue(out.contains("zh-TW 摘要頁"), out);
	}

	/** 官方表格來源 + Default 欄是「—」→ 確實沒有預設值,要講死。 */
	@Test
	public void 官方表格來源的null預設值講成確實沒有預設值() {
		String out = server.nginx_directive("proxy_pass");
		assertTrue(out.contains("預設值:無(官方文件標示無預設值)"), out);
		// 官方表格條目不該掛上摘要頁的來源說明
		assertFalse(out.contains("zh-TW 摘要頁"), out);
	}

	/** 語料沒寫的欄位一律「文件未列出」,不能輸出空白 —— 空的 context 會被讀成「哪裡都不能用」。 */
	@Test
	public void 空欄位印文件未列出而不是空白() {
		String out = server.nginx_directive("limit_req_status");
		assertTrue(out.contains("語法:文件未列出"), out);
		assertTrue(out.contains("可用 context:文件未列出"), out);
		assertFalse(out.contains("語法:\n"), out);
		assertFalse(out.contains("可用 context:\n"), out);
	}

	/**
	 * 沒問題時的措辭不能退回「未發現問題。」。
	 *
	 * 檢查器刻意「寧可漏不可誤」,有四類已知漏報;講成保證會讓 AI 把「我們查不出問題」
	 * 讀成「這份設定正確」,而那正是最貴的一種誤導。
	 */
	@Test
	public void 沒問題時的措辭不得講成保證() {
		String out = server.nginx_check_config("http {\n  server {\n    listen 80;\n  }\n}\n");
		assertTrue(out.contains("不代表設定完全正確"), out);
		assertFalse(out.equals("未發現問題。"), out);
	}

	/** 清單型輸出在 syntax 為空時只印名字,不留懸空的破折號。 */
	@Test
	public void 清單列的空語法不留懸空破折號() {
		String out = server.nginx_module("ngx_http_realip_module");
		assertTrue(out.contains("\n- real_ip_header\n") || out.endsWith("\n- real_ip_header"), out);
		assertFalse(out.contains("- real_ip_header —"), out);
		// 有語法的照常印
		assertTrue(out.contains("- set_real_ip_from — set_real_ip_from"), out);
	}

	/** 截斷時必須講出總數與取得其餘的方法 —— 可以少給,但不能讓呼叫端以為拿到的是全部。 */
	@Test
	public void 截斷時標題要講總數與怎麼拿全部() {
		List<com.cym.model.NginxDirective> all = new ArrayList<>();
		all.addAll(server.docService.byContext("location"));
		String out = server.nginx_context("location", 5);

		assertTrue(out.contains("共 " + all.size() + " 條"), out);
		assertTrue(out.contains("以下列出前 5 條"), out);
		assertTrue(out.contains("省略 limit"), out);
		assertEquals(5, out.lines().filter(l -> l.startsWith("- ")).count(), out);
	}

	/** 省略 limit 就是全部,標題不能誆稱截斷。 */
	@Test
	public void 沒給limit就回全部且不說截斷() {
		int total = server.docService.byContext("location").size();
		String out = server.nginx_context("location", null);

		assertTrue(out.startsWith("location 可用的指令(" + total + " 條):"), out.substring(0, 60));
		assertFalse(out.contains("以下列出前"), out.substring(0, 60));
		assertEquals(total, out.lines().filter(l -> l.startsWith("- ")).count());
	}

	/** limit 大於總數不算截斷;0 與負數視同不限制。 */
	@Test
	public void limit超過總數或非正數都不算截斷() {
		int total = server.docService.byContext("events").size();
		for (Integer limit : new Integer[] { 999, 0, -3 }) {
			String out = server.nginx_context("events", limit);
			assertFalse(out.contains("以下列出前"), "limit=" + limit + " → " + out);
			assertEquals(total, out.lines().filter(l -> l.startsWith("- ")).count(), "limit=" + limit);
		}
	}

	// ---- 參數缺漏 ----

	/** 參數沒給時要明講,不能把 null 當成查詢字串印出來。 */
	@Test
	public void 參數為null時不輸出字面null() {
		for (String out : List.of(
				server.nginx_directive(null),
				server.nginx_search(null, null),
				server.nginx_module(null),
				server.nginx_context(null, null),
				server.nginx_check_config(null))) {
			assertFalse(out.contains("null"), out);
			assertTrue(out.startsWith("請提供"), out);
		}
	}

	/** 空白字串與 null 同等對待 —— MCP client 兩種都送得出來。 */
	@Test
	public void 空白參數與null同等對待() {
		assertTrue(server.nginx_directive("   ").startsWith("請提供"));
		assertTrue(server.nginx_check_config("").startsWith("請提供"));
	}

	// ---- 設定鍵 ----

	/**
	 * TOKEN_KEY 是 @Condition(bean 註冊)、AppFilter(認證)、InitConfig(載語料)三處共用的鍵。
	 * 改動它等於同時改變這三件事,測試在這裡釘住字面值當作提醒。
	 */
	@Test
	public void token設定鍵維持mcp_token() {
		assertEquals("mcp.token", NginxDocMcpServer.TOKEN_KEY);
	}

	/**
	 * ENDPOINT 必須全小寫。
	 *
	 * AppFilter 比對的是 {@code ctx.path().toLowerCase()};常數若含大寫,startsWith 恆為 false,
	 * 端點照樣掛得起來但認證閘完全失效 —— 這是 fail-open,比 token key 改錯嚴重得多
	 * (那個只會 404)。所以這條不是風格檢查,是安全不變式。
	 */
	@Test
	public void 端點路徑必須全小寫否則認證閘失效() {
		assertEquals(NginxDocMcpServer.ENDPOINT.toLowerCase(), NginxDocMcpServer.ENDPOINT);
		assertTrue(NginxDocMcpServer.ENDPOINT.startsWith("/"), NginxDocMcpServer.ENDPOINT);
	}
}
