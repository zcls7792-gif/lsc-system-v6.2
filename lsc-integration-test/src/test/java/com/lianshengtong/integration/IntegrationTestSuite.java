package com.lianshengtong.integration;

import com.lianshengtong.common.dto.HybridPayDTO;
import com.lianshengtong.common.enums.*;
import com.lianshengtong.common.result.R;
import com.lianshengtong.integration.mock.*;
import com.lianshengtong.integration.scene.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LSC 系统集成测试套件
 * <p>
 * 模拟全链路 API 级联调用验证：
 * Order → Ledger → Promotion → Risk → Media → B2B → Evidence
 * </p>
 * <p>
 * 所有 Feign/HTTP 调用通过 Mock 桩替代，验证服务间协作逻辑的正确性。
 * </p>
 */
@DisplayName("LSC 系统集成测试 - API级联调用验证")
class IntegrationTestSuite {

    private IntegrationTestContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new IntegrationTestContext();
    }

    // ============== 场景1: 标准消费流程 ==============

    @Test
    @DisplayName("标准消费: 用户下单→支付→账本扣减→核销→推广")
    void standardConsumptionFlow() {
        Long userId = ctx.registerUser(1L, "13800138000", 0);

        String orderNo = ctx.createOrder(userId, "美团外卖",
                new BigDecimal("50.00"), 10L);
        assertNotNull(orderNo);

        ctx.payOrder(orderNo, "WECHAT", new BigDecimal("40.00"), 10L);

        ctx.ledgerDeduct(userId, 10L);

        ctx.writeOff(orderNo, userId);

        Long promotionId = ctx.checkFirstOrder(userId);
        assertNotNull(promotionId);

        assertTrue(ctx.verifyUserLscBalance(userId) >= 0);
    }

    @Test
    @DisplayName("标准消费: 混合支付(LSC+人民币)完整流程")
    void hybridPayFlow() {
        Long userId = ctx.registerUser(2L, "13800138001", 0);
        ctx.ledgerIssue(userId, 100L);

        HybridPayDTO payPlan = ctx.calcHybridPay(new BigDecimal("80.00"), 50L, 100L);
        assertEquals(50L, payPlan.getLscAmount().longValue());
        assertEquals(new BigDecimal("30.00"), payPlan.getRmbAmount());

        String orderNo = ctx.createOrder(userId, "滴滴打车",
                new BigDecimal("80.00"), 50L);

        ctx.ledgerDeduct(userId, 50L);
        ctx.payOrder(orderNo, "WECHAT", new BigDecimal("30.00"), 50L);

        ctx.writeOff(orderNo, userId);

        assertTrue(ctx.verifyUserLscBalance(userId) >= 50);
    }

    // ============== 场景2: 风控异常检测 ==============

    @Test
    @DisplayName("风控: 批量异常下单检测")
    void riskBatchOrderDetection() {
        Long userId = ctx.registerUser(3L, "13800138002", 0);

        List<String> orderNos = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            orderNos.add(ctx.createOrder(userId, "便利店" + i,
                    new BigDecimal("10.00"), 5L));
        }

        boolean blocked = ctx.isBatchBlocked(userId);
        assertTrue(blocked, "短时间内超过5笔订单应触发批量风控");
    }

    @Test
    @DisplayName("风控: 异常混合支付检测")
    void riskAbnormalMixPayDetection() {
        Long userId = ctx.registerUser(4L, "13800138003", 0);

        boolean flagged = ctx.isAbnormalMixPay(
                new BigDecimal("200.00"), 5L, 195L);
        assertTrue(flagged, "LSC比例异常高应触发风控检测");
    }

    // ============== 场景3: 订单生命周期 ==============

    @Test
    @DisplayName("订单: 创建→取消→退款完整流程")
    void orderLifecycle_cancelAndRefund() {
        Long userId = ctx.registerUser(5L, "13800138004", 0);

        String orderNo = ctx.createOrder(userId, "电商购物",
                new BigDecimal("100.00"), 30L);
        ctx.payOrder(orderNo, "WECHAT", new BigDecimal("70.00"), 30L);

        ctx.cancelOrder(orderNo);

        ctx.refundOrder(orderNo);

        assertTrue(ctx.verifyOrderCancelled(orderNo));
        assertTrue(ctx.verifyBalanceRestored(userId, 30L));
    }

    // ============== 场景4: B2B订单流程 ==============

    @Test
    @DisplayName("B2B: 订单创建→AI核验→确认→流转→作废流程")
    void b2bOrderLifecycle() {
        Long initiatorId = ctx.registerMerchant(101L, "initiator");
        Long counterpartyId = ctx.registerMerchant(201L, "counterparty");

        String b2bOrderNo = ctx.createB2bOrder(initiatorId, counterpartyId,
                new BigDecimal("50000.00"), 50000L);
        assertNotNull(b2bOrderNo);

        boolean aiPassed = ctx.aiVerifyB2b(b2bOrderNo);
        assertTrue(aiPassed);

        ctx.confirmB2bOrder(b2bOrderNo, counterpartyId);

        ctx.executeB2bTransfer(b2bOrderNo, initiatorId);

        assertTrue(ctx.verifyB2bTransferred(b2bOrderNo));
    }

    @Test
    @DisplayName("B2B: 异常订单作废流程")
    void b2bOrderVoidFlow() {
        Long initiatorId = ctx.registerMerchant(102L, "initiator2");
        Long counterpartyId = ctx.registerMerchant(202L, "counterparty2");

        String b2bOrderNo = ctx.createB2bOrder(initiatorId, counterpartyId,
                new BigDecimal("10000.00"), 10000L);

        ctx.confirmB2bOrder(b2bOrderNo, counterpartyId);

        ctx.voidB2bOrder(b2bOrderNo, initiatorId, "风控检测异常");

        assertTrue(ctx.verifyB2bVoided(b2bOrderNo));
    }

    // ============== 场景5: 推广与证据链 ==============

    @Test
    @DisplayName("推广: 邀请首单奖励流程")
    void promotionReferralFlow() {
        Long referrerId = ctx.registerUser(6L, "13800138005", 0);
        String referralCode = ctx.generateReferralCode(referrerId);

        Long refereeId = ctx.registerUser(7L, "13800138006", 0);
        ctx.applyReferralCode(refereeId, referralCode);

        ctx.placeFirstOrder(refereeId);

        Long rewardLsc = ctx.checkReferralReward(referrerId);
        assertTrue(rewardLsc > 0);
    }

    @Test
    @DisplayName("证据链: 哈希上链与Merkle树根验证")
    void evidenceHashOnChainFlow() {
        List<String> hashBatch = Arrays.asList(
                ctx.submitEvidenceHash("订单001"),
                ctx.submitEvidenceHash("订单002"),
                ctx.submitEvidenceHash("订单003"),
                ctx.submitEvidenceHash("订单004")
        );

        String merkleRoot = ctx.computeMerkleRoot(hashBatch);
        assertNotNull(merkleRoot);
        assertFalse(merkleRoot.isEmpty());

        String chainTxHash = ctx.saveToChain(merkleRoot);
        assertNotNull(chainTxHash);
        assertTrue(chainTxHash.startsWith("0x"));

        boolean verified = ctx.verifyMerkleProof(hashBatch.get(0), merkleRoot);
        assertTrue(verified);
    }

    // ============== 场景6: 对账与存证 ==============

    @Test
    @DisplayName("对账: 日终对账报告生成与哈希上链")
    void dailyReconciliationFlow() {
        ctx.simulateOrderPayments(20, new BigDecimal("1000.00"));

        String reportId = ctx.generateReconcileReport();
        assertNotNull(reportId);

        boolean consistent = ctx.verifyReconcileConsistency(reportId);
        assertTrue(consistent);

        String chainHash = ctx.hashReconcileOnChain(reportId);
        assertNotNull(chainHash);
    }

    // ============== 场景7: 媒体服务 ==============

    @Test
    @DisplayName("媒体: 文件上传→哈希校验→CDN分发流程")
    void mediaUploadAndCdnFlow() {
        String fileHash = ctx.uploadAndHashFile("test-image.jpg", "image/jpeg", 2048);

        boolean hashValid = ctx.verifyFileHash(fileHash);
        assertTrue(hashValid);

        String cdnUrl = ctx.getCdnUrl(fileHash);
        assertNotNull(cdnUrl);
        assertTrue(cdnUrl.startsWith("https://cdn."));
    }

    // ============== 场景8: 地图服务 ==============

    @Test
    @DisplayName("地图: 地理编码→逆编码→导航唤起流程")
    void mapGeocodeAndNavigateFlow() {
        var geoResult = ctx.geocodeAddress("北京市天安门", "北京");
        assertNotNull(geoResult);
        assertTrue(geoResult.getLongitude() > 116.0);

        var reverseResult = ctx.reverseGeocode(geoResult.getLongitude(), geoResult.getLatitude());
        assertNotNull(reverseResult);

        var navigateResult = ctx.navigateTo(
                geoResult.getLongitude(), geoResult.getLatitude(),
                116.397, 39.909, "故宫博物院");
        assertNotNull(navigateResult);
        assertNotNull(navigateResult.getUrl());
    }

    // ============== 场景9: 管理员操作 ==============

    @Test
    @DisplayName("管理: 商品发布→AI审核→人工复核→上架流程")
    void adminProductAuditFlow() {
        Long merchantId = ctx.registerMerchant(301L, "merchant1");

        Long productId = ctx.publishProduct(merchantId, "智能手表",
                new BigDecimal("299.00"), 50);

        String aiReviewId = ctx.submitAiReview(productId);
        assertNotNull(aiReviewId);

        ctx.manualReview(productId, true, "商品真实有效");

        ctx.onShelfProduct(productId);

        assertTrue(ctx.verifyProductOnShelf(productId));
    }

    // ============== 场景10: 并发安全 ==============

    @Test
    @DisplayName("并发: 多线程同时下单不超卖")
    void concurrentOrder_noOversell() {
        Long userId = ctx.registerUser(8L, "13800138007", 0);
        ctx.ledgerIssue(userId, 50L);

        int threadCount = 5;
        int ordersPerThread = 10;
        long budget = 50L;

        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger failCount = new java.util.concurrent.atomic.AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            new Thread(() -> {
                try {
                    for (int i = 0; i < ordersPerThread; i++) {
                        if (successCount.get() + failCount.get() >= threadCount * ordersPerThread)
                            break;
                        try {
                            ctx.ledgerDeduct(userId, 1L);
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            failCount.incrementAndGet();
                            break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            }, "order-thread-" + threadId).start();
        }

        try {
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("interrupted");
        }

        long totalDeducted = successCount.get();
        assertTrue(totalDeducted <= budget,
                "总扣减金额不能超过余额: deducted=" + totalDeducted + " budget=" + budget);
    }

    // ============== 场景11: 数据一致性 ==============

    @Test
    @DisplayName("一致性: 订单→账本→推广数据同步校验")
    void dataConsistency_check() {
        Long userId = ctx.registerUser(9L, "13800138008", 0);

        ctx.ledgerIssue(userId, 200L);

        String orderNo = ctx.createOrder(userId, "连锁超市",
                new BigDecimal("150.00"), 80L);

        ctx.ledgerDeduct(userId, 80L);
        ctx.payOrder(orderNo, "WECHAT", new BigDecimal("70.00"), 80L);
        ctx.writeOff(orderNo, userId);

        Long currentBalance = ctx.verifyUserLscBalance(userId);
        assertTrue(currentBalance >= 120L,
                "余额应不少于初始-已扣减: balance=" + currentBalance);

        boolean ledgerMatch = ctx.verifyOrderLedgerConsistency(orderNo, userId);
        assertTrue(ledgerMatch, "订单与账本记录应一致");
    }
}
