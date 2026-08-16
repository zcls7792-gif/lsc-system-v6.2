package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiProductReviewDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.aigateway.service.AiProductReviewService;
import com.lianshengtong.common.enums.AiReviewResultEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 商品AI审核服务实现
 * <p>
 * 图片违规检测 + 视频多模态识别 调用外部AI模型API；文案敏感词检测(保本/增值/理财/收益)本地快检。
 * 超时10秒自动降级为人工审核模式，返回默认结果 AI_SUSPICIOUS，不影响核心业务。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProductReviewServiceImpl implements AiProductReviewService {

    private static final String CAPABILITY = "product-review";

    /** 文案敏感词清单 */
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "保本", "增值", "理财", "收益", "稳赚", "保收益", "无风险");

    private final AiCircuitBreakerManager circuitBreakerManager;

    @Value("${ai.gateway.model.product-review.endpoint:}")
    private String endpoint;

    @Override
    public AiProductReviewDTO.Response review(AiProductReviewDTO.Request request) {
        // 文案敏感词本地快检(无需调用外部模型，低延迟)
        List<String> hitWords = detectSensitiveWords(request.getProductCopy());

        return circuitBreakerManager.execute(CAPABILITY,
                () -> callExternalModel(request, hitWords),
                () -> fallback(request, hitWords));
    }

    /**
     * 调用外部AI模型API(图片违规检测 + 视频多模态识别)
     * TODO 接入真实多模态审核模型，当前为占位实现
     */
    private AiProductReviewDTO.Response callExternalModel(AiProductReviewDTO.Request request, List<String> hitWords) {
        log.info("[AiProductReview] 调用外部模型 endpoint={} productId={} imgs={} video={}",
                endpoint, request.getProductId(),
                request.getImageUrls() == null ? 0 : request.getImageUrls().size(),
                request.getVideoUrl());
        // 占位：外部模型未接入时返回AI通过，敏感词命中则返回可疑
        Integer result = hitWords.isEmpty()
                ? AiReviewResultEnum.AI_PASS.getCode()
                : AiReviewResultEnum.AI_SUSPICIOUS.getCode();
        return AiProductReviewDTO.Response.builder()
                .reviewResult(result)
                .confidence(new BigDecimal("0.85"))
                .imageViolations(new ArrayList<>())
                .videoViolations(new ArrayList<>())
                .sensitiveWords(hitWords)
                .fallback(false)
                .message("外部模型审核完成(占位)")
                .build();
    }

    /**
     * 降级：返回 AI_SUSPICIOUS 转人工审核，不影响核心业务
     */
    private AiProductReviewDTO.Response fallback(AiProductReviewDTO.Request request, List<String> hitWords) {
        log.warn("[AiProductReview] 降级为人工审核模式 productId={}", request.getProductId());
        return AiProductReviewDTO.Response.builder()
                .reviewResult(AiReviewResultEnum.AI_SUSPICIOUS.getCode())
                .confidence(BigDecimal.ZERO)
                .imageViolations(new ArrayList<>())
                .videoViolations(new ArrayList<>())
                .sensitiveWords(hitWords)
                .fallback(true)
                .message("AI模型超时或熔断，降级为人工审核")
                .build();
    }

    private List<String> detectSensitiveWords(String copy) {
        List<String> hit = new ArrayList<>();
        if (copy == null || copy.isEmpty()) {
            return hit;
        }
        for (String word : SENSITIVE_WORDS) {
            if (copy.contains(word)) {
                hit.add(word);
            }
        }
        return hit;
    }
}
