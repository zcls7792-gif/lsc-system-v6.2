package com.lianshengtong.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品个性化推荐DTO
 * <p>
 * 基于用户画像与行为进行商品个性化推荐。
 * </p>
 */
public class AiRecommendDTO implements Serializable {

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

        /** 推荐场景(首页/类目/相似/猜你喜欢) */
        private String scene;

        /** 候选商品ID列表(召回阶段已筛选) */
        private List<Long> candidateProductIds;

        /** 推荐数量 */
        private Integer topN;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 排序后推荐商品列表 */
        private List<RecommendItem> items;

        /** 是否降级(降级时返回热门兜底) */
        private Boolean fallback;

        /** 推荐说明 */
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecommendItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 商品ID */
        private Long productId;

        /** 推荐分数 0~1 */
        private BigDecimal score;

        /** 推荐理由 */
        private String reason;
    }
}
