package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiCustomerServiceDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.aigateway.service.AiCustomerServiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * AI客服问答服务实现
 * <p>
 * 智能客服问答，支持多轮对话与知识库检索，调用外部AI模型API。
 * 超时10秒自动降级建议转人工。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCustomerServiceServiceImpl implements AiCustomerServiceService {

    private static final String CAPABILITY = "customer-service";

    private final AiCircuitBreakerManager circuitBreakerManager;

    @Value("${ai.gateway.model.customer-service.endpoint:}")
    private String endpoint;

    @Override
    public AiCustomerServiceDTO.Response chat(AiCustomerServiceDTO.Request request) {
        return circuitBreakerManager.execute(CAPABILITY,
                () -> callExternalModel(request),
                () -> fallback(request));
    }

    /**
     * 调用外部AI对话模型API
     * TODO 接入真实对话/知识库模型，当前为占位实现
     */
    private AiCustomerServiceDTO.Response callExternalModel(AiCustomerServiceDTO.Request request) {
        log.info("[AiCustomerService] 调用外部模型 endpoint={} sessionId={} scene={}",
                endpoint, request.getSessionId(), request.getScene());
        return AiCustomerServiceDTO.Response.builder()
                .sessionId(request.getSessionId())
                .answer("您好，已收到您的咨询，正在为您处理(占位)")
                .hitKnowledgeIds(new ArrayList<>())
                .needHuman(false)
                .confidence(0.85)
                .fallback(false)
                .message("客服问答完成(占位)")
                .build();
    }

    private AiCustomerServiceDTO.Response fallback(AiCustomerServiceDTO.Request request) {
        log.warn("[AiCustomerService] 降级建议转人工 sessionId={}", request.getSessionId());
        return AiCustomerServiceDTO.Response.builder()
                .sessionId(request.getSessionId())
                .answer("客服系统繁忙，已为您转接人工客服，请稍候。")
                .hitKnowledgeIds(new ArrayList<>())
                .needHuman(true)
                .confidence(0.0)
                .fallback(true)
                .message("AI模型超时或熔断，降级转人工")
                .build();
    }
}
