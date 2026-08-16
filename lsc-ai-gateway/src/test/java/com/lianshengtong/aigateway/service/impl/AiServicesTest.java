package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.*;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.common.enums.AiReviewResultEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI 服务实现综合单元测试")
class AiServicesTest {

    @Mock
    private AiCircuitBreakerManager circuitBreakerManager;

    // ==================== AiB2bVerifyServiceImpl ====================

    @InjectMocks
    private AiB2bVerifyServiceImpl aiB2bVerifyService;

    // ==================== AiAddressVerifyServiceImpl ====================

    @InjectMocks
    private AiAddressVerifyServiceImpl aiAddressVerifyService;

    // ==================== AiReleasePredictServiceImpl ====================

    @InjectMocks
    private AiReleasePredictServiceImpl aiReleasePredictService;

    // ==================== AiMerchantProfileServiceImpl ====================

    @InjectMocks
    private AiMerchantProfileServiceImpl aiMerchantProfileService;

    // ==================== AiParamSimulationServiceImpl ====================

    @InjectMocks
    private AiParamSimulationServiceImpl aiParamSimulationService;

    // ==================== AiCustomerServiceServiceImpl ====================

    @InjectMocks
    private AiCustomerServiceServiceImpl aiCustomerServiceService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiB2bVerifyService, "endpoint", "http://test-b2b-endpoint");
        ReflectionTestUtils.setField(aiAddressVerifyService, "endpoint", "http://test-address-endpoint");
        ReflectionTestUtils.setField(aiReleasePredictService, "endpoint", "http://test-release-endpoint");
        ReflectionTestUtils.setField(aiMerchantProfileService, "endpoint", "http://test-merchant-endpoint");
        ReflectionTestUtils.setField(aiParamSimulationService, "endpoint", "http://test-param-endpoint");
        ReflectionTestUtils.setField(aiCustomerServiceService, "endpoint", "http://test-cs-endpoint");
    }

    // ==================== 辅助方法 ====================

    private AiB2bVerifyDTO.Request buildB2bRequest() {
        return AiB2bVerifyDTO.Request.builder()
                .orderNo("B2B-20250101-001")
                .orderAmount(new BigDecimal("50000.00"))
                .fromMerchantId(100L)
                .toMerchantId(200L)
                .orderInfo("电子产品批发 100件")
                .evidenceImageUrls(Arrays.asList("http://img/contract.jpg", "http://img/invoice.jpg"))
                .contractTemplateUrl("http://template/standard.pdf")
                .build();
    }

    private AiAddressVerifyDTO.Request buildAddressRequest() {
        return AiAddressVerifyDTO.Request.builder()
                .merchantId(100L)
                .declaredAddress("北京市朝阳区建国路88号")
                .registeredAddress("北京市朝阳区建国路88号SOHO现代城")
                .longitude(new BigDecimal("116.4689"))
                .latitude(new BigDecimal("39.9089"))
                .storefrontImageUrl("http://img/storefront.jpg")
                .build();
    }

    private AiReleasePredictDTO.Request buildReleaseRequest() {
        List<BigDecimal> historyK = Arrays.asList(
                new BigDecimal("0.0065"), new BigDecimal("0.0068"), new BigDecimal("0.0070"),
                new BigDecimal("0.0071"), new BigDecimal("0.0069"), new BigDecimal("0.0070"),
                new BigDecimal("0.0072")
        );
        List<LocalDate> dates = Arrays.asList(
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2), LocalDate.of(2025, 1, 3),
                LocalDate.of(2025, 1, 4), LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 6),
                LocalDate.of(2025, 1, 7)
        );
        return AiReleasePredictDTO.Request.builder()
                .startDate(LocalDate.of(2025, 1, 8))
                .historyKSeries(historyK)
                .historyDates(dates)
                .predictDays(7)
                .build();
    }

    private AiMerchantProfileDTO.Request buildMerchantProfileRequest() {
        Map<String, BigDecimal> metrics = new HashMap<>();
        metrics.put("orderCount", new BigDecimal("1520"));
        metrics.put("totalAmount", new BigDecimal("850000.00"));
        metrics.put("refundRate", new BigDecimal("0.02"));
        return AiMerchantProfileDTO.Request.builder()
                .merchantId(100L)
                .category("餐饮美食")
                .statDays(30)
                .transactionMetrics(metrics)
                .complaintTags(Arrays.asList("上菜慢", "口味一般"))
                .build();
    }

    private AiParamSimulationDTO.Request buildParamSimulationRequest() {
        Map<String, BigDecimal> changes = new HashMap<>();
        changes.put("rate_max", new BigDecimal("0.015"));
        changes.put("k_min", new BigDecimal("0.006"));
        Map<String, BigDecimal> current = new HashMap<>();
        current.put("rate_max", new BigDecimal("0.012"));
        current.put("k_min", new BigDecimal("0.005"));
        return AiParamSimulationDTO.Request.builder()
                .paramChanges(changes)
                .currentParams(current)
                .simulateDays(30)
                .currentLLocked(5000000L)
                .currentMTotal(new BigDecimal("100000000.00"))
                .build();
    }

    private AiCustomerServiceDTO.Request buildCustomerServiceRequest() {
        return AiCustomerServiceDTO.Request.builder()
                .sessionId("session-001")
                .userId(100L)
                .question("如何申请退款？")
                .context(Arrays.asList(
                        AiCustomerServiceDTO.Round.builder().role("user").content("你好").build(),
                        AiCustomerServiceDTO.Round.builder().role("assistant").content("您好，请问有什么可以帮您？").build()
                ))
                .scene("退款")
                .build();
    }

    // ==================== 1. AiB2bVerifyServiceImpl 测试 ====================

    @Nested
    @DisplayName("AiB2bVerifyServiceImpl - B2B贸易背景核验")
    class AiB2bVerifyTests {

        @Test
        @DisplayName("verify() 成功路径 - 返回 AI_PASS, confidence=0.80, contractMatchScore=88.00")
        void verify_primary_returnsPass() {
            AiB2bVerifyDTO.Request request = buildB2bRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiB2bVerifyDTO.Response> primary =
                        (java.util.function.Supplier<AiB2bVerifyDTO.Response>) inv.getArguments()[1];
                return primary.get();
            });

            AiB2bVerifyDTO.Response result = aiB2bVerifyService.verify(request);

            assertNotNull(result);
            assertEquals(AiReviewResultEnum.AI_PASS.getCode(), result.getReviewResult());
            assertEquals(0, new BigDecimal("0.80").compareTo(result.getConfidence()));
            assertEquals(0, new BigDecimal("88.00").compareTo(result.getContractMatchScore()));
            assertNotNull(result.getOcrResults());
            assertTrue(result.getOcrResults().isEmpty());
            assertNotNull(result.getProfileAnomalies());
            assertTrue(result.getProfileAnomalies().isEmpty());
            assertFalse(result.getFallback());
            verify(circuitBreakerManager).execute(eq("b2b-verify"), any(), any());
        }

        @Test
        @DisplayName("verify() 降级路径 - 返回 AI_SUSPICIOUS, fallback=true")
        void verify_fallback_returnsSuspicious() {
            AiB2bVerifyDTO.Request request = buildB2bRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiB2bVerifyDTO.Response> fallback =
                        (java.util.function.Supplier<AiB2bVerifyDTO.Response>) inv.getArguments()[2];
                return fallback.get();
            });

            AiB2bVerifyDTO.Response result = aiB2bVerifyService.verify(request);

            assertNotNull(result);
            assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getConfidence()));
            assertTrue(result.getFallback());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("降级为人工审核"));
        }
    }

    // ==================== 2. AiAddressVerifyServiceImpl 测试 ====================

    @Nested
    @DisplayName("AiAddressVerifyServiceImpl - 地址真实性核验")
    class AiAddressVerifyTests {

        @Test
        @DisplayName("verify() 成功路径 - 返回 AI_PASS, confidence=0.82, addressConsistency=0.90, geoMatchScore=0.88")
        void verify_primary_returnsPass() {
            AiAddressVerifyDTO.Request request = buildAddressRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiAddressVerifyDTO.Response> primary =
                        (java.util.function.Supplier<AiAddressVerifyDTO.Response>) inv.getArguments()[1];
                return primary.get();
            });

            AiAddressVerifyDTO.Response result = aiAddressVerifyService.verify(request);

            assertNotNull(result);
            assertEquals(AiReviewResultEnum.AI_PASS.getCode(), result.getReviewResult());
            assertEquals(0, new BigDecimal("0.82").compareTo(result.getConfidence()));
            assertEquals(0, new BigDecimal("0.90").compareTo(result.getAddressConsistency()));
            assertEquals(0, new BigDecimal("0.88").compareTo(result.getGeoMatchScore()));
            assertNotNull(result.getStreetViewMatchDesc());
            assertFalse(result.getFallback());
            verify(circuitBreakerManager).execute(eq("address-verify"), any(), any());
        }

        @Test
        @DisplayName("verify() 降级路径 - 返回 AI_SUSPICIOUS, fallback=true")
        void verify_fallback_returnsSuspicious() {
            AiAddressVerifyDTO.Request request = buildAddressRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiAddressVerifyDTO.Response> fallback =
                        (java.util.function.Supplier<AiAddressVerifyDTO.Response>) inv.getArguments()[2];
                return fallback.get();
            });

            AiAddressVerifyDTO.Response result = aiAddressVerifyService.verify(request);

            assertNotNull(result);
            assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getConfidence()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getAddressConsistency()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getGeoMatchScore()));
            assertTrue(result.getFallback());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("降级为人工审核"));
        }
    }

    // ==================== 3. AiReleasePredictServiceImpl 测试 ====================

    @Nested
    @DisplayName("AiReleasePredictServiceImpl - LSC释放趋势预测")
    class AiReleasePredictTests {

        @Test
        @DisplayName("predict() 成功路径 - predictedK7d=0.0070, predictedK30d=0.0072, confidence=0.78, trend=FLAT")
        void predict_primary_returnsPrediction() {
            AiReleasePredictDTO.Request request = buildReleaseRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiReleasePredictDTO.Response> primary =
                        (java.util.function.Supplier<AiReleasePredictDTO.Response>) inv.getArguments()[1];
                return primary.get();
            });

            AiReleasePredictDTO.Response result = aiReleasePredictService.predict(request);

            assertNotNull(result);
            assertEquals(0, new BigDecimal("0.0070").compareTo(result.getPredictedK7d()));
            assertEquals(0, new BigDecimal("0.0072").compareTo(result.getPredictedK30d()));
            assertEquals(0, new BigDecimal("0.78").compareTo(result.getConfidence()));
            assertEquals("FLAT", result.getTrend());
            assertNotNull(result.getPredictedKSeries());
            assertTrue(result.getPredictedKSeries().isEmpty());
            assertFalse(result.getFallback());
            verify(circuitBreakerManager).execute(eq("release-predict"), any(), any());
        }

        @Test
        @DisplayName("predict() 降级路径 - 使用历史最后值作为预测, fallback=true")
        void predict_fallback_usesLastHistoryValue() {
            AiReleasePredictDTO.Request request = buildReleaseRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiReleasePredictDTO.Response> fallback =
                        (java.util.function.Supplier<AiReleasePredictDTO.Response>) inv.getArguments()[2];
                return fallback.get();
            });

            AiReleasePredictDTO.Response result = aiReleasePredictService.predict(request);

            assertNotNull(result);
            BigDecimal expectedLastK = new BigDecimal("0.0072");
            assertEquals(0, expectedLastK.compareTo(result.getPredictedK7d()));
            assertEquals(0, expectedLastK.compareTo(result.getPredictedK30d()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getConfidence()));
            assertEquals("FLAT", result.getTrend());
            assertTrue(result.getFallback());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("最近一日"));
        }
    }

    // ==================== 4. AiMerchantProfileServiceImpl 测试 ====================

    @Nested
    @DisplayName("AiMerchantProfileServiceImpl - 商家画像构建")
    class AiMerchantProfileTests {

        @Test
        @DisplayName("buildProfile() 成功路径 - creditScore=80, healthIndex=0.82, riskFlags为空")
        void buildProfile_primary_returnsProfile() {
            AiMerchantProfileDTO.Request request = buildMerchantProfileRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiMerchantProfileDTO.Response> primary =
                        (java.util.function.Supplier<AiMerchantProfileDTO.Response>) inv.getArguments()[1];
                return primary.get();
            });

            AiMerchantProfileDTO.Response result = aiMerchantProfileService.buildProfile(request);

            assertNotNull(result);
            assertEquals(0, new BigDecimal("80.00").compareTo(result.getCreditScore()));
            assertEquals(0, new BigDecimal("0.82").compareTo(result.getHealthIndex()));
            assertNotNull(result.getRiskFlags());
            assertTrue(result.getRiskFlags().isEmpty());
            assertNotNull(result.getProfileTags());
            assertTrue(result.getProfileTags().isEmpty());
            assertEquals(request.getTransactionMetrics(), result.getDimensionScores());
            assertFalse(result.getFallback());
            verify(circuitBreakerManager).execute(eq("merchant-profile"), any(), any());
        }

        @Test
        @DisplayName("buildProfile() 降级路径 - creditScore=60, healthIndex=0, fallback=true")
        void buildProfile_fallback_returnsConservative() {
            AiMerchantProfileDTO.Request request = buildMerchantProfileRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiMerchantProfileDTO.Response> fallback =
                        (java.util.function.Supplier<AiMerchantProfileDTO.Response>) inv.getArguments()[2];
                return fallback.get();
            });

            AiMerchantProfileDTO.Response result = aiMerchantProfileService.buildProfile(request);

            assertNotNull(result);
            assertEquals(0, new BigDecimal("60.00").compareTo(result.getCreditScore()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getHealthIndex()));
            assertNotNull(result.getRiskFlags());
            assertTrue(result.getRiskFlags().isEmpty());
            assertTrue(result.getFallback());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("降级"));
        }
    }

    // ==================== 5. AiParamSimulationServiceImpl 测试 ====================

    @Nested
    @DisplayName("AiParamSimulationServiceImpl - 参数调整仿真推演")
    class AiParamSimulationTests {

        @Test
        @DisplayName("simulate() 成功路径 - totalRelease=0, avgK=0.0070, 有风险提示")
        void simulate_primary_returnsSimulation() {
            AiParamSimulationDTO.Request request = buildParamSimulationRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiParamSimulationDTO.Response> primary =
                        (java.util.function.Supplier<AiParamSimulationDTO.Response>) inv.getArguments()[1];
                return primary.get();
            });

            AiParamSimulationDTO.Response result = aiParamSimulationService.simulate(request);

            assertNotNull(result);
            assertEquals(0L, result.getTotalRelease());
            assertEquals(0, new BigDecimal("0.0070").compareTo(result.getAvgK()));
            assertNotNull(result.getRiskHint());
            assertFalse(result.getRiskHint().isEmpty());
            assertNotNull(result.getDailyReleaseSeries());
            assertTrue(result.getDailyReleaseSeries().isEmpty());
            assertNotNull(result.getDailyKSeries());
            assertTrue(result.getDailyKSeries().isEmpty());
            assertFalse(result.getFallback());
            verify(circuitBreakerManager).execute(eq("param-simulation"), any(), any());
        }

        @Test
        @DisplayName("simulate() 降级路径 - 零值返回, fallback=true")
        void simulate_fallback_returnsZeros() {
            AiParamSimulationDTO.Request request = buildParamSimulationRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiParamSimulationDTO.Response> fallback =
                        (java.util.function.Supplier<AiParamSimulationDTO.Response>) inv.getArguments()[2];
                return fallback.get();
            });

            AiParamSimulationDTO.Response result = aiParamSimulationService.simulate(request);

            assertNotNull(result);
            assertEquals(0L, result.getTotalRelease());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getAvgK()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getReleaseChangePct()));
            assertTrue(result.getFallback());
            assertNotNull(result.getRiskHint());
            assertTrue(result.getMessage().contains("降级"));
        }
    }

    // ==================== 6. AiCustomerServiceServiceImpl 测试 ====================

    @Nested
    @DisplayName("AiCustomerServiceServiceImpl - AI客服问答")
    class AiCustomerServiceTests {

        @Test
        @DisplayName("chat() 成功路径 - needHuman=false, confidence=0.85, 返回答案和命中知识")
        void chat_primary_returnsAnswer() {
            AiCustomerServiceDTO.Request request = buildCustomerServiceRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiCustomerServiceDTO.Response> primary =
                        (java.util.function.Supplier<AiCustomerServiceDTO.Response>) inv.getArguments()[1];
                return primary.get();
            });

            AiCustomerServiceDTO.Response result = aiCustomerServiceService.chat(request);

            assertNotNull(result);
            assertEquals("session-001", result.getSessionId());
            assertNotNull(result.getAnswer());
            assertFalse(result.getAnswer().isEmpty());
            assertNotNull(result.getHitKnowledgeIds());
            assertTrue(result.getHitKnowledgeIds().isEmpty());
            assertFalse(result.getNeedHuman());
            assertEquals(0.85, result.getConfidence(), 0.001);
            assertFalse(result.getFallback());
            verify(circuitBreakerManager).execute(eq("customer-service"), any(), any());
        }

        @Test
        @DisplayName("chat() 降级路径 - needHuman=true, 返回人工客服转接提示")
        void chat_fallback_returnsHumanTransfer() {
            AiCustomerServiceDTO.Request request = buildCustomerServiceRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiCustomerServiceDTO.Response> fallback =
                        (java.util.function.Supplier<AiCustomerServiceDTO.Response>) inv.getArguments()[2];
                return fallback.get();
            });

            AiCustomerServiceDTO.Response result = aiCustomerServiceService.chat(request);

            assertNotNull(result);
            assertEquals("session-001", result.getSessionId());
            assertNotNull(result.getAnswer());
            assertTrue(result.getAnswer().contains("人工"));
            assertTrue(result.getNeedHuman());
            assertEquals(0.0, result.getConfidence(), 0.001);
            assertTrue(result.getFallback());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("转人工"));
        }
    }

    // ==================== 额外边界测试 (Extra Edge Cases) ====================

    @Nested
    @DisplayName("额外边界测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("AiReleasePredict predict() 降级时历史序列为空，predictedK 返回 0")
        void predict_fallback_emptyHistory_returnsZero() {
            AiReleasePredictDTO.Request request = AiReleasePredictDTO.Request.builder()
                    .startDate(LocalDate.of(2025, 1, 8))
                    .historyKSeries(new ArrayList<>())
                    .historyDates(new ArrayList<>())
                    .predictDays(7)
                    .build();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiReleasePredictDTO.Response> fallback =
                        (java.util.function.Supplier<AiReleasePredictDTO.Response>) inv.getArguments()[2];
                return fallback.get();
            });

            AiReleasePredictDTO.Response result = aiReleasePredictService.predict(request);

            assertNotNull(result);
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getPredictedK7d()));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getPredictedK30d()));
            assertTrue(result.getFallback());
        }

        @Test
        @DisplayName("AiB2bVerify verify() 请求参数为 null 的字段不影响降级路径")
        void verify_fallback_nullFieldsInRequest() {
            AiB2bVerifyDTO.Request request = AiB2bVerifyDTO.Request.builder()
                    .orderNo("ORDER-001")
                    .fromMerchantId(1L)
                    .toMerchantId(2L)
                    .build();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiB2bVerifyDTO.Response> fallback =
                        (java.util.function.Supplier<AiB2bVerifyDTO.Response>) inv.getArguments()[2];
                return fallback.get();
            });

            AiB2bVerifyDTO.Response result = aiB2bVerifyService.verify(request);

            assertNotNull(result);
            assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
            assertTrue(result.getFallback());
        }

        @Test
        @DisplayName("AiCustomerService chat() 降级时 confidence 为 0.0")
        void chat_fallback_confidenceIsZero() {
            AiCustomerServiceDTO.Request request = buildCustomerServiceRequest();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiCustomerServiceDTO.Response> fallback =
                        (java.util.function.Supplier<AiCustomerServiceDTO.Response>) inv.getArguments()[2];
                return fallback.get();
            });

            AiCustomerServiceDTO.Response result = aiCustomerServiceService.chat(request);

            assertNotNull(result);
            assertEquals(0.0, result.getConfidence(), 0.001);
            assertTrue(result.getFallback());
            assertTrue(result.getNeedHuman());
        }

        @Test
        @DisplayName("AiMerchantProfile buildProfile() 成功路径 dimensionScores 从请求透传")
        void buildProfile_primary_dimensionScoresFromRequest() {
            Map<String, BigDecimal> metrics = new HashMap<>();
            metrics.put("orderCount", new BigDecimal("999"));
            metrics.put("totalAmount", new BigDecimal("123456.78"));
            AiMerchantProfileDTO.Request request = AiMerchantProfileDTO.Request.builder()
                    .merchantId(999L)
                    .category("测试类目")
                    .statDays(7)
                    .transactionMetrics(metrics)
                    .complaintTags(new ArrayList<>())
                    .build();

            when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(inv -> {
                java.util.function.Supplier<AiMerchantProfileDTO.Response> primary =
                        (java.util.function.Supplier<AiMerchantProfileDTO.Response>) inv.getArguments()[1];
                return primary.get();
            });

            AiMerchantProfileDTO.Response result = aiMerchantProfileService.buildProfile(request);

            assertNotNull(result);
            assertEquals(2, result.getDimensionScores().size());
            assertEquals(new BigDecimal("999"), result.getDimensionScores().get("orderCount"));
            assertEquals(new BigDecimal("123456.78"), result.getDimensionScores().get("totalAmount"));
            assertFalse(result.getFallback());
        }
    }
}