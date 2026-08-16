package com.lianshengtong.aigateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * B2B贸易背景核验请求/响应DTO
 * <p>
 * OCR提取凭证信息、合同匹配度评分、商家画像异常检测。
 * </p>
 */
public class AiB2bVerifyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Request implements Serializable {
        private static final long serialVersionUID = 1L;

        /** B2B订单号 */
        private String orderNo;

        /** 订单金额 */
        private BigDecimal orderAmount;

        /** 发起方商家ID */
        private Long fromMerchantId;

        /** 接收方商家ID */
        private Long toMerchantId;

        /** 订单信息(商品/数量/单价摘要) */
        private String orderInfo;

        /** 凭证图片URL列表(合同/发票/物流单) */
        private List<String> evidenceImageUrls;

        /** 合同范本URL(用于匹配度比对) */
        private String contractTemplateUrl;
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

        /** OCR提取的凭证信息明细 */
        private List<OcrResult> ocrResults;

        /** 合同匹配度评分 0~100 */
        private BigDecimal contractMatchScore;

        /** 商家画像异常标记列表 */
        private List<String> profileAnomalies;

        /** 是否降级 */
        private Boolean fallback;

        /** 核验说明 */
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrResult implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 凭证图片URL */
        private String imageUrl;

        /** 凭证类型(合同/发票/物流单) */
        private String evidenceType;

        /** OCR识别文本 */
        private String recognizedText;

        /** 关键字段提取(金额/日期/双方名称等) */
        private String keyFields;
    }
}
