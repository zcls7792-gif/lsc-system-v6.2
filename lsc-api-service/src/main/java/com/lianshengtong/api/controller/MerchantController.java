package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.Merchant;
import com.lianshengtong.api.repository.MerchantRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    private final MerchantRepository merchantRepo;

    public MerchantController(MerchantRepository merchantRepo) {
        this.merchantRepo = merchantRepo;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<Merchant>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        List<Merchant> filtered = merchantRepo.findAll().stream().filter(m -> {
            if (keyword != null && !keyword.isEmpty()) {
                if (m.getMerchantName() == null || !m.getMerchantName().contains(keyword)) return false;
            }
            if (status != null && !status.equals(m.getAuditStatus())) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Merchant> detail(@PathVariable long id) {
        return merchantRepo.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("商家不存在"));
    }

    @GetMapping("/audit/list")
    public ApiResponse<PageResult<Merchant>> auditList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Merchant> pending = merchantRepo.findAll().stream()
                .filter(m -> m.getAuditStatus() != null && m.getAuditStatus() == 0)
                .collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(pending, page, size));
    }

    /** 审核落库：更新 auditStatus/status */
    @PostMapping("/audit")
    public ApiResponse<Void> audit(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        Object statusObj = body.get("auditStatus");
        if (idObj == null) return ApiResponse.fail("缺少 id");
        long id = Long.parseLong(idObj.toString());
        merchantRepo.findById(id).ifPresent(m -> {
            if (statusObj != null) {
                int s = Integer.parseInt(statusObj.toString());
                m.setAuditStatus(s);
                m.setStatus(s);
            }
            merchantRepo.save(m);
        });
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/credit")
    public ApiResponse<Merchant> credit(@PathVariable long id) {
        return merchantRepo.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("商家不存在"));
    }

    /** 信用分调整落库 */
    @PostMapping("/{id}/credit/adjust")
    public ApiResponse<Void> creditAdjust(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Object deltaObj = body.get("delta");
        Object scoreObj = body.get("creditScore");
        merchantRepo.findById(id).ifPresent(m -> {
            if (scoreObj != null) {
                m.setCreditScore(Integer.parseInt(scoreObj.toString()));
            } else if (deltaObj != null && m.getCreditScore() != null) {
                m.setCreditScore(m.getCreditScore() + Integer.parseInt(deltaObj.toString()));
            }
            // 信用分变化联动处罚状态
            int c = m.getCreditScore() == null ? 100 : m.getCreditScore();
            m.setPenaltyStatus(c < 40 ? 3 : c < 60 ? 2 : c < 80 ? 1 : 0);
            merchantRepo.save(m);
        });
        return ApiResponse.success(null);
    }

    /** 处罚落库 */
    @PostMapping("/{id}/penalty")
    public ApiResponse<Void> penalty(@PathVariable long id, @RequestBody Map<String, Object> body) {
        Object pStatus = body.get("penaltyStatus");
        merchantRepo.findById(id).ifPresent(m -> {
            if (pStatus != null) m.setPenaltyStatus(Integer.parseInt(pStatus.toString()));
            merchantRepo.save(m);
        });
        return ApiResponse.success(null);
    }

    @GetMapping("/violation/logs")
    public ApiResponse<PageResult<Map<String, Object>>> violationLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.success(PageResult.of(MockData.riskLogs, page, size));
    }

    @GetMapping("/store/addresses")
    public ApiResponse<List<Map<String, Object>>> storeAddresses() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Merchant m : merchantRepo.findAll()) {
            list.add(mapOf("id", m.getId(), "merchantId", m.getId(),
                    "province", m.getProvince(), "city", m.getCity(),
                    "district", m.getDistrict(), "addressDetail", m.getAddressDetail(),
                    "longitude", m.getLongitude(), "latitude", m.getLatitude(),
                    "aiAddressVerified", m.getAiAddressVerified()));
        }
        return ApiResponse.success(list);
    }

    @GetMapping("/store/addresses/update-state")
    public ApiResponse<Map<String, Object>> addressUpdateState() {
        return ApiResponse.success(mapOf("count", 3, "needReview", true));
    }

    @DeleteMapping("/store/addresses/{id}")
    public ApiResponse<Void> deleteAddress(@PathVariable long id) {
        return ApiResponse.success(null);
    }

    private Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) m.put((String) kvs[i], kvs[i + 1]);
        return m;
    }
}
