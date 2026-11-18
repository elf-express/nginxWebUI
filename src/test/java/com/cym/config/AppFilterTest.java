package com.cym.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.controller.adminPage.AdminController;
import com.cym.ext.GeoipDbInfo;
import com.cym.model.Admin;
import com.cym.service.AdminService;
import com.cym.service.CreditService;
import com.cym.service.GeoipService;
import com.cym.service.SettingService;
import com.cym.testsupport.FakeContext;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.testsupport.TestMessages;
import com.cym.utils.JsonResult;
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

	// ───────────── 管理員管理頁只給超管（E-64） ─────────────

	private static final String ORIGINAL_HASH = "original-hash";
	private static final String MAIL_PASS = "mail-secret";

	private Admin storedAdmin(String name, int type) {
		Admin admin = new Admin();
		admin.setName(name);
		admin.setType(type);
		admin.setPass(ORIGINAL_HASH);
		admin.setAuth(false);
		admin.setApi(false);
		sqlHelper.insertOrUpdate(admin);
		return admin;
	}

	/** 已登入的請求；session 裡的 admin 是另一個物件，避免測試改到資料庫那一筆 */
	private FakeContext loggedIn(String path, Admin admin) {
		Admin sessionAdmin = new Admin();
		sessionAdmin.setId(admin.getId());
		sessionAdmin.setName(admin.getName());
		sessionAdmin.setType(admin.getType());
		return new FakeContext(path).session("isLogin", true).session("admin", sessionAdmin);
	}

	private AdminController adminController() {
		SettingService settingService = filter.settingService;
		AdminController controller = new AdminController();
		ReflectUtil.setFieldValue(controller, "sqlHelper", sqlHelper);
		ReflectUtil.setFieldValue(controller, "adminService", filter.adminService);
		ReflectUtil.setFieldValue(controller, "m", m);
		ReflectUtil.setFieldValue(controller, "settingService", settingService);
		return controller;
	}

	/** 過濾器放行後由真的 AdminController 處理，回應寫回 ctx，回傳 handler 被呼叫幾次 */
	private int runAdminAction(FakeContext ctx, String action, Admin target) throws Throwable {
		AdminController controller = adminController();
		AtomicInteger calls = new AtomicInteger();
		filter.doFilter(ctx, c -> {
			calls.incrementAndGet();
			JsonResult result = switch (action) {
			case "addOver" -> controller.addOver(target.getId(), target.getName(), false, 0, null);
			case "changePassOver" -> controller.changePassOver(target.getId(), "hacked", false);
			case "del" -> controller.del(target.getId());
			case "getMailSetting" -> controller.getMailSetting();
			default -> throw new IllegalArgumentException(action);
			};
			c.output(JSONUtil.toJsonStr(result));
		});
		return calls.get();
	}

	private static final String[] ADMIN_ACTIONS = { "addOver", "changePassOver", "del", "getMailSetting" };

	private void assertNotPermission(FakeContext ctx) {
		assertTrue(ctx.getHandled());
		JSONObject body = JSONUtil.parseObj(ctx.outputText());
		assertFalse(body.getBool("success"));
		assertEquals("401", body.getStr("status"));
		assertEquals(m.get("loginStr.notPermission"), body.getStr("msg"));
	}

	@Test
	void restrictedAdminCannotUseAdminActionsEvenWithShowAdmin() throws Throwable {
		filter.settingService.set("mail_pass", MAIL_PASS);

		for (String showAdmin : new String[] { null, "true" }) {
			for (String action : ADMIN_ACTIONS) {
				String label = action + " showAdmin=" + showAdmin;
				Admin superAdmin = storedAdmin("root-" + label, 0);
				Admin restricted = storedAdmin("limited-" + label, 1);
				// 受限帳號把自己升為超管；其餘動作都對超管下手
				Admin target = "addOver".equals(action) ? restricted : superAdmin;

				FakeContext ctx = loggedIn("/adminPage/admin/" + action, restricted);
				if (showAdmin != null) {
					ctx.param("showAdmin", showAdmin);
				}

				assertEquals(0, runAdminAction(ctx, action, target), label + "：請求不應送到 AdminController");
				assertNotPermission(ctx);
				assertFalse(ctx.outputText().contains(MAIL_PASS), label + "：不應回傳郵件密碼");

				Admin storedTarget = sqlHelper.findById(target.getId(), Admin.class);
				assertNotNull(storedTarget, label + "：帳號不應被刪除");
				assertEquals(ORIGINAL_HASH, storedTarget.getPass(), label + "：密碼不應被改");
				assertEquals(target.getType(), storedTarget.getType(), label + "：角色不應被改");
			}
		}
	}

	@Test
	void restrictedAdminIsRejectedRegardlessOfPathCase() throws Throwable {
		Admin restricted = storedAdmin("limited", 1);
		Admin superAdmin = storedAdmin("root", 0);
		FakeContext ctx = loggedIn("/ADMINPAGE/ADMIN/del", restricted).param("showAdmin", "true");

		assertEquals(0, runAdminAction(ctx, "del", superAdmin));
		assertNotPermission(ctx);
		assertNotNull(sqlHelper.findById(superAdmin.getId(), Admin.class));
	}

	@Test
	void creditKeyWithoutSessionAdminCannotUseAdminActions() throws Throwable {
		Admin superAdmin = storedAdmin("root", 0);
		String key = creditService.make(superAdmin.getId());
		FakeContext ctx = new FakeContext("/adminPage/admin/del").param("creditKey", key).param("showAdmin", "true");

		assertEquals(0, runAdminAction(ctx, "del", superAdmin));
		assertNotPermission(ctx);
		assertNotNull(sqlHelper.findById(superAdmin.getId(), Admin.class));
	}

	@Test
	void superAdminCanUseAdminActions() throws Throwable {
		filter.settingService.set("mail_pass", MAIL_PASS);

		for (String showAdmin : new String[] { null, "true" }) {
			for (String action : ADMIN_ACTIONS) {
				String label = action + " showAdmin=" + showAdmin;
				Admin superAdmin = storedAdmin("root-" + label, 0);
				Admin other = storedAdmin("other-" + label, 1);

				FakeContext ctx = loggedIn("/adminPage/admin/" + action, superAdmin);
				if (showAdmin != null) {
					ctx.param("showAdmin", showAdmin);
				}

				assertEquals(1, runAdminAction(ctx, action, other), label + "：超管請求應送到 AdminController");
				JSONObject body = JSONUtil.parseObj(ctx.outputText());
				assertTrue(body.getBool("success"), label);

				Admin storedOther = sqlHelper.findById(other.getId(), Admin.class);
				switch (action) {
				case "addOver" -> assertEquals(0, storedOther.getType(), label);
				case "changePassOver" -> assertNotEquals(ORIGINAL_HASH, storedOther.getPass(), label);
				case "del" -> assertNull(storedOther, label);
				default -> assertEquals(MAIL_PASS, body.getJSONObject("obj").getStr("mail_pass"), label);
				}
			}
		}
	}

	@Test
	void restrictedAdminStillReachesOtherAdminPages() throws Throwable {
		Admin restricted = storedAdmin("limited", 1);
		FakeContext ctx = loggedIn(ADMIN_PAGE, restricted);

		assertEquals(1, run(ctx));
		assertEquals("", ctx.outputText());
	}

	@Test
	void showAdminParamIsStillPassedToTheMenu() throws Throwable {
		Admin restricted = storedAdmin("limited", 1);
		FakeContext ctx = loggedIn(ADMIN_PAGE, restricted).param("showAdmin", "true");

		run(ctx);
		assertEquals("true", ctx.attr("showAdmin"));
	}
}
