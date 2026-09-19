package com.lianshengtong.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.enums.AiReviewResultEnum;
import com.lianshengtong.common.enums.ProductStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.mall.constant.MallConstants;
import com.lianshengtong.mall.dto.ProductPublishDTO;
import com.lianshengtong.mall.entity.Product;
import com.lianshengtong.mall.entity.ProductCategory;
import com.lianshengtong.mall.feign.AiGatewayFeignClient;
import com.lianshengtong.mall.mapper.ProductCategoryMapper;
import com.lianshengtong.mall.mapper.ProductMapper;
import com.lianshengtong.mall.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 商品服务实现
 * <p>人民币价格与 LSC 价格强制一致(共用 price 字段，1:1)。
 * 发布后异步提交 AI 审核，AI 通过或人工通过方可上架。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductMapper productMapper;
    private final ProductCategoryMapper productCategoryMapper;
    private final AiGatewayFeignClient aiGatewayFeignClient;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long publishProduct(ProductPublishDTO dto) {
        Product product = new Product();
        product.setMerchantId(dto.getMerchantId());
        product.setCategoryId(dto.getCategoryId());
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setMainImage(dto.getMainImage());
        // 人民币价格 = LSC 价格，共用 price 字段(1:1)
        product.setPrice(dto.getPrice());
        product.setCostPrice(dto.getCostPrice());
        // V7.3: 赠送 LSC 数量，未提供则按进销差自动计算，硬上限不超过售价
        product.setGrantPoints(resolveGrantPoints(dto.getPrice(), dto.getCostPrice(), dto.getGrantPoints()));
        product.setStock(dto.getStock());
        product.setStatus(ProductStatusEnum.UNDER_REVIEW.getCode());
        product.setAiReview(AiReviewResultEnum.NOT_REVIEWED.getCode());
        product.setSalesCount(0L);
        productMapper.insert(product);
        log.info("商品发布成功 id={} merchantId={} grantPoints={}", product.getId(), product.getMerchantId(), product.getGrantPoints());

        // 异步提交 AI 审核
        try {
            R<String> resp = aiGatewayFeignClient.submitProductReview(product.getId(),
                    product.getName(), product.getDescription(), product.getMainImage());
            if (resp == null || !resp.isSuccess()) {
                log.warn("AI审核任务提交失败 productId={}", product.getId());
            }
        } catch (RuntimeException e) {
            log.error("AI审核任务提交异常 productId={}", product.getId(), e);
        }
        return product.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProduct(Long id, ProductPublishDTO dto) {
        Product product = getProduct(id);
        product.setCategoryId(dto.getCategoryId());
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setMainImage(dto.getMainImage());
        product.setPrice(dto.getPrice());
        product.setCostPrice(dto.getCostPrice());
        product.setGrantPoints(resolveGrantPoints(dto.getPrice(), dto.getCostPrice(), dto.getGrantPoints()));
        product.setStock(dto.getStock());
        productMapper.updateById(product);
        log.info("商品更新成功 id={} grantPoints={}", id, product.getGrantPoints());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void offShelf(Long id) {
        Product product = getProduct(id);
        product.setStatus(ProductStatusEnum.OFF_SHELF.getCode());
        productMapper.updateById(product);
        log.info("商品下架 id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onShelf(Long id) {
        Product product = getProduct(id);
        // 上架需 AI 通过 或 人工通过
        if (product.getAiReview() == null
                || (product.getAiReview() != AiReviewResultEnum.AI_PASS.getCode()
                && product.getAiReview() != AiReviewResultEnum.MANUAL_PASS.getCode())) {
            throw new BizException("商品未通过审核，不可上架");
        }
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        productMapper.updateById(product);
        log.info("商品上架 id={}", id);
    }

    @Override
    public IPage<Product> listProducts(Integer page, Integer size, Long categoryId, Integer status) {
        Page<Product> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        wrapper.orderByDesc(Product::getCreatedAt);
        return productMapper.selectPage(p, wrapper);
    }

    @Override
    public IPage<Product> listProductsAdmin(Integer page, Integer size, String keyword, Long merchantId, Integer status) {
        Page<Product> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Product::getName, keyword);
        }
        if (merchantId != null) {
            wrapper.eq(Product::getMerchantId, merchantId);
        }
        if (status != null) {
            wrapper.eq(Product::getStatus, status);
        }
        wrapper.orderByDesc(Product::getCreatedAt);
        return productMapper.selectPage(p, wrapper);
    }

    @Override
    public IPage<Product> auditList(Integer page, Integer size, Integer aiReview) {
        Page<Product> p = new Page<>(page == null ? 1 : page, size == null ? 20 : size);
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        // 默认查询 AI 可疑(aiReview=2) 的商品
        wrapper.eq(Product::getAiReview, aiReview == null ? AiReviewResultEnum.AI_SUSPICIOUS.getCode() : aiReview);
        wrapper.orderByDesc(Product::getCreatedAt);
        return productMapper.selectPage(p, wrapper);
    }

    @Override
    public Product getAiReviewResult(Long productId) {
        return getProduct(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void manualReview(Long productId, Boolean pass, String reason) {
        Integer aiReview = Boolean.TRUE.equals(pass)
                ? AiReviewResultEnum.MANUAL_PASS.getCode()
                : AiReviewResultEnum.MANUAL_REJECT.getCode();
        updateAiReview(productId, aiReview, reason);
        log.info("商品人工复核 productId={} pass={} reason={}", productId, pass, reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStatus(Long productId, Integer status) {
        Product product = getProduct(productId);
        if (status != null && status == ProductStatusEnum.ON_SHELF.getCode()) {
            // 上架需 AI 通过 或 人工通过
            if (product.getAiReview() == null
                    || (product.getAiReview() != AiReviewResultEnum.AI_PASS.getCode()
                    && product.getAiReview() != AiReviewResultEnum.MANUAL_PASS.getCode())) {
                throw new BizException("商品未通过审核，不可上架");
            }
            product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        } else {
            product.setStatus(ProductStatusEnum.OFF_SHELF.getCode());
        }
        productMapper.updateById(product);
        log.info("商品状态切换 productId={} status={}", productId, product.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAiReview(Long productId, Integer aiReview, String remark) {
        Product product = getProduct(productId);
        product.setAiReview(aiReview);
        product.setAiReviewRemark(remark);
        // AI 可疑或拒绝 -> 自动下架
        if (AiReviewResultEnum.AI_SUSPICIOUS.getCode() == aiReview
                || AiReviewResultEnum.MANUAL_REJECT.getCode() == aiReview) {
            product.setStatus(ProductStatusEnum.OFF_SHELF.getCode());
        }
        productMapper.updateById(product);
        log.info("AI审核结果回调 productId={} aiReview={}", productId, aiReview);
    }

    private Product getProduct(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw new BizException(404, "商品不存在");
        }
        return product;
    }

    /**
     * 解析商品赠送 LSC 数量 (V7.3)
     * <p>
     * 规则：
     * <ol>
     *   <li>商家手动传入 grantPoints 时，校验不超过售价(MAX_GRANT_RATIO=100%)，超过则抛异常</li>
     *   <li>未传入时，按进销差自动计算：grantPoints = floor(price - costPrice)</li>
     *   <li>自动计算结果同样受 MAX_GRANT_RATIO 上限约束(进销差不可能超过售价，理论上不会触发)</li>
     *   <li>costPrice 为空或为 0 时，自动计算结果为 0（无利润则不赠送）</li>
     * </ol>
     *
     * @param price       售价(元)
     * @param costPrice   成本价(元，可空)
     * @param grantPoints 商家手动指定的赠送数量(可空)
     * @return 最终赠送 LSC 数量
     */
    private Long resolveGrantPoints(BigDecimal price, BigDecimal costPrice, Long grantPoints) {
        // price 合法性由 DTO 校验层(@NotNull, @DecimalMin)保证；此处防御性处理
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return grantPoints != null ? grantPoints : 0L;
        }
        long maxGrant = price.multiply(MallConstants.MAX_GRANT_RATIO)
                .setScale(0, RoundingMode.FLOOR).longValue();

        // 商家手动指定
        if (grantPoints != null) {
            if (grantPoints > maxGrant) {
                throw new BizException(400,
                        "赠送LSC数量(" + grantPoints + ")不可超过售价(" + maxGrant + ")");
            }
            return grantPoints;
        }

        // 自动按进销差计算
        if (costPrice == null || costPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return 0L;
        }
        long autoGrant = price.subtract(costPrice)
                .setScale(0, RoundingMode.FLOOR).longValue();
        if (autoGrant < 0) {
            return 0L;
        }
        return Math.min(autoGrant, maxGrant);
    }

    @Override
    public Product getProductDetail(Long id) {
        return getProduct(id);
    }

    @Override
    public java.util.List<ProductCategory> listAllCategories() {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(ProductCategory::getSort);
        return productCategoryMapper.selectList(wrapper);
    }

    @Override
    public java.util.List<ProductCategory> listSubCategories(Long parentId) {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductCategory::getParentId, parentId == null ? 0 : parentId)
                .orderByAsc(ProductCategory::getSort);
        return productCategoryMapper.selectList(wrapper);
    }
}
