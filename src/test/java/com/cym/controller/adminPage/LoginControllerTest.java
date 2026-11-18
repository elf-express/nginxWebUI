package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.ContextHolder;

import com.cym.model.Admin;
import com.cym.service.AdminService;
import com.cym.service.CreditService;
import com.cym.service.SettingService;
import com.cym.testsupport.FakeContext;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.testsupport.TestMessages;
import com.cym.utils.AuthUtils;
import com.cym.utils.EncodePassUtils;
import com.cym.utils.JsonResult;
import com.cym.utils.MessageUtils;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/** 登入端點：回應不含密碼雜湊、TOTP 密鑰與 API token；getAuth 不論是否帶 remote 都要驗證碼。 */
class LoginControllerTest {
	private static final String NAME = "admin";
	private static final String PASS = "Admin1234";
	private static final String CAPTCHA = "1234";
	private static final String TOKEN = "api-token-secret";

	private InMemorySqlHelper sqlHelper;
	private SettingService settingService;
	private AuthUtils authUtils;
	private MessageUtils m;
	private LoginController controller;
	private FakeContext ctx;
	private Admin admin;

	@BeforeEach
	void setUp() {
		sqlHelper = new InMemorySqlHelper();
		settingService = new SettingService();
		ReflectUtil.setFieldValue(settingService, "sqlHelper", sqlHelper);
		m = TestMessages.load();
		ReflectUtil.setFieldValue(m, "settingService", settingService);

		AdminService adminService = new AdminService();
		ReflectUtil.setFieldValue(adminService, "sqlHelper", sqlHelper);
		authUtils = new AuthUtils();

		controller = new LoginController();
		ReflectUtil.setFieldValue(controller, "sqlHelper", sqlHelper);
		ReflectUtil.setFieldValue(controller, "adminService", adminService);
		ReflectUtil.setFieldValue(controller, "settingService", settingService);
		ReflectUtil.setFieldValue(controller, "authUtils", authUtils);
		ReflectUtil.setFieldValue(controller, "m", m);
		CreditService creditService = new CreditService();
		ReflectUtil.setFieldValue(creditService, "sqlHelper", sqlHelper);
		ReflectUtil.setFieldValue(controller, "creditService", creditService);

		admin = new Admin();
		admin.setName(NAME);
		admin.setPass(EncodePassUtils.encode(PASS));
		admin.setAuth(true);
		admin.setKey(authUtils.makeKey());
		admin.setApi(true);
		admin.setToken(TOKEN);
		admin.setType(0);
		sqlHelper.insertOrUpdate(admin);

		ctx = new FakeContext("/adminPage/login");
		ContextHolder.currentSet(ctx);
	}

	@AfterEach
	void tearDown() {
		ContextHolder.currentRemove();
	}

	private static String b64(String value) {
		return Base64.encode(Base64.encode(value));
	}

	/** 回應序列化後不得出現任何機密值 */
	private void assertNoSecrets(JsonResult<?> result) {
		String json = JSONUtil.toJsonStr(result);
		Admin stored = sqlHelper.findById(admin.getId(), Admin.class);
		assertFalse(json.contains(stored.getPass()), "回應含密碼雜湊: " + json);
		assertFalse(json.contains(stored.getKey()), "回應含 TOTP 密鑰: " + json);
		assertFalse(json.contains(TOKEN), "回應含 API token: " + json);
		JSONObject obj = JSONUtil.parseObj(json).getJSONObject("obj");
		if (obj != null) {
			assertFalse(obj.containsKey("pass"));
			assertFalse(obj.containsKey("key"));
			assertFalse(obj.containsKey("token"));
		}
	}

	@Test
	void getAuthReturnsOnlyTheAuthFlag() {
		ctx.session("captcha", CAPTCHA);

		JsonResult<?> result = controller.getAuth(b64(NAME), b64(PASS), b64(CAPTCHA), null);

		assertTrue(result.isSuccess());
		assertNoSecrets(result);
		assertEquals(Boolean.TRUE, ((Map<?, ?>) result.getObj()).get("auth"));
		assertEquals(CAPTCHA, ctx.session("captcha"), "成功時保留驗證碼給接著的 login 使用");
	}

	@Test
	void getAuthWithRemoteFlagAndNoCaptchaIsRejected() {
		JsonResult<?> result = controller.getAuth(b64(NAME), b64(PASS), null, 1);

		assertFalse(result.isSuccess());
		assertEquals(m.get("loginStr.backError1"), result.getMsg());
		assertNoSecrets(result);
	}

	@Test
	void getAuthWithRemoteFlagAndSessionCaptchaIsStillRejected() {
		ctx.session("captcha", CAPTCHA);

		JsonResult<?> result = controller.getAuth(b64(NAME), b64(PASS), b64(CAPTCHA), 1);

		assertFalse(result.isSuccess(), "remote 呼叫要比對遠端驗證碼，不是瀏覽器 session 的驗證碼");
		assertEquals(m.get("loginStr.backError1"), result.getMsg());
	}

	@Test
	void getAuthWithRemoteFlagChecksTheRemoteCaptcha() {
		settingService.set("remoteCode", CAPTCHA);

		JsonResult<?> result = controller.getAuth(b64(NAME), b64(PASS), b64(CAPTCHA), 1);

		assertTrue(result.isSuccess());
		assertNoSecrets(result);
		assertEquals(Boolean.TRUE, ((Map<?, ?>) result.getObj()).get("auth"));
		assertEquals(CAPTCHA, settingService.get("remoteCode"), "成功時保留遠端驗證碼給接著的 getCredit 使用");
	}

	@Test
	void getAuthWithWrongRemoteCaptchaDestroysIt() {
		settingService.set("remoteCode", CAPTCHA);

		JsonResult<?> result = controller.getAuth(b64(NAME), b64(PASS), b64("9999"), 1);

		assertFalse(result.isSuccess());
		assertEquals(m.get("loginStr.backError1"), result.getMsg());
		assertNull(settingService.get("remoteCode"));
	}

	@Test
	void getAuthWrongPasswordDestroysTheCaptcha() {
		ctx.session("captcha", CAPTCHA);

		JsonResult<?> first = controller.getAuth(b64(NAME), b64("wrong"), b64(CAPTCHA), null);
		JsonResult<?> second = controller.getAuth(b64(NAME), b64(PASS), b64(CAPTCHA), null);

		assertEquals(m.get("loginStr.backError2"), first.getMsg());
		assertFalse(second.isSuccess(), "同一個驗證碼不可用來重複猜密碼");
		assertEquals(m.get("loginStr.backError1"), second.getMsg());
	}

	@Test
	void getAuthWrongPasswordDestroysTheRemoteCaptcha() {
		settingService.set("remoteCode", CAPTCHA);

		JsonResult<?> first = controller.getAuth(b64(NAME), b64("wrong"), b64(CAPTCHA), 1);

		assertEquals(m.get("loginStr.backError2"), first.getMsg());
		assertNull(settingService.get("remoteCode"));
	}

	@Test
	void getCreditWithoutRemoteCaptchaIsRejectedBeforeCheckingThePassword() {
		JsonResult<?> result = controller.getCredit(b64(NAME), b64("wrong"), b64("9999"), null);

		assertFalse(result.isSuccess());
		assertEquals(m.get("loginStr.backError1"), result.getMsg(), "驗證碼錯誤時不可透露密碼是否正確");
	}

	@Test
	void getCreditWrongPasswordDestroysTheRemoteCaptcha() {
		settingService.set("remoteCode", CAPTCHA);

		JsonResult<?> result = controller.getCredit(b64(NAME), b64("wrong"), b64(CAPTCHA), null);

		assertEquals(m.get("loginStr.backError2"), result.getMsg());
		assertNull(settingService.get("remoteCode"), "同一個遠端驗證碼不可用來重複猜密碼");
	}

	@Test
	void getCreditWrongTotpDestroysTheRemoteCaptcha() {
		settingService.set("remoteCode", CAPTCHA);

		JsonResult<?> result = controller.getCredit(b64(NAME), b64(PASS), b64(CAPTCHA), "abcdef");

		assertEquals(m.get("loginStr.backError6"), result.getMsg());
		assertNull(settingService.get("remoteCode"));
	}

	@Test
	void getCreditWithRemoteCaptchaAndTotpIssuesACreditKey() {
		settingService.set("remoteCode", CAPTCHA);
		String totp = String.valueOf(authUtils.getCode(admin.getKey()));

		JsonResult<?> result = controller.getCredit(b64(NAME), b64(PASS), b64(CAPTCHA), totp);

		assertTrue(result.isSuccess(), result.getMsg());
		assertNotNull(((Map<?, ?>) result.getObj()).get("creditKey"));
		assertNoSecrets(result);
		assertNull(settingService.get("remoteCode"), "用過即銷毀");
	}

	@Test
	void loginResponseHasNoSecrets() {
		ctx.session("captcha", CAPTCHA);
		String totp = String.valueOf(authUtils.getCode(admin.getKey()));

		JsonResult<?> result = controller.submitLogin(b64(NAME), b64(PASS), b64(CAPTCHA), b64(totp));

		assertTrue(result.isSuccess(), result.getMsg());
		assertNoSecrets(result);
		String autoKey = (String) ((Map<?, ?>) result.getObj()).get("autoKey");
		assertNotNull(autoKey);
		assertEquals(sqlHelper.findById(admin.getId(), Admin.class).getAutoKey(), autoKey);
	}

	@Test
	void autoLoginResponseHasNoSecrets() {
		admin.setAutoKey("auto-key");
		sqlHelper.updateById(admin);

		JsonResult<?> result = controller.autoLogin("auto-key");

		assertTrue(result.isSuccess());
		assertNoSecrets(result);
		assertEquals(Boolean.TRUE, ctx.session("isLogin"));
	}
}
