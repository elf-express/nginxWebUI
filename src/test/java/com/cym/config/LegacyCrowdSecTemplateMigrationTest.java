package com.cym.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.cym.model.Param;
import com.cym.model.Template;
import com.cym.testsupport.InMemorySqlHelper;

/** 舊版「CrowdSec Auth Request」模板（指向不存在的 /crowdsec-check）遷移移除。 */
class LegacyCrowdSecTemplateMigrationTest {
	private static final String CN_NAME = "CrowdSec Auth Request (CrowdSec 認證請求)";
	private static final String EN_NAME = "CrowdSec Auth Request";

	private InMemorySqlHelper sql;

	@BeforeEach
	void setUp() {
		sql = new InMemorySqlHelper();
	}

	private String addTemplate(String name, String authRequestValue) {
		Template template = new Template();
		template.setName(name);
		template.setDef("");
		template.setGroupName("crowdsec");
		String id = sql.insertOrUpdate(template);
		addTemplateParam(id, "auth_request", authRequestValue);
		addTemplateParam(id, "auth_request_set", "$auth_status $upstream_status");
		return id;
	}

	private void addTemplateParam(String templateId, String name, String value) {
		Param param = new Param();
		param.setTemplateId(templateId);
		param.setName(name);
		param.setValue(value);
		sql.insertOrUpdate(param);
	}

	private void addReference(String serverId, String templateId) {
		Param param = new Param();
		param.setServerId(serverId);
		param.setTemplateValue(templateId);
		param.setTemplateName("whatever");
		sql.insertOrUpdate(param);
	}

	@Test
	void removesSeedTemplate() {
		String id = addTemplate(CN_NAME, "/crowdsec-check");
		addReference("server-1", id);
		Template other = new Template();
		other.setName("Other");
		String otherId = sql.insertOrUpdate(other);
		addTemplateParam(otherId, "add_header", "X-A 1");

		assertEquals(1, LegacyCrowdSecTemplateMigration.run(sql));

		assertEquals(1L, sql.findAllCount(Template.class));
		// 只剩別的模板的那一筆 params，模板自己的 params 與 server 的引用都被刪
		assertEquals(1L, sql.findAllCount(Param.class));
	}

	@Test
	void keepsEditedTemplate() {
		String id = addTemplate(CN_NAME, "/my-own-check");
		addReference("server-1", id);

		assertEquals(0, LegacyCrowdSecTemplateMigration.run(sql));

		assertEquals(1L, sql.findAllCount(Template.class));
		assertEquals(3L, sql.findAllCount(Param.class));
	}

	@Test
	void idempotent() {
		String id = addTemplate(CN_NAME, "/crowdsec-check");
		addReference("server-1", id);

		assertEquals(1, LegacyCrowdSecTemplateMigration.run(sql));
		assertEquals(0, LegacyCrowdSecTemplateMigration.run(sql));
	}

	@Test
	void legacyEnglishNameAlsoRemoved() {
		String id = addTemplate(EN_NAME, "/crowdsec-check");
		addReference("server-1", id);

		assertEquals(1, LegacyCrowdSecTemplateMigration.run(sql));

		assertEquals(0L, sql.findAllCount(Template.class));
		assertEquals(0L, sql.findAllCount(Param.class));
	}

	@Test
	void keepsTemplateWithExtraParam() {
		String id = addTemplate(CN_NAME, "/crowdsec-check");
		addTemplateParam(id, "add_header", "X-Extra 1");

		assertEquals(0, LegacyCrowdSecTemplateMigration.run(sql));

		assertEquals(1L, sql.findAllCount(Template.class));
	}
}
