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
	public void parsePage_全語料解析出947條指令() throws Exception {
		int total = 0;
		try (var paths = Files.list(Path.of("docs/nginxdocumentation"))) {
			for (Path p : paths.filter(x -> x.getFileName().toString().endsWith("page.md")).toList()) {
				total += NginxDocParser.parsePage(Files.readString(p)).size();
			}
		}
		assertEquals(947, total, "指令表格數與語料實測值不符,解析器漏了形態");
	}
}
