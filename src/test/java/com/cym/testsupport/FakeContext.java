package com.cym.testsupport;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.noear.solon.core.handle.ContextEmpty;

/** 測試用的請求：可設定 path、header、參數與 session，並記下輸出與轉址（不經 Solon 啟動）。 */
public class FakeContext extends ContextEmpty {
	private final String path;
	private final Map<String, String> headers = new HashMap<>();
	private final Map<String, String> params = new HashMap<>();
	private final Map<String, Object> session = new HashMap<>();
	private final StringBuilder output = new StringBuilder();
	private String redirectUrl;

	public FakeContext(String path) {
		this.path = path;
		headers.put("Host", "localhost:8080");
	}

	public FakeContext header(String name, String value) {
		headers.put(name, value);
		return this;
	}

	public FakeContext param(String name, String value) {
		params.put(name, value);
		return this;
	}

	public FakeContext session(String name, Object value) {
		session.put(name, value);
		return this;
	}

	/** 已寫出的回應內容 */
	public String outputText() {
		return output.toString();
	}

	/** 轉址目標；沒有轉址時為 null */
	public String redirectUrl() {
		return redirectUrl;
	}

	@Override
	public String path() {
		return path;
	}

	@Override
	public String url() {
		return "http://localhost:8080" + path;
	}

	@Override
	public String header(String name) {
		return headers.get(name);
	}

	@Override
	public String param(String name) {
		return params.get(name);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T> T session(String name, Class<T> clz) {
		return (T) session.get(name);
	}

	@Override
	public void sessionSet(String name, Object val) {
		session.put(name, val);
	}

	@Override
	public void output(byte[] bytes) {
		output.append(new String(bytes, StandardCharsets.UTF_8));
	}

	@Override
	public void redirect(String url, int code) {
		redirectUrl = url;
	}
}
