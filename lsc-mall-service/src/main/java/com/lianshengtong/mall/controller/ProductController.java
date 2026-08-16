package com.lianshengtong.mall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.common.dto.HybridPayDTO;
import com.lianshengtong.common.result.R;
import com.lianshengtong.mall.dto.HybridPayCalcDTO;
import com.lianshengtong.mall.dto.ProductPublishDTO;
import com.lianshengtong.mall.entity.Product;
import com.lianshengtong.mall.service.HybridPayService;
import com.lianshengtong.mall.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 权益商城商品接口
 */
@Tag(name = "权益商城", description = "商品发布/上下架/查询/混合支付计算/AI审核回调")
@RestController
@RequestMapping("/api/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final HybridPayService hybridPayService;

    @Operation(summary = "商品发布")
    @PostMapping("/publish")
    public R<Long> publish(@Valid @RequestBody ProductPublishDTO dto) {
        return R.ok(productService.publishProduct(dto));
    }

    @Operation(summary = "商品更新")
    @PutMapping("/update")
    public R<Void> update(@RequestParam("id") Long id, @Valid @RequestBody ProductPublishDTO dto) {
        productService.updateProduct(id, dto);
        return R.ok();
    }

    @Operation(summary = "商品下架")
    @PostMapping("/off-shelf")
    public R<Void> offShelf(@RequestParam("id") Long id) {
        productService.offShelf(id);
        return R.ok();
    }

    @Operation(summary = "商品上架")
    @PostMapping("/on-shelf")
    public R<Void> onShelf(@RequestParam("id") Long id) {
        productService.onShelf(id);
        return R.ok();
    }

    @Operation(summary = "商品分页列表")
    @GetMapping("/list")
    public R<IPage<Product>> list(@RequestParam(required = false, defaultValue = "1") Integer page,
                                  @RequestParam(required = false, defaultValue = "20") Integer size,
                                  @RequestParam(required = false) Long categoryId,
                                  @RequestParam(required = false) Integer status,
                                  @RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) Long merchantId) {
        // 管理后台传 keyword/merchantId 走 admin 查询，否则走类目查询
        if ((keyword != null && !keyword.isBlank()) || merchantId != null) {
            return R.ok(productService.listProductsAdmin(page, size, keyword, merchantId, status));
        }
        return R.ok(productService.listProducts(page, size, categoryId, status));
    }

    @Operation(summary = "商品详情")
    @GetMapping("/{id}")
    public R<Product> detail(@PathVariable("id") Long id) {
        return R.ok(productService.getProductDetail(id));
    }

    @Operation(summary = "待审核商品列表(AI可疑)")
    @GetMapping("/audit/list")
    public R<IPage<Product>> auditList(@RequestParam(required = false, defaultValue = "1") Integer page,
                                       @RequestParam(required = false, defaultValue = "20") Integer size,
                                       @RequestParam(required = false) Integer aiReview) {
        return R.ok(productService.auditList(page, size, aiReview));
    }

    @Operation(summary = "商品AI审核结果")
    @GetMapping("/{id}/ai-review")
    public R<Product> aiReview(@PathVariable("id") Long id) {
        return R.ok(productService.getAiReviewResult(id));
    }

    @Operation(summary = "人工复核商品")
    @PostMapping("/audit/{id}")
    public R<Void> audit(@PathVariable("id") Long id,
                         @RequestBody Map<String, Object> body) {
        String status = String.valueOf(body.get("status"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));
        Boolean pass = "pass".equalsIgnoreCase(status) || "approved".equalsIgnoreCase(status)
                || "1".equals(status) || "true".equalsIgnoreCase(status);
        productService.manualReview(id, pass, reason);
        return R.ok();
    }

    @Operation(summary = "上架/下架")
    @PostMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable("id") Long id,
                                @RequestBody Map<String, Object> body) {
        Integer status = body.get("status") == null ? null : Integer.parseInt(String.valueOf(body.get("status")));
        productService.toggleStatus(id, status);
        return R.ok();
    }

    @Operation(summary = "AI审核结果回调")
    @PostMapping("/ai-review-callback")
    public R<Void> aiReviewCallback(@RequestParam("productId") Long productId,
                                    @RequestParam("aiReview") Integer aiReview,
                                    @RequestParam(value = "remark", required = false) String remark) {
        productService.updateAiReview(productId, aiReview, remark);
        return R.ok();
    }

    @Operation(summary = "混合支付计算")
    @PostMapping("/hybrid-pay-calc")
    public R<HybridPayDTO> hybridPayCalc(@Valid @RequestBody HybridPayCalcDTO dto) {
        return R.ok(hybridPayService.calc(dto));
    }

    @Operation(summary = "删除商品(软删除:状态置为下架)")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable("id") Long id) {
        productService.offShelf(id);
        return R.ok();
    }

    @Operation(summary = "类目树(含父子)")
    @GetMapping("/categories")
    public R<java.util.List<com.lianshengtong.mall.entity.ProductCategory>> categories() {
        return R.ok(productService.listAllCategories());
    }

    @Operation(summary = "子类目列表")
    @GetMapping("/categories/{parentId}")
    public R<java.util.List<com.lianshengtong.mall.entity.ProductCategory>> subCategories(
            @PathVariable("parentId") Long parentId) {
        return R.ok(productService.listSubCategories(parentId));
    }
}
