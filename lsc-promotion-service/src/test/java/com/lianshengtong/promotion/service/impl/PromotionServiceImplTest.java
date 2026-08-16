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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("推广服务单元测试")
class PromotionServiceImplTest {

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

    // ============== checkFirstOrder 测试 ==============

    @Test
    @DisplayName("校验首单：有效已完成订单 - 无挂账记录 -> 首单")
    void testCheckFirstOrder_ValidCompletedOrder() {
        FirstOrderCheckDTO dto = buildValidDto();
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertTrue(result.getFirstOrder());
        assertEquals(new BigDecimal("100"), result.getFirstOrderAmount());
    }

    @Test
    @DisplayName("校验首单：30天内首单返回 true")
    void testCheckFirstOrder_firstOrderWithin30Days() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setOrderAmount(new BigDecimal("50"));
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertTrue(result.getFirstOrder());
        assertEquals(new BigDecimal("50"), result.getFirstOrderAmount());
    }

    @Test
    @DisplayName("校验首单：存在历史订单返回 false")
    void testCheckFirstOrder_existingOrderReturnsFalse() {
        FirstOrderCheckDTO dto = buildValidDto();
        when(promotionPendingMapper.selectCount(any())).thenReturn(5L);

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertFalse(result.getFirstOrder());
    }

    @Test
    @DisplayName("校验首单：金额不足 1 元 -> 非首单")
    void testCheckFirstOrder_InsufficientAmount() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setOrderAmount(new BigDecimal("0.50"));

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertFalse(result.getFirstOrder());
    }

    @Test
    @DisplayName("校验首单：订单未完成 -> 非首单")
    void testCheckFirstOrder_NotCompleted() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setOrderStatus(OrderStatusEnum.PAID.getCode());

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertFalse(result.getFirstOrder());
    }

    @Test
    @DisplayName("校验首单：全额退款 -> 非首单")
    void testCheckFirstOrder_FullyRefunded() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setRefundAmount(new BigDecimal("100"));

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertFalse(result.getFirstOrder());
    }

    @Test
    @DisplayName("校验首单：分布式锁获取失败 -> 抛出 BizException")
    void testCheckFirstOrder_ConcurrentLockFail() {
        FirstOrderCheckDTO dto = buildValidDto();
        try {
            when(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);
        } catch (InterruptedException e) {
            // ignore
        }

        assertThrows(BizException.class, () -> promotionService.checkFirstOrder(dto));
    }

    @Test
    @DisplayName("校验首单：已有挂账记录 -> 非首单")
    void testCheckFirstOrder_ExistingRecord() {
        FirstOrderCheckDTO dto = buildValidDto();
        when(promotionPendingMapper.selectCount(any())).thenReturn(1L);

        RewardResultDTO result = promotionService.checkFirstOrder(dto);

        assertFalse(result.getFirstOrder());
    }

    // ============== calculateReward (calcReward) 测试 ==============

    @Test
    @DisplayName("奖励计算：标准奖励计算(10%比例)")
    void testCalcReward_standardReward() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setOrderAmount(new BigDecimal("200"));
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        RewardResultDTO result = promotionService.calcReward(dto);

        assertTrue(result.getFirstOrder());
        assertTrue(result.getSuccess());
        assertEquals(new BigDecimal("20.00"), result.getRewardAmount());
    }

    @Test
    @DisplayName("奖励计算：有推荐人且账本划转成功")
    void testCalcReward_WithReferrer() {
        FirstOrderCheckDTO dto = buildValidDto();
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        RewardResultDTO result = promotionService.calcReward(dto);

        assertTrue(result.getFirstOrder());
        assertTrue(result.getSuccess());
        assertEquals(100L, result.getReferrerId());
        assertEquals(new BigDecimal("10.00"), result.getRewardAmount());
    }

    @Test
    @DisplayName("奖励计算：奖励封顶测试")
    void testCalcReward_rewardCappedAtMax() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setOrderAmount(new BigDecimal("10000"));
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        RewardResultDTO result = promotionService.calcReward(dto);

        assertTrue(result.getSuccess());
        assertEquals(new BigDecimal("1000.00"), result.getRewardAmount());
    }

    @Test
    @DisplayName("奖励计算：零金额订单返回零奖励")
    void testCalcReward_zeroAmountReturnsZeroReward() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setOrderAmount(BigDecimal.ZERO);
        dto.setOrderStatus(OrderStatusEnum.COMPLETED.getCode());

        RewardResultDTO result = promotionService.calcReward(dto);

        assertFalse(result.getFirstOrder());
    }

    @Test
    @DisplayName("奖励计算：无推荐人 -> success=false")
    void testCalcReward_NoReferrer() {
        FirstOrderCheckDTO dto = buildValidDto();
        dto.setReferrerId(null);
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);

        RewardResultDTO result = promotionService.calcReward(dto);

        assertTrue(result.getFirstOrder());
        assertFalse(result.getSuccess());
    }

    @Test
    @DisplayName("奖励计算：账本划转失败 -> 落挂账表")
    void testCalcReward_LedgerFail_FallsBackToPending() {
        FirstOrderCheckDTO dto = buildValidDto();
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);
        when(ledgerFeignClient.ledgerOp(any())).thenThrow(new RuntimeException("账本服务不可用"));
        doAnswer(invocation -> {
            PromotionPending p = invocation.getArgument(0);
            p.setId(999999L);
            return 1;
        }).when(promotionPendingMapper).insert(any());

        RewardResultDTO result = promotionService.calcReward(dto);

        assertTrue(result.getFirstOrder());
        assertFalse(result.getSuccess());
        assertNotNull(result.getPendingId());
        verify(promotionPendingMapper).insert(any(PromotionPending.class));
    }

    @Test
    @DisplayName("奖励计算：验证奖励金额 = 订单金额 * 10%")
    void testCalcReward_RewardAmount() {
        FirstOrderCheckDTO dto = buildValidDto();
        when(promotionPendingMapper.selectCount(any())).thenReturn(0L);
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        RewardResultDTO result = promotionService.calcReward(dto);

        assertEquals(new BigDecimal("10.00"), result.getRewardAmount());
    }

    // ============== rollbackReward 测试 ==============

    @Test
    @DisplayName("奖励回滚：成功回滚恢复余额")
    void testRollbackReward_success() {
        RollbackRewardDTO dto = new RollbackRewardDTO();
        dto.setUserId(1L);
        dto.setOrderNo("ORD001");

        PromotionPending pending = buildPending(1L, "ORD001", 1,
                new BigDecimal("10.00"), 100L);
        when(promotionPendingMapper.selectOne(any())).thenReturn(pending);
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        promotionService.rollbackReward(dto);

        verify(ledgerFeignClient).ledgerOp(any());
        verify(promotionPendingMapper).updateById(any(PromotionPending.class));
    }

    @Test
    @DisplayName("奖励回滚：挂账状态(未补发)直接置废弃")
    void testRollbackReward_pendingStatus() {
        RollbackRewardDTO dto = new RollbackRewardDTO();
        dto.setUserId(1L);
        dto.setOrderNo("ORD001");

        PromotionPending pending = buildPending(1L, "ORD001", 0,
                new BigDecimal("10.00"), 100L);
        when(promotionPendingMapper.selectOne(any())).thenReturn(pending);

        promotionService.rollbackReward(dto);

        verify(ledgerFeignClient, never()).ledgerOp(any());
        verify(promotionPendingMapper).updateById(any(PromotionPending.class));
    }

    @Test
    @DisplayName("奖励回滚：已回滚状态幂等返回成功")
    void testRollbackReward_alreadyRolledBack() {
        RollbackRewardDTO dto = new RollbackRewardDTO();
        dto.setUserId(1L);
        dto.setOrderNo("ORD001");

        PromotionPending pending = buildPending(1L, "ORD001", 2,
                new BigDecimal("10.00"), 100L);
        when(promotionPendingMapper.selectOne(any())).thenReturn(pending);

        promotionService.rollbackReward(dto);

        verify(ledgerFeignClient, never()).ledgerOp(any());
    }

    @Test
    @DisplayName("奖励回滚：不存在挂账记录静默返回")
    void testRollbackReward_noRecord() {
        RollbackRewardDTO dto = new RollbackRewardDTO();
        dto.setUserId(999L);
        dto.setOrderNo("NOT_EXIST");

        when(promotionPendingMapper.selectOne(any())).thenReturn(null);

        promotionService.rollbackReward(dto);

        verify(ledgerFeignClient, never()).ledgerOp(any());
    }

    // ============== batchIssue (pendingAutoFill) 测试 ==============

    @Test
    @DisplayName("批量补发：成功补发挂账奖励")
    void testPendingAutoFill_success() {
        PromotionPending p1 = buildPending(1L, "ORD001", 0, new BigDecimal("10.00"), 100L);
        PromotionPending p2 = buildPending(2L, "ORD002", 0, new BigDecimal("20.00"), 200L);
        when(promotionPendingMapper.selectList(any()))
                .thenReturn(Arrays.asList(p1, p2));
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        int result = promotionService.pendingAutoFill();

        assertEquals(2, result);
        verify(ledgerFeignClient, times(2)).ledgerOp(any());
    }

    @Test
    @DisplayName("批量补发：部分成功部分失败")
    void testPendingAutoFill_partialSuccess() {
        PromotionPending p1 = buildPending(1L, "ORD001", 0, new BigDecimal("10.00"), 100L);
        PromotionPending p2 = buildPending(2L, "ORD002", 0, new BigDecimal("20.00"), 200L);
        when(promotionPendingMapper.selectList(any()))
                .thenReturn(Arrays.asList(p1, p2));
        R<Object> ok = R.ok();
        R<Object> fail = R.fail("账本不可用");
        when(ledgerFeignClient.ledgerOp(any()))
                .thenReturn(ok)
                .thenReturn(fail);

        int result = promotionService.pendingAutoFill();

        assertEquals(1, result);
    }

    @Test
    @DisplayName("批量补发：空挂账列表返回 0")
    void testPendingAutoFill_emptyList() {
        when(promotionPendingMapper.selectList(any()))
                .thenReturn(Collections.emptyList());

        int result = promotionService.pendingAutoFill();

        assertEquals(0, result);
    }

    @Test
    @DisplayName("批量补发：调用异常也计入失败并增加重试次数")
    void testPendingAutoFill_exceptionHandled() {
        PromotionPending p = buildPending(1L, "ORD001", 0, new BigDecimal("10.00"), 100L);
        when(promotionPendingMapper.selectList(any()))
                .thenReturn(Collections.singletonList(p));
        when(ledgerFeignClient.ledgerOp(any())).thenThrow(new RuntimeException("超时"));

        int result = promotionService.pendingAutoFill();

        assertEquals(0, result);
        verify(promotionPendingMapper).updateById(any(PromotionPending.class));
    }

    // ============== getPromotionRecord / pendingList 测试 ==============

    @Test
    @DisplayName("查询挂账：分页返回有效记录")
    void testPendingList_withRecords() {
        PromotionPending p = buildPending(1L, "ORD001", 0, new BigDecimal("10.00"), 100L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PromotionPending> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(Collections.singletonList(p));
        page.setTotal(1);
        when(promotionPendingMapper.selectPage(any(), any())).thenReturn(page);

        var result = promotionService.pendingList(1, 20, null);

        assertNotNull(result);
        assertEquals(1, result.getTotal());
    }

    @Test
    @DisplayName("查询挂账：按状态过滤")
    void testPendingList_filterByStatus() {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<PromotionPending> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        when(promotionPendingMapper.selectPage(any(), any())).thenReturn(page);

        var result = promotionService.pendingList(1, 20, 1);

        assertNotNull(result);
        assertEquals(0, result.getTotal());
    }

    // ============== dailyStats (pendingAutoFill 统计) 测试 ==============

    @Test
    @DisplayName("挂账补发：聚合统计 - 全部成功")
    void testPendingAutoFill_allSuccess() {
        List<PromotionPending> pendings = Arrays.asList(
                buildPending(1L, "O1", 0, new BigDecimal("5.00"), 100L),
                buildPending(2L, "O2", 0, new BigDecimal("15.00"), 200L),
                buildPending(3L, "O3", 0, new BigDecimal("25.00"), 300L)
        );
        when(promotionPendingMapper.selectList(any())).thenReturn(pendings);
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        int result = promotionService.pendingAutoFill();

        assertEquals(3, result);
    }

    @Test
    @DisplayName("挂账补发：聚合统计 - 按批次大小限制")
    void testPendingAutoFill_batchSizeLimit() {
        List<PromotionPending> manyPendings = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            manyPendings.add(buildPending((long) i, "ORD" + i, 0,
                    new BigDecimal("10.00"), (long) (i + 100)));
        }
        when(promotionPendingMapper.selectList(any())).thenReturn(manyPendings);
        R<Object> ok = R.ok();
        when(ledgerFeignClient.ledgerOp(any())).thenReturn(ok);

        int result = promotionService.pendingAutoFill();

        assertEquals(100, result);
    }
}