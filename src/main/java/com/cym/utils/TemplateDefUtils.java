package com.cym.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.cym.model.Param;

import cn.hutool.core.util.StrUtil;

/**
 * 參數模板「自動套用層級」：多選、小寫、逗號分隔存入 {@code Template.def}。
 * <p>
 * 合法值：http / server / server1 / server2 / stream / location / upstream
 * <p>
 * 另依參數指令做<strong>最小安全過濾</strong>：明顯不存在的層級從可選集剔除（UI 唯讀禁用 + 存檔再濾）。
 * 完整 directive→context 矩陣可之後替換 {@link #allowedContexts(Iterable)} 實作。
 */
public final class TemplateDefUtils {

	/** 固定順序（UI tag / 正規化輸出） */
	public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
			"http", "server", "server1", "server2", "stream", "location", "upstream"));

	/** HTTP 站點棧（含 location / upstream）— stream L4 無這些語意 */
	public static final List<String> HTTP_STACK = Collections.unmodifiableList(
			Arrays.asList("http", "server", "location", "upstream"));

	/** stream 棧（全域 + TCP/UDP server） */
	public static final List<String> STREAM_STACK = Collections.unmodifiableList(
			Arrays.asList("stream", "server1", "server2"));

	private static final Set<String> ALLOWED = new LinkedHashSet<>(ALL);

	/**
	 * 幾乎只在 HTTP 有意義的指令名（小寫）。出現任一時禁用 stream / server1 / server2。
	 * 保守清單：寧可多禁一點，避免 if/add_header 再進 stream。
	 */
	private static final Set<String> HTTP_ONLY_NAMES = setOf(
			"if",
			"add_header",
			"more_set_headers",
			"more_clear_headers",
			"more_set_input_headers",
			"auth_request",
			"auth_request_set",
			"auth_basic",
			"auth_basic_user_file",
			"auth_jwt",
			"auth_jwt_key_file",
			"root",
			"alias",
			"index",
			"try_files",
			"rewrite",
			"return", // stream 有 return 但語意不同；模板若與 if 混用多為 HTTP
			"error_page",
			"proxy_set_header",
			"proxy_hide_header",
			"proxy_pass_header",
			"fastcgi_pass",
			"fastcgi_param",
			"uwsgi_pass",
			"scgi_pass",
			"grpc_pass",
			"limit_req",
			"limit_req_zone",
			"limit_req_status",
			"limit_req_dry_run",
			"limit_req_log_level",
			"client_max_body_size",
			"client_body_buffer_size",
			"expires",
			"etag",
			"gzip",
			"brotli",
			"ssi",
			"charset",
			"types",
			"default_type",
			"sendfile",
			"tcp_nopush",
			"keepalive_timeout",
			"lingering_close",
			"open_file_cache",
			"stub_status",
			"sub_filter",
			"addition_types",
			"image_filter",
			"mp4",
			"flv",
			"hls",
			"dav_methods",
			"create_full_put_path",
			"min_delete_depth",
			"internal",
			"mirror",
			"slice",
			"http2",
			"http3",
			"quic");

	/**
	 * 幾乎只在 stream 有意義的指令名。出現任一時禁用 http / server / location / upstream。
	 */
	private static final Set<String> STREAM_ONLY_NAMES = setOf(
			"ssl_preread",
			"proxy_protocol_timeout",
			"proxy_responses",
			"proxy_requests",
			"proxy_socket_keepalive",
			"js_access",
			"js_preread",
			"js_filter",
			"preread_buffer_size",
			"preread_timeout",
			"udp_requests",
			"udp_responses");

	private TemplateDefUtils() {
	}

	/** 是否包含指定層級（大小寫不敏感；多值逗號/空白分隔） */
	public static boolean contains(String def, String type) {
		if (StrUtil.isEmpty(def) || StrUtil.isEmpty(type)) {
			return false;
		}
		String want = type.trim().toLowerCase(Locale.ROOT);
		for (String part : split(def)) {
			if (want.equals(part)) {
				return true;
			}
		}
		return false;
	}

	/** 解析為小寫合法 token 列表（去重、固定順序） */
	public static List<String> parse(String def) {
		if (StrUtil.isEmpty(def)) {
			return Collections.emptyList();
		}
		LinkedHashSet<String> picked = new LinkedHashSet<>();
		for (String part : split(def)) {
			if (ALLOWED.contains(part)) {
				picked.add(part);
			}
		}
		List<String> ordered = new ArrayList<>();
		for (String key : ALL) {
			if (picked.contains(key)) {
				ordered.add(key);
			}
		}
		return ordered;
	}

	/** 正規化後寫回 DB：小寫、逗號分隔、固定序；空 → "" */
	public static String normalize(String def) {
		List<String> list = parse(def);
		if (list.isEmpty()) {
			return "";
		}
		return String.join(",", list);
	}

	/** 從 checkbox 多值陣列正規化 */
	public static String normalizeFromList(List<String> values) {
		if (values == null || values.isEmpty()) {
			return "";
		}
		return normalize(String.join(",", values));
	}

	/**
	 * 依模板參數推算「允許自動套用」的層級（固定序）。
	 * 無參數或無法判斷時回傳全部（進可攻）；有 HTTP-only / stream-only 衝突時收斂。
	 */
	public static List<String> allowedContexts(Iterable<Param> params) {
		boolean hasHttpOnly = false;
		boolean hasStreamOnly = false;
		if (params != null) {
			for (Param p : params) {
				if (p == null || StrUtil.isEmpty(p.getName())) {
					continue;
				}
				String n = p.getName().trim().toLowerCase(Locale.ROOT);
				// 區塊名可能寫成 "if (" 殘片
				if (n.startsWith("if")) {
					n = "if";
				}
				if (HTTP_ONLY_NAMES.contains(n)) {
					hasHttpOnly = true;
				}
				if (STREAM_ONLY_NAMES.contains(n)) {
					hasStreamOnly = true;
				}
			}
		}

		// 同時出現兩邊 → 極保守：全部禁用自動套用（只手動）
		if (hasHttpOnly && hasStreamOnly) {
			return Collections.emptyList();
		}
		if (hasHttpOnly) {
			return new ArrayList<>(HTTP_STACK);
		}
		if (hasStreamOnly) {
			return new ArrayList<>(STREAM_STACK);
		}
		return new ArrayList<>(ALL);
	}

	/** 從指令名列表推算（前端可送 names；後端亦可自 Param 取） */
	public static List<String> allowedContextsFromNames(Iterable<String> directiveNames) {
		List<Param> fake = new ArrayList<>();
		if (directiveNames != null) {
			for (String name : directiveNames) {
				if (StrUtil.isEmpty(name)) {
					continue;
				}
				Param p = new Param();
				p.setName(name);
				fake.add(p);
			}
		}
		return allowedContexts(fake);
	}

	/**
	 * 正規化 def 並剔除當前參數下不允許的層級（存檔必跑，不信前端）。
	 */
	public static String normalizeAndFilter(String def, Iterable<Param> params) {
		List<String> wanted = parse(def);
		if (wanted.isEmpty()) {
			return "";
		}
		Set<String> allowed = new HashSet<>(allowedContexts(params));
		List<String> kept = new ArrayList<>();
		for (String key : ALL) {
			if (wanted.contains(key) && allowed.contains(key)) {
				kept.add(key);
			}
		}
		if (kept.isEmpty()) {
			return "";
		}
		return String.join(",", kept);
	}

	public static boolean isContextAllowed(String context, Iterable<Param> params) {
		if (StrUtil.isEmpty(context)) {
			return false;
		}
		return allowedContexts(params).contains(context.trim().toLowerCase(Locale.ROOT));
	}

	private static String[] split(String def) {
		return def.split("[,;\\s]+");
	}

	private static Set<String> setOf(String... items) {
		Set<String> s = new HashSet<>();
		Collections.addAll(s, items);
		return Collections.unmodifiableSet(s);
	}
}
