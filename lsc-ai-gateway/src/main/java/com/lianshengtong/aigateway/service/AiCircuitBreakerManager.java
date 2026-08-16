package com.lianshengtong.aigateway.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * AI降级熔断管理器
 * <p>
 * 统一管理各AI能力的超时(默认10秒)、失败计数与熔断状态。
 * <ul>
 *   <li>超时10秒自动降级为人工审核模式，返回默认结果(AI_SUSPICIOUS)，不影响核心业务</li>
 *   <li>连续失败达到阈值({@code failure-threshold})触发熔断(OPEN)，期间直接返回降级结果</li>
 *   <li>熔断 {@code retry-after-seconds} 秒后进入HALF_OPEN，允许一次试探调用</li>
 *   <li>统计累计调用次数/成功次数/失败次数/降级次数/平均延迟，供监控使用</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
public class AiCircuitBreakerManager {

    @Value("${ai.gateway.model-timeout-ms:10000}")
    private long modelTimeoutMs;

    @Value("${ai.gateway.circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${ai.gateway.circuit-breaker.retry-after-seconds:60}")
    private long retryAfterSeconds;

    @Value("${ai.gateway.fallback-enabled:true}")
    private boolean fallbackEnabled;

    @Value("${ai.gateway.thread-pool.core-size:4}")
    private int corePoolSize;

    @Value("${ai.gateway.thread-pool.max-size:16}")
    private int maxPoolSize;

    @Value("${ai.gateway.thread-pool.queue-capacity:512}")
    private int queueCapacity;

    private ExecutorService executor;

    @PostConstruct
    public void init() {
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                r -> {
                    Thread t = new Thread(r, "ai-model-call");
                    t.setDaemon(true);
                    return t;
                },
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("[AiCircuitBreaker] init timeoutMs={} failureThreshold={} retryAfterSec={} fallback={} core={} max={} queue={}",
                modelTimeoutMs, failureThreshold, retryAfterSeconds, fallbackEnabled,
                corePoolSize, maxPoolSize, queueCapacity);
    }

    @PreDestroy
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    /**
     * 熔断器状态
     */
    private enum State { CLOSED, OPEN, HALF_OPEN }

    private static final class BreakerState {
        volatile State state = State.CLOSED;
        volatile int failureCount = 0;
        /** 熔断打开时间戳(毫秒) */
        volatile long openedAt = 0L;
        /** 累计调用次数 */
        final AtomicLong totalCalls = new AtomicLong(0);
        /** 成功次数 */
        final AtomicLong successCount = new AtomicLong(0);
        /** 失败次数(超时/异常) */
        final AtomicLong failureCountStat = new AtomicLong(0);
        /** 降级次数(熔断直接返回) */
        final AtomicLong fallbackCount = new AtomicLong(0);
        /** 累计延迟(毫秒)，用于计算平均延迟 */
        final AtomicLong totalLatencyMs = new AtomicLong(0);
        /** 最近一次错误原因 */
        volatile String lastError = "";
    }

    private final Map<String, BreakerState> breakers = new ConcurrentHashMap<>();

    private BreakerState breaker(String capability) {
        return breakers.computeIfAbsent(capability, k -> new BreakerState());
    }

    /**
     * 判断指定AI能力是否处于熔断开启状态
     */
    public boolean isCircuitOpen(String capability) {
        BreakerState b = breaker(capability);
        if (b.state == State.OPEN) {
            // 熔断恢复期到达 -> HALF_OPEN 允许一次试探
            if (System.currentTimeMillis() - b.openedAt >= retryAfterSeconds * 1000L) {
                b.state = State.HALF_OPEN;
                log.warn("[AiCircuitBreaker] {} 进入HALF_OPEN，放行一次试探调用", capability);
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 带10秒超时与降级熔断的AI调用封装。
     *
     * @param capability       AI能力标识(用于独立熔断计数)
     * @param action           外部AI模型调用逻辑
     * @param fallbackSupplier 降级结果提供者(超时/熔断/异常时返回)
     * @param <T>              返回类型
     * @return AI结果或降级结果
     */
    public <T> T execute(String capability, Supplier<T> action, Supplier<T> fallbackSupplier) {
        BreakerState b = breaker(capability);
        b.totalCalls.incrementAndGet();
        // 熔断开启直接降级，不发起调用
        if (fallbackEnabled && isCircuitOpen(capability)) {
            b.fallbackCount.incrementAndGet();
            log.warn("[AiCircuitBreaker] {} 熔断开启，直接降级", capability);
            return fallbackSupplier.get();
        }
        long start = System.currentTimeMillis();
        CompletableFuture<T> future = CompletableFuture.supplyAsync(action, executor);
        try {
            T result = future.get(modelTimeoutMs, TimeUnit.MILLISECONDS);
            long elapsed = System.currentTimeMillis() - start;
            b.totalLatencyMs.addAndGet(elapsed);
            onSuccess(capability);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("[AiCircuitBreaker] {} 调用超时(>{}ms)，降级为人工审核模式", capability, modelTimeoutMs);
            return onFailure(capability, fallbackSupplier, "timeout");
        } catch (ExecutionException e) {
            String msg = e.getCause() == null ? e.getMessage() : e.getCause().getMessage();
            log.warn("[AiCircuitBreaker] {} 调用异常：{}", capability, msg);
            return onFailure(capability, fallbackSupplier, "exception:" + msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[AiCircuitBreaker] {} 调用被中断，降级", capability);
            return onFailure(capability, fallbackSupplier, "interrupted");
        }
    }

    private void onSuccess(String capability) {
        BreakerState b = breaker(capability);
        b.successCount.incrementAndGet();
        // HALF_OPEN试探成功 -> 关闭熔断
        if (b.state == State.HALF_OPEN) {
            b.state = State.CLOSED;
            b.failureCount = 0;
            log.info("[AiCircuitBreaker] {} HALF_OPEN试探成功，熔断关闭", capability);
            return;
        }
        b.failureCount = 0;
    }

    private <T> T onFailure(String capability, Supplier<T> fallbackSupplier, String reason) {
        BreakerState b = breaker(capability);
        b.failureCountStat.incrementAndGet();
        b.failureCount++;
        b.lastError = reason;
        if (b.state == State.HALF_OPEN || b.failureCount >= failureThreshold) {
            b.state = State.OPEN;
            b.openedAt = System.currentTimeMillis();
            log.error("[AiCircuitBreaker] {} 触发熔断(OPEN)，原因={} 失败次数={} 阈值={} 恢复期={}s",
                    capability, reason, b.failureCount, failureThreshold, retryAfterSeconds);
        }
        if (fallbackEnabled) {
            b.fallbackCount.incrementAndGet();
            return fallbackSupplier.get();
        }
        throw new com.lianshengtong.common.exception.BizException(
                "AI模型调用失败(" + capability + ")：" + reason);
    }

    /**
     * 获取某AI能力当前熔断状态(供健康检查/监控使用)
     */
    public String getState(String capability) {
        BreakerState b = breakers.get(capability);
        return b == null ? "CLOSED" : b.state.name();
    }

    /**
     * 获取所有AI能力的监控指标快照
     * <p>包含调用次数/成功/失败/降级/平均延迟/熔断状态/最近错误。</p>
     *
     * @return Map: capability -> metrics
     */
    public Map<String, Object> metrics() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, BreakerState> entry : breakers.entrySet()) {
            BreakerState b = entry.getValue();
            Map<String, Object> m = new LinkedHashMap<>();
            long total = b.totalCalls.get();
            long success = b.successCount.get();
            long failure = b.failureCountStat.get();
            long fallback = b.fallbackCount.get();
            long totalLatency = b.totalLatencyMs.get();
            m.put("state", b.state.name());
            m.put("totalCalls", total);
            m.put("successCount", success);
            m.put("failureCount", failure);
            m.put("fallbackCount", fallback);
            m.put("avgLatencyMs", total == 0 ? 0 : totalLatency / total);
            m.put("failureCountCurrent", b.failureCount);
            m.put("lastError", b.lastError);
            result.put(entry.getKey(), m);
        }
        return result;
    }

    /**
     * 重置指定AI能力的熔断状态(人工运维介入)
     *
     * @param capability AI能力标识
     * @return 是否成功重置
     */
    public boolean reset(String capability) {
        BreakerState b = breakers.get(capability);
        if (b == null) {
            return false;
        }
        b.state = State.CLOSED;
        b.failureCount = 0;
        b.openedAt = 0L;
        b.lastError = "";
        log.warn("[AiCircuitBreaker] {} 熔断状态被人工重置为CLOSED", capability);
        return true;
    }
}
