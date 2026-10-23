package com.cym.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.function.Function;

import com.cym.utils.CrowdSecCredentials.Secret;
import com.cym.utils.CrowdSecCredentials.Source;

import cn.hutool.core.util.HexUtil;

/**
 * 驗證頁的兩把金鑰。
 * cookie 金鑰（簽 nwu_pass 與內建題目，只在本機）：secrets 檔 → 環境變數 → 自動產生並存檔（POSIX 權限 600）。
 * 交接票金鑰（與外部驗證站共用）：secrets 檔 → 環境變數，不自動產生。
 * 含換行的值視為沒有設定（設定檔一行一個值）。
 */
public class ChallengeSecrets {
	public static final String COOKIE_FILE_NAME = "nwu_challenge_secret";
	public static final String COOKIE_ENV = "NWU_CHALLENGE_SECRET";
	public static final String TICKET_FILE_NAME = "nwu_challenge_ticket_secret";
	public static final String TICKET_ENV = "NWU_CHALLENGE_TICKET_SECRET";

	private final Function<String, String> env;
	private final Path secretsDir;
	private final Path generatedFile;

	public ChallengeSecrets(Function<String, String> env, Path secretsDir, Path generatedFile) {
		this.env = env;
		this.secretsDir = secretsDir;
		this.generatedFile = generatedFile;
	}

	/** home 為 nginxWebUI 資料目錄（結尾有 /） */
	public static ChallengeSecrets system(String home) {
		return new ChallengeSecrets(System::getenv, Paths.get(CrowdSecCredentials.SECRETS_DIR),
				Paths.get(home + "crowdsec/challenge_cookie_secret"));
	}

	/** 畫面顯示用，不產生檔案：自動產生的檔案還不存在時回 NONE */
	public Secret peekCookieSecret() {
		Secret secret = read(COOKIE_FILE_NAME, COOKIE_ENV);
		if (secret.isPresent()) {
			return secret;
		}
		String generated = readGenerated();
		return generated == null ? new Secret("", Source.NONE) : new Secret(generated, Source.GENERATED);
	}

	/** 套用設定用：沒有 secrets 檔與環境變數時讀自動產生的檔案，檔案不存在就產生 */
	public Secret cookieSecret() throws IOException {
		Secret secret = peekCookieSecret();
		if (secret.isPresent()) {
			return secret;
		}
		return new Secret(generate(), Source.GENERATED);
	}

	public Secret ticketSecret() {
		return read(TICKET_FILE_NAME, TICKET_ENV);
	}

	private Secret read(String fileName, String envName) {
		if (secretsDir != null) {
			try {
				Path file = secretsDir.resolve(fileName);
				if (Files.isRegularFile(file)) {
					String text = Files.readString(file, StandardCharsets.UTF_8).trim();
					if (usable(text)) {
						return new Secret(text, Source.SECRETS_FILE);
					}
				}
			} catch (IOException | RuntimeException e) {
				// 讀不到就退回環境變數
			}
		}
		String value = env.apply(envName);
		value = value == null ? "" : value.trim();
		if (usable(value)) {
			return new Secret(value, Source.ENV);
		}
		return new Secret("", Source.NONE);
	}

	private static boolean usable(String value) {
		return !value.isEmpty() && value.indexOf('\n') < 0 && value.indexOf('\r') < 0;
	}

	private String readGenerated() {
		try {
			if (Files.isRegularFile(generatedFile)) {
				String text = Files.readString(generatedFile, StandardCharsets.UTF_8).trim();
				return usable(text) ? text : null;
			}
		} catch (IOException | RuntimeException e) {
			// 當作還沒產生
		}
		return null;
	}

	private String generate() throws IOException {
		byte[] bytes = new byte[32];
		new SecureRandom().nextBytes(bytes);
		String value = HexUtil.encodeHexStr(bytes);
		Files.createDirectories(generatedFile.getParent());
		Files.deleteIfExists(generatedFile);
		if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			Files.createFile(generatedFile,
					PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
		} else {
			Files.createFile(generatedFile);
		}
		Files.writeString(generatedFile, value, StandardCharsets.UTF_8);
		return value;
	}
}
