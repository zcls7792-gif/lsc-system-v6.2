package com.lianshengtong.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商家画像构建DTO
 * <p>
 * 基于商家交易、核销、B2B流转、客诉等数据构建商家画像，用于资格审核与风控。
 * </p>
 */
public class AiMerchantProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 商家ID */
        private Long merchantId;

        /** 经营类目 */
        private String category;

        /** 统计周期天数 */
        private Integer statDays;

        /** 交易汇总指标(订单数/成交额/核销额/B2B流转额/退款率) */
        private Map<String, BigDecimal> transactionMetrics;

        /** 客诉标签列表 */
        private List<String> complaintTags;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 商家信用评分 0~100 */
        private BigDecimal creditScore;

        /** 画像标签(如 优质商家/高风险/活跃度低) */
        private List<String> profileTags;

        /** 经营健康度 0~1 */
        private BigDecimal healthIndex;

        /** 异常风险标记 */
        private List<String> riskFlags;

        /** 各维度评分 */
        private Map<String, BigDecimal> dimensionScores;

        /** 是否降级 */
        private Boolean fallback;

        /** 画像说明 */
        private String message;
    }
}
