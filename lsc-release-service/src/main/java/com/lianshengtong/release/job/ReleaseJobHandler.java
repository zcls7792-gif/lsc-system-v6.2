package com.lianshengtong.release.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import com.lianshengtong.common.result.R;
import com.lianshengtong.release.dto.AiReleasePredictDTO;
import com.lianshengtong.release.entity.DailyReleaseSummary;
import com.lianshengtong.release.feign.AiGatewayFeignClient;
import com.lianshengtong.release.feign.LscLedgerFeignClient;
import com.lianshengtong.release.mapper.DailyReleaseSummaryMapper;
import com.lianshengtong.release.service.BatchReleaseService;
import com.lianshengtong.release.service.ReleaseCalcService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

/**
 * 释放服务 XXL-JOB 定时任务处理器
 * <p>
 * 任务清单(在 XXL-JOB 控制台配置 cron)：
 * <ul>
 *   <li>dailyReleaseJob：每日凌晨执行，单实例运行，核心释放流程</li>
 *   <li>expireTransferJob：每日凌晨扫描过期LSC转回(可用 -> 锁定)</li>
 *   <li>reconcileJob：释放完成后全量账务比对</li>
 * </ul>
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReleaseJobHandler {

    private final ReleaseCalcService releaseCalcService;
    private final BatchReleaseService batchReleaseService;
    private final DailyReleaseSummaryMapper dailyReleaseSummaryMapper;
    private final AiGatewayFeignClient aiGatewayFeignClient;
    private final LscLedgerFeignClient lscLedgerFeignClient;

    /**
     * 每日释放任务(单实例运行由 Redisson 分布式锁保障)
     */
    @XxlJob("dailyReleaseJob")
    public void dailyReleaseJob() {
        LocalDate date = LocalDate.now();
        log.info("[dailyReleaseJob] 开始执行 date={}", date);

        // 查询或创建当日汇总记录(断点续跑：优先复用已有记录)
        DailyReleaseSummary summary = dailyReleaseSummaryMapper.findByDate(date);
        boolean fresh = summary == null;
        if (fresh) {
            summary = new DailyReleaseSummary();
            summary.setDate(date);
            summary.setActualReleased(0L);
            summary.setBatchCount(0);
            summary.setFailedBatchCount(0);
        }

        // 从账本服务拉取输入参数
        // 1) L_locked: 全网锁定总量(由 ledger-service 跨分片聚合)
        Long lLocked = 0L;
        try {
            R<Map<String, Object>> lockResp = lscLedgerFeignClient.lockedSummary();
            if (lockResp != null && lockResp.isSuccess() && lockResp.getData() != null) {
                Object totalLocked = lockResp.getData().get("totalLocked");
                if (totalLocked != null) {
                    lLocked = Long.parseLong(String.valueOf(totalLocked));
                }
            }
        } catch (Exception e) {
            log.warn("[dailyReleaseJob] 拉取全网锁定汇总失败 err={}", e.getMessage());
        }
        // 2) N_total: 前一日核销总额(流水类型=7 商家核销，按昨日聚合)
        BigDecimal nTotal = BigDecimal.ZERO;
        try {
            LocalDate yesterday = date.minusDays(1);
            R<Map<String, Object>> nResp = lscLedgerFeignClient.dailySummary(yesterday.toString(), "7");
            if (nResp != null && nResp.isSuccess() && nResp.getData() != null) {
                Object totalAmount = nResp.getData().get("totalAmount");
                if (totalAmount != null) {
                    nTotal = new BigDecimal(String.valueOf(totalAmount));
                }
            }
        } catch (Exception e) {
            log.warn("[dailyReleaseJob] 拉取前一日核销总额失败 err={}", e.getMessage());
        }
        // 3) M_total: 监管账户总余额(由支付机构提供，简化为 N_total 的固定倍数估算；真实部署对接支付机构接口)
        // 估算关系：监管账户余额近似等于累计核销尚未划拨资金，按当日 L_locked * 87% 近似(现金化率87%)
        BigDecimal mTotal = BigDecimal.valueOf(lLocked)
                .multiply(new BigDecimal("0.87"))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        summary.setLLocked(lLocked);
        summary.setNTotal(nTotal);
        summary.setMTotal(mTotal);

        if (fresh) {
            dailyReleaseSummaryMapper.insert(summary);
        } else {
            dailyReleaseSummaryMapper.updateById(summary);
        }

        // AI 释放趋势预测(7/30天核销率均值)，超时降级不影响主流程
        try {
            R<AiReleasePredictDTO.Response> predictResp =
                    aiGatewayFeignClient.releasePredict(
                            AiReleasePredictDTO.Request.builder()
                                    .startDate(date)
                                    .historyKSeries(Collections.emptyList())
                                    .historyDates(Collections.emptyList())
                                    .predictDays(7)
                                    .build());
            if (predictResp != null && predictResp.isSuccess() && predictResp.getData() != null) {
                summary.setAiPredictedK7d(predictResp.getData().getPredictedK7d());
                summary.setAiPredictedK30d(predictResp.getData().getPredictedK30d());
            }
        } catch (Exception e) {
            log.warn("[dailyReleaseJob] AI趋势预测调用失败，跳过 date={} 原因={}", date, e.getMessage());
        }

        // 核心算法：k -> rate -> 校验 -> T_release
        try {
            releaseCalcService.calcDailyRelease(summary);
        } catch (Exception e) {
            log.error("[dailyReleaseJob] 释放计算失败(可能rate越界)，终止任务 date={}", date, e);
            dailyReleaseSummaryMapper.updateById(summary);
            return;
        }

        // 批量释放(含断点续跑、汇总校验)
        batchReleaseService.executeBatchRelease(summary);

        log.info("[dailyReleaseJob] 执行完成 date={} status={} tRelease={} actualReleased={}",
                date, summary.getStatus(), summary.getTRelease(), summary.getActualReleased());
    }

    /**
     * 过期LSC转回任务(扫描全网过期可用明细转回锁定)
     */
    @XxlJob("expireTransferJob")
    public void expireTransferJob() {
        log.info("[expireTransferJob] 开始执行");
        try {
            R<Map<String, Object>> resp = lscLedgerFeignClient.expireTransfer();
            if (resp != null && resp.isSuccess() && resp.getData() != null) {
                log.info("[expireTransferJob] 完成 用户数={} 转回总量={}",
                        resp.getData().get("userCount"), resp.getData().get("transferAmount"));
            } else {
                log.warn("[expireTransferJob] 账本服务返回失败 {}", resp == null ? "null" : resp.getMessage());
            }
        } catch (Exception e) {
            log.error("[expireTransferJob] 执行异常", e);
        }
    }

    /**
     * 账务比对任务(释放完成后全量账务比对)
     */
    @XxlJob("reconcileJob")
    public void reconcileJob() {
        LocalDate date = LocalDate.now();
        log.info("[reconcileJob] 开始执行 date={}", date);
        DailyReleaseSummary summary = dailyReleaseSummaryMapper.findByDate(date);
        if (summary == null) {
            log.warn("[reconcileJob] 当日无释放汇总记录 date={}", date);
            return;
        }
        boolean ok = batchReleaseService.reconcile(summary);
        if (ok) {
            log.info("[reconcileJob] 账务比对一致 date={} 计划={} 实际={}",
                    date, summary.getTRelease(), summary.getActualReleased());
        } else {
            log.error("[reconcileJob] 账务比对不一致，需人工介入 date={} 计划={} 实际={} 失败批次={}",
                    date, summary.getTRelease(), summary.getActualReleased(), summary.getFailedBatchCount());
        }
    }
}

