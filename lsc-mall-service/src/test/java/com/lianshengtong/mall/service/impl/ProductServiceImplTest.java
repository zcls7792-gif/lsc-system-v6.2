package com.lianshengtong.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.enums.AiReviewResultEnum;
import com.lianshengtong.common.enums.ProductStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.mall.dto.ProductPublishDTO;
import com.lianshengtong.mall.entity.Product;
import com.lianshengtong.mall.entity.ProductCategory;
import com.lianshengtong.mall.feign.AiGatewayFeignClient;
import com.lianshengtong.mall.mapper.ProductCategoryMapper;
import com.lianshengtong.mall.mapper.ProductMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("商品服务单元测试")
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductCategoryMapper productCategoryMapper;
    @Mock
    private AiGatewayFeignClient aiGatewayFeignClient;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product createMockProduct() {
        Product p = new Product();
        p.setId(1L);
        p.setMerchantId(100L);
        p.setCategoryId(10L);
        p.setName("测试商品");
        p.setDescription("商品描述");
        p.setMainImage("https://cdn.example.com/img.jpg");
        p.setPrice(new BigDecimal("99.99"));
        p.setStock(100);
        p.setStatus(ProductStatusEnum.UNDER_REVIEW.getCode());
        p.setAiReview(AiReviewResultEnum.AI_PASS.getCode());
        p.setAiReviewRemark("AI审核通过");
        p.setSalesCount(0L);
        p.setVersion(1);
        return p;
    }

    private ProductPublishDTO createPublishDTO() {
        ProductPublishDTO dto = new ProductPublishDTO();
        dto.setMerchantId(100L);
        dto.setCategoryId(10L);
        dto.setName("新商品");
        dto.setDescription("商品描述");
        dto.setMainImage("https://cdn.example.com/img.jpg");
        dto.setPrice(new BigDecimal("99.99"));
        dto.setStock(100);
        return dto;
    }

    // ============== publishProduct 测试 ==============

    @Test
    @DisplayName("publishProduct: 成功发布商品")
    void publishProduct_success() {
        ProductPublishDTO dto = createPublishDTO();
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
        verify(productMapper).insert(any(Product.class));
    }

    @Test
    @DisplayName("publishProduct: AI审核提交失败不影响发布")
    void publishProduct_aiSubmitFail_stillPublished() {
        ProductPublishDTO dto = createPublishDTO();
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(2L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("AI服务不可用"));

        Long productId = productService.publishProduct(dto);

        assertEquals(2L, productId.longValue());
    }

    // ============== offShelf 测试 ==============

    @Test
    @DisplayName("offShelf: 成功下架")
    void offShelf_success() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.offShelf(1L));
        assertEquals(ProductStatusEnum.OFF_SHELF.getCode(), product.getStatus());
    }

    // ============== onShelf 测试 ==============

    @Test
    @DisplayName("onShelf: AI审核通过可上架")
    void onShelf_aiPass_success() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.AI_PASS.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.onShelf(1L));
        assertEquals(ProductStatusEnum.ON_SHELF.getCode(), product.getStatus());
    }

    @Test
    @DisplayName("onShelf: 人工审核通过可上架")
    void onShelf_manualPass_success() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.MANUAL_PASS.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.onShelf(1L));
    }

    @Test
    @DisplayName("onShelf: AI可疑不可上架抛异常")
    void onShelf_aiSuspicious_throws() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.AI_SUSPICIOUS.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class, () -> productService.onShelf(1L));
    }

    @Test
    @DisplayName("onShelf: 未审核不可上架")
    void onShelf_notReviewed_throws() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.NOT_REVIEWED.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class, () -> productService.onShelf(1L));
    }

    // ============== manualReview 测试 ==============

    @Test
    @DisplayName("manualReview: 人工通过")
    void manualReview_pass_success() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.manualReview(1L, true, "确认通过"));
        assertEquals(AiReviewResultEnum.MANUAL_PASS.getCode(), product.getAiReview());
    }

    @Test
    @DisplayName("manualReview: 人工拒绝自动下架")
    void manualReview_reject_autoOffShelf() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.manualReview(1L, false, "虚假商品"));
        assertEquals(AiReviewResultEnum.MANUAL_REJECT.getCode(), product.getAiReview());
        assertEquals(ProductStatusEnum.OFF_SHELF.getCode(), product.getStatus());
    }

    // ============== toggleStatus 测试 ==============

    @Test
    @DisplayName("toggleStatus: 下架成功")
    void toggleStatus_offShelf_success() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.toggleStatus(1L, ProductStatusEnum.OFF_SHELF.getCode()));
    }

    // ============== listProducts 测试 ==============

    @Test
    @DisplayName("listProducts: 分页查询默认值")
    void listProducts_defaultPage() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProducts(null, null, null, null);

        assertNotNull(result);
    }

    // ============== getProductDetail 测试 ==============

    @Test
    @DisplayName("getProductDetail: 商品不存在抛异常")
    void getProductDetail_notFound_throws() {
        when(productMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> productService.getProductDetail(999L));
    }

    // ============== listAllCategories 测试 ==============

    @Test
    @DisplayName("listAllCategories: 返回分类列表")
    void listAllCategories_success() {
        List<ProductCategory> categories = new ArrayList<>();
        ProductCategory c1 = new ProductCategory();
        c1.setId(1L);
        c1.setName("电子产品");
        categories.add(c1);
        when(productCategoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(categories);

        List<ProductCategory> result = productService.listAllCategories();

        assertEquals(1, result.size());
    }

    // ============== updateAiReview 测试 ==============

    @Test
    @DisplayName("updateAiReview: AI可疑自动下架")
    void updateAiReview_suspicious_autoOffShelf() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.updateAiReview(1L, AiReviewResultEnum.AI_SUSPICIOUS.getCode(), "检测到敏感词");

        assertEquals(ProductStatusEnum.OFF_SHELF.getCode(), product.getStatus());
    }
}
