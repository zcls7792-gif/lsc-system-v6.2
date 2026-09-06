package com.lianshengtong.lsc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.common.R;
import com.lianshengtong.lsc.entity.Product;
import com.lianshengtong.lsc.security.UserContext;
import com.lianshengtong.lsc.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/mall")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/products")
    public R<Page<Product>> list(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(productService.list(categoryId, keyword, pageNo, pageSize));
    }

    @GetMapping("/products/{id}")
    public R<Product> detail(@PathVariable Long id) {
        return R.ok(productService.detail(id));
    }

    @PostMapping("/products")
    public R<Product> create(@RequestBody Map<String, Object> body) {
        Long merchantId = UserContext.getUserId();
        return R.ok(productService.create(merchantId,
                (String) body.get("productName"),
                (String) body.get("productDesc"),
                new BigDecimal(body.get("price").toString()),
                Long.valueOf(body.get("lscPrice").toString()),
                body.get("stock") != null ? Integer.valueOf(body.get("stock").toString()) : 0));
    }

    @PutMapping("/products/{id}/status")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        productService.updateStatus(id, Integer.valueOf(body.get("status").toString()));
        return R.ok();
    }
}
