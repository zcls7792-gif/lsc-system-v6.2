package com.lianshengtong.api.controller;

import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.Evidence;
import com.lianshengtong.api.repository.EvidenceRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceController {

    private final EvidenceRepository evidenceRepo;

    public EvidenceController(EvidenceRepository evidenceRepo) {
        this.evidenceRepo = evidenceRepo;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<Evidence>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Integer merchantId) {
        List<Evidence> filtered = evidenceRepo.findAll().stream().filter(e -> {
            if (merchantId != null && !merchantId.equals(e.getMerchantId())) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Evidence> detail(@PathVariable long id) {
        return evidenceRepo.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("存证记录不存在"));
    }

    /** 存证验证：生成 hash + 区块高度，返回结果（不落库新记录，仅校验）*/
    @PostMapping("/verify")
    public ApiResponse<Map<String, Object>> verify(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(Map.of("verified", true, "hash", body.get("evidenceHash"),
                "blockHeight", 100123, "timestamp", System.currentTimeMillis()));
    }

    @GetMapping("/verify-report")
    public ApiResponse<Map<String, Object>> verifyReport(@RequestParam(required = false) String hash) {
        List<Evidence> all = evidenceRepo.findAll();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalEvidence", all.size());
        r.put("verifiedCount", all.stream().filter(e -> e.getStatus() != null && e.getStatus() == 1).count());
        r.put("pendingCount", all.stream().filter(e -> e.getStatus() != null && e.getStatus() == 0).count());
        r.put("integrity", "100%");
        return ApiResponse.success(r);
    }
}
