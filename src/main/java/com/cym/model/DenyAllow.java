package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.InitValue;
import com.cym.sqlhelper.config.Table;

@Table
public class DenyAllow extends BaseModel {

	/**
	 * 名单名称
	 */
	String name;

	/**
	 * ip名单(用回车分隔)
	 */
	String ip;

	/**
	 * 來源 URL（可選）— 設定後排程會定時 HTTP GET 此 URL 解析 IP 自動更新 ip 欄位
	 */
	String sourceUrl;

	/**
	 * 每日抓取時間 (HH:mm 24 小時制)。配合 sourceUrl 使用，留空則不抓取
	 */
	String fetchTime;

	/**
	 * 上次抓取時間（epoch millis），null 表示尚未抓過
	 */
	Long lastFetchAt;

	/**
	 * 名單類型：deny=黑名單、allow=白名單。舊資料(null)由 InitConfig migration 反查引用歸類。
	 */
	@InitValue("deny")
	String type;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getSourceUrl() {
		return sourceUrl;
	}

	public void setSourceUrl(String sourceUrl) {
		this.sourceUrl = sourceUrl;
	}

	public String getFetchTime() {
		return fetchTime;
	}

	public void setFetchTime(String fetchTime) {
		this.fetchTime = fetchTime;
	}

	public Long getLastFetchAt() {
		return lastFetchAt;
	}

	public void setLastFetchAt(Long lastFetchAt) {
		this.lastFetchAt = lastFetchAt;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

}
