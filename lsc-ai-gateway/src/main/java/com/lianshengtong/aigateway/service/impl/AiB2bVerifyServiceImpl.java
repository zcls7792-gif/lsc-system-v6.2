package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiB2bVerifyDTO;
import com.lianshengtong.aigateway.service.AiB2bVerifyService;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.common.enums.AiReviewResultEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * B2B贸易背景核验服务实现
 * <p>
 * OCR提取凭证信息 + 合同匹配度评分 + 商家画像异常检测，调用外部AI模型API。
 * 超时10秒自动降级为人工审核模式，返回 AI_SUSPICIOUS。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiB2bVerifyServiceImpl implements AiB2bVerifyService {

    private static final String CAPABILITY = "b2b-verify";

    private final AiCircuitBreakerManager circuitBreakerManager;

    @Value("${ai.gateway.model.b2b-verify.endpoint:}")
    private String endpoint;

    @Override
    public AiB2bVerifyDTO.Response verify(AiB2bVerifyDTO.Request request) {
        return circuitBreakerManager.execute(CAPABILITY,
                () -> callExternalModel(request),
                () -> fallback(request));
    }

    /**
     * 调用外部AI模型API(OCR + 合同匹配 + 画像异常)
     * TODO 接入真实贸易核验模型，当前为占位实现
     */
    private AiB2bVerifyDTO.Response callExternalModel(AiB2bVerifyDTO.Request request) {
        log.info("[AiB2bVerify] 调用外部模型 endpoint={} orderNo={} evidences={}",
                endpoint, request.getOrderNo(),
                request.getEvidenceImageUrls() == null ? 0 : request.getEvidenceImageUrls().size());
        return AiB2bVerifyDTO.Response.builder()
                .reviewResult(AiReviewResultEnum.AI_PASS.getCode())
                .confidence(new BigDecimal("0.80"))
                .ocrResults(new ArrayList<>())
                .contractMatchScore(new BigDecimal("88.00"))
                .profileAnomalies(new ArrayList<>())
                .fallback(false)
                .message("外部模型核验完成(占位)")
                .build();
    }

    private AiB2bVerifyDTO.Response fallback(AiB2bVerifyDTO.Request request) {
        log.warn("[AiB2bVerify] 降级为人工审核模式 orderNo={}", request.getOrderNo());
        return AiB2bVerifyDTO.Response.builder()
                .reviewResult(AiReviewResultEnum.AI_SUSPICIOUS.getCode())
                .confidence(BigDecimal.ZERO)
                .ocrResults(new ArrayList<>())
                .contractMatchScore(BigDecimal.ZERO)
                .profileAnomalies(new ArrayList<>())
                .fallback(true)
                .message("AI模型超时或熔断，降级为人工审核")
                .build();
    }
}
