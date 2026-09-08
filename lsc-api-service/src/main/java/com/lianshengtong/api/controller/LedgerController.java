package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.LedgerTxn;
import com.lianshengtong.api.repository.LedgerTxnRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ledger")
public class LedgerController {

    private final LedgerTxnRepository ledgerRepo;

    public LedgerController(LedgerTxnRepository ledgerRepo) {
        this.ledgerRepo = ledgerRepo;
    }

    @GetMapping("/account")
    public ApiResponse<Map<String, Object>> account(@RequestParam(required = false) Integer userId) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("userId", userId != null ? userId : 10001);
        a.put("totalLsc", 9860000);
        a.put("availableLsc", 12850);
        a.put("frozenLsc", 3500);
        a.put("totalReleased", 9860000);
        a.put("totalConsumed", 847150);
        a.put("totalWriteoff", 312000);
        return ApiResponse.success(a);
    }

    @GetMapping("/account/{userId}")
    public ApiResponse<Map<String, Object>> accountByUser(@PathVariable int userId) {
        return account(userId);
    }

    @GetMapping("/overview/{userId}")
    public ApiResponse<Map<String, Object>> overview(@PathVariable int userId) {
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("totalLsc", 9860000);
        o.put("availableLsc", 12850);
        o.put("frozenLsc", 3500);
        o.put("todayReleased", 2800);
        o.put("todayConsumed", 1200);
        o.put("monthlyReleased", 85000);
        o.put("monthlyConsumed", 42000);
        return ApiResponse.success(o);
    }

    @GetMapping("/transactions")
    public ApiResponse<PageResult<LedgerTxn>> transactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Integer userId) {
        List<LedgerTxn> filtered = ledgerRepo.findAll().stream().filter(t -> {
            if (type != null && !type.equals(t.getType())) return false;
            if (userId != null && t.getUserId() != null && userId.longValue() != t.getUserId()) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/transaction-types")
    public ApiResponse<List<Map<String, Object>>> transactionTypes() {
        // V6.2 第十四章 14.4 流水类型枚举 1-9
        List<Map<String, Object>> types = new ArrayList<>();
        types.add(Map.of("code", 1, "name", "消费发行"));
        types.add(Map.of("code", 2, "name", "每日释放"));
        types.add(Map.of("code", 3, "name", "推广奖励释放"));
        types.add(Map.of("code", 4, "name", "权益商城消费"));
        types.add(Map.of("code", 5, "name", "线下消费"));
        types.add(Map.of("code", 6, "name", "过期转回"));
        types.add(Map.of("code", 7, "name", "商家核销"));
        types.add(Map.of("code", 8, "name", "B2B流转支付"));
        types.add(Map.of("code", 9, "name", "退款发行回滚"));
        return ApiResponse.success(types);
    }

    @GetMapping("/recent-trend")
    public ApiResponse<List<Map<String, Object>>> recentTrend(@RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days; i > 0; i--) {
            trend.add(Map.of(
                    "date", java.time.LocalDate.now().minusDays(i).toString(),
                    "released", 2000 + new Random().nextInt(3000),
                    "consumed", 800 + new Random().nextInt(2000)));
        }
        return ApiResponse.success(trend);
    }

    @GetMapping("/promotion/summary")
    public ApiResponse<Map<String, Object>> promotionSummary() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalPromotion", 9860000);
        s.put("totalReleased", 8471500);
        s.put("totalWriteoff", 312000);
        s.put("totalFrozen", 3500);
        s.put("progress", "85.9%");
        s.put("merchantCount", 368);
        s.put("userCount", 12580);
        return ApiResponse.success(s);
    }

    @GetMapping("/available-details")
    public ApiResponse<Map<String, Object>> availableDetails(@RequestParam(required = false) Integer userId) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("availableLsc", 12850);
        d.put("frozenLsc", 3500);
        d.put("details", List.of(
                Map.of("source", "释放", "amount", 9860, "date", java.time.LocalDate.now().toString()),
                Map.of("source", "转入", "amount", 2990, "date", java.time.LocalDate.now().minusDays(1).toString())
        ));
        return ApiResponse.success(d);
    }
}
