package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("userCount", 12580);
        d.put("merchantCount", MockData.merchants.size());
        d.put("orderCount", MockData.orders.size());
        d.put("totalTransaction", 285600);
        d.put("totalLsc", 9860000);
        d.put("todayOrder", 38);
        d.put("todayTransaction", 12856);
        d.put("todayLsc", 2800);
        d.put("writeoffRate", "94.5%");
        d.put("activeMerchants", MockData.merchants.stream().filter(m -> m.get("auditStatus").equals(1)).count());
        return ApiResponse.success(d);
    }

    @GetMapping("/release-trend")
    public ApiResponse<List<Map<String, Object>>> releaseTrend(@RequestParam(defaultValue = "7") int days) {
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days; i > 0; i--) {
            trend.add(Map.of(
                    "date", java.time.LocalDate.now().minusDays(i).toString(),
                    "released", 2000 + new Random().nextInt(3000),
                    "writeoff", 800 + new Random().nextInt(2000)));
        }
        return ApiResponse.success(trend);
    }

    @GetMapping("/writeoff-rate")
    public ApiResponse<Map<String, Object>> writeoffRate() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalWriteoff", 312000);
        r.put("totalReleased", 9860000);
        r.put("rate", "3.16%");
        r.put("todayWriteoff", 2800);
        r.put("weekWriteoff", 18600);
        r.put("monthWriteoff", 85000);
        return ApiResponse.success(r);
    }

    @GetMapping("/merchant/stats")
    public ApiResponse<Map<String, Object>> merchantStats() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("todayOrders", 38);
        s.put("todayRevenue", 12856.50);
        s.put("availableLsc", 12850);
        s.put("creditScore", 100);
        s.put("weekRevenue", 85600);
        s.put("weekOrders", 265);
        s.put("monthRevenue", 356000);
        s.put("monthOrders", 1080);
        return ApiResponse.success(s);
    }

    @GetMapping("/merchant/week-trend")
    public ApiResponse<List<Map<String, Object>>> merchantWeekTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 7; i > 0; i--) {
            trend.add(Map.of(
                    "date", java.time.LocalDate.now().minusDays(i).toString(),
                    "orders", 20 + new Random().nextInt(30),
                    "revenue", 800 + new Random().nextInt(2000)));
        }
        return ApiResponse.success(trend);
    }
}
