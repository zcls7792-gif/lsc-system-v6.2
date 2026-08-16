package com.lianshengtong.release.service.impl;

import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.ResultCode;
import com.lianshengtong.common.utils.ReleaseCalcUtil;
import com.lianshengtong.release.alert.AlertChannel;
import com.lianshengtong.release.entity.DailyReleaseSummary;
import com.lianshengtong.release.service.ReleaseCalcService;
import com.lianshengtong.release.service.ReleaseConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * 释放核心算法服务实现
 * <p>
 * 严格遵循：中间变量 decimal(18,6)，结果向下取整，rate 二次校验 [0.03%, 0.05%]。
 * 算法参数(rate_max/rate_min/k_min/k_max/alpha)由 {@link ReleaseConfigService} 提供，
 * 实际计算委托 {@link ReleaseCalcUtil}。
 * 告警通过 {@link AlertChannel} 抽象发送，默认为日志通道，可按需替换为钉钉/飞书/短信。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseCalcServiceImpl implements ReleaseCalcService {

    private final ReleaseConfigService releaseConfigService;
    private final AlertChannel alertChannel;

    @Value("${release.alert-receivers:admin-super-001,admin-super-002}")
    private String alertReceivers;

    @Override
    public BigDecimal calcK(BigDecimal nTotal, BigDecimal mTotal) {
        if (mTotal == null || mTotal.compareTo(BigDecimal.ZERO) <= 0) {
            // 监管账户余额为0时无法计算核销率，按0处理(将触发rate=rateMax)
            log.warn("[ReleaseCalc] M_total<=0，核销率k按0处理 nTotal={} mTotal={}", nTotal, mTotal);
            return BigDecimal.ZERO.setScale(ReleaseCalcUtil.SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal n = nTotal == null ? BigDecimal.ZERO : nTotal;
        // k = N_total / M_total，保留6位小数
        return n.divide(mTotal, ReleaseCalcUtil.SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calcRate(BigDecimal k) {
        BigDecimal rateMax = releaseConfigService.getRateMax();
        BigDecimal rateMin = releaseConfigService.getRateMin();
        BigDecimal kMin = releaseConfigService.getKMin();
        BigDecimal kMax = releaseConfigService.getKMax();
        BigDecimal alpha = releaseConfigService.getAlpha();
        BigDecimal rate = ReleaseCalcUtil.calcRate(k, rateMax, rateMin, kMin, kMax, alpha);
        log.info("[ReleaseCalc] calcRate k={} -> rate={} (rateMax={} rateMin={} kMin={} kMax={} alpha={})",
                k, rate, rateMax, rateMin, kMin, kMax, alpha);
        return rate;
    }

    @Override
    public long calcReleaseTotal(BigDecimal rate, long lLocked) {
        if (lLocked <= 0) {
            log.info("[ReleaseCalc] L_locked<=0，释放总量为0 lLocked={}", lLocked);
            return 0L;
        }
        // T_release = rate * L_locked，向下取整到整数个LSC
        long total = ReleaseCalcUtil.calcReleaseAmount(lLocked, rate);
        log.info("[ReleaseCalc] calcReleaseTotal rate={} lLocked={} -> T_release={}", rate, lLocked, total);
        return total;
    }

    @Override
    public boolean validateRate(BigDecimal rate) {
        BigDecimal rateMin = releaseConfigService.getRateMin();
        BigDecimal rateMax = releaseConfigService.getRateMax();
        boolean valid = ReleaseCalcUtil.isRateValid(rate, rateMin, rateMax);
        if (!valid) {
            // 越界：终止任务推送告警
            String content = String.format("释放比例rate=%s越界[%s,%s]，当日释放任务已终止", rate, rateMin, rateMax);
            log.error("[ReleaseCalc] 释放比例rate={} 越界 [{}{}] 硬约束，终止任务推送告警 接收人={}",
                    rate, rateMin, rateMax, alertReceivers);
            alertChannel.send(alertReceivers, "释放比例越界告警", content);
        }
        return valid;
    }

    @Override
    public DailyReleaseSummary calcDailyRelease(DailyReleaseSummary summary) {
        // 1. 计算核销率 k
        BigDecimal k = calcK(summary.getNTotal(), summary.getMTotal());
        summary.setK(k);

        // 2. 计算释放速率 rate
        BigDecimal rate = calcRate(k);
        summary.setRate(rate);

        // 3. 二次校验 rate 硬约束，越界终止
        if (!validateRate(rate)) {
            summary.setStatus(com.lianshengtong.common.enums.ReleaseTaskStatusEnum.FAILED.getCode());
            summary.setFailReason(ResultCode.RELEASE_RATE_OUT_OF_RANGE.getMessage());
            throw new BizException(ResultCode.RELEASE_RATE_OUT_OF_RANGE);
        }

        // 4. 计算当日释放总量 T_release (向下取整)
        long lLocked = summary.getLLocked() == null ? 0L : summary.getLLocked();
        long tRelease = calcReleaseTotal(rate, lLocked);
        summary.setTRelease(tRelease);

        if (summary.getDate() == null) {
            summary.setDate(LocalDate.now());
        }
        log.info("[ReleaseCalc] 日期={} k={} rate={} L_locked={} T_release={}",
                summary.getDate(), k, rate, lLocked, tRelease);
        return summary;
    }
}
