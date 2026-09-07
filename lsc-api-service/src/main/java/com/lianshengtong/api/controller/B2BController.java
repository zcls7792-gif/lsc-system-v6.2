package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/b2b")
public class B2BController {

    @GetMapping("/list")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status) {
        List<Map<String, Object>> filtered = MockData.b2bOrders.stream().filter(o -> {
            if (status != null && !status.equals(o.get("status"))) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String orderNo) {
        return MockData.b2bOrders.stream().filter(o -> o.get("orderNo").equals(orderNo))
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("B2B订单不存在"));
    }

    @PostMapping("/create")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Map<String, Object> order = new LinkedHashMap<>(body);
        order.put("id", MockData.b2bOrders.size() + 1);
        order.put("orderNo", "B2B" + System.currentTimeMillis());
        order.put("status", 0);
        order.put("statusDesc", "待确认");
        order.put("createdAt", java.time.LocalDateTime.now().toString());
        MockData.b2bOrders.add(0, order);
        return ApiResponse.success(order);
    }

    @PostMapping("/confirm")
    public ApiResponse<Void> confirm(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @PostMapping("/complete")
    public ApiResponse<Void> complete(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @GetMapping("/{orderNo}/documents")
    public ApiResponse<List<Map<String, Object>>> documents(@PathVariable String orderNo) {
        List<Map<String, Object>> docs = new ArrayList<>();
        docs.add(mapOf("id", 1, "name", "采购合同", "url", "/files/contract.pdf"));
        docs.add(mapOf("id", 2, "name", "发票", "url", "/files/invoice.pdf"));
        return ApiResponse.success(docs);
    }

    @GetMapping("/{orderNo}/verify-result")
    public ApiResponse<Map<String, Object>> verifyResult(@PathVariable String orderNo) {
        return ApiResponse.success(mapOf("verified", true, "score", 92,
                "items", List.of("营业执照匹配", "账户信息一致", "商品品类合规")));
    }

    @PostMapping("/{orderNo}/verify-confirm")
    public ApiResponse<Void> verifyConfirm(@PathVariable String orderNo, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    private Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) m.put((String) kvs[i], kvs[i + 1]);
        return m;
    }
}
