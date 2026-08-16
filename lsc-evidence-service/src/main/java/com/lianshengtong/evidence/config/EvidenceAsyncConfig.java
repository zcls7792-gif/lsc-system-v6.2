package com.lianshengtong.evidence.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 存证异步处理配置（优化版）
 * <p>
 * 针对存证上链场景优化：
 * - 核心线程池扩展到 CPU 核数的 2 倍
 * - 队列容量增加到 5000 以支撑高并发
 * - 允许核心线程超时回收
 * </p>
 */
@Configuration
@EnableAsync
@EnableScheduling
public class EvidenceAsyncConfig {

    @Value("${lsc.evidence.async.core-pool-size:8}")
    private int corePoolSize;

    @Value("${lsc.evidence.async.max-pool-size:16}")
    private int maxPoolSize;

    @Value("${lsc.evidence.async.queue-capacity:5000}")
    private int queueCapacity;

    @Bean("evidenceExecutor")
    public Executor evidenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("evidence-async-");
        executor.setKeepAliveSeconds(60);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
