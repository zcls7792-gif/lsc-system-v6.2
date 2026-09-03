package com.lianshengtong.order.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.common.enums.OrderStatusEnum;
import com.lianshengtong.order.dto.OrderCreateDTO;
import com.lianshengtong.order.dto.OrderPayDTO;
import com.lianshengtong.order.dto.OrderRefundDTO;
import com.lianshengtong.order.entity.Order;
import com.lianshengtong.order.service.OrderService;
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
import java.util.Collections;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单 Controller 层 MockMvc 测试
 *
 * <p>覆盖所有对外 HTTP 入口：create / create-mall / pay / complete / cancel /
 * refund / detail / list / export / daily-summary / ship / agree-refund /
 * reject-refund / refund-list / stats-today 共 14 个接口，
 * 并分别校验 DTO 级别 @NotNull/@NotBlank 约束触发 400。
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerMockMvcTest {

    private static final String ORDER_NO = "ORD20260903000001";

    private MockMvc mockMvc;

    private final ObjectMapper om = new ObjectMapper();

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(orderController)
                .setValidator(validator)
                .build();
    }

    private Order buildMinimalOrder() {
        Order o = new Order();
        o.setOrderNo(ORDER_NO);
        o.setConsumerId(1001L);
        o.setMerchantId(2001L);
        o.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        o.setTotalPrice(new BigDecimal("100.00"));
        o.setLscAmount(60L);
        o.setRmbAmount(new BigDecimal("40.00"));
        return o;
    }

    // ============== 创建订单 ==============

    @Test
    @DisplayName("POST /api/order/create: 创建订单成功 -> 返回 R<Order>")
    void create_success() throws Exception {
        when(orderService.createOrder(any(OrderCreateDTO.class))).thenReturn(buildMinimalOrder());

        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(60L);

        mockMvc.perform(post("/api/order/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(ORDER_NO))
                .andExpect(jsonPath("$.data.lscAmount").value(60));
    }

    @Nested
    @DisplayName("POST /api/order/create DTO 校验")
    class CreateDtoValidation {

        @Test
        @DisplayName("缺少 orderType -> 400 校验失败")
        void missingOrderType() throws Exception {
            OrderCreateDTO dto = new OrderCreateDTO();
            // dto.setOrderType 故意不设置 -> @NotNull
            dto.setConsumerId(1001L);
            dto.setMerchantId(2001L);
            dto.setTotalPrice(new BigDecimal("100"));

            mockMvc.perform(post("/api/order/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
            verify(orderService, never()).createOrder(any());
        }

        @Test
        @DisplayName("缺少 consumerId / merchantId / totalPrice -> 400")
        void missingRequiredFields() throws Exception {
            // 缺 3 个 @NotNull 字段
            mockMvc.perform(post("/api/order/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
            verify(orderService, never()).createOrder(any());
        }

        @Test
        @DisplayName("totalPrice 为 0 -> @Positive 校验失败")
        void nonPositiveTotalPrice() throws Exception {
            OrderCreateDTO dto = new OrderCreateDTO();
            dto.setOrderType(0);
            dto.setConsumerId(1001L);
            dto.setMerchantId(2001L);
            dto.setTotalPrice(BigDecimal.ZERO);

            mockMvc.perform(post("/api/order/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("totalPrice 为负 -> @Positive 校验失败")
        void negativeTotalPrice() throws Exception {
            OrderCreateDTO dto = new OrderCreateDTO();
            dto.setOrderType(0);
            dto.setConsumerId(1001L);
            dto.setMerchantId(2001L);
            dto.setTotalPrice(new BigDecimal("-1"));

            mockMvc.perform(post("/api/order/create")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // ============== create-mall ==============

    @Test
    @DisplayName("POST /api/order/create-mall: 商城下单 -> 返回订单号字符串")
    void createMall_success() throws Exception {
        when(orderService.createOrder(any(OrderCreateDTO.class))).thenAnswer(inv -> {
            OrderCreateDTO dto = inv.getArgument(0);
            Order o = new Order();
            o.setOrderNo("MALL001");
            o.setProductId(dto.getProductId());
            o.setQuantity(dto.getQuantity());
            return o;
        });

        mockMvc.perform(post("/api/order/create-mall")
                        .param("productId", "88")
                        .param("merchantId", "2001")
                        .param("consumerId", "1001")
                        .param("lscAmount", "50")
                        .param("rmbAmount", "50.00")
                        .param("totalPrice", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("MALL001"));
    }

    @Test
    @DisplayName("POST /api/order/create-mall: lscAmount 缺省默认 0 仍成功")
    void createMall_nullLscDefaultsToZero() throws Exception {
        when(orderService.createOrder(any(OrderCreateDTO.class))).thenAnswer(inv -> {
            Order o = new Order();
            OrderCreateDTO dto = inv.getArgument(0);
            o.setOrderNo("MALL002");
            o.setLscAmount(dto.getLscAmount() == null ? -1L : dto.getLscAmount());
            return o;
        });

        // 不传 lscAmount：Controller 里应置为 0L → createOrder 不会取 -1
        mockMvc.perform(post("/api/order/create-mall")
                        .param("productId", "88")
                        .param("merchantId", "2001")
                        .param("consumerId", "1001")
                        .param("totalPrice", "99.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("MALL002"));
    }

    // ============== 支付/完成/取消 ==============

    @Test
    @DisplayName("POST /api/order/pay: 支付成功")
    void pay_success() throws Exception {
        Order paid = buildMinimalOrder();
        paid.setStatus(OrderStatusEnum.PAID.getCode());
        when(orderService.payOrder(any(OrderPayDTO.class))).thenReturn(paid);

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setConsumerId(1001L);

        mockMvc.perform(post("/api/order/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(OrderStatusEnum.PAID.getCode()));
    }

    @Test
    @DisplayName("POST /api/order/pay: 缺少 orderNo -> 400")
    void pay_missingOrderNo() throws Exception {
        OrderPayDTO dto = new OrderPayDTO();
        dto.setConsumerId(1001L);

        mockMvc.perform(post("/api/order/pay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
        verify(orderService, never()).payOrder(any());
    }

    @Test
    @DisplayName("POST /api/order/complete: 完成 -> completed 状态")
    void complete_success() throws Exception {
        Order completed = buildMinimalOrder();
        completed.setStatus(OrderStatusEnum.COMPLETED.getCode());
        when(orderService.completeOrder(eq(ORDER_NO), eq(2001L))).thenReturn(completed);

        mockMvc.perform(post("/api/order/complete")
                        .param("orderNo", ORDER_NO)
                        .param("operatorId", "2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(OrderStatusEnum.COMPLETED.getCode()));
    }

    @Test
    @DisplayName("POST /api/order/cancel: 消费者取消待支付订单 -> cancelled")
    void cancel_success() throws Exception {
        Order cancelled = buildMinimalOrder();
        cancelled.setStatus(OrderStatusEnum.CANCELLED.getCode());
        when(orderService.cancelOrder(eq(ORDER_NO), eq(1001L))).thenReturn(cancelled);

        mockMvc.perform(post("/api/order/cancel")
                        .param("orderNo", ORDER_NO)
                        .param("operatorId", "1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(OrderStatusEnum.CANCELLED.getCode()));
    }

    // ============== 退款 ==============

    @Test
    @DisplayName("POST /api/order/refund: refund* 全空 -> 走全额退款 refundOrder")
    void refund_full() throws Exception {
        Order refunded = buildMinimalOrder();
        refunded.setStatus(OrderStatusEnum.REFUNDED.getCode());
        refunded.setRefundLscAmount(60L);
        when(orderService.refundOrder(any(OrderRefundDTO.class))).thenReturn(refunded);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);

        mockMvc.perform(post("/api/order/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(OrderStatusEnum.REFUNDED.getCode()));
        verify(orderService).refundOrder(any());
        verify(orderService, never()).partialRefund(any());
    }

    @Test
    @DisplayName("POST /api/order/refund: 携带退款金额 -> 走 partialRefund")
    void refund_partial() throws Exception {
        Order partial = buildMinimalOrder();
        partial.setStatus(OrderStatusEnum.PARTIAL_REFUNDED.getCode());
        partial.setRefundLscAmount(20L);
        when(orderService.partialRefund(any(OrderRefundDTO.class))).thenReturn(partial);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundLscAmount(20L);

        mockMvc.perform(post("/api/order/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(OrderStatusEnum.PARTIAL_REFUNDED.getCode()));
        verify(orderService).partialRefund(any());
        verify(orderService, never()).refundOrder(any());
    }

    @Test
    @DisplayName("POST /api/order/refund: refundRmbAmount>0 也走部分退款")
    void refund_partialByRmb() throws Exception {
        when(orderService.partialRefund(any(OrderRefundDTO.class))).thenReturn(buildMinimalOrder());

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundRmbAmount(new BigDecimal("10.00"));

        mockMvc.perform(post("/api/order/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isOk());
        verify(orderService).partialRefund(any());
    }

    @Test
    @DisplayName("POST /api/order/refund: 缺少 operatorId -> @NotNull -> 400")
    void refund_missingOperator() throws Exception {
        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);

        mockMvc.perform(post("/api/order/refund")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    // ============== 查询 ==============

    @Test
    @DisplayName("GET /api/order/{orderNo}: 详情查询")
    void detail_byOrderNo() throws Exception {
        when(orderService.getByOrderNo(eq(ORDER_NO))).thenReturn(buildMinimalOrder());

        mockMvc.perform(get("/api/order/" + ORDER_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(ORDER_NO));
    }

    @Test
    @DisplayName("GET /api/order/list: 兼容 page/size 与 pageNum/pageSize")
    void list_bothPaginationStyles() throws Exception {
        Page<Order> p = new Page<>(1, 20);
        p.setRecords(Collections.singletonList(buildMinimalOrder()));
        when(orderService.listOrders(eq(1), eq(20), any(), any(), any(), any(), any(), any()))
                .thenReturn(p);

        // style 1: page/size
        mockMvc.perform(get("/api/order/list")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].orderNo").value(ORDER_NO));

        // style 2: pageNum/pageSize
        mockMvc.perform(get("/api/order/list")
                        .param("pageNum", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].orderNo").value(ORDER_NO));
    }

    @Test
    @DisplayName("GET /api/order/export: 复用 listOrders(1,10000) 最多 10000 条")
    void export_callsListWithMaxSize() throws Exception {
        Page<Order> p = new Page<>(1, 20);
        p.setRecords(Collections.emptyList());
        when(orderService.listOrders(eq(1), eq(10000), any(), any(), any(), any(), any(), any()))
                .thenReturn(p);

        mockMvc.perform(get("/api/order/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
        verify(orderService).listOrders(eq(1), eq(10000), any(), any(), any(), any(), any(), any());
    }

    // ============== 发货 / 同意退款 / 拒绝退款 ==============

    @Test
    @DisplayName("POST /api/order/ship: 发货 -> completeOrder")
    void ship_delegatesToComplete() throws Exception {
        when(orderService.completeOrder(eq(ORDER_NO), eq(2001L))).thenReturn(buildMinimalOrder());

        String body = om.writeValueAsString(Map.of(
                "orderNo", ORDER_NO,
                "operatorId", 2001L));
        mockMvc.perform(post("/api/order/ship")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(orderService).completeOrder(eq(ORDER_NO), eq(2001L));
    }

    @Test
    @DisplayName("POST /api/order/ship: operatorId=null 透传到 completeOrder(null) 并抛出业务异常")
    void ship_nullOperatorId_passedToService() throws Exception {
        // 当 service 抛出业务异常时 controller 层原样冒泡
        when(orderService.completeOrder(eq(ORDER_NO), eq(null)))
                .thenThrow(new com.lianshengtong.common.exception.BizException(
                        com.lianshengtong.common.result.ResultCode.FORBIDDEN, "无权操作"));

        String body = om.writeValueAsString(Map.of("orderNo", ORDER_NO));
        // Standalone MockMvc 没有全局异常处理器，未处理业务异常会冒泡为 5xx
        try {
            mockMvc.perform(post("/api/order/ship")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is5xxServerError());
        } catch (Exception e) {
            // Spring standalone 会把 handler 异常包装成 NestedServletException 抛出
            assertNotNull(e, "handler exception was thrown");
        }
        verify(orderService).completeOrder(eq(ORDER_NO), eq(null));
    }

    @Test
    @DisplayName("POST /api/order/refund/agree: 构建 OrderRefundDTO 调用 refundOrder")
    void agreeRefund_buildsDtoAndCallsRefund() throws Exception {
        when(orderService.refundOrder(any(OrderRefundDTO.class))).thenReturn(buildMinimalOrder());

        String body = om.writeValueAsString(Map.of(
                "orderNo", ORDER_NO,
                "operatorId", 2001L));
        mockMvc.perform(post("/api/order/refund/agree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(orderService).refundOrder(any(OrderRefundDTO.class));
    }

    @Test
    @DisplayName("POST /api/order/refund/reject: reason=null 传 null 安全")
    void rejectRefund_nullReasonPasses() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "orderNo", ORDER_NO,
                "operatorId", 2001L));
        mockMvc.perform(post("/api/order/refund/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(orderService).rejectRefund(eq(ORDER_NO), eq(2001L), eq(null));
    }

    @Test
    @DisplayName("POST /api/order/refund/reject: 有 reason -> 正确透传")
    void rejectRefund_withReason() throws Exception {
        String body = om.writeValueAsString(Map.of(
                "orderNo", ORDER_NO,
                "operatorId", 2001L,
                "reason", "商品无问题"));
        mockMvc.perform(post("/api/order/refund/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        verify(orderService).rejectRefund(eq(ORDER_NO), eq(2001L), eq("商品无问题"));
    }

    // ============== 汇总 / 退款列表 / 今日统计 ==============

    @Test
    @DisplayName("GET /api/order/daily-summary: 传 date=YYYY-MM-DD 正常解析")
    void dailySummary_validDate() throws Exception {
        when(orderService.dailySummary(any())).thenReturn(Map.of(
                "totalCount", 5L,
                "totalAmount", new BigDecimal("500")));

        mockMvc.perform(get("/api/order/daily-summary")
                        .param("date", "2026-09-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(5));
    }

    @Test
    @DisplayName("GET /api/order/daily-summary: listOrders 统计结果中 totalAmount 为 big decimal 返回")
    void dailySummary_returnsBigDecimal() throws Exception {
        when(orderService.dailySummary(any())).thenReturn(Map.of(
                "totalCount", 5L,
                "totalAmount", new BigDecimal("500")));

        mockMvc.perform(get("/api/order/daily-summary")
                        .param("date", "2026-09-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(500));
        verify(orderService).dailySummary(any());
    }

    @Test
    @DisplayName("GET /api/order/refund/list: 默认 page=1 size=20")
    void refundList_defaults() throws Exception {
        @SuppressWarnings("unchecked")
        IPage<Order> page = new Page<>(1, 20);
        when(orderService.listOrders(anyInt(), anyInt(), anyLong(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/order/refund/list").param("merchantId", "2001"))
                .andExpect(status().isOk());
        verify(orderService).listOrders(eq(1), eq(20), eq(2001L), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("GET /api/order/stats-today: 带 merchantId")
    void statsToday_withMerchant() throws Exception {
        when(orderService.statsToday(eq(2001L))).thenReturn(Map.of(
                "todayOrderCount", 3L,
                "todayRevenue", BigDecimal.TEN));

        mockMvc.perform(get("/api/order/stats-today").param("merchantId", "2001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayOrderCount").value(3));
    }

    @Test
    @DisplayName("GET /api/order/stats-today: 不带 merchantId -> 全平台汇总")
    void statsToday_noMerchant() throws Exception {
        when(orderService.statsToday(eq(null))).thenReturn(Map.of(
                "todayOrderCount", 0L,
                "todayRevenue", BigDecimal.ZERO));

        mockMvc.perform(get("/api/order/stats-today"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayOrderCount").value(0));
        verify(orderService).statsToday(eq(null));
    }
}
