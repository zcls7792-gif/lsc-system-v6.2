package com.zcls.lsc.common.idem;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 幂等键与请求摘要（13.1）。
 * 写请求带 Idempotency-Key 与 request_id；服务端存请求摘要。
 * 相同键相同请求返回已记录结果；相同键不同请求返回 409 并告警（4.4 / 7.2）。
 */
public final class IdempotencyKey {

    /** 业务幂等键（user_id + ":" + business_key，作为业务去重维度）。 */
    private final String businessKey;
    /** 客户端 Idempotency-Key。 */
    private final String idempotencyKey;
    /** 请求摘要（参数指纹），用于检测相同 key 不同请求。 */
    private final String requestHash;

    public IdempotencyKey(String businessKey, String idempotencyKey, String requestHash) {
        this.businessKey = Objects.requireNonNull(businessKey, "businessKey");
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public String businessKey() {
        return businessKey;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }

    /** 计算请求摘要（SHA-256，16 进制）。 */
    public static String hashOf(String payload) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IdempotencyKey that)) return false;
        return businessKey.equals(that.businessKey)
                && java.util.Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(businessKey, idempotencyKey);
    }

    @Override
    public String toString() {
        return "IdempotencyKey{b=" + businessKey + ", i=" + idempotencyKey + ", h=" + requestHash + "}";
    }
}
