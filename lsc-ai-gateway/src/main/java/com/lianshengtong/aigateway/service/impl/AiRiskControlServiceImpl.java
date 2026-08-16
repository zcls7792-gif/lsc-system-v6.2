package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiRiskControlDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.aigateway.service.AiRiskControlService;
import com.lianshengtong.aigateway.service.LocalRuleEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;

/**
 * 动态风控评分服务实现
 * <p>
 * 基于用户行为特征进行风控评分，调用外部AI模型API。
 * 超时10秒自动降级，返回保守评分(建议人工复核)。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRiskControlServiceImpl implements AiRiskControlService {

    private static final String CAPABILITY = "risk-control";

    private final AiCircuitBreakerManager circuitBreakerManager;
    private final LocalRuleEngine localRuleEngine;

    @Value("${ai.gateway.model.risk-control.endpoint:}")
    private String endpoint;

    @Override
    public AiRiskControlDTO.Response score(AiRiskControlDTO.Request request) {
        return circuitBreakerManager.execute(CAPABILITY,
                () -> callExternalModel(request),
                () -> fallback(request));
    }

    /**
     * 调用外部AI风控模型API
     * TODO 接入真实风控评分模型，当前为占位实现
     */
    private AiRiskControlDTO.Response callExternalModel(AiRiskControlDTO.Request request) {
        log.info("[AiRiskControl] 调用外部模型 endpoint={} userId={} features={}",
                endpoint, request.getUserId(),
                request.getBehaviorFeatures() == null ? 0 : request.getBehaviorFeatures().size());
        return AiRiskControlDTO.Response.builder()
                .riskScore(new BigDecimal("85.00"))
                .riskLevel("LOW")
                .blocked(false)
                .hitRules(new ArrayList<>())
                .dimensionScores(request.getBehaviorFeatures() != null ? request.getBehaviorFeatures() : new java.util.HashMap<>())
                .fallback(false)
                .message("风控评分完成(占位)")
                .build();
    }

    private AiRiskControlDTO.Response fallback(AiRiskControlDTO.Request request) {
        log.warn("[AiRiskControl] 降级 userId={}", request.getUserId());
        AiRiskControlDTO.Response ruleResponse = localRuleEngine.evaluate(request);
        ruleResponse.setFallback(true);
        ruleResponse.setMessage("AI熔断降级: " + ruleResponse.getMessage());
        return ruleResponse;
    }
}
