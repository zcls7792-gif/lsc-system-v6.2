package com.lianshengtong.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.lsc.LscUnitConstants;
import com.lianshengtong.common.lsc.ReleaseRateCalculator;
import com.lianshengtong.ledger.entity.LscAccount;
import com.lianshengtong.ledger.entity.LscGrantLot;
import com.lianshengtong.ledger.mapper.LscAccountMapper;
import com.lianshengtong.ledger.mapper.LscGrantLotMapper;
import com.lianshengtong.ledger.service.LscLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日释放定时任务（V7.7.2 第五章、第十三章 13.3）
 * <p>
 * 执行顺序：
 * 1. 取得前日一致性快照，计算周转率 w
 * 2. 插值得到释放率 r
 * 3. 按用户分段执行当日释放，逐 Lot 写唯一结果
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyReleaseTask {

    private final LscLedgerService ledgerService;
    private final LscAccountMapper accountMapper;
    private final LscGrantLotMapper grantLotMapper;

    /**
     * 每日 00:30 执行释放（北京时间）。
     * 实际生产应使用 XXL-Job 或类似调度器，此处用 Spring Schedule 简化。
     */
    @Scheduled(cron = "0 30 0 * * ?")
    public void executeDailyRelease() {
        LocalDate bizDate = LocalDate.now();
        log.info("[每日释放] 开始执行，业务日={}", bizDate);

        try {
            // 1. 计算周转率 w 和释放率 r（V7.7.2 5.2-5.3）
            long wPpm = calculateTurnoverRate();
            long wMin = LscUnitConstants.W_MIN_DEFAULT_PPM;
            long wMax = LscUnitConstants.W_MAX_DEFAULT_PPM;
            long rMin = LscUnitConstants.RELEASE_MIN_PPB;
            long rMax = LscUnitConstants.RELEASE_MAX_PPB;

            if (!ReleaseRateCalculator.isValidWConfig(wMin, wMax)) {
                log.error("[每日释放] w配置非法 wMin={} wMax={}，中止", wMin, wMax);
                return;
            }

            long ratePpb = ReleaseRateCalculator.calcRatePpb(wPpm, wMin, wMax, rMin, rMax);
            log.info("[每日释放] w={} ppm, rate={} ppb ({}%)", wPpm, ratePpb,
                    ratePpb * 100.0 / LscUnitConstants.PPB_BASE);

            // 2. 分页处理有活跃 GrantLot 的用户
            long lastUserId = 0L;
            int batchSize = 500;
            int totalUsers = 0;

            while (true) {
                List<LscAccount> accounts = accountMapper.selectList(
                        new LambdaQueryWrapper<LscAccount>()
                                .gt(LscAccount::getUserId, lastUserId)
                                .orderByAsc(LscAccount::getUserId)
                                .last("LIMIT " + batchSize));
                if (accounts.isEmpty()) break;

                for (LscAccount acc : accounts) {
                    try {
                        // 检查是否有需要释放的活跃批次
                        Long count = grantLotMapper.selectCount(
                                new LambdaQueryWrapper<LscGrantLot>()
                                        .eq(LscGrantLot::getUserId, acc.getUserId())
                                        .eq(LscGrantLot::getState, "ACTIVE")
                                        .eq(LscGrantLot::getRefundHold, false)
                                        .le(LscGrantLot::getFirstReleaseDate, bizDate));
                        if (count != null && count > 0) {
                            ledgerService.dailyRelease(acc.getUserId(), bizDate, ratePpb);
                            totalUsers++;
                        }
                    } catch (Exception e) {
                        log.error("[每日释放] 用户{}释放失败", acc.getUserId(), e);
                    }
                    lastUserId = acc.getUserId();
                }
            }
            log.info("[每日释放] 完成，共处理 {} 个用户", totalUsers);
        } catch (Exception e) {
            log.error("[每日释放] 执行异常", e);
        }
    }

    /**
     * 计算平台周转率 w（V7.7.2 5.2）。
     * w = floor(max(N, 0) * 1e6 / B)
     * N = 前日成功核销 + 到期作废 - 退款返还入批次
     * B = 前日期末全部 Available Lot 未终结余额
     */
    private long calculateTurnoverRate() {
        // 简化实现：实际应查询 release_day_snapshot 前日封账数据
        // 此处返回默认值 1.5% 作为示例
        return 15000L; // 1.5% -> 对应 0.075% 释放率
    }
}
