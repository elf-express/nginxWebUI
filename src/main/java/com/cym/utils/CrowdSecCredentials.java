package com.cym.utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import cn.hutool.core.util.StrUtil;

/**
 * CrowdSec 連線資訊：bouncer 金鑰先讀 Docker secrets 檔，其次環境變數；位址與 resolver 讀環境變數。
 */
public class CrowdSecCredentials {
	public static final String KEY_FILE_NAME = "crowdsec_bouncer_key";
	public static final String KEY_ENV = "CROWDSEC_BOUNCER_KEY";
	public static final String LAPI_URL_ENV = "CROWDSEC_LAPI_URL";
	public static final String APPSEC_URL_ENV = "CROWDSEC_APPSEC_URL";
	public static final String RESOLVER_ENV = "CROWDSEC_RESOLVER";
	public static final String DEFAULT_RESOLVER = "127.0.0.11";

	private static final String MASK = "••••••";

	public enum Source {
		SECRETS_FILE, ENV, NONE
	}

	public record Secret(String value, Source source) {
		/** value 非空白才算已設定 */
		public boolean isPresent() {
			return StrUtil.isNotBlank(value);
		}

		/** 遮罩顯示：6 個圓點加末 4 碼；長度 4 以內只顯示圓點；未設定回空字串 */
		public String masked() {
			if (!isPresent()) {
				return "";
			}
			if (value.length() <= 4) {
				return MASK;
			}
			return MASK + value.substring(value.length() - 4);
		}
	}

	private final Function<String, String> env;
	private final Path secretsDir;

	public CrowdSecCredentials(Function<String, String> env, Path secretsDir) {
		this.env = env;
		this.secretsDir = secretsDir;
	}

	public static CrowdSecCredentials system() {
		return new CrowdSecCredentials(System::getenv, Paths.get("/run/secrets"));
	}

	/** secrets 檔 crowdsec_bouncer_key（內容空白視為沒有）→ 環境變數 CROWDSEC_BOUNCER_KEY */
	public Secret bouncerKey() {
		if (secretsDir != null) {
			try {
				Path file = secretsDir.resolve(KEY_FILE_NAME);
				if (Files.isRegularFile(file)) {
					String text = Files.readString(file, StandardCharsets.UTF_8).trim();
					if (!text.isEmpty()) {
						return new Secret(text, Source.SECRETS_FILE);
					}
				}
			} catch (Exception e) {
				// 讀不到就退回環境變數
			}
		}
		String fromEnv = trimmed(KEY_ENV);
		if (fromEnv != null) {
			return new Secret(fromEnv, Source.ENV);
		}
		return new Secret("", Source.NONE);
	}

	/** LAPI 位址，去尾端 /，未設定回 null */
	public String lapiUrl() {
		return url(LAPI_URL_ENV);
	}

	/** AppSec 位址，去尾端 /，未設定回 null */
	public String appsecUrl() {
		return url(APPSEC_URL_ENV);
	}

	/** nginx resolver 位址，預設 Docker 內建 DNS */
	public String resolver() {
		String value = trimmed(RESOLVER_ENV);
		return value == null ? DEFAULT_RESOLVER : value;
	}

	private String url(String name) {
		String value = trimmed(name);
		if (value == null) {
			return null;
		}
		while (value.endsWith("/")) {
			value = value.substring(0, value.length() - 1);
		}
		return value.isEmpty() ? null : value;
	}

	private String trimmed(String name) {
		String value = env.apply(name);
		if (value == null) {
			return null;
		}
		value = value.trim();
		return value.isEmpty() ? null : value;
	}
}
