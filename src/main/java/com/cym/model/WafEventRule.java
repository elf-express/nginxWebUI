package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.Table;

/** WafEvent 命中的一條規則（E-394）；alertId 是 CrowdSec alert id，同步時以它去重 */
@Table
public class WafEventRule extends BaseModel {
	String eventId;
	String alertId;
	/** 與所屬事件相同（epoch 毫秒），清理與近期命中數統計用 */
	Long eventTime;
	/** CRS 規則編號（942100）或 vpatch 規則名稱（crowdsecurity/vpatch-env-access） */
	String ruleName;
	/** vpatch 或 crs */
	String ruleSource;
	/** WafCategories 的分類代碼 */
	String category;
	String message;

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getAlertId() {
		return alertId;
	}

	public void setAlertId(String alertId) {
		this.alertId = alertId;
	}

	public Long getEventTime() {
		return eventTime;
	}

	public void setEventTime(Long eventTime) {
		this.eventTime = eventTime;
	}

	public String getRuleName() {
		return ruleName;
	}

	public void setRuleName(String ruleName) {
		this.ruleName = ruleName;
	}

	public String getRuleSource() {
		return ruleSource;
	}

	public void setRuleSource(String ruleSource) {
		this.ruleSource = ruleSource;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
