package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/order")
public class OrderController {

    @GetMapping("/list")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer merchantId) {
        List<Map<String, Object>> filtered = MockData.orders.stream().filter(o -> {
            if (status != null && !status.equals(o.get("status"))) return false;
            if (merchantId != null && !merchantId.equals(o.get("merchantId"))) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String orderNo) {
        return MockData.orders.stream().filter(o -> o.get("orderNo").equals(orderNo))
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("订单不存在"));
    }

    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> detailByParam(@RequestParam String orderNo) {
        return detail(orderNo);
    }

    @GetMapping("/stats-today")
    public ApiResponse<Map<String, Object>> statsToday(@RequestParam(required = false) Integer merchantId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("orderCount", 38);
        stats.put("totalAmount", 12856.50);
        stats.put("lscAmount", 6428);
        stats.put("rmbAmount", 6428.50);
        stats.put("refundCount", 2);
        stats.put("refundAmount", 199.00);
        return ApiResponse.success(stats);
    }

    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> order = new LinkedHashMap<>(body);
        order.put("id", MockData.orders.size() + 1);
        order.put("orderNo", "LS" + System.currentTimeMillis());
        order.put("status", 0);
        order.put("statusDesc", "待支付");
        order.put("createdAt", java.time.LocalDateTime.now().toString());
        MockData.orders.add(0, order);
        return ApiResponse.success(order);
    }

    @PostMapping("/pay")
    public ApiResponse<Map<String, Object>> pay(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", 1);
        r.put("statusDesc", "已支付");
        return ApiResponse.success(r);
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@RequestBody Map<String, Object> body) {
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
    public ApiResponse<PageResult<Map<String, Object>>> refundList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> refunds = MockData.orders.stream()
                .filter(o -> o.get("status").equals(4) || o.get("status").equals(5))
                .collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(refunds, page, size));
    }

    @PostMapping("/refund/apply")
    public ApiResponse<Void> refundApply(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @GetMapping("/export")
    public ApiResponse<Map<String, Object>> export() {
        return ApiResponse.success(new LinkedHashMap<>(Map.of("url", "/files/orders.xlsx")));
    }
}
