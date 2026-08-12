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
			// 一行可能收掉不只一層(} }、}}),或收完之後接著開新區塊(} location /b {)。
			// 這才是這裡不能寫成 equals("}") 的理由 —— 行尾註解在上面 stripComment 就剝掉了,
			// } # end location 走到這裡已經是 },那一種寫法 equals 也接得住。
			//
			// 只 pop 一次、再把整行剩下的部分丟掉,後面每一行都會被算在錯的那一層,而且這種
			// 設定的括號是平衡的,使用者照著但書去數括號只會更相信那個誤報。所以逐個吃掉
			// 行首的 },再讓剩下的部分走回底下的正常流程。
			while (line.startsWith("}")) {
				stack.pollLast();
				line = line.substring(1).trim();
			}
			if (line.isEmpty()) {
				continue; // 整行只有收尾的大括號
			}

			String first = line.split("[\\s{;]", 2)[0];

			if (line.endsWith("{")) {
				// 開區塊那一行本身也要比對 context。design doc 舉的頭號例子就是「if 寫在 http 層」,
				// 而「if 的 context 不含 http」與指令行的判斷一模一樣是確定的 —— 不屬於本類
				// 刻意保留的「不確定就閉嘴」。少了這一段,六種 nginx 會直接拒絕啟動的區塊錯位
				// (if/location 掛在 http、events 掛在 http 裡、server 掛在 location 裡、
				// upstream 掛在 server 裡、limit_except 掛在 server)全部靜音,而同一個名字
				// 寫成指令行反而抓得到。
				//
				// 只比對 context,不查「指令不存在」:認不得的區塊名多半是第三方模組的區塊
				// (geoip2 { }),那正是 OPAQUE 存在的理由,拿去猜拼字候選只會製造誤報。
				//
				// 順序要在 pushBlocks 之前 —— 比的是這個區塊「被放進去的那一層」,不是它
				// 自己開出來的那一層。
				checkContext(problems, stack, svc, first, lineNo);
				pushBlocks(stack, line, first);
				continue;
			}
			if (line.indexOf('{') >= 0 && line.indexOf('}') < 0) {
				// 開了區塊,但同一行 { 後面還有內容(server { listen 80;)。這種行不以 { 結尾,
				// 原本一層都不推,於是配對的 } 會 pop 掉父層,之後每一行都算在錯的那一層。
				//
				// 這比行尾 } 更難防:這種設定括號平衡、每個 } 也都已經獨立成行,連但書建議的
				// 「把 } 拆開再檢查一次」都驗不出來 —— 兩次結果一樣,誤報反而被當成可信。
				//
				// 推 OPAQUE 而不是猜區塊名:深度跟著對齊,這一層整層靜音。裡面少報幾則,
				// 好過拿錯的層去斷言。同一行已經有 } 的(map $a $b { default 0; })不推,
				// 那種行自己開自己關,推了就再也不會被 pop,整份設定從此靜音。
				checkContext(problems, stack, svc, first, lineNo);
				stack.addLast(OPAQUE);
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

			if (svc.directive(first).isEmpty()) {
				List<String> hints = svc.suggest(first);
				if (!hints.isEmpty()) {
					problems.add("第 " + lineNo + " 行: 未知指令 " + first + ",是否想寫 " + String.join(" / ", hints) + " ?");
				}
				continue; // 沒有候選就不報:可能是第三方模組的指令
			}

			checkContext(problems, stack, svc, first, lineNo);
		}
		return problems;
	}

	/**
	 * 比對 name 用在堆疊目前這一層合不合法,不合法就往 problems 加一則。
	 *
	 * 指令行與開區塊那一行共用這一段:兩者要問的問題完全相同(這個名字允許出現在這一層嗎),
	 * 差別只在開區塊那一行不查拼字候選。分成兩份寫的話,家族過濾與 if 的雙 context 這兩個
	 * 最容易寫錯的地方就會有兩份實作,而其中一份遲早會落後。
	 *
	 * 看不懂的層(OPAQUE)、不像指令名的字、語料查不到的名字、以及沒有任何一份定義說得出
	 * context 的名字,一律回報不出東西 —— 這幾道就是「只回報能確定的問題」的入口。
	 */
	private static void checkContext(List<String> problems, Deque<String> stack, NginxDocService svc,
			String name, int lineNo) {
		String ctx = stack.isEmpty() ? "main" : stack.peekLast();
		if (OPAQUE.equals(ctx) || !DIRECTIVE_NAME.matcher(name).matches()) {
			return;
		}
		// 同名指令可能跨模組（語料有 122 個這種名字）。只要任一個定義允許目前 context
		// 就算合法 —— 否則 stream 設定裡的 proxy_pass 會被 http 版的定義誤判成錯誤。
		List<NginxDirective> known = svc.directive(name).stream().filter(d -> !d.contexts().isEmpty()).toList();
		if (known.isEmpty()) {
			return; // 語料沒有這個名字,或沒有任何一份定義說得出 context → 不報
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
		if (legal) {
			return;
		}
		String allowed = known.stream()
				.map(d -> d.module() + ": " + String.join(", ", d.contexts()))
				.collect(Collectors.joining(" / "));
		problems.add("第 " + lineNo + " 行: " + name + " 不能用在 " + where(family, ctx)
				+ ",官方允許的 context 是 " + allowed + " — " + known.get(0).sourceUrl());
	}

	/**
	 * 推入這一行開啟的區塊。
	 *
	 * 同一行寫兩個區塊(http { server {)在手寫 conf 裡看得到,只推一層會讓裡面每一行都
	 * 少算一層,一份合法設定因此吐出抱怨 —— 那是誤報,和左大括號帶註解同一類。
	 *
	 * 但不能無條件數大括號:location ~ ^/api/v[0-9]{1,2}/ { 的正則裡就有大括號。所以只在
	 * 「整行沒有 }」且「切出來的每一段開頭都長得像區塊名」時才逐段推入,任一條不成立就
	 * 退回單次推入 —— 退路就是原本的行為,最差情況等於現狀。
	 */
	private static void pushBlocks(Deque<String> stack, String line, String fallback) {
		if (line.indexOf('}') < 0) {
			List<String> names = new ArrayList<>();
			for (String segment : line.split("\\{")) {
				String name = segment.trim().split("[\\s;]", 2)[0];
				if (!DIRECTIVE_NAME.matcher(name).matches()) {
					names.clear();
					break;
				}
				names.add(name);
			}
			if (!names.isEmpty()) {
				names.forEach(name -> stack.addLast(blockContext(name)));
				return;
			}
		}
		stack.addLast(blockContext(fallback));
	}

	/** 區塊名 → 要推進堆疊的 context;認不得的(含 map 這類資料區塊)一律 opaque。 */
	private static String blockContext(String name) {
		return DIRECTIVE_BLOCKS.contains(name) ? name : OPAQUE;
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
