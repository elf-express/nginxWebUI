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
	/** 排程顯示字串（如 "Daily 03:00"，由 GeoipService 依 geoip.fetchTime 動態組）；供 versions JSON */
	private String scheduleStr;
	/** 排程時間 HH:mm（來自 geoip.fetchTime）；前端表格以 i18n 模板套此值顯示 */
	private String scheduleTime;
	/** 檔案絕對路徑 */
	private String filePath;
	/** 檔案最後修改 epoch millis;不存在為 null */
	private Long lastModifiedAt;
	/** 檔案最後修改格式化字串 yyyy-MM-dd HH:mm;不存在為 null */
	private String lastModifiedStr;
	/** 交叉驗證狀態:ok=正常、warn=待確認 */
	private String status;
	/** 待確認原因文字(已套 i18n);正常時空 list */
	private java.util.List<String> statusReasons;
	/** 是否為 Cloudflare IP 清單列(realip.conf,無 mmdb build date) */
	private Boolean cloudflare;

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

	public String getScheduleTime() {
		return scheduleTime;
	}

	public void setScheduleTime(String scheduleTime) {
		this.scheduleTime = scheduleTime;
	}

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		this.filePath = filePath;
	}

	public Long getLastModifiedAt() {
		return lastModifiedAt;
	}

	public void setLastModifiedAt(Long lastModifiedAt) {
		this.lastModifiedAt = lastModifiedAt;
	}

	public String getLastModifiedStr() {
		return lastModifiedStr;
	}

	public void setLastModifiedStr(String lastModifiedStr) {
		this.lastModifiedStr = lastModifiedStr;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public java.util.List<String> getStatusReasons() {
		return statusReasons;
	}

	public void setStatusReasons(java.util.List<String> statusReasons) {
		this.statusReasons = statusReasons;
	}

	public Boolean getCloudflare() {
		return cloudflare;
	}

	public void setCloudflare(Boolean cloudflare) {
		this.cloudflare = cloudflare;
	}
}
