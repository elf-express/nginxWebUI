package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.noear.solon.core.handle.ContextHolder;

import com.cym.testsupport.FakeContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** remote/src 只代為讀取遠端節點的驗證碼圖片：其他協定、路徑、非圖片回應與跳轉一律不讀。 */
class RemoteControllerSrcTest {
	private static final String CODE_PATH = "/adminPage/login/getRemoteCode";
	private static final String IMAGE = "GIF89a-fake-captcha";
	private static final String SECRET = "INTERNAL-ONLY-SECRET";

	private RemoteController controller;
	private FakeContext ctx;
	private HttpServer server;
	private String base;
	/** 驗證碼路徑的回應模式：gif（預設）、html、svg、redirect */
	private volatile String mode = "gif";

	@BeforeEach
	void setUp() throws IOException {
		controller = new RemoteController();
		ctx = new FakeContext("/adminPage/remote/src");
		ContextHolder.currentSet(ctx);

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext(CODE_PATH, ex -> {
			switch (mode) {
			case "html" -> respond(ex, 200, "text/html", SECRET);
			case "svg" -> respond(ex, 200, "image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\" onload=\"alert(1)\"/>");
			case "redirect" -> {
				ex.getResponseHeaders().add("Location", base + "/secret");
				respond(ex, 302, "image/gif", "");
			}
			default -> respond(ex, 200, "image/gif", IMAGE);
			}
		});
		server.createContext("/secret", ex -> respond(ex, 200, "image/gif", SECRET));
		server.start();
		base = "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@AfterEach
	void tearDown() {
		server.stop(0);
		ContextHolder.currentRemove();
	}

	private static void respond(HttpExchange ex, int status, String type, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		ex.getResponseHeaders().add("Content-Type", type);
		ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			try (OutputStream os = ex.getResponseBody()) {
				os.write(bytes);
			}
		}
		ex.close();
	}

	private void assertRejected(String url) throws Exception {
		controller.src(url);
		assertEquals("", ctx.outputText(), url);
		assertEquals(400, ctx.status(), url);
	}

	@Test
	void captchaImageOfNodeBeingAddedIsProxied() throws Exception {
		controller.src(base + CODE_PATH + "?t=abc");
		assertEquals(IMAGE, ctx.outputText());
		assertEquals("image/gif", ctx.contentType());
	}

	@Test
	void fileUrlIsRejected(@TempDir Path dir) throws Exception {
		Path canary = Files.writeString(dir.resolve("canary.txt"), SECRET);
		assertRejected(canary.toUri().toString());
	}

	@Test
	void ftpAndJarUrlsAreRejected() throws Exception {
		assertRejected("ftp://127.0.0.1:1" + CODE_PATH);
		assertRejected("jar:file:/tmp/x.jar!" + CODE_PATH);
	}

	@Test
	void pathOtherThanRemoteCaptchaIsRejected() throws Exception {
		assertRejected(base + "/secret");
		assertRejected(base + CODE_PATH + "/../../secret");
	}

	@Test
	void nonImageResponseIsNotRelayed() throws Exception {
		mode = "html";
		assertRejected(base + CODE_PATH);
	}

	@Test
	void svgResponseIsNotRelayed() throws Exception {
		mode = "svg";
		assertRejected(base + CODE_PATH);
	}

	@Test
	void redirectIsNotFollowed() throws Exception {
		mode = "redirect";
		assertRejected(base + CODE_PATH);
	}

	@Test
	void linkLocalAndMetadataAddressesAreRejected() throws Exception {
		assertRejected("http://169.254.169.254" + CODE_PATH);
		assertRejected("http://[fe80::1]" + CODE_PATH);
		assertRejected("http://0.0.0.0" + CODE_PATH);
	}

	@Test
	void urlWithCredentialsOrWithoutHostIsRejected() throws Exception {
		assertRejected("http://user:pw@127.0.0.1:1" + CODE_PATH);
		assertRejected("http:" + CODE_PATH);
		assertRejected("not a url");
		assertRejected(null);
	}
}
