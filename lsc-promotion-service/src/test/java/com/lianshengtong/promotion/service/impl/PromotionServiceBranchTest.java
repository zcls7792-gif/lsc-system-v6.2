package com.lianshengtong.promotion.service.impl;

import com.lianshengtong.common.enums.OrderStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.promotion.dto.FirstOrderCheckDTO;
import com.lianshengtong.promotion.dto.RewardResultDTO;
import com.lianshengtong.promotion.dto.RollbackRewardDTO;
import com.lianshengtong.promotion.entity.PromotionPending;
import com.lianshengtong.promotion.feign.LedgerFeignClient;
import com.lianshengtong.promotion.feign.UserFeignClient;
import com.lianshengtong.promotion.mapper.PromotionPendingMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("推广服务分支覆盖率测试")
class PromotionServiceBranchTest {

    @Mock
    private PromotionPendingMapper promotionPendingMapper;

    @Mock
    private LedgerFeignClient ledgerFeignClient;

    @Mock
    private UserFeignClient userFeignClient;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock rLock;

    @InjectMocks
    private PromotionServiceImpl promotionService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(promotionService, "rewardRate", new BigDecimal("0.10"));
        ReflectionTestUtils.setField(promotionService, "firstOrderMinAmount", new BigDecimal("1"));
        ReflectionTestUtils.setField(promotionService, "maxLevel", 1);
        ReflectionTestUtils.setField(promotionService, "pendingBatchSize", 500);

        lenient().when(redissonClient.getLock(anyString())).thenReturn(rLock);
        try {
            lenient().when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        } catch (InterruptedException e) {
            // ignore
        }
        lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
    }

    private FirstOrderCheckDTO buildValidDto() {
        FirstOrderCheckDTO dto = new FirstOrderCheckDTO();
        dto.setUserId(1L);
        dto.setOrderNo("ORD001");
        dto.setOrderAmount(new BigDecimal("100"));
        dto.setOrderStatus(OrderStatusEnum.COMPLETED.getCode());
        dto.setRefundAmount(BigDecimal.ZERO);
        dto.setReferrerId(100L);
        return dto;
    }

    private PromotionPending buildPending(Long userId, String orderNo, Integer status,
                                           BigDecimal rewardAmount, Long referrerId) {
        PromotionPending p = new PromotionPending();
        p.setId(999L);
        p.setUserId(userId);
        p.setOrderNo(orderNo);
        p.setStatus(status);
        p.setRewardAmount(rewardAmount);
        p.setReferrerId(referrerId);
        p.setFirstOrderAmount(new BigDecimal("100"));
        p.setRetryCount(0);
        return p;
    }

    // ============== notifyFirstOrder 测试 (全新覆盖) ==============

    @Test
    @DisplayName("首单通知：参数缺失(consumerId为null)直接返回")
    void testNotifyFirstOrder_nullConsumerId() {
        promotionService.notifyFirstOrder(null, "ORD001", new BigDecimal("100"),
                OrderStatusEnum.COMPLETED.getCode(), BigDecimal.ZERO);
        verify(userFeignClient, never()).getUserInfo(anyLong());
    }

    @Test
    @DisplayName("首单通知：参数缺失(orderNo为null)直接返回")
    void testNotifyFirstOrder_nullOrderNo() {
        promotionService.notifyFirstOrder(1L, null, new BigDecimal("100"),
                OrderStatusEnum.COMPLETED.getCode(), BigDecimal.ZERO);
        verify(userFeignClient, never()).getUserInfo(anyLong());
    }

    @Test
    @DisplayName("首单通知：userFeignClient返回null，referrerId保持null")
    void testNotifyFirstOrder_userFeignReturnsNull() {
        when(userFeignClient.getUserInfo(1L)).thenReturn(null);
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);

        promotionService.notifyFirstOrder(1L, "ORD001", new BigDecimal("100"),
                OrderStatusEnum.COMPLETED.getCode(), BigDecimal.ZERO);

        verify(userFeignClient).getUserInfo(1L);
    }

    @Test
    @DisplayName("首单通知：userFeignClient抛出异常，不阻断流程")
    void testNotifyFirstOrder_userFeignThrows() {
        when(userFeignClient.getUserInfo(1L)).thenThrow(new RuntimeException("用户服务不可用"));

        promotionService.notifyFirstOrder(1L, "ORD001", new BigDecimal("100"),
                OrderStatusEnum.COMPLETED.getCode(), BigDecimal.ZERO);

        verify(userFeignClient).getUserInfo(1L);
    }

    @Test
    @DisplayName("首单通知：userFeignClient成功但data中无referrerId")
    void testNotifyFirstOrder_noReferrerIdInData() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "testUser");
        R<Map<String, Object>> resp = R.ok(data);
        when(userFeignClient.getUserInfo(1L)).thenReturn(resp);
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);

        promotionService.notifyFirstOrder(1L, "ORD001", new BigDecimal("100"),
                OrderStatusEnum.COMPLETED.getCode(), BigDecimal.ZERO);

        verify(userFeignClient).getUserInfo(1L);
    }

    @Test
    @DisplayName("首单通知：userFeignClient成功有referrerId，calcReward成功划转")
    void testNotifyFirstOrder_withReferrerIdAndSuccess() {
        Map<String, Object> data = new HashMap<>();
        data.put("referrerId", 200L);
        R<Map<String, Object>> resp = R.ok(data);
        when(userFeignClient.getUserInfo(1L)).thenReturn(resp);
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        promotionService.notifyFirstOrder(1L, "ORD001", new BigDecimal("100"),
                OrderStatusEnum.COMPLETED.getCode(), BigDecimal.ZERO);

        verify(ledgerFeignClient).ledgerOp(any());
        verify(promotionPendingMapper).selectCount(any());
    }

    @Test
    @DisplayName("首单通知：refundAmount为null时按0处理")
    void testNotifyFirstOrder_nullRefundAmount() {
        Map<String, Object> data = new HashMap<>();
        data.put("referrerId", 200L);
        R<Map<String, Object>> resp = R.ok(data);
        when(userFeignClient.getUserInfo(1L)).thenReturn(resp);
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        promotionService.notifyFirstOrder(1L, "ORD001", new BigDecimal("100"),
                OrderStatusEnum.COMPLETED.getCode(), null);

        verify(ledgerFeignClient).ledgerOp(any());
    }

    @Test
    @DisplayName("首单通知：userFeignClient返回success但data为null")
    void testNotifyFirstOrder_userFeignSuccessButNullData() {
        R<Map<String, Object>> resp = R.ok(null);
        when(userFeignClient.getUserInfo(1L)).thenReturn(resp);
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);

        promotionService.notifyFirstOrder(1L, "ORD001", new BigDecimal("100"),
                OrderStatusEnum.COMPLETED.getCode(), BigDecimal.ZERO);

        verify(userFeignClient).getUserInfo(1L);
    }

    // ============== rollbackReward 分支测试 (status=1账本回滚失败) ==============

    @Test
    @DisplayName("奖励回滚：status=1已补发，账本回滚划转失败抛BizException")
    void testRollbackReward_claimedStatusLedgerFail() {
        RollbackRewardDTO dto = new RollbackRewardDTO();
        dto.setUserId(1L);
        dto.setOrderNo("ORD001");

        PromotionPending pending = buildPending(1L, "ORD001", 1,
                new BigDecimal("10.00"), 100L);
        when(promotionPendingMapper.selectOne(any())).thenReturn(pending);
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(null);

        assertThrows(BizException.class, () -> promotionService.rollbackReward(dto));
    }

    @Test
    @DisplayName("奖励回滚：status=1已补发，账本返回失败(isSuccess=false)抛BizException")
    void testRollbackReward_claimedStatusLedgerReturnFail() {
        RollbackRewardDTO dto = new RollbackRewardDTO();
        dto.setUserId(1L);
        dto.setOrderNo("ORD001");

        PromotionPending pending = buildPending(1L, "ORD001", 1,
                new BigDecimal("10.00"), 100L);
        when(promotionPendingMapper.selectOne(any())).thenReturn(pending);
        R<Object> fail = R.fail("账本余额不足");
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(fail);

        assertThrows(BizException.class, () -> promotionService.rollbackReward(dto));
    }

    // ============== pendingAutoFill 分支测试 (账本返回null走重试) ==============

    @Test
    @DisplayName("批量补发：账本返回null(非异常)走重试次数增加分支")
    void testPendingAutoFill_ledgerReturnsNull() {
        PromotionPending p = buildPending(1L, "ORD001", 0, new BigDecimal("10.00"), 100L);
        when(promotionPendingMapper.selectList(any()))
                .thenReturn(Collections.singletonList(p));
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(null);

        int result = promotionService.pendingAutoFill();

        assertEquals(0, result);
        verify(promotionPendingMapper).updateById(any(PromotionPending.class));
    }

    @Test
    @DisplayName("批量补发：混合场景 - 成功+账本返回null+异常")
    void testPendingAutoFill_mixedSuccessNullException() {
        PromotionPending p1 = buildPending(1L, "ORD001", 0, new BigDecimal("10.00"), 100L);
        PromotionPending p2 = buildPending(2L, "ORD002", 0, new BigDecimal("20.00"), 200L);
        PromotionPending p3 = buildPending(3L, "ORD003", 0, new BigDecimal("30.00"), 300L);
        when(promotionPendingMapper.selectList(any()))
                .thenReturn(Arrays.asList(p1, p2, p3));
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any()))
                .thenReturn(ok)
                .thenReturn(null)
                .thenThrow(new RuntimeException("超时"));

        int result = promotionService.pendingAutoFill();

        assertEquals(1, result);
        verify(promotionPendingMapper, times(3)).updateById(any(PromotionPending.class));
    }

    @Test
    @DisplayName("批量补发：单条账本返回isSuccess=false")
    void testPendingAutoFill_ledgerReturnFail() {
        PromotionPending p = buildPending(1L, "ORD001", 0, new BigDecimal("10.00"), 100L);
        when(promotionPendingMapper.selectList(any()))
                .thenReturn(Collections.singletonList(p));
        R<Object> fail = R.fail("处理中");
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(fail);

        int result = promotionService.pendingAutoFill();

        assertEquals(0, result);
        verify(promotionPendingMapper).updateById(any(PromotionPending.class));
    }

    // ============== pendingList 边界测试 ==============

    @Test
    @DisplayName("查询挂账：page和size为null时使用默认值(1和20)")
    void testPendingList_nullPageAndSize() {
        Page<PromotionPending> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(promotionPendingMapper.selectPage(any(), any())).thenReturn(page);

        var result = promotionService.pendingList(null, null, null);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    @Test
    @DisplayName("查询挂账：按status=0(待补发)过滤")
    void testPendingList_filterByPendingStatus() {
        PromotionPending p = buildPending(1L, "ORD001", 0, new BigDecimal("10.00"), 100L);
        Page<PromotionPending> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(p));
        page.setTotal(1);
        when(promotionPendingMapper.selectPage(any(), any())).thenReturn(page);

        var result = promotionService.pendingList(1, 20, 0);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    // ============== checkFirstOrder 边界测试 ==============

    @Test
    @DisplayName("校验首单：部分退款(退款<订单金额)仍有效")
    void testCheckFirstOrder_partialRefund() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setOrderAmount(new BigDecimal("100"));
        dto.setRefundAmount(new BigDecimal("30"));
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertTrue(result.getFirstOrder());
    }

    @Test
    @DisplayName("校验首单：refundAmount为null仍有效")
    void testCheckFirstOrder_nullRefundAmount() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setRefundAmount(null);
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertTrue(result.getFirstOrder());
    }

    @Test
    @DisplayName("校验首单：有效消费金额刚好等于1元(边界)")
    void testCheckFirstOrder_exactlyMinAmount() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setOrderAmount(new BigDecimal("1"));
        dto.setRefundAmount(BigDecimal.ZERO);
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertTrue(result.getFirstOrder());
    }

    @Test
    @DisplayName("校验首单：有效消费金额为0.99元(低于门槛)")
    void testCheckFirstOrder_belowMinAmount() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setOrderAmount(new BigDecimal("0.99"));
        dto.setRefundAmount(BigDecimal.ZERO);

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertFalse(result.getFirstOrder());
    }

    // ============== calcReward 分支测试 (ledger返回null走挂账) ==============

    @Test
    @DisplayName("奖励计算：账本返回null(非异常)走挂账分支")
    void testCalcReward_ledgerReturnNull() {
        FirstOrderCheckDTO dto = buildValidDto();
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(null);
        doAnswer(invocation -> {
            PromotionPending p = invocation.getArgument(0);
            p.setId(888888L);
            return 1;
        }).when(promotionPendingMapper).insert(any());

        RewardResultDTO result = promotionService.calcReward(dto);

        assertTrue(result.getFirstOrder());
        assertFalse(result.getSuccess());
        assertNotNull(result.getPendingId());
        assertEquals(888888L, result.getPendingId());
    }

    @Test
    @DisplayName("奖励计算：首单非首单直接返回，不触发划转")
    void testCalcReward_notFirstOrder() {
        FirstOrderCheckDTO dto = buildValidDto();
        when(promotionPendingMapper.selectCount(any())).thenReturn(3L);

        RewardResultDTO result = promotionService.calcReward(dto);

        assertFalse(result.getFirstOrder());
        assertNull(result.getSuccess());
        verify(ledgerFeignClient, never()).ledgerOp(any());
        verify(promotionPendingMapper, never()).insert(any());
    }

    // ============== rollbackReward status=2 已废弃分支 ==============

    @Test
    @DisplayName("奖励回滚：status=2已废弃(非0非1)静默返回")
    void testRollbackReward_statusAlreadyDeprecated() {
        RollbackRewardDTO dto = new RollbackRewardDTO();
        dto.setUserId(1L);
        dto.setOrderNo("ORD001");

        PromotionPending pending = buildPending(1L, "ORD001", 2,
                new BigDecimal("10.00"), 100L);
        when(promotionPendingMapper.selectOne(any())).thenReturn(pending);

        promotionService.rollbackReward(dto);

        verify(ledgerFeignClient, never()).ledgerOp(any());
        verify(promotionPendingMapper, never()).updateById(any());
    }

    // ============== checkFirstOrder 分布式锁已持有但被中断 ==============

    @Test
    @DisplayName("校验首单：tryLock返回true但isHeldByCurrentThread为false时仍执行unlock逻辑")
    void testCheckFirstOrder_lockNotHeldByCurrentThread() throws Exception {
        FirstOrderCheckDTO dto = buildValidDto();
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);
        when(rLock.isHeldByCurrentThread()).thenReturn(false);

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertTrue(result.getFirstOrder());
        verify(rLock, never()).unlock();
    }
}