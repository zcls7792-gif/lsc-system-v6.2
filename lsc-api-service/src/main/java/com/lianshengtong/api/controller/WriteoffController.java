package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/writeoff")
public class WriteoffController {

    @GetMapping("/list")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer merchantId) {
        List<Map<String, Object>> filtered = MockData.writeoffs.stream().filter(w -> {
            if (status != null && !status.equals(w.get("status"))) return false;
            if (merchantId != null && !merchantId.equals(w.get("merchantId"))) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{orderNo}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable String orderNo) {
        return MockData.writeoffs.stream().filter(w -> w.get("orderNo").equals(orderNo))
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("核销记录不存在"));
    }

    @GetMapping("/by-id/{id}")
    public ApiResponse<Map<String, Object>> byId(@PathVariable int id) {
        return MockData.writeoffs.stream().filter(w -> ((Integer) w.get("id")) == id)
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("核销记录不存在"));
    }

    @PostMapping("/apply")
    public ApiResponse<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        Map<String, Object> w = new LinkedHashMap<>(body);
        w.put("id", MockData.writeoffs.size() + 1);
        w.put("orderNo", "WO" + System.currentTimeMillis());
        w.put("status", 0);
        w.put("statusDesc", "待审核");
        w.put("createdAt", java.time.LocalDateTime.now().toString());
        MockData.writeoffs.add(0, w);
        return ApiResponse.success(w);
    }

    @PostMapping("/audit")
    public ApiResponse<Void> audit(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalCount", MockData.writeoffs.size());
        s.put("pendingCount", MockData.writeoffs.stream().filter(w -> w.get("status").equals(0)).count());
        s.put("approvedCount", MockData.writeoffs.stream().filter(w -> w.get("status").equals(1)).count());
        s.put("rejectedCount", MockData.writeoffs.stream().filter(w -> w.get("status").equals(2)).count());
        s.put("totalLscAmount", 125680);
        s.put("todayCount", 5);
        s.put("todayLscAmount", 2800);
        return ApiResponse.success(s);
    }

    @GetMapping("/quota")
    public ApiResponse<Map<String, Object>> quota(@RequestParam(required = false) Integer merchantId) {
        return ApiResponse.success(Map.of("dailyLimit", 5500, "usedToday", 1200, "remaining", 4300));
    }
}
