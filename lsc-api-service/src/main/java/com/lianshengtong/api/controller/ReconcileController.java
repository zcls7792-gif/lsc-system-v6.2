package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/reconcile")
public class ReconcileController {

    @GetMapping("/report")
    public ApiResponse<Map<String, Object>> report(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalTransactions", 12580);
        r.put("matchedCount", 12560);
        r.put("unmatchedCount", 20);
        r.put("matchRate", "99.84%");
        r.put("totalLscAmount", 986000);
        r.put("totalRmbAmount", 1285600);
        r.put("diffAmount", 350.50);
        r.put("status", "基本一致");
        return ApiResponse.success(r);
    }

    @GetMapping("/report/{date}")
    public ApiResponse<Map<String, Object>> reportByDate(@PathVariable String date) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("date", date);
        r.put("totalTransactions", 420);
        r.put("matchedCount", 418);
        r.put("unmatchedCount", 2);
        r.put("matchRate", "99.52%");
        r.put("totalLscAmount", 28000);
        r.put("totalRmbAmount", 35600);
        return ApiResponse.success(r);
    }
}
