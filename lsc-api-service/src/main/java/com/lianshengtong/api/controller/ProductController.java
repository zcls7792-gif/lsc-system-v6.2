package com.lianshengtong.api.controller;

import com.lianshengtong.api.data.MockData;
import com.lianshengtong.api.dto.ApiResponse;
import com.lianshengtong.api.dto.PageResult;
import com.lianshengtong.api.entity.Product;
import com.lianshengtong.api.repository.ProductRepository;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    private final ProductRepository productRepo;

    public ProductController(ProductRepository productRepo) {
        this.productRepo = productRepo;
    }

    @GetMapping("/list")
    public ApiResponse<PageResult<Product>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) Long merchantId) {
        List<Product> filtered = productRepo.findAll().stream().filter(p -> {
            if (keyword != null && !keyword.isEmpty()) {
                if (p.getProductName() == null || !p.getProductName().contains(keyword)) return false;
            }
            if (status != null && !status.equals(p.getStatus())) return false;
            if (categoryId != null && !categoryId.equals(p.getCategoryId())) return false;
            if (merchantId != null && !merchantId.equals(p.getMerchantId())) return false;
            return true;
        }).collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(filtered, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> detail(@PathVariable long id) {
        return productRepo.findById(id)
                .map(ApiResponse::success)
                .orElse(ApiResponse.fail("商品不存在"));
    }

    @GetMapping("/detail")
    public ApiResponse<Product> detailByParam(@RequestParam long id) {
        return detail(id);
    }

    @GetMapping("/hot")
    public ApiResponse<List<Product>> hot() {
        return ApiResponse.success(productRepo.findAll().stream()
                .sorted((a, b) -> {
                    int sa = a.getSales() == null ? 0 : a.getSales();
                    int sb = b.getSales() == null ? 0 : b.getSales();
                    return Integer.compare(sb, sa);
                })
                .limit(6)
                .collect(Collectors.toList()));
    }

    @GetMapping("/recommend")
    public ApiResponse<List<Product>> recommend() {
        return ApiResponse.success(productRepo.findAll().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 1)
                .limit(8)
                .collect(Collectors.toList()));
    }

    @GetMapping("/search")
    public ApiResponse<PageResult<Product>> search(
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
        // 附近门店返回商家摘要，数据源沿用 MockData（未持久化的表）
        return ApiResponse.success(MockData.merchants.stream().limit(5).collect(Collectors.toList()));
    }

    @GetMapping("/audit/list")
    public ApiResponse<PageResult<Product>> auditList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<Product> pending = productRepo.findAll().stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == 2)
                .collect(Collectors.toList());
        return ApiResponse.success(PageResult.of(pending, page, size));
    }

    /** 商品审核落库：更新 status */
    @PostMapping("/audit")
    public ApiResponse<Void> audit(@RequestBody Map<String, Object> body) {
        Object idObj = body.get("id");
        Object statusObj = body.get("status");
        if (idObj == null) return ApiResponse.fail("缺少 id");
        long id = Long.parseLong(idObj.toString());
        productRepo.findById(id).ifPresent(p -> {
            if (statusObj != null) p.setStatus(Integer.parseInt(statusObj.toString()));
            productRepo.save(p);
        });
        return ApiResponse.success(null);
    }

    @GetMapping("/{id}/ai-review")
    public ApiResponse<Map<String, Object>> aiReview(@PathVariable long id) {
        Map<String, Object> r = mapOf("result", 1, "tags", List.of("图片合规", "文本无敏感词"),
                "score", 95, "suggestion", "商品信息合规");
        return ApiResponse.success(r);
    }

    /** 新建商品落库 */
    @PostMapping
    public ApiResponse<Product> create(@RequestBody Product body) {
        long newId = 3000L + (productRepo.count() + 1);
        body.setId(newId);
        if (body.getStatus() == null) body.setStatus(2);
        if (body.getSales() == null) body.setSales(0);
        if (body.getProductImages() == null) body.setProductImages(new ArrayList<>());
        if (body.getImages() == null) body.setImages(new ArrayList<>());
        if (body.getCreatedAt() == null) {
            body.setCreatedAt(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }
        return ApiResponse.success(productRepo.save(body));
    }

    /** 更新商品落库 */
    @PutMapping("/{id}")
    public ApiResponse<Product> update(@PathVariable long id, @RequestBody Product body) {
        body.setId(id);
        return ApiResponse.success(productRepo.save(body));
    }

    /** 删除商品落库 */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        productRepo.deleteById(id);
        return ApiResponse.success(null);
    }

    private Map<String, Object> mapOf(Object... kvs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kvs.length; i += 2) m.put((String) kvs[i], kvs[i + 1]);
        return m;
    }
}
