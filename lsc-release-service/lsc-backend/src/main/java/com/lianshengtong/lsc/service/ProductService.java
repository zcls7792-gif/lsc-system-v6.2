package com.lianshengtong.lsc.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.lsc.common.BusinessException;
import com.lianshengtong.lsc.common.ErrorCode;
import com.lianshengtong.lsc.entity.Product;
import com.lianshengtong.lsc.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;

    public Page<Product> list(Long categoryId, String keyword, int pageNo, int pageSize) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, 1)
                .orderByDesc(Product::getSales);
        if (keyword != null && !keyword.isEmpty()) {
            qw.like(Product::getProductName, keyword);
        }
        return productMapper.selectPage(new Page<>(pageNo, pageSize), qw);
    }

    public Product detail(Long id) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return p;
    }

    public Product create(Long merchantId, String productName, String productDesc,
                          BigDecimal price, Long lscPrice, Integer stock) {
        // 1:1 价格校验：人民币价必须等于 LSC 价
        if (price.compareTo(BigDecimal.valueOf(lscPrice)) != 0) {
            throw new BusinessException(ErrorCode.PRICE_NOT_MATCH);
        }
        Product p = new Product();
        p.setMerchantId(merchantId);
        p.setProductName(productName);
        p.setProductDesc(productDesc);
        p.setPrice(price);
        p.setLscPrice(lscPrice);
        p.setStock(stock);
        p.setSales(0);
        p.setAiReviewResult(0); // AI 默认通过
        p.setStatus(1);
        p.setCreatedAt(LocalDateTime.now());
        productMapper.insert(p);
        return p;
    }

    public void updateStatus(Long id, Integer status) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        p.setStatus(status);
        productMapper.updateById(p);
    }

    public void aiReview(Long id, Integer aiResult, String aiTags) {
        Product p = productMapper.selectById(id);
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        p.setAiReviewResult(aiResult);
        p.setAiTags(aiTags);
        productMapper.updateById(p);
    }
}
