package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.RiskLog;
import com.lianshengtong.api.repository.RiskLogRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskLogRepository riskRepo;

    public RiskController(RiskLogRepository riskRepo) {
        this.riskRepo = riskRepo;
    }

    @GetMapping("/logs")
    public ApiResponse<PageResult<RiskLog>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer levelCode,
            @RequestParam(required = false) Integer merchantId) {
        List<RiskLog> filtered = riskRepo.findAll().stream().filter(l -> {
            if (levelCode != null && !levelCode.equals(l.getLevelCode())) return false;
            if (merchantId != null && l.getMerchantId() != null && merchantId.longValue() != l.getMerchantId()) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/logs/{id}")
    public ApiResponse<RiskLog> logDetail(@PathVariable long id) {
        return riskRepo.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("日志不存在"));
    }

    @GetMapping("/dashboard")
    public ApiResponse<Map<String, Object>> dashboard() {
        List<RiskLog> all = riskRepo.findAll();
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("totalAlerts", all.size());
        d.put("highRisk", all.stream().filter(l -> "高".equals(l.getLevel())).count());
        d.put("mediumRisk", all.stream().filter(l -> "中".equals(l.getLevel())).count());
        d.put("lowRisk", all.stream().filter(l -> "低".equals(l.getLevel())).count());
        d.put("todayAlerts", 3);
        d.put("topMerchants", List.of(
                Map.of("merchantName", "佳味食品加工厂", "riskScore", 82, "level", "高"),
                Map.of("merchantName", "明珠家居广场", "riskScore", 68, "level", "中")
        ));
        return ApiResponse.success(d);
    }
}
