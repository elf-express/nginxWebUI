package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.ContextHolder;

import com.cym.ext.AsycPack;
import com.cym.model.Admin;
import com.cym.model.Remote;
import com.cym.service.ConfService;
import com.cym.service.SettingService;
import com.cym.testsupport.FakeContext;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.testsupport.TestMessages;
import com.cym.utils.JsonResult;
import com.cym.utils.MessageUtils;
import com.sun.net.httpserver.HttpServer;

import cn.hutool.core.util.ReflectUtil;

/**
 * 「從遠端同步」：主控端向來源節點取同步資料要用 POST（新版節點的 getAsycPack 只收 POST），
 * 取不到資料時回失敗，不能顯示成功（E-161）。
 */
class RemoteControllerAsycTest {
	private static final String CREDIT_KEY = "credit-key-1";
	private static final String PACK = "{\"basicList\":[{\"name\":\"worker_processes\",\"value\":\"2\"}]}";
	private static final String NOT_ALLOWED = "{\"success\":false,\"status\":\"405\",\"msg\":\"POST only\"}";

	private HttpServer server;
	private final AtomicReference<String> method = new AtomicReference<>();
	private final AtomicReference<String> query = new AtomicReference<>();
	private final AtomicReference<String> body = new AtomicReference<>();
	private int status;
	private String response;

	private InMemorySqlHelper sqlHelper;
	private MessageUtils m;
	private RemoteController controller;
	private AsycPack applied;
	private Remote source;

	@BeforeEach
	void setUp() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/adminPage/remote/getAsycPack", exchange -> {
			method.set(exchange.getRequestMethod());
			query.set(exchange.getRequestURI().getRawQuery());
			body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(status, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		});
		server.start();

		sqlHelper = new InMemorySqlHelper();
		SettingService settingService = new SettingService();
		ReflectUtil.setFieldValue(settingService, "sqlHelper", sqlHelper);
		m = TestMessages.load();
		ReflectUtil.setFieldValue(m, "settingService", settingService);

		controller = new RemoteController();
		ReflectUtil.setFieldValue(controller, "sqlHelper", sqlHelper);
		ReflectUtil.setFieldValue(controller, "m", m);
		ReflectUtil.setFieldValue(controller, "confService", new ConfService() {
			@Override
			public void setAsycPack(AsycPack asycPack) {
				applied = asycPack;
			}
		});

		source = new Remote();
		source.setProtocol("http");
		source.setIp("127.0.0.1");
		source.setPort(server.getAddress().getPort());
		source.setCreditKey(CREDIT_KEY);
		sqlHelper.insertOrUpdate(source);

		Admin admin = new Admin();
		admin.setName("admin");
		ContextHolder.currentSet(new FakeContext("/adminPage/remote/asyc").session("admin", admin));
	}

	@AfterEach
	void tearDown() {
		ContextHolder.currentRemove();
		server.stop(0);
	}

	private JsonResult<?> syncToLocal() {
		return controller.asyc(source.getId(), new String[] { "local" }, new String[] { "basic", "http" });
	}

	private static String decode(String s) {
		return s == null ? "" : URLDecoder.decode(s, StandardCharsets.UTF_8);
	}

	@Test
	void fetchesPackFromRemoteWithPost() {
		status = 200;
		response = PACK;

		JsonResult<?> result = syncToLocal();

		assertTrue(result.isSuccess(), result.getMsg());
		assertEquals("POST", method.get());
		String form = decode(body.get());
		assertTrue(form.contains("creditKey=" + CREDIT_KEY), form);
		assertTrue(form.contains("asycData=basic,http"), form);
		assertFalse(decode(query.get()).contains(CREDIT_KEY), "creditKey 不應放在網址上");
		assertNotNull(applied);
		assertEquals("worker_processes", applied.getBasicList().get(0).getName());
	}

	@Test
	void remoteMethodNotAllowedFailsTheSync() {
		status = 405;
		response = NOT_ALLOWED;

		JsonResult<?> result = syncToLocal();

		assertFalse(result.isSuccess());
		assertEquals(m.get("remoteStr.asycFail"), result.getMsg());
		assertNull(applied, "取不到資料時不得套用");
	}

	@Test
	void remoteErrorJsonFailsTheSync() {
		status = 200;
		response = "{\"success\":false,\"status\":\"401\",\"msg\":\"wrong creditKey\"}";

		JsonResult<?> result = syncToLocal();

		assertFalse(result.isSuccess());
		assertNull(applied);
	}

	@Test
	void emptyPackFailsTheSync() {
		status = 200;
		response = "{}";

		JsonResult<?> result = syncToLocal();

		assertFalse(result.isSuccess());
		assertNull(applied);
	}

	@Test
	void unreachableRemoteFailsTheSync() {
		server.stop(0);
		status = 200;
		response = PACK;

		JsonResult<?> result = syncToLocal();

		assertFalse(result.isSuccess());
		assertNull(applied);
	}
}
