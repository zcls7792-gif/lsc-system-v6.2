package com.lianshengtong.order.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.dto.LscLedgerOpDTO;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.order.dto.OrderCreateDTO;
import com.lianshengtong.order.dto.OrderPayDTO;
import com.lianshengtong.order.dto.OrderRefundDTO;
import com.lianshengtong.order.entity.Order;
import com.lianshengtong.order.feign.LscLedgerFeignClient;
import com.lianshengtong.order.feign.PromotionFeignClient;
import com.lianshengtong.order.mapper.OrderMapper;
import com.lianshengtong.common.enums.OrderStatusEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 订单服务核心业务路径单元测试
 * <p>
 * 覆盖 OrderServiceImpl 的关键路径：
 * <ul>
 *   <li>createOrder: 混合支付拆分（LSC + 人民币）</li>
 *   <li>cancelOrder: 状态机校验与权限校验</li>
 *   <li>getByOrderNo: 订单不存在及退款金额兜底</li>
 *   <li>refundOrder: 全额退款流程（Feign 调用 + 锁）</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单服务单元测试")
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;
    @Mock
    private LscLedgerFeignClient lscLedgerFeignClient;
    @Mock
    private PromotionFeignClient promotionFeignClient;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    @InjectMocks
    private OrderServiceImpl orderService;

    private static final String ORDER_NO = "OD20260805000001";

    // ============== createOrder 测试 ==============

    @Test
    @DisplayName("createOrder: 混合支付拆分 LSC + 人民币")
    void createOrder_mixedPayment() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setProductName("测试商品");
        dto.setQuantity(1);
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(60L);

        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order order = orderService.createOrder(dto);

        assertNotNull(order);
        assertEquals(60L, order.getLscAmount());
        assertEquals(0, new BigDecimal("40.00").compareTo(order.getRmbAmount()),
                "人民币 = 100 - 60 = 40");
        assertEquals(OrderStatusEnum.PENDING_PAY.getCode(), order.getStatus());
        // 退款金额初始化
        assertEquals(0L, order.getRefundLscAmount());
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getRefundRmbAmount()));
        verify(orderMapper).insert(any(Order.class));
    }

    @Test
    @DisplayName("createOrder: LSC 数量超过订单总价应抛异常")
    void createOrder_lscExceedsTotal() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(200L);

        BizException ex = assertThrows(BizException.class, () -> orderService.createOrder(dto));
        assertTrue(ex.getMessage().contains("LSC支付数量不能超过订单总价"));
    }

    @Test
    @DisplayName("createOrder: LSC 为负数应抛异常")
    void createOrder_negativeLsc() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(-10L);

        assertThrows(BizException.class, () -> orderService.createOrder(dto));
    }

    @Test
    @DisplayName("createOrder: LSC 为空时按 0 处理")
    void createOrder_nullLsc() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(null);

        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order order = orderService.createOrder(dto);
        assertEquals(0L, order.getLscAmount());
        assertEquals(0, new BigDecimal("100.00").compareTo(order.getRmbAmount()));
    }

    // ============== getByOrderNo 测试 ==============

    @Test
    @DisplayName("getByOrderNo: 订单不存在抛异常")
    void getByOrderNo_notFound() {
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> orderService.getByOrderNo(ORDER_NO));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("getByOrderNo: 历史数据退款字段为 null 时填充默认值")
    void getByOrderNo_refundFieldsNull() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setRefundLscAmount(null);
        order.setRefundRmbAmount(null);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        Order result = orderService.getByOrderNo(ORDER_NO);
        assertEquals(0L, result.getRefundLscAmount(), "退款 LSC 应默认为 0");
        assertEquals(0, BigDecimal.ZERO.compareTo(result.getRefundRmbAmount()),
                "退款人民币应默认为 0");
    }

    // ============== cancelOrder 测试 ==============

    @Test
    @DisplayName("cancelOrder: 非待支付状态不可取消")
    void cancelOrder_invalidStatus() {
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        BizException ex = assertThrows(BizException.class,
                () -> orderService.cancelOrder(ORDER_NO, 1001L));
        assertTrue(ex.getMessage().contains("仅待支付订单可取消"));
    }

    @Test
    @DisplayName("cancelOrder: 非订单参与方不可取消")
    void cancelOrder_notParticipant() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        BizException ex = assertThrows(BizException.class,
                () -> orderService.cancelOrder(ORDER_NO, 9999L));
        assertTrue(ex.getMessage().contains("非订单参与方"));
    }

    @Test
    @DisplayName("cancelOrder: 消费者可取消待支付订单")
    void cancelOrder_consumerCancelSuccess() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        Order result = orderService.cancelOrder(ORDER_NO, 1001L);
        assertEquals(OrderStatusEnum.CANCELLED.getCode(), result.getStatus());
        verify(orderMapper).updateById(any(Order.class));
    }

    @Test
    @DisplayName("cancelOrder: 商家可取消待支付订单")
    void cancelOrder_merchantCancelSuccess() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        Order result = orderService.cancelOrder(ORDER_NO, 2001L);
        assertEquals(OrderStatusEnum.CANCELLED.getCode(), result.getStatus());
    }

    // ============== refundOrder 测试 ==============

    @Test
    @DisplayName("refundOrder: 已退款订单不可重复退款")
    void refundOrder_alreadyRefunded() {
        Order order = buildPaidOrder();
        order.setRefundLscAmount(50L);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.refundOrder(dto));
        assertTrue(ex.getMessage().contains("已发生退款"));
    }

    @Test
    @DisplayName("refundOrder: 非商家不可发起退款")
    void refundOrder_notMerchant() {
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(9999L);

        BizException ex = assertThrows(BizException.class, () -> orderService.refundOrder(dto));
        assertTrue(ex.getMessage().contains("仅订单商家可发起退款"));
    }

    @Test
    @DisplayName("refundOrder: 待支付订单不可退款")
    void refundOrder_pendingPayStatus() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        order.setLscAmount(100L);
        order.setRmbAmount(new BigDecimal("0"));
        order.setRefundLscAmount(0L);
        order.setRefundRmbAmount(BigDecimal.ZERO);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.refundOrder(dto));
        assertTrue(ex.getMessage().contains("订单当前状态不可退款"));
    }

    @Test
    @DisplayName("refundOrder: LSC 全额退款流程成功")
    void refundOrder_lscOnlyRefundSuccess() throws Exception {
        Order order = buildPaidOrder();
        order.setLscAmount(100L);
        order.setRmbAmount(BigDecimal.ZERO); // 仅 LSC 支付
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.refundLsc(any(LscLedgerOpDTO.class)))
                .thenReturn(R.ok());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);

        Order result = orderService.refundOrder(dto);

        assertEquals(OrderStatusEnum.REFUNDED.getCode(), result.getStatus());
        assertEquals(100L, result.getRefundLscAmount());

        // 验证 Feign 调用参数
        ArgumentCaptor<LscLedgerOpDTO> captor = ArgumentCaptor.forClass(LscLedgerOpDTO.class);
        verify(lscLedgerFeignClient).refundLsc(captor.capture());
        LscLedgerOpDTO opDTO = captor.getValue();
        assertEquals(100L, opDTO.getAvailableDelta(), "退款金额应为正数退回");
        assertEquals(order.getConsumerId(), opDTO.getUserId());
        assertEquals(order.getMerchantId(), opDTO.getCounterpartyId());

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("refundOrder: 账本服务无响应应抛异常并释放锁")
    void refundOrder_ledgerNoResponse() throws Exception {
        Order order = buildPaidOrder();
        order.setLscAmount(100L);
        order.setRmbAmount(BigDecimal.ZERO);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.refundLsc(any())).thenReturn(null);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.refundOrder(dto));
        assertTrue(ex.getMessage().contains("账本服务无响应"));
        verify(rLock).unlock();
    }

    // ============== payOrder 测试 ==============

    @Test
    @DisplayName("payOrder: LSC+RMB 混合支付成功")
    void payOrder_successLscAndRmb() throws Exception {
        setLockFields();
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        order.setLscAmount(60L);
        order.setRmbAmount(new BigDecimal("40.00"));
        order.setRefundLscAmount(0L);
        order.setRefundRmbAmount(BigDecimal.ZERO);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.payLsc(any(LscLedgerOpDTO.class))).thenReturn(R.ok());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setConsumerId(1001L);

        Order result = orderService.payOrder(dto);
        assertEquals(OrderStatusEnum.PAID.getCode(), result.getStatus());
        assertNotNull(result.getPayTime());
        verify(lscLedgerFeignClient).payLsc(any(LscLedgerOpDTO.class));
        verify(orderMapper).updateById(any(Order.class));
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("payOrder: 获取锁失败抛异常")
    void payOrder_lockAcquisitionFailure() throws Exception {
        setLockFields();
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        order.setLscAmount(0L);
        order.setRmbAmount(new BigDecimal("10.00"));
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setConsumerId(1001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.payOrder(dto));
        assertTrue(ex.getMessage().contains("订单支付处理中"));
    }

    @Test
    @DisplayName("payOrder: 非订单消费者不可支付")
    void payOrder_wrongConsumer() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setConsumerId(9999L);

        BizException ex = assertThrows(BizException.class, () -> orderService.payOrder(dto));
        assertTrue(ex.getMessage().contains("仅下单消费者可支付"));
    }

    @Test
    @DisplayName("payOrder: 非待支付状态不可支付")
    void payOrder_nonPendingStatus() {
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setConsumerId(1001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.payOrder(dto));
        assertTrue(ex.getMessage().contains("订单当前状态不可支付"));
    }

    @Test
    @DisplayName("payOrder: LSC 账本服务无响应抛异常")
    void payOrder_lscLedgerNoResponse() throws Exception {
        setLockFields();
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        order.setLscAmount(50L);
        order.setRmbAmount(BigDecimal.ZERO);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.payLsc(any(LscLedgerOpDTO.class))).thenReturn(null);

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setConsumerId(1001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.payOrder(dto));
        assertTrue(ex.getMessage().contains("账本服务无响应"));
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("payOrder: LSC 账本返回失败消息抛异常")
    void payOrder_lscLedgerFailMessage() throws Exception {
        setLockFields();
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        order.setLscAmount(50L);
        order.setRmbAmount(BigDecimal.ZERO);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.payLsc(any(LscLedgerOpDTO.class))).thenReturn(R.fail("余额不足"));

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setConsumerId(1001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.payOrder(dto));
        assertTrue(ex.getMessage().contains("余额不足"));
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("payOrder: 支付被中断抛异常")
    void payOrder_interruptedException() throws Exception {
        setLockFields();
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        order.setLscAmount(0L);
        order.setRmbAmount(new BigDecimal("10.00"));
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("模拟中断"));
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        OrderPayDTO dto = new OrderPayDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setConsumerId(1001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.payOrder(dto));
        assertTrue(ex.getMessage().contains("订单支付被中断"));
    }

    // ============== completeOrder 测试 ==============

    @Test
    @DisplayName("completeOrder: 成功完成订单并通知推广服务")
    void completeOrder_successWithNotification() {
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(promotionFeignClient.notifyFirstOrder(anyLong(), anyString(), any(), anyInt(), any()))
                .thenReturn(R.ok());

        Order result = orderService.completeOrder(ORDER_NO, 2001L);
        assertEquals(OrderStatusEnum.COMPLETED.getCode(), result.getStatus());
        assertNotNull(result.getCompletedAt());
        verify(promotionFeignClient).notifyFirstOrder(anyLong(), anyString(), any(), anyInt(), any());
        verify(orderMapper).updateById(any(Order.class));
    }

    @Test
    @DisplayName("completeOrder: 非商家不可确认完成")
    void completeOrder_wrongMerchant() {
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        BizException ex = assertThrows(BizException.class,
                () -> orderService.completeOrder(ORDER_NO, 9999L));
        assertTrue(ex.getMessage().contains("仅订单商家可确认完成"));
    }

    @Test
    @DisplayName("completeOrder: 非已支付状态不可完成")
    void completeOrder_nonPaidStatus() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        BizException ex = assertThrows(BizException.class,
                () -> orderService.completeOrder(ORDER_NO, 2001L));
        assertTrue(ex.getMessage().contains("订单当前状态不可完成"));
    }

    @Test
    @DisplayName("completeOrder: 通知推广服务失败不阻断主流程")
    void completeOrder_notificationFailureTolerance() {
        Order order = buildPaidOrder();
        order.setTotalPrice(new BigDecimal("100.00"));
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(promotionFeignClient.notifyFirstOrder(anyLong(), anyString(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("推广服务不可用"));

        Order result = orderService.completeOrder(ORDER_NO, 2001L);
        assertEquals(OrderStatusEnum.COMPLETED.getCode(), result.getStatus());
        verify(orderMapper).updateById(any(Order.class));
    }

    @Test
    @DisplayName("completeOrder: 订单金额为 null 时通知使用默认值")
    void completeOrder_nullTotalPrice() {
        Order order = buildPaidOrder();
        order.setTotalPrice(null);
        order.setRefundRmbAmount(null);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);
        when(promotionFeignClient.notifyFirstOrder(anyLong(), anyString(), any(), anyInt(), any()))
                .thenReturn(R.ok());

        Order result = orderService.completeOrder(ORDER_NO, 2001L);
        assertEquals(OrderStatusEnum.COMPLETED.getCode(), result.getStatus());
        ArgumentCaptor<BigDecimal> amountCaptor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(promotionFeignClient).notifyFirstOrder(anyLong(), anyString(),
                amountCaptor.capture(), anyInt(), any());
        assertEquals(0, BigDecimal.ZERO.compareTo(amountCaptor.getValue()));
    }

    // ============== refundOrder 新增测试 ==============

    @Test
    @DisplayName("refundOrder: 已完成状态订单可全额退款")
    void refundOrder_completedStatusRefund() throws Exception {
        setLockFields();
        Order order = buildCompletedOrder();
        order.setLscAmount(100L);
        order.setRmbAmount(BigDecimal.ZERO);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.refundLsc(any(LscLedgerOpDTO.class))).thenReturn(R.ok());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);

        Order result = orderService.refundOrder(dto);
        assertEquals(OrderStatusEnum.REFUNDED.getCode(), result.getStatus());
        assertEquals(100L, result.getRefundLscAmount());
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("refundOrder: RMB 退款路径")
    void refundOrder_rmbRefundPath() throws Exception {
        setLockFields();
        Order order = buildPaidOrder();
        order.setLscAmount(0L);
        order.setRmbAmount(new BigDecimal("50.00"));
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);

        Order result = orderService.refundOrder(dto);
        assertEquals(OrderStatusEnum.REFUNDED.getCode(), result.getStatus());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getRefundRmbAmount()));
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("refundOrder: 获取锁失败抛异常")
    void refundOrder_lockFailure() throws Exception {
        setLockFields();
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.refundOrder(dto));
        assertTrue(ex.getMessage().contains("订单退款处理中"));
    }

    @Test
    @DisplayName("refundOrder: 退款被中断抛异常")
    void refundOrder_interruptedException() throws Exception {
        setLockFields();
        Order order = buildPaidOrder();
        order.setLscAmount(0L);
        order.setRmbAmount(new BigDecimal("50.00"));
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("模拟中断"));
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);

        BizException ex = assertThrows(BizException.class, () -> orderService.refundOrder(dto));
        assertTrue(ex.getMessage().contains("订单退款被中断"));
    }

    // ============== partialRefund 测试 ==============

    @Test
    @DisplayName("partialRefund: 部分退款成功")
    void partialRefund_success() throws Exception {
        setLockFields();
        Order order = buildPaidOrder();
        order.setLscAmount(100L);
        order.setRefundLscAmount(0L);
        order.setRefundRmbAmount(BigDecimal.ZERO);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.refundLsc(any(LscLedgerOpDTO.class))).thenReturn(R.ok());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundLscAmount(30L);
        dto.setRefundRmbAmount(BigDecimal.ZERO);

        Order result = orderService.partialRefund(dto);
        assertEquals(30L, result.getRefundLscAmount());
        assertEquals(OrderStatusEnum.PARTIAL_REFUNDED.getCode(), result.getStatus());
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("partialRefund: 累计退款 LSC 超过实付抛异常")
    void partialRefund_cumulativeLscLimitExceeded() {
        Order order = buildPaidOrder();
        order.setLscAmount(100L);
        order.setRefundLscAmount(80L);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundLscAmount(30L);
        dto.setRefundRmbAmount(BigDecimal.ZERO);

        BizException ex = assertThrows(BizException.class, () -> orderService.partialRefund(dto));
        assertTrue(ex.getMessage().contains("累计退款LSC超过实付LSC"));
    }

    @Test
    @DisplayName("partialRefund: 累计退款人民币超过实付抛异常")
    void partialRefund_cumulativeRmbLimitExceeded() {
        Order order = buildPaidOrder();
        order.setLscAmount(0L);
        order.setRmbAmount(new BigDecimal("50.00"));
        order.setRefundRmbAmount(new BigDecimal("40.00"));
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundLscAmount(0L);
        dto.setRefundRmbAmount(new BigDecimal("20.00"));

        BizException ex = assertThrows(BizException.class, () -> orderService.partialRefund(dto));
        assertTrue(ex.getMessage().contains("累计退款人民币超过实付人民币"));
    }

    @Test
    @DisplayName("partialRefund: 获取锁失败抛异常")
    void partialRefund_lockFailure() throws Exception {
        setLockFields();
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundLscAmount(10L);
        dto.setRefundRmbAmount(BigDecimal.ZERO);

        BizException ex = assertThrows(BizException.class, () -> orderService.partialRefund(dto));
        assertTrue(ex.getMessage().contains("订单退款处理中"));
    }

    @Test
    @DisplayName("partialRefund: 退款金额为零抛异常")
    void partialRefund_zeroAmount() {
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundLscAmount(0L);
        dto.setRefundRmbAmount(BigDecimal.ZERO);

        BizException ex = assertThrows(BizException.class, () -> orderService.partialRefund(dto));
        assertTrue(ex.getMessage().contains("本次退款金额必须大于0"));
    }

    @Test
    @DisplayName("partialRefund: 非可退款状态抛异常")
    void partialRefund_invalidStatus() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        order.setLscAmount(100L);
        order.setRefundLscAmount(0L);
        order.setRefundRmbAmount(BigDecimal.ZERO);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundLscAmount(10L);

        BizException ex = assertThrows(BizException.class, () -> orderService.partialRefund(dto));
        assertTrue(ex.getMessage().contains("订单当前状态不可部分退款"));
    }

    @Test
    @DisplayName("partialRefund: 退款被中断抛异常")
    void partialRefund_interruptedException() throws Exception {
        setLockFields();
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("模拟中断"));
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundLscAmount(10L);

        BizException ex = assertThrows(BizException.class, () -> orderService.partialRefund(dto));
        assertTrue(ex.getMessage().contains("订单退款被中断"));
    }

    @Test
    @DisplayName("partialRefund: 全额退完自动变为已退款状态")
    void partialRefund_fullyRefunded() throws Exception {
        setLockFields();
        Order order = buildPaidOrder();
        order.setLscAmount(100L);
        order.setRmbAmount(new BigDecimal("50.00"));
        order.setRefundLscAmount(0L);
        order.setRefundRmbAmount(BigDecimal.ZERO);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.refundLsc(any(LscLedgerOpDTO.class))).thenReturn(R.ok());
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        OrderRefundDTO dto = new OrderRefundDTO();
        dto.setOrderNo(ORDER_NO);
        dto.setOperatorId(2001L);
        dto.setRefundLscAmount(100L);
        dto.setRefundRmbAmount(new BigDecimal("50.00"));

        Order result = orderService.partialRefund(dto);
        assertEquals(OrderStatusEnum.REFUNDED.getCode(), result.getStatus());
        assertEquals(100L, result.getRefundLscAmount());
        assertEquals(0, new BigDecimal("50.00").compareTo(result.getRefundRmbAmount()));
        verify(rLock).unlock();
    }

    // ============== listOrders 测试 ==============

    @Test
    @DisplayName("listOrders: 带用户和状态过滤")
    void listOrders_withFilters() {
        Page<Order> mockPage = new Page<>(1, 20);
        mockPage.setRecords(Collections.emptyList());
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        IPage<Order> result = orderService.listOrders(1, 20, 1001L, 1);
        assertNotNull(result);
        assertEquals(0, result.getTotal());
        verify(orderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listOrders: 带日期过滤")
    void listOrders_withDates() {
        Page<Order> mockPage = new Page<>(1, 20);
        mockPage.setRecords(Collections.emptyList());
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        IPage<Order> result = orderService.listOrders(1, 20, 1001L, null,
                null, null, "2026-08-01", "2026-08-31");
        assertNotNull(result);
        verify(orderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listOrders: 默认分页参数")
    void listOrders_defaultPagination() {
        Page<Order> mockPage = new Page<>(1, 20);
        mockPage.setRecords(Collections.emptyList());
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        IPage<Order> result = orderService.listOrders(null, null, null, null);
        assertNotNull(result);
        verify(orderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listOrders: 带订单号和类型过滤")
    void listOrders_withOrderNoAndType() {
        Page<Order> mockPage = new Page<>(1, 20);
        mockPage.setRecords(Collections.emptyList());
        when(orderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        IPage<Order> result = orderService.listOrders(1, 20, null, null,
                "OD", 0, null, null);
        assertNotNull(result);
        verify(orderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ============== dailySummary 测试 ==============

    @Test
    @DisplayName("dailySummary: 正常聚合")
    void dailySummary_normalAggregation() {
        Order order1 = new Order();
        order1.setOrderNo("OD001");
        order1.setStatus(OrderStatusEnum.PAID.getCode());
        order1.setTotalPrice(new BigDecimal("100.00"));
        order1.setPayTime(LocalDateTime.now());

        Order order2 = new Order();
        order2.setOrderNo("OD002");
        order2.setStatus(OrderStatusEnum.COMPLETED.getCode());
        order2.setTotalPrice(new BigDecimal("200.00"));
        order2.setPayTime(LocalDateTime.now());

        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(java.util.Arrays.asList(order1, order2));

        Map<String, Object> result = orderService.dailySummary(LocalDate.now());
        assertNotNull(result);
        assertEquals(2L, result.get("totalCount"));
        assertEquals(0, new BigDecimal("300.00").compareTo((BigDecimal) result.get("totalAmount")));
    }

    @Test
    @DisplayName("dailySummary: 订单总价为 null 时跳过累加")
    void dailySummary_nullTotalPrice() {
        Order order = new Order();
        order.setOrderNo("OD001");
        order.setStatus(OrderStatusEnum.PAID.getCode());
        order.setTotalPrice(null);
        order.setPayTime(LocalDateTime.now());

        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(order));

        Map<String, Object> result = orderService.dailySummary(LocalDate.now());
        assertNotNull(result);
        assertEquals(1L, result.get("totalCount"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) result.get("totalAmount")));
    }

    @Test
    @DisplayName("dailySummary: 日期为 null 使用当天")
    void dailySummary_nullDate() {
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        Map<String, Object> result = orderService.dailySummary(null);
        assertNotNull(result);
        assertEquals(0L, result.get("totalCount"));
    }

    // ============== statsToday 测试 ==============

    @Test
    @DisplayName("statsToday: 带商家ID过滤")
    void statsToday_withMerchantId() {
        Order paidOrder = new Order();
        paidOrder.setOrderNo("OD001");
        paidOrder.setStatus(OrderStatusEnum.PAID.getCode());
        paidOrder.setTotalPrice(new BigDecimal("100.00"));
        paidOrder.setCreatedAt(LocalDateTime.now());
        paidOrder.setMerchantId(2001L);

        Order completedOrder = new Order();
        completedOrder.setOrderNo("OD002");
        completedOrder.setStatus(OrderStatusEnum.COMPLETED.getCode());
        completedOrder.setTotalPrice(new BigDecimal("200.00"));
        completedOrder.setCreatedAt(LocalDateTime.now());
        completedOrder.setMerchantId(2001L);

        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(java.util.Arrays.asList(paidOrder, completedOrder));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> result = orderService.statsToday(2001L);
        assertNotNull(result);
        assertEquals(2L, result.get("todayOrderCount"));
        assertEquals(0, new BigDecimal("300.00").compareTo((BigDecimal) result.get("todayRevenue")));
        assertEquals(0L, result.get("pendingShipCount"));
        assertEquals(0L, result.get("pendingRefundCount"));
    }

    @Test
    @DisplayName("statsToday: 无商家ID过滤")
    void statsToday_withoutMerchantId() {
        Order order = new Order();
        order.setOrderNo("OD001");
        order.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        order.setTotalPrice(new BigDecimal("50.00"));
        order.setCreatedAt(LocalDateTime.now());

        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(order));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> result = orderService.statsToday(null);
        assertNotNull(result);
        assertEquals(1L, result.get("todayOrderCount"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) result.get("todayRevenue")),
                "待支付订单不计入营收");
    }

    @Test
    @DisplayName("statsToday: 营收计算排除非已支付/已完成状态")
    void statsToday_revenueCalculation() {
        Order pendingPay = new Order();
        pendingPay.setOrderNo("OD001");
        pendingPay.setStatus(OrderStatusEnum.PENDING_PAY.getCode());
        pendingPay.setTotalPrice(new BigDecimal("100.00"));
        pendingPay.setCreatedAt(LocalDateTime.now());

        Order cancelled = new Order();
        cancelled.setOrderNo("OD002");
        cancelled.setStatus(OrderStatusEnum.CANCELLED.getCode());
        cancelled.setTotalPrice(new BigDecimal("200.00"));
        cancelled.setCreatedAt(LocalDateTime.now());

        Order refunded = new Order();
        refunded.setOrderNo("OD003");
        refunded.setStatus(OrderStatusEnum.REFUNDED.getCode());
        refunded.setTotalPrice(new BigDecimal("300.00"));
        refunded.setCreatedAt(LocalDateTime.now());

        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(java.util.Arrays.asList(pendingPay, cancelled, refunded));
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        Map<String, Object> result = orderService.statsToday(null);
        assertNotNull(result);
        assertEquals(3L, result.get("todayOrderCount"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) result.get("todayRevenue")),
                "非已支付/已完成状态不计入营收");
    }

    @Test
    @DisplayName("statsToday: 待发货和待退款统计")
    void statsToday_pendingCounts() {
        when(orderMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        when(orderMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(5L, 2L);

        Map<String, Object> result = orderService.statsToday(2001L);
        assertNotNull(result);
        assertEquals(5L, result.get("pendingShipCount"));
        assertEquals(2L, result.get("pendingRefundCount"));
    }

    // ============== rejectRefund 测试 ==============

    @Test
    @DisplayName("rejectRefund: 正常拒绝退款恢复为已完成")
    void rejectRefund_normalRejection() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.REFUNDED.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(orderMapper.updateById(any(Order.class))).thenReturn(1);

        orderService.rejectRefund(ORDER_NO, 2001L, "商品无问题");

        assertEquals(OrderStatusEnum.COMPLETED.getCode(), order.getStatus());
        verify(orderMapper).updateById(any(Order.class));
    }

    @Test
    @DisplayName("rejectRefund: 非退款状态不做任何操作")
    void rejectRefund_nonRefundStatus() {
        Order order = buildPaidOrder();
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        orderService.rejectRefund(ORDER_NO, 2001L, "不应触发");

        assertEquals(OrderStatusEnum.PAID.getCode(), order.getStatus());
        verify(orderMapper, never()).updateById(any(Order.class));
    }

    @Test
    @DisplayName("rejectRefund: 订单状态为 null 安全跳过")
    void rejectRefund_nullStatus() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(null);
        when(orderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        orderService.rejectRefund(ORDER_NO, 2001L, "测试null状态");

        verify(orderMapper, never()).updateById(any(Order.class));
    }

    // ============== createOrder 新增测试 ==============

    @Test
    @DisplayName("createOrder: 带商品ID创建")
    void createOrder_withProductId() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setProductId(88L);
        dto.setProductName("测试商品");
        dto.setQuantity(2);
        dto.setTotalPrice(new BigDecimal("200.00"));
        dto.setLscAmount(50L);

        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order order = orderService.createOrder(dto);
        assertNotNull(order);
        assertEquals(88L, order.getProductId());
        assertEquals("测试商品", order.getProductName());
        assertEquals(2, order.getQuantity());
    }

    @Test
    @DisplayName("createOrder: 数量为 null 时默认设为 1")
    void createOrder_withQuantityDefault() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(null);
        dto.setQuantity(null);

        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order order = orderService.createOrder(dto);
        assertEquals(1, order.getQuantity());
    }

    @Test
    @DisplayName("createOrder: 商品ID为 null 时默认设为 0")
    void createOrder_withProductIdDefault() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setProductId(null);

        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order order = orderService.createOrder(dto);
        assertEquals(0L, order.getProductId());
    }

    @Test
    @DisplayName("createOrder: 纯人民币支付（无LSC）")
    void createOrder_rmbOnly() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(1);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setTotalPrice(new BigDecimal("88.00"));
        dto.setLscAmount(0L);

        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order order = orderService.createOrder(dto);
        assertEquals(0L, order.getLscAmount());
        assertEquals(0, new BigDecimal("88.00").compareTo(order.getRmbAmount()));
    }

    // ============== generateOrderNo 验证 ==============

    @Test
    @DisplayName("createOrder: 验证订单号格式")
    void generateOrderNo_verification() {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setOrderType(0);
        dto.setConsumerId(1001L);
        dto.setMerchantId(2001L);
        dto.setTotalPrice(new BigDecimal("100.00"));

        when(orderMapper.insert(any(Order.class))).thenReturn(1);

        Order order = orderService.createOrder(dto);
        assertNotNull(order.getOrderNo());
        assertTrue(order.getOrderNo().startsWith("ORD"),
                "订单号应以 ORD 开头");
        assertTrue(order.getOrderNo().length() > 14,
                "订单号应包含时间戳+序号");
    }

    // ============== 辅助方法 ==============

    private void setLockFields() {
        ReflectionTestUtils.setField(orderService, "lockWaitMs", 3000L);
        ReflectionTestUtils.setField(orderService, "lockLeaseMs", 10000L);
    }

    private Order buildCompletedOrder() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.COMPLETED.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        order.setLscAmount(100L);
        order.setRmbAmount(new BigDecimal("50.00"));
        order.setRefundLscAmount(0L);
        order.setRefundRmbAmount(BigDecimal.ZERO);
        return order;
    }

    private Order buildPaidOrder() {
        Order order = new Order();
        order.setOrderNo(ORDER_NO);
        order.setStatus(OrderStatusEnum.PAID.getCode());
        order.setConsumerId(1001L);
        order.setMerchantId(2001L);
        order.setLscAmount(100L);
        order.setRmbAmount(new BigDecimal("50.00"));
        order.setRefundLscAmount(0L);
        order.setRefundRmbAmount(BigDecimal.ZERO);
        return order;
    }
}
