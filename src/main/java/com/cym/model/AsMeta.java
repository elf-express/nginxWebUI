package com.cym.model;

import com.cym.sqlhelper.bean.BaseModel;
import com.cym.sqlhelper.config.Table;

@Table
public class AsMeta extends BaseModel {

	/**
	 * ASN number digits, e.g. "51167"
	 */
	String asn;

	String handle;

	String description;

	/**
	 * ISO 3166-1 alpha-2
	 */
	String countryCode;

	/**
	 * hosting | isp | business | education_research | government_admin | null
	 */
	String category;

	String networkRole;

	String origin;

	/**
	 * yyyy-MM-dd or null
	 */
	String lastAnnounced;

	Long syncedAt;

	public AsMeta() {
	}

	public String getAsn() {
		return asn;
	}

	public void setAsn(String asn) {
		this.asn = asn;
	}

	public String getHandle() {
		return handle;
	}

	public void setHandle(String handle) {
		this.handle = handle;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public String getNetworkRole() {
		return networkRole;
	}

	public void setNetworkRole(String networkRole) {
		this.networkRole = networkRole;
	}

	public String getOrigin() {
		return origin;
	}

	public void setOrigin(String origin) {
		this.origin = origin;
	}

	public String getLastAnnounced() {
		return lastAnnounced;
	}

	public void setLastAnnounced(String lastAnnounced) {
		this.lastAnnounced = lastAnnounced;
	}

	public Long getSyncedAt() {
		return syncedAt;
	}

	public void setSyncedAt(Long syncedAt) {
		this.syncedAt = syncedAt;
	}

}
