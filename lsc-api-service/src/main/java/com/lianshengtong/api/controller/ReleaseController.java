package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/release")
public class ReleaseController {

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("totalReleased", 9860000);
        s.put("totalWriteoff", 312000);
        s.put("totalFrozen", 3500);
        s.put("availableLsc", 9545500);
        s.put("merchantCount", 368);
        s.put("userCount", 12580);
        s.put("todayReleased", 2800);
        s.put("monthReleased", 85000);
        s.put("progress", "85.9%");
        return ApiResponse.success(s);
    }

    @GetMapping("/trend")
    public ApiResponse<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "30") int days) {
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = days; i > 0; i--) {
            trend.add(Map.of(
                    "date", java.time.LocalDate.now().minusDays(i).toString(),
                    "released", 2000 + new Random().nextInt(3000),
                    "writeoff", 800 + new Random().nextInt(2000)));
        }
        return ApiResponse.success(trend);
    }

    @GetMapping("/predict")
    public ApiResponse<Map<String, Object>> predict() {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("nextMonthPredict", 95000);
        p.put("confidence", "92%");
        p.put("factors", List.of("历史趋势", "商户活跃度", "季节因素"));
        p.put("trend", "上升");
        p.put("growthRate", "11.8%");
        return ApiResponse.success(p);
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("releaseRatio", 0.85);
        c.put("writeoffRatio", 0.15);
        c.put("minReleaseAmount", 100);
        c.put("maxReleaseAmount", 100000);
        c.put("dailyReleaseLimit", 10000);
        c.put("autoApprove", false);
        c.put("status", 1);
        return ApiResponse.success(c);
    }

    @PostMapping("/config")
    public ApiResponse<Void> updateConfig(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @GetMapping("/gray/approvals")
    public ApiResponse<PageResult<Map<String, Object>>> grayApprovals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("id", 1, "merchantName", "盛源百货商行", "status", 0, "amount", 5000));
        list.add(Map.of("id", 2, "merchantName", "优选生鲜超市", "status", 1, "amount", 3200));
        return ApiResponse.success(PageResult.of(list, page, size));
    }

    @GetMapping("/gray/approvals/{id}")
    public ApiResponse<Map<String, Object>> grayApprovalDetail(@PathVariable int id) {
        return ApiResponse.success(Map.of("id", id, "status", 0, "amount", 5000,
                "merchantName", "盛源百货商行", "reason", "灰度测试释放"));
    }
}
