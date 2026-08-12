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
		// 語料是部分翻譯的 zh-TW 版,brief 原本用的 "reverse proxy" 全語料 0 次命中
		// (已譯為「反向代理」)。改用同樣是自由文字、且確實存在的英文概念詞。
		List<String> hits = svc.search("load balancing", 5);
		assertFalse(hits.isEmpty());
		assertTrue(hits.size() <= 5);
		assertTrue(hits.get(0).contains("https://nginx.org/"));
	}
}
