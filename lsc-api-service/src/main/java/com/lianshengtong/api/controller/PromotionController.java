package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.entity.*;
import com.lianshengtong.api.repository.*;
import com.lianshengtong.api.util.HashUtil;
import com.lianshengtong.api.util.SnowflakeIdGenerator;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 推广奖励控制器（V6.2 第八章）
 *
 * 【推荐层级约束】严格仅支持一级推荐关系，无二级、无三级。
 *   被推荐人仅触发其直接推荐人（referrer_id，唯一）的首单奖励；
 *   不向上递归追溯父级推荐人，不向下分发到下下级。
 *
 * 规则要点：
 * 8.1 首单定义：用户实名认证后第一笔有效消费，金额不低于10元，状态为已完成，首单不退款
 * 8.2 奖励数量 = 首单消费金额 × 10%，从【直接推荐人】锁定池划转至可用池，即时到账
 *     锁定余额不足部分记入挂账表
 *     奖励从推荐人自身锁定池扣减转为可用，不产生新增发行
 * 8.3 推广奖励一旦发放，永久有效，不存在回滚场景
 * 8.4 挂账自动补发：每日定时扫描挂账表，推荐人锁定余额新增后自动补发
 *
 * 流水类型：3 推广奖励释放
 */
@RestController
@RequestMapping("/api/promotion")
public class PromotionController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final double REWARD_RATE = 0.10; // 首单消费金额 × 10%
    private static final double FIRST_ORDER_MIN_AMOUNT = 10.0; // 首单门槛
    /** 推荐关系层级上限：仅一级，禁止多级分发 */
    private static final int MAX_REFERRAL_DEPTH = 1;

    private final UserRepository userRepo;
    private final MerchantRepository merchantRepo;
    private final OrderRepository orderRepo;
    private final LscAccountRepository lscAccountRepo;
    private final LedgerTxnRepository ledgerRepo;
    private final PromotionPendingRepository pendingRepo;

    public PromotionController(UserRepository userRepo,
                               MerchantRepository merchantRepo,
                               OrderRepository orderRepo,
                               LscAccountRepository lscAccountRepo,
                               LedgerTxnRepository ledgerRepo,
                               PromotionPendingRepository pendingRepo) {
        this.userRepo = userRepo;
        this.merchantRepo = merchantRepo;
        this.orderRepo = orderRepo;
        this.lscAccountRepo = lscAccountRepo;
        this.ledgerRepo = ledgerRepo;
        this.pendingRepo = pendingRepo;
    }

    /**
     * V6.2 触发推广奖励
     * 在被推荐人首单完成后调用
     * 1. 校验首单：金额≥10元、状态=已完成、为首单
     * 2. 计算奖励 = 首单金额 × 10%
     * 3. 从推荐人锁定池扣减，划转至可用池（即时到账）
     * 4. 锁定余额不足部分记入挂账表
     */
    @PostMapping("/reward/trigger")
    public ApiResponse<Map<String, Object>> triggerReward(@RequestBody Map<String, Object> body) {
        String orderNo = (String) body.get("orderNo");
        Long userId = toLong(body.get("userId"));

        if (orderNo == null || userId == null) {
            return ApiResponse.fail("orderNo 和 userId 不能为空");
        }

        // 查订单
        Optional<Order> orderOpt = orderRepo.findAll().stream()
                .filter(o -> orderNo.equals(o.getOrderNo()))
                .findFirst();
        if (orderOpt.isEmpty()) {
            return ApiResponse.fail("订单不存在：" + orderNo);
        }
        Order order = orderOpt.get();

        // V6.2 首单校验
        if (order.getIsFirstOrder() == null || order.getIsFirstOrder() != 1) {
            return ApiResponse.fail("非首单订单，不触发推广奖励");
        }
        if (order.getStatus() == null || order.getStatus() != 2) {
            return ApiResponse.fail("订单状态未达已完成，不触发推广奖励");
        }
        double totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 0;
        if (totalAmount < FIRST_ORDER_MIN_AMOUNT) {
            return ApiResponse.fail("首单消费金额低于" + FIRST_ORDER_MIN_AMOUNT + "元，不触发推广奖励");
        }

        // 查被推荐人 user
        Optional<User> userOpt = userRepo.findById(userId);
        if (userOpt.isEmpty() || userOpt.get().getReferrerId() == null) {
            return ApiResponse.fail("用户无推荐人或用户不存在");
        }
        User user = userOpt.get();
        Long referrerId = user.getReferrerId();

        // 【一级推荐强约束】仅向直接推荐人发放，不递归向上追溯
        // 不查询 referrerId 的 referrerId，确保无二级、无三级
        if (MAX_REFERRAL_DEPTH != 1) {
            return ApiResponse.fail("系统配置异常：仅支持一级推荐");
        }

        // V6.2 奖励数量 = 首单消费金额 × 10%（向下取整）
        long rewardAmount = (long) (totalAmount * REWARD_RATE);
        if (rewardAmount <= 0) {
            return ApiResponse.fail("奖励数量为0");
        }

        // 查推荐人 LSC 账户
        LscAccount referrerAcct = lscAccountRepo.findById(referrerId).orElse(null);
        if (referrerAcct == null) {
            // 推荐人无账户 → 全额挂账
            return createPendingRecord(referrerId, userId, orderNo, rewardAmount, 0,
                    "推荐人LSC账户不存在，全额挂账");
        }

        long lockedBefore = referrerAcct.getTotalLocked() == null ? 0 : referrerAcct.getTotalLocked();
        long availableBefore = referrerAcct.getTotalAvailable() == null ? 0 : referrerAcct.getTotalAvailable();

        long actualPaid;
        long pendingAmount;

        if (lockedBefore >= rewardAmount) {
            // 锁定余额充足，全额发放
            actualPaid = rewardAmount;
            pendingAmount = 0;
        } else {
            // 锁定余额不足，部分发放 + 剩余挂账
            actualPaid = lockedBefore;
            pendingAmount = rewardAmount - lockedBefore;
        }

        // 原子事务：推荐人锁定池扣减 → 可用池增加
        if (actualPaid > 0) {
            referrerAcct.setTotalLocked(lockedBefore - actualPaid);
            referrerAcct.setTotalAvailable(availableBefore + actualPaid);
            referrerAcct.setUpdatedAt(LocalDateTime.now().format(FMT));
            lscAccountRepo.save(referrerAcct);

            // 落库流水（type 3 推广奖励释放）
            LedgerTxn txn = new LedgerTxn();
            txn.setId(SnowflakeIdGenerator.getInstance().nextId());
            txn.setUserId(referrerId);
            txn.setType(3);
            txn.setTypeStr("推广奖励释放");
            txn.setAmount(actualPaid);
            txn.setBeforeLocked(lockedBefore);
            txn.setAfterLocked(referrerAcct.getTotalLocked());
            txn.setBeforeAvailable(availableBefore);
            txn.setAfterAvailable(referrerAcct.getTotalAvailable());
            txn.setCounterpartyId(userId);
            txn.setOrderNo(orderNo);
            txn.setIdempotentKey("PROMO-" + orderNo);
            txn.setRemark("首单奖励，从锁定池划转至可用池");

            // SHA-256 哈希存证
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("type", 3);
            evidence.put("referrerId", referrerId);
            evidence.put("referredId", userId);
            evidence.put("orderNo", orderNo);
            evidence.put("rewardAmount", rewardAmount);
            evidence.put("actualPaid", actualPaid);
            txn.setEvidenceHash(HashUtil.sha256(evidence));
            txn.setCreatedAt(LocalDateTime.now().format(FMT));
            ledgerRepo.save(txn);

            System.out.println("[V6.2 推广奖励] 推荐人" + referrerId + " 收到首单奖励"
                    + actualPaid + "LSC（来自被推荐人" + userId + " 订单" + orderNo + "）"
                    + (pendingAmount > 0 ? " 挂账" + pendingAmount : " 全额到账"));
        }

        // 挂账记录
        if (pendingAmount > 0) {
            return createPendingRecord(referrerId, userId, orderNo, pendingAmount, actualPaid,
                    "锁定余额不足，差额挂账待补发");
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("referrerId", referrerId);
        r.put("referredId", userId);
        r.put("orderNo", orderNo);
        r.put("rewardAmount", rewardAmount);
        r.put("paidAmount", actualPaid);
        r.put("pendingAmount", 0L);
        r.put("status", "全额到账");
        r.put("permanent", true); // V6.2 永久有效
        return ApiResponse.success(r);
    }

    /**
     * V6.2 挂账自动补发扫描（8.4）
     * 每日定时任务调用，遍历 status=0/2 的挂账记录
     * 推荐人锁定余额新增后自动补发
     */
    @PostMapping("/pending/scan")
    public ApiResponse<Map<String, Object>> scanPending() {
        List<PromotionPending> pendingList = pendingRepo.findByStatus(0);
        pendingList.addAll(pendingRepo.findByStatus(2));

        long totalPaid = 0;
        int settledCount = 0;
        for (PromotionPending p : pendingList) {
            LscAccount acct = lscAccountRepo.findById(p.getReferrerId()).orElse(null);
            if (acct == null) continue;
            long locked = acct.getTotalLocked() == null ? 0 : acct.getTotalLocked();
            long remaining = p.getPendingAmount() - (p.getPaidAmount() == null ? 0 : p.getPaidAmount());
            if (locked <= 0 || remaining <= 0) continue;

            long payNow = Math.min(locked, remaining);
            long lockedBefore = acct.getTotalLocked();
            long availableBefore = acct.getTotalAvailable() == null ? 0 : acct.getTotalAvailable();
            acct.setTotalLocked(lockedBefore - payNow);
            acct.setTotalAvailable(availableBefore + payNow);
            acct.setUpdatedAt(LocalDateTime.now().format(FMT));
            lscAccountRepo.save(acct);

            // 补发流水
            LedgerTxn txn = new LedgerTxn();
            txn.setId(SnowflakeIdGenerator.getInstance().nextId());
            txn.setUserId(p.getReferrerId());
            txn.setType(3);
            txn.setTypeStr("推广奖励补发");
            txn.setAmount(payNow);
            txn.setBeforeLocked(lockedBefore);
            txn.setAfterAvailable(acct.getTotalAvailable());
            txn.setCounterpartyId(p.getReferredId());
            txn.setOrderNo(p.getFirstOrderNo());
            txn.setIdempotentKey("PROMO-PATCH-" + p.getId());
            txn.setRemark("挂账补发");
            txn.setCreatedAt(LocalDateTime.now().format(FMT));
            ledgerRepo.save(txn);

            // 更新挂账记录
            long newPaid = (p.getPaidAmount() == null ? 0 : p.getPaidAmount()) + payNow;
            p.setPaidAmount(newPaid);
            if (newPaid >= p.getPendingAmount()) {
                p.setStatus(1); // 已补发
                settledCount++;
            } else {
                p.setStatus(2); // 部分补发
            }
            p.setUpdatedAt(LocalDateTime.now().format(FMT));
            pendingRepo.save(p);

            totalPaid += payNow;
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("scanned", pendingList.size());
        r.put("settled", settledCount);
        r.put("totalPaid", totalPaid);
        return ApiResponse.success(r);
    }

    /**
     * V6.2 挂账列表查询
     */
    @GetMapping("/pending/list")
    public ApiResponse<List<PromotionPending>> pendingList(
            @RequestParam(required = false) Long referrerId,
            @RequestParam(required = false) Integer status) {
        List<PromotionPending> list = pendingRepo.findAll();
        List<PromotionPending> filtered = new ArrayList<>();
        for (PromotionPending p : list) {
            if (referrerId != null && !referrerId.equals(p.getReferrerId())) continue;
            if (status != null && !status.equals(p.getStatus())) continue;
            filtered.add(p);
        }
        return ApiResponse.success(filtered);
    }

    /**
     * V6.2 推广统计
     */
    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        List<User> allUsers = userRepo.findAll();
        long totalReferrals = allUsers.stream()
                .filter(u -> u.getReferrerId() != null)
                .count();
        long totalFirstOrders = allUsers.stream()
                .filter(u -> u.getFirstOrderCompleted() != null && u.getFirstOrderCompleted() == 1)
                .count();

        List<PromotionPending> pendings = pendingRepo.findAll();
        long totalPendingAmount = pendings.stream()
                .mapToLong(p -> p.getPendingAmount() - (p.getPaidAmount() == null ? 0 : p.getPaidAmount()))
                .filter(x -> x > 0)
                .sum();
        long totalPaidAmount = pendings.stream()
                .mapToLong(p -> p.getPaidAmount() == null ? 0 : p.getPaidAmount())
                .sum();

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalReferrals", totalReferrals);
        s.put("totalFirstOrders", totalFirstOrders);
        s.put("rewardRate", REWARD_RATE);
        s.put("totalPendingAmount", totalPendingAmount);
        s.put("totalPaidAmount", totalPaidAmount);
        s.put("pendingRecords", pendings.size());
        s.put("permanent", true);
        s.put("maxReferralDepth", MAX_REFERRAL_DEPTH); // 仅一级，无二级、无三级
        return ApiResponse.success(s);
    }

    // =============== 辅助 ===============

    private ApiResponse<Map<String, Object>> createPendingRecord(Long referrerId, Long userId,
                                                                   String orderNo, long pendingAmount,
                                                                   long paidAmount, String reason) {
        PromotionPending p = new PromotionPending();
        p.setId(SnowflakeIdGenerator.getInstance().nextId());
        p.setReferrerId(referrerId);
        p.setReferredId(userId);
        p.setFirstOrderNo(orderNo);
        p.setPendingAmount(pendingAmount);
        p.setPaidAmount(paidAmount);
        p.setStatus(paidAmount > 0 ? 2 : 0);
        p.setCreatedAt(LocalDateTime.now().format(FMT));
        p.setUpdatedAt(LocalDateTime.now().format(FMT));
        pendingRepo.save(p);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("referrerId", referrerId);
        r.put("referredId", userId);
        r.put("orderNo", orderNo);
        r.put("rewardAmount", pendingAmount + paidAmount);
        r.put("paidAmount", paidAmount);
        r.put("pendingAmount", pendingAmount);
        r.put("status", paidAmount > 0 ? "部分挂账" : "全额挂账");
        r.put("reason", reason);
        r.put("pendingId", p.getId());
        return ApiResponse.success(r);
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
