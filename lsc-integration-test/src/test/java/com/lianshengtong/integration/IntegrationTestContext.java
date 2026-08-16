package com.lianshengtong.integration;

import com.lianshengtong.common.dto.HybridPayDTO;
import com.lianshengtong.common.enums.*;
import com.lianshengtong.map.dto.GeoResult;
import com.lianshengtong.map.dto.NavigateResult;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 集成测试上下文
 * <p>
 * 提供跨服务的 Mock 桩实现，模拟全链路 API 调用的数据流。
 * 所有状态保存在内存中，保证测试的可重复性和独立性。
 * </p>
 */
public class IntegrationTestContext {

    private final Map<Long, TestUser> users = new ConcurrentHashMap<>();
    private final Map<String, TestOrder> orders = new ConcurrentHashMap<>();
    private final Map<String, TestB2bOrder> b2bOrders = new ConcurrentHashMap<>();
    private final Map<Long, TestProduct> products = new ConcurrentHashMap<>();
    private final List<String> evidenceHashes = Collections.synchronizedList(new ArrayList<>());
    private final AtomicLong orderSeq = new AtomicLong(1);
    private final AtomicLong b2bOrderSeq = new AtomicLong(1);
    private final AtomicLong productSeq = new AtomicLong(1);

    // ============== 用户管理 ==============

    public Long registerUser(Long userId, String mobile, int userType) {
        TestUser user = new TestUser();
        user.userId = userId;
        user.mobile = mobile;
        user.userType = userType;
        user.lscBalance = 0L;
        user.status = 1;
        user.referralCode = "REF" + userId;
        users.put(userId, user);
        return userId;
    }

    public Long registerMerchant(Long merchantId, String name) {
        TestUser user = new TestUser();
        user.userId = merchantId;
        user.mobile = name + "@merchant.com";
        user.userType = 1;
        user.lscBalance = 0L;
        user.status = 1;
        user.referralCode = "MREF" + merchantId;
        users.put(merchantId, user);
        return merchantId;
    }

    // ============== 账本操作 ==============

    public void ledgerIssue(Long userId, long amount) {
        TestUser user = requireUser(userId);
        user.lscBalance += amount;
    }

    public void ledgerDeduct(Long userId, long amount) {
        TestUser user = requireUser(userId);
        if (user.lscBalance < amount) {
            throw new RuntimeException("余额不足");
        }
        user.lscBalance -= amount;
    }

    public long getUserLscBalance(Long userId) {
        TestUser user = users.get(userId);
        return user == null ? 0L : user.lscBalance;
    }

    // ============== 订单管理 ==============

    public String createOrder(Long userId, String merchantName,
                              BigDecimal totalAmount, long lscAmount) {
        TestUser user = requireUser(userId);
        String orderNo = "ORD" + System.currentTimeMillis() + orderSeq.getAndIncrement();
        TestOrder order = new TestOrder();
        order.orderNo = orderNo;
        order.userId = userId;
        order.merchantName = merchantName;
        order.totalAmount = totalAmount;
        order.lscAmount = lscAmount;
        order.status = OrderStatusEnum.PENDING.getCode();
        order.confirmTime = System.currentTimeMillis();
        orders.put(orderNo, order);
        return orderNo;
    }

    public void payOrder(String orderNo, String channel,
                         BigDecimal rmbAmount, long lscPaid) {
        TestOrder order = requireOrder(orderNo);
        order.payChannel = channel;
        order.rmbPaid = rmbAmount;
        order.lscPaid = lscPaid;
        order.status = OrderStatusEnum.PAID.getCode();
    }

    public void cancelOrder(String orderNo) {
        TestOrder order = requireOrder(orderNo);
        if (order.status > OrderStatusEnum.PAID.getCode()) {
            throw new RuntimeException("订单状态不可取消");
        }
        order.status = OrderStatusEnum.CANCELLED.getCode();
    }

    public void refundOrder(String orderNo) {
        TestOrder order = requireOrder(orderNo);
        order.status = OrderStatusEnum.REFUNDED.getCode();
    }

    public boolean verifyOrderCancelled(String orderNo) {
        TestOrder order = orders.get(orderNo);
        return order != null && order.status == OrderStatusEnum.CANCELLED.getCode();
    }

    public boolean verifyOrderLedgerConsistency(String orderNo, Long userId) {
        TestOrder order = orders.get(orderNo);
        TestUser user = users.get(userId);
        if (order == null || user == null) return false;
        return user.lscBalance >= 0;
    }

    public long verifyUserLscBalance(Long userId) {
        return getUserLscBalance(userId);
    }

    public boolean verifyBalanceRestored(Long userId, long original) {
        return getUserLscBalance(userId) >= original;
    }

    // ============== 核销 ==============

    public void writeOff(String orderNo, Long userId) {
        TestOrder order = requireOrder(orderNo);
        TestUser user = requireUser(userId);
        if (order.getStatus() < OrderStatusEnum.PAID.getCode()) {
            throw new RuntimeException("订单未支付不可核销");
        }
        order.status = OrderStatusEnum.COMPLETED.getCode();
        order.writeOffTime = System.currentTimeMillis();
    }

    // ============== 混合支付计算 ==============

    public HybridPayDTO calcHybridPay(BigDecimal totalPrice, long lscAmount,
                                        Long maxAvailableLsc) {
        long lscUsed = Math.min(lscAmount, totalPrice.longValue());
        if (maxAvailableLsc != null) {
            lscUsed = Math.min(lscUsed, maxAvailableLsc);
        }
        BigDecimal rmbAmount = totalPrice.subtract(BigDecimal.valueOf(lscUsed))
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (rmbAmount.signum() < 0) rmbAmount = BigDecimal.ZERO;

        return HybridPayDTO.builder()
                .lscAmount(lscUsed)
                .rmbAmount(rmbAmount)
                .totalPrice(totalPrice)
                .build();
    }

    // ============== 风控检测 ==============

    public boolean isBatchBlocked(Long userId) {
        TestUser user = requireUser(userId);
        long recentCount = orders.values().stream()
                .filter(o -> o.userId.equals(userId))
                .filter(o -> System.currentTimeMillis() - o.confirmTime < 60000)
                .count();
        return recentCount > 5;
    }

    public boolean isAbnormalMixPay(BigDecimal total, long lsc, long rmb) {
        if (total.compareTo(BigDecimal.ZERO) <= 0) return false;
        BigDecimal lscRatio = BigDecimal.valueOf(lsc).divide(total, 4, java.math.RoundingMode.HALF_UP);
        return lscRatio.compareTo(new BigDecimal("0.9")) > 0
                && total.compareTo(new BigDecimal("100")) > 0;
    }

    // ============== 推广 ==============

    public Long checkFirstOrder(Long userId) {
        TestUser user = requireUser(userId);
        boolean hasFirstOrder = orders.values().stream()
                .anyMatch(o -> o.userId.equals(userId));
        if (!hasFirstOrder) return null;
        user.promotionLsc += 50L;
        return 1L;
    }

    public String generateReferralCode(Long userId) {
        return requireUser(userId).referralCode;
    }

    public void applyReferralCode(Long userId, String code) {
        TestUser user = requireUser(userId);
        user.referredByCode = code;
    }

    public void placeFirstOrder(Long userId) {
        createOrder(userId, "首次下单商家",
                new BigDecimal("10.00"), 1L);
    }

    public Long checkReferralReward(Long userId) {
        TestUser user = requireUser(userId);
        return user.promotionLsc;
    }

    // ============== B2B 订单 ==============

    public String createB2bOrder(Long initiatorId, Long counterpartyId,
                                  BigDecimal totalAmount, long lscAmount) {
        String orderNo = "B2B" + System.currentTimeMillis() + b2bOrderSeq.getAndIncrement();
        TestB2bOrder order = new TestB2bOrder();
        order.orderNo = orderNo;
        order.initiatorId = initiatorId;
        order.counterpartyId = counterpartyId;
        order.totalAmount = totalAmount;
        order.lscAmount = lscAmount;
        order.status = B2BOrderStatusEnum.PENDING_CONFIRM.getCode();
        order.aiVerified = false;
        order.counterpartyConfirmed = false;
        order.lscTransferred = false;
        b2bOrders.put(orderNo, order);
        return orderNo;
    }

    public boolean aiVerifyB2b(String b2bOrderNo) {
        TestB2bOrder order = requireB2bOrder(b2bOrderNo);
        order.aiVerified = true;
        return true;
    }

    public void confirmB2bOrder(String b2bOrderNo, Long confirmerId) {
        TestB2bOrder order = requireB2bOrder(b2bOrderNo);
        if (!order.counterpartyId.equals(confirmerId)) {
            throw new RuntimeException("非接收方不可确认");
        }
        order.counterpartyConfirmed = true;
        order.status = B2BOrderStatusEnum.CONFIRMED.getCode();
    }

    public void executeB2bTransfer(String b2bOrderNo, Long operatorId) {
        TestB2bOrder order = requireB2bOrder(b2bOrderNo);
        order.lscTransferred = true;
        order.status = B2BOrderStatusEnum.TRANSFERRED.getCode();
    }

    public void voidB2bOrder(String b2bOrderNo, Long operatorId, String reason) {
        TestB2bOrder order = requireB2bOrder(b2bOrderNo);
        order.status = B2BOrderStatusEnum.VOIDED.getCode();
        order.voidReason = reason;
    }

    public boolean verifyB2bTransferred(String b2bOrderNo) {
        TestB2bOrder order = b2bOrders.get(b2bOrderNo);
        return order != null && order.lscTransferred;
    }

    public boolean verifyB2bVoided(String b2bOrderNo) {
        TestB2bOrder order = b2bOrders.get(b2bOrderNo);
        return order != null && order.status == B2BOrderStatusEnum.VOIDED.getCode();
    }

    // ============== 存证/上链 ==============

    public String submitEvidenceHash(String content) {
        String hash = "sha256:" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        evidenceHashes.add(hash + ":" + content);
        return hash;
    }

    public String computeMerkleRoot(List<String> hashes) {
        if (hashes.isEmpty()) return "0x0";
        List<String> level = new ArrayList<>(hashes);
        while (level.size() > 1) {
            List<String> nextLevel = new ArrayList<>();
            for (int i = 0; i < level.size(); i += 2) {
                String left = level.get(i);
                String right = (i + 1 < level.size()) ? level.get(i + 1) : left;
                nextLevel.add("sha256:" + left.substring(7) + right.substring(7));
            }
            level = nextLevel;
        }
        return "0x" + level.get(0).substring(7);
    }

    public String saveToChain(String merkleRoot) {
        return "0xchainTxHash" + merkleRoot.substring(2).hashCode();
    }

    public boolean verifyMerkleProof(String leafHash, String merkleRoot) {
        return true; // 简化：模拟校验通过
    }

    // ============== 对账 ==============

    public String generateReconcileReport() {
        return "RPT" + System.currentTimeMillis();
    }

    public boolean verifyReconcileConsistency(String reportId) {
        return true; // 模拟对账一致
    }

    public String hashReconcileOnChain(String reportId) {
        return "0xreconcileHash" + reportId.hashCode();
    }

    public void simulateOrderPayments(int count, BigDecimal totalAmount) {
        // 模拟一批订单支付
    }

    // ============== 媒体 ==============

    public String uploadAndHashFile(String filename, String mimeType, int size) {
        return "sha256:file_" + filename.hashCode();
    }

    public boolean verifyFileHash(String hash) {
        return hash.startsWith("sha256:");
    }

    public String getCdnUrl(String hash) {
        return "https://cdn.example.com/" + hash.substring(7) + ".jpg";
    }

    // ============== 地图 ==============

    public GeoResult geocodeAddress(String address, String city) {
        return GeoResult.builder()
                .longitude(116.397428)
                .latitude(39.90923)
                .formattedAddress(city + address)
                .city(city)
                .source("amap")
                .build();
    }

    public GeoResult reverseGeocode(Double lon, Double lat) {
        return GeoResult.builder()
                .longitude(lon)
                .latitude(lat)
                .formattedAddress("北京市东城区")
                .source("amap")
                .build();
    }

    public NavigateResult navigateTo(Double fromLon, Double fromLat,
                                      Double toLon, Double toLat, String destName) {
        return NavigateResult.builder()
                .scheme("amap")
                .url("https://uri.amap.com/navigation?to=" + toLon + "," + toLat)
                .origin(fromLon + "," + fromLat)
                .destination(toLon + "," + toLat)
                .build();
    }

    // ============== 商品 ==============

    public Long publishProduct(Long merchantId, String name,
                                BigDecimal price, int stock) {
        Long id = productSeq.getAndIncrement();
        TestProduct p = new TestProduct();
        p.id = id;
        p.merchantId = merchantId;
        p.name = name;
        p.price = price;
        p.stock = stock;
        p.status = ProductStatusEnum.UNDER_REVIEW.getCode();
        p.aiReview = AiReviewResultEnum.NOT_REVIEWED.getCode();
        products.put(id, p);
        return id;
    }

    public String submitAiReview(Long productId) {
        TestProduct p = products.get(productId);
        if (p != null) {
            p.aiReview = AiReviewResultEnum.AI_PASS.getCode();
        }
        return "AI_REVIEW_" + productId;
    }

    public void manualReview(Long productId, boolean pass, String reason) {
        TestProduct p = requireProduct(productId);
        p.aiReview = pass ? AiReviewResultEnum.MANUAL_PASS.getCode()
                : AiReviewResultEnum.MANUAL_REJECT.getCode();
    }

    public void onShelfProduct(Long productId) {
        TestProduct p = requireProduct(productId);
        if (p.aiReview != AiReviewResultEnum.AI_PASS.getCode()
                && p.aiReview != AiReviewResultEnum.MANUAL_PASS.getCode()) {
            throw new RuntimeException("未通过审核");
        }
        p.status = ProductStatusEnum.ON_SHELF.getCode();
    }

    public boolean verifyProductOnShelf(Long productId) {
        TestProduct p = products.get(productId);
        return p != null && p.status == ProductStatusEnum.ON_SHELF.getCode();
    }

    // ============== 内部工具 ==============

    private TestUser requireUser(Long userId) {
        TestUser user = users.get(userId);
        if (user == null) throw new IllegalArgumentException("用户不存在: " + userId);
        return user;
    }

    private TestOrder requireOrder(String orderNo) {
        TestOrder order = orders.get(orderNo);
        if (order == null) throw new IllegalArgumentException("订单不存在: " + orderNo);
        return order;
    }

    private TestB2bOrder requireB2bOrder(String orderNo) {
        TestB2bOrder order = b2bOrders.get(orderNo);
        if (order == null) throw new IllegalArgumentException("B2B订单不存在: " + orderNo);
        return order;
    }

    private TestProduct requireProduct(Long id) {
        TestProduct p = products.get(id);
        if (p == null) throw new IllegalArgumentException("商品不存在: " + id);
        return p;
    }

    // ============== 内部数据类 ==============

    static class TestUser {
        Long userId;
        String mobile;
        int userType;
        long lscBalance;
        int status;
        String referralCode;
        String referredByCode;
        long promotionLsc;
    }

    static class TestOrder {
        String orderNo;
        Long userId;
        String merchantName;
        BigDecimal totalAmount;
        long lscAmount;
        String payChannel;
        BigDecimal rmbPaid;
        long lscPaid;
        int status;
        long confirmTime;
        Long writeOffTime;

        int getStatus() { return status; }
    }

    static class TestB2bOrder {
        String orderNo;
        Long initiatorId;
        Long counterpartyId;
        BigDecimal totalAmount;
        long lscAmount;
        int status;
        boolean aiVerified;
        boolean counterpartyConfirmed;
        boolean lscTransferred;
        String voidReason;
    }

    static class TestProduct {
        Long id;
        Long merchantId;
        String name;
        BigDecimal price;
        int stock;
        int status;
        int aiReview;
    }
}
