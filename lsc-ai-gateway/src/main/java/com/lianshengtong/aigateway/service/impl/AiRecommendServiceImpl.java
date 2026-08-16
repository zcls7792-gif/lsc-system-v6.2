package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiRecommendDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.aigateway.service.AiRecommendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * 商品个性化推荐服务实现
 * <p>
 * 基于用户画像与行为进行商品个性化推荐，调用外部AI模型API。
 * 超时10秒自动降级为热门兜底。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRecommendServiceImpl implements AiRecommendService {

    private static final String CAPABILITY = "recommend";

    private final AiCircuitBreakerManager circuitBreakerManager;

    @Value("${ai.gateway.model.recommend.endpoint:}")
    private String endpoint;

    @Override
    public AiRecommendDTO.Response recommend(AiRecommendDTO.Request request) {
        return circuitBreakerManager.execute(CAPABILITY,
                () -> callExternalModel(request),
                () -> fallback(request));
    }

    /**
     * 调用外部AI推荐模型API
     * TODO 接入真实推荐模型，当前为占位实现
     */
    private AiRecommendDTO.Response callExternalModel(AiRecommendDTO.Request request) {
        log.info("[AiRecommend] 调用外部模型 endpoint={} userId={} scene={} topN={}",
                endpoint, request.getUserId(), request.getScene(), request.getTopN());
        return AiRecommendDTO.Response.builder()
                .items(new ArrayList<>())
                .fallback(false)
                .message("推荐完成(占位)")
                .build();
    }

    private AiRecommendDTO.Response fallback(AiRecommendDTO.Request request) {
        log.warn("[AiRecommend] 降级为热门兜底 userId={}", request.getUserId());
        return AiRecommendDTO.Response.builder()
                .items(new ArrayList<>())
                .fallback(true)
                .message("AI模型超时或熔断，降级为热门兜底")
                .build();
    }
}
