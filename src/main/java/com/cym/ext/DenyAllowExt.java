package com.cym.ext;

import java.util.List;

import com.cym.model.DenyAllow;

public class DenyAllowExt {
	DenyAllow denyAllow;
	Integer ipCount;
	List<String> usedBy;

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

	public List<String> getUsedBy() {
		return usedBy;
	}

	public void setUsedBy(List<String> usedBy) {
		this.usedBy = usedBy;
	}

}
