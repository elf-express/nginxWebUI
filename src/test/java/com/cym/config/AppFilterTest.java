package com.cym.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.ext.GeoipDbInfo;
import com.cym.model.Admin;
import com.cym.service.AdminService;
import com.cym.service.CreditService;
import com.cym.service.GeoipService;
import com.cym.service.SettingService;
import com.cym.testsupport.FakeContext;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.testsupport.TestMessages;
import com.cym.utils.MessageUtils;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/**
 * AppFilter 的兩道認證閘門：
 * /api/** 只放行未逾期且有接口權限的 token；/adminPage/** 沒登入時只放行已知的 creditKey。
 */
class AppFilterTest {
	private InMemorySqlHelper sqlHelper;
	private MessageUtils m;
	private CreditService creditService;
	private AppFilter filter;
	private String savedLang;

	@BeforeEach
	void setUp() {
		savedLang = ViewConfig.lang;
		ViewConfig.lang = null;

		sqlHelper = new InMemorySqlHelper();
		SettingService settingService = new SettingService();
		ReflectUtil.setFieldValue(settingService, "sqlHelper", sqlHelper);
		m = TestMessages.load();
		ReflectUtil.setFieldValue(m, "settingService", settingService);

		AdminService adminService = new AdminService();
		ReflectUtil.setFieldValue(adminService, "sqlHelper", sqlHelper);
		creditService = new CreditService();
		ReflectUtil.setFieldValue(creditService, "sqlHelper", sqlHelper);

		VersionConfig versionConfig = new VersionConfig();
		versionConfig.currentVersion = "0.0.0";

		filter = new AppFilter();
		filter.adminService = adminService;
		filter.m = m;
		filter.creditService = creditService;
		filter.projectName = "nginxWebUI";
		filter.versionConfig = versionConfig;
		filter.settingService = settingService;
		filter.geoipService = new GeoipService() {
			@Override
			public List<GeoipDbInfo> getDbInfos() {
				return List.of();
			}
		};
	}

	@AfterEach
	void restoreLang() {
		ViewConfig.lang = savedLang;
	}

	private Admin apiAdmin(String token, long tokenTimeout, boolean api) {
		Admin admin = new Admin();
		admin.setName("api-" + token);
		admin.setApi(api);
		admin.setToken(token);
		admin.setTokenTimeout(tokenTimeout);
		sqlHelper.insertOrUpdate(admin);
		return admin;
	}

	/** 跑一次過濾器，回傳後面的 handler 被呼叫幾次 */
	private int run(FakeContext ctx) throws Throwable {
		AtomicInteger calls = new AtomicInteger();
		filter.doFilter(ctx, c -> calls.incrementAndGet());
		return calls.get();
	}

	private void assertApiRejected(FakeContext ctx) throws Throwable {
		assertEquals(0, run(ctx), "請求不應送到 API handler");
		assertTrue(ctx.getHandled());
		JSONObject body = JSONUtil.parseObj(ctx.outputText());
		assertFalse(body.getBool("success"));
		assertEquals("401", body.getStr("status"));
		assertEquals(m.get("apiStr.wrongToken"), body.getStr("msg"));
	}

	private static final String API_WRITE = "/api/server/insertOrUpdate";
	private static final long FUTURE = System.currentTimeMillis() + 60 * 60 * 1000L;

	@Test
	void apiWithoutTokenIsRejected() throws Throwable {
		apiAdmin("good", FUTURE, true);
		assertApiRejected(new FakeContext(API_WRITE));
	}

	@Test
	void apiWithUnknownTokenIsRejected() throws Throwable {
		apiAdmin("good", FUTURE, true);
		assertApiRejected(new FakeContext(API_WRITE).header("token", "guess"));
	}

	@Test
	void apiWithExpiredTokenIsRejected() throws Throwable {
		apiAdmin("old", System.currentTimeMillis() - 1000, true);
		assertApiRejected(new FakeContext(API_WRITE).header("token", "old"));
	}

	@Test
	void apiWithTokenOfAdminWithoutApiPermissionIsRejected() throws Throwable {
		apiAdmin("noapi", FUTURE, false);
		assertApiRejected(new FakeContext(API_WRITE).header("token", "noapi"));
	}

	@Test
	void apiWithValidTokenReachesHandler() throws Throwable {
		apiAdmin("good", FUTURE, true);
		FakeContext ctx = new FakeContext(API_WRITE).header("token", "good");

		assertEquals(1, run(ctx));
		assertEquals("", ctx.outputText());
	}

	@Test
	void apiPathMatchIsCaseInsensitive() throws Throwable {
		apiAdmin("good", FUTURE, true);
		assertApiRejected(new FakeContext("/API/server/insertOrUpdate"));
	}

	private static final String ADMIN_PAGE = "/adminPage/server";

	private void assertRedirectedToLogin(FakeContext ctx) throws Throwable {
		assertEquals(0, run(ctx), "請求不應送到後台 handler");
		assertTrue(ctx.getHandled());
		assertEquals("/adminPage/login", ctx.redirectUrl());
	}

	@Test
	void adminPageWithoutLoginOrCreditKeyRedirectsToLogin() throws Throwable {
		assertRedirectedToLogin(new FakeContext(ADMIN_PAGE));
	}

	@Test
	void adminPageWithEmptyCreditKeyRedirectsToLogin() throws Throwable {
		creditService.make("1");
		assertRedirectedToLogin(new FakeContext(ADMIN_PAGE).param("creditKey", ""));
	}

	@Test
	void adminPageWithUnknownCreditKeyRedirectsToLogin() throws Throwable {
		creditService.make("1");
		assertRedirectedToLogin(new FakeContext(ADMIN_PAGE).param("creditKey", "guess"));
	}

	@Test
	void adminPageWithKnownCreditKeyReachesHandler() throws Throwable {
		String key = creditService.make("1");
		FakeContext ctx = new FakeContext(ADMIN_PAGE).param("creditKey", key);

		assertEquals(1, run(ctx));
		assertNull(ctx.redirectUrl());
	}

	@Test
	void adminPageWithLoginSessionReachesHandler() throws Throwable {
		FakeContext ctx = new FakeContext(ADMIN_PAGE).session("isLogin", true);

		assertEquals(1, run(ctx));
		assertNull(ctx.redirectUrl());
	}

	@Test
	void loginPageIsReachableWithoutLogin() throws Throwable {
		FakeContext ctx = new FakeContext("/adminPage/login");

		assertEquals(1, run(ctx));
		assertNull(ctx.redirectUrl());
	}
}
