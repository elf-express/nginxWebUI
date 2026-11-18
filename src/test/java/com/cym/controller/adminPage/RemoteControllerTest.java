package com.cym.controller.adminPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.noear.solon.core.handle.ContextHolder;

import com.cym.config.VersionConfig;
import com.cym.model.Admin;
import com.cym.model.AdminGroup;
import com.cym.model.Group;
import com.cym.model.Remote;
import com.cym.service.AdminService;
import com.cym.service.GroupService;
import com.cym.service.RemoteService;
import com.cym.service.SettingService;
import com.cym.testsupport.FakeContext;
import com.cym.testsupport.InMemorySqlHelper;
import com.cym.testsupport.TestMessages;
import com.cym.utils.JsonResult;
import com.cym.utils.MessageUtils;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

/** 遠端節點的群組隔離：受限帳號只看得到、只能操作自己群組內的節點，且任何回應都不含連線憑證。 */
class RemoteControllerTest {
	private static final String PASS_A = "pass-node-a";
	private static final String KEY_A = "credit-key-node-a";
	private static final String PASS_B = "pass-node-b";
	private static final String KEY_B = "credit-key-node-b";
	private static final String PASS_FREE = "pass-node-free";
	private static final String KEY_FREE = "credit-key-node-free";

	private InMemorySqlHelper sqlHelper;
	private RemoteController controller;
	private FakeContext ctx;
	private Admin superAdmin;
	private Admin limited;
	private Group groupA;
	private Group groupB;
	private Remote nodeA;
	private Remote nodeB;
	private Remote nodeFree;

	@BeforeEach
	void setUp() {
		sqlHelper = new InMemorySqlHelper();
		SettingService settingService = new SettingService();
		ReflectUtil.setFieldValue(settingService, "sqlHelper", sqlHelper);
		MessageUtils m = TestMessages.load();
		ReflectUtil.setFieldValue(m, "settingService", settingService);

		AdminService adminService = new AdminService();
		ReflectUtil.setFieldValue(adminService, "sqlHelper", sqlHelper);
		RemoteService remoteService = new RemoteService();
		ReflectUtil.setFieldValue(remoteService, "sqlHelper", sqlHelper);
		ReflectUtil.setFieldValue(remoteService, "adminService", adminService);
		GroupService groupService = new GroupService();
		ReflectUtil.setFieldValue(groupService, "sqlHelper", sqlHelper);
		VersionConfig versionConfig = new VersionConfig();
		versionConfig.currentVersion = "test";

		controller = new RemoteController();
		ReflectUtil.setFieldValue(controller, "sqlHelper", sqlHelper);
		ReflectUtil.setFieldValue(controller, "m", m);
		ReflectUtil.setFieldValue(controller, "adminService", adminService);
		ReflectUtil.setFieldValue(controller, "remoteService", remoteService);
		ReflectUtil.setFieldValue(controller, "groupService", groupService);
		ReflectUtil.setFieldValue(controller, "settingService", settingService);
		ReflectUtil.setFieldValue(controller, "versionConfig", versionConfig);
		ReflectUtil.setFieldValue(controller, "port", 8080);

		groupA = group("A");
		groupB = group("B");
		nodeA = remote("node-a", groupA.getId(), PASS_A, KEY_A);
		nodeB = remote("node-b", groupB.getId(), PASS_B, KEY_B);
		nodeFree = remote("node-free", null, PASS_FREE, KEY_FREE);

		superAdmin = admin("root", 0);
		limited = admin("limited", 1);
		AdminGroup adminGroup = new AdminGroup();
		adminGroup.setAdminId(limited.getId());
		adminGroup.setGroupId(groupA.getId());
		sqlHelper.insertOrUpdate(adminGroup);

		ctx = new FakeContext("/adminPage/remote");
		ctx.session("localType", "local");
		ContextHolder.currentSet(ctx);
	}

	@AfterEach
	void tearDown() {
		ContextHolder.currentRemove();
	}

	private Group group(String name) {
		Group group = new Group();
		group.setName(name);
		sqlHelper.insertOrUpdate(group);
		return group;
	}

	/** 位址指向不會回應的本機埠，allTable 探測版本時立即失敗 */
	private Remote remote(String descr, String parentId, String pass, String creditKey) {
		Remote remote = new Remote();
		remote.setProtocol("http");
		remote.setIp("127.0.0.1");
		remote.setPort(1);
		remote.setDescr(descr);
		remote.setName("remote-admin");
		remote.setPass(pass);
		remote.setCreditKey(creditKey);
		remote.setParentId(parentId);
		remote.setMonitor(0);
		sqlHelper.insertOrUpdate(remote);
		return remote;
	}

	private Admin admin(String name, int type) {
		Admin admin = new Admin();
		admin.setName(name);
		admin.setType(type);
		sqlHelper.insertOrUpdate(admin);
		return admin;
	}

	private void loginAs(Admin admin) {
		ctx.session("admin", admin);
	}

	/** 序列化後的回應不得出現任何節點的密碼、creditKey，也不得有這兩個欄位 */
	private static void assertNoCredentials(Object response) {
		String json = JSONUtil.toJsonStr(response);
		for (String secret : new String[] { PASS_A, KEY_A, PASS_B, KEY_B, PASS_FREE, KEY_FREE }) {
			assertFalse(json.contains(secret), "回應含連線憑證: " + json);
		}
		assertFalse(json.contains("\"pass\""), "回應含 pass 欄位: " + json);
		assertFalse(json.contains("\"creditKey\""), "回應含 creditKey 欄位: " + json);
	}

	private List<String> allTableIds() {
		Map<String, Object> table = controller.allTable(ctx);
		assertNoCredentials(table);
		JSONArray data = JSONUtil.parseObj(JSONUtil.toJsonStr(table)).getJSONArray("data");
		List<String> ids = new ArrayList<>();
		for (Object row : data) {
			ids.add(((JSONObject) row).getStr("id"));
		}
		return ids;
	}

	private static String treeJson(JsonResult<?> result) {
		assertTrue(result.isSuccess());
		return JSONUtil.toJsonStr(result.getObj());
	}

	// ───────────── 受限帳號 ─────────────

	@Test
	void allTableListsOnlyNodesInTheLimitedAdminsGroups() {
		loginAs(limited);

		List<String> ids = allTableIds();

		assertTrue(ids.contains(nodeA.getId()));
		assertTrue(ids.contains("local"));
		assertTrue(ids.contains(groupA.getId()));
		assertFalse(ids.contains(nodeB.getId()), "清單含其他群組的節點");
		assertFalse(ids.contains(nodeFree.getId()), "清單含未分組的節點");
		assertFalse(ids.contains(groupB.getId()));
	}

	@Test
	void detailOfOwnGroupNodeHasNoCredentials() {
		loginAs(limited);

		JsonResult<?> result = controller.detail(nodeA.getId());

		assertTrue(result.isSuccess());
		assertNoCredentials(result);
		JSONObject obj = JSONUtil.parseObj(JSONUtil.toJsonStr(result.getObj()));
		assertEquals(nodeA.getId(), obj.getStr("id"));
		assertEquals("remote-admin", obj.getStr("name"));
		assertEquals(groupA.getId(), obj.getStr("parentId"));
	}

	@Test
	void detailOfOtherGroupNodeIsRejected() {
		loginAs(limited);

		JsonResult<?> result = controller.detail(nodeB.getId());

		assertFalse(result.isSuccess());
		assertNull(result.getObj());
		assertNoCredentials(result);
	}

	@Test
	void detailOfUngroupedNodeIsRejected() {
		loginAs(limited);

		assertFalse(controller.detail(nodeFree.getId()).isSuccess());
	}

	@Test
	void changeToOtherGroupNodeIsRejectedAndSessionUntouched() {
		loginAs(limited);

		JsonResult<?> result = controller.change(nodeB.getId(), ctx);

		assertFalse(result.isSuccess());
		assertEquals("local", ctx.session("localType"));
		assertNull(ctx.session("remote"));
	}

	@Test
	void changeToOwnGroupNodeSwitchesSession() {
		loginAs(limited);

		JsonResult<?> result = controller.change(nodeA.getId(), ctx);

		assertTrue(result.isSuccess());
		assertNoCredentials(result);
		assertEquals("remote", ctx.session("localType"));
		assertEquals(nodeA.getId(), ((Remote) ctx.session("remote")).getId());
	}

	@Test
	void changeBackToLocalIsAllowed() {
		loginAs(limited);
		ctx.session("localType", "remote");
		ctx.session("remote", nodeA);

		assertTrue(controller.change("local", ctx).isSuccess());

		assertEquals("local", ctx.session("localType"));
		assertNull(ctx.session("remote"));
	}

	@Test
	void deleteOtherGroupNodeIsRejected() {
		loginAs(limited);

		assertFalse(controller.del(nodeB.getId()).isSuccess());

		assertNotNull(sqlHelper.findById(nodeB.getId(), Remote.class));
	}

	@Test
	void contentOfOtherGroupNodeIsRejected() {
		loginAs(limited);

		assertFalse(controller.content(nodeB.getId()).isSuccess());
	}

	@Test
	void setMonitorOnOtherGroupNodeIsRejected() {
		loginAs(limited);

		assertFalse(controller.setMonitor(nodeB.getId(), 1).isSuccess());

		assertEquals(0, sqlHelper.findById(nodeB.getId(), Remote.class).getMonitor());
	}

	@Test
	void addOverCannotOverwriteOtherGroupNode() {
		loginAs(limited);
		Remote edit = new Remote();
		edit.setId(nodeB.getId());
		edit.setIp("10.0.0.9");
		edit.setPort(8080);
		edit.setProtocol("http");
		edit.setParentId(groupA.getId());

		JsonResult<?> result = controller.addOver(edit, "", "");

		assertFalse(result.isSuccess());
		assertEquals(m().get("remoteStr.noPermission"), result.getMsg());
		Remote stored = sqlHelper.findById(nodeB.getId(), Remote.class);
		assertSame(nodeB, stored);
		assertEquals("127.0.0.1", stored.getIp());
	}

	@Test
	void addOverCannotPlaceNodeInOtherGroup() {
		loginAs(limited);
		Remote edit = new Remote();
		edit.setIp("10.0.0.10");
		edit.setPort(8080);
		edit.setProtocol("http");
		edit.setParentId(groupB.getId());

		JsonResult<?> result = controller.addOver(edit, "", "");

		assertFalse(result.isSuccess());
		assertEquals(m().get("remoteStr.noPermission"), result.getMsg());
	}

	@Test
	void cmdOverOnOtherGroupNodeIsRejected() {
		loginAs(limited);

		JsonResult<?> result = controller.cmdOver(new String[] { nodeB.getId() }, "check", null);

		assertFalse(result.isSuccess());
	}

	@Test
	void asycFromOrToOtherGroupNodeIsRejected() {
		loginAs(limited);

		assertFalse(controller.asyc(nodeB.getId(), new String[] { nodeA.getId() }, new String[] { "http" }).isSuccess());
		assertFalse(controller.asyc(nodeA.getId(), new String[] { nodeB.getId() }, new String[] { "http" }).isSuccess());
	}

	@Test
	void cmdTreeListsOnlyNodesInTheLimitedAdminsGroups() {
		loginAs(limited);

		String json = treeJson(controller.getCmdRemote());

		assertTrue(json.contains("node-a"));
		assertFalse(json.contains("node-b"), "指令清單含其他群組的節點: " + json);
		assertFalse(json.contains("node-free"), "指令清單含未分組的節點: " + json);
	}

	// ───────────── 群組端點（E-183） ─────────────

	private Group renamed(Group source, String name) {
		Group edit = new Group();
		edit.setId(source.getId());
		edit.setName(name);
		return edit;
	}

	@Test
	void groupDetailOfOwnGroupIsAllowed() {
		loginAs(limited);

		JsonResult<?> result = controller.groupDetail(groupA.getId());

		assertTrue(result.isSuccess());
		assertEquals(groupA.getId(), ((Group) result.getObj()).getId());
	}

	@Test
	void groupDetailOfOtherGroupIsRejected() {
		loginAs(limited);

		JsonResult<?> result = controller.groupDetail(groupB.getId());

		assertFalse(result.isSuccess());
		assertEquals(m().get("remoteStr.noPermission"), result.getMsg());
		assertNull(result.getObj());
	}

	@Test
	void limitedAdminCannotRenameOtherGroup() {
		loginAs(limited);

		JsonResult<?> result = controller.addGroupOver(renamed(groupB, "hijacked"));

		assertFalse(result.isSuccess());
		assertEquals(m().get("remoteStr.noPermission"), result.getMsg());
		assertEquals("B", sqlHelper.findById(groupB.getId(), Group.class).getName());
	}

	@Test
	void limitedAdminCannotRenameOwnGroup() {
		loginAs(limited);

		assertFalse(controller.addGroupOver(renamed(groupA, "renamed")).isSuccess());

		assertEquals("A", sqlHelper.findById(groupA.getId(), Group.class).getName());
	}

	@Test
	void limitedAdminCannotCreateGroup() {
		loginAs(limited);
		Group created = new Group();
		created.setName("new");

		assertFalse(controller.addGroupOver(created).isSuccess());

		assertEquals(2, sqlHelper.findAll(Group.class).size());
	}

	@Test
	void limitedAdminCannotDeleteOtherGroup() {
		loginAs(limited);

		JsonResult<?> result = controller.delGroup(groupB.getId());

		assertFalse(result.isSuccess());
		assertEquals(m().get("remoteStr.noPermission"), result.getMsg());
		assertNotNull(sqlHelper.findById(groupB.getId(), Group.class));
		assertEquals(groupB.getId(), sqlHelper.findById(nodeB.getId(), Remote.class).getParentId());
	}

	@Test
	void limitedAdminCannotDeleteOwnGroup() {
		loginAs(limited);

		assertFalse(controller.delGroup(groupA.getId()).isSuccess());

		assertNotNull(sqlHelper.findById(groupA.getId(), Group.class));
		assertEquals(groupA.getId(), sqlHelper.findById(nodeA.getId(), Remote.class).getParentId());
	}

	@Test
	void noAdminInSessionCannotUseGroupEndpoints() {
		assertFalse(controller.groupDetail(groupA.getId()).isSuccess());
		assertFalse(controller.addGroupOver(renamed(groupA, "renamed")).isSuccess());
		assertFalse(controller.delGroup(groupA.getId()).isSuccess());

		assertEquals("A", sqlHelper.findById(groupA.getId(), Group.class).getName());
	}

	@Test
	void noAdminInSessionSeesNoNodes() {
		assertFalse(controller.detail(nodeA.getId()).isSuccess());
		assertFalse(controller.change(nodeA.getId(), ctx).isSuccess());
		assertEquals("local", ctx.session("localType"));
	}

	// ───────────── 超級管理員行為不變 ─────────────

	@Test
	void superAdminStillSeesEveryNodeWithoutCredentials() {
		loginAs(superAdmin);

		List<String> ids = allTableIds();

		assertTrue(ids.contains(nodeA.getId()));
		assertTrue(ids.contains(nodeB.getId()));
		assertTrue(ids.contains(nodeFree.getId()));
		assertTrue(ids.contains(groupA.getId()));
		assertTrue(ids.contains(groupB.getId()));
	}

	@Test
	void superAdminCanReadAndSwitchToAnyNode() {
		loginAs(superAdmin);

		JsonResult<?> detail = controller.detail(nodeB.getId());
		assertTrue(detail.isSuccess());
		assertNoCredentials(detail);

		assertTrue(controller.change(nodeFree.getId(), ctx).isSuccess());
		assertEquals("remote", ctx.session("localType"));
		Remote inSession = (Remote) ctx.session("remote");
		assertEquals(KEY_FREE, inSession.getCreditKey(), "session 仍保留完整節點供轉發使用");
	}

	@Test
	void superAdminCmdTreeStillListsEveryNode() {
		loginAs(superAdmin);

		String json = treeJson(controller.getCmdRemote());

		assertTrue(json.contains("node-a"));
		assertTrue(json.contains("node-b"));
		assertTrue(json.contains("node-free"));
	}

	@Test
	void superAdminCanDeleteAnyNode() {
		loginAs(superAdmin);

		assertTrue(controller.del(nodeB.getId()).isSuccess());

		assertNull(sqlHelper.findById(nodeB.getId(), Remote.class));
	}

	@Test
	void superAdminCanReadCreateRenameAndDeleteAnyGroup() {
		loginAs(superAdmin);

		JsonResult<?> detail = controller.groupDetail(groupB.getId());
		assertTrue(detail.isSuccess());
		assertEquals(groupB.getId(), ((Group) detail.getObj()).getId());

		Group created = new Group();
		created.setName("C");
		assertTrue(controller.addGroupOver(created).isSuccess());
		assertEquals(3, sqlHelper.findAll(Group.class).size());

		assertTrue(controller.addGroupOver(renamed(groupB, "B2")).isSuccess());
		assertEquals("B2", sqlHelper.findById(groupB.getId(), Group.class).getName());

		assertTrue(controller.delGroup(groupB.getId()).isSuccess());
		assertNull(sqlHelper.findById(groupB.getId(), Group.class));
		assertNull(sqlHelper.findById(nodeB.getId(), Remote.class).getParentId());
	}

	private MessageUtils m() {
		return (MessageUtils) ReflectUtil.getFieldValue(controller, "m");
	}
}
