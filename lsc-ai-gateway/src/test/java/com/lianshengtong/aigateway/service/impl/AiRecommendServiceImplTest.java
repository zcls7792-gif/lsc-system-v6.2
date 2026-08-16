package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiRecommendDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI推荐服务单元测试")
class AiRecommendServiceImplTest {

    @Mock
    private AiCircuitBreakerManager circuitBreakerManager;

    @InjectMocks
    private AiRecommendServiceImpl aiRecommendService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiRecommendService, "endpoint", "http://test-endpoint");
    }

    private AiRecommendDTO.Request buildRequest(Long userId, String scene, Integer topN) {
        return AiRecommendDTO.Request.builder()
                .userId(userId)
                .userType(1)
                .scene(scene)
                .candidateProductIds(Arrays.asList(1L, 2L, 3L))
                .topN(topN)
                .build();
    }

    @Test
    @DisplayName("recommend() 成功返回非降级结果")
    void recommend_success_notFallback() {
        AiRecommendDTO.Request request = buildRequest(1L, "home", 10);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> action =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRecommendDTO.Response result = aiRecommendService.recommend(request);

        assertNotNull(result);
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("recommend() 返回空商品列表")
    void recommend_returnsEmptyItemsList() {
        AiRecommendDTO.Request request = buildRequest(1L, "home", 10);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> action =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRecommendDTO.Response result = aiRecommendService.recommend(request);

        assertNotNull(result);
        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    @DisplayName("recommend() 降级返回 isFallback=true")
    void recommend_fallback_isFallbackTrue() {
        AiRecommendDTO.Request request = buildRequest(1L, "home", 10);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> fallback =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[2];
            return fallback.get();
        });

        AiRecommendDTO.Response result = aiRecommendService.recommend(request);

        assertNotNull(result);
        assertTrue(result.getFallback());
    }

    @Test
    @DisplayName("recommend() 使用不同 userId 正常推荐")
    void recommend_differentUserId() {
        AiRecommendDTO.Request request = buildRequest(999L, "home", 5);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> action =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRecommendDTO.Response result = aiRecommendService.recommend(request);

        assertNotNull(result);
        assertFalse(result.getFallback());
        assertNotNull(result.getItems());
    }

    @Test
    @DisplayName("recommend() 使用 scene 参数正常推荐")
    void recommend_withScene() {
        AiRecommendDTO.Request request = buildRequest(1L, "category", 8);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> action =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRecommendDTO.Response result = aiRecommendService.recommend(request);

        assertNotNull(result);
        assertFalse(result.getFallback());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    @DisplayName("recommend() 使用 topN 参数正常推荐")
    void recommend_withTopN() {
        AiRecommendDTO.Request request = buildRequest(1L, "guess", 20);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> action =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRecommendDTO.Response result = aiRecommendService.recommend(request);

        assertNotNull(result);
        assertFalse(result.getFallback());
        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    @DisplayName("recommend() 成功流程 message 为 '推荐完成(占位)'")
    void recommend_success_message() {
        AiRecommendDTO.Request request = buildRequest(1L, "home", 10);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> action =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRecommendDTO.Response result = aiRecommendService.recommend(request);

        assertNotNull(result);
        assertEquals("推荐完成(占位)", result.getMessage());
    }

    @Test
    @DisplayName("recommend() 降级流程 message 包含 '降级为热门兜底'")
    void recommend_fallback_message() {
        AiRecommendDTO.Request request = buildRequest(1L, "home", 10);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> fallback =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[2];
            return fallback.get();
        });

        AiRecommendDTO.Response result = aiRecommendService.recommend(request);

        assertNotNull(result);
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("降级为热门兜底"));
    }

    @Test
    @DisplayName("recommend() 降级流程返回空 items 列表")
    void recommend_fallback_emptyItems() {
        AiRecommendDTO.Request request = buildRequest(1L, "home", 10);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> fallback =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[2];
            return fallback.get();
        });

        AiRecommendDTO.Response result = aiRecommendService.recommend(request);

        assertNotNull(result);
        assertTrue(result.getFallback());
        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    @DisplayName("recommend() 调用使用 capability 'recommend'")
    void recommend_capability_isRecommend() {
        AiRecommendDTO.Request request = buildRequest(1L, "home", 10);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRecommendDTO.Response> action =
                    (java.util.function.Supplier<AiRecommendDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        aiRecommendService.recommend(request);

        verify(circuitBreakerManager).execute(eq("recommend"), any(), any());
    }
}