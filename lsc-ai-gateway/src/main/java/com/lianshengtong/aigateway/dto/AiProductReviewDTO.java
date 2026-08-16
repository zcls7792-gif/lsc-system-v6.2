package com.lianshengtong.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 商品AI审核请求/响应DTO
 * <p>
 * 支持图片违规检测、视频多模态识别、文案敏感词检测(如"保本/增值/理财/收益")。
 * </p>
 */
public class AiProductReviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 商品ID */
        private Long productId;

        /** 商家ID */
        private Long merchantId;

        /** 商品图片URL列表 */
        private List<String> imageUrls;

        /** 商品视频URL(可为空) */
        private String videoUrl;

        /** 商品文案/标题/详情 */
        private String productCopy;

        /** 商品类目 */
        private String category;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 审核结果：0未审核 1AI通过 2AI可疑 3人工通过 4人工拒绝(见AiReviewResultEnum) */
        private Integer reviewResult;

        /** 综合置信度 0~1 */
        private BigDecimal confidence;

        /** 图片违规命中明细 */
        private List<ViolationItem> imageViolations;

        /** 视频违规命中明细 */
        private List<ViolationItem> videoViolations;

        /** 文案敏感词命中列表(如 保本/增值/理财/收益) */
        private List<String> sensitiveWords;

        /** 是否降级(超时/熔断回退人工审核模式) */
        private Boolean fallback;

        /** 审核说明 */
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ViolationItem implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 命中资源URL */
        private String resourceUrl;

        /** 违规类型(如 涉黄/涉政/违禁品/虚假宣传) */
        private String violationType;

        /** 置信度 0~1 */
        private BigDecimal confidence;
    }
}
