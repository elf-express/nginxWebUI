package com.cym.controller.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.model.Admin;
import com.cym.service.AdminService;
import com.cym.service.SettingService;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.testsupport.TestMessages;
import com.cym.utils.EncodePassUtils;
import com.cym.utils.JsonResult;
import com.cym.utils.MessageUtils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;

/** 取得 API token：帳密錯誤或沒有接口權限都不發 token，有權限才發。 */
class TokenControllerTest {
	private InMemorySqlHelper sqlHelper;
	private AdminService adminService;
	private MessageUtils m;
	private TokenController controller;

	/** 與正式 SqlHelper 相同，updateById 只寫入非 null 欄位（makeToken 依賴這個行為）。 */
	static class PartialUpdateSqlHelper extends InMemorySqlHelper {
		@Override
		public void updateById(Object object) {
			Object existing = findById((String) ReflectUtil.getFieldValue(object, "id"), object.getClass());
			if (existing == null) {
				return;
			}
			BeanUtil.copyProperties(object, existing, CopyOptions.create().ignoreNullValue());
		}
	}

	@BeforeEach
	void setUp() {
		sqlHelper = new PartialUpdateSqlHelper();
		SettingService settingService = new SettingService();
		ReflectUtil.setFieldValue(settingService, "sqlHelper", sqlHelper);
		m = TestMessages.load();
		ReflectUtil.setFieldValue(m, "settingService", settingService);

		adminService = new AdminService();
		ReflectUtil.setFieldValue(adminService, "sqlHelper", sqlHelper);

		controller = new TokenController();
		ReflectUtil.setFieldValue(controller, "adminService", adminService);
		ReflectUtil.setFieldValue(controller, "m", m);
	}

	private Admin admin(String name, String pass, boolean api) {
		Admin admin = new Admin();
		admin.setName(name);
		admin.setPass(EncodePassUtils.encode(pass));
		admin.setApi(api);
		sqlHelper.insertOrUpdate(admin);
		return admin;
	}

	@Test
	void wrongPasswordGetsNoToken() {
		admin("api", "secret", true);

		JsonResult result = controller.getToken("api", "wrong");

		assertFalse(result.isSuccess());
		assertEquals(m.get("loginStr.backError2"), result.getMsg());
		assertNull(sqlHelper.findAll(Admin.class).get(0).getToken());
	}

	@Test
	void adminWithoutApiPermissionGetsNoToken() {
		admin("noapi", "secret", false);

		JsonResult result = controller.getToken("noapi", "secret");

		assertFalse(result.isSuccess());
		assertEquals(m.get("loginStr.notPermission"), result.getMsg());
		assertNull(sqlHelper.findAll(Admin.class).get(0).getToken());
	}

	@Test
	@SuppressWarnings("unchecked")
	void adminWithApiPermissionGetsWorkingToken() {
		Admin admin = admin("api", "secret", true);
		long before = System.currentTimeMillis();

		JsonResult result = controller.getToken("api", "secret");

		assertTrue(result.isSuccess());
		Map<String, Object> obj = (Map<String, Object>) result.getObj();
		String token = (String) obj.get("token");
		assertNotNull(token);
		assertTrue((Long) obj.get("tokenTimeout") > before);

		Admin byToken = adminService.getByToken(token);
		assertNotNull(byToken);
		assertEquals(admin.getId(), byToken.getId());
		assertTrue(byToken.getApi());
	}

	@Test
	void expiredTokenIsNotFound() {
		Admin admin = admin("api", "secret", true);
		admin.setToken("old-token");
		admin.setTokenTimeout(System.currentTimeMillis() - 1000);

		assertNull(adminService.getByToken("old-token"));
		assertNull(adminService.getByToken(null));
	}
}
