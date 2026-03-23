package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.InitValue;
import com.cym.sqlhelper.config.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Nginx 動態模組管理
 */
@Table
public class Module extends BaseModel {
	/**
	 * 模組 .so 檔案名稱 (e.g., "ngx_stream_module.so")
	 */
	String name;
	/**
	 * i18n 描述 key 後綴 (e.g., "descrStream")，透過 moduleStr map 解析
	 */
	String descrKey;
	/**
	 * 是否啟用 true:啟用 false:停用(預設)
	 */
	@InitValue("false")
	Boolean enable;
	/**
	 * 載入順序（固定不可更改，數字越小越先載入）
	 */
	@JsonIgnore
	Long seq;

	public Module() {
	}

	public Module(String name, String descrKey, Boolean enable, Long seq) {
		this.name = name;
		this.descrKey = descrKey;
		this.enable = enable;
		this.seq = seq;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescrKey() {
		return descrKey;
	}

	public void setDescrKey(String descrKey) {
		this.descrKey = descrKey;
	}

	public Boolean getEnable() {
		return enable;
	}

	public void setEnable(Boolean enable) {
		this.enable = enable;
	}

	public Long getSeq() {
		return seq;
	}

	public void setSeq(Long seq) {
		this.seq = seq;
	}
}
