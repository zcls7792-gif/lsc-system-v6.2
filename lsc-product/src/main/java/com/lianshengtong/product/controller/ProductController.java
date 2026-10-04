package com.lianshengtong.product.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.result.R;
import com.lianshengtong.product.entity.Product;
import com.lianshengtong.product.entity.ProductPriceVersion;
import com.lianshengtong.product.entity.ProductSku;
import com.lianshengtong.product.mapper.ProductMapper;
import com.lianshengtong.product.mapper.ProductPriceVersionMapper;
import com.lianshengtong.product.mapper.ProductSkuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品接口（V7.7.2 第三章 3.2）
 * <p>
 * C端白名单不含B价、成本及内部系数；B端可查看采购价和零售价。
 * </p>
 */
@RestController
@RequestMapping("/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductMapper productMapper;
    private final ProductSkuMapper skuMapper;
    private final ProductPriceVersionMapper priceMapper;

    /** 商品列表（C端：仅零售价，含首个SKU价格） */
    @GetMapping
    public R<IPage<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String buyerType) {
        Page<Product> p = new Page<>(page, size);
        LambdaQueryWrapper<Product> w = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "ON_SALE")
                .orderByDesc(Product::getProductId);
        IPage<Product> products = productMapper.selectPage(p, w);

        IPage<Map<String, Object>> result = new Page<>(products.getCurrent(), products.getSize(), products.getTotal());
        result.setRecords(products.getRecords().stream().map(prod -> {
            Map<String, Object> m = new HashMap<>();
            m.put("productId", prod.getProductId());
            m.put("name", prod.getName());
            m.put("categoryId", prod.getCategoryId());
            m.put("status", prod.getStatus());
            // 查询首个SKU的价格
            List<ProductSku> skus = skuMapper.selectList(
                    new LambdaQueryWrapper<ProductSku>()
                            .eq(ProductSku::getProductId, prod.getProductId())
                            .last("LIMIT 1"));
            if (!skus.isEmpty()) {
                ProductPriceVersion pv = priceMapper.selectLatestEffective(skus.get(0).getSkuId());
                if (pv != null) {
                    m.put("retailPriceCent", pv.getRetailPriceCent());
                    m.put("grantCoefficientPpm", pv.getGrantCoefficientPpm());
                    if ("B".equals(buyerType)) {
                        m.put("bPriceCent", pv.getBPriceCent());
                    }
                }
            }
            return m;
        }).toList());
        return R.ok(result);
    }

    /** 商品详情 + SKU + 价格版本 */
    @GetMapping("/{productId}")
    public R<Map<String, Object>> detail(@PathVariable Long productId,
                                          @RequestParam(defaultValue = "C") String buyerType) {
        Product product = productMapper.selectById(productId);
        if (product == null) return R.fail(404, "商品不存在");

        List<ProductSku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<ProductSku>().eq(ProductSku::getProductId, productId));

        Map<String, Object> data = new HashMap<>();
        data.put("product", product);
        data.put("skus", skus.stream().map(sku -> {
            Map<String, Object> sm = new HashMap<>();
            sm.put("skuId", sku.getSkuId());
            sm.put("skuCode", sku.getSkuCode());
            sm.put("specJson", sku.getSpecJson());
            ProductPriceVersion pv = priceMapper.selectLatestEffective(sku.getSkuId());
            if (pv != null) {
                sm.put("retailPriceCent", pv.getRetailPriceCent());
                if ("B".equals(buyerType)) {
                    sm.put("bPriceCent", pv.getBPriceCent());
                }
                sm.put("grantCoefficientPpm", pv.getGrantCoefficientPpm());
            }
            return sm;
        }).toList());
        return R.ok(data);
    }
}
