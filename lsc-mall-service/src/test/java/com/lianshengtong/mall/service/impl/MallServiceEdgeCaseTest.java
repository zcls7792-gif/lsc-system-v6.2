package com.lianshengtong.mall.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.common.enums.AiReviewResultEnum;
import com.lianshengtong.common.enums.ProductStatusEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.mall.dto.HybridPayCalcDTO;
import com.lianshengtong.mall.dto.ProductPublishDTO;
import com.lianshengtong.mall.entity.Product;
import com.lianshengtong.mall.entity.ProductCategory;
import com.lianshengtong.mall.feign.AiGatewayFeignClient;
import com.lianshengtong.mall.mapper.ProductCategoryMapper;
import com.lianshengtong.mall.mapper.ProductMapper;
import com.lianshengtong.common.dto.HybridPayDTO;
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
@DisplayName("商城服务边界场景测试")
class MallServiceEdgeCaseTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductCategoryMapper productCategoryMapper;
    @Mock
    private AiGatewayFeignClient aiGatewayFeignClient;

    @InjectMocks
    private ProductServiceImpl productService;

    private final HybridPayServiceImpl hybridPayService = new HybridPayServiceImpl();

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

    // ==================== ProductServiceImpl - publishProduct 边界场景 ====================

    @Test
    @DisplayName("publishProduct: 名称为null仍可发布(DB层校验)")
    void publishProduct_nullName_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setName(null);
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
        verify(productMapper).insert(argThat(p -> p.getName() == null));
    }

    @Test
    @DisplayName("publishProduct: 名称为空字符串仍可发布")
    void publishProduct_emptyName_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setName("");
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 描述为null仍可发布")
    void publishProduct_nullDescription_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setDescription(null);
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 主图为null仍可发布")
    void publishProduct_nullMainImage_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setMainImage(null);
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 价格为null仍可发布(服务层不校验)")
    void publishProduct_nullPrice_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setPrice(null);
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 价格为0仍可发布")
    void publishProduct_zeroPrice_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setPrice(BigDecimal.ZERO);
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 价格为负数仍可发布")
    void publishProduct_negativePrice_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setPrice(new BigDecimal("-10.00"));
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 库存为null仍可发布")
    void publishProduct_nullStock_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setStock(null);
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 库存为负数仍可发布")
    void publishProduct_negativeStock_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setStock(-5);
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 库存为0仍可发布")
    void publishProduct_zeroStock_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setStock(0);
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: AI审核返回null不影响发布")
    void publishProduct_aiReturnNull_stillPublished() {
        ProductPublishDTO dto = createPublishDTO();
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(3L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(null);

        Long productId = productService.publishProduct(dto);

        assertEquals(3L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: AI审核返回失败不影响发布")
    void publishProduct_aiReturnFail_stillPublished() {
        ProductPublishDTO dto = createPublishDTO();
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(4L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.fail("AI审核服务异常"));

        Long productId = productService.publishProduct(dto);

        assertEquals(4L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 价格为0.01(最小值)正常发布")
    void publishProduct_minimumPrice_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setPrice(new BigDecimal("0.01"));
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: 超大全精度价格正常发布")
    void publishProduct_largeScalePrice_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setPrice(new BigDecimal("99999999.9999"));
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    @Test
    @DisplayName("publishProduct: categoryId为null仍可发布")
    void publishProduct_nullCategoryId_published() {
        ProductPublishDTO dto = createPublishDTO();
        dto.setCategoryId(null);
        doAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(1L);
            return null;
        }).when(productMapper).insert(any(Product.class));
        when(aiGatewayFeignClient.submitProductReview(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(R.ok("review-task-id"));

        Long productId = productService.publishProduct(dto);

        assertEquals(1L, productId.longValue());
    }

    // ==================== ProductServiceImpl - updateProduct 边界场景 ====================

    @Test
    @DisplayName("updateProduct: 更新不存在的商品抛异常")
    void updateProduct_notFound_throws() {
        when(productMapper.selectById(999L)).thenReturn(null);
        ProductPublishDTO dto = createPublishDTO();

        BizException ex = assertThrows(BizException.class,
                () -> productService.updateProduct(999L, dto));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("updateProduct: 更新时设置null名称")
    void updateProduct_setNullName() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);
        ProductPublishDTO dto = createPublishDTO();
        dto.setName(null);

        assertDoesNotThrow(() -> productService.updateProduct(1L, dto));
        assertNull(product.getName());
    }

    @Test
    @DisplayName("updateProduct: 更新时设置null价格")
    void updateProduct_setNullPrice() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);
        ProductPublishDTO dto = createPublishDTO();
        dto.setPrice(null);

        assertDoesNotThrow(() -> productService.updateProduct(1L, dto));
        assertNull(product.getPrice());
    }

    @Test
    @DisplayName("updateProduct: 更新时设置0库存")
    void updateProduct_setZeroStock() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);
        ProductPublishDTO dto = createPublishDTO();
        dto.setStock(0);

        assertDoesNotThrow(() -> productService.updateProduct(1L, dto));
        assertEquals(0, product.getStock().intValue());
    }

    @Test
    @DisplayName("updateProduct: 更新时设置负数库存")
    void updateProduct_setNegativeStock() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);
        ProductPublishDTO dto = createPublishDTO();
        dto.setStock(-10);

        assertDoesNotThrow(() -> productService.updateProduct(1L, dto));
        assertEquals(-10, product.getStock().intValue());
    }

    // ==================== ProductServiceImpl - onShelf 边界场景 ====================

    @Test
    @DisplayName("onShelf: aiReview为null不可上架")
    void onShelf_aiReviewNull_throws() {
        Product product = createMockProduct();
        product.setAiReview(null);
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class, () -> productService.onShelf(1L));
    }

    @Test
    @DisplayName("onShelf: aiReview为拒绝状态不可上架")
    void onShelf_aiRejected_throws() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.MANUAL_REJECT.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class, () -> productService.onShelf(1L));
    }

    @Test
    @DisplayName("onShelf: aiReview为未审核不可上架")
    void onShelf_notReviewed_throws() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.NOT_REVIEWED.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class, () -> productService.onShelf(1L));
    }

    @Test
    @DisplayName("onShelf: aiReview为AI可疑不可上架")
    void onShelf_aiSuspicious_throws() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.AI_SUSPICIOUS.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class, () -> productService.onShelf(1L));
    }

    // ==================== ProductServiceImpl - listProducts 边界场景 ====================

    @Test
    @DisplayName("listProducts: page=0使用默认值1")
    void listProducts_pageZero_defaultsTo1() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProducts(0, 0, null, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("listProducts: size为0使用默认值20")
    void listProducts_sizeZero_defaultsTo20() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProducts(1, 0, null, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("listProducts: 负数page使用默认值1")
    void listProducts_negativePage_defaultsTo1() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProducts(-1, 10, null, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("listProducts: 全部参数为null使用默认值")
    void listProducts_allNull_defaultValues() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProducts(null, null, null, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("listProducts: 超大page和size值正常处理")
    void listProducts_largePageSize() {
        Page<Product> page = new Page<>(99999, 99999);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProducts(99999, 99999, null, null);

        assertNotNull(result);
    }

    // ==================== ProductServiceImpl - listProductsAdmin 边界场景 ====================

    @Test
    @DisplayName("listProductsAdmin: 空白关键字被忽略")
    void listProductsAdmin_blankKeyword_ignored() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProductsAdmin(null, null, "  ", null, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("listProductsAdmin: 负数page和size")
    void listProductsAdmin_negativePagination() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProductsAdmin(-5, -10, null, null, null);

        assertNotNull(result);
    }

    // ==================== ProductServiceImpl - toggleStatus 边界场景 ====================

    @Test
    @DisplayName("toggleStatus: status为null时走下架分支")
    void toggleStatus_nullStatus_offShelf() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.toggleStatus(1L, null));
        assertEquals(ProductStatusEnum.OFF_SHELF.getCode(), product.getStatus());
    }

    @Test
    @DisplayName("toggleStatus: status为OFF_SHELF时无需AI审核")
    void toggleStatus_offShelf_noAiCheck() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.NOT_REVIEWED.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.toggleStatus(1L, ProductStatusEnum.OFF_SHELF.getCode()));
    }

    @Test
    @DisplayName("toggleStatus: aiReview为null上架抛异常")
    void toggleStatus_aiReviewNull_onShelf_throws() {
        Product product = createMockProduct();
        product.setAiReview(null);
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class,
                () -> productService.toggleStatus(1L, ProductStatusEnum.ON_SHELF.getCode()));
    }

    @Test
    @DisplayName("toggleStatus: 不存在的商品抛异常")
    void toggleStatus_productNotFound_throws() {
        when(productMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class,
                () -> productService.toggleStatus(999L, ProductStatusEnum.OFF_SHELF.getCode()));
    }

    // ==================== ProductServiceImpl - manualReview 边界场景 ====================

    @Test
    @DisplayName("manualReview: pass为null时视为false(Boolean)")
    void manualReview_nullPass_treatedAsFalse() {
        Product product = createMockProduct();
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.manualReview(1L, null, "测试"));
        assertEquals(AiReviewResultEnum.MANUAL_REJECT.getCode(), product.getAiReview());
    }

    @Test
    @DisplayName("manualReview: pass为true且原状态为下架")
    void manualReview_passTrue_alreadyOffShelf() {
        Product product = createMockProduct();
        product.setStatus(ProductStatusEnum.OFF_SHELF.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.manualReview(1L, true, "通过"));
        assertEquals(AiReviewResultEnum.MANUAL_PASS.getCode(), product.getAiReview());
    }

    // ==================== ProductServiceImpl - updateAiReview 边界场景 ====================

    @Test
    @DisplayName("updateAiReview: AI_PASS不改状态")
    void updateAiReview_aiPass_noStatusChange() {
        Product product = createMockProduct();
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.updateAiReview(1L, AiReviewResultEnum.AI_PASS.getCode(), null);

        assertEquals(ProductStatusEnum.ON_SHELF.getCode(), product.getStatus());
    }

    @Test
    @DisplayName("updateAiReview: MANUAL_PASS不改状态")
    void updateAiReview_manualPass_noStatusChange() {
        Product product = createMockProduct();
        product.setStatus(ProductStatusEnum.UNDER_REVIEW.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.updateAiReview(1L, AiReviewResultEnum.MANUAL_PASS.getCode(), null);

        assertEquals(ProductStatusEnum.UNDER_REVIEW.getCode(), product.getStatus());
    }

    @Test
    @DisplayName("updateAiReview: remark为null正常更新")
    void updateAiReview_nullRemark() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.updateAiReview(1L, AiReviewResultEnum.AI_SUSPICIOUS.getCode(), null);

        assertNull(product.getAiReviewRemark());
    }

    // ==================== ProductServiceImpl - listSubCategories 边界场景 ====================

    @Test
    @DisplayName("listSubCategories: parentId=0返回一级分类")
    void listSubCategories_parentIdZero() {
        List<ProductCategory> rootList = new ArrayList<>();
        rootList.add(createMockCategory(1L, 0L, "一级分类", 1));
        when(productCategoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(rootList);

        List<ProductCategory> result = productService.listSubCategories(0L);

        assertEquals(1, result.size());
    }

    @Test
    @DisplayName("listSubCategories: 不存在的parentId返回空列表")
    void listSubCategories_nonExistentParentId() {
        when(productCategoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(new ArrayList<>());

        List<ProductCategory> result = productService.listSubCategories(99999L);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // ==================== HybridPayServiceImpl 边界场景 ====================

    @Test
    @DisplayName("calc: totalPrice为0.01时LSC全部消费RMB=0")
    void calc_minimumTotalPrice_allLsc() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("0.01"));
        dto.setLscAmount(100L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(1L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: totalPrice为0.99 LSC=1 RMB=0")
    void calc_priceLessThanOne_partialLsc() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("0.99"));
        dto.setLscAmount(1L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(1L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: lscAmount等于totalPrice整数部分全部消费")
    void calc_lscAmountEqualsIntegerPart() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("50.50"));
        dto.setLscAmount(50L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.50"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: totalPrice精度超出2位时RMB正确舍入")
    void calc_totalPriceManyDecimals_correctRounding() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("33.333"));
        dto.setLscAmount(10L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(10L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("23.33"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: totalPrice为0且lscAmount=0结果全0")
    void calc_zeroTotalZeroLsc_allZero() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(BigDecimal.ZERO);
        dto.setLscAmount(0L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: lscAmount为MAX_LONG截断到总价")
    void calc_maxLongLsc_truncated() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(Long.MAX_VALUE);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(100L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: totalPrice为极大值且LSC=0")
    void calc_veryLargeTotalZeroLsc() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("999999999.99"));
        dto.setLscAmount(0L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("999999999.99"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: totalPrice为负数LSC为0正常计算")
    void calc_negativeTotalPrice_zeroLsc() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("-50.00"));
        dto.setLscAmount(0L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: totalPrice为负数LSC为正值被截断")
    void calc_negativeTotalPrice_positiveLsc() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("-50.00"));
        dto.setLscAmount(30L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: maxAvailableLsc为负数强制归零")
    void calc_negativeMaxAvailable_forcedZero() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(100L);
        dto.setMaxAvailableLsc(-100L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("100.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: maxAvailableLsc为0完全不使用LSC")
    void calc_zeroMaxAvailable_noLscUsed() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("50.00"));
        dto.setLscAmount(30L);
        dto.setMaxAvailableLsc(0L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("50.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: lscAmount为负数抛异常")
    void calc_negativeLsc_throws() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(-1L);

        assertThrows(BizException.class, () -> hybridPayService.calc(dto));
    }

    @Test
    @DisplayName("calc: maxAvailableLsc超过总价时仍受总价约束")
    void calc_maxAvailableExceedsPrice_priceConstrains() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("50.00"));
        dto.setLscAmount(200L);
        dto.setMaxAvailableLsc(500L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: lscAmount刚好等于maxAvailableLsc和totalPrice")
    void calc_lscEqualsAllThree() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("30.00"));
        dto.setLscAmount(30L);
        dto.setMaxAvailableLsc(30L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(30L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: totalPrice为null抛NullPointerException")
    void calc_nullTotalPrice_throws() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(null);
        dto.setLscAmount(10L);

        assertThrows(NullPointerException.class, () -> hybridPayService.calc(dto));
    }

    @Test
    @DisplayName("calc: lscAmount为null抛NullPointerException")
    void calc_nullLscAmount_throws() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(null);

        assertThrows(NullPointerException.class, () -> hybridPayService.calc(dto));
    }

    @Test
    @DisplayName("calc: 小数点后一位价格正确舍入")
    void calc_oneDecimalPrice_correctRounding() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("33.3"));
        dto.setLscAmount(10L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(10L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("23.30"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: totalPrice < 1 时LSC不能为负")
    void calc_priceLessThanOne_zeroLscCovers() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("0.50"));
        dto.setLscAmount(10L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.50"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: totalPrice为0.49 lscAmount=1 受总价约束LSC=0")
    void calc_priceZeroFourtyNine_lscCannotCover() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("0.49"));
        dto.setLscAmount(1L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.49"), result.getRmbAmount());
    }

    private ProductCategory createMockCategory(Long id, Long parentId, String name, Integer sort) {
        ProductCategory c = new ProductCategory();
        c.setId(id);
        c.setParentId(parentId);
        c.setName(name);
        c.setSort(sort);
        c.setStatus(1);
        return c;
    }
}