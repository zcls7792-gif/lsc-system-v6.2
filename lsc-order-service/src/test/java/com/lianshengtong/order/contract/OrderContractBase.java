package com.lianshengtong.order.contract;

import com.lianshengtong.order.controller.OrderController;
import com.lianshengtong.order.dto.OrderCreateDTO;
import com.lianshengtong.order.entity.Order;
import com.lianshengtong.order.service.OrderService;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Spring Cloud Contract 契约测试基底类 — lsc-order-service (Provider for mall-service)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public abstract class OrderContractBase {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @BeforeEach
    public void setup() {
        // --- createMallOrder 契约 ---
        Order mockOrder = new Order();
        mockOrder.setOrderNo("MALL-20260901-00001");
        mockOrder.setOrderType(0);
        mockOrder.setConsumerId(10001L);
        mockOrder.setMerchantId(20001L);
        mockOrder.setProductId(101L);
        mockOrder.setQuantity(1);
        mockOrder.setTotalPrice(new BigDecimal("69.90"));
        mockOrder.setLscAmount(5000L);
        mockOrder.setRmbAmount(new BigDecimal("19.90"));
        mockOrder.setStatus(0);
        mockOrder.setCreatedAt(LocalDateTime.of(2026, 9, 1, 11, 0, 0));

        when(orderService.createOrder(any(OrderCreateDTO.class))).thenReturn(mockOrder);

        RestAssuredMockMvc.standaloneSetup(orderController);
    }
}
