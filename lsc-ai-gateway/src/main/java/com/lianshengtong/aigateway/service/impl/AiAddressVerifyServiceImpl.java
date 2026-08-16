package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiAddressVerifyDTO;
import com.lianshengtong.aigateway.service.AiAddressVerifyService;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.common.enums.AiReviewResultEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * 地址真实性核验服务实现
 * <p>
 * 对比高德/百度地图实景图、工商注册地址，调用外部AI模型API。
 * 超时10秒自动降级为人工审核模式，返回 AI_SUSPICIOUS。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAddressVerifyServiceImpl implements AiAddressVerifyService {

    private static final String CAPABILITY = "address-verify";

    private final AiCircuitBreakerManager circuitBreakerManager;

    @Value("${ai.gateway.model.address-verify.endpoint:}")
    private String endpoint;

    @Override
    public AiAddressVerifyDTO.Response verify(AiAddressVerifyDTO.Request request) {
        return circuitBreakerManager.execute(CAPABILITY,
                () -> callExternalModel(request),
                () -> fallback(request));
    }

    /**
     * 调用外部AI模型API(实景图比对 + 工商地址比对)
     * TODO 接入地图实景图比对模型，当前为占位实现
     */
    private AiAddressVerifyDTO.Response callExternalModel(AiAddressVerifyDTO.Request request) {
        log.info("[AiAddressVerify] 调用外部模型 endpoint={} merchantId={} addr={}",
                endpoint, request.getMerchantId(), request.getDeclaredAddress());
        return AiAddressVerifyDTO.Response.builder()
                .reviewResult(AiReviewResultEnum.AI_PASS.getCode())
                .confidence(new BigDecimal("0.82"))
                .addressConsistency(new BigDecimal("0.90"))
                .geoMatchScore(new BigDecimal("0.88"))
                .streetViewMatchDesc("实景图与门头照片匹配(占位)")
                .fallback(false)
                .message("外部模型核验完成(占位)")
                .build();
    }

    private AiAddressVerifyDTO.Response fallback(AiAddressVerifyDTO.Request request) {
        log.warn("[AiAddressVerify] 降级为人工审核模式 merchantId={}", request.getMerchantId());
        return AiAddressVerifyDTO.Response.builder()
                .reviewResult(AiReviewResultEnum.AI_SUSPICIOUS.getCode())
                .confidence(BigDecimal.ZERO)
                .addressConsistency(BigDecimal.ZERO)
                .geoMatchScore(BigDecimal.ZERO)
                .streetViewMatchDesc("AI模型超时或熔断，需人工实地核验")
                .fallback(true)
                .message("AI模型超时或熔断，降级为人工审核")
                .build();
    }
}
