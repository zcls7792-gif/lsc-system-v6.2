package com.lianshengtong.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 参数调整仿真推演DTO
 * <p>
 * 在正式变更释放参数前，对参数变更进行模拟推演，输出模拟天数内的核销率/释放量预测。
 * </p>
 */
public class AiParamSimulationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 参数变更项(键值对，如 rate_max/rate_min/k_min/k_max/alpha) */
        private Map<String, BigDecimal> paramChanges;

        /** 当前参数快照 */
        private Map<String, BigDecimal> currentParams;

        /** 模拟天数 */
        private Integer simulateDays;

        /** 当前全网锁定LSC总量 */
        private Long currentLLocked;

        /** 当前监管账户余额总和 */
        private BigDecimal currentMTotal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 模拟期间每日释放量序列 */
        private java.util.List<Long> dailyReleaseSeries;

        /** 模拟期间每日核销率预测序列 */
        private java.util.List<BigDecimal> dailyKSeries;

        /** 模拟期累计释放总量 */
        private Long totalRelease;

        /** 模拟期平均核销率 */
        private BigDecimal avgK;

        /** 相对当前参数的释放量变化百分比 */
        private BigDecimal releaseChangePct;

        /** 风险提示 */
        private String riskHint;

        /** 是否降级 */
        private Boolean fallback;

        /** 推演说明 */
        private String message;
    }
}
