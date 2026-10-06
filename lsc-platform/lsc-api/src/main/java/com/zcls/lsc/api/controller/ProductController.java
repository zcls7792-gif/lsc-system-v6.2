package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import com.zcls.lsc.product.PriceVersionService;
import com.zcls.lsc.product.PriceVersionService.PriceVersionRow;
import com.zcls.lsc.product.ProductAuditService;
import com.zcls.lsc.product.ProductAuditService.AuditRecordRow;
import com.zcls.lsc.product.ProductService;
import com.zcls.lsc.product.ProductService.ProductRow;
import com.zcls.lsc.product.SkuService;
import com.zcls.lsc.product.SkuService.SkuRow;
import com.zcls.lsc.product.enums.ProductEnums.AuditDecision;
import com.zcls.lsc.product.enums.ProductEnums.ProductStatus;
import com.zcls.lsc.product.enums.ProductEnums.SaleUnit;
import com.zcls.lsc.product.enums.ProductEnums.SkuStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 第13章 商品主数据接口（管理端 + 商户端）。
 *
 *  - 商品 CRUD 与状态机（草稿/审核/上架/下架/售罄）
 *  - SKU 规格、箱规、最小起订量管理
 *  - 价格版本（成本密文 + 双签 + 生效时间 + 赠送系数）
 *  - 商品审核记录
 */
@RestController
@RequestMapping("/v1")
public class ProductController {

    private final ProductService productService;
    private final SkuService skuService;
    private final PriceVersionService priceVersionService;
    private final ProductAuditService productAuditService;

    public ProductController(ProductService productService, SkuService skuService,
                            PriceVersionService priceVersionService,
                            ProductAuditService productAuditService) {
        this.productService = productService;
        this.skuService = skuService;
        this.priceVersionService = priceVersionService;
        this.productAuditService = productAuditService;
    }

    // ===== 商品 =====

    // ===== C 端公开接口（只读，无需鉴权） =====

    /** C 端商品列表（仅 ON_SALE）。 */
    @GetMapping("/products")
    public ApiResponse<List<ProductRow>> listOnSaleProducts(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") long offset) {
        return ApiResponse.ok(productService.listByStatus(ProductStatus.ON_SALE, limit, offset));
    }

    /** C 端商品详情。 */
    @GetMapping("/products/{id}")
    public ApiResponse<ProductRow> getProductPublic(@PathVariable long id) {
        return ApiResponse.ok(productService.getProduct(id));
    }

    /** C 端查询商品下的 SKU 列表。 */
    @GetMapping("/products/{productId}/skus")
    public ApiResponse<List<SkuRow>> listSkusByProductPublic(@PathVariable long productId) {
        return ApiResponse.ok(skuService.listByProduct(productId));
    }

    /** C 端查询 SKU 当前生效价格版本。 */
    @GetMapping("/skus/{skuId}/price")
    public ApiResponse<PriceVersionRow> getSkuActivePrice(@PathVariable long skuId) {
        return ApiResponse.ok(priceVersionService.getActivePriceVersion(skuId));
    }

    @PostMapping("/admin/products")
    public ApiResponse<Long> createProductAdmin(
            @RequestParam long sellerEntityId,
            @RequestParam String name,
            @RequestParam long categoryId,
            @RequestParam String returnPolicyVersion,
            @RequestParam(required = false) String descriptionRef) {
        return ApiResponse.ok(productService.createProduct(sellerEntityId, name, categoryId,
                returnPolicyVersion, descriptionRef));
    }

    @PutMapping("/admin/products/{id}")
    public ApiResponse<Void> updateProduct(
            @PathVariable long id,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String descriptionRef) {
        productService.updateProduct(id, name, descriptionRef);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/products/{id}")
    public ApiResponse<ProductRow> getProduct(@PathVariable long id) {
        return ApiResponse.ok(productService.getProduct(id));
    }

    @GetMapping("/admin/products")
    public ApiResponse<List<ProductRow>> listProducts(
            @RequestParam String status,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") long offset) {
        return ApiResponse.ok(productService.listByStatus(ProductStatus.valueOf(status), limit, offset));
    }

    @PostMapping("/admin/products/{id}/submit-review")
    public ApiResponse<Long> submitReview(@PathVariable long id, @RequestParam long applicantId) {
        return ApiResponse.ok(productAuditService.submitReview(id, applicantId));
    }

    @PostMapping("/admin/products/{id}/off-sale")
    public ApiResponse<Void> offSale(@PathVariable long id) {
        productService.offSale(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/products/{id}/on-sale")
    public ApiResponse<Void> onSale(@PathVariable long id) {
        productService.onSale(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/admin/products/{id}/sold-out")
    public ApiResponse<Void> markSoldOut(@PathVariable long id) {
        productService.markSoldOut(id);
        return ApiResponse.ok(null);
    }

    // ===== SKU =====

    @PostMapping("/admin/skus")
    public ApiResponse<Long> createSku(
            @RequestParam long productId,
            @RequestParam String skuCode,
            @RequestParam(required = false) String specJson,
            @RequestParam(defaultValue = "PIECE") String saleUnit,
            @RequestParam(defaultValue = "1") int packQty,
            @RequestParam(defaultValue = "1") int bMinQty) {
        return ApiResponse.ok(skuService.createSku(productId, skuCode, specJson,
                SaleUnit.valueOf(saleUnit), packQty, bMinQty));
    }

    @PutMapping("/admin/skus/{id}")
    public ApiResponse<Void> updateSku(
            @PathVariable long id,
            @RequestParam(required = false) String specJson,
            @RequestParam(required = false) String saleUnit,
            @RequestParam(required = false) Integer packQty,
            @RequestParam(required = false) Integer bMinQty) {
        skuService.updateSku(id, specJson,
                saleUnit == null ? null : SaleUnit.valueOf(saleUnit), packQty, bMinQty);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/skus/{id}")
    public ApiResponse<SkuRow> getSku(@PathVariable long id) {
        return ApiResponse.ok(skuService.getSku(id));
    }

    @GetMapping("/admin/products/{productId}/skus")
    public ApiResponse<List<SkuRow>> listSkusByProduct(@PathVariable long productId) {
        return ApiResponse.ok(skuService.listByProduct(productId));
    }

    @PostMapping("/admin/skus/{id}/status")
    public ApiResponse<Void> updateSkuStatus(@PathVariable long id, @RequestParam String status) {
        skuService.updateStatus(id, SkuStatus.valueOf(status));
        return ApiResponse.ok(null);
    }

    // ===== 价格版本 =====

    @PostMapping("/admin/price-versions")
    public ApiResponse<Long> createPriceVersion(
            @RequestParam long skuId,
            @RequestParam long retailPriceCent,
            @RequestParam long bPriceCent,
            @RequestParam long costPriceCent,
            @RequestParam long grantCoefPpm,
            @RequestParam(defaultValue = "0") long grantCUnit,
            @RequestParam(defaultValue = "0") long grantBUnit,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime effectiveAt,
            @RequestParam long approvedBy,
            @RequestParam long requesterId) {
        return ApiResponse.ok(priceVersionService.createPriceVersion(skuId, retailPriceCent,
                bPriceCent, costPriceCent, grantCoefPpm, grantCUnit, grantBUnit,
                effectiveAt, approvedBy, requesterId));
    }

    @GetMapping("/admin/skus/{skuId}/price-versions/active")
    public ApiResponse<PriceVersionRow> getActivePriceVersion(@PathVariable long skuId) {
        return ApiResponse.ok(priceVersionService.getActivePriceVersion(skuId));
    }

    @GetMapping("/admin/skus/{skuId}/price-versions")
    public ApiResponse<List<PriceVersionRow>> listPriceVersions(@PathVariable long skuId) {
        return ApiResponse.ok(priceVersionService.listPriceVersions(skuId));
    }

    @GetMapping("/admin/price-versions/{priceVersion}/cost")
    public ApiResponse<Long> decryptCost(@PathVariable long priceVersion) {
        // 管理端解密成本价（需权限控制）
        PriceVersionRow row = priceVersionService.getPriceVersion(priceVersion);
        if (row == null) {
            return ApiResponse.fail(40400, "price version not found");
        }
        return ApiResponse.ok(priceVersionService.decryptCost(row.costPriceEnc(), row.costKeyVersion()));
    }

    // ===== 商品审核 =====

    @PostMapping("/admin/audits/{auditId}/review")
    public ApiResponse<Void> reviewProduct(
            @PathVariable long auditId,
            @RequestParam long reviewerId,
            @RequestParam String decision,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String evidenceRef) {
        productAuditService.review(auditId, reviewerId, AuditDecision.valueOf(decision),
                reason, evidenceRef);
        return ApiResponse.ok(null);
    }

    @GetMapping("/admin/products/{productId}/audits")
    public ApiResponse<List<AuditRecordRow>> listAuditHistory(@PathVariable long productId) {
        return ApiResponse.ok(productAuditService.listAuditHistory(productId));
    }
}
