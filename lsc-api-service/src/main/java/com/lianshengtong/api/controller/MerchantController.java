package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/merchant")
public class MerchantController {

    @GetMapping("/list")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status) {
        List<Map<String, Object>> filtered = MockData.merchants.stream().filter(m -> {
            if (keyword != null && !keyword.isEmpty()) {
                String name = (String) m.get("merchantName");
                if (name == null || !name.contains(keyword)) return false;
            }
            if (status != null && !status.equals(m.get("auditStatus"))) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable int id) {
        return MockData.merchants.stream().filter(m -> ((Integer) m.get("id")) == id)
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("商家不存在"));
    }

    @GetMapping("/audit/list")
    public ApiResponse<PageResult<Map<String, Object>>> auditList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> pending = MockData.merchants.stream()
                .filter(m -> m.get("auditStatus").equals(0))
                .collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(pending, page, size));
    }

    @PostMapping("/audit")
    public ApiResponse<Void> audit(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/credit")
    public ApiResponse<Map<String, Object>> credit(@PathVariable int id) {
        return MockData.merchants.stream().filter(m -> ((Integer) m.get("id")) == id)
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("商家不存在"));
    }

    @PostMapping("/{id}/credit/adjust")
    public ApiResponse<Void> creditAdjust(@PathVariable int id, @RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @PostMapping("/{id}/penalty")
    public ApiResponse<Void> penalty(@PathVariable int id, @RequestBody Map<String, Object> body) {
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
        for (Map<String, Object> m : MockData.merchants) {
            list.add(mapOf("id", m.get("id"), "merchantId", m.get("id"),
                    "province", m.get("province"), "city", m.get("city"),
                    "district", m.get("district"), "addressDetail", m.get("addressDetail"),
                    "longitude", m.get("longitude"), "latitude", m.get("latitude"),
                    "aiAddressVerified", m.get("aiAddressVerified")));
        }
        return ApiResponse.success(list);
    }

    @GetMapping("/store/addresses/update-state")
    public ApiResponse<Map<String, Object>> addressUpdateState() {
        return ApiResponse.success(mapOf("count", 3, "needReview", true));
    }

    @DeleteMapping("/store/addresses/{id}")
    public ApiResponse<Void> deleteAddress(@PathVariable int id) {
        return ApiResponse.success(null);
    }

    private Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) m.put((String) kvs[i], kvs[i + 1]);
        return m;
    }
}
