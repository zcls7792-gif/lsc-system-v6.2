package com.lianshengtong.release.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 释放趋势预测 Feign 调用 DTO (本地契约)
 * <p>
 * 镜像 lsc-ai-gateway 的 {@code AiReleasePredictDTO} 契约，避免跨服务模块依赖。
 * 字段须与 AI 网关侧保持一致以保证序列化兼容。
 * </p>
 */
public class AiReleasePredictDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 起始预测日期 */
        private LocalDate startDate;

        /** 历史核销率序列(按日期升序) */
        private List<BigDecimal> historyKSeries;

        /** 历史对应日期序列 */
        private List<LocalDate> historyDates;

        /** 预测天数(7或30) */
        private Integer predictDays;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 7天核销率均值预测 */
        private BigDecimal predictedK7d;

        /** 30天核销率均值预测 */
        private BigDecimal predictedK30d;

        /** 预测置信度 0~1 */
        private BigDecimal confidence;

        /** 趋势方向 UP/FLAT/DOWN */
        private String trend;

        /** 是否降级 */
        private Boolean fallback;
    }
}
