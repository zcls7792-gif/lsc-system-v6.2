package com.lianshengtong.evidence.security;

/**
 * Refresh Token 黑名单服务 - 支持主动撤销
 * <p>
 * 实现：
 * <ul>
 *   <li>{@link InMemoryTokenBlacklistService} - 单机内存实现 (默认，standalone 环境)</li>
 *   <li>{@link RedisTokenBlacklistService} - Redis 分布式实现 (生产环境，多副本共享)</li>
 * </ul>
 * <p>
 * 设计：
 * <ul>
 *   <li>登出时调用 {@link #revoke(String, long)} 将 Refresh Token 加入黑名单</li>
 *   <li>刷新时调用 {@link #isRevoked(String)} 检查是否已被撤销</li>
 *   <li>TTL 与 Refresh Token 有效期一致，过期后自动清理</li>
 * </ul>
 */
public interface TokenBlacklistService {

    /**
     * 撤销一个 Token (加入黑名单)
     *
     * @param tokenJti     Token 的唯一标识 (建议使用 token 的 SHA-256 哈希，避免存原 Token)
     * @param expireMs     剩余有效期 (毫秒)，过期后自动清理
     */
    void revoke(String tokenJti, long expireMs);

    /**
     * 检查 Token 是否已被撤销
     *
     * @param tokenJti Token 的唯一标识
     * @return true 表示已被撤销，应拒绝使用
     */
    boolean isRevoked(String tokenJti);
}
