package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.cym.service.AsnBlockService;
import com.cym.service.CrowdSecClient;
import com.cym.utils.JsonResult;

import cn.hutool.core.util.ReflectUtil;

/** ASN 推送／撤銷遇到管理憑證無效時回明確錯誤（不是籠統的 crowdsec_error）。 */
class AsnControllerTest {

	private AsnController controller(AsnBlockService service) {
		AsnController controller = new AsnController();
		ReflectUtil.setFieldValue(controller, "asnBlockService", service);
		return controller;
	}

	@Test
	void pushIntentReportsInvalidMachineCredentials() {
		AsnController controller = controller(new AsnBlockService() {
			@Override
			public void pushIntent(String intentId) {
				throw new CrowdSecClient.MachineAuthException();
			}
		});

		JsonResult result = controller.pushIntent("1");

		assertFalse(result.isSuccess());
		assertEquals(CrowdSecClient.MachineAuthException.CODE, result.getMsg());
	}

	@Test
	void revokeIntentReportsInvalidMachineCredentials() {
		AsnController controller = controller(new AsnBlockService() {
			@Override
			public boolean isCrowdSecConfigured() {
				return true;
			}

			@Override
			public int revokeByReasonTag(String reasonTag) {
				throw new CrowdSecClient.MachineAuthException();
			}
		});

		JsonResult result = controller.revokeIntent(null, "nginxwebui:as-ban:AS64500");

		assertFalse(result.isSuccess());
		assertEquals(CrowdSecClient.MachineAuthException.CODE, result.getMsg());
	}

	@Test
	void setProfileToLightReportsInvalidMachineCredentials() {
		AsnController controller = controller(new AsnBlockService() {
			@Override
			public String getProfile() {
				return AsnBlockService.PROFILE_MANUAL;
			}

			@Override
			public boolean isCrowdSecConfigured() {
				return true;
			}

			@Override
			public int revokeAllWebuiAsnBans() {
				throw new CrowdSecClient.MachineAuthException();
			}

			@Override
			public void setProfile(String profile) {
				fail("profile must stay unchanged when revoke fails");
			}
		});

		JsonResult result = controller.setProfile(AsnBlockService.PROFILE_LIGHT, "revoke");

		assertFalse(result.isSuccess());
		assertEquals(CrowdSecClient.MachineAuthException.CODE, result.getMsg());
	}
}
