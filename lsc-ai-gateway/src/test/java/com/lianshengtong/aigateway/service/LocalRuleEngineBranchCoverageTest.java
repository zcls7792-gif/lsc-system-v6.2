package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiRiskControlDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 补齐 LocalRuleEngine 分支覆盖率 (I-05)，见 LSC_V6.2_Reports/LSC_V6.2_Code_Quality_Completeness_Audit_20260822.md
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LocalRuleEngine 分支覆盖率补齐测试")
class LocalRuleEngineBranchCoverageTest {

    private LocalRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new LocalRuleEngine();
    }

    @Test
    @DisplayName("evaluate: behaviorFeatures 为 null 时命中 DEFAULT 规则")
    void evaluate_nullBehaviorFeatures_returnsDefault() {
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L)
                .userType(1)
                .behaviorFeatures(null)
                .build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("MEDIUM", resp.getRiskLevel());
        assertEquals(new BigDecimal("50"), resp.getRiskScore());
        assertFalse(resp.getBlocked());
        assertTrue(resp.getHitRules().contains("DEFAULT"));
        assertTrue(resp.getFallback());
    }

    @Test
    @DisplayName("evaluate: userType 非 1 时 NEW_USER_ANOMALY 不命中走 DEFAULT")
    void evaluate_newUserAnomaly_userTypeNot1_notBlocked() {
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(2L)
                .userType(2)
                .deviceFingerprint("short")
                .build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertFalse(resp.getBlocked());
        assertEquals("MEDIUM", resp.getRiskLevel());
        assertTrue(resp.getHitRules().contains("DEFAULT"));
        assertFalse(resp.getHitRules().contains("NEW_USER_ANOMALY"));
    }

    @Test
    @DisplayName("evaluate: userType=1 且设备指纹 <10 字符命中 NEW_USER_ANOMALY 被拦截")
    void evaluate_newUserAnomaly_userType1ShortFingerprint_blocked() {
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L)
                .userType(1)
                .deviceFingerprint("abc")
                .build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("HIGH", resp.getRiskLevel());
        assertTrue(resp.getBlocked());
        assertTrue(resp.getHitRules().contains("NEW_USER_ANOMALY"));
    }

    @Test
    @DisplayName("evaluate: anomalyTags 非空命中 ANOMALY_TAG 高风险")
    void evaluate_anomalyTagsNonEmpty_hitsHigh() {
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(1L)
                .userType(1)
                .anomalyTags(Arrays.asList("suspicious"))
                .build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("HIGH", resp.getRiskLevel());
        assertTrue(resp.getBlocked());
        assertTrue(resp.getHitRules().contains("ANOMALY_TAG"));
    }

    @Test
    @DisplayName("evaluate: 商家核销频率 >5 命中 MERCHANT_HIGH_WRITEON")
    void evaluate_merchantHighWriteoff_gt5_hits() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("writeoffFrequency", new BigDecimal("6"));
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(2L)
                .userType(2)
                .behaviorFeatures(features)
                .build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("MEDIUM", resp.getRiskLevel());
        assertEquals(new BigDecimal("55"), resp.getRiskScore());
        assertFalse(resp.getBlocked());
        assertTrue(resp.getHitRules().contains("MERCHANT_HIGH_WRITEON"));
    }

    @Test
    @DisplayName("getRuleHitStats: evaluate 后 reset 再查统计全为零")
    void getRuleHitStats_afterReset_returnsZeros() {
        Map<String, BigDecimal> features = new HashMap<>();
        features.put("orderFrequency", new BigDecimal("3"));
        engine.evaluate(AiRiskControlDTO.Request.builder()
                .userId(1L).userType(1).behaviorFeatures(features).build());

        engine.resetStats();
        Map<String, Integer> stats = engine.getRuleHitStats();

        assertNotNull(stats);
        assertEquals(0, stats.get("FREQ_LOW"));
        assertEquals(0, stats.get("FREQ_HIGH"));
        assertEquals(0, stats.get("DEFAULT"));
    }

    @Test
    @DisplayName("evaluate: 空请求(null behaviorFeatures + null anomalyTags)命中 DEFAULT")
    void evaluate_emptyRequest_hitsDefaultOnly() {
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(99L)
                .userType(2)
                .build();

        AiRiskControlDTO.Response resp = engine.evaluate(req);

        assertEquals("MEDIUM", resp.getRiskLevel());
        assertEquals(new BigDecimal("50"), resp.getRiskScore());
        assertFalse(resp.getBlocked());
        assertTrue(resp.getHitRules().contains("DEFAULT"));
        assertEquals(1, resp.getHitRules().size());
    }
}
