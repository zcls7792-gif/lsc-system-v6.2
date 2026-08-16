package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiRiskControlDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.aigateway.service.LocalRuleEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI风控评分服务单元测试")
class AiRiskControlServiceImplTest {

    @Mock
    private AiCircuitBreakerManager circuitBreakerManager;

    @Mock
    private LocalRuleEngine localRuleEngine;

    @InjectMocks
    private AiRiskControlServiceImpl aiRiskControlService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiRiskControlService, "endpoint", "http://test-endpoint");
    }

    private AiRiskControlDTO.Request buildRequest(Long userId, Integer userType, Map<String, BigDecimal> features) {
        return AiRiskControlDTO.Request.builder()
                .userId(userId)
                .userType(userType)
                .behaviorFeatures(features)
                .build();
    }

    @Test
    @DisplayName("score() 成功路径 - 熔断器关闭，正常评分返回 LOW 风险")
    void score_success() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("loginFreq", new BigDecimal("0.8"));
        features.put("orderFreq", new BigDecimal("0.5"));
        AiRiskControlDTO.Request request = buildRequest(1L, 1, features);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> action =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("85.00").compareTo(result.getRiskScore()));
        assertEquals("LOW", result.getRiskLevel());
        assertFalse(result.getBlocked());
        assertNotNull(result.getHitRules());
        assertTrue(result.getHitRules().isEmpty());
        assertEquals(features, result.getDimensionScores());
        assertFalse(result.getFallback());
        verify(circuitBreakerManager).execute(eq("risk-control"), any(), any());
    }

    @Test
    @DisplayName("score() behaviorFeatures 为 null 时正常处理")
    void score_nullBehaviorFeatures() {
        AiRiskControlDTO.Request request = buildRequest(1L, 1, null);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> action =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertNotNull(result.getDimensionScores());
        assertTrue(result.getDimensionScores().isEmpty());
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("score() userType=1 消费者类型正常评分")
    void score_consumerUserType() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("loginFreq", new BigDecimal("0.3"));
        AiRiskControlDTO.Request request = buildRequest(100L, 1, features);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> action =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertEquals("LOW", result.getRiskLevel());
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("score() userType=2 商家类型正常评分")
    void score_merchantUserType() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("orderFreq", new BigDecimal("0.9"));
        AiRiskControlDTO.Request request = buildRequest(200L, 2, features);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> action =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertEquals("LOW", result.getRiskLevel());
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("score() 降级返回 MEDIUM 风险等级，评分为 50")
    void score_fallback() {
        AiRiskControlDTO.Request request = buildRequest(1L, 1, new HashMap<>());

        AiRiskControlDTO.Response ruleResponse = AiRiskControlDTO.Response.builder()
                .riskScore(new BigDecimal("50.00"))
                .riskLevel("MEDIUM")
                .blocked(false)
                .hitRules(new ArrayList<>())
                .dimensionScores(request.getBehaviorFeatures())
                .fallback(true)
                .message("本地规则引擎评估: 命中规则=DEFAULT")
                .build();

        when(localRuleEngine.evaluate(any())).thenReturn(ruleResponse);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> fallback =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[2];
            return fallback.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getRiskScore()));
        assertEquals("MEDIUM", result.getRiskLevel());
        assertFalse(result.getBlocked());
        assertTrue(result.getHitRules().isEmpty());
        assertTrue(result.getFallback());
    }

    @Test
    @DisplayName("score() 正常流程中 blocked 为 false")
    void score_blockedIsFalse() {
        AiRiskControlDTO.Request request = buildRequest(1L, 1, new HashMap<>());

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> action =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertFalse(result.getBlocked());
    }

    @Test
    @DisplayName("score() 正常流程中 hitRules 为空列表")
    void score_hitRulesEmpty() {
        AiRiskControlDTO.Request request = buildRequest(1L, 2, new HashMap<>());

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> action =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertNotNull(result.getHitRules());
        assertTrue(result.getHitRules().isEmpty());
    }

    @Test
    @DisplayName("score() dimensionScores 从请求参数中正确获取")
    void score_dimensionScoresFromRequest() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("loginFreq", new BigDecimal("0.85"));
        features.put("orderFreq", new BigDecimal("0.42"));
        features.put("ipCluster", new BigDecimal("0.15"));
        AiRiskControlDTO.Request request = buildRequest(1L, 1, features);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> action =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertEquals(3, result.getDimensionScores().size());
        assertEquals(new BigDecimal("0.85"), result.getDimensionScores().get("loginFreq"));
        assertEquals(new BigDecimal("0.42"), result.getDimensionScores().get("orderFreq"));
        assertEquals(new BigDecimal("0.15"), result.getDimensionScores().get("ipCluster"));
    }

    @Test
    @DisplayName("score() 正常流程 message 包含 '风控评分完成'")
    void score_messageContainsSuccess() {
        AiRiskControlDTO.Request request = buildRequest(1L, 1, new HashMap<>());

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> action =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("风控评分完成"));
    }

    @Test
    @DisplayName("score() 正常流程 fallback 标志为 false")
    void score_fallbackFlagFalse() {
        AiRiskControlDTO.Request request = buildRequest(1L, 1, new HashMap<>());

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> action =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("score() 降级流程 message 包含 'AI熔断降级'")
    void score_fallbackMessage() {
        AiRiskControlDTO.Request request = buildRequest(1L, 1, new HashMap<>());

        AiRiskControlDTO.Response ruleResponse = AiRiskControlDTO.Response.builder()
                .riskScore(new BigDecimal("50.00"))
                .riskLevel("MEDIUM")
                .blocked(false)
                .hitRules(new ArrayList<>())
                .dimensionScores(request.getBehaviorFeatures())
                .fallback(true)
                .message("本地规则引擎评估: 命中规则=DEFAULT")
                .build();

        when(localRuleEngine.evaluate(any())).thenReturn(ruleResponse);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> fallback =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[2];
            return fallback.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        assertTrue(result.getMessage().contains("AI熔断降级"));
    }

    @Test
    @DisplayName("fallback() 使用 LocalRuleEngine 评估，返回带 fallback 标记的响应")
    void testFallbackUsesLocalRuleEngine() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("orderFrequency", new BigDecimal("5"));
        AiRiskControlDTO.Request request = buildRequest(1L, 1, features);

        AiRiskControlDTO.Response ruleResponse = AiRiskControlDTO.Response.builder()
                .riskScore(new BigDecimal("85.00"))
                .riskLevel("LOW")
                .blocked(false)
                .hitRules(new ArrayList<>(Collections.singletonList("FREQ_LOW")))
                .dimensionScores(features)
                .fallback(true)
                .message("本地规则引擎评估: 命中规则=FREQ_LOW")
                .build();

        when(localRuleEngine.evaluate(any())).thenReturn(ruleResponse);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiRiskControlDTO.Response> fallback =
                    (java.util.function.Supplier<AiRiskControlDTO.Response>) invocation.getArguments()[2];
            return fallback.get();
        });

        AiRiskControlDTO.Response result = aiRiskControlService.score(request);

        assertNotNull(result);
        verify(localRuleEngine).evaluate(request);
        assertTrue(result.getFallback());
        assertTrue(result.getMessage().contains("AI熔断降级"));
        assertEquals(0, new BigDecimal("85.00").compareTo(result.getRiskScore()));
        assertEquals("LOW", result.getRiskLevel());
    }
}