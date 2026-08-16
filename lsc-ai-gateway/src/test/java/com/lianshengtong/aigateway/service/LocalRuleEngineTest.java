package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiRiskControlDTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI 本地规则引擎测试")
class LocalRuleEngineTest {

    private LocalRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new LocalRuleEngine();
    }

    @Test
    @DisplayName("高频小额下单 -> LOW 风险评分 85")
    void testLowFrequencyOrder() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("orderFrequency", new BigDecimal("5"));
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L).userType(1).behaviorFeatures(features).build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("LOW", resp.getRiskLevel());
        assertEquals(new BigDecimal("85"), resp.getRiskScore());
        assertFalse(resp.getBlocked());
        assertTrue(resp.getFallback());
        assertTrue(resp.getHitRules().contains("FREQ_LOW"));
    }

    @Test
    @DisplayName("中频下单 -> MEDIUM 风险评分 65")
    void testMidFrequencyOrder() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("orderFrequency", new BigDecimal("25"));
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L).userType(1).behaviorFeatures(features).build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("MEDIUM", resp.getRiskLevel());
        assertEquals(new BigDecimal("65"), resp.getRiskScore());
        assertTrue(resp.getHitRules().contains("FREQ_MID"));
    }

    @Test
    @DisplayName("高频下单 -> HIGH 风险评分 35 被拦截")
    void testHighFrequencyOrder() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("orderFrequency", new BigDecimal("80"));
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L).userType(1).behaviorFeatures(features).build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("HIGH", resp.getRiskLevel());
        assertEquals(new BigDecimal("35"), resp.getRiskScore());
        assertTrue(resp.getBlocked());
        assertTrue(resp.getHitRules().contains("FREQ_HIGH"));
    }

    @Test
    @DisplayName("异常标签命中 -> HIGH 直接拦截")
    void testAnomalyTagHit() {
        List<String> tags = Arrays.asList("BATCH_FRAUD", "IP_ANOMALY");
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L).userType(1).anomalyTags(tags).build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("HIGH", resp.getRiskLevel());
        assertTrue(resp.getBlocked());
        assertTrue(resp.getHitRules().contains("ANOMALY_TAG"));
    }

    @Test
    @DisplayName("新用户 + 异常设备指纹 -> HIGH")
    void testNewUserAnomalyDevice() {
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L).userType(1).deviceFingerprint("short").build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("HIGH", resp.getRiskLevel());
        assertTrue(resp.getBlocked());
        assertTrue(resp.getHitRules().contains("NEW_USER_ANOMALY"));
    }

    @Test
    @DisplayName("商家高频核销 -> MEDIUM")
    void testMerchantHighWriteoff() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("writeoffFrequency", new BigDecimal("10"));
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(2L).userType(2).behaviorFeatures(features).build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("MEDIUM", resp.getRiskLevel());
        assertTrue(resp.getHitRules().contains("MERCHANT_HIGH_WRITEON"));
    }

    @Test
    @DisplayName("空请求 -> 默认保守评分 MEDIUM 50")
    void testDefaultRule() {
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L).userType(1).build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("MEDIUM", resp.getRiskLevel());
        assertEquals(new BigDecimal("50"), resp.getRiskScore());
        assertTrue(resp.getHitRules().contains("DEFAULT"));
    }

    @Test
    @DisplayName("规则命中统计正确")
    void testRuleHitStats() {
        Map<String, BigDecimal> featuresLow = new HashMap<>();
        featuresLow.put("orderFrequency", new BigDecimal("3"));
        engine.evaluate(AiRiskControlDTO.Request.builder().userId(1L).userType(1).behaviorFeatures(featuresLow).build());
        engine.evaluate(AiRiskControlDTO.Request.builder().userId(1L).userType(1).behaviorFeatures(featuresLow).build());

        Map<String, BigDecimal> featuresHigh = new HashMap<>();
        featuresHigh.put("orderFrequency", new BigDecimal("90"));
        engine.evaluate(AiRiskControlDTO.Request.builder().userId(2L).userType(1).behaviorFeatures(featuresHigh).build());

        Map<String, Integer> stats = engine.getRuleHitStats();
        assertEquals(2, stats.get("FREQ_LOW"));
        assertEquals(1, stats.get("FREQ_HIGH"));
    }

    @Test
    @DisplayName("重置规则命中计数")
    void testResetStats() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("orderFrequency", new BigDecimal("3"));
        engine.evaluate(AiRiskControlDTO.Request.builder().userId(1L).userType(1).behaviorFeatures(features).build());

        engine.resetStats();
        Map<String, Integer> stats = engine.getRuleHitStats();
        assertEquals(0, stats.get("FREQ_LOW"));
    }

    @Test
    @DisplayName("添加自定义规则后生效")
    void testAddCustomRule() {
        engine.addRule("CUSTOM_TEST", "LOW", new BigDecimal("90"),
                r -> r.getUserType() != null && r.getUserType() == 99);

        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L).userType(99).build();
        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("LOW", resp.getRiskLevel());
        assertEquals(new BigDecimal("90"), resp.getRiskScore());
        assertTrue(resp.getHitRules().contains("CUSTOM_TEST"));
    }

    @Test
    @DisplayName("响应包含 fallback=true 标识来自本地规则引擎")
    void testFallbackFlag() {
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L).userType(1).build();
        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertTrue(resp.getFallback());
        assertTrue(resp.getMessage().contains("本地规则引擎"));
    }
}