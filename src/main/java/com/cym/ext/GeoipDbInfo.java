package com.cym.ext;

/**
 * GeoIP MMDB 資料庫資訊（view DTO，非 @Table 實體）。
 * 給 header 下拉與「防護與憑證」頁的 GeoIP 資訊表格顯示用。
 */
public class GeoipDbInfo {
	/** country / city / asn */
	private String key;
	/** 顯示名稱：Country / City / ASN */
	private String displayName;
	/** 檔名：GeoLite2-Country.mmdb ... */
	private String fileName;
	/** 資料庫版本（MMDB build date，格式 yyyy.MM.dd）；檔案不存在/讀失敗為 null */
	private String version;
	/** 檔案是否存在 */
	private Boolean exists;
	/** 可讀檔案大小（如 6.3 MB）；檔案不存在為 null */
	private String sizeStr;
	/** 上次手動下載時間（epoch millis）；未下載過為 null */
	private Long lastUpdateAt;
	/** 上次手動下載時間（格式化字串 yyyy-MM-dd HH:mm）；未下載過為 null */
	private String lastUpdateStr;
	/** 排程說明（目前固定為 Docker cron 每週三、六 03:00 UTC） */
	private String scheduleStr;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public String getDisplayName() {
		return displayName;
	}

	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

	public Boolean getExists() {
		return exists;
	}

	public void setExists(Boolean exists) {
		this.exists = exists;
	}

	public String getSizeStr() {
		return sizeStr;
	}

	public void setSizeStr(String sizeStr) {
		this.sizeStr = sizeStr;
	}

	public Long getLastUpdateAt() {
		return lastUpdateAt;
	}

	public void setLastUpdateAt(Long lastUpdateAt) {
		this.lastUpdateAt = lastUpdateAt;
	}

	public String getLastUpdateStr() {
		return lastUpdateStr;
	}

	public void setLastUpdateStr(String lastUpdateStr) {
		this.lastUpdateStr = lastUpdateStr;
	}

	public String getScheduleStr() {
		return scheduleStr;
	}

	public void setScheduleStr(String scheduleStr) {
		this.scheduleStr = scheduleStr;
	}
}
