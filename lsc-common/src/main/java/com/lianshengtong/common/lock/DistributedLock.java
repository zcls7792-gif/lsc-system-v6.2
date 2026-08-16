package com.lianshengtong.common.lock;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


/**
 * 分布式锁工具
 * 用于LSC账务操作的并发控制
 * 防止同一用户并发操作导致余额不一致
 */
@Component
public class DistributedLock {

    @Autowired
    private RedissonClient redissonClient;

    private static final String LOCK_PREFIX = "lsc:lock:";
    private static final long DEFAULT_WAIT = 3L;
    private static final long DEFAULT_LEASE = 30L;

    /**
     * 获取锁并执行操作
     * @param lockKey 锁key
     * @param supplier 业务操作
     * @return 业务操作返回值
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        return executeWithLock(lockKey, DEFAULT_WAIT, DEFAULT_LEASE, supplier);
    }

    public <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(LOCK_PREFIX + lockKey);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new RuntimeException("获取分布式锁失败: " + lockKey);
            }
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("获取锁被中断: " + lockKey, e);
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 多用户锁(按userId排序防止死锁)
     * 用于B2B流转等双方操作场景
     */
    public <T> T executeWithMultiLock(Long userId1, Long userId2, Supplier<T> supplier) {
        // 按userId排序，确保加锁顺序一致
        long minId = Math.min(userId1, userId2);
        long maxId = Math.max(userId1, userId2);

        return executeWithLock("user:" + minId, 5L, 30L, () ->
            executeWithLock("user:" + maxId, 5L, 30L, supplier)
        );
    }

    /**
     * 无返回值版本
     */
    public void executeWithLock(String lockKey, Runnable runnable) {
        executeWithLock(lockKey, () -> {
            runnable.run();
            return null;
        });
    }


    public DistributedLock() {}

    public DistributedLock(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    public RedissonClient getRedissonClient() { return redissonClient; }
    public void setRedissonClient(RedissonClient redissonClient) { this.redissonClient = redissonClient; }
}
