package com.cym.ext;

import java.util.List;

/**
 * GeoIP/IP 資料庫交叉驗證結果(view DTO)。
 * evaluateStatus 純函式回傳 reason code + days,由 GeoipService 再套 i18n 模板成顯示文字。
 */
public record GeoipStatus(String status, List<Reason> reasons) {
	/** code: fileStale(檔案最後修改距今 > 7 天) / buildStale(建置日期距今 > 14 天) / corrupt(讀版本失敗)。days: corrupt 為 null。 */
	public record Reason(String code, Integer days) {
	}
}
