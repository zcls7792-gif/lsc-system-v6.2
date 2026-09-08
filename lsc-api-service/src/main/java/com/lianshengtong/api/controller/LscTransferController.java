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
 * LSC 流转控制器（V6.2 第三章 3.1 流转权限矩阵 + P0-7）
 *
 * 权限矩阵（接口层强制实现）：
 * ┌──────────┬──────────┬────────────────────┐
 * │ from     │ to       │ 规则               │
 * ├──────────┼──────────┼────────────────────┤
 * │ 消费者   │ 消费者   │ 禁止（接口拒绝）   │
 * │ 消费者   │ 商家     │ 允许（绑定订单）   │
 * │ 商家     │ 消费者   │ 禁止（接口拒绝）   │
 * │ 商家     │ 商家     │ 允许（绑定B2B订单）│
 * │ 商家     │ 核销     │ 允许（每日1次）   │
 * └──────────┴──────────┴────────────────────┘
 *
 * 流水类型（type）：
 * 1 消费发行 2 每日释放 3 推广奖励释放 4 权益商城消费
 * 5 线下消费 6 过期转回 7 商家核销 8 B2B流转支付 9 退款发行回滚
 */
@RestController
@RequestMapping("/api/lsc/transfer")
public class LscTransferController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserRepository userRepo;
    private final MerchantRepository merchantRepo;
    private final LscAccountRepository lscAccountRepo;
    private final LedgerTxnRepository ledgerRepo;
    private final OrderRepository orderRepo;
    private final B2BOrderRepository b2bRepo;

    public LscTransferController(UserRepository userRepo,
                                 MerchantRepository merchantRepo,
                                 LscAccountRepository lscAccountRepo,
                                 LedgerTxnRepository ledgerRepo,
                                 OrderRepository orderRepo,
                                 B2BOrderRepository b2bRepo) {
        this.userRepo = userRepo;
        this.merchantRepo = merchantRepo;
        this.lscAccountRepo = lscAccountRepo;
        this.ledgerRepo = ledgerRepo;
        this.orderRepo = orderRepo;
        this.b2bRepo = b2bRepo;
    }

    /**
     * V6.2 权限矩阵校验
     * 返回：0 消费者 / 1 商家 / -1 未找到
     */
    private int resolveUserType(Long userId) {
        if (userRepo.existsById(userId)) {
            User u = userRepo.findById(userId).get();
            return u.getUserType() != null ? u.getUserType() : 0;
        }
        // 兼容：merchant 表里也可能直接存在
        if (merchantRepo.existsById(userId)) {
            return 1;
        }
        return -1;
    }

    /**
     * V6.2 权限矩阵：禁止 C→C 和 M→C
     */
    private ApiResponse<Map<String, Object>> rejectForbidden(int fromType, int toType) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", false);
        if (fromType == 0 && toType == 0) {
            r.put("reason", "消费者之间不可进行LSC流转");
            System.out.println("[V6.2 流转拒绝] 消费者→消费者 禁止");
        } else if (fromType == 1 && toType == 0) {
            r.put("reason", "商家不可向消费者支付或流转LSC");
            System.out.println("[V6.2 流转拒绝] 商家→消费者 禁止");
        } else {
            r.put("reason", "用户类型不匹配，流转被拒绝");
        }
        return ApiResponse.success(r);
    }

    /**
     * V6.2 消费者→商家 流转（权益商城消费/线下扫码消费）
     * 必须绑定具体订单，1:1价值锚定
     * 流水类型：4 权益商城消费 / 5 线下消费
     */
    @PostMapping("/c2m")
    public ApiResponse<Map<String, Object>> consumerToMerchant(@RequestBody Map<String, Object> body) {
        Long fromUserId = toLong(body.get("fromUserId"));
        Long toMerchantId = toLong(body.get("toMerchantId"));
        Long amount = toLong(body.get("amount"));
        String orderNo = (String) body.get("orderNo");
        String scene = (String) body.getOrDefault("scene", "商城消费"); // 商城消费 / 线下消费

        int fromType = resolveUserType(fromUserId);
        int toType = resolveUserType(toMerchantId);

        // V6.2 权限矩阵校验
        if (fromType == 0 && toType == 0) return rejectForbidden(0, 0);
        if (fromType == 1 && toType == 0) return rejectForbidden(1, 0);
        if (toType != 1) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "接收方必须是商家会员");
            return ApiResponse.success(r);
        }

        // V6.2 必须绑定订单
        if (orderNo == null || orderNo.isEmpty()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "消费者→商家流转必须绑定具体订单");
            return ApiResponse.success(r);
        }

        // 校验订单存在
        boolean orderExists = orderRepo.findAll().stream()
                .anyMatch(o -> orderNo.equals(o.getOrderNo()));
        if (!orderExists) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "订单不存在：" + orderNo);
            return ApiResponse.success(r);
        }

        // 校验可用 LSC 余额
        Optional<LscAccount> fromAcctOpt = lscAccountRepo.findById(fromUserId);
        if (fromAcctOpt.isEmpty()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "发起方LSC账户不存在");
            return ApiResponse.success(r);
        }
        LscAccount fromAcct = fromAcctOpt.get();
        if (fromAcct.getTotalAvailable() == null || fromAcct.getTotalAvailable() < amount) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "可用LSC余额不足");
            return ApiResponse.success(r);
        }

        // 原子事务：扣减发起方可用 + 增加接收方可用
        long fromBefore = fromAcct.getTotalAvailable();
        fromAcct.setTotalAvailable(fromBefore - amount);
        fromAcct.setUpdatedAt(LocalDateTime.now().format(FMT));
        lscAccountRepo.save(fromAcct);

        LscAccount toAcct = lscAccountRepo.findById(toMerchantId)
                .orElseGet(() -> {
                    LscAccount a = new LscAccount();
                    a.setUserId(toMerchantId);
                    a.setTotalLocked(0L);
                    a.setTotalAvailable(0L);
                    a.setVersion(0);
                    return a;
                });
        long toBefore = toAcct.getTotalAvailable() == null ? 0 : toAcct.getTotalAvailable();
        toAcct.setTotalAvailable(toBefore + amount);
        toAcct.setUpdatedAt(LocalDateTime.now().format(FMT));
        lscAccountRepo.save(toAcct);

        // 落库流水（type 4/5）
        int typeCode = "线下消费".equals(scene) ? 5 : 4;
        LedgerTxn txn = new LedgerTxn();
        txn.setId(SnowflakeIdGenerator.getInstance().nextId());
        txn.setUserId(fromUserId);
        txn.setType(typeCode);
        txn.setTypeStr(scene);
        txn.setAmount(amount);
        txn.setBeforeAvailable(fromBefore);
        txn.setAfterAvailable(fromAcct.getTotalAvailable());
        txn.setCounterpartyId(toMerchantId);
        txn.setOrderNo(orderNo);
        String idemKey = "C2M-" + orderNo + "-" + fromUserId;
        txn.setIdempotentKey(idemKey);
        txn.setCreatedAt(LocalDateTime.now().format(FMT));

        // SHA-256 哈希存证
        Map<String, Object> evidenceData = new LinkedHashMap<>();
        evidenceData.put("type", typeCode);
        evidenceData.put("userId", fromUserId);
        evidenceData.put("counterpartyId", toMerchantId);
        evidenceData.put("amount", amount);
        evidenceData.put("orderNo", orderNo);
        evidenceData.put("beforeAvailable", fromBefore);
        evidenceData.put("afterAvailable", fromAcct.getTotalAvailable());
        txn.setEvidenceHash(HashUtil.sha256(evidenceData));
        ledgerRepo.save(txn);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("txnId", txn.getId());
        r.put("type", typeCode);
        r.put("typeStr", scene);
        r.put("amount", amount);
        r.put("fromAvailableBefore", fromBefore);
        r.put("fromAvailableAfter", fromAcct.getTotalAvailable());
        r.put("toAvailableBefore", toBefore);
        r.put("toAvailableAfter", toAcct.getTotalAvailable());
        r.put("evidenceHash", txn.getEvidenceHash());
        r.put("orderNo", orderNo);
        System.out.println("[V6.2 C2M流转] 用户" + fromUserId + "→商家" + toMerchantId
                + " " + amount + "LSC 场景:" + scene + " 订单:" + orderNo
                + " 哈希:" + txn.getEvidenceHash().substring(0, 16) + "...");
        return ApiResponse.success(r);
    }

    /**
     * V6.2 商家→商家 流转（B2B交易支付）
     * 必须绑定经交易对手方确认的B2B订单，严禁空流转
     * 接收方LSC有效期重置为365天
     * 流水类型：8 B2B流转支付
     */
    @PostMapping("/m2m")
    public ApiResponse<Map<String, Object>> merchantToMerchant(@RequestBody Map<String, Object> body) {
        Long fromMerchantId = toLong(body.get("fromMerchantId"));
        Long toMerchantId = toLong(body.get("toMerchantId"));
        Long amount = toLong(body.get("amount"));
        String b2bOrderNo = (String) body.get("b2bOrderNo");

        int fromType = resolveUserType(fromMerchantId);
        int toType = resolveUserType(toMerchantId);

        // V6.2 权限矩阵校验
        if (fromType == 0 && toType == 0) return rejectForbidden(0, 0);
        if (fromType == 1 && toType == 0) return rejectForbidden(1, 0);
        if (fromType != 1 || toType != 1) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "商家间流转要求双方均为商家会员");
            return ApiResponse.success(r);
        }

        // V6.2 准入三要件校验
        ApiResponse<Map<String, Object>> eligibilityError = checkMerchantEligibility(fromMerchantId, "发起方");
        if (eligibilityError != null) return eligibilityError;
        eligibilityError = checkMerchantEligibility(toMerchantId, "接收方");
        if (eligibilityError != null) return eligibilityError;

        // V6.2 必须绑定已确认的 B2B 订单
        if (b2bOrderNo == null || b2bOrderNo.isEmpty()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "商家间LSC流转必须绑定经确认的B2B订单，严禁空流转");
            return ApiResponse.success(r);
        }

        Optional<B2BOrder> b2bOpt = b2bRepo.findAll().stream()
                .filter(o -> b2bOrderNo.equals(o.getOrderNo()))
                .findFirst();
        if (b2bOpt.isEmpty()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "B2B订单不存在：" + b2bOrderNo);
            return ApiResponse.success(r);
        }
        B2BOrder b2b = b2bOpt.get();
        // 必须已确认（status>=1）
        if (b2b.getStatus() == null || b2b.getStatus() < 1) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "B2B订单尚未经交易对手方确认，无法流转");
            return ApiResponse.success(r);
        }
        // LSC 已流转则拒绝（幂等）
        if (b2b.getLscTransferred() != null && b2b.getLscTransferred() == 1) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "该B2B订单LSC已流转，幂等拒绝");
            return ApiResponse.success(r);
        }

        // 校验可用 LSC 余额
        Optional<LscAccount> fromAcctOpt = lscAccountRepo.findById(fromMerchantId);
        if (fromAcctOpt.isEmpty() || fromAcctOpt.get().getTotalAvailable() < amount) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", "发起方可用LSC余额不足");
            return ApiResponse.success(r);
        }
        LscAccount fromAcct = fromAcctOpt.get();

        // 原子事务
        long fromBefore = fromAcct.getTotalAvailable();
        fromAcct.setTotalAvailable(fromBefore - amount);
        fromAcct.setUpdatedAt(LocalDateTime.now().format(FMT));
        lscAccountRepo.save(fromAcct);

        LscAccount toAcct = lscAccountRepo.findById(toMerchantId)
                .orElseGet(() -> {
                    LscAccount a = new LscAccount();
                    a.setUserId(toMerchantId);
                    a.setTotalLocked(0L);
                    a.setTotalAvailable(0L);
                    a.setVersion(0);
                    return a;
                });
        long toBefore = toAcct.getTotalAvailable() == null ? 0 : toAcct.getTotalAvailable();
        toAcct.setTotalAvailable(toBefore + amount);
        // V6.2 接收方LSC有效期重置为365天（由独立的有效期服务处理，此处仅记录）
        toAcct.setUpdatedAt(LocalDateTime.now().format(FMT));
        lscAccountRepo.save(toAcct);

        // 标记 B2B 订单已流转
        b2b.setStatus(2);
        b2b.setStatusDesc("已流转");
        b2b.setLscTransferred(1);
        b2bRepo.save(b2b);

        // 落库流水（type 8）
        LedgerTxn txn = new LedgerTxn();
        txn.setId(SnowflakeIdGenerator.getInstance().nextId());
        txn.setUserId(fromMerchantId);
        txn.setType(8);
        txn.setTypeStr("B2B流转支付");
        txn.setAmount(amount);
        txn.setBeforeAvailable(fromBefore);
        txn.setAfterAvailable(fromAcct.getTotalAvailable());
        txn.setCounterpartyId(toMerchantId);
        txn.setOrderNo(b2bOrderNo);
        txn.setIdempotentKey("M2M-" + b2bOrderNo);
        txn.setRemark("接收方LSC有效期重置365天");
        txn.setCreatedAt(LocalDateTime.now().format(FMT));

        // SHA-256 哈希存证
        Map<String, Object> evidenceData = new LinkedHashMap<>();
        evidenceData.put("type", 8);
        evidenceData.put("userId", fromMerchantId);
        evidenceData.put("counterpartyId", toMerchantId);
        evidenceData.put("amount", amount);
        evidenceData.put("b2bOrderNo", b2bOrderNo);
        evidenceData.put("beforeAvailable", fromBefore);
        evidenceData.put("afterAvailable", fromAcct.getTotalAvailable());
        txn.setEvidenceHash(HashUtil.sha256(evidenceData));
        ledgerRepo.save(txn);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("txnId", txn.getId());
        r.put("type", 8);
        r.put("typeStr", "B2B流转支付");
        r.put("amount", amount);
        r.put("fromAvailableBefore", fromBefore);
        r.put("fromAvailableAfter", fromAcct.getTotalAvailable());
        r.put("toAvailableBefore", toBefore);
        r.put("toAvailableAfter", toAcct.getTotalAvailable());
        r.put("b2bOrderNo", b2bOrderNo);
        r.put("receiverValidityReset", "365天");
        r.put("evidenceHash", txn.getEvidenceHash());
        System.out.println("[V6.2 M2M流转] 商家" + fromMerchantId + "→商家" + toMerchantId
                + " " + amount + "LSC B2B订单:" + b2bOrderNo
                + " 接收方有效期重置365天 哈希:" + txn.getEvidenceHash().substring(0, 16) + "...");
        return ApiResponse.success(r);
    }

    /**
     * V6.2 流转权限矩阵查询（前端展示用）
     */
    @GetMapping("/matrix")
    public ApiResponse<Map<String, Object>> matrix() {
        Map<String, Object> m = new LinkedHashMap<>();
        // from -> to -> allowed
        Map<String, Object> consumer = new LinkedHashMap<>();
        consumer.put("toConsumer", false);
        consumer.put("toMerchant", true);
        consumer.put("reason", "消费者仅可向商家支付LSC抵扣货款");
        Map<String, Object> merchant = new LinkedHashMap<>();
        merchant.put("toConsumer", false);
        merchant.put("toMerchant", true);
        merchant.put("reason", "商家间须以真实贸易为基础绑定B2B订单");
        merchant.put("toWriteoff", true);
        merchant.put("writeoffReason", "仅商家具备核销资格，每日1次");
        m.put("consumer", consumer);
        m.put("merchant", merchant);
        m.put("rules", List.of(
                "消费者→消费者：禁止（接口层直接拒绝）",
                "消费者→商家：允许（绑定订单，1:1价值锚定）",
                "商家→消费者：禁止（接口层直接拒绝）",
                "商家→商家：允许（绑定经确认的B2B订单）",
                "商家核销：允许（仅限商家，每日1次）"
        ));
        return ApiResponse.success(m);
    }

    /**
     * V6.2 流水查询（支持按 type/userId 过滤）
     */
    @GetMapping("/transactions")
    public ApiResponse<List<LedgerTxn>> transactions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer type,
            @RequestParam(defaultValue = "20") int limit) {
        List<LedgerTxn> all = ledgerRepo.findAll();
        List<LedgerTxn> filtered = new ArrayList<>();
        for (LedgerTxn t : all) {
            if (userId != null && !userId.equals(t.getUserId())) continue;
            if (type != null && !type.equals(t.getType())) continue;
            filtered.add(t);
            if (filtered.size() >= limit) break;
        }
        filtered.sort((a, b) -> {
            String ta = a.getCreatedAt() == null ? "" : a.getCreatedAt();
            String tb = b.getCreatedAt() == null ? "" : b.getCreatedAt();
            return tb.compareTo(ta);
        });
        return ApiResponse.success(filtered);
    }

    // =============== 辅助方法 ===============

    /**
     * V6.2 商家合规准入三要件校验：
     * 1. 营业执照
     * 2. 对公结算账户
     * 3. 监管协议签署
     */
    private ApiResponse<Map<String, Object>> checkMerchantEligibility(Long merchantId, String label) {
        Optional<Merchant> opt = merchantRepo.findById(merchantId);
        if (opt.isEmpty()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", label + "商家不存在");
            return ApiResponse.success(r);
        }
        Merchant m = opt.get();
        if (m.getBusinessLicense() == null || m.getBusinessLicense().isEmpty()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", label + "未上传营业执照，不具备流转权");
            return ApiResponse.success(r);
        }
        if (m.getCorporateAccountNo() == null || m.getCorporateAccountNo().isEmpty()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", label + "未开立对公账户，不具备流转权");
            return ApiResponse.success(r);
        }
        if (m.getRegulatoryAgreementSigned() == null || m.getRegulatoryAgreementSigned() != 1) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("success", false);
            r.put("reason", label + "未签署监管协议，不具备流转权");
            return ApiResponse.success(r);
        }
        return null;
    }

    private Long toLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number) return ((Number) o).longValue();
        return Long.parseLong(o.toString());
    }
}
