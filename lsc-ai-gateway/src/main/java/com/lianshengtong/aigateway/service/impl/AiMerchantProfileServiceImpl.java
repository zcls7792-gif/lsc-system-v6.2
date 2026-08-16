package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiMerchantProfileDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.aigateway.service.AiMerchantProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * 商家画像构建服务实现
 * <p>
 * 基于商家交易、核销、B2B流转、客诉等数据构建商家画像，调用外部AI模型API。
 * 超时10秒自动降级，返回保守画像(需人工复核)。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMerchantProfileServiceImpl implements AiMerchantProfileService {

    private static final String CAPABILITY = "merchant-profile";

    private final AiCircuitBreakerManager circuitBreakerManager;

    @Value("${ai.gateway.model.merchant-profile.endpoint:}")
    private String endpoint;

    @Override
    public AiMerchantProfileDTO.Response buildProfile(AiMerchantProfileDTO.Request request) {
        return circuitBreakerManager.execute(CAPABILITY,
                () -> callExternalModel(request),
                () -> fallback(request));
    }

    /**
     * 调用外部AI画像模型API
     * TODO 接入真实商家画像模型，当前为占位实现
     */
    private AiMerchantProfileDTO.Response callExternalModel(AiMerchantProfileDTO.Request request) {
        log.info("[AiMerchantProfile] 调用外部模型 endpoint={} merchantId={} statDays={}",
                endpoint, request.getMerchantId(), request.getStatDays());
        return AiMerchantProfileDTO.Response.builder()
                .creditScore(new BigDecimal("80.00"))
                .profileTags(new ArrayList<>())
                .healthIndex(new BigDecimal("0.82"))
                .riskFlags(new ArrayList<>())
                .dimensionScores(request.getTransactionMetrics())
                .fallback(false)
                .message("商家画像构建完成(占位)")
                .build();
    }

    private AiMerchantProfileDTO.Response fallback(AiMerchantProfileDTO.Request request) {
        log.warn("[AiMerchantProfile] 降级 merchantId={}", request.getMerchantId());
        return AiMerchantProfileDTO.Response.builder()
                .creditScore(new BigDecimal("60.00"))
                .profileTags(new ArrayList<>())
                .healthIndex(BigDecimal.ZERO)
                .riskFlags(new ArrayList<>())
                .dimensionScores(request.getTransactionMetrics())
                .fallback(true)
                .message("AI模型超时或熔断，降级为人工复核")
                .build();
    }
}
