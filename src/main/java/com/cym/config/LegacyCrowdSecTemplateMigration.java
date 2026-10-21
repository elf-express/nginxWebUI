package com.cym.config;

import java.util.List;
import java.util.Set;

import com.cym.model.Param;
import com.cym.model.Template;
import com.cym.sqlhelper.utils.ConditionAndWrapper;
import com.cym.sqlhelper.utils.SqlHelper;

/**
 * 移除舊版預設參數模板「CrowdSec Auth Request」。
 * 它的 auth_request 指向從未存在的 /crowdsec-check，套用後該站點每個請求都會 500。
 * 只刪「內容仍是原樣」的種子模板；使用者改過的保留。
 */
public final class LegacyCrowdSecTemplateMigration {
	private static final List<String> LEGACY_NAMES = List.of(
			"CrowdSec Auth Request (CrowdSec 認證請求)",
			"CrowdSec Auth Request");

	private static final Set<String> SEED_PARAMS = Set.of(
			"auth_request=/crowdsec-check",
			"auth_request_set=$auth_status $upstream_status");

	private LegacyCrowdSecTemplateMigration() {
	}

	/**
	 * 刪除原樣的舊模板、它的 params，以及 server/location 上引用它的 Param。
	 *
	 * @return 移除的模板數
	 */
	public static int run(SqlHelper sqlHelper) {
		int removed = 0;
		for (String name : LEGACY_NAMES) {
			List<Template> templates = sqlHelper.findListByQuery(new ConditionAndWrapper().eq("name", name),
					Template.class);
			for (Template template : templates) {
				if (!isUntouchedSeed(sqlHelper, template)) {
					continue;
				}
				sqlHelper.deleteByQuery(new ConditionAndWrapper().eq("templateId", template.getId()), Param.class);
				sqlHelper.deleteByQuery(new ConditionAndWrapper().eq("templateValue", template.getId()), Param.class);
				sqlHelper.deleteById(template.getId(), Template.class);
				removed++;
			}
		}
		return removed;
	}

	private static boolean isUntouchedSeed(SqlHelper sqlHelper, Template template) {
		List<Param> params = sqlHelper.findListByQuery(new ConditionAndWrapper().eq("templateId", template.getId()),
				Param.class);
		if (params.size() != SEED_PARAMS.size()) {
			return false;
		}
		for (Param param : params) {
			if (!SEED_PARAMS.contains(param.getName() + "=" + param.getValue())) {
				return false;
			}
		}
		return true;
	}
}
