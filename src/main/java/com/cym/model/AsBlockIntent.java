package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.InitValue;
import com.cym.sqlhelper.config.Table;

@Table
public class AsBlockIntent extends BaseModel {

	public static final String STATUS_CANDIDATE = "candidate";
	public static final String STATUS_PENDING = "pending";
	public static final String STATUS_ACTIVE = "active";
	public static final String STATUS_FAILED = "failed";
	public static final String STATUS_REVOKED = "revoked";

	String asn;

	@InitValue("pending")
	String status;

	@InitValue("24h")
	String duration;

	/**
	 * nginxwebui:as-ban:AS{asn}
	 */
	String reasonTag;

	String pushBatchId;

	Long lastPushAt;

	String lastError;

	/**
	 * manual | strict
	 */
	String createdByProfile;

	String note;

	public AsBlockIntent() {
	}

	public String getAsn() {
		return asn;
	}

	public void setAsn(String asn) {
		this.asn = asn;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDuration() {
		return duration;
	}

	public void setDuration(String duration) {
		this.duration = duration;
	}

	public String getReasonTag() {
		return reasonTag;
	}

	public void setReasonTag(String reasonTag) {
		this.reasonTag = reasonTag;
	}

	public String getPushBatchId() {
		return pushBatchId;
	}

	public void setPushBatchId(String pushBatchId) {
		this.pushBatchId = pushBatchId;
	}

	public Long getLastPushAt() {
		return lastPushAt;
	}

	public void setLastPushAt(Long lastPushAt) {
		this.lastPushAt = lastPushAt;
	}

	public String getLastError() {
		return lastError;
	}

	public void setLastError(String lastError) {
		this.lastError = lastError;
	}

	public String getCreatedByProfile() {
		return createdByProfile;
	}

	public void setCreatedByProfile(String createdByProfile) {
		this.createdByProfile = createdByProfile;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

}
