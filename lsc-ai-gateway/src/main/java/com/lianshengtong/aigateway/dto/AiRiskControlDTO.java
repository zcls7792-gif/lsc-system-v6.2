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
 * 动态风控评分请求/响应DTO
 * <p>
 * 基于用户行为特征进行风控评分。
 * </p>
 */
public class AiRiskControlDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 用户ID */
        private Long userId;

        /** 用户类型 1消费者 2商家 */
        private Integer userType;

        /** 用户行为特征(键值对，如 登录频率/下单频率/核销频率/IP聚集度) */
        private Map<String, BigDecimal> behaviorFeatures;

        /** 近30日异常事件标签 */
        private List<String> anomalyTags;

        /** 设备指纹 */
        private String deviceFingerprint;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 风控评分 0~100(越高越安全) */
        private BigDecimal riskScore;

        /** 风险等级 LOW/MEDIUM/HIGH */
        private String riskLevel;

        /** 是否拦截 */
        private Boolean blocked;

        /** 命中风控规则列表 */
        private List<String> hitRules;

        /** 各维度风险贡献度 */
        private Map<String, BigDecimal> dimensionScores;

        /** 是否降级 */
        private Boolean fallback;

        /** 评分说明 */
        private String message;
    }
}
