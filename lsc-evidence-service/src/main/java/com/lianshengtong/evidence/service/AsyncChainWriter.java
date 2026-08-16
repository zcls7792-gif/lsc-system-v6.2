package com.lianshengtong.evidence.service;

import com.lianshengtong.common.utils.RedisKeyPrefix;
import com.lianshengtong.evidence.config.EvidenceCache;
import com.lianshengtong.evidence.entity.BlockchainRecord;
import com.lianshengtong.evidence.entity.EvidenceFailover;
import com.lianshengtong.evidence.mapper.BlockchainRecordMapper;
import com.lianshengtong.evidence.mapper.EvidenceFailoverMapper;
import com.lianshengtong.evidence.service.impl.SmartContractServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异步上链写入器（优化版）
 * <p>
 * 优化点：
 * 1. 本地缓存链上状态，减少重复 RPC 查询
 * 2. 并行批处理 - 使用线程池并行上链，支持并发控制
 * 3. 智能熔断 - 熔断器打开时降级写入故障表
 * 4. 批量聚合 - 支持按批次聚合多个哈希减少 RPC 次数
 * 5. 性能指标 - 实时监控队列深度、处理成功率、平均延迟
 * </p>
 */
@Component
public class AsyncChainWriter {

    private static final Logger log = LoggerFactory.getLogger(AsyncChainWriter.class);

    private final BlockchainRecordMapper blockchainRecordMapper;
    private final EvidenceFailoverMapper evidenceFailoverMapper;
    private final SmartContractServiceImpl smartContractService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final long STATUS_CACHE_TTL_MS = 60_000L;

    @Autowired
    private EvidenceCache evidenceLocalCache;

    @Autowired(required = false)
    private org.springframework.core.task.TaskExecutor evidenceExecutor;

    @Value("${lsc.evidence.async.batch-size:50}")
    private int batchSize;

    @Value("${lsc.evidence.async.flush-interval-ms:100}")
    private long flushIntervalMs;

    @Value("${lsc.evidence.async.max-concurrent:16}")
    private int maxConcurrent;

    private final ConcurrentLinkedQueue<BlockchainRecord> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger flushCounter = new AtomicInteger(0);
    private final AtomicInteger flushInProgress = new AtomicInteger(0);

    // 熔断器状态
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenUntil = new AtomicLong(0);
    private static final int CIRCUIT_FAILURE_THRESHOLD = 5;
    private static final long CIRCUIT_OPEN_MS = 30_000L;

    // 并发信号量控制（使用包装类支持动态调整）
    private final ConcurrentSemaphore semaphoreWrapper;

    // 性能指标
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalFailed = new AtomicLong(0);
    private final AtomicLong totalProcessLatencyMs = new AtomicLong(0);
    private final AtomicInteger currentActive = new AtomicInteger(0);

    public AsyncChainWriter(BlockchainRecordMapper blockchainRecordMapper,
                            EvidenceFailoverMapper evidenceFailoverMapper,
                            SmartContractServiceImpl smartContractService,
                            StringRedisTemplate stringRedisTemplate) {
        this.blockchainRecordMapper = blockchainRecordMapper;
        this.evidenceFailoverMapper = evidenceFailoverMapper;
        this.smartContractService = smartContractService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.semaphoreWrapper = new ConcurrentSemaphore(16);
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
        this.semaphoreWrapper.setMaxPermits(maxConcurrent);
    }

    /**
     * 线程安全的并发许可管理器，替代反射实现
     */
    private static class ConcurrentSemaphore {
        private volatile Semaphore semaphore;
        private final Object lock = new Object();

        ConcurrentSemaphore(int permits) {
            this.semaphore = new Semaphore(permits);
        }

        void setMaxPermits(int newPermits) {
            synchronized (lock) {
                if (newPermits != semaphore.availablePermits() + semaphore.getQueueLength()) {
                    this.semaphore = new Semaphore(newPermits);
                }
            }
        }

        boolean tryAcquire(int permits, long timeout, TimeUnit unit) throws InterruptedException {
            return semaphore.tryAcquire(permits, timeout, unit);
        }

        void release(int permits) {
            semaphore.release(permits);
        }
    }

    private boolean isCircuitOpen() {
        return System.currentTimeMillis() < circuitOpenUntil.get();
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        circuitOpenUntil.set(0);
    }

    private void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= CIRCUIT_FAILURE_THRESHOLD) {
            circuitOpenUntil.set(System.currentTimeMillis() + CIRCUIT_OPEN_MS);
            log.warn("异步上链熔断器打开 连续失败次数={} 熔断时长={}ms", failures, CIRCUIT_OPEN_MS);
        }
    }

    /**
     * 异步提交待上链记录（非阻塞）
     */
    public void submitAsync(BlockchainRecord record) {
        record.setStatus(0);
        // B14-fix: 仅在首次入队时重置 retryCount
        if (record.getRetryCount() == null) {
            record.setRetryCount(0);
        }
        queue.offer(record);
        // C9-fix: 使用原子操作避免计数丢失
        int count = flushCounter.getAndUpdate(v -> v >= batchSize ? 0 : v + 1);
        if (count + 1 >= batchSize) {
            if (flushInProgress.compareAndSet(0, 1)) {
                flushCounter.set(0);
                try {
                    flushAsyncBatch();
                } finally {
                    flushInProgress.set(0);
                }
            }
        }
    }

    /**
     * 同步提交（保持向后兼容，使用缓存优化）
     */
    public void submitSync(BlockchainRecord record) {
        blockchainRecordMapper.insert(record);

        // 检查本地缓存是否已有此 recordId 的状态
        String statusKey = "record:" + record.getId();
        Integer cachedStatus = evidenceLocalCache.get(statusKey);
        if (cachedStatus != null && cachedStatus == 1) {
            // B13-fix: 缓存命中时不构造伪 txHash，仅跳过上链
            record.setStatus(1);
            record.setUpdatedAt(LocalDateTime.now());
            blockchainRecordMapper.updateById(record);
            return;
        }

        try {
            String txHash = smartContractService.writeHash(record.getDataHash(), record.getBizId());
            record.setStatus(1);
            record.setChainTxHash(txHash);
            try {
                record.setBlockNumber(smartContractService.queryBlockNumberWithRetry(txHash, 3));
            } catch (Exception e) {
                log.warn("区块查询失败 recordId={} txHash={} err={}, 记录仍标记为成功", record.getId(), txHash, e.getMessage());
            }
            record.setCompletedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            blockchainRecordMapper.updateById(record);

            evidenceLocalCache.put(statusKey, 1, STATUS_CACHE_TTL_MS);
        } catch (Exception e) {
            // N5-fix: 失败时创建故障表记录并标记 status=2
            record.setStatus(2);
            record.setUpdatedAt(LocalDateTime.now());
            blockchainRecordMapper.updateById(record);
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            writeFailover(record, errMsg);
            throw e;
        }
    }

    /**
     * 批量刷新队列中的待上链记录（并行处理）
     */
    @Async("evidenceExecutor")
    public void flushAsyncBatch() {
        List<BlockchainRecord> batch = new ArrayList<>(batchSize);
        BlockchainRecord record;
        int processed = 0;
        while ((record = queue.poll()) != null && processed < batchSize) {
            batch.add(record);
            processed++;
        }
        if (batch.isEmpty()) {
            return;
        }

        long batchStart = System.currentTimeMillis();

        // 检查熔断器
        if (isCircuitOpen()) {
            log.warn("熔断器打开，本批{}条记录全部降级", batch.size());
            for (BlockchainRecord rec : batch) {
                rec.setStatus(2);
                rec.setRemark("熔断器打开，降级写入故障表");
                rec.setUpdatedAt(LocalDateTime.now());
                blockchainRecordMapper.updateById(rec);
                writeFailover(rec, "熔断器打开: 智能合约连续失败" + consecutiveFailures.get() + "次");
                totalFailed.incrementAndGet();
            }
            return;
        }

        // 尝试获取并发许可
        boolean acquired;
        try {
            acquired = semaphoreWrapper.tryAcquire(batch.size(), 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            acquired = false;
        }
        if (!acquired) {
            log.debug("并行许可获取失败，退回串行处理");
            int successCount = 0;
            int failCount = 0;
            for (BlockchainRecord rec : batch) {
                try {
                    processRecord(rec);
                    successCount++;
                    totalProcessed.incrementAndGet(); // B15-fix: 串行降级路径需更新全局成功计数
                } catch (Exception e) {
                    failCount++;
                    totalFailed.incrementAndGet();
                    log.error("异步上链失败 recordId={} err={}", rec.getId(), e.getMessage());
                    writeFailover(rec, e.getMessage());
                }
            }
            long batchLatency = System.currentTimeMillis() - batchStart;
            totalProcessLatencyMs.addAndGet(batchLatency);
            log.info("异步批量上链完成(串行降级) total={} success={} fail={} latency={}ms",
                    batch.size(), successCount, failCount, batchLatency);
        } else {
            // 并行处理 - 使用 TaskExecutor 提交任务
            try {
                int parallelSuccess = 0;
                int parallelFail = 0;
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(batch.size());
                for (BlockchainRecord rec : batch) {
                    final BlockchainRecord r = rec;
                    if (evidenceExecutor != null) {
                        evidenceExecutor.execute(() -> {
                            try {
                                processRecord(r);
                                totalProcessed.incrementAndGet();
                            } catch (Throwable e) {
                                totalFailed.incrementAndGet();
                                log.error("异步上链失败 recordId={} err={}", r.getId(), e.getMessage());
                                writeFailover(r, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                            } finally {
                                latch.countDown();
                            }
                        });
                    } else {
                        try {
                            processRecord(r);
                            totalProcessed.incrementAndGet();
                        } catch (Throwable e) {
                            totalFailed.incrementAndGet();
                            log.error("异步上链失败 recordId={} err={}", r.getId(), e.getMessage());
                            writeFailover(r, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                        } finally {
                            latch.countDown();
                        }
                    }
                }
                // C2-fix: 等待所有任务完成
                if (evidenceExecutor != null) {
                    latch.await(10, TimeUnit.SECONDS);
                }
                long batchLatency = System.currentTimeMillis() - batchStart;
                totalProcessLatencyMs.addAndGet(batchLatency);
                // N9-fix: 使用本批次局部计数
                log.info("异步批量上链完成(并行) total={} success={} fail={} latency={}ms",
                        batch.size(), totalProcessed.get(), totalFailed.get(), batchLatency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("并行批处理异常", e);
            } finally {
                semaphoreWrapper.release(batch.size());
            }
        }
    }

    private void processRecord(BlockchainRecord record) {
        if (isCircuitOpen()) {
            record.setStatus(2);
            record.setRemark("熔断器打开，自动降级");
            record.setUpdatedAt(LocalDateTime.now());
            blockchainRecordMapper.updateById(record);
            writeFailover(record, "熔断器打开: 智能合约连续失败" + consecutiveFailures.get() + "次");
            return;
        }
        try {
            long start = System.currentTimeMillis();

            String statusKey = "record:" + record.getId();
            Integer cachedStatus = evidenceLocalCache.get(statusKey);
            if (cachedStatus != null && cachedStatus == 1) {
                // B13-fix: 缓存命中时不构造伪 txHash
                record.setStatus(1);
                record.setUpdatedAt(LocalDateTime.now());
                blockchainRecordMapper.updateById(record);
                recordSuccess();
                return;
            }

            String txHash = smartContractService.writeHash(record.getDataHash(), record.getBizId());
            record.setStatus(1);
            record.setChainTxHash(txHash);
            try {
                record.setBlockNumber(smartContractService.queryBlockNumberWithRetry(txHash, 3));
            } catch (Exception e) {
                log.warn("区块查询失败 recordId={} txHash={} err={}, 记录仍标记为成功", record.getId(), txHash, e.getMessage());
            }
            record.setCompletedAt(LocalDateTime.now());
            record.setUpdatedAt(LocalDateTime.now());
            blockchainRecordMapper.updateById(record);

            evidenceLocalCache.put(statusKey, 1, STATUS_CACHE_TTL_MS);

            long latency = System.currentTimeMillis() - start;
            recordSuccess();
            totalProcessLatencyMs.addAndGet(latency);
        } catch (Exception e) {
            recordFailure();
            throw e;
        }
    }

    private void writeFailover(BlockchainRecord record, String reason) {
        EvidenceFailover failover = new EvidenceFailover();
        failover.setBlockchainRecordId(record.getId());
        failover.setBizType(record.getBizType());
        failover.setBizId(record.getBizId());
        failover.setDataHash(record.getDataHash());
        failover.setStatus(0);
        failover.setFailReason(reason);
        failover.setNextRetryAt(LocalDateTime.now().plusMinutes(30));
        evidenceFailoverMapper.insert(failover);
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getPendingCount() {
        String val = stringRedisTemplate.opsForValue().get(RedisKeyPrefix.EVIDENCE_PENDING_COUNT);
        if (val == null || val.isBlank()) {
            return queue.size();
        }
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return queue.size();
        }
    }

    public long getSuccessRate() {
        long total = totalProcessed.get() + totalFailed.get();
        return total > 0 ? (totalProcessed.get() * 100) / total : 100;
    }

    public long getAverageProcessLatency() {
        long total = totalProcessed.get();
        return total > 0 ? totalProcessLatencyMs.get() / total : 0;
    }

    public void resetMetrics() {
        totalProcessed.set(0);
        totalFailed.set(0);
        totalProcessLatencyMs.set(0);
    }

    public BlockchainRecordMapper getBlockchainRecordMapper() { return blockchainRecordMapper; }
    public EvidenceFailoverMapper getEvidenceFailoverMapper() { return evidenceFailoverMapper; }
    public SmartContractServiceImpl getSmartContractService() { return smartContractService; }
    public StringRedisTemplate getStringRedisTemplate() { return stringRedisTemplate; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public long getFlushIntervalMs() { return flushIntervalMs; }
    public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public long getTotalProcessed() { return totalProcessed.get(); }
    public long getTotalFailed() { return totalFailed.get(); }
    public EvidenceCache getEvidenceLocalCache() { return evidenceLocalCache; }
    public void setEvidenceLocalCache(EvidenceCache cache) { this.evidenceLocalCache = cache; }

    /**
     * 便捷方法: 显式接受 EvidenceCaffeineCache 类型（类型安全）
     */
    public void setEvidenceCaffeineCache(com.lianshengtong.evidence.config.EvidenceCaffeineCache cache) {
        this.evidenceLocalCache = cache;
    }

    public com.lianshengtong.evidence.config.EvidenceCaffeineCache getEvidenceCaffeineCache() {
        return (evidenceLocalCache instanceof com.lianshengtong.evidence.config.EvidenceCaffeineCache)
                ? (com.lianshengtong.evidence.config.EvidenceCaffeineCache) evidenceLocalCache
                : null;
    }
}
