package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.InitValue;
import com.cym.sqlhelper.config.Table;

@Table
public class GeoRule extends BaseModel {

	/**
	 * 模式：0=允許(白名單), 1=封鎖(黑名單)
	 */
	@InitValue("0")
	Integer mode;

	/**
	 * 國家代碼，逗號分隔 "TW,JP,US"
	 */
	String countries;

	/**
	 * 綁定 Server ID，null=全域(http 層級)
	 */
	String serverId;

	/**
	 * 啟用狀態
	 */
	@InitValue("true")
	Boolean enable;

	public GeoRule() {
	}

	public Integer getMode() {
		return mode;
	}

	public void setMode(Integer mode) {
		this.mode = mode;
	}

	public String getCountries() {
		return countries;
	}

	public void setCountries(String countries) {
		this.countries = countries;
	}

	public String getServerId() {
		return serverId;
	}

	public void setServerId(String serverId) {
		this.serverId = serverId;
	}

	public Boolean getEnable() {
		return enable;
	}

	public void setEnable(Boolean enable) {
		this.enable = enable;
	}

}
