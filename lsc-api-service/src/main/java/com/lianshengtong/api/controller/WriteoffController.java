package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.Writeoff;
import com.lianshengtong.api.entity.Merchant;
import com.lianshengtong.api.repository.WriteoffRepository;
import com.lianshengtong.api.repository.MerchantRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 核销控制器（V6.2 核销规则更新版）
 * 核销资金流向：100 LSC → 87元商家主账户 + 3元平台技术服务费 + 10元留存监管账户
 */
@RestController
@RequestMapping("/api/writeoff")
public class WriteoffController {

    // V6.2 核销状态：0待处理 1处理中 2成功 3失败
    private static final String[] STATUS_DESC = {"待处理", "处理中", "成功", "失败"};

    // V6.2 核销资金分配比例（硬常量）
    private static final double CASH_RATE = 0.87;       // 商家回收
    private static final double PLATFORM_FEE_RATE = 0.03; // 平台技术服务费
    private static final double RETAINED_RATE = 0.10;    // 留存监管账户

    // V6.2 26档 A-Z 核销限额映射
    private static final int[][] NH_LIMIT_TIERS = {
        {100000, 275}, {200000, 550}, {400000, 1100}, {600000, 1650},
        {800000, 2200}, {1000000, 2750}, {1200000, 3300}, {1400000, 3850},
        {1600000, 4400}, {1800000, 4950}, {2000000, 5500}, {2500000, 6900},
        {3000000, 8250}, {3500000, 9660}, {4000000, 11000}, {4500000, 12400},
        {5000000, 13800}, {6000000, 16500}, {7000000, 19000}, {8000000, 22000},
        {9000000, 24800}, {10000000, 27600}, {12000000, 33000}, {15000000, 41000},
        {17000000, 46900}, {20000000, 55000}
    };
    private static final String[] TIER_LABELS = {
        "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z"
    };

    // V6.2 新商家初始额度
    private static final int NEW_MERCHANT_DAILY_LIMIT = 80;

    private final WriteoffRepository writeoffRepo;
    private final MerchantRepository merchantRepo;

    public WriteoffController(WriteoffRepository writeoffRepo, MerchantRepository merchantRepo) {
        this.writeoffRepo = writeoffRepo;
        this.merchantRepo = merchantRepo;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<Writeoff>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer merchantId) {
        List<Writeoff> filtered = writeoffRepo.findAll().stream().filter(w -> {
            if (status != null && !status.equals(w.getStatus())) return false;
            if (merchantId != null && (w.getMerchantId() == null || merchantId.longValue() != w.getMerchantId())) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Writeoff> detail(@PathVariable String orderNo) {
        return writeoffRepo.findAll().stream()
                .filter(w -> orderNo.equals(w.getOrderNo()))
                .findFirst()
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("核销记录不存在"));
    }

    @GetMapping("/by-id/{id}")
    public ApiResponse<Writeoff> byId(@PathVariable long id) {
        return writeoffRepo.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("核销记录不存在"));
    }

    /**
     * V6.2 核销申请（三笔划拨逻辑）
     * 1. 校验商家资格（信用分≥40才有核销权）
     * 2. 校验每日限额（26档A-Z）
     * 3. 计算三笔划拨：87%商家主账户 + 3%平台技术服务费 + 10%留存监管
     * 4. 落库核销记录
     */
    @PostMapping("/apply")
    public ApiResponse<Writeoff> apply(@RequestBody Writeoff body) {
        // 查商家
        if (body.getMerchantId() == null) {
            return ApiResponse.fail("商家ID不能为空");
        }
        Optional<Merchant> merchantOpt = merchantRepo.findById(body.getMerchantId());
        if (merchantOpt.isEmpty()) {
            return ApiResponse.fail("商家不存在");
        }
        Merchant merchant = merchantOpt.get();

        // V6.2 信用分校验：40-59暂停核销，20-39暂停核销+B2B，<20永久关闭
        int creditScore = merchant.getCreditScore() != null ? merchant.getCreditScore() : 100;
        if (creditScore < 40) {
            return ApiResponse.fail("商家信用分低于40，核销权限已暂停");
        }

        // V6.2 每日核销限额校验
        int dailyLimit = getDailyNhLimit(merchant);
        double lscAmount = body.getLscAmount() != null ? body.getLscAmount() : 0;
        if (lscAmount > dailyLimit) {
            return ApiResponse.fail("核销数量" + (int)lscAmount + "超过每日限额" + dailyLimit + "（档位" + merchant.getNhLimitLevel() + "）");
        }

        // V6.2 信用分影响限额：60-79分降至50%
        int effectiveLimit = dailyLimit;
        if (creditScore >= 60 && creditScore < 80) {
            effectiveLimit = (int)(dailyLimit * 0.5);
            if (lscAmount > effectiveLimit) {
                return ApiResponse.fail("信用分60-79，限额降至50%（" + effectiveLimit + "），核销数量超限");
            }
        }

        // V6.2 三笔划拨计算
        double cashAmount = lscAmount * CASH_RATE;        // 87% → 商家主账户
        double platformFee = lscAmount * PLATFORM_FEE_RATE; // 3% → 平台技术服务费
        double retained = lscAmount * RETAINED_RATE;     // 10% → 留存监管账户

        long newId = writeoffRepo.count() + 1;
        body.setId(newId);
        if (body.getOrderNo() == null || body.getOrderNo().isEmpty()) {
            body.setOrderNo("WO" + System.currentTimeMillis());
        }
        // V6.2 幂等键
        if (body.getIdempotentKey() == null || body.getIdempotentKey().isEmpty()) {
            body.setIdempotentKey("NH-" + body.getOrderNo());
        }
        if (body.getVersion() == null) body.setVersion(1);

        // 设置三笔划拨金额
        body.setCashAmount(cashAmount);
        body.setPlatformFeeAmount(platformFee);
        body.setRetainedAmount(retained);
        body.setAvailableBefore(lscAmount + 10000); // 模拟核销前可用余额
        body.setAvailableAfter(10000.0);             // 核销后可用余额
        body.setFundBefore(50000.0);                  // 模拟商家主账户前余额
        body.setFundAfter(50000.0 + cashAmount);      // 商家主账户后余额

        // 状态：0待处理 → 初始为待处理
        if (body.getStatus() == null) body.setStatus(0);
        if (body.getStatusDesc() == null && body.getStatus() >= 0 && body.getStatus() < STATUS_DESC.length) {
            body.setStatusDesc(STATUS_DESC[body.getStatus()]);
        }
        if (body.getCreatedAt() == null) {
            body.setCreatedAt(java.time.LocalDateTime.now().toString());
        }

        Writeoff saved = writeoffRepo.save(body);
        System.out.println("[V6.2 核销] 商家" + body.getMerchantId() + " 核销" + (int)lscAmount + "LSC"
                + " → 商家主账户" + String.format("%.2f", cashAmount) + "元"
                + " + 平台服务费" + String.format("%.2f", platformFee) + "元"
                + " + 留存监管" + String.format("%.2f", retained) + "元");
        return ApiResponse.success(saved);
    }

    /**
     * V6.2 核销审核：0待处理 → 1处理中 → 2成功/3失败
     */
    @PostMapping("/audit")
    public ApiResponse<Void> audit(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        Object statusObj = body.get("status");
        if (idObj != null && statusObj != null) {
            long id = Long.parseLong(idObj.toString());
            int status = Integer.parseInt(statusObj.toString());
            writeoffRepo.findById(id).ifPresent(w -> {
                w.setStatus(status);
                if (status >= 0 && status < STATUS_DESC.length) {
                    w.setStatusDesc(STATUS_DESC[status]);
                }
                if (status == 2) { // 成功
                    w.setCompletedAt(java.time.LocalDateTime.now().toString());
                }
                w.setVersion(w.getVersion() != null ? w.getVersion() + 1 : 2);
                writeoffRepo.save(w);
            });
        }
        return ApiResponse.success(null);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        List<Writeoff> all = writeoffRepo.findAll();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalCount", all.size());
        s.put("pendingCount", all.stream().filter(w -> w.getStatus() != null && w.getStatus() == 0).count());
        s.put("processingCount", all.stream().filter(w -> w.getStatus() != null && w.getStatus() == 1).count());
        s.put("successCount", all.stream().filter(w -> w.getStatus() != null && w.getStatus() == 2).count());
        s.put("failedCount", all.stream().filter(w -> w.getStatus() != null && w.getStatus() == 3).count());
        s.put("totalLscAmount", all.stream().mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount()).sum());
        s.put("totalCashAmount", all.stream().mapToDouble(w -> w.getCashAmount() == null ? 0 : w.getCashAmount()).sum());
        s.put("totalPlatformFee", all.stream().mapToDouble(w -> w.getPlatformFeeAmount() == null ? 0 : w.getPlatformFeeAmount()).sum());
        s.put("totalRetained", all.stream().mapToDouble(w -> w.getRetainedAmount() == null ? 0 : w.getRetainedAmount()).sum());
        s.put("todayCount", all.stream().filter(w -> {
            String today = java.time.LocalDate.now().toString();
            return w.getCreatedAt() != null && w.getCreatedAt().startsWith(today);
        }).count());
        return ApiResponse.success(s);
    }

    /**
     * V6.2 核销限额查询：根据商家月营业额返回档位和每日限额
     */
    @GetMapping("/quota")
    public ApiResponse<Map<String, Object>> quota(@RequestParam(required = false) Long merchantId) {
        Map<String, Object> q = new LinkedHashMap<>();
        if (merchantId != null) {
            Optional<Merchant> opt = merchantRepo.findById(merchantId);
            if (opt.isPresent()) {
                Merchant m = opt.get();
                int dailyLimit = getDailyNhLimit(m);
                int creditScore = m.getCreditScore() != null ? m.getCreditScore() : 100;
                int effectiveLimit = dailyLimit;
                if (creditScore >= 60 && creditScore < 80) {
                    effectiveLimit = (int)(dailyLimit * 0.5);
                } else if (creditScore < 40) {
                    effectiveLimit = 0;
                }
                q.put("tier", m.getNhLimitLevel());
                q.put("monthlyRevenue", m.getMonthlyRevenue());
                q.put("dailyLimit", dailyLimit);
                q.put("effectiveLimit", effectiveLimit);
                q.put("creditScore", creditScore);
                q.put("penaltyStatus", m.getPenaltyStatus());
                // 今日已用
                String today = java.time.LocalDate.now().toString();
                int usedToday = (int) writeoffRepo.findAll().stream()
                        .filter(w -> merchantId.equals(w.getMerchantId())
                                && w.getCreatedAt() != null && w.getCreatedAt().startsWith(today)
                                && w.getStatus() != null && w.getStatus() <= 2)
                        .mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount())
                        .sum();
                q.put("usedToday", usedToday);
                q.put("remaining", Math.max(0, effectiveLimit - usedToday));
            }
        } else {
            q.put("dailyLimit", 5500);
            q.put("usedToday", 1200);
            q.put("remaining", 4300);
        }
        return ApiResponse.success(q);
    }

    /**
     * V6.2 根据商家月营业额计算26档每日核销限额
     */
    private int getDailyNhLimit(Merchant merchant) {
        int revenue = merchant.getMonthlyRevenue() != null ? merchant.getMonthlyRevenue() : 0;
        // 新商家初始额度
        if (revenue < 100000) {
            return NEW_MERCHANT_DAILY_LIMIT;
        }
        // 匹配26档
        for (int i = 0; i < NH_LIMIT_TIERS.length; i++) {
            if (revenue >= NH_LIMIT_TIERS[i][0]) {
                // 继续找更高的档位
                if (i == NH_LIMIT_TIERS.length - 1 || revenue < NH_LIMIT_TIERS[i + 1][0]) {
                    return NH_LIMIT_TIERS[i][1];
                }
            }
        }
        return NEW_MERCHANT_DAILY_LIMIT;
    }

    /**
     * V6.2 获取档位标签
     */
    public static String getTierLabel(int monthlyRevenue) {
        if (monthlyRevenue < 100000) return "0"; // 新商家
        for (int i = 0; i < NH_LIMIT_TIERS.length; i++) {
            if (monthlyRevenue >= NH_LIMIT_TIERS[i][0]) {
                if (i == NH_LIMIT_TIERS.length - 1 || monthlyRevenue < NH_LIMIT_TIERS[i + 1][0]) {
                    return TIER_LABELS[i];
                }
            }
        }
        return "0";
    }

    /**
     * V6.2 根据档位标签获取每日限额
     */
    public static int getDailyLimitByTier(String tier) {
        for (int i = 0; i < TIER_LABELS.length; i++) {
            if (TIER_LABELS[i].equals(tier)) {
                return NH_LIMIT_TIERS[i][1];
            }
        }
        return NEW_MERCHANT_DAILY_LIMIT;
    }
}
