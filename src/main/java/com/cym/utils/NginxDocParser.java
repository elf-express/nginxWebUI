package com.cym.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cym.model.NginxDirective;

/**
 * 把抓取自 nginx.org 的 markdown 解析成指令定義。
 *
 * 全語料 947 個指令表格格式零變異,所以這裡不需要處理特例:
 *   <tr><th>Syntax:</th><td><code><strong>NAME</strong> ...;</code></td></tr>
 *   <tr><th>Default:</th><td><pre>VALUE</pre></td></tr>   (無預設值時是 —)
 *   <tr><th>Context:</th><td><code>ctx</code>, <code>ctx</code></td></tr>
 *
 * 判斷「這是不是官方頁面」用內容特徵而非檔名:自寫文件放進同一目錄也不會污染指令索引。
 */
public class NginxDocParser {

	private static final Pattern SOURCE = Pattern.compile("^>\\s*Source:\\s*\\[?(https://nginx\\.org/[^\\s\\])]+)", Pattern.MULTILINE);
	private static final Pattern MODULE = Pattern.compile("^##\\s+Module\\s+(\\S+)", Pattern.MULTILINE);
	private static final Pattern TABLE = Pattern.compile("<table cellspacing=\"0\">.*?</table>", Pattern.DOTALL);
	private static final Pattern NAME = Pattern.compile("<strong>([^<]+)</strong>");
	private static final Pattern SYNTAX_CELL = Pattern.compile("<th>Syntax:</th><td>(.*?)</td>", Pattern.DOTALL);
	private static final Pattern DEFAULT_CELL = Pattern.compile("<th>Default:</th><td>(.*?)</td>", Pattern.DOTALL);
	private static final Pattern CONTEXT_CELL = Pattern.compile("<th>Context:</th><td>(.*?)</td>", Pattern.DOTALL);
	private static final Pattern CODE = Pattern.compile("<code>([^<]*)</code>");
	private static final Pattern TAG = Pattern.compile("<[^>]+>");

	private NginxDocParser() {
	}

	/** 移除 HTML 標籤、還原 markdown 跳脫底線與 HTML entity,壓掉多餘空白。 */
	public static String stripHtml(String html) {
		if (html == null) {
			return "";
		}
		String s = TAG.matcher(html).replaceAll("");
		s = s.replace("\\_", "_").replace("\\*", "*");
		s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
		return s.replaceAll("\\s+", " ").trim();
	}

	/** 解析單一頁面。不是 nginx.org 抓來的頁面回空清單。 */
	public static List<NginxDirective> parsePage(String markdown) {
		List<NginxDirective> result = new ArrayList<>();
		if (markdown == null) {
			return result;
		}

		Matcher src = SOURCE.matcher(markdown);
		if (!src.find()) {
			return result; // 沒有官方 Source 標頭 → 不是指令文件
		}
		String sourceUrl = src.group(1);

		Matcher mod = MODULE.matcher(markdown);
		String module = mod.find() ? stripHtml(mod.group(1)) : "";
		if (!module.matches("ngx_[a-z_]+_module")) {
			String fromUrl = moduleFromUrl(sourceUrl);
			if (!fromUrl.isEmpty()) {
				module = fromUrl;
			}
		}

		Matcher tables = TABLE.matcher(markdown);
		while (tables.find()) {
			String table = tables.group();
			// 表格之後、下一個表格之前的第一段文字就是這條指令的說明
			int tailStart = tables.end();
			int nextTable = markdown.indexOf("<table", tailStart);
			String tail = nextTable > 0 ? markdown.substring(tailStart, nextTable) : markdown.substring(tailStart);
			String description = firstParagraph(tail);

			Matcher syntaxCell = SYNTAX_CELL.matcher(table);
			if (!syntaxCell.find()) {
				continue; // 不是指令定義表格(例如頁面導覽表)
			}
			Matcher name = NAME.matcher(table);
			if (!name.find()) {
				continue;
			}

			String defaultValue = null;
			Matcher defCell = DEFAULT_CELL.matcher(table);
			if (defCell.find()) {
				String raw = stripHtml(defCell.group(1));
				if (!raw.isEmpty() && !"—".equals(raw)) {
					defaultValue = raw;
				}
			}

			List<String> contexts = new ArrayList<>();
			Matcher ctxCell = CONTEXT_CELL.matcher(table);
			if (ctxCell.find()) {
				Matcher code = CODE.matcher(ctxCell.group(1));
				while (code.find()) {
					String c = stripHtml(code.group(1));
					if (!c.isEmpty()) {
						contexts.add(c);
					}
				}
			}

			result.add(new NginxDirective(
					stripHtml(name.group(1)),
					stripHtml(syntaxCell.group(1)),
					defaultValue,
					contexts,
					module,
					sourceUrl,
					description));
		}
		return result;
	}

	/**
	 * 模組名優先取自 ## Module 標題;131page.md 的標題底線被抓取工具剝掉了(全語料唯一),
	 * 這時改從 sourceUrl 推導 —— URL 不經過 markdown 轉義,不會有這個問題。
	 */
	private static String moduleFromUrl(String url) {
		int slash = url.lastIndexOf('/');
		int dot = url.lastIndexOf(".html");
		if (slash < 0 || dot < 0 || dot <= slash) {
			return "";
		}
		String name = url.substring(slash + 1, dot);
		return name.startsWith("ngx_") ? name : "";
	}

	/** 取第一段實質文字當說明,跳過標題／引用／表格列。超過 400 字截斷,MCP 回應不需要整段。 */
	private static String firstParagraph(String tail) {
		for (String line : tail.split("\n")) {
			String t = line.trim();
			if (t.isEmpty() || t.startsWith("#") || t.startsWith(">") || t.startsWith("|") || t.startsWith("<table")) {
				continue;
			}
			String s = stripHtml(t);
			if (s.isEmpty()) {
				continue;
			}
			return s.length() > 400 ? s.substring(0, 400) + "…" : s;
		}
		return "";
	}
}
