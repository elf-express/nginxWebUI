package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.Table;

/** WAF 規則調整的設定正本（E-395）：分類或單條規則的處理方式與排除站台 */
@Table
public class WafRuleSetting extends BaseModel {
	/** category：分類；rule：單條規則 */
	String scope;
	/** 分類代碼（vpatch:<產品>、WafCategories 的 CRS 代碼 crs-<類別>）或規則名稱（crowdsecurity/vpatch-xxx、CRS 編號） */
	String ruleKey;
	/** 已知漏洞 block／log／off；CRS count／log／off；單條另可 inherit（跟分類相同） */
	String action;
	/** 排除的站台 id，逗號分隔 */
	String excludedServerIds;

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getRuleKey() {
		return ruleKey;
	}

	public void setRuleKey(String ruleKey) {
		this.ruleKey = ruleKey;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getExcludedServerIds() {
		return excludedServerIds;
	}

	public void setExcludedServerIds(String excludedServerIds) {
		this.excludedServerIds = excludedServerIds;
	}
}
