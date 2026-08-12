package com.cym.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cym.model.NginxDirective;

/**
 * 把抓取自 nginx.org 的 markdown 解析成指令定義。
 *
 * 全語料 947 個指令表格格式零變異,表格本身不需要處理特例:
 *   <tr><th>Syntax:</th><td><code><strong>NAME</strong> ...;</code></td></tr>
 *   <tr><th>Default:</th><td><pre>VALUE</pre></td></tr>   (無預設值時是 —)
 *   <tr><th>Context:</th><td><code>ctx</code>, <code>ctx</code></td></tr>
 *
 * 但頁首的 Source 與 Module 標題有擷取畸形,各需一道防線:
 *   - Source 可能被寫成 markdown 連結 [url](url) → 見 SOURCE 的 \[?
 *   - Module 標題的底線可能被剝掉 → 見 moduleFromUrl 的 fallback
 *
 * 判斷「這是不是官方頁面」用內容特徵而非檔名:自寫文件放進同一目錄也不會污染指令索引。
 *
 * 語料另有 7 頁官方模組頁是手寫 zh-TW 摘要,沒有 nginx.org 表格(access / auth_request /
 * limit_conn ×2 / limit_req / map / realip)—— 恰好是本專案自己產生的設定最常用的那幾條。
 * 只認表格會讓 limit_req、real_ip_header 這些指令根本不在索引裡,設定檢查就會對自家合法
 * 輸出誤報「指令不存在」。摘要流程見 parseSummary,只在表格抽不到東西時才跑。
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

	/** 任何層級的標題,用來切出「這條指令的段落到哪裡結束」。 */
	private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$", Pattern.MULTILINE);
	/** 摘要頁的指令標題:h3/h4 + backtick 包住的識別字,其餘(版本號、context)留在 group(2)。 */
	private static final Pattern SUMMARY_HEADING = Pattern.compile("^`([a-z][a-z0-9_]*)`\\s*(.*)$");
	/** 標題尾巴的括號。全半形都收,語料目前只有全形。 */
	private static final Pattern PARENS = Pattern.compile("[（(]([^）)]*)[）)]");
	private static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");
	private static final Pattern CONTEXT_BULLET = Pattern.compile("^-\\s*\\*\\*語境[：:]\\*\\*\\s*(.*)$", Pattern.MULTILINE);
	private static final Pattern SYNTAX_BULLET = Pattern.compile("^-\\s*\\*\\*語法[：:]\\*\\*\\s*(.*)$", Pattern.MULTILINE);
	/** 說明段落要跳過的中繼資料條列(語境／語法／預設),它們是欄位不是描述。 */
	private static final Pattern META_BULLET = Pattern.compile("^-\\s*\\*\\*(語境|語法|預設)[：:]\\*\\*");
	private static final Pattern FENCE_LINE = Pattern.compile("^\\s*```");

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
		if (!module.matches("ngx_[a-z0-9_]+_module")) {
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
					description,
					NginxDirective.Origin.OFFICIAL_TABLE));
		}

		// 表格流程有收穫就到此為止 —— 92 頁表格頁的行為一個位元都不變,摘要指令純粹疊加。
		if (result.isEmpty()) {
			result.addAll(parseSummary(markdown, module, sourceUrl));
		}
		return result;
	}

	/**
	 * 手寫 zh-TW 摘要頁的指令抽取。三種子格式並存:
	 *   #### `allow`                                    → 語境／語法走 - **粗體：** 條列
	 *   #### `limit_req_zone`（僅 `http`）                → 標題括號帶 context,語法在 fenced block
	 *   #### `limit_conn_dry_run`（1.17.6）               → 括號裝的是版本號,不是 context
	 *
	 * 版本號與 context 的分辨規則:括號內有 backtick 才是 context。全語料無例外。
	 */
	private static List<NginxDirective> parseSummary(String markdown, String module, String sourceUrl) {
		List<NginxDirective> result = new ArrayList<>();

		// 先收集所有標題位置:一條指令的段落止於「下一個標題」,不論那個標題是不是指令。
		// 用下一個「指令標題」當邊界會讓每頁最後一條指令吃進整個 ### 本專案 區段。
		List<int[]> headings = new ArrayList<>(); // {標題行起點, 標題行終點, 井號數}
		Matcher h = HEADING.matcher(markdown);
		while (h.find()) {
			headings.add(new int[] { h.start(), h.end(), h.group(1).length() });
		}

		List<String> previousContexts = List.of(); // 供「語境：同上」反向參照
		for (int i = 0; i < headings.size(); i++) {
			int[] cur = headings.get(i);
			if (cur[2] < 3 || cur[2] > 4) {
				continue; // 指令標題只出現在 h3(map)與 h4(其餘六頁)
			}
			Matcher title = SUMMARY_HEADING.matcher(markdown.substring(cur[0] + cur[2], cur[1]).trim());
			if (!title.find()) {
				continue; // 一般章節標題(### 範例 / #### 特殊參數)
			}
			String body = markdown.substring(cur[1], i + 1 < headings.size() ? headings.get(i + 1)[0] : markdown.length());

			List<String> contexts = contextsOf(title.group(2), body, previousContexts);
			previousContexts = contexts;

			result.add(new NginxDirective(
					title.group(1),
					syntaxOf(body),
					null, // 摘要頁沒有 Default 欄;PROJECT_SUMMARY 的 null 代表「未列出」而非「無預設值」
					contexts,
					module,
					sourceUrl,
					summaryDescription(body),
					NginxDirective.Origin.PROJECT_SUMMARY));
		}
		return result;
	}

	/** context 先看標題括號(內含 backtick 才算),再看 - **語境：** 條列,「同上」沿用前一條。 */
	private static List<String> contextsOf(String titleTail, String body, List<String> previousContexts) {
		Matcher parens = PARENS.matcher(titleTail);
		if (parens.find()) {
			List<String> fromTitle = backtickedTokens(parens.group(1));
			if (!fromTitle.isEmpty()) {
				return fromTitle;
			}
		}

		Matcher bullet = CONTEXT_BULLET.matcher(body);
		if (bullet.find()) {
			String value = bullet.group(1).trim().replaceAll("[。\\s]+$", "");
			if ("同上".equals(value)) {
				return previousContexts;
			}
			List<String> out = new ArrayList<>();
			for (String part : value.split("[,、/]")) {
				String token = part.replace("`", "").trim();
				if (!token.isEmpty()) {
					out.add(token);
				}
			}
			return List.copyOf(out);
		}
		return List.of();
	}

	/** 語法先看 - **語法：** 條列,沒有就取段落內第一個 fenced block 的第一行。 */
	private static String syntaxOf(String body) {
		Matcher bullet = SYNTAX_BULLET.matcher(body);
		if (bullet.find()) {
			return bullet.group(1).replace("`", "").trim();
		}
		boolean inFence = false;
		for (String line : body.split("\n")) {
			if (FENCE_LINE.matcher(line).find()) {
				if (inFence) {
					break; // 整個 block 都沒有可用的首行,不要跳到下一個 block 亂抓
				}
				inFence = true;
				continue;
			}
			if (inFence && !line.isBlank()) {
				return line.trim();
			}
		}
		return "";
	}

	/** 說明取段落內第一行實質文字,跳過中繼資料條列與 fenced block(那是語法,不是描述)。 */
	private static String summaryDescription(String body) {
		boolean inFence = false;
		for (String line : body.split("\n")) {
			if (FENCE_LINE.matcher(line).find()) {
				inFence = !inFence;
				continue;
			}
			String t = line.trim();
			if (inFence || t.isEmpty() || t.startsWith("#") || t.startsWith(">") || t.startsWith("|")
					|| META_BULLET.matcher(t).find()) {
				continue;
			}
			// 剝掉條列符號:它是版面而不是內容,留著會讓 MCP 回應多一個沒有意義的「- 」
			String s = stripHtml(t.replaceFirst("^-\\s+", ""));
			if (s.isEmpty()) {
				continue;
			}
			return s.length() > 400 ? s.substring(0, 400) + "…" : s;
		}
		return "";
	}

	/** 取出字串裡所有 backtick 包住的 token。 */
	private static List<String> backtickedTokens(String text) {
		List<String> out = new ArrayList<>();
		Matcher m = BACKTICKED.matcher(text);
		while (m.find()) {
			String token = m.group(1).trim();
			if (!token.isEmpty()) {
				out.add(token);
			}
		}
		return List.copyOf(out);
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
