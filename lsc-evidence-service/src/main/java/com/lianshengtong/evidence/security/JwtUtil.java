package com.lianshengtong.evidence.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public class JwtUtil {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final byte[] secretKey;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtUtil(String secret, long accessExpirationMs, long refreshExpirationMs) {
        this.secretKey = secret.getBytes(StandardCharsets.UTF_8);
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public JwtUtil(String secret, long expirationMs) {
        this(secret, expirationMs, expirationMs);
    }

    /**
     * 生成 Access Token (短期, 2小时)
     */
    public String generateToken(String username, String role) {
        return generateToken(username, role, "access", accessExpirationMs);
    }

    /**
     * 生成 Refresh Token (长期, 7天)
     */
    public String generateRefreshToken(String username, String role) {
        return generateToken(username, role, "refresh", refreshExpirationMs);
    }

    private String generateToken(String username, String role, String tokenType, long expirationMs) {
        long now = System.currentTimeMillis();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", username);
        payload.put("role", role);
        payload.put("type", tokenType);
        payload.put("iat", now);
        payload.put("exp", now + expirationMs);

        String headerB64 = base64UrlEncode(HEADER.getBytes(StandardCharsets.UTF_8));
        String payloadJson = serializePayload(payload);
        String payloadB64 = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;
        String signature = sign(signingInput);
        return signingInput + "." + signature;
    }

    /**
     * 验证 Token (不过滤 type，仅验签和过期)
     */
    public Claims validateToken(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        String signingInput = parts[0] + "." + parts[1];
        String expectedSig = sign(signingInput);
        if (!MessageDigest.isEqual(expectedSig.getBytes(StandardCharsets.UTF_8),
                parts[2].getBytes(StandardCharsets.UTF_8))) return null;

        try {
            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            Map<String, Object> claims = objectMapper.readValue(payloadJson,
                    new TypeReference<Map<String, Object>>() {});

            Long exp = claims.get("exp") instanceof Number n ? n.longValue() : null;
            if (exp == null) return null; // S5-fix: 缺少 exp 的 token 视为无效
            if (exp < System.currentTimeMillis()) return null;

            String type = (String) claims.getOrDefault("type", "access");

            return new Claims(
                    (String) claims.get("sub"),
                    (String) claims.get("role"),
                    type,
                    exp != null ? exp : 0L
            );
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 验证 Refresh Token (必须是 refresh 类型)
     */
    public Claims validateRefreshToken(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return null;
        if (!"refresh".equals(claims.type())) return null;
        return claims;
    }

    /**
     * 计算 Token 的唯一标识 (JTI) - 用于黑名单 key
     * <p>
     * 使用 SHA-256 哈希整个 token 字符串作为唯一标识，
     * 避免在黑名单中存储原始 token。
     *
     * @param token JWT token
     * @return 64 位十六进制字符串 (SHA-256)
     */
    public String tokenJti(String token) {
        if (token == null || token.isBlank()) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 退化为 token 的 hashcode
            return Integer.toHexString(token.hashCode());
        }
    }

    /**
     * 解析 Token 中的 claims (不验签，仅解析 payload) - 仅用于调试/日志
     */
    private Map<String, Object> parsePayloadOnly(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return null;
            String payloadJson = new String(base64UrlDecode(parts[1]), StandardCharsets.UTF_8);
            return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secretKey, ALGORITHM));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(signature);
        } catch (Exception e) {
            throw new RuntimeException("JWT signing failed", e);
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("JWT payload serialization failed", e);
        }
    }

    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] base64UrlDecode(String data) {
        return Base64.getUrlDecoder().decode(data);
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return refreshExpirationMs;
    }

    public record Claims(String username, String role, String type, long expiration) {}
}
