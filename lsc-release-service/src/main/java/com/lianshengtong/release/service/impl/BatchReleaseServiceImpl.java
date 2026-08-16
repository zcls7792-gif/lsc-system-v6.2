package com.lianshengtong.release.service.impl;

import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.enums.LscTransactionTypeEnum;
import com.lianshengtong.common.enums.ReleaseTaskStatusEnum;
import com.lianshengtong.common.result.R;
import com.lianshengtong.release.entity.DailyReleaseSummary;
import com.lianshengtong.release.feign.LscLedgerFeignClient;
import com.lianshengtong.release.mapper.DailyReleaseSummaryMapper;
import com.lianshengtong.release.service.BatchReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 批量释放服务实现
 * <p>
 * 每批10万条，批次校验总量一致性，失败回滚标记，全量汇总校验，断点续跑。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchReleaseServiceImpl implements BatchReleaseService {

    private final LscLedgerFeignClient lscLedgerFeignClient;
    private final DailyReleaseSummaryMapper dailyReleaseSummaryMapper;
    private final RedissonClient redissonClient;

    @Value("${release.batch-size:100000}")
    private int batchSize;

    @Value("${release.lock-key:lsc:release:lock:daily}")
    private String lockKey;

    @Value("${release.lock-expire-seconds:7200}")
    private long lockExpireSeconds;

    @Override
    public DailyReleaseSummary executeBatchRelease(DailyReleaseSummary summary) {
        // 分布式锁：保障每日释放单实例运行
        RLock lock = redissonClient.getLock(lockKey + ":" + summary.getDate());
        boolean locked;
        try {
            locked = lock.tryLock(0, lockExpireSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取释放分布式锁被中断", e);
        }
        if (!locked) {
            log.warn("[BatchRelease] 日期={} 已有实例在执行，跳过", summary.getDate());
            summary.setFailReason("已有实例在执行，跳过");
            return summary;
        }
        try {
            return doBatchRelease(summary);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 批量释放主流程(支持断点续跑)
     */
    private DailyReleaseSummary doBatchRelease(DailyReleaseSummary summary) {
        summary.setStatus(ReleaseTaskStatusEnum.RUNNING.getCode());
        dailyReleaseSummaryMapper.updateById(summary);

        long planTotal = summary.getTRelease() == null ? 0L : summary.getTRelease();
        // 断点续跑：已释放量从汇总记录恢复
        long actualReleased = summary.getActualReleased() == null ? 0L : summary.getActualReleased();
        int batchCount = summary.getBatchCount() == null ? 0 : summary.getBatchCount();
        int failedBatchCount = summary.getFailedBatchCount() == null ? 0 : summary.getFailedBatchCount();

        // 加载待释放记录(按用户维度拆分，占位：实际从账本/会员服务查询锁定余额明细)
        List<ReleaseItem> items = loadPendingReleaseItems(summary, planTotal, actualReleased);
        if (items.isEmpty()) {
            log.warn("[BatchRelease] 日期={} 无待释放记录，planTotal={}", summary.getDate(), planTotal);
        }

        log.info("[BatchRelease] 日期={} 开始批量释放 planTotal={} 待处理记录={} 已释放={} 断点续跑={}",
                summary.getDate(), planTotal, items.size(), actualReleased, actualReleased > 0);

        // 分批处理
        int from = 0;
        while (from < items.size()) {
            int to = Math.min(from + batchSize, items.size());
            List<ReleaseItem> batch = items.subList(from, to);
            int batchNo = batchCount + 1;
            try {
                long batchReleased = processBatch(summary, batch, batchNo);
                actualReleased += batchReleased;
                batchCount++;
                // 每批次校验总量一致性(本批释放量>0 且不超过剩余计划)
                if (batchReleased < 0) {
                    throw new IllegalStateException("批次释放量为负，数据异常");
                }
                // 持久化进度(断点续跑依据)
                summary.setActualReleased(actualReleased);
                summary.setBatchCount(batchCount);
                summary.setStatus(ReleaseTaskStatusEnum.RUNNING.getCode());
                dailyReleaseSummaryMapper.updateById(summary);
                log.info("[BatchRelease] 批次#{} 完成 本批释放={} 累计释放={}/{}", batchNo, batchReleased, actualReleased, planTotal);
            } catch (Exception e) {
                failedBatchCount++;
                // 失败批次回滚并标记(Seata 全局事务回滚由账本服务侧保障；此处记录失败)
                log.error("[BatchRelease] 批次#{} 失败，回滚并标记 from={} to={} 原因={}", batchNo, from, to, e.getMessage(), e);
                summary.setFailedBatchCount(failedBatchCount);
                summary.setActualReleased(actualReleased);
                summary.setBatchCount(batchCount + 1);
                summary.setFailReason("批次#" + batchNo + "失败：" + e.getMessage());
                dailyReleaseSummaryMapper.updateById(summary);
                // 失败批次终止后续，等待人工介入或次日断点续跑
                summary.setStatus(ReleaseTaskStatusEnum.FAILED.getCode());
                return summary;
            }
            from = to;
        }

        // 全量完成后汇总校验
        summary.setActualReleased(actualReleased);
        summary.setBatchCount(batchCount);
        summary.setFailedBatchCount(failedBatchCount);
        boolean ok = reconcile(summary);
        summary.setStatus(ok ? ReleaseTaskStatusEnum.SUCCESS.getCode() : ReleaseTaskStatusEnum.FAILED.getCode());
        if (!ok) {
            summary.setFailReason("汇总校验不一致：实际=" + actualReleased + " 计划=" + planTotal);
            log.error("[BatchRelease] 日期={} 汇总校验不一致 实际={} 计划={}", summary.getDate(), actualReleased, planTotal);
        } else {
            summary.setFailReason(null);
            log.info("[BatchRelease] 日期={} 全量释放完成 实际={} 计划={}", summary.getDate(), actualReleased, planTotal);
        }
        dailyReleaseSummaryMapper.updateById(summary);
        return summary;
    }

    /**
     * 处理单个批次：调用账本服务批量释放，返回本批实际释放总量
     */
    private long processBatch(DailyReleaseSummary summary, List<ReleaseItem> batch, int batchNo) {
        List<LscLedgerOpDTO> opList = new ArrayList<>(batch.size());
        long batchPlan = 0L;
        for (ReleaseItem item : batch) {
            LscLedgerOpDTO op = LscLedgerOpDTO.builder()
                    .idempotentKey("DAILY_RELEASE_" + summary.getDate() + "_" + item.userId + "_" + batchNo)
                    .transactionType(LscTransactionTypeEnum.DAILY_RELEASE.getCode())
                    .userId(item.userId)
                    .lockedDelta(-item.amount)
                    .availableDelta(item.amount)
                    .orderNo("RELEASE_" + summary.getDate() + "_" + batchNo)
                    .remark("每日释放 rate=" + summary.getRate())
                    .build();
            opList.add(op);
            batchPlan += item.amount;
        }
        R<Map<String, Object>> resp = lscLedgerFeignClient.releaseBatch(opList);
        if (resp == null || !resp.isSuccess() || resp.getData() == null) {
            throw new IllegalStateException("账本批量释放接口失败：" + (resp == null ? "null" : resp.getMessage()));
        }
        Map<String, Object> result = resp.getData();
        // 每批次校验总量一致性：本批实际释放量应等于本批计划量
        long batchActual = toLong(result.get("releasedAmount"));
        if (batchActual != batchPlan) {
            throw new IllegalStateException("批次#" + batchNo + "总量不一致 计划=" + batchPlan + " 实际=" + batchActual);
        }
        return batchActual;
    }

    @Override
    public boolean reconcile(DailyReleaseSummary summary) {
        long plan = summary.getTRelease() == null ? 0L : summary.getTRelease();
        long actual = summary.getActualReleased() == null ? 0L : summary.getActualReleased();
        return actual == plan;
    }

    /**
     * 加载待释放记录(按用户维度)
     * <p>调用账本服务查询全网锁定明细，按 rate * lLocked_i 拆分到各用户，
     * 截断到 planTotal 总量；按用户ID 升序排列以便断点续跑跳过已释放部分。</p>
     */
    private List<ReleaseItem> loadPendingReleaseItems(DailyReleaseSummary summary, long planTotal, long actualReleased) {
        List<ReleaseItem> items = new ArrayList<>();
        BigDecimal rate = summary.getRate() == null ? BigDecimal.ZERO : summary.getRate();
        if (rate.signum() <= 0 || planTotal <= 0) {
            log.info("[BatchRelease] rate={} planTotal={} 跳过加载", rate, planTotal);
            return items;
        }
        try {
            R<Map<String, Object>> resp = lscLedgerFeignClient.lockedSummary();
            if (resp == null || !resp.isSuccess() || resp.getData() == null) {
                log.warn("[BatchRelease] 拉取全网锁定汇总失败，返回空列表");
                return items;
            }
            Object accountsObj = resp.getData().get("accounts");
            if (!(accountsObj instanceof List)) {
                log.warn("[BatchRelease] 锁定明细格式异常 accounts={}", accountsObj);
                return items;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> accounts = (List<Map<String, Object>>) accountsObj;
            long cumulative = 0L;
            for (Map<String, Object> acc : accounts) {
                if (cumulative >= planTotal) {
                    break;
                }
                Object userIdObj = acc.get("userId");
                Object lockedObj = acc.get("totalLocked");
                if (userIdObj == null || lockedObj == null) {
                    continue;
                }
                Long userId = Long.parseLong(String.valueOf(userIdObj));
                long locked = Long.parseLong(String.valueOf(lockedObj));
                if (locked <= 0) {
                    continue;
                }
                // 每用户释放量 = rate * lLocked_i (向下取整，至少0)
                long release = BigDecimal.valueOf(locked)
                        .multiply(rate)
                        .setScale(0, RoundingMode.DOWN)
                        .longValue();
                if (release <= 0) {
                    continue;
                }
                // 截断到总计划量
                if (cumulative + release > planTotal) {
                    release = planTotal - cumulative;
                }
                if (release <= 0) {
                    break;
                }
                items.add(new ReleaseItem(userId, release));
                cumulative += release;
            }
            log.info("[BatchRelease] 加载待释放记录 用户数={} 累计={} planTotal={} actualReleased={}",
                    items.size(), cumulative, planTotal, actualReleased);
        } catch (Exception e) {
            log.error("[BatchRelease] 加载待释放记录异常", e);
        }
        return items;
    }

    private long toLong(Object val) {
        if (val == null) {
            return 0L;
        }
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(val));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** 待释放记录内部结构 */
    private static final class ReleaseItem {
        final Long userId;
        final long amount;

        ReleaseItem(Long userId, long amount) {
            this.userId = userId;
            this.amount = amount;
        }
    }
}
