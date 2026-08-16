package com.lianshengtong.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * LSC释放趋势预测请求/响应DTO
 * <p>
 * 基于历史核销率序列，预测未来7-30天核销率走势，供释放服务参数调节参考。
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

        /** 历史核销率序列(按日期升序，k = N_total / M_total) */
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

        /** 未来N天核销率预测序列 */
        private List<BigDecimal> predictedKSeries;

        /** 预测对应日期序列 */
        private List<LocalDate> predictedDates;

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

        /** 预测说明 */
        private String message;
    }
}
