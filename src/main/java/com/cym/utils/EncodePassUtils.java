package com.cym.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;

public class EncodePassUtils {

	/** 舊版固定 salt，只用來驗證舊格式雜湊 */
	static final String LEGACY_SALT = "nginxWebUI";
	/** bcrypt cost */
	static final int BCRYPT_ROUNDS = 12;
	/** bcrypt 雜湊的開頭 */
	private static final String BCRYPT_PREFIX = "$2a$";

	private static volatile String dummyHash;

	/** 產生 bcrypt 雜湊；空字串或 null 原樣回傳 */
	public static String encode(String pass) {
		if (StrUtil.isEmpty(pass)) {
			return pass;
		}
		return BCrypt.hashpw(pass, BCrypt.gensalt(BCRYPT_ROUNDS));
	}

	/** 舊格式雜湊：md5(pass) + md5(LEGACY_SALT)；只供驗證與測試使用 */
	public static String encodeLegacy(String pass) {
		if (StrUtil.isEmpty(pass)) {
			return pass;
		}
		return SecureUtil.md5(pass) + SecureUtil.md5(LEGACY_SALT);
	}

	/** 比對明文與已存雜湊（bcrypt 或舊格式）；任一邊為空或雜湊格式損壞時回傳 false */
	public static boolean matches(String rawPass, String storedHash) {
		if (StrUtil.isEmpty(rawPass) || StrUtil.isEmpty(storedHash)) {
			return false;
		}
		if (isBcrypt(storedHash)) {
			try {
				return BCrypt.checkpw(rawPass, storedHash);
			} catch (Exception e) {
				return false;
			}
		}
		return MessageDigest.isEqual(encodeLegacy(rawPass).getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
	}

	/** 已存雜湊不是 bcrypt（且非空）時回傳 true */
	public static boolean needsUpgrade(String storedHash) {
		return StrUtil.isNotEmpty(storedHash) && !isBcrypt(storedHash);
	}

	/** 固定的 bcrypt 假雜湊（第一次呼叫時產生並快取），帳號不存在時拿來跑一次比對 */
	public static String dummyHash() {
		if (dummyHash == null) {
			dummyHash = encode("nginxWebUI-dummy-password");
		}
		return dummyHash;
	}

	private static boolean isBcrypt(String hash) {
		return hash.startsWith(BCRYPT_PREFIX);
	}

}
