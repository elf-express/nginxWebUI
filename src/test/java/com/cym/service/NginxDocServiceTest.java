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
	}

	@Test
	public void suggest_最多八筆() {
		assertTrue(svc.suggest("proxy").size() <= 8);
	}

	@Test
	public void suggest_完全不相干時回空() {
		assertTrue(svc.suggest("zzzzzzzzzz").isEmpty());
	}

	@Test
	public void loadFromClasspath_語料真的在classpath裡() {
		NginxDocService fromCp = new NginxDocService();
		fromCp.loadFromClasspath();
		assertTrue(fromCp.size() > 900, "classpath 載入到 " + fromCp.size() + " 條,語料可能沒進 target/classes");
	}
}
