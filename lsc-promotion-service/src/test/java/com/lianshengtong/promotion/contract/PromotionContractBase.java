package com.lianshengtong.promotion.contract;

import com.lianshengtong.promotion.controller.PromotionController;
import com.lianshengtong.promotion.service.PromotionService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.mockito.Mockito.*;

/**
 * Spring Cloud Contract 契约测试基底类 — lsc-promotion-service (Provider for order-service)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class PromotionContractBase {

    @Mock
    private PromotionService promotionService;

    @InjectMocks
    private PromotionController promotionController;

    @BeforeEach
    public void setup() {
        // --- notifyFirstOrder 契约 (void 返回) ---
        doNothing().when(promotionService)
                .notifyFirstOrder(
                        eq(10001L),
                        eq("ORD-20260901-00100"),
                        eq(new BigDecimal("168.00")),
                        eq(2),
                        any(BigDecimal.class)
                );

        RestAssuredMockMvc.standaloneSetup(promotionController);
    }
}
