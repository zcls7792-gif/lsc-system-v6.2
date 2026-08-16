package com.lianshengtong.common.utils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 乐观锁辅助工具
 * <p>
 * 基于 MyBatis-Plus version 字段的乐观锁实现，
 * 适用于低冲突场景（如单账户高频读写），避免分布式锁开销。
 * </p>
 */
public final class OptimisticLockHelper {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockHelper.class);

    private OptimisticLockHelper() {}

    /**
     * 乐观锁执行器：在指定次数内重试，每次重试重新读取最新数据
     * 
     * @param operation 操作名称（用于日志）
     * @param maxRetries 最大重试次数
     * @param action 业务逻辑（返回更新影响行数，0 表示版本冲突）
     * @return 最终影响行数
     */
    public static int execute(String operation, int maxRetries, Supplier<Integer> action) {
        int retry = 0;
        while (true) {
            int rows = action.get();
            if (rows > 0) {
                return rows;
            }
            retry++;
            if (retry >= maxRetries) {
                log.warn("乐观锁重试耗尽 operation={} retries={}", operation, retry);
                throw new OptimisticLockingFailureException(
                        operation + " 乐观锁冲突超过最大重试次数(" + maxRetries + ")");
            }
            log.debug("乐观锁重试 operation={} attempt={}/{}", operation, retry, maxRetries);
            try {
                Thread.sleep(50L * retry);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OptimisticLockingFailureException(operation + " 乐观锁重试被中断");
            }
        }
    }

    /**
     * 乐观锁执行器（带默认重试次数 3）
     */
    public static int execute(String operation, Supplier<Integer> action) {
        return execute(operation, 3, action);
    }
}