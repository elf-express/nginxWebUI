package com.cym.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * 代為讀取遠端節點的驗證碼圖片（新增／編輯遠端節點時使用）。
 * 只接受 http(s)、固定路徑 /adminPage/login/getRemoteCode、不含帳密的網址；
 * 目標解析後不得是 link-local、萬用位址或群播位址；不跟隨跳轉，只轉送 png／gif／jpeg 回應。
 */
public final class RemoteCaptchaFetcher {
	public static final String CAPTCHA_PATH = "/adminPage/login/getRemoteCode";
	private static final int TIMEOUT_MS = 3000;
	private static final int MAX_BYTES = 256 * 1024;
	private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/gif", "image/jpeg");

	private RemoteCaptchaFetcher() {
	}

	/** 讀到的圖片：回應的 Content-Type 與內容 */
	public record Image(String contentType, byte[] body) {
	}

	/** 讀取驗證碼圖片；網址或回應不合規時回傳 null */
	public static Image fetch(String url) {
		URI uri = validate(url);
		if (uri == null) {
			return null;
		}
		HttpURLConnection conn = null;
		try {
			conn = (HttpURLConnection) uri.toURL().openConnection();
			conn.setInstanceFollowRedirects(false);
			conn.setConnectTimeout(TIMEOUT_MS);
			conn.setReadTimeout(TIMEOUT_MS);
			conn.setUseCaches(false);
			if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
				return null;
			}
			String type = mediaType(conn.getContentType());
			if (!IMAGE_TYPES.contains(type)) {
				return null;
			}
			byte[] body = readLimited(conn.getInputStream());
			return body == null ? null : new Image(type, body);
		} catch (IOException | RuntimeException e) {
			return null;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/** 檢查網址；合規時回傳正規化後的 URI，否則回傳 null */
	static URI validate(String url) {
		if (url == null || url.isBlank()) {
			return null;
		}
		URI uri;
		try {
			uri = new URI(url.trim()).normalize();
		} catch (URISyntaxException e) {
			return null;
		}
		String scheme = uri.getScheme();
		if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
			return null;
		}
		if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null) {
			return null;
		}
		if (!CAPTCHA_PATH.equals(uri.getRawPath())) {
			return null;
		}
		try {
			for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
				if (address.isLinkLocalAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) {
					return null;
				}
			}
		} catch (IOException e) {
			return null;
		}
		return uri;
	}

	/** Content-Type 去掉參數、轉小寫；沒有時回傳空字串 */
	private static String mediaType(String contentType) {
		if (contentType == null) {
			return "";
		}
		int semicolon = contentType.indexOf(';');
		String type = semicolon >= 0 ? contentType.substring(0, semicolon) : contentType;
		return type.trim().toLowerCase(Locale.ROOT);
	}

	private static byte[] readLimited(InputStream in) throws IOException {
		try (InputStream is = in) {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[4096];
			int total = 0;
			int n;
			while ((n = is.read(buffer)) != -1) {
				total += n;
				if (total > MAX_BYTES) {
					return null;
				}
				out.write(buffer, 0, n);
			}
			return out.toByteArray();
		}
	}
}
