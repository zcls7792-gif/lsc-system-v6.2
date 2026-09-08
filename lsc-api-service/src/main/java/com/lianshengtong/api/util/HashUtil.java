package com.lianshengtong.api.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;

/**
 * SHA-256 哈希序列化工具（V6.2 附录 P0-5 + 第十五章存证体系）
 * 统一 SHA-256 哈希序列化格式：
 * 1. 字段按字典序（key 自然序）排列
 * 2. 拼接格式：key1=value1&key2=value2&...
 * 3. 对拼接字符串做 SHA-256，输出 64 位十六进制小写
 */
public final class HashUtil {

    private HashUtil() {}

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    /**
     * 对键值对 map 做统一序列化后计算 SHA-256
     */
    public static String sha256(Map<String, ?> data) {
        return sha256(serialize(data));
    }

    /**
     * 直接对字符串做 SHA-256
     */
    public static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    /**
     * 统一序列化：key 字典序 + key=value 拼接 + & 分隔
     */
    public static String serialize(Map<String, ?> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        // 字典序
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, ?> e : data.entrySet()) {
            if (e.getValue() != null) {
                sorted.put(e.getKey(), e.getValue());
            }
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (!first) sb.append('&');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    /**
     * 字节数组转十六进制小写
     */
    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[j++] = HEX[v >>> 4];
            out[j++] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    /**
     * 校验：原数据序列化哈希与给定哈希是否一致
     */
    public static boolean verify(Map<String, ?> data, String expectedHash) {
        String actual = sha256(data);
        return actual.equalsIgnoreCase(expectedHash);
    }
}
