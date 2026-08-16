package com.lianshengtong.b2b.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.b2b.dto.*;
import com.lianshengtong.b2b.entity.B2bOrder;
import com.lianshengtong.b2b.feign.AiGatewayFeignClient;
import com.lianshengtong.b2b.feign.LscLedgerFeignClient;
import com.lianshengtong.b2b.mapper.B2bOrderMapper;
import com.lianshengtong.common.enums.B2BOrderStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.utils.ShardedLockUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("B2B订单服务单元测试")
class B2bOrderServiceImplTest {

    @Mock
    private B2bOrderMapper b2bOrderMapper;
    @Mock
    private LscLedgerFeignClient lscLedgerFeignClient;
    @Mock
    private AiGatewayFeignClient aiGatewayFeignClient;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;
    @Mock
    private ShardedLockUtil shardedLockUtil;

    @InjectMocks
    private B2bOrderServiceImpl b2bOrderService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(b2bOrderService, "orderValidityDays", 7);
        ReflectionTestUtils.setField(b2bOrderService, "lockWaitMs", 3000L);
        ReflectionTestUtils.setField(b2bOrderService, "lockLeaseMs", 10000L);
    }

    private B2bOrder createMockOrder() {
        B2bOrder order = new B2bOrder();
        order.setId(1L);
        order.setOrderNo("B2B20260806000001");
        order.setInitiatorId(100L);
        order.setCounterpartyId(200L);
        order.setTradeDescription("B2B贸易订单");
        order.setTotalAmountRmb(new BigDecimal("1000.00"));
        order.setLscAmount(1000L);
        order.setStatus(B2BOrderStatusEnum.PENDING_CONFIRM.getCode());
        order.setAiVerificationResult(0);
        order.setCounterpartyConfirmed(0);
        order.setLscTransferred(0);
        order.setVersion(1);
        order.setExpireAt(LocalDateTime.now().plusDays(7));
        return order;
    }

    // ============== createOrder 测试 ==============

    @Test
    @DisplayName("createOrder: 成功创建B2B订单")
    void createOrder_success() {
        B2bOrderCreateDTO dto = new B2bOrderCreateDTO();
        dto.setInitiatorId(100L);
        dto.setCounterpartyId(200L);
        dto.setTotalAmountRmb(new BigDecimal("1000.00"));
        dto.setLscAmount(1000L);
        dto.setTradeDescription("测试B2B订单");

        when(b2bOrderMapper.insert(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.createOrder(dto);

        assertNotNull(result);
        assertEquals(B2BOrderStatusEnum.PENDING_CONFIRM.getCode(), result.getStatus());
        assertEquals(Integer.valueOf(0), result.getAiVerificationResult());
        assertEquals(Integer.valueOf(0), result.getCounterpartyConfirmed());
        assertEquals(Integer.valueOf(0), result.getLscTransferred());
        assertEquals(Integer.valueOf(1), result.getVersion());
    }

    @Test
    @DisplayName("createOrder: LSC金额与人民币金额不匹配抛异常")
    void createOrder_amountMismatch_throws() {
        B2bOrderCreateDTO dto = new B2bOrderCreateDTO();
        dto.setInitiatorId(100L);
        dto.setCounterpartyId(200L);
        dto.setTotalAmountRmb(new BigDecimal("1000.00"));
        dto.setLscAmount(500L);

        assertThrows(BizException.class, () -> b2bOrderService.createOrder(dto));
    }

    @Test
    @DisplayName("createOrder: 发起方与接收方相同抛异常")
    void createOrder_sameParty_throws() {
        B2bOrderCreateDTO dto = new B2bOrderCreateDTO();
        dto.setInitiatorId(100L);
        dto.setCounterpartyId(100L);
        dto.setTotalAmountRmb(new BigDecimal("1000.00"));
        dto.setLscAmount(1000L);

        assertThrows(BizException.class, () -> b2bOrderService.createOrder(dto));
    }

    // ============== confirmOrder 测试 ==============

    @Test
    @DisplayName("confirmOrder: 成功确认订单")
    void confirmOrder_success() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong())).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.confirmOrder(dto);

        assertEquals(B2BOrderStatusEnum.CONFIRMED.getCode(), result.getStatus());
        assertEquals(Integer.valueOf(1), result.getCounterpartyConfirmed());
    }

    @Test
    @DisplayName("confirmOrder: 非接收方确认抛异常")
    void confirmOrder_notCounterparty_throws() {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(999L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(BizException.class, () -> b2bOrderService.confirmOrder(dto));
    }

    @Test
    @DisplayName("confirmOrder: 锁获取失败抛异常")
    void confirmOrder_lockFail_throws() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong())).thenReturn(null);

        assertThrows(BizException.class, () -> b2bOrderService.confirmOrder(dto));
    }

    // ============== executeTransfer 测试 ==============

    @Test
    @DisplayName("executeTransfer: 成功流转")
    void executeTransfer_success() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong())).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.ok());
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.executeTransfer(dto);

        assertEquals(B2BOrderStatusEnum.TRANSFERRED.getCode(), result.getStatus());
        assertEquals(Integer.valueOf(1), result.getLscTransferred());
    }

    @Test
    @DisplayName("executeTransfer: 非发起方操作抛异常")
    void executeTransfer_notInitiator_throws() {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(999L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));
    }

    // ============== cancelOrder 测试 ==============

    @Test
    @DisplayName("cancelOrder: 发起方成功取消订单")
    void cancelOrder_success_byInitiator() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderCancelDTO dto = new B2bOrderCancelDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.cancelOrder(dto);

        assertEquals(B2BOrderStatusEnum.CANCELLED.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("cancelOrder: 非参与方无权取消")
    void cancelOrder_notParty_throws() {
        B2bOrder order = createMockOrder();
        B2bOrderCancelDTO dto = new B2bOrderCancelDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(999L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(BizException.class, () -> b2bOrderService.cancelOrder(dto));
    }

    // ============== voidOrder 测试 ==============

    @Test
    @DisplayName("voidOrder: 成功作废订单")
    void voidOrder_success() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderVoidDTO dto = new B2bOrderVoidDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);
        dto.setReason("风控检测异常");

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.voidOrder(dto);

        assertEquals(B2BOrderStatusEnum.VOIDED.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("voidOrder: 已流转订单不可作废")
    void voidOrder_alreadyTransferred_throws() {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.TRANSFERRED.getCode());

        B2bOrderVoidDTO dto = new B2bOrderVoidDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(BizException.class, () -> b2bOrderService.voidOrder(dto));
    }

    // ============== getByOrderNo 测试 ==============

    @Test
    @DisplayName("getByOrderNo: 订单不存在抛异常")
    void getByOrderNo_notFound_throws() {
        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(BizException.class, () -> b2bOrderService.getByOrderNo("NOT_EXIST"));
    }

    // ============== manualVerifyConfirm 测试 ==============

    @Test
    @DisplayName("manualVerifyConfirm: 人工判定虚假作废订单")
    void manualVerifyConfirm_fake_voided() {
        B2bOrder order = createMockOrder();
        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.manualVerifyConfirm("B2B20260806000001", false, "涉嫌欺诈");

        assertEquals(B2BOrderStatusEnum.VOIDED.getCode(), result.getStatus());
        assertEquals(Integer.valueOf(4), result.getAiVerificationResult());
    }

    // ============== createOrder 补充测试 ==============

    @Test
    @DisplayName("createOrder: 带合同编号和贸易凭证成功创建")
    void createOrder_success_withContractAndEvidence() {
        B2bOrderCreateDTO dto = new B2bOrderCreateDTO();
        dto.setInitiatorId(100L);
        dto.setCounterpartyId(200L);
        dto.setTotalAmountRmb(new BigDecimal("5000.00"));
        dto.setLscAmount(5000L);
        dto.setTradeDescription("大宗商品贸易");
        dto.setContractNo("HT20260807001");
        dto.setTradeEvidenceUrls("[\"https://example.com/evidence1.jpg\"]");

        when(b2bOrderMapper.insert(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.createOrder(dto);

        assertNotNull(result);
        assertEquals("HT20260807001", result.getContractNo());
        assertEquals("[\"https://example.com/evidence1.jpg\"]", result.getTradeEvidenceUrls());
        assertEquals(B2BOrderStatusEnum.PENDING_CONFIRM.getCode(), result.getStatus());
        assertNotNull(result.getIdempotentKey());
        assertNotNull(result.getExpireAt());
    }

    // ============== executeTransfer 补充测试 ==============

    @Test
    @DisplayName("executeTransfer: 非已确认状态抛异常")
    void executeTransfer_nonConfirmedStatus_throws() {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.PENDING_CONFIRM.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));
    }

    @Test
    @DisplayName("executeTransfer: 已过期订单抛异常")
    void executeTransfer_expiredOrder_throws() {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());
        order.setExpireAt(LocalDateTime.now().minusDays(1));

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));
    }

    @Test
    @DisplayName("executeTransfer: LSC与RMB金额不匹配抛异常")
    void executeTransfer_amountMismatch_throws() {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());
        order.setLscAmount(500L);
        order.setTotalAmountRmb(new BigDecimal("1000.00"));

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));
    }

    @Test
    @DisplayName("executeTransfer: 获取分布式锁失败抛异常")
    void executeTransfer_lockFail_throws() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong())).thenReturn(null);

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));
    }

    @Test
    @DisplayName("executeTransfer: 获取锁被中断抛异常")
    void executeTransfer_lockInterrupted_throws() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new InterruptedException());

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));
    }

    @Test
    @DisplayName("executeTransfer: 账本流转返回null抛异常")
    void executeTransfer_ledgerReturnNull_throws() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong())).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(null);

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));
    }

    @Test
    @DisplayName("executeTransfer: 账本流转失败抛异常")
    void executeTransfer_ledgerFailure_throws() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong())).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.fail("余额不足"));

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));
    }

    @Test
    @DisplayName("executeTransfer: 乐观锁冲突抛异常")
    void executeTransfer_optimisticLockConflict_throws() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong())).thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.ok());
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(0);

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));
    }

    // ============== cancelOrder 补充测试 ==============

    @Test
    @DisplayName("cancelOrder: 接收方成功取消订单")
    void cancelOrder_success_byCounterparty() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderCancelDTO dto = new B2bOrderCancelDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.cancelOrder(dto);

        assertEquals(B2BOrderStatusEnum.CANCELLED.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("cancelOrder: 非可取消状态抛异常")
    void cancelOrder_nonCancellableStatus_throws() {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.TRANSFERRED.getCode());

        B2bOrderCancelDTO dto = new B2bOrderCancelDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(BizException.class, () -> b2bOrderService.cancelOrder(dto));
    }

    @Test
    @DisplayName("cancelOrder: 获取锁失败抛异常")
    void cancelOrder_lockFail_throws() throws Exception {
        B2bOrder order = createMockOrder();

        B2bOrderCancelDTO dto = new B2bOrderCancelDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        assertThrows(BizException.class, () -> b2bOrderService.cancelOrder(dto));
    }

    @Test
    @DisplayName("cancelOrder: 获取锁被中断抛异常")
    void cancelOrder_interrupted_throws() throws Exception {
        B2bOrder order = createMockOrder();

        B2bOrderCancelDTO dto = new B2bOrderCancelDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException());

        assertThrows(BizException.class, () -> b2bOrderService.cancelOrder(dto));
    }

    // ============== voidOrder 补充测试 ==============

    @Test
    @DisplayName("voidOrder: 已完成订单不可作废")
    void voidOrder_alreadyCompleted_throws() {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.COMPLETED.getCode());

        B2bOrderVoidDTO dto = new B2bOrderVoidDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        assertThrows(BizException.class, () -> b2bOrderService.voidOrder(dto));
    }

    @Test
    @DisplayName("voidOrder: 获取锁失败抛异常")
    void voidOrder_lockFail_throws() throws Exception {
        B2bOrder order = createMockOrder();

        B2bOrderVoidDTO dto = new B2bOrderVoidDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS))).thenReturn(false);

        assertThrows(BizException.class, () -> b2bOrderService.voidOrder(dto));
    }

    @Test
    @DisplayName("voidOrder: 获取锁被中断抛异常")
    void voidOrder_interrupted_throws() throws Exception {
        B2bOrder order = createMockOrder();

        B2bOrderVoidDTO dto = new B2bOrderVoidDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(redissonClient.getLock(anyString())).thenReturn(rLock);
        when(rLock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenThrow(new InterruptedException());

        assertThrows(BizException.class, () -> b2bOrderService.voidOrder(dto));
    }

    // ============== getAiVerification 测试 ==============

    @Test
    @DisplayName("getAiVerification: AI核验成功返回数据")
    void getAiVerification_success() {
        B2bOrder order = createMockOrder();
        Map<String, Object> aiData = new HashMap<>();
        aiData.put("result", 1);
        aiData.put("score", new BigDecimal("85.5"));
        aiData.put("riskTags", "低风险");

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(aiGatewayFeignClient.b2bVerify(anyString(), anyString(), any())).thenReturn(R.ok(aiData));
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        Map<String, Object> result = b2bOrderService.getAiVerification("B2B20260806000001");

        assertNotNull(result);
        assertEquals(1, result.get("result"));
        assertEquals(new BigDecimal("85.5"), result.get("score"));
        assertEquals("低风险", result.get("riskTags"));
    }

    @Test
    @DisplayName("getAiVerification: AI服务返回null抛异常")
    void getAiVerification_nullResult_throws() {
        B2bOrder order = createMockOrder();

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(aiGatewayFeignClient.b2bVerify(anyString(), anyString(), any())).thenReturn(null);

        assertThrows(BizException.class, () -> b2bOrderService.getAiVerification("B2B20260806000001"));
    }

    @Test
    @DisplayName("getAiVerification: AI服务返回失败抛异常")
    void getAiVerification_notSuccess_throws() {
        B2bOrder order = createMockOrder();

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(aiGatewayFeignClient.b2bVerify(anyString(), anyString(), any()))
                .thenReturn(R.fail("AI网关异常"));

        assertThrows(BizException.class, () -> b2bOrderService.getAiVerification("B2B20260806000001"));
    }

    @Test
    @DisplayName("getAiVerification: AI返回数据为null抛异常")
    void getAiVerification_dataNull_throws() {
        B2bOrder order = createMockOrder();

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(aiGatewayFeignClient.b2bVerify(anyString(), anyString(), any())).thenReturn(R.ok());

        assertThrows(BizException.class, () -> b2bOrderService.getAiVerification("B2B20260806000001"));
    }

    // ============== manualVerifyConfirm 补充测试 ==============

    @Test
    @DisplayName("manualVerifyConfirm: pass=true 状态置为人工真实")
    void manualVerifyConfirm_passTrue_status3() {
        B2bOrder order = createMockOrder();
        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.manualVerifyConfirm("B2B20260806000001", true, "核验通过");

        assertEquals(Integer.valueOf(3), result.getAiVerificationResult());
        assertEquals(B2BOrderStatusEnum.PENDING_CONFIRM.getCode(), result.getStatus());
    }

    @Test
    @DisplayName("manualVerifyConfirm: pass=true 带备注写入风险标签")
    void manualVerifyConfirm_passTrue_withRemark() {
        B2bOrder order = createMockOrder();
        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.manualVerifyConfirm("B2B20260806000001", true, "核验资料齐全");

        assertEquals(Integer.valueOf(3), result.getAiVerificationResult());
        assertEquals("人工:核验资料齐全", result.getAiRiskTags());
    }

    @Test
    @DisplayName("manualVerifyConfirm: pass=false 不带备注风险标签不变")
    void manualVerifyConfirm_withoutRemark() {
        B2bOrder order = createMockOrder();
        order.setAiRiskTags(null);
        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.manualVerifyConfirm("B2B20260806000001", false, null);

        assertEquals(Integer.valueOf(4), result.getAiVerificationResult());
        assertEquals(B2BOrderStatusEnum.VOIDED.getCode(), result.getStatus());
        assertNull(result.getAiRiskTags());
    }

    @Test
    @DisplayName("manualVerifyConfirm: 已有标签追加人工备注")
    void manualVerifyConfirm_withExistingTags() {
        B2bOrder order = createMockOrder();
        order.setAiRiskTags("AI可疑;高风险");
        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.manualVerifyConfirm("B2B20260806000001", true, "人工复核真实");

        assertEquals(Integer.valueOf(3), result.getAiVerificationResult());
        assertEquals("AI可疑;高风险;人工:人工复核真实", result.getAiRiskTags());
    }

    // ============== listOrders 测试 ==============

    @Test
    @DisplayName("listOrders: 全参数过滤查询成功")
    void listOrders_withAllFilters() {
        Page<B2bOrder> mockPage = new Page<>(1, 20);
        when(b2bOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        IPage<B2bOrder> result = b2bOrderService.listOrders(1, 20, 100L,
                B2BOrderStatusEnum.PENDING_CONFIRM.getCode(), "B2B", "2026-08-01", "2026-08-07");

        assertNotNull(result);
        verify(b2bOrderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listOrders: 仅订单号和日期过滤")
    void listOrders_withOrderNoDateFilters() {
        Page<B2bOrder> mockPage = new Page<>(1, 20);
        when(b2bOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        IPage<B2bOrder> result = b2bOrderService.listOrders(1, 20, null, null,
                "B2B2026", "2026-08-01", "2026-08-31");

        assertNotNull(result);
        verify(b2bOrderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listOrders: 默认分页参数")
    void listOrders_defaultPagination() {
        Page<B2bOrder> mockPage = new Page<>(1, 20);
        when(b2bOrderMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mockPage);

        IPage<B2bOrder> result = b2bOrderService.listOrders(null, null, 100L, null,
                null, null, null);

        assertNotNull(result);
        verify(b2bOrderMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    // ============== getByOrderNo 补充测试 ==============

    @Test
    @DisplayName("getByOrderNo: 成功获取订单")
    void getByOrderNo_success() {
        B2bOrder order = createMockOrder();
        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);

        B2bOrder result = b2bOrderService.getByOrderNo("B2B20260806000001");

        assertNotNull(result);
        assertEquals("B2B20260806000001", result.getOrderNo());
        assertEquals(100L, result.getInitiatorId());
        assertEquals(200L, result.getCounterpartyId());
    }
}
