package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.InitValue;
import com.cym.sqlhelper.config.Table;

@Table
public class AsnRule extends BaseModel {

	/**
	 * ASN 號碼，如 "4134"
	 */
	String asn;

	/**
	 * 組織名稱，如 "China Telecom Backbone"
	 */
	String orgName;

	/**
	 * 啟用狀態
	 */
	@InitValue("true")
	Boolean enable;

	public AsnRule() {
	}

	public String getAsn() {
		return asn;
	}

	public void setAsn(String asn) {
		this.asn = asn;
	}

	public String getOrgName() {
		return orgName;
	}

	public void setOrgName(String orgName) {
		this.orgName = orgName;
	}

	public Boolean getEnable() {
		return enable;
	}

	public void setEnable(Boolean enable) {
		this.enable = enable;
	}

}
