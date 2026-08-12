package com.cym.model;

import java.util.List;

/**
 * 一條 nginx 指令的官方定義。
 * defaultValue 為 null 的意思由 origin 決定,不能一概而論 —— 見 {@link Origin}。
 */
public record NginxDirective(
		String name,
		String syntax,
		String defaultValue,
		List<String> contexts,
		String module,
		String sourceUrl,
		String description,
		Origin origin) {

	/** 這條定義是從哪種頁面抽出來的 —— 決定 defaultValue 為 null 時該怎麼解讀。 */
	public enum Origin {
		/** nginx.org 原始表格。defaultValue 為 null 表示官方 Default 欄寫「—」,確實無預設值(語料 359 條)。 */
		OFFICIAL_TABLE,
		/** 本專案手寫的 zh-TW 摘要頁。沒有 Default 欄,null 只代表「未列出」,不是「沒有」。 */
		PROJECT_SUMMARY
	}
}
