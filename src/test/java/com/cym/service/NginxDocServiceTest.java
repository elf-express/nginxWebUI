package com.cym.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.cym.model.NginxDirective;
import com.cym.utils.NginxDocParser;

public class NginxDocServiceTest {

	private static NginxDocService svc;
	private static final List<String> pages = new ArrayList<>();

	@BeforeAll
	public static void setUp() throws Exception {
		// 依檔名排序,對齊 loadFromClasspath 的 001→200 順序:122 個指令名同時存在於多個模組
		// (proxy_pass 在 http 與 stream 都有),索引是先到先贏,而 Files.list 的順序在 Linux 上不保證。
		try (var paths = Files.list(Path.of("docs/nginxdocumentation"))) {
			for (Path p : paths.filter(x -> x.getFileName().toString().endsWith("page.md")).sorted().toList()) {
				pages.add(Files.readString(p));
			}
		}
		svc = new NginxDocService();
		svc.load(pages);
	}

	@Test
	public void directive_查得到並帶完整欄位() {
		NginxDirective d = svc.directive("proxy_pass").stream()
				.filter(x -> "ngx_http_proxy_module".equals(x.module())).findFirst().orElseThrow();
		assertTrue(d.contexts().contains("location"));
		assertTrue(d.sourceUrl().startsWith("https://nginx.org/"));
	}

	@Test
	public void directive_查無回空清單() {
		assertTrue(svc.directive("proxy_pas").isEmpty());
	}

	@Test
	public void directive_同名跨模組時全部回傳() {
		List<NginxDirective> list = svc.directive("proxy_pass");
		assertTrue(list.size() >= 2, "proxy_pass 同時存在於 http 與 stream 模組");
		assertTrue(list.stream().anyMatch(d -> d.module().contains("stream")));
		assertTrue(list.stream().anyMatch(d -> d.module().contains("http")));
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
		// 語料是部分翻譯的 zh-TW 版,brief 原本用的 "reverse proxy" 全語料 0 次命中
		// (已譯為「反向代理」)。改用同樣是自由文字、且確實存在的英文概念詞。
		List<String> hits = svc.search("load balancing", 5);
		assertFalse(hits.isEmpty());
		assertTrue(hits.size() <= 5);
		assertTrue(hits.get(0).contains("https://nginx.org/"));
	}

	@Test
	public void search_limit真的截斷() {
		// load balancing 只命中 1 頁,截斷路徑不會被走到;nginx 每頁的 Source 標頭都有,150 頁全中。
		assertEquals(3, svc.search("nginx", 3).size());
		assertTrue(svc.search("nginx", 0).isEmpty(), "limit 0 不該回任何結果");
	}

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
	public void suggest_前綴候選排在子字串候選之前() {
		// ssl_certificat 的子字串命中(grpc_/proxy_/uwsgi_/zone_sync_ 開頭)有十幾個,沒有前綴優先
		// 就會把使用者真正要的 ssl_certificate_key 擠出 8 筆之外。
		List<String> hits = svc.suggest("ssl_certificat");
		assertTrue(hits.contains("ssl_certificate_key"), "實際回傳:" + hits);
		assertTrue(hits.get(0).startsWith("ssl_certificat"), "第一筆該是前綴相符,實際:" + hits);
		// 光看前兩條擋不住「把兩個 bucket 併回一串」——長度排序會讓 ssl_certificate 照樣排第一。
		// 真正的性質是:前綴相符的要全部排在非前綴相符之前(ssl_certificate_compression 早於
		// grpc_ssl_certificate,即使它比較長)。
		int lastPrefix = -1;
		int firstOther = hits.size();
		for (int i = 0; i < hits.size(); i++) {
			if (hits.get(i).startsWith("ssl_certificat")) {
				lastPrefix = i;
			} else {
				firstOther = Math.min(firstOther, i);
			}
		}
		assertTrue(lastPrefix < firstOther, "前綴相符必須全部排在子字串相符之前,實際:" + hits);
	}

	@Test
	public void suggest_前綴bucket內短的優先() {
		// 語料順序下 user 排在 9 個 userid_* 之後,不排序就會被 8 筆額度切掉。
		// 這條路徑走得到:directive() 大小寫敏感而 suggest() 不敏感,查 "User" 就會落到這裡。
		assertEquals("user", svc.suggest("User").get(0));
		assertEquals("use", svc.suggest("use").get(0));
		assertEquals("proxy", svc.suggest("Proxy").get(0));
	}

	@Test
	public void suggest_最多八筆() {
		assertTrue(svc.suggest("proxy").size() <= 8);
	}

	@Test
	public void suggest_編輯距離二收三拒() {
		// 換位錯字:proxy_redirect 既不是 proxy_reidrect 的前綴也不是子字串,只能靠編輯距離(=2)撈回來。
		// 用前綴型的錯字(例如 worker_process)測不到這條路 —— 它會在 prefix bucket 就命中。
		assertTrue(svc.suggest("proxy_reidrect").contains("proxy_redirect"));
		// sendfile 距 sendfile_on 是 3,超過門檻;差一的實作會讓這條變綠。
		assertTrue(svc.suggest("sendfile_on").isEmpty());
	}

	@Test
	public void suggest_完全不相干時回空() {
		assertTrue(svc.suggest("zzzzzzzzzz").isEmpty());
	}

	@Test
	public void suggest_回傳不可變() {
		assertThrows(UnsupportedOperationException.class, () -> svc.suggest("proxy").add("x"));
	}

	@Test
	public void directive_本專案自己產生的設定查得到() {
		// 前 4 條只存在於手寫 zh-TW 摘要頁,少了摘要流程就完全不在索引裡,設定檢查會把 nginxWebUI
		// 自己輸出的合法 conf 判成「指令不存在」。後 2 條(allow / map)的 stream 版有官方表格,
		// 但 http 版同樣只來自摘要頁 —— 見 directive_limit_conn的http與stream版都要在。
		for (String name : List.of("limit_req", "limit_conn", "real_ip_header", "auth_request", "allow", "map")) {
			assertFalse(svc.directive(name).isEmpty(), name + " 不在索引裡");
		}
		NginxDirective d = svc.directive("limit_req").get(0);
		assertEquals(List.of("http", "server", "location"), d.contexts());
		assertEquals(NginxDirective.Origin.PROJECT_SUMMARY, d.origin());
	}

	@Test
	public void directive_limit_conn的http與stream版都要在() {
		// 兩者的 context 與超限行為完全不同(stream 是關閉連線,沒有狀態碼),
		// 只回一筆會讓 AI 拿到另一層的 context 而毫無察覺。
		List<NginxDirective> list = svc.directive("limit_conn");
		assertEquals(2, list.size(), "實際:" + list);
		assertTrue(list.stream().anyMatch(d -> d.contexts().equals(List.of("http", "server", "location"))));
		assertTrue(list.stream().anyMatch(d -> d.contexts().equals(List.of("stream", "server"))));
	}

	@Test
	public void byContext_摘要頁的指令沒有汙染context清單() {
		// 「#### `limit_conn_dry_run`（1.17.6）」的括號裝的是版本號。抽錯就會多出一個叫 1.17.6 的 context,
		// 而 byContext 是 MCP 反查「這一層能用什麼指令」的入口,多一個假 context 就是多一個假答案。
		assertTrue(svc.byContext("1.17.6").isEmpty());
		assertTrue(svc.knownContexts().stream().noneMatch(c -> c.matches("[0-9].*")), svc.knownContexts().toString());
	}

	@Test
	public void 語料統計與README引用的數字一致() {
		// README / README_TW / CLAUDE.md 都寫「969 條指令 / 803 個相異名稱 / 99 個模組 / 15 個 context」。
		// 969 有 NginxDocParserTest 守著,另外三個原本沒有任何測試 —— 語料一改動,
		// 只有文件會默默過時,而文件正是使用者判斷「這個索引完不完整」的唯一依據。
		//
		// 這裡從同一份公開 parser API 重算(計數規則對齊 NginxDocService.load:module 為空的不計入),
		// 最後再與 service 自己的公開統計交叉核對,確保重算沒有偏離 load() 的實際行為。
		Set<String> names = new HashSet<>();
		Set<String> modules = new HashSet<>();
		Set<String> contexts = new HashSet<>();
		int total = 0;
		for (String md : pages) {
			for (NginxDirective d : NginxDocParser.parsePage(md)) {
				total++;
				names.add(d.name());
				contexts.addAll(d.contexts());
				if (!d.module().isEmpty()) {
					modules.add(d.module());
				}
			}
		}
		assertEquals(969, total, "指令總條數");
		assertEquals(803, names.size(), "相異指令名稱");
		assertEquals(99, modules.size(), "模組數");
		assertEquals(15, contexts.size(), "context 數");

		assertEquals(total, svc.size(), "重算的總條數必須與 service 一致");
		assertEquals(contexts.size(), svc.knownContexts().size(), "重算的 context 數必須與 service 一致");
	}

	@Test
	public void loadFromClasspath_語料真的在classpath裡() {
		NginxDocService fromCp = new NginxDocService();
		fromCp.loadFromClasspath();
		assertTrue(fromCp.size() > 900, "classpath 載入到 " + fromCp.size() + " 條,語料可能沒進 target/classes");
	}
}
