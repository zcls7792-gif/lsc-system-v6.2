package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @GetMapping("/list")
    public ApiResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Integer merchantId) {
        List<Map<String, Object>> filtered = MockData.products.stream().filter(p -> {
            if (keyword != null && !keyword.isEmpty()) {
                String name = (String) p.get("productName");
                if (name == null || !name.contains(keyword)) return false;
            }
            if (status != null && !status.equals(p.get("status"))) return false;
            if (categoryId != null && !categoryId.equals(p.get("categoryId"))) return false;
            if (merchantId != null && !merchantId.equals(p.get("merchantId"))) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable int id) {
        return MockData.products.stream().filter(p -> ((Integer) p.get("id")) == id)
                .findFirst().map(ApiResponse::success).orElse(ApiResponse.fail("商品不存在"));
    }

    @GetMapping("/detail")
    public ApiResponse<Map<String, Object>> detailByParam(@RequestParam int id) {
        return detail(id);
    }

    @GetMapping("/hot")
    public ApiResponse<List<Map<String, Object>>> hot() {
        return ApiResponse.success(MockData.products.stream()
                .sorted((a, b) -> ((Integer) b.get("sales")).compareTo((Integer) a.get("sales")))
                .limit(6).collect(Collectors.toList()));
    }

    @GetMapping("/recommend")
    public ApiResponse<List<Map<String, Object>>> recommend() {
        return ApiResponse.success(MockData.products.stream()
                .filter(p -> p.get("status").equals(1))
                .limit(8).collect(Collectors.toList()));
    }

    @GetMapping("/search")
    public ApiResponse<PageResult<Map<String, Object>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return list(page, size, keyword, null, null, null);
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        return ApiResponse.success(MockData.categories);
    }

    @GetMapping("/categories/{parentId}")
    public ApiResponse<List<Map<String, Object>>> categoriesByParent(@PathVariable int parentId) {
        return ApiResponse.success(MockData.categories);
    }

    @GetMapping("/banners")
    public ApiResponse<List<Map<String, Object>>> banners() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(mapOf("id", 1, "image", "https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=" +
                java.net.URLEncoder.encode("LSC消费权益平台 banner 活动促销") + "&image_size=landscape_16_9",
                "link", "/pages/product/detail?id=2001"));
        list.add(mapOf("id", 2, "image", "https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=" +
                java.net.URLEncoder.encode("有机食品产地直供 banner") + "&image_size=landscape_16_9",
                "link", "/pages/product/detail?id=2002"));
        return ApiResponse.success(list);
    }

    @GetMapping("/stores/nearby")
    public ApiResponse<List<Map<String, Object>>> nearbyStores(
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double lat) {
        return ApiResponse.success(MockData.merchants.stream().limit(5).collect(Collectors.toList()));
    }

    @GetMapping("/audit/list")
    public ApiResponse<PageResult<Map<String, Object>>> auditList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Map<String, Object>> pending = MockData.products.stream()
                .filter(p -> p.get("status").equals(2))
                .collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(pending, page, size));
    }

    @PostMapping("/audit")
    public ApiResponse<Void> audit(@RequestBody Map<String, Object> body) {
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/ai-review")
    public ApiResponse<Map<String, Object>> aiReview(@PathVariable int id) {
        Map<String, Object> r = mapOf("result", 1, "tags", List.of("图片合规", "文本无敏感词"),
                "score", 95, "suggestion", "商品信息合规");
        return ApiResponse.success(r);
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        body.put("id", 3000 + MockData.products.size());
        body.put("status", 2);
        MockData.products.add(0, body);
        return ApiResponse.success(body);
    }

    @PutMapping("/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable int id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        return ApiResponse.success(body);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable int id) {
        MockData.products.removeIf(p -> ((Integer) p.get("id")) == id);
        return ApiResponse.success(null);
    }

    private Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) m.put((String) kvs[i], kvs[i + 1]);
        return m;
    }
}
