package com.lianshengtong.common.utils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256加解密工具
 * 用于用户敏感信息(身份证号、手机号等)加密存储
 * 密钥从环境变量LSC_AES_KEY读取，默认仅用于开发环境
 * <p>
 * 安全改进：
 * - 密钥从环境变量读取，不再硬编码
 * - IV随机生成并拼接到密文前缀，保证语义安全性
 * </p>
 */
public class AesEncryptUtil {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final String SECRET_KEY_ENV = "LSC_AES_KEY";
    private static final String DEFAULT_DEV_KEY = "LSC-V6.2-AI-AES-Dev-Key-OnlyForTesting!!";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int IV_LENGTH = 16;

    private static SecretKeySpec getKey() {
        try {
            String envKey = System.getenv(SECRET_KEY_ENV);
            String keySource = (envKey != null && !envKey.isBlank()) ? envKey : DEFAULT_DEV_KEY;
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(keySource.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES密钥生成失败", e);
        }
    }

    private static byte[] generateIv() {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    public static String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = generateIv();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, getKey(), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, IV_LENGTH, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    public static String decrypt(String cipherText) {
        if (cipherText == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(cipherText);
            if (decoded.length < IV_LENGTH) {
                throw new IllegalArgumentException("密文长度不足");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, IV_LENGTH);
            byte[] encrypted = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, getKey(), new IvParameterSpec(iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("AES解密失败", e);
        }
    }

    /**
     * 手机号脱敏: 138****5678
     */
    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    /**
     * 身份证号脱敏: 110***********1234
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) return idCard;
        int len = idCard.length();
        return idCard.substring(0, 3) + "*".repeat(len - 7) + idCard.substring(len - 4);
    }

    /**
     * 真实姓名脱敏: 张*三
     */
    public static String maskName(String name) {
        if (name == null || name.length() <= 1) return name;
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
