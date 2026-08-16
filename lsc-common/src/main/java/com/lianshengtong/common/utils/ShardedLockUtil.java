package com.lianshengtong.common.utils;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * 分片分布式锁工具
 * <p>
 * 将高竞争的单一锁拆分为 N 个分片锁，降低锁竞争。
 * 分片数通过构造参数配置，使用 hashCode &amp; (N-1) 做路由。
 * </p>
 */
@Component
public class ShardedLockUtil {

    private static final Logger log = LoggerFactory.getLogger(ShardedLockUtil.class);

    private final RedissonClient redissonClient;
    private final int shardCount;
    private final long defaultWaitMs;
    private final long defaultLeaseMs;

    public ShardedLockUtil(RedissonClient redissonClient,
                           @Value("${lsc.sharded-lock.shards:16}") int shardCount,
                           @Value("${lsc.sharded-lock.wait-ms:3000}") long defaultWaitMs,
                           @Value("${lsc.sharded-lock.lease-ms:10000}") long defaultLeaseMs) {
        this.redissonClient = redissonClient;
        this.shardCount = shardCount;
        this.defaultWaitMs = defaultWaitMs;
        this.defaultLeaseMs = defaultLeaseMs;
    }

    /**
     * 尝试获取分片锁，返回锁对象或 null（调用方必须检查 null）
     * <p>
     * 分片键 = lockKey + ":shard:" + Math.abs(identifier.hashCode()) & (shardCount - 1)
     * </p>
     *
     * @param lockKey   业务锁前缀
     * @param identifier 分片标识符（如 orderNo、userId）
     * @return 获取到的 RLock，或 null 表示获取失败
     * @throws InterruptedException 线程被中断
     */
    public RLock tryShardedLock(String lockKey, String identifier) throws InterruptedException {
        return tryShardedLock(lockKey, identifier, defaultWaitMs, defaultLeaseMs);
    }

    /**
     * 尝试获取分片锁（自定义等待和租约时间）
     */
    public RLock tryShardedLock(String lockKey, String identifier, long waitMs, long leaseMs) throws InterruptedException {
        int shard = resolveShard(identifier);
        String shardedKey = lockKey + ":shard:" + shard;
        RLock lock = redissonClient.getLock(shardedKey);
        if (lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS)) {
            log.debug("分片锁获取成功 key={} shard={} identifier={}", lockKey, shard, identifier);
            return lock;
        }
        log.warn("分片锁获取失败 key={} shard={} identifier={} waitMs={}", lockKey, shard, identifier, waitMs);
        return null;
    }

    /**
     * 解析分片编号（可用于监控分片分布）
     * <p>
     * 对 hashCode 做无符号处理，再用位与运算路由到分片。
     * 要求 shardCount 为 2 的幂（默认 16），位与运算比取模更快。
     * </p>
     */
    public int resolveShard(String identifier) {
        // hashCode 无符号右移避免 Integer.MIN_VALUE 变负数
        int hash = identifier.hashCode() & 0x7fffffff;
        return hash & (shardCount - 1);
    }

    public int getShardCount() {
        return shardCount;
    }





    public RedissonClient getRedissonClient() { return redissonClient; }
    public long getDefaultWaitMs() { return defaultWaitMs; }
    public long getDefaultLeaseMs() { return defaultLeaseMs; }
}