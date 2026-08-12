package com.cym.mcp;

import java.util.List;
import java.util.stream.Collectors;

import org.noear.solon.ai.annotation.ToolMapping;
import org.noear.solon.ai.mcp.McpChannel;
import org.noear.solon.ai.mcp.server.annotation.McpServerEndpoint;
import org.noear.solon.annotation.Condition;
import org.noear.solon.annotation.Inject;
import org.noear.solon.annotation.Param;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cym.model.NginxDirective;
import com.cym.service.NginxDocService;
import com.cym.utils.NginxConfChecker;

/**
 * nginx 官方文件的 MCP 端點。
 *
 * 這個類只做協定門面:參數描述、結果轉字串、錯誤訊息。查詢邏輯一律在 NginxDocService,
 * 檢查邏輯在 NginxConfChecker。工具方法保持在 20 行內,日後加「操作 nginxWebUI」那組
 * 工具時才不會互相糾纏。
 *
 * 所有工具唯讀。任何情況都回傳可讀字串,不向外拋例外——協定層的例外對 AI 是不透明的失敗。
 *
 * 通道選 STREAMABLE_STATELESS:有狀態的 STREAMABLE 要先 initialize 握手、之後每個請求都要
 * 帶 Mcp-Session-Id,回應還包成 SSE。無狀態通道對一個純唯讀查詢服務已經夠用,而且裸 POST
 * 直接回 JSON,測試與除錯都簡單得多。
 *
 * {@code @Condition(onProperty = "mcp.token")}:沒設 token 就整個 bean 不註冊,連
 * 「Mcp-Server started ... mcpEndpoint=/mcp」那行 log 都不會出現。對一個自架管理工具來說,
 * 使用者明明沒開任何東西卻在升級後看到多一個對外端點的訊息,是會嚇到人的。
 * (AppFilter 的 404 仍然保留:兩道各自獨立,任一道失效都還擋得住。)
 */
@Condition(onProperty = NginxDocMcpServer.TOKEN_KEY)
@McpServerEndpoint(channel = McpChannel.STREAMABLE_STATELESS, mcpEndpoint = NginxDocMcpServer.ENDPOINT, name = "nginx-docs")
public class NginxDocMcpServer {
	private static final Logger logger = LoggerFactory.getLogger(NginxDocMcpServer.class);

	/**
	 * 端點路徑。router 掛 handler 與 AppFilter 認證都用這一個常數,不可在任一邊寫死字面值。
	 *
	 * <p><b>必須全小寫。</b>AppFilter 比對的是 {@code ctx.path().toLowerCase()},常數若含大寫,
	 * {@code startsWith} 會恆為 false —— 端點照樣掛得起來、但認證閘完全失效,變成未認證即可存取。
	 * (AppFilter 那邊另外再 toLowerCase() 一次作為第二道保險,但不要依賴它而在這裡寫大寫。)
	 *
	 * <p>這個常數比 {@link #TOKEN_KEY} 更不能半套改名:token key 只改一半的後果是端點永遠 404
	 * (fail-closed,煩但安全);**路徑**只改一半的後果是 router 在新路徑掛上 handler、filter 還守
	 * 舊路徑 —— fail-open。
	 */
	public static final String ENDPOINT = "/mcp";

	/**
	 * MCP 的開關兼認證金鑰的設定鍵。三個地方共用這一個常數:上面的 @Condition(決定 bean 註冊)、
	 * AppFilter(決定 404/401)、InitConfig(決定要不要載入語料)。
	 *
	 * 共用常數之外,三處還必須用**同一種讀法**(`Solon.cfg().getByExpr`)。cfg().get() 不查環境變數
	 * 而 getByExpr() 會 —— 混用會造出「bean 註冊了但 filter 一律回 404」這種端點永遠打不通、
	 * 又極難查的狀態。
	 */
	public static final String TOKEN_KEY = "mcp.token";

	/** 欄位在語料裡沒寫時的統一措辭。空字串會讓 AI 讀成「哪裡都不能用」/「沒有語法」。 */
	private static final String UNLISTED = "文件未列出";

	/**
	 * nginx_search 每次最多回幾筆。
	 *
	 * 上限存在不是問題,不講才是:@Param 描述與截斷提示都由這個常數組出來,改一個數字兩邊
	 * 一起變 —— 寫死字面值的話,遲早會出現「描述說 30、實際截到別的數字」。
	 */
	private static final int SEARCH_MAX_HITS = 30;

	@Inject
	NginxDocService docService;

	@ToolMapping(description = "查詢 nginx 指令的官方定義:語法、預設值、可用的 context、所屬模組與官方連結。同名指令若存在於多個模組會全部列出。查無時回傳拼法相近的候選。")
	public String nginx_directive(@Param(description = "指令名稱,例如 proxy_pass") String name) {
		if (indexEmpty()) {
			return indexEmptyMessage();
		}
		if (isBlank(name)) {
			return "請提供指令名稱,例如 proxy_pass。";
		}
		List<NginxDirective> defs = docService.directive(name);
		if (defs.isEmpty()) {
			List<String> hints = docService.suggest(name);
			return hints.isEmpty()
					? "查無指令 " + name + "。可改用 nginx_search 以關鍵字搜尋。"
					: "查無指令 " + name + "。是否想查:" + String.join(" / ", hints);
		}
		if (defs.size() == 1) {
			return format(defs.get(0));
		}
		// 全部列出而非挑一個:proxy_pass 在 http 與 stream 底下的 context 完全不同,
		// 只回其中一份會讓 AI 拿著錯誤的 context 卻毫無察覺。
		return defs.size() + " 個模組定義了 " + name + ",以下全部列出:\n\n"
				+ defs.stream().map(this::format).collect(Collectors.joining("\n\n"));
	}

	// 描述要講清楚這是什麼樣的比對,否則「全文搜尋」會被讀成拆詞檢索。實作是把整串關鍵字
	// 當一個字面片語做大小寫不敏感的 indexOf,而語料內文多半已譯成 zh-TW —— 兩件事疊起來,
	// nginx 最經典的英文片語 reverse proxy 是查不到的(它在語料裡寫作「反向代理」)。
	// 描述寫得比實作大,呼叫端就會拿一次查無當成「官方文件沒講這件事」。
	@ToolMapping(description = "以關鍵字全文搜尋 nginx 官方文件,用於還不知道指令名稱時。比對方式是把整串關鍵字當一個字面片語(不拆詞、不分大小寫),且語料內文多為 zh-TW 譯文,因此單一短詞、指令名或中文詞命中率最高,英文多詞片語(例如 reverse proxy)經常查無。每頁只回第一個命中片段與來源連結。")
	public String nginx_search(@Param(description = "搜尋關鍵字,單一短詞或指令名效果最好") String query,
			@Param(description = "最多回傳幾筆,預設 10,上限 " + SEARCH_MAX_HITS
					+ "(要求更多會截到上限,並在回應開頭說明)", required = false) Integer limit) {
		if (indexEmpty()) {
			return indexEmptyMessage();
		}
		if (isBlank(query)) {
			return "請提供搜尋關鍵字。";
		}
		int n = (limit == null || limit <= 0) ? 10 : Math.min(limit, SEARCH_MAX_HITS);
		List<String> hits = docService.search(query, n);
		// 查無不能停在死路 —— 其他工具查無時都給得出下一步,只有這裡是一堵牆。而且這個工具
		// 查無的最常見原因(整串片語比對 + zh-TW 譯文)恰好是縮短關鍵字就能解掉的。
		if (hits.isEmpty()) {
			return "查無「" + query + "」的相關內容。這是整串字面比對:請把關鍵字縮短成單一詞再試一次"
					+ "(多詞片語只要有一個字不完全相符就會整串落空),或改用中文譯詞。"
					+ "若已經知道指令名稱,用 nginx_directive 直接查它的定義;"
					+ "若想知道某個 context(location、server…)能用哪些指令,用 nginx_context。";
		}
		// 靜默截斷與 nginx_context 的截斷是同一件事:可以少給,但不能讓呼叫端以為手上這份是
		// 全部。差別在 search 是逐頁掃到湊滿 n 就停,拿不到「總命中數」,所以只講得出上限
		// 生效了、實際回了幾筆 —— 不為了印總數去改搜尋演算法。
		String header = limit != null && limit > SEARCH_MAX_HITS
				? "要求 " + limit + " 筆,但本工具每次最多回 " + SEARCH_MAX_HITS + " 筆,以下 " + hits.size() + " 筆:\n\n"
				: "";
		return header + String.join("\n\n---\n\n", hits);
	}

	@ToolMapping(description = "列出某個 nginx 模組的所有指令。簡寫若對應多個模組會回候選清單要求指定。")
	public String nginx_module(@Param(description = "模組名稱或簡寫,例如 ngx_http_proxy_module 或 proxy") String name) {
		if (indexEmpty()) {
			return indexEmptyMessage();
		}
		if (isBlank(name)) {
			return "請提供模組名稱或簡寫,例如 ngx_http_proxy_module 或 proxy。";
		}
		List<String> hits = docService.byModule(name);
		if (hits.isEmpty()) {
			// 查無不能停在死路。規格的錯誤契約是「查無 → 回傳該類別的有效值」,nginx_context
			// 直接倒出 15 個 context;模組有 99 個,整包倒出只是把成本轉嫁給呼叫端,所以改成
			// 給一個走得通的下一步 —— 這個參數本來就是子字串比對,ngx_ 就能把 99 個全列出來。
			// 數字現算不寫死:語料增減模組時,這句話不會變成另一個過期的說明。
			return "查無模組 " + name + "。這個參數是子字串比對:用 proxy、ssl 這類簡寫可列出相符的模組,"
					+ "查 ngx_ 可列出全部 " + docService.byModule("ngx_").size() + " 個模組名稱;"
					+ "也可以先用 nginx_directive 查一條指令,回應裡的「模組」欄位就是完整模組名。";
		}
		if (hits.size() > 1) {
			return "「" + name + "」對應到多個模組,請指定其中一個:\n" + String.join("\n", hits);
		}
		String module = hits.get(0);
		List<NginxDirective> list = docService.directivesOfModule(module);
		// 印總條數,與 nginx_context 對稱 —— 少了它,呼叫端無從判斷這份清單是不是完整的。
		return module + "(共 " + list.size() + " 條指令)\n\n"
				+ list.stream().map(this::brief).collect(Collectors.joining("\n"));
	}

	@ToolMapping(description = "反查某個 context(例如 location、server、http)裡能使用哪些指令。寫設定時用這個確認指令放對地方。")
	public String nginx_context(@Param(description = "context 名稱,例如 location") String context,
			// 提示要指向真正最大的那個 context,否則呼叫端會照著它低估最壞情況。
			// server(761 條 / 約 44 KB)> http(599 條)> location(541 條)—— 由
			// NginxDocMcpServerTest.context參數的大小提示要指向真正最大的context 從語料重算核對。
			@Param(description = "最多列出幾條,省略則列出全部(最大的 server 有 761 條,約 44 KB)", required = false) Integer limit) {
		if (indexEmpty()) {
			return indexEmptyMessage();
		}
		if (isBlank(context)) {
			return "請提供 context 名稱。已知的 context:" + String.join(", ", docService.knownContexts());
		}
		List<NginxDirective> list = docService.byContext(context);
		if (list.isEmpty()) {
			return "查無 context「" + context + "」。已知的 context:" + String.join(", ", docService.knownContexts());
		}
		int total = list.size();
		// 預設不截斷:截掉的很可能正好是呼叫端要找的那一條,比回應太長糟得多。
		List<NginxDirective> shown = (limit == null || limit <= 0 || limit >= total) ? list : list.subList(0, limit);
		// 有截斷就必須講清楚總數 —— 可以少給,但不能讓呼叫端以為手上這份是全部。
		String header = shown.size() < total
				? context + " 可用的指令共 " + total + " 條,以下列出前 " + shown.size() + " 條(省略 limit 參數可取得全部):\n"
				: context + " 可用的指令(" + total + " 條):\n";
		return header + shown.stream().map(this::brief).collect(Collectors.joining("\n"));
	}

	// 描述只講做得到的事:寫得比實作大是在製造錯誤的信任,寫得比實作小則是把已經做到的事
	// 藏起來,兩邊都要跟著實作改。開區塊那一行原本完全不檢查(見到行尾的 { 就推堆疊並跳過),
	// 現在 NginxConfChecker 會先比對這個區塊放在這一層合不合法,規格範例舉的「if 寫在 http 層」
	// 因此抓得到 —— 這句描述必須跟著改,否則就換成文件比實作小。
	@ToolMapping(description = "拿一段 nginx 設定對照官方文件檢查:指令是否存在、是否用在合法的 context。區塊「內」的指令與開區塊那一行本身都會檢查(http 層誤寫 if {、location 裡誤寫 server { 這類 nginx 會拒絕啟動的錯位都會回報)。不檢查語法細節與參數值。只回報能確定的問題,沒有回報不等於設定正確。")
	public String nginx_check_config(@Param(description = "要檢查的 nginx 設定文字") String conf) {
		if (indexEmpty()) {
			return indexEmptyMessage();
		}
		if (isBlank(conf)) {
			// 空輸入回「未發現問題」會被讀成「這份設定沒問題」,但根本沒有東西被檢查過。
			return "請提供要檢查的 nginx 設定文字。";
		}
		List<String> problems;
		try {
			problems = NginxConfChecker.check(conf, docService);
		} catch (RuntimeException e) {
			// 五個工具裡只有這個要剖析任意使用者文字,是唯一真的可能爆掉的地方。
			// 讓例外飛到協定層,AI 只會收到一個沒有內容的失敗;寧可誠實說「檢查失敗」。
			logger.warn("nginx_check_config 檢查失敗", e);
			return "檢查過程發生錯誤,無法完成檢查:" + e;
		}
		// 檢查器刻意「寧可漏不可誤」(第三方區塊與資料區塊整層靜音、{ 後面同行還有內容時該層
		// 轉靜音、跨行指令跳過、大小寫錯的指令名被形狀過濾吃掉),所以無問題不等於設定正確,
		// 措辭不能寫成保證。
		//
		// 有問題時同樣要附但書,而且理由更硬:context 是逐行追蹤大括號推得的,追蹤一旦偏掉,
		// 後面每一行都會被算在錯的那一層。
		//
		// 但書講的是**一整類**情形,不是列舉。列舉過的寫法一旦修好、沒列到的寫法又還沒修,
		// 但書就從「提醒」變成「背書」:讀者逐條核對、發現自己那行都不符合,反而更相信那個
		// 誤報 —— 而一個被相信的誤報比沒寫但書更糟。
		//
		// 這裡的類別是「} 沒有自己獨佔一行」。checker 逐個吃掉行首的 } 之後,} }、}}、
		// } location /b { 都判得對了,但行尾的 }(listen 80; })仍然沒被看見 —— 所以但書
		// 不能收斂成剩下那一種寫法,只能講整類,並給一個涵蓋整類的驗證方式:把每個 } 拆成
		// 獨立一行再跑一次,兩次結果一樣才代表括號追蹤沒有偏掉。
		return problems.isEmpty()
				? "未發現可確定的問題(此檢查只回報能確定的錯誤,不代表設定完全正確)。"
				: "以下判斷以逐行括號追蹤推得;只要有 } 與其他內容寫在同一行,或大括號不平衡,context 就可能判斷錯誤。"
						+ "把每個 } 單獨成行後再檢查一次,兩次結果一致才代表判斷可信。\n"
						+ String.join("\n", problems);
	}

	/**
	 * 一行摘要,用於清單型輸出。
	 *
	 * 沒有語法就只印名字:摘要頁來源的指令有 10 條 syntax 是空的,照印會留下一個懸空的破折號。
	 */
	private String brief(NginxDirective d) {
		return d.syntax().isBlank() ? "- " + d.name() : "- " + d.name() + " — " + d.syntax();
	}

	private String format(NginxDirective d) {
		StringBuilder sb = new StringBuilder();
		sb.append("指令:").append(d.name())
				.append("\n語法:").append(orUnlisted(d.syntax()))
				.append("\n預設值:").append(defaultValueOf(d))
				.append("\n可用 context:").append(orUnlisted(String.join(", ", d.contexts())))
				.append("\n模組:").append(orUnlisted(d.module()));
		if (!d.description().isBlank()) {
			sb.append("\n說明:").append(d.description());
		}
		sb.append("\n官方文件:").append(d.sourceUrl());
		if (d.origin() == NginxDirective.Origin.PROJECT_SUMMARY) {
			// 兩種來源的完整度差很多,不標明的話 AI 無從判斷「沒寫」是文件缺漏還是真的沒有。
			sb.append("\n來源說明:本條取自本專案的 zh-TW 摘要頁,不是 nginx.org 的原始指令表格,欄位可能不完整。");
		}
		return sb.toString();
	}

	/**
	 * defaultValue 為 null 的意思由 origin 決定。
	 *
	 * 官方表格的 null 是 Default 欄印「—」,確實沒有預設值;摘要頁沒有 Default 欄,null 只
	 * 代表這頁沒列出。兩者都講成「無」,會讓 MCP 對 limit_req_status 回答「預設值:無」——
	 * 它實際預設 503。對 AI 講錯 nginx 語意,正是這整個功能要防的事。
	 */
	private String defaultValueOf(NginxDirective d) {
		if (d.defaultValue() != null) {
			return d.defaultValue();
		}
		return d.origin() == NginxDirective.Origin.OFFICIAL_TABLE
				? "無(官方文件標示無預設值)"
				: UNLISTED;
	}

	private String orUnlisted(String value) {
		return value == null || value.isBlank() ? UNLISTED : value;
	}

	/**
	 * 參數是空的時候要明講,不能讓它一路流進查詢 —— "查無指令 " 這種回應會讓模型以為
	 * 自己查的東西真的不存在,而不是自己沒帶參數。
	 *
	 * 實際會走到這裡的只有空字串與全空白。必填參數缺漏或送 null 都到不了方法裡:
	 * solon-ai-mcp 在呼叫前就擋下來,直接回 {@code isError:true} 與
	 * {@code Missing required parameter 'name'}。
	 *
	 * null 分支仍然留著。它不花成本,而且不依賴上游那道擋:框架換版本或改行為時,
	 * 這裡的最差情況是多一個永遠為 false 的判斷,不是回一句「查無指令 null」。
	 */
	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	/**
	 * 索引沒載入時要說清楚,不能讓查詢靜靜地回「查無此指令」——那等於告訴 AI
	 * 「nginx 沒有 proxy_pass 這個指令」,比拒答還糟。
	 */
	private boolean indexEmpty() {
		return docService.size() == 0;
	}

	private String indexEmptyMessage() {
		return "nginx 文件索引尚未載入,目前無法查詢(這是伺服器端的問題,不代表查詢的指令不存在)。請檢查伺服器啟動日誌。";
	}
}
