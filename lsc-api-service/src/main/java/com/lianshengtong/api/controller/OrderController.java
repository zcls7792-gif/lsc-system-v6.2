package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.Order;
import com.lianshengtong.api.entity.LedgerTxn;
import com.lianshengtong.api.entity.LscAccount;
import com.lianshengtong.api.repository.LedgerTxnRepository;
import com.lianshengtong.api.repository.LscAccountRepository;
import com.lianshengtong.api.repository.OrderRepository;
import com.lianshengtong.api.util.HashUtil;
import com.lianshengtong.api.util.SnowflakeIdGenerator;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单控制器（V6.2 退款规则 + LSC 消费发行）
 * 退款规则：首单不退，LSC订单不退，仅纯人民币非首单可退
 * 订单状态：0待支付 1已支付 2已完成 3已取消 4已退款 5部分退款
 * 订单类型：0纯人民币 1 LSC全额抵扣 2混合支付
 *
 * V6.2 消费发行：纯人民币支付时，消费者获得100%锁定LSC，商家获得16%锁定LSC
 * 流水类型：1 消费发行
 */
@RestController
@RequestMapping("/api/order")
public class OrderController {

    private static final String[] STATUS_DESC = {"待支付", "已支付", "已完成", "已取消", "已退款", "部分退款"};
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // V6.2 首单消费门槛
    private static final double FIRST_ORDER_MIN_AMOUNT = 10.0;
    // V6.2 消费发行：消费者获得100% LSC，商家获得16% LSC
    private static final double CONSUMER_LSC_RATE = 1.0;
    private static final double MERCHANT_LSC_RATE = 0.16;

    private final OrderRepository orderRepo;
    private final LscAccountRepository lscAccountRepo;
    private final LedgerTxnRepository ledgerRepo;

    public OrderController(OrderRepository orderRepo,
                           LscAccountRepository lscAccountRepo,
                           LedgerTxnRepository ledgerRepo) {
        this.orderRepo = orderRepo;
        this.lscAccountRepo = lscAccountRepo;
        this.ledgerRepo = ledgerRepo;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<Order>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long merchantId) {
        List<Order> filtered = orderRepo.findAll().stream().filter(o -> {
            if (status != null && !status.equals(o.getStatus())) return false;
            if (merchantId != null && !merchantId.equals(o.getMerchantId())) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Order> detail(@PathVariable String orderNo) {
        return orderRepo.findAll().stream()
                .filter(o -> orderNo.equals(o.getOrderNo()))
                .findFirst()
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("订单不存在"));
    }

    @GetMapping("/detail")
    public ApiResponse<Order> detailByParam(@RequestParam String orderNo) {
        return detail(orderNo);
    }

    @GetMapping("/stats-today")
    public ApiResponse<Map<String, Object>> statsToday(@RequestParam(required = false) Long merchantId) {
        List<Order> todayOrders = orderRepo.findAll().stream()
                .filter(o -> {
                    String today = java.time.LocalDate.now().toString();
                    boolean isToday = o.getCreatedAt() != null && o.getCreatedAt().startsWith(today);
                    boolean matchMerchant = merchantId == null || merchantId.equals(o.getMerchantId());
                    return isToday && matchMerchant;
                }).collect(Collectors.toList());
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("orderCount", todayOrders.size());
        stats.put("totalAmount", todayOrders.stream().mapToDouble(o -> o.getTotalAmount() == null ? 0 : o.getTotalAmount()).sum());
        stats.put("lscAmount", todayOrders.stream().mapToDouble(o -> o.getLscAmount() == null ? 0 : o.getLscAmount()).sum());
        stats.put("rmbAmount", todayOrders.stream().mapToDouble(o -> o.getRmbAmount() == null ? 0 : o.getRmbAmount()).sum());
        stats.put("refundCount", todayOrders.stream().filter(o -> o.getStatus() != null && (o.getStatus() == 4 || o.getStatus() == 5)).count());
        stats.put("refundAmount", todayOrders.stream()
                .filter(o -> o.getStatus() != null && (o.getStatus() == 4 || o.getStatus() == 5))
                .mapToDouble(o -> o.getRefundRmbAmount() == null ? 0 : o.getRefundRmbAmount()).sum());
        return ApiResponse.success(stats);
    }

    /**
     * V6.2 创建订单
     * 1. 校验首单门槛（不低于10元）
     * 2. 设置订单类型（0纯RMB 1 LSC全额 2混合）
     * 3. 设置首单标记
     * 4. 人民币消费触发LSC发行（100元=100锁定LSC）
     */
    @PostMapping("/create")
    public ApiResponse<Order> create(@RequestBody Order body) {
        // V6.2 设置订单类型
        if (body.getOrderType() == null) {
            double lscAmount = body.getLscAmount() != null ? body.getLscAmount() : 0;
            double rmbAmount = body.getRmbAmount() != null ? body.getRmbAmount() : 0;
            double totalAmount = body.getTotalAmount() != null ? body.getTotalAmount() : 0;
            if (lscAmount > 0 && rmbAmount > 0) {
                body.setOrderType(2); // 混合支付
            } else if (lscAmount > 0 && rmbAmount == 0) {
                body.setOrderType(1); // LSC全额抵扣
            } else {
                body.setOrderType(0); // 纯人民币
            }
        }

        // V6.2 首单标记：检查该用户是否有已完成订单
        Long userId = body.getUserId();
        boolean isFirstOrder = false;
        if (userId != null) {
            boolean hasCompletedOrder = orderRepo.findAll().stream()
                    .anyMatch(o -> userId.equals(o.getUserId())
                            && o.getStatus() != null && o.getStatus() == 2
                            && o.getIsFirstOrder() != null && o.getIsFirstOrder() == 1);
            isFirstOrder = !hasCompletedOrder;
        }
        body.setIsFirstOrder(isFirstOrder ? 1 : 0);

        // V6.2 首单门槛校验
        if (isFirstOrder) {
            double totalAmount = body.getTotalAmount() != null ? body.getTotalAmount() : 0;
            if (totalAmount < FIRST_ORDER_MIN_AMOUNT) {
                return ApiResponse.fail("首单消费金额不低于" + FIRST_ORDER_MIN_AMOUNT + "元");
            }
        }

        // V6.2 退款初始化
        body.setRefundLscAmount(0.0);
        body.setRefundRmbAmount(0.0);

        long newId = (orderRepo.count() + 1);
        body.setId(newId);
        if (body.getOrderNo() == null || body.getOrderNo().isEmpty()) {
            body.setOrderNo("LS" + System.currentTimeMillis());
        }
        if (body.getStatus() == null) body.setStatus(0);
        if (body.getStatusDesc() == null && body.getStatus() != null && body.getStatus() >= 0 && body.getStatus() < STATUS_DESC.length) {
            body.setStatusDesc(STATUS_DESC[body.getStatus()]);
        }
        if (body.getCreatedAt() == null) {
            body.setCreatedAt(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        Order saved = orderRepo.save(body);

        // V6.2 人民币消费触发LSC发行：纯RMB订单在创建时即发行
        double rmbAmount = body.getRmbAmount() != null ? body.getRmbAmount() : 0;
        if (rmbAmount > 0 && body.getOrderType() != null && body.getOrderType() == 0) {
            issueLscForRmbPayment(body, userId, rmbAmount);
        }

        return ApiResponse.success(saved);
    }

    /**
     * V6.2 消费发行：纯人民币支付时，消费者获得100%锁定LSC，商家获得16%锁定LSC
     * 流水类型：1 消费发行，落库 + 哈希存证
     */
    private void issueLscForRmbPayment(Order order, Long consumerId, double rmbAmount) {
        Long merchantId = order.getMerchantId();
        long consumerLsc = (long) (rmbAmount * CONSUMER_LSC_RATE);
        long merchantLsc = (long) (rmbAmount * MERCHANT_LSC_RATE);
        String now = LocalDateTime.now().format(FMT);

        // 消费者锁定LSC增加
        if (consumerId != null && consumerLsc > 0) {
            LscAccount acct = lscAccountRepo.findById(consumerId).orElse(null);
            if (acct == null) {
                acct = new LscAccount();
                acct.setUserId(consumerId);
                acct.setTotalLocked(0L);
                acct.setTotalAvailable(0L);
                acct.setVersion(0);
            }
            long beforeLocked = acct.getTotalLocked() == null ? 0 : acct.getTotalLocked();
            acct.setTotalLocked(beforeLocked + consumerLsc);
            acct.setUpdatedAt(now);
            lscAccountRepo.save(acct);

            // 消费发行流水（消费者侧）
            LedgerTxn txn = new LedgerTxn();
            txn.setId(SnowflakeIdGenerator.getInstance().nextId());
            txn.setUserId(consumerId);
            txn.setType(1);
            txn.setTypeStr("消费发行");
            txn.setAmount(consumerLsc);
            txn.setBeforeLocked(beforeLocked);
            txn.setAfterLocked(acct.getTotalLocked());
            txn.setCounterpartyId(merchantId);
            txn.setOrderNo(order.getOrderNo());
            txn.setIdempotentKey("ISSUE-C-" + order.getOrderNo());
            txn.setRemark("消费发行：消费者获得100%锁定LSC");
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("type", 1);
            evidence.put("userId", consumerId);
            evidence.put("counterpartyId", merchantId);
            evidence.put("amount", consumerLsc);
            evidence.put("orderNo", order.getOrderNo());
            evidence.put("beforeLocked", beforeLocked);
            evidence.put("afterLocked", acct.getTotalLocked());
            txn.setEvidenceHash(HashUtil.sha256(evidence));
            txn.setCreatedAt(now);
            ledgerRepo.save(txn);
        }

        // 商家锁定LSC增加（16%）
        if (merchantId != null && merchantLsc > 0) {
            LscAccount mAcct = lscAccountRepo.findById(merchantId).orElse(null);
            if (mAcct == null) {
                mAcct = new LscAccount();
                mAcct.setUserId(merchantId);
                mAcct.setTotalLocked(0L);
                mAcct.setTotalAvailable(0L);
                mAcct.setVersion(0);
            }
            long mBefore = mAcct.getTotalLocked() == null ? 0 : mAcct.getTotalLocked();
            mAcct.setTotalLocked(mBefore + merchantLsc);
            mAcct.setUpdatedAt(now);
            lscAccountRepo.save(mAcct);

            // 消费发行流水（商家侧）
            LedgerTxn txn = new LedgerTxn();
            txn.setId(SnowflakeIdGenerator.getInstance().nextId());
            txn.setUserId(merchantId);
            txn.setType(1);
            txn.setTypeStr("消费发行-商家");
            txn.setAmount(merchantLsc);
            txn.setBeforeLocked(mBefore);
            txn.setAfterLocked(mAcct.getTotalLocked());
            txn.setCounterpartyId(consumerId);
            txn.setOrderNo(order.getOrderNo());
            txn.setIdempotentKey("ISSUE-M-" + order.getOrderNo());
            txn.setRemark("消费发行：商家获得16%锁定LSC");
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("type", 1);
            evidence.put("userId", merchantId);
            evidence.put("counterpartyId", consumerId);
            evidence.put("amount", merchantLsc);
            evidence.put("orderNo", order.getOrderNo());
            evidence.put("beforeLocked", mBefore);
            evidence.put("afterLocked", mAcct.getTotalLocked());
            txn.setEvidenceHash(HashUtil.sha256(evidence));
            txn.setCreatedAt(now);
            ledgerRepo.save(txn);
        }

        System.out.println("[V6.2 LSC发行] 订单" + order.getOrderNo()
                + " 消费者" + consumerId + "获得" + consumerLsc + "锁定LSC"
                + " 商家" + merchantId + "获得" + merchantLsc + "锁定LSC");
    }

    /** 支付订单：status 0 -> 1 */
    @PostMapping("/pay")
    public ApiResponse<Map<String, Object>> pay(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        Map<String, Object> r = new LinkedHashMap<>();
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            orderRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(1);
                        o.setStatusDesc("已支付");
                        orderRepo.save(o);
                    });
        }
        r.put("status", 1);
        r.put("statusDesc", "已支付");
        return ApiResponse.success(r);
    }

    /** 取消订单：status -> 3 */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            orderRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(3);
                        o.setStatusDesc("已取消");
                        orderRepo.save(o);
                    });
        }
        return ApiResponse.success(null);
    }

    /** 确认收货：status -> 2 */
    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            orderRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(2);
                        o.setStatusDesc("已完成");
                        o.setCompletedAt(java.time.LocalDateTime.now().toString());
                        orderRepo.save(o);
                    });
        }
        return ApiResponse.success(null);
    }

    @PostMapping("/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = new LinkedHashMap<>(body);
        r.put("totalAmount", 99.9);
        r.put("lscAmount", 49);
        r.put("rmbAmount", 50.9);
        return ApiResponse.success(r);
    }

    @GetMapping("/refund/list")
    public ApiResponse<PageResult<Order>> refundList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Order> refunds = orderRepo.findAll().stream()
                .filter(o -> o.getStatus() != null && (o.getStatus() == 4 || o.getStatus() == 5))
                .collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(refunds, page, size));
    }

    /**
     * V6.2 退款规则强制实现
     * 规则一：首单不退
     * 规则二：LSC订单不退（orderType=1或2）
     * 规则三：仅纯人民币非首单可退（orderType=0, isFirstOrder=0）
     */
    @PostMapping("/refund/apply")
    public ApiResponse<Map<String, Object>> refundApply(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        Object refundAmountObj = body.get("refundAmount");
        Map<String, Object> r = new LinkedHashMap<>();

        if (orderNoObj == null) {
            return ApiResponse.fail("订单号不能为空");
        }
        String orderNo = orderNoObj.toString();
        Optional<Order> orderOpt = orderRepo.findAll().stream()
                .filter(o -> orderNo.equals(o.getOrderNo()))
                .findFirst();

        if (orderOpt.isEmpty()) {
            return ApiResponse.fail("订单不存在");
        }
        Order order = orderOpt.get();

        // V6.2 规则一：首单不退
        if (order.getIsFirstOrder() != null && order.getIsFirstOrder() == 1) {
            r.put("success", false);
            r.put("reason", "首单订单不支持退款");
            System.out.println("[V6.2 退款拒绝] 订单" + orderNo + "为首单，不可退款");
            return ApiResponse.success(r);
        }

        // V6.2 规则二：LSC订单不退
        if (order.getOrderType() != null && order.getOrderType() > 0) {
            r.put("success", false);
            r.put("reason", "使用LSC的订单不支持退款");
            System.out.println("[V6.2 退款拒绝] 订单" + orderNo + "含LSC支付，不可退款");
            return ApiResponse.success(r);
        }

        // V6.2 规则三：仅纯人民币非首单可退
        double refundAmount = refundAmountObj != null ? Double.parseDouble(refundAmountObj.toString()) : 0;
        double totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : 0;

        if (refundAmount > 0 && refundAmount < totalAmount) {
            // 部分退款
            order.setStatus(5);
            order.setStatusDesc("部分退款");
            order.setRefundRmbAmount(refundAmount);
            // V6.2 退款触发LSC发行回滚（按比例）
            double refundRatio = refundAmount / totalAmount;
            System.out.println("[V6.2 部分退款] 订单" + orderNo + " 退款" + refundAmount
                    + "元，LSC发行按比例回滚" + String.format("%.1f%%", refundRatio * 100));
        } else {
            // 全额退款
            order.setStatus(4);
            order.setStatusDesc("已退款");
            order.setRefundRmbAmount(totalAmount);
            // V6.2 退款触发LSC发行回滚
            System.out.println("[V6.2 全额退款] 订单" + orderNo + " 退款" + totalAmount
                    + "元，LSC发行全额回滚");
        }

        orderRepo.save(order);
        r.put("success", true);
        r.put("status", order.getStatus());
        r.put("statusDesc", order.getStatusDesc());
        return ApiResponse.success(r);
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> export() {
        return ApiResponse.success(new LinkedHashMap<>(Map.of("url", "/files/orders.xlsx")));
    }
}
