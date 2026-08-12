package com.cym.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
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

	/** 已知的區塊指令;出現 name {  時推入這個 context。 */
	private static final List<String> BLOCKS = List.of(
			"http", "server", "location", "upstream", "stream", "events", "mail", "map", "geo",
			"types", "limit_except", "if", "charset_map", "split_clients");

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
			String line = lines[i].trim();
			int lineNo = i + 1;

			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			if (line.equals("}")) {
				stack.pollLast();
				continue;
			}

			String first = line.split("[\\s{;]", 2)[0];

			if (line.endsWith("{")) {
				stack.addLast(BLOCKS.contains(first) ? first : "unknown");
				continue;
			}
			if (!line.endsWith(";")) {
				continue; // 跨行指令,無法確定 → 不報
			}

			List<NginxDirective> defs = svc.directive(first);
			if (defs.isEmpty()) {
				List<String> hints = svc.suggest(first);
				if (!hints.isEmpty()) {
					problems.add("第 " + lineNo + " 行: 未知指令 " + first + ",是否想寫 " + String.join(" / ", hints) + " ?");
				}
				continue; // 沒有候選就不報:可能是第三方模組的指令
			}

			String ctx = stack.isEmpty() ? "main" : stack.peekLast();
			if ("unknown".equals(ctx)) {
				continue; // 無法確定目前在哪一層 → 不報
			}

			// 同名指令可能跨模組（語料有 122 個這種名字）。只要任一個定義允許目前 context
			// 就算合法 —— 否則 stream 設定裡的 proxy_pass 會被 http 版的定義誤判成錯誤。
			List<NginxDirective> known = defs.stream().filter(d -> !d.contexts().isEmpty()).toList();
			if (known.isEmpty()) {
				continue; // 沒有任何一份定義說得出 context → 不報
			}
			if (known.stream().noneMatch(d -> d.contexts().contains(ctx))) {
				NginxDirective first0 = known.get(0);
				String allowed = known.stream()
						.map(d -> d.module() + ": " + String.join(", ", d.contexts()))
						.collect(Collectors.joining(" / "));
				problems.add("第 " + lineNo + " 行: " + first + " 不能用在 " + ctx
						+ ",官方允許的 context 是 " + allowed + " — " + first0.sourceUrl());
			}
		}
		return problems;
	}
}
