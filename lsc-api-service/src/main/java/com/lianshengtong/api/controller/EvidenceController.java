package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    @GetMapping("/list")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer merchantId) {
        List<Map<String, Object>> filtered = new ArrayList<>(MockData.evidenceRecords);
        if (merchantId != null) filtered = filtered.stream().filter(e -> merchantId.equals(e.get("merchantId"))).toList();
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable int id) {
        return MockData.evidenceRecords.stream().filter(e -> ((Integer) e.get("id")) == id)
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("存证记录不存在"));
    }

    @PostMapping("/verify")
    public ApiResponse<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("verified", true, "hash", body.get("evidenceHash"),
                "blockHeight", 100123, "timestamp", System.currentTimeMillis()));
    }

    @GetMapping("/verify-report")
    public ApiResponse<Map<String, Object>> verifyReport(@RequestParam(required = false) String hash) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalEvidence", MockData.evidenceRecords.size());
        r.put("verifiedCount", MockData.evidenceRecords.stream().filter(e -> e.get("status").equals(1)).count());
        r.put("pendingCount", MockData.evidenceRecords.stream().filter(e -> e.get("status").equals(0)).count());
        r.put("integrity", "100%");
        return ApiResponse.success(r);
    }
}
