package com.lianshengtong.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 地址真实性核验请求/响应DTO
 * <p>
 * 对比高德/百度地图实景图、工商注册地址，核验经营地址真实性。
 * </p>
 */
public class AiAddressVerifyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 商家ID */
        private Long merchantId;

        /** 申报经营地址 */
        private String declaredAddress;

        /** 工商注册地址 */
        private String registeredAddress;

        /** 经度 */
        private BigDecimal longitude;

        /** 纬度 */
        private BigDecimal latitude;

        /** 门头照片URL(可选) */
        private String storefrontImageUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Response implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 核验结果：1AI通过 2AI可疑(见AiReviewResultEnum) */
        private Integer reviewResult;

        /** 综合置信度 0~1 */
        private BigDecimal confidence;

        /** 申报地址与注册地址一致性 0~1 */
        private BigDecimal addressConsistency;

        /** 经纬度与地址匹配度 0~1 */
        private BigDecimal geoMatchScore;

        /** 实景图比对结果描述 */
        private String streetViewMatchDesc;

        /** 是否降级 */
        private Boolean fallback;

        /** 核验说明 */
        private String message;
    }
}
