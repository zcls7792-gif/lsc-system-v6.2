package com.lianshengtong.evidence.security;

/**
 * 登录尝试跟踪服务 - 用于暴力破解防护
 * <p>
 * 提供：
 * <ul>
 *   <li>记录登录失败次数</li>
 *   <li>检查账户是否被锁定</li>
 *   <li>登录成功时清除失败计数</li>
 * </ul>
 * <p>
 * 实现类：
 * <ul>
 *   <li>{@link InMemoryLoginAttemptService} - 单机内存实现 (默认，standalone 环境)</li>
 *   <li>{@link RedisLoginAttemptService} - Redis 分布式实现 (生产环境，多副本共享)</li>
 * </ul>
 */
public interface LoginAttemptService {

    /**
     * 检查账户是否被锁定
     *
     * @param username 用户名
     * @return true 表示已锁定，应拒绝登录
     */
    boolean isLocked(String username);

    /**
     * 获取剩余锁定时间 (毫秒)
     *
     * @param username 用户名
     * @return 剩余锁定毫秒数，0 表示未锁定
     */
    long remainingLockMs(String username);

    /**
     * 记录一次登录失败
     *
     * @param username 用户名
     */
    void recordFailure(String username);

    /**
     * 登录成功时清除失败计数
     *
     * @param username 用户名
     */
    void recordSuccess(String username);
}
