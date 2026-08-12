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

	@Test
	public void check_語料沒寫context的指令不誤報() {
		// limit_req_status 來自手寫摘要頁,contexts 是空的。空 = 不知道,不是「哪裡都不能用」。
		String conf = """
				http {
				    server {
				        limit_req_status 429;
				    }
				}
				""";
		assertEquals(List.of(), NginxConfChecker.check(conf, svc));
	}
}
