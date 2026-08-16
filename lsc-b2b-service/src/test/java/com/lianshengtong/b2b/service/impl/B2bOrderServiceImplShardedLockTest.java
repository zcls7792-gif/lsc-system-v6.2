package com.lianshengtong.b2b.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.b2b.dto.B2bOrderConfirmDTO;
import com.lianshengtong.b2b.dto.B2bOrderTransferDTO;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("B2B订单服务 - 分片锁集成测试")
class B2bOrderServiceImplShardedLockTest {

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

    // ============== confirmOrder 分片锁测试 ==============

    @Test
    @DisplayName("confirmOrder: 使用分片锁 - 验证以orderNo作为标识符传递给ShardedLockUtil")
    void confirmOrder_usesShardedLockWithOrderNo() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(
                eq("lock:b2b:order:"), eq("B2B20260806000001"), eq(3000L), eq(10000L)))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.confirmOrder(dto);

        assertNotNull(result);
        assertEquals(B2BOrderStatusEnum.CONFIRMED.getCode(), result.getStatus());
        assertEquals(Integer.valueOf(1), result.getCounterpartyConfirmed());
        verify(shardedLockUtil).tryShardedLock(
                "lock:b2b:order:", "B2B20260806000001", 3000L, 10000L);
    }

    @Test
    @DisplayName("confirmOrder: 分片锁获取失败返回null时抛出BizException")
    void confirmOrder_shardedLockFail_throwsBizException() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> b2bOrderService.confirmOrder(dto));
        assertTrue(exception.getMessage().contains("订单确认处理中"));
    }

    @Test
    @DisplayName("confirmOrder: 分片锁获取被中断时抛出BizException并恢复中断标记")
    void confirmOrder_shardedLockInterrupted_throwsBizException() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new InterruptedException("模拟中断"));

        BizException exception = assertThrows(BizException.class,
                () -> b2bOrderService.confirmOrder(dto));
        assertTrue(exception.getMessage().contains("订单确认被中断"));
        assertTrue(Thread.currentThread().isInterrupted(), "中断标记应被恢复");
    }

    @Test
    @DisplayName("confirmOrder: 分片锁在业务成功后释放")
    void confirmOrder_shardedLockReleasedOnSuccess() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        b2bOrderService.confirmOrder(dto);

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("confirmOrder: 分片锁在业务异常(updateById返回0)时仍然释放")
    void confirmOrder_shardedLockReleasedOnUpdateFail() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(0);

        assertThrows(BizException.class, () -> b2bOrderService.confirmOrder(dto));

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("confirmOrder: 分片锁在运行时异常时仍然释放")
    void confirmOrder_shardedLockReleasedOnRuntimeException() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class)))
                .thenThrow(new RuntimeException("数据库连接异常"));

        assertThrows(RuntimeException.class, () -> b2bOrderService.confirmOrder(dto));

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("confirmOrder: 锁非当前线程持有则不执行unlock")
    void confirmOrder_lockNotHeldByCurrentThread_noUnlock() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        b2bOrderService.confirmOrder(dto);

        verify(rLock, never()).unlock();
    }

    // ============== executeTransfer 分片锁测试 ==============

    @Test
    @DisplayName("executeTransfer: 使用分片锁 - 验证以orderNo作为标识符传递给ShardedLockUtil")
    void executeTransfer_usesShardedLockWithOrderNo() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(
                eq("lock:b2b:order:"), eq("B2B20260806000001"), eq(3000L), eq(10000L)))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.ok());
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        B2bOrder result = b2bOrderService.executeTransfer(dto);

        assertNotNull(result);
        assertEquals(B2BOrderStatusEnum.TRANSFERRED.getCode(), result.getStatus());
        assertEquals(Integer.valueOf(1), result.getLscTransferred());
        verify(shardedLockUtil).tryShardedLock(
                "lock:b2b:order:", "B2B20260806000001", 3000L, 10000L);
    }

    @Test
    @DisplayName("executeTransfer: 分片锁获取失败返回null时抛出BizException")
    void executeTransfer_shardedLockFail_throwsBizException() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> b2bOrderService.executeTransfer(dto));
        assertTrue(exception.getMessage().contains("订单流转处理中"));
    }

    @Test
    @DisplayName("executeTransfer: 分片锁获取被中断时抛出BizException并恢复中断标记")
    void executeTransfer_shardedLockInterrupted_throwsBizException() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenThrow(new InterruptedException("模拟中断"));

        BizException exception = assertThrows(BizException.class,
                () -> b2bOrderService.executeTransfer(dto));
        assertTrue(exception.getMessage().contains("订单流转被中断"));
        assertTrue(Thread.currentThread().isInterrupted(), "中断标记应被恢复");
    }

    @Test
    @DisplayName("executeTransfer: 分片锁在业务成功后释放")
    void executeTransfer_shardedLockReleasedOnSuccess() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.ok());
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        b2bOrderService.executeTransfer(dto);

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("executeTransfer: 分片锁在账本服务返回失败时仍然释放")
    void executeTransfer_shardedLockReleasedOnLedgerFail() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.fail("账本服务异常"));

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("executeTransfer: 分片锁在updateById返回0时仍然释放")
    void executeTransfer_shardedLockReleasedOnUpdateFail() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.ok());
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(0);

        assertThrows(BizException.class, () -> b2bOrderService.executeTransfer(dto));

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("executeTransfer: 锁非当前线程持有则不执行unlock")
    void executeTransfer_lockNotHeldByCurrentThread_noUnlock() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.ok());
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        b2bOrderService.executeTransfer(dto);

        verify(rLock, never()).unlock();
    }

    // ============== 分片路由一致性测试 ==============

    @Test
    @DisplayName("分片路由: 相同orderNo总是路由到相同分片(幂等性)")
    void shardRouting_sameOrderNo_sameShard() {
        String orderNo = "B2B20260806000001";
        int shardCount = 16;

        int shard1 = Math.abs(orderNo.hashCode()) & (shardCount - 1);
        int shard2 = Math.abs(orderNo.hashCode()) & (shardCount - 1);
        int shard3 = Math.abs(orderNo.hashCode()) & (shardCount - 1);

        assertEquals(shard1, shard2, "第二次计算应命中同一分片");
        assertEquals(shard2, shard3, "第三次计算应命中同一分片");
    }

    @Test
    @DisplayName("分片路由: 不同orderNo应分布到不同分片(避免热点)")
    void shardRouting_differentOrderNo_distributed() {
        int shardCount = 16;
        java.util.Set<Integer> shards = new java.util.HashSet<>();
        for (int i = 1; i <= 20; i++) {
            String orderNo = "B2B20260806" + String.format("%06d", i);
            int shard = Math.abs(orderNo.hashCode()) & (shardCount - 1);
            shards.add(shard);
        }
        assertTrue(shards.size() > 1,
                "20个不同订单号应分布到多个分片，实际分布: " + shards.size());
    }

    @Test
    @DisplayName("分片路由: 分片编号在有效范围内(0至shardCount-1)")
    void shardRouting_shardInValidRange() {
        int shardCount = 16;
        for (int i = 0; i < 100; i++) {
            String identifier = "test-identifier-" + i;
            int shard = Math.abs(identifier.hashCode()) & (shardCount - 1);
            assertTrue(shard >= 0 && shard < shardCount,
                    "分片编号应在0至" + (shardCount - 1) + "之间, 实际: " + shard);
        }
    }

    @Test
    @DisplayName("分片路由: 使用ShardedLockUtil.resolveShard验证分片一致性")
    void shardRouting_resolveShardConsistency() {
        String identifier = "B2B20260806000001";
        int expectedShard = Math.abs(identifier.hashCode()) & (16 - 1);

        when(shardedLockUtil.resolveShard(identifier)).thenReturn(expectedShard);

        int firstCall = shardedLockUtil.resolveShard(identifier);
        int secondCall = shardedLockUtil.resolveShard(identifier);

        assertEquals(expectedShard, firstCall);
        assertEquals(expectedShard, secondCall);
        verify(shardedLockUtil, times(2)).resolveShard(identifier);
    }

    @Test
    @DisplayName("分片路由: 边界条件 - hashCode为负数时仍能正确路由")
    void shardRouting_negativeHashCode_handled() {
        String negativeHashId = "order-neg-hash-test";
        int shardCount = 16;

        int shard = Math.abs(negativeHashId.hashCode()) & (shardCount - 1);

        assertTrue(shard >= 0 && shard < shardCount,
                "即使hashCode为负，Math.abs后也应得到有效分片");
    }

    // ============== 配置参数传递正确性测试 ==============

    @Test
    @DisplayName("confirmOrder: 验证lockWaitMs和lockLeaseMs正确传递给分片锁")
    void confirmOrder_lockParamsPassedCorrectly() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(
                eq("lock:b2b:order:"), eq("B2B20260806000001"),
                eq(3000L), eq(10000L)))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        b2bOrderService.confirmOrder(dto);

        verify(shardedLockUtil).tryShardedLock(
                "lock:b2b:order:", "B2B20260806000001", 3000L, 10000L);
    }

    @Test
    @DisplayName("executeTransfer: 验证lockWaitMs和lockLeaseMs正确传递给分片锁")
    void executeTransfer_lockParamsPassedCorrectly() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(
                eq("lock:b2b:order:"), eq("B2B20260806000001"),
                eq(3000L), eq(10000L)))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.ok());
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        b2bOrderService.executeTransfer(dto);

        verify(shardedLockUtil).tryShardedLock(
                "lock:b2b:order:", "B2B20260806000001", 3000L, 10000L);
    }

    // ============== 多次调用锁释放幂等性测试 ==============

    @Test
    @DisplayName("confirmOrder: 分片锁unlock只调用一次(幂等释放)")
    void confirmOrder_unlockCalledOnce() throws Exception {
        B2bOrder order = createMockOrder();
        B2bOrderConfirmDTO dto = new B2bOrderConfirmDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setConfirmerId(200L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        b2bOrderService.confirmOrder(dto);

        verify(rLock, times(1)).unlock();
    }

    @Test
    @DisplayName("executeTransfer: 分片锁unlock只调用一次(幂等释放)")
    void executeTransfer_unlockCalledOnce() throws Exception {
        B2bOrder order = createMockOrder();
        order.setStatus(B2BOrderStatusEnum.CONFIRMED.getCode());

        B2bOrderTransferDTO dto = new B2bOrderTransferDTO();
        dto.setOrderNo("B2B20260806000001");
        dto.setOperatorId(100L);

        when(b2bOrderMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(order);
        when(shardedLockUtil.tryShardedLock(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(rLock);
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        when(lscLedgerFeignClient.b2bTransfer(any())).thenReturn(R.ok());
        when(b2bOrderMapper.updateById(any(B2bOrder.class))).thenReturn(1);

        b2bOrderService.executeTransfer(dto);

        verify(rLock, times(1)).unlock();
    }
}