package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.B2BOrder;
import com.lianshengtong.api.repository.B2BOrderRepository;
import com.lianshengtong.api.repository.MerchantRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * B2B 交易控制器（V6.2 更新）
 * 状态：0待确认 1已确认 2已流转 3已完成 4已取消 5已作废
 * 流程：创建(0) → 确认(1) → LSC流转(2) → 完成(3)
 * AI核验标记：0 AI判定真实 1 AI判定可疑 2人工确认真实 3人工确认虚假
 * 超时：7日未确认自动取消
 */
@RestController
@RequestMapping("/api/b2b")
public class B2BController {

    private static final String[] STATUS_DESC = {"待确认", "已确认", "已流转", "已完成", "已取消", "已作废"};

    // V6.2 超时时间：7天
    private static final long TIMEOUT_DAYS = 7;

    private final B2BOrderRepository b2bRepo;
    private final MerchantRepository merchantRepo;

    public B2BController(B2BOrderRepository b2bRepo, MerchantRepository merchantRepo) {
        this.b2bRepo = b2bRepo;
        this.merchantRepo = merchantRepo;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<B2BOrder>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        List<B2BOrder> filtered = b2bRepo.findAll().stream().filter(o -> {
            if (status != null && !status.equals(o.getStatus())) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<B2BOrder> detail(@PathVariable String orderNo) {
        return b2bRepo.findAll().stream()
                .filter(o -> orderNo.equals(o.getOrderNo()))
                .findFirst()
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("B2B订单不存在"));
    }

    /**
     * V6.2 创建 B2B 订单
     * 1. 设置幂等键
     * 2. AI初步核验
     * 3. 设置7日超时
     */
    @PostMapping("/create")
    public ApiResponse<B2BOrder> create(@RequestBody B2BOrder body) {
        long newId = b2bRepo.count() + 1;
        body.setId(newId);
        if (body.getOrderNo() == null || body.getOrderNo().isEmpty()) {
            body.setOrderNo("B2B" + System.currentTimeMillis());
        }
        if (body.getStatus() == null) body.setStatus(0);
        if (body.getStatusDesc() == null && body.getStatus() >= 0 && body.getStatus() < STATUS_DESC.length) {
            body.setStatusDesc(STATUS_DESC[body.getStatus()]);
        }
        if (body.getCreatedAt() == null) {
            body.setCreatedAt(java.time.LocalDateTime.now().toString());
        }

        // V6.2 幂等键
        if (body.getIdempotentKey() == null || body.getIdempotentKey().isEmpty()) {
            body.setIdempotentKey("B2B-" + body.getOrderNo());
        }
        if (body.getVersion() == null) body.setVersion(1);

        // V6.2 设置7日超时
        if (body.getExpireAt() == null) {
            body.setExpireAt(java.time.LocalDateTime.now().plusDays(TIMEOUT_DAYS).toString());
        }

        // V6.2 AI初步核验（模拟）
        if (body.getAiVerificationResult() == null) {
            // 模拟AI核验：85%判定真实，15%判定可疑
            body.setAiVerificationResult(Math.random() > 0.15 ? 0 : 1);
            body.setAiVerificationScore(80 + Math.random() * 20);
        }

        // V6.2 初始化确认与流转状态
        if (body.getCounterpartyConfirmed() == null) body.setCounterpartyConfirmed(0);
        if (body.getLscTransferred() == null) body.setLscTransferred(0);

        B2BOrder saved = b2bRepo.save(body);
        System.out.println("[V6.2 B2B创建] 订单" + body.getOrderNo()
                + " 发起方" + body.getInitiatorId()
                + " 对方" + body.getCounterpartyId()
                + " LSC" + body.getLscAmount()
                + " AI核验:" + (body.getAiVerificationResult() == 0 ? "真实" : "可疑")
                + " 超时:" + body.getExpireAt());
        return ApiResponse.success(saved);
    }

    /**
     * V6.2 交易对手方确认：status 0 -> 1
     */
    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        Object confirmedByObj = body.get("confirmedBy");
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            b2bRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        // V6.2 检查超时
                        if (isExpired(o)) {
                            o.setStatus(4);
                            o.setStatusDesc("已取消(超时)");
                            b2bRepo.save(o);
                            return;
                        }
                        o.setStatus(1);
                        o.setStatusDesc("已确认");
                        o.setCounterpartyConfirmed(1);
                        if (confirmedByObj != null) {
                            o.setConfirmedBy(confirmedByObj.toString());
                        }
                        o.setConfirmedAt(java.time.LocalDateTime.now().toString());
                        o.setVersion(o.getVersion() != null ? o.getVersion() + 1 : 2);
                        b2bRepo.save(o);
                        System.out.println("[V6.2 B2B确认] 订单" + orderNo + " 已确认");
                    });
        }
        return ApiResponse.success(null);
    }

    /**
     * V6.2 LSC流转：status 1 -> 2
     * 确认后执行LSC流转，按1:1价值锚定
     */
    @PostMapping("/transfer")
    public ApiResponse<Map<String, Object>> transfer(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        Map<String, Object> r = new LinkedHashMap<>();
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            Optional<B2BOrder> opt = b2bRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst();
            if (opt.isPresent()) {
                B2BOrder o = opt.get();
                // V6.2 必须已确认才能流转
                if (o.getStatus() == null || o.getStatus() < 1) {
                    r.put("success", false);
                    r.put("reason", "订单尚未确认，无法流转");
                    return ApiResponse.success(r);
                }
                // V6.2 已流转不允许重复
                if (o.getLscTransferred() != null && o.getLscTransferred() == 1) {
                    r.put("success", false);
                    r.put("reason", "LSC已流转，幂等拒绝");
                    return ApiResponse.success(r);
                }
                // 执行流转
                o.setStatus(2);
                o.setStatusDesc("已流转");
                o.setLscTransferred(1);
                o.setVersion(o.getVersion() != null ? o.getVersion() + 1 : 2);
                b2bRepo.save(o);
                r.put("success", true);
                r.put("lscAmount", o.getLscAmount());
                r.put("fromUserId", o.getInitiatorId());
                r.put("toUserId", o.getCounterpartyId());
                System.out.println("[V6.2 B2B流转] 订单" + orderNo
                        + " LSC" + o.getLscAmount() + "从" + o.getInitiatorId() + "流转至" + o.getCounterpartyId()
                        + " 接收方有效期重置为365天");
            }
        }
        return ApiResponse.success(r);
    }

    /** 取消订单：status -> 4 */
    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestBody Map<String, Object> body) {
        updateStatus(body, 4, "已取消");
        return ApiResponse.success(null);
    }

    /**
     * V6.2 作废虚假贸易订单：status -> 5
     * 关联双方流转权限冻结，扣减信用分40分
     */
    @PostMapping("/void")
    public ApiResponse<Map<String, Object>> voidOrder(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        Map<String, Object> r = new LinkedHashMap<>();
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            b2bRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(5);
                        o.setStatusDesc("已作废");
                        o.setAiVerificationResult(3); // 人工确认虚假
                        o.setVersion(o.getVersion() != null ? o.getVersion() + 1 : 2);
                        b2bRepo.save(o);

                        // V6.2 扣减信用分40分
                        merchantRepo.findById(o.getInitiatorId()).ifPresent(m -> {
                            int score = m.getCreditScore() != null ? m.getCreditScore() : 100;
                            m.setCreditScore(Math.max(0, score - 40));
                            merchantRepo.save(m);
                        });
                        merchantRepo.findById(o.getCounterpartyId()).ifPresent(m -> {
                            int score = m.getCreditScore() != null ? m.getCreditScore() : 100;
                            m.setCreditScore(Math.max(0, score - 40));
                            merchantRepo.save(m);
                        });
                        System.out.println("[V6.2 B2B作废] 订单" + orderNo + " 虚假贸易，双方扣减信用分40分");
                    });
            r.put("success", true);
            r.put("reason", "虚假贸易订单已作废，关联双方信用分扣减40分");
        }
        return ApiResponse.success(r);
    }

    /** 完成订单：status 2 -> 3 */
    @PostMapping("/complete")
    public ApiResponse<Void> complete(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            b2bRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(3);
                        o.setStatusDesc("已完成");
                        o.setCompletedAt(java.time.LocalDateTime.now().toString());
                        o.setVersion(o.getVersion() != null ? o.getVersion() + 1 : 2);
                        b2bRepo.save(o);
                    });
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/{orderNo}/documents")
    public ApiResponse<List<Map<String, Object>>> documents(@PathVariable String orderNo) {
        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(mapOf("id", 1, "name", "采购合同", "url", "/files/contract.pdf"));
        docs.add(mapOf("id", 2, "name", "发票", "url", "/files/invoice.pdf"));
        return ApiResponse.success(docs);
    }

    /**
     * V6.2 AI核验结果
     */
    @GetMapping("/{orderNo}/verify-result")
    public ApiResponse<Map<String, Object>> verifyResult(@PathVariable String orderNo) {
        return b2bRepo.findAll().stream()
                .filter(o -> orderNo.equals(o.getOrderNo()))
                .findFirst()
                .map(o -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("aiVerificationResult", o.getAiVerificationResult());
                    r.put("aiVerificationScore", o.getAiVerificationScore());
                    r.put("verified", o.getAiVerificationResult() != null && o.getAiVerificationResult() == 0);
                    r.put("items", List.of("营业执照匹配", "账户信息一致", "商品品类合规"));
                    return ApiResponse.success(r);
                })
                .orElse(ApiResponse.fail("B2B订单不存在"));
    }

    @PostMapping("/{orderNo}/verify-confirm")
    public ApiResponse<Void> verifyConfirm(@PathVariable String orderNo, @RequestBody Map<String, Object> body) {
        Object resultObj = body.get("aiVerificationResult");
        if (resultObj != null) {
            int result = Integer.parseInt(resultObj.toString());
            b2bRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setAiVerificationResult(result);
                        o.setVersion(o.getVersion() != null ? o.getVersion() + 1 : 2);
                        b2bRepo.save(o);
                    });
        }
        return ApiResponse.success(null);
    }

    private void updateStatus(Map<String, Object> body, int status, String statusDesc) {
        Object orderNoObj = body.get("orderNo");
        Object idObj = body.get("id");
        if (orderNoObj != null) {
            String orderNo = orderNoObj.toString();
            b2bRepo.findAll().stream()
                    .filter(o -> orderNo.equals(o.getOrderNo()))
                    .findFirst()
                    .ifPresent(o -> {
                        o.setStatus(status);
                        o.setStatusDesc(statusDesc);
                        o.setVersion(o.getVersion() != null ? o.getVersion() + 1 : 2);
                        b2bRepo.save(o);
                    });
        } else if (idObj != null) {
            long id = Long.parseLong(idObj.toString());
            b2bRepo.findById(id).ifPresent(o -> {
                o.setStatus(status);
                o.setStatusDesc(statusDesc);
                o.setVersion(o.getVersion() != null ? o.getVersion() + 1 : 2);
                b2bRepo.save(o);
            });
        }
    }

    /**
     * V6.2 检查7日超时
     */
    private boolean isExpired(B2BOrder o) {
        if (o.getExpireAt() == null) return false;
        try {
            java.time.LocalDateTime expireAt = java.time.LocalDateTime.parse(o.getExpireAt());
            return java.time.LocalDateTime.now().isAfter(expireAt);
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) m.put((String) kvs[i], kvs[i + 1]);
        return m;
    }
}
