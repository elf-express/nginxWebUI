package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.Table;

/** 一個被 WAF 命中的請求（E-394）。同一個請求的 vpatch 與 CRS alert 併成一列，命中的規則在 WafEventRule */
@Table
public class WafEvent extends BaseModel {
	/** 合併用：IP|方法|網址|秒 */
	String eventKey;
	/** 請求時間（epoch 毫秒） */
	Long eventTime;
	String ip;
	/** IpMatch.canonical(ip)，等值查詢用 */
	String ipKey;
	String country;
	/** 站台 Host（去掉連接埠、小寫） */
	String host;
	String method;
	String uri;
	String userAgent;
	/** vpatch、crs，兩者都有時為 crs,vpatch */
	String ruleSource;
	/** 命中規則的分類代碼，前後與中間以逗號分隔（,vpatch,crs-lfi,），篩選用 */
	String categories;
	/** block（WAF 擋下，403）或 log（只記錄） */
	String action;
	/** 第一條命中規則的說明 */
	String message;

	public String getEventKey() {
		return eventKey;
	}

	public void setEventKey(String eventKey) {
		this.eventKey = eventKey;
	}

	public Long getEventTime() {
		return eventTime;
	}

	public void setEventTime(Long eventTime) {
		this.eventTime = eventTime;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getIpKey() {
		return ipKey;
	}

	public void setIpKey(String ipKey) {
		this.ipKey = ipKey;
	}

	public String getCountry() {
		return country;
	}

	public void setCountry(String country) {
		this.country = country;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getMethod() {
		return method;
	}

	public void setMethod(String method) {
		this.method = method;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public String getUserAgent() {
		return userAgent;
	}

	public void setUserAgent(String userAgent) {
		this.userAgent = userAgent;
	}

	public String getRuleSource() {
		return ruleSource;
	}

	public void setRuleSource(String ruleSource) {
		this.ruleSource = ruleSource;
	}

	public String getCategories() {
		return categories;
	}

	public void setCategories(String categories) {
		this.categories = categories;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
