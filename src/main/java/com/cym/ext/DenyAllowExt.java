package com.cym.ext;

import com.cym.model.DenyAllow;

public class DenyAllowExt {
	DenyAllow denyAllow;
	Integer ipCount;
	/** yyyy-MM-dd HH:mm 格式，null 表示尚未抓取過 */
	String lastFetchAtStr;

	public String getLastFetchAtStr() {
		return lastFetchAtStr;
	}

	public void setLastFetchAtStr(String lastFetchAtStr) {
		this.lastFetchAtStr = lastFetchAtStr;
	}

	public DenyAllow getDenyAllow() {
		return denyAllow;
	}

	public void setDenyAllow(DenyAllow denyAllow) {
		this.denyAllow = denyAllow;
	}

	public Integer getIpCount() {
		return ipCount;
	}

	public void setIpCount(Integer ipCount) {
		this.ipCount = ipCount;
	}

}
