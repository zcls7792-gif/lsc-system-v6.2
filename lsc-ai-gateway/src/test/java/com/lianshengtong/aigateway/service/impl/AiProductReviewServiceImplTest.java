package com.lianshengtong.aigateway.service.impl;

import com.lianshengtong.aigateway.dto.AiProductReviewDTO;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.common.enums.AiReviewResultEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AI商品审核服务单元测试")
class AiProductReviewServiceImplTest {

    @Mock
    private AiCircuitBreakerManager circuitBreakerManager;

    @InjectMocks
    private AiProductReviewServiceImpl aiProductReviewService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(aiProductReviewService, "endpoint", "http://test-endpoint");
    }

    private AiProductReviewDTO.Request buildRequest(String productCopy) {
        return AiProductReviewDTO.Request.builder()
                .productId(1L)
                .merchantId(100L)
                .productCopy(productCopy)
                .category("金融")
                .build();
    }

    @Test
    @DisplayName("review() 无敏感词返回 AI_PASS")
    void review_noSensitiveWords_returnsPass() {
        AiProductReviewDTO.Request request = buildRequest("这是一款优质商品，品质保证");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_PASS.getCode(), result.getReviewResult());
        assertFalse(result.getFallback());
        assertTrue(result.getSensitiveWords().isEmpty());
    }

    @Test
    @DisplayName("review() 含敏感词 '保本' 返回 AI_SUSPICIOUS")
    void review_withBaoBen_returnsSuspicious() {
        AiProductReviewDTO.Request request = buildRequest("本产品保本保息，稳定收益");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
        assertTrue(result.getSensitiveWords().contains("保本"));
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("review() 含敏感词 '增值' 返回 AI_SUSPICIOUS")
    void review_withZhiZeng_returnsSuspicious() {
        AiProductReviewDTO.Request request = buildRequest("产品可以实现资产增值，欢迎咨询");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
        assertTrue(result.getSensitiveWords().contains("增值"));
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("review() 含敏感词 '理财' 返回 AI_SUSPICIOUS")
    void review_withLiCai_returnsSuspicious() {
        AiProductReviewDTO.Request request = buildRequest("优质理财产品，推荐购买");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
        assertTrue(result.getSensitiveWords().contains("理财"));
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("review() 含敏感词 '收益' 返回 AI_SUSPICIOUS")
    void review_withShouYi_returnsSuspicious() {
        AiProductReviewDTO.Request request = buildRequest("稳定收益，值得信赖");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
        assertTrue(result.getSensitiveWords().contains("收益"));
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("review() 含敏感词 '稳赚' 返回 AI_SUSPICIOUS")
    void review_withWenZhuan_returnsSuspicious() {
        AiProductReviewDTO.Request request = buildRequest("稳赚不赔，零风险投资");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
        assertTrue(result.getSensitiveWords().contains("稳赚"));
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("review() 包含多个敏感词时全部命中")
    void review_multipleSensitiveWords_allHit() {
        AiProductReviewDTO.Request request = buildRequest("保本理财，增值收益，稳赚不赔");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
        assertTrue(result.getSensitiveWords().contains("保本"));
        assertTrue(result.getSensitiveWords().contains("增值"));
        assertTrue(result.getSensitiveWords().contains("理财"));
        assertTrue(result.getSensitiveWords().contains("收益"));
        assertTrue(result.getSensitiveWords().contains("稳赚"));
        assertTrue(result.getSensitiveWords().size() >= 5);
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("review() 空文案返回 AI_PASS")
    void review_emptyCopy_returnsPass() {
        AiProductReviewDTO.Request request = buildRequest("");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_PASS.getCode(), result.getReviewResult());
        assertTrue(result.getSensitiveWords().isEmpty());
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("review() null 文案返回 AI_PASS")
    void review_nullCopy_returnsPass() {
        AiProductReviewDTO.Request request = buildRequest(null);

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_PASS.getCode(), result.getReviewResult());
        assertTrue(result.getSensitiveWords().isEmpty());
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("review() 降级返回 AI_SUSPICIOUS 且置信度为 0")
    void review_fallback_returnsSuspiciousWithZeroConfidence() {
        AiProductReviewDTO.Request request = buildRequest("普通商品描述");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> fallback =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[2];
            return fallback.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getReviewResult());
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getConfidence()));
        assertTrue(result.getFallback());
    }

    @Test
    @DisplayName("review() 正常流程 message 包含 '外部模型审核完成'")
    void review_messageContainsExternalModel() {
        AiProductReviewDTO.Request request = buildRequest("这是一款优质商品");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("外部模型审核完成"));
    }

    @Test
    @DisplayName("review() 降级流程 message 包含 '降级为人工审核'")
    void review_fallbackMessageContainsManualReview() {
        AiProductReviewDTO.Request request = buildRequest("保本高收益产品");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> fallback =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[2];
            return fallback.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertNotNull(result.getMessage());
        assertTrue(result.getMessage().contains("降级为人工审核"));
    }

    @Test
    @DisplayName("review() 正常流程置信度为 0.85")
    void review_successConfidence() {
        AiProductReviewDTO.Request request = buildRequest("普通商品文案");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> action =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[1];
            return action.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("0.85").compareTo(result.getConfidence()));
        assertFalse(result.getFallback());
    }

    @Test
    @DisplayName("review() 降级流程 fallback 标志为 true")
    void review_fallbackFlagTrue() {
        AiProductReviewDTO.Request request = buildRequest("测试文案");

        when(circuitBreakerManager.execute(anyString(), any(), any())).thenAnswer(invocation -> {
            java.util.function.Supplier<AiProductReviewDTO.Response> fallback =
                    (java.util.function.Supplier<AiProductReviewDTO.Response>) invocation.getArguments()[2];
            return fallback.get();
        });

        AiProductReviewDTO.Response result = aiProductReviewService.review(request);

        assertNotNull(result);
        assertTrue(result.getFallback());
    }
}