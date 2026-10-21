package com.cym.utils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.util.StrUtil;

/**
 * CrowdSec 連線資訊：bouncer 金鑰與 machine 密碼先讀 Docker secrets 檔，其次環境變數；
 * 位址、machine 帳號與 resolver 讀環境變數。
 */
public class CrowdSecCredentials {
	public static final String SECRETS_DIR = "/run/secrets";
	public static final String KEY_FILE_NAME = "crowdsec_bouncer_key";
	public static final String KEY_ENV = "CROWDSEC_BOUNCER_KEY";
	public static final String MACHINE_PASSWORD_FILE_NAME = "crowdsec_machine_password";
	public static final String MACHINE_PASSWORD_ENV = "CROWDSEC_MACHINE_PASSWORD";
	public static final String MACHINE_ID_ENV = "CROWDSEC_MACHINE_ID";
	public static final String DEFAULT_MACHINE_ID = "nginxwebui";
	public static final String LAPI_URL_ENV = "CROWDSEC_LAPI_URL";
	public static final String APPSEC_URL_ENV = "CROWDSEC_APPSEC_URL";
	public static final String RESOLVER_ENV = "CROWDSEC_RESOLVER";
	public static final String DEFAULT_RESOLVER = "127.0.0.11";

	/** 舊版 docker/.env.example 內未註解的範例金鑰 */
	public static final String PLACEHOLDER_KEY = "your-bouncer-key-here";

	private static final Logger logger = LoggerFactory.getLogger(CrowdSecCredentials.class);
	private static final AtomicBoolean placeholderWarned = new AtomicBoolean(false);
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

		/** 避免自動產生的 toString 印出完整金鑰 */
		@Override
		public String toString() {
			return "Secret[" + masked() + ", " + source + "]";
		}
	}

	private final Function<String, String> env;
	private final Path secretsDir;

	public CrowdSecCredentials(Function<String, String> env, Path secretsDir) {
		this.env = env;
		this.secretsDir = secretsDir;
	}

	public static CrowdSecCredentials system() {
		return new CrowdSecCredentials(System::getenv, Paths.get(SECRETS_DIR));
	}

	/** secrets 檔在容器內的完整路徑（畫面提示用） */
	public static String secretFilePath(String fileName) {
		return SECRETS_DIR + "/" + fileName;
	}

	/** secrets 檔 crowdsec_bouncer_key（內容空白視為沒有）→ 環境變數 CROWDSEC_BOUNCER_KEY */
	public Secret bouncerKey() {
		return readSecret(KEY_FILE_NAME, KEY_ENV, true);
	}

	/** secrets 檔 crowdsec_machine_password（內容空白視為沒有）→ 環境變數 CROWDSEC_MACHINE_PASSWORD */
	public Secret machinePassword() {
		return readSecret(MACHINE_PASSWORD_FILE_NAME, MACHINE_PASSWORD_ENV, false);
	}

	/** machine 帳號，未設定時 nginxwebui */
	public String machineId() {
		String value = trimmed(MACHINE_ID_ENV);
		return value == null ? DEFAULT_MACHINE_ID : value;
	}

	private Secret readSecret(String fileName, String envName, boolean rejectPlaceholder) {
		if (secretsDir != null) {
			try {
				Path file = secretsDir.resolve(fileName);
				if (Files.isRegularFile(file)) {
					String text = Files.readString(file, StandardCharsets.UTF_8).trim();
					if (!text.isEmpty() && !(rejectPlaceholder && isPlaceholder(text))) {
						return new Secret(text, Source.SECRETS_FILE);
					}
				}
			} catch (Exception e) {
				// 讀不到就退回環境變數
			}
		}
		String fromEnv = trimmed(envName);
		if (fromEnv != null && !(rejectPlaceholder && isPlaceholder(fromEnv))) {
			return new Secret(fromEnv, Source.ENV);
		}
		return new Secret("", Source.NONE);
	}

	/** 舊版 docker/.env.example 的範例值視為未設定，並只警告一次（不含金鑰內容） */
	private static boolean isPlaceholder(String key) {
		if (!PLACEHOLDER_KEY.equals(key)) {
			return false;
		}
		if (placeholderWarned.compareAndSet(false, true)) {
			logger.warn("CROWDSEC_BOUNCER_KEY still has the placeholder value from the old .env.example; CrowdSec bouncer stays disabled");
		}
		return true;
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
