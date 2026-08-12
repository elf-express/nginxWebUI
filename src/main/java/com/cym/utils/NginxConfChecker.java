package com.cym.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.cym.model.NginxDirective;
import com.cym.service.NginxDocService;

/**
 * 拿一段 conf 文字對照官方文件檢查:指令是否存在、是否用在合法 context。
 *
 * 只做單向掃描追蹤區塊巢狀,不建完整語法樹——判斷 context 只需要知道「目前在哪一層」。
 * 也因此對半成品設定同樣能給意見,不會因為語法不完整就整份放棄。
 *
 * 只回報能確定的問題:無法判斷的一律不報,誤報會讓使用者不信任整個工具。
 */
public class NginxConfChecker {

	/**
	 * 內容是指令的區塊;出現 name { 時把 name 推成目前的 context。
	 *
	 * 刻意不含 map / geo / types / charset_map / split_clients —— 它們大括號裡裝的是
	 * 「值對值」的對照資料(default upgrade;、C0 D18E;),逐行當指令查會把 default、CN
	 * 報成拼錯的指令。不在這張表上的區塊一律當作看不懂,見 {@link #OPAQUE}。
	 *
	 * 語料 15 個 context 全部在這裡有對應(扣掉 any 與資料區塊),漏一個就等於那個 context
	 * 底下的指令永遠不會被檢查 —— mgmt / oidc_provider / acme_issuer 共 41 條就是這樣被漏掉過。
	 */
	private static final List<String> DIRECTIVE_BLOCKS = List.of(
			"http", "server", "location", "upstream", "stream", "events", "mail", "limit_except", "if",
			"mgmt", "oidc_provider", "acme_issuer");

	/**
	 * 頂層區塊 → 該層只認這個家族的模組。
	 *
	 * stream { server { } } 與 http { server { } } 對堆疊來說都只是字串 server,只看最內層
	 * 會讓 add_header、root 這些純 HTTP 指令在 stream 的 server 裡暢行無阻(它們的 contexts
	 * 含 server,一比就中)—— 而那正是本 repo 反覆踩到的雷。
	 */
	private static final List<String> FAMILY_BLOCKS = List.of("http", "stream", "mail");

	/** 看不懂的區塊:資料區塊、或第三方模組的區塊(geoip2 { })。整層都不檢查。 */
	private static final String OPAQUE = "opaque";

	/** 官方把「哪裡都能用」寫成 context any(全語料只有 include 一條)。 */
	private static final String ANY = "any";

	/** 指令名的形狀。語料 969 條的名字全部符合,所以不符合的字就不是打錯的指令。 */
	private static final Pattern DIRECTIVE_NAME = Pattern.compile("[a-z][a-z0-9_]*");

	private NginxConfChecker() {
	}

	public static List<String> check(String conf, NginxDocService svc) {
		List<String> problems = new ArrayList<>();
		if (conf == null || conf.isBlank()) {
			return problems;
		}

		Deque<String> stack = new ArrayDeque<>();
		String[] lines = conf.split("\n");

		for (int i = 0; i < lines.length; i++) {
			// 行尾註解要先剝掉,不能只認整行都是註解的情況。手寫 conf 到處是 server { # site A,
			// 而這種行結尾是註解不是 {:區塊沒被推進 stack,裡面每一行都拿到錯的 context,
			// 配對的 } 又多 pop 一層,錯位一路擴散到檔尾。指令行同理(proxy_pas x; # typo
			// 結尾不是分號就整行被丟掉,真的錯字反而靜音)。
			String line = stripComment(lines[i]).trim();
			int lineNo = i + 1;

			if (line.isEmpty()) {
				continue; // 空行,或整行都是註解
			}
			// 收尾的大括號常帶註解(} # end location)。只比對整行等於 } 會漏掉這個 pop。
			if (line.startsWith("}")) {
				stack.pollLast();
				continue;
			}

			String first = line.split("[\\s{;]", 2)[0];

			if (line.endsWith("{")) {
				stack.addLast(DIRECTIVE_BLOCKS.contains(first) ? first : OPAQUE);
				continue;
			}
			if (!line.endsWith(";")) {
				continue; // 跨行指令,無法確定 → 不報
			}

			String ctx = stack.isEmpty() ? "main" : stack.peekLast();
			if (OPAQUE.equals(ctx)) {
				// 連這一層在講什麼都不知道,就不能斷言裡面的字是不是指令 —— 這個判斷要在
				// 「指令不存在」之前,否則 map 裡的 default 0; 照樣會被報成拼錯的 default_type。
				continue;
			}

			if (!DIRECTIVE_NAME.matcher(first).matches()) {
				// 不長得像指令名的字不是打錯的指令,是別的東西 —— 最常見的是跨行字串的續行
				// (log_format 第二行的 '$status …)。語料 969 條指令的名字全部符合這個形狀。
				continue;
			}

			List<NginxDirective> defs = svc.directive(first);
			if (defs.isEmpty()) {
				List<String> hints = svc.suggest(first);
				if (!hints.isEmpty()) {
					problems.add("第 " + lineNo + " 行: 未知指令 " + first + ",是否想寫 " + String.join(" / ", hints) + " ?");
				}
				continue; // 沒有候選就不報:可能是第三方模組的指令
			}

			// 同名指令可能跨模組（語料有 122 個這種名字）。只要任一個定義允許目前 context
			// 就算合法 —— 否則 stream 設定裡的 proxy_pass 會被 http 版的定義誤判成錯誤。
			List<NginxDirective> known = defs.stream().filter(d -> !d.contexts().isEmpty()).toList();
			if (known.isEmpty()) {
				continue; // 沒有任何一份定義說得出 context → 不報
			}
			// 先用最外層區塊把候選定義篩掉別的家族,再比對 context。同名跨模組的指令兩份定義
			// 都在,篩完會留下對的那一份 —— 這正是 directive() 回 list 的用意。
			String family = topLevelFamily(stack);
			List<NginxDirective> applicable = family == null ? known
					: known.stream().filter(d -> {
						String f = moduleFamily(d.module());
						return f == null || f.equals(family);
					}).toList();

			List<String> accepted = acceptedContexts(stack, ctx);
			// applicable 被篩空時 anyMatch 自然是 false,也就會被報出來 —— 一條純 HTTP 指令
			// 在 stream 底下沒有任何一份適用的定義,這件事是確定的。
			boolean legal = applicable.stream().anyMatch(
					d -> d.contexts().contains(ANY) || d.contexts().stream().anyMatch(accepted::contains));
			if (!legal) {
				String allowed = known.stream()
						.map(d -> d.module() + ": " + String.join(", ", d.contexts()))
						.collect(Collectors.joining(" / "));
				problems.add("第 " + lineNo + " 行: " + first + " 不能用在 " + where(family, ctx)
						+ ",官方允許的 context 是 " + allowed + " — " + known.get(0).sourceUrl());
			}
		}
		return problems;
	}

	/**
	 * 剝掉行尾註解。引號裡的 # 不是註解 —— add_header X "a#b"; 剝過頭會變成沒有分號的殘句,
	 * 整行從檢查裡消失,連真的寫錯都跟著靜音。
	 */
	private static String stripComment(String line) {
		char quote = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (quote != 0 && c == '\\' && i + 1 < line.length()) {
				i++; // 引號內的跳脫,連同下一個字一起跳過
			} else if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
			} else if (c == '"' || c == '\'') {
				quote = c;
			} else if (c == '#') {
				return line.substring(0, i);
			}
		}
		return line;
	}

	/** 最外層區塊決定的家族;不是 http / stream / mail(例如只貼了一段 server {})就回 null 表示不篩。 */
	private static String topLevelFamily(Deque<String> stack) {
		String outermost = stack.peekFirst();
		return outermost != null && FAMILY_BLOCKS.contains(outermost) ? outermost : null;
	}

	/**
	 * 模組屬於哪個家族。回 null 代表「哪個家族都算數」——ngx_core_module 這種跨層模組
	 * (include 就在裡面),以及語料裡 3 個不照前綴命名的模組(ngx_mgmt / ngx_otel /
	 * ngx_google_perftools)。只在「能明確判定是別的家族」時才篩掉,才不會反過來造成誤報。
	 */
	private static String moduleFamily(String module) {
		for (String family : FAMILY_BLOCKS) {
			if (module.startsWith("ngx_" + family + "_")) {
				return family;
			}
		}
		return null;
	}

	/**
	 * 訊息裡怎麼稱呼「目前這一層」。
	 *
	 * 只有 stream / mail 需要標明是哪一層的 server:http 的 server 是所有人預設的讀法,
	 * 標成「http 的 server」只是雜訊,而「stream 的 server」正是要講清楚的那個區別。
	 */
	private static String where(String family, String ctx) {
		boolean needsQualifier = ("stream".equals(family) || "mail".equals(family)) && !family.equals(ctx);
		return needsQualifier ? family + " 的 " + ctx : ctx;
	}

	/**
	 * 目前這一層在官方文件裡可能被寫成哪些 context 名稱。
	 *
	 * 只有 if 需要兩個名字:rewrite 模組的 return / set / rewrite 寫 if,另外 28 條
	 * (add_header、expires、proxy_pass…)寫 if in location,而同一個 if { } 裡兩種都會出現。
	 * 但不能無條件全收 —— if in location 的指令掛在 server 底下的 if 裡是真的錯,nginx 會拒絕啟動,
	 * 所以要看 if 的上一層到底是不是 location。
	 */
	private static List<String> acceptedContexts(Deque<String> stack, String ctx) {
		if (!"if".equals(ctx)) {
			return List.of(ctx);
		}
		Iterator<String> fromInnermost = stack.descendingIterator();
		fromInnermost.next(); // 跳過 if 自己
		String parent = fromInnermost.hasNext() ? fromInnermost.next() : "main";
		return "location".equals(parent) ? List.of("if", "if in location") : List.of("if");
	}
}
