package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    @GetMapping("/logs")
    public ApiResponse<PageResult<Map<String, Object>>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer levelCode,
            @RequestParam(required = false) Integer merchantId) {
        List<Map<String, Object>> filtered = new ArrayList<>(MockData.riskLogs);
        if (merchantId != null) filtered = filtered.stream().filter(l -> merchantId.equals(l.get("merchantId"))).toList();
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/logs/{id}")
    public ApiResponse<Map<String, Object>> logDetail(@PathVariable int id) {
        return MockData.riskLogs.stream().filter(l -> ((Integer) l.get("id")) == id)
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("日志不存在"));
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("totalAlerts", MockData.riskLogs.size());
        d.put("highRisk", MockData.riskLogs.stream().filter(l -> l.get("level").equals("高")).count());
        d.put("mediumRisk", MockData.riskLogs.stream().filter(l -> l.get("level").equals("中")).count());
        d.put("lowRisk", MockData.riskLogs.stream().filter(l -> l.get("level").equals("低")).count());
        d.put("todayAlerts", 3);
        d.put("topMerchants", List.of(
                Map.of("merchantName", "佳味食品加工厂", "riskScore", 82, "level", "高"),
                Map.of("merchantName", "明珠家居广场", "riskScore", 68, "level", "中")
        ));
        return ApiResponse.success(d);
    }
}
