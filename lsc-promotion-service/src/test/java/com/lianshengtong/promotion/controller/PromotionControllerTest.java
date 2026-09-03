package com.lianshengtong.promotion.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.promotion.dto.FirstOrderCheckDTO;
import com.lianshengtong.promotion.dto.RewardResultDTO;
import com.lianshengtong.promotion.dto.RollbackRewardDTO;
import com.lianshengtong.promotion.entity.PromotionPending;
import com.lianshengtong.promotion.service.PromotionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PromotionController MockMvc 测试。
 * 覆盖 check-first-order / calc-reward / rollback / pending-list / first-order-notify
 * 以及对应 DTO @NotNull 校验。
 */
@ExtendWith(MockitoExtension.class)
class PromotionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock private PromotionService promotionService;
    @InjectMocks private PromotionController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean v = new LocalValidatorFactoryBean();
        v.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(v).build();
    }

    private FirstOrderCheckDTO buildDto() {
        FirstOrderCheckDTO dto = new FirstOrderCheckDTO();
        dto.setUserId(1001L);
        dto.setReferrerId(2001L);
        dto.setOrderNo("ORD001");
        dto.setOrderAmount(new BigDecimal("100.00"));
        dto.setOrderStatus(2);
        return dto;
    }

    private RewardResultDTO rewardResult() {
        RewardResultDTO r = new RewardResultDTO();
        r.setFirstOrder(true);
        r.setRewardAmount(new BigDecimal("10.00"));
        r.setReferrerId(2001L);
        r.setSuccess(true);
        return r;
    }

    @Nested
    @DisplayName("POST /api/promotion/check-first-order")
    class CheckFirstOrderApi {

        @Test
        @DisplayName("有效DTO -> 透传 service，返回首单 true")
        void validDto() throws Exception {
            when(promotionService.checkFirstOrder(any())).thenReturn(rewardResult());

            mockMvc.perform(post("/api/promotion/check-first-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(buildDto())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.firstOrder").value(true))
                    .andExpect(jsonPath("$.data.rewardAmount").value(10.00));
        }

        @Test
        @DisplayName("缺少 consumerId -> @NotNull -> 400")
        void missingConsumerId() throws Exception {
            FirstOrderCheckDTO dto = buildDto();
            dto.setUserId(null);

            mockMvc.perform(post("/api/promotion/check-first-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
            verify(promotionService, never()).checkFirstOrder(any());
        }

        @Test
        @DisplayName("缺少 orderNo/orderAmount/orderStatus -> 400")
        void missingMultipleRequired() throws Exception {
            mockMvc.perform(post("/api/promotion/check-first-order")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/promotion/calc-reward")
    class CalcRewardApi {

        @Test
        @DisplayName("有效DTO -> 调用 calcReward 并返回奖励数")
        void validDto() throws Exception {
            RewardResultDTO res = rewardResult();
            res.setRewardAmount(new BigDecimal("50.00"));
            res.setFirstOrderAmount(new BigDecimal("500.00"));
            when(promotionService.calcReward(any())).thenReturn(res);

            FirstOrderCheckDTO dto = buildDto();
            mockMvc.perform(post("/api/promotion/calc-reward")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rewardAmount").value(50.00));
        }

        @Test
        @DisplayName("DTO orderAmount < 0 -> 触发 @NegativeOrZero 不成立时: Negative，此处 DTO 无限制则通过 service 抛异常")
        void negativeOrderAmountPassedToService() throws Exception {
            FirstOrderCheckDTO dto = buildDto();
            dto.setOrderAmount(new BigDecimal("-1"));
            when(promotionService.calcReward(any()))
                    .thenThrow(new com.lianshengtong.common.exception.BizException("订单金额必须大于0"));

            try {
                mockMvc.perform(post("/api/promotion/calc-reward")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(om.writeValueAsString(dto)))
                        .andExpect(status().is5xxServerError());
            } catch (Exception e) {
                // 独立 MockMvc 会把 BizException 冒泡
                assertNotNull(e);
            }
            verify(promotionService).calcReward(any());
        }
    }

    @Nested
    @DisplayName("POST /api/promotion/rollback")
    class RollbackApi {

        @Test
        @DisplayName("有效DTO -> 调用 rollbackReward 返回 OK")
        void validDto() throws Exception {
            RollbackRewardDTO dto = new RollbackRewardDTO();
            dto.setOrderNo("ORD001");
            dto.setUserId(1001L);

            mockMvc.perform(post("/api/promotion/rollback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            verify(promotionService).rollbackReward(any());
        }

        @Test
        @DisplayName("缺少 @NotNull userId/orderNo -> 400")
        void missingRequired() throws Exception {
            RollbackRewardDTO dto = new RollbackRewardDTO();
            mockMvc.perform(post("/api/promotion/rollback")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
            verify(promotionService, never()).rollbackReward(any());
        }
    }

    @Nested
    @DisplayName("GET /api/promotion/pending-list")
    class PendingListApi {
        @Test
        @DisplayName("不传过滤 -> 默认 1/20 status=null")
        void defaultParams() throws Exception {
            Page<PromotionPending> p = new Page<>(1, 20);
            p.setRecords(List.of());
            when(promotionService.pendingList(1, 20, null)).thenReturn(p);

            mockMvc.perform(get("/api/promotion/pending-list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.current").value(1));
            verify(promotionService).pendingList(1, 20, null);
        }

        @Test
        @DisplayName("传 status=1 + page=2 + size=50")
        void withFilters() throws Exception {
            Page<PromotionPending> p = new Page<>(2, 50);
            p.setRecords(List.of());
            when(promotionService.pendingList(2, 50, 1)).thenReturn(p);

            mockMvc.perform(get("/api/promotion/pending-list")
                            .param("page", "2").param("size", "50").param("status", "1"))
                    .andExpect(status().isOk());
            verify(promotionService).pendingList(2, 50, 1);
        }
    }

    @Nested
    @DisplayName("POST /api/promotion/first-order-notify")
    class FirstOrderNotifyApi {
        @Test
        @DisplayName("全部必填参数齐全 refundAmount 可选")
        void allRequired_present() throws Exception {
            mockMvc.perform(post("/api/promotion/first-order-notify")
                            .param("consumerId", "1001")
                            .param("orderNo", "ORD001")
                            .param("orderAmount", "100.00")
                            .param("orderStatus", "2"))
                    .andExpect(status().isOk());
            verify(promotionService).notifyFirstOrder(
                    eq(1001L), eq("ORD001"),
                    eq(new BigDecimal("100.00")), eq(2), eq(null));
        }

        @Test
        @DisplayName("带 refundAmount -> 透传 BigDecimal")
        void withRefundAmount() throws Exception {
            mockMvc.perform(post("/api/promotion/first-order-notify")
                            .param("consumerId", "1001")
                            .param("orderNo", "ORD002")
                            .param("orderAmount", "200.00")
                            .param("orderStatus", "3")
                            .param("refundAmount", "30.00"))
                    .andExpect(status().isOk());
            verify(promotionService).notifyFirstOrder(
                    eq(1001L), eq("ORD002"),
                    eq(new BigDecimal("200.00")), eq(3),
                    eq(new BigDecimal("30.00")));
        }
    }

    // ============== Fallback / 降级相关补充（Controller 层通过 service 抛业务异常不崩） ==============

    @Nested
    @DisplayName("Fallback / 降级：controller 层不 catch 业务异常")
    class FallbackPropagation {

        @Test
        @DisplayName("notifyFirstOrder 抛异常 -> 仍由 Controller 原样冒泡不吞")
        void notifyFirstOrder_exceptionPropagates() throws Exception {
            doThrow(new com.lianshengtong.common.exception.BizException("重复通知"))
                    .when(promotionService).notifyFirstOrder(anyLong(), anyString(), any(), anyInt(), any());
            try {
                mockMvc.perform(post("/api/promotion/first-order-notify")
                                .param("consumerId", "1")
                                .param("orderNo", "O")
                                .param("orderAmount", "1")
                                .param("orderStatus", "2"))
                        .andExpect(status().is5xxServerError());
            } catch (Exception e) {
                assertNotNull(e);
            }
        }
    }
}
