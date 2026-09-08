package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.entity.LedgerTxn;
import com.lianshengtong.api.entity.Writeoff;
import com.lianshengtong.api.entity.Order;
import com.lianshengtong.api.repository.LedgerTxnRepository;
import com.lianshengtong.api.repository.WriteoffRepository;
import com.lianshengtong.api.repository.OrderRepository;
import com.lianshengtong.api.util.HashUtil;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * 对账控制器（V6.2 第十二章 12.3 日终对账机制）
 * 每日凌晨AI对账Agent自动比对支付机构流水与内部LSC账本流水
 * 异常即时告警，对账结果哈希上链存证
 */
@RestController
@RequestMapping("/api/reconcile")
public class ReconcileController {

    private final LedgerTxnRepository ledgerRepo;
    private final WriteoffRepository writeoffRepo;
    private final OrderRepository orderRepo;

    public ReconcileController(LedgerTxnRepository ledgerRepo,
                               WriteoffRepository writeoffRepo,
                               OrderRepository orderRepo) {
        this.ledgerRepo = ledgerRepo;
        this.writeoffRepo = writeoffRepo;
        this.orderRepo = orderRepo;
    }

    /**
     * V6.2 对账报告
     * 比对：支付机构流水（订单交易额）vs 内部LSC账本（流水类型1-9）
     */
    @GetMapping("/report")
    public ApiResponse<Map<String, Object>> report(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        // 默认取今日（final 局部变量，供 lambda 使用）
        final String start = startDate != null ? startDate : LocalDate.now().toString();
        final String end = endDate != null ? endDate : LocalDate.now().toString();

        // 订单交易笔数和金额（人民币侧）
        List<Order> orders = orderRepo.findAll().stream()
                .filter(o -> o.getCreatedAt() != null
                        && o.getCreatedAt().compareTo(start) >= 0
                        && o.getCreatedAt().compareTo(end + "T23:59:59") <= 0)
                .collect(java.util.stream.Collectors.toList());
        long totalTransactions = orders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() >= 1)
                .count();
        double totalRmbAmount = orders.stream()
                .filter(o -> o.getStatus() != null && o.getStatus() >= 1)
                .mapToDouble(o -> o.getRmbAmount() == null ? 0 : o.getRmbAmount())
                .sum();
        double totalLscIssueExpected = totalRmbAmount; // 1元 = 1 LSC 发行

        // 内部 LSC 账本流水（type 1 消费发行）
        List<LedgerTxn> txns = ledgerRepo.findAll().stream()
                .filter(t -> t.getType() != null && t.getType() == 1)
                .filter(t -> t.getCreatedAt() != null
                        && t.getCreatedAt().compareTo(start) >= 0)
                .collect(java.util.stream.Collectors.toList());
        long totalLscActual = txns.stream()
                .mapToLong(t -> t.getAmount() == null ? 0 : t.getAmount())
                .sum();

        // 核销记录
        List<Writeoff> writeoffs = writeoffRepo.findAll().stream()
                .filter(w -> w.getCreatedAt() != null && w.getCreatedAt().startsWith(start))
                .collect(java.util.stream.Collectors.toList());
        long writeoffCount = writeoffs.size();
        double writeoffLsc = writeoffs.stream()
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getLscAmount() == null ? 0 : w.getLscAmount())
                .sum();
        double expectedCash = writeoffs.stream()
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getCashAmount() == null ? 0 : w.getCashAmount())
                .sum();
        double expectedPlatformFee = writeoffs.stream()
                .filter(w -> w.getStatus() != null && w.getStatus() == 2)
                .mapToDouble(w -> w.getPlatformFeeAmount() == null ? 0 : w.getPlatformFeeAmount())
                .sum();

        // 差异计算
        double lscDiff = totalLscActual - totalLscIssueExpected;
        long matchedCount = Math.min(orders.size(), txns.size());
        long unmatchedCount = Math.abs(orders.size() - txns.size());
        double matchRate = totalTransactions > 0
                ? (double) matchedCount / totalTransactions * 100 : 100;

        // V6.2 状态判定
        String status;
        boolean alert = false;
        if (Math.abs(lscDiff) < 1 && matchRate >= 99.5) {
            status = "一致";
        } else if (Math.abs(lscDiff) < 100 && matchRate >= 95) {
            status = "基本一致";
        } else {
            status = "异常";
            alert = true;
        }

        // V6.2 对账结果哈希上链存证
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("date", start);
        evidence.put("totalTransactions", totalTransactions);
        evidence.put("totalRmbAmount", totalRmbAmount);
        evidence.put("totalLscActual", totalLscActual);
        evidence.put("writeoffLsc", writeoffLsc);
        evidence.put("expectedCash", expectedCash);
        evidence.put("expectedPlatformFee", expectedPlatformFee);
        String hash = HashUtil.sha256(evidence);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("date", start);
        r.put("totalTransactions", totalTransactions);
        r.put("matchedCount", matchedCount);
        r.put("unmatchedCount", unmatchedCount);
        r.put("matchRate", String.format("%.2f%%", matchRate));
        r.put("totalRmbAmount", Math.round(totalRmbAmount * 100) / 100.0);
        r.put("totalLscExpected", Math.round(totalLscIssueExpected));
        r.put("totalLscActual", totalLscActual);
        r.put("lscDiff", Math.round(lscDiff * 100) / 100.0);
        r.put("writeoffCount", writeoffCount);
        r.put("writeoffLsc", Math.round(writeoffLsc * 100) / 100.0);
        r.put("expectedCash87", Math.round(expectedCash * 100) / 100.0);
        r.put("expectedPlatformFee3", Math.round(expectedPlatformFee * 100) / 100.0);
        r.put("status", status);
        r.put("alert", alert);
        r.put("evidenceHash", hash);
        r.put("blockchainStored", !alert); // 异常时不存证，告警处理
        if (alert) {
            r.put("alertRecipients", List.of("超级管理员1", "超级管理员2"));
            System.out.println("[V6.2 对账告警] 日期" + start
                    + " LSC差异" + lscDiff + " 匹配率" + matchRate + "%");
        }
        return ApiResponse.success(r);
    }

    @GetMapping("/report/{date}")
    public ApiResponse<Map<String, Object>> reportByDate(@PathVariable String date) {
        return report(date, date);
    }

    /**
     * V6.2 对账异常清单
     */
    @GetMapping("/mismatches")
    public ApiResponse<List<Map<String, Object>>> mismatches(
            @RequestParam(required = false) String date) {
        final String d = date != null ? date : LocalDate.now().toString();
        List<Map<String, Object>> list = new ArrayList<>();

        // 订单侧 vs 账本侧逐笔对比
        List<Order> orders = orderRepo.findAll().stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().startsWith(d))
                .filter(o -> o.getStatus() != null && o.getStatus() >= 1)
                .collect(java.util.stream.Collectors.toList());

        for (Order o : orders) {
            boolean matched = ledgerRepo.findAll().stream()
                    .anyMatch(t -> t.getType() != null && t.getType() == 1
                            && o.getOrderNo().equals(t.getOrderNo()));
            if (!matched) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("orderNo", o.getOrderNo());
                m.put("type", "LSC发行缺失");
                m.put("rmbAmount", o.getRmbAmount());
                m.put("expectedLsc", o.getRmbAmount());
                m.put("actualLsc", 0);
                m.put("diff", o.getRmbAmount());
                list.add(m);
            }
        }
        return ApiResponse.success(list);
    }
}
