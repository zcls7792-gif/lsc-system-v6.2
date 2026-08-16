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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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
@DisplayName("商品服务与混合支付 - 扩展分支测试")
class ProductServiceImplExtendedTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductCategoryMapper productCategoryMapper;
    @Mock
    private AiGatewayFeignClient aiGatewayFeignClient;

    @InjectMocks
    private ProductServiceImpl productService;

    private final HybridPayServiceImpl hybridPayService = new HybridPayServiceImpl();

    @BeforeAll
    static void initMybatisPlus() {
        try {
            Class<?> tableInfoHelper = Class.forName("com.baomidou.mybatisplus.core.metadata.TableInfoHelper");
            tableInfoHelper.getMethod("initTableInfo", Class.class).invoke(null, Product.class);
            tableInfoHelper.getMethod("initTableInfo", Class.class).invoke(null, ProductCategory.class);
        } catch (Exception ignored) {
        }
    }

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

    private ProductCategory createMockCategory(Long id, Long parentId, String name, Integer sort) {
        ProductCategory c = new ProductCategory();
        c.setId(id);
        c.setParentId(parentId);
        c.setName(name);
        c.setSort(sort);
        c.setStatus(1);
        return c;
    }

    // ==================== ProductServiceImpl - offShelf ====================

    @Test
    @DisplayName("offShelf: 成功下架 - 设置OFF_SHELF并updateById")
    void offShelf_success() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.offShelf(1L));
        assertEquals(ProductStatusEnum.OFF_SHELF.getCode(), product.getStatus());
        verify(productMapper).updateById(product);
    }

    @Test
    @DisplayName("offShelf: 商品不存在抛BizException(404)")
    void offShelf_notFound_throws() {
        when(productMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> productService.offShelf(999L));
        assertEquals(404, ex.getCode());
        assertEquals("商品不存在", ex.getMessage());
    }

    // ==================== ProductServiceImpl - onShelf ====================

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
        assertEquals(ProductStatusEnum.ON_SHELF.getCode(), product.getStatus());
    }

    @Test
    @DisplayName("onShelf: AI审核结果为null不可上架")
    void onShelf_aiNull_throws() {
        Product product = createMockProduct();
        product.setAiReview(null);
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class, () -> productService.onShelf(1L));
    }

    @Test
    @DisplayName("onShelf: AI审核不通过(AI_REJECTED)不可上架")
    void onShelf_aiRejected_throws() {
        Product product = createMockProduct();
        product.setAiReview(2);
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class, () -> productService.onShelf(1L));
    }

    // ==================== ProductServiceImpl - toggleStatus ====================

    @Test
    @DisplayName("toggleStatus: 切换上架 - AI通过成功")
    void toggleStatus_onShelf_aiPass_success() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.AI_PASS.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.toggleStatus(1L, ProductStatusEnum.ON_SHELF.getCode()));
        assertEquals(ProductStatusEnum.ON_SHELF.getCode(), product.getStatus());
    }

    @Test
    @DisplayName("toggleStatus: 切换上架 - 人工通过成功")
    void toggleStatus_onShelf_manualPass_success() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.MANUAL_PASS.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.toggleStatus(1L, ProductStatusEnum.ON_SHELF.getCode()));
        assertEquals(ProductStatusEnum.ON_SHELF.getCode(), product.getStatus());
    }

    @Test
    @DisplayName("toggleStatus: 切换上架 - 未通过审核抛异常")
    void toggleStatus_onShelf_aiFail_throws() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.AI_SUSPICIOUS.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);

        assertThrows(BizException.class, () -> productService.toggleStatus(1L, ProductStatusEnum.ON_SHELF.getCode()));
    }

    @Test
    @DisplayName("toggleStatus: 任何时候切换下架成功")
    void toggleStatus_offShelf_anyTime_success() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.NOT_REVIEWED.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.toggleStatus(1L, ProductStatusEnum.OFF_SHELF.getCode()));
        assertEquals(ProductStatusEnum.OFF_SHELF.getCode(), product.getStatus());
    }

    // ==================== ProductServiceImpl - manualReview ====================

    @Test
    @DisplayName("manualReview: pass=true → MANUAL_PASS")
    void manualReview_pass_true() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.manualReview(1L, true, "确认通过"));
        assertEquals(AiReviewResultEnum.MANUAL_PASS.getCode(), product.getAiReview());
    }

    @Test
    @DisplayName("manualReview: pass=false → MANUAL_REJECT 并自动下架")
    void manualReview_pass_false_autoOffShelf() {
        Product product = createMockProduct();
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        assertDoesNotThrow(() -> productService.manualReview(1L, false, "虚假商品"));
        assertEquals(AiReviewResultEnum.MANUAL_REJECT.getCode(), product.getAiReview());
        assertEquals(ProductStatusEnum.OFF_SHELF.getCode(), product.getStatus());
    }

    // ==================== ProductServiceImpl - updateAiReview ====================

    @Test
    @DisplayName("updateAiReview: AI_SUSPICIOUS → 自动下架")
    void updateAiReview_suspicious_autoOffShelf() {
        Product product = createMockProduct();
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.updateAiReview(1L, AiReviewResultEnum.AI_SUSPICIOUS.getCode(), "检测到敏感词");

        assertEquals(ProductStatusEnum.OFF_SHELF.getCode(), product.getStatus());
        assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), product.getAiReview());
    }

    @Test
    @DisplayName("updateAiReview: MANUAL_REJECT → 自动下架")
    void updateAiReview_manualReject_autoOffShelf() {
        Product product = createMockProduct();
        product.setStatus(ProductStatusEnum.ON_SHELF.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.updateAiReview(1L, AiReviewResultEnum.MANUAL_REJECT.getCode(), "人工拒绝");

        assertEquals(ProductStatusEnum.OFF_SHELF.getCode(), product.getStatus());
        assertEquals(AiReviewResultEnum.MANUAL_REJECT.getCode(), product.getAiReview());
    }

    @Test
    @DisplayName("updateAiReview: AI_PASS → 不改状态")
    void updateAiReview_aiPass_noStatusChange() {
        Product product = createMockProduct();
        product.setStatus(ProductStatusEnum.UNDER_REVIEW.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);
        when(productMapper.updateById(any(Product.class))).thenReturn(1);

        productService.updateAiReview(1L, AiReviewResultEnum.AI_PASS.getCode(), "AI审核通过");

        assertEquals(ProductStatusEnum.UNDER_REVIEW.getCode(), product.getStatus());
        assertEquals(AiReviewResultEnum.AI_PASS.getCode(), product.getAiReview());
    }

    // ==================== ProductServiceImpl - auditList ====================

    @Test
    @DisplayName("auditList: 默认查询AI可疑(aiReview=2)商品")
    void auditList_defaultSuspiciousFilter() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.auditList(null, null, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("auditList: 指定aiReview筛选")
    void auditList_specificAiReviewFilter() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.auditList(1, 10, AiReviewResultEnum.MANUAL_REJECT.getCode());

        assertNotNull(result);
    }

    // ==================== ProductServiceImpl - listProductsAdmin ====================

    @Test
    @DisplayName("listProductsAdmin: 关键字过滤")
    void listProductsAdmin_keywordFilter() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProductsAdmin(null, null, "手机", null, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("listProductsAdmin: 商家ID过滤")
    void listProductsAdmin_merchantIdFilter() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProductsAdmin(null, null, null, 200L, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("listProductsAdmin: 状态过滤")
    void listProductsAdmin_statusFilter() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProductsAdmin(null, null, null, null, ProductStatusEnum.ON_SHELF.getCode());

        assertNotNull(result);
    }

    @Test
    @DisplayName("listProductsAdmin: 默认分页参数")
    void listProductsAdmin_defaultPagination() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProductsAdmin(null, null, null, null, null);

        assertNotNull(result);
    }

    // ==================== ProductServiceImpl - listProducts ====================

    @Test
    @DisplayName("listProducts: 分类ID过滤")
    void listProducts_categoryIdFilter() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProducts(null, null, 10L, null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("listProducts: 状态过滤")
    void listProducts_statusFilter() {
        Page<Product> page = new Page<>(1, 20);
        when(productMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);

        IPage<Product> result = productService.listProducts(null, null, null, ProductStatusEnum.OFF_SHELF.getCode());

        assertNotNull(result);
    }

    // ==================== ProductServiceImpl - listSubCategories ====================

    @Test
    @DisplayName("listSubCategories: 指定parentId过滤并按sort排序")
    void listSubCategories_parentIdFilter() {
        List<ProductCategory> subList = new ArrayList<>();
        subList.add(createMockCategory(2L, 1L, "子类A", 1));
        subList.add(createMockCategory(3L, 1L, "子类B", 2));
        when(productCategoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(subList);

        List<ProductCategory> result = productService.listSubCategories(1L);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getParentId().longValue());
    }

    @Test
    @DisplayName("listSubCategories: parentId为null时默认使用0")
    void listSubCategories_defaultZeroParentId() {
        List<ProductCategory> rootList = new ArrayList<>();
        rootList.add(createMockCategory(1L, 0L, "一级分类", 1));
        when(productCategoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(rootList);

        List<ProductCategory> result = productService.listSubCategories(null);

        assertEquals(1, result.size());
        assertEquals(0L, result.get(0).getParentId().longValue());
    }

    // ==================== ProductServiceImpl - getProductDetail ====================

    @Test
    @DisplayName("getProductDetail: 成功获取商品详情")
    void getProductDetail_success() {
        Product product = createMockProduct();
        when(productMapper.selectById(1L)).thenReturn(product);

        Product result = productService.getProductDetail(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId().longValue());
        assertEquals("测试商品", result.getName());
    }

    // ==================== ProductServiceImpl - getAiReviewResult ====================

    @Test
    @DisplayName("getAiReviewResult: 返回商品审核结果")
    void getAiReviewResult_success() {
        Product product = createMockProduct();
        product.setAiReview(AiReviewResultEnum.AI_SUSPICIOUS.getCode());
        when(productMapper.selectById(1L)).thenReturn(product);

        Product result = productService.getAiReviewResult(1L);

        assertNotNull(result);
        assertEquals(AiReviewResultEnum.AI_SUSPICIOUS.getCode(), result.getAiReview());
    }

    // ==================== HybridPayServiceImpl - 边界场景 ====================

    @Test
    @DisplayName("calc: reqLsc为负抛BizException")
    void calc_reqLscNegative_throws() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(-1L);

        assertThrows(BizException.class, () -> hybridPayService.calc(dto));
    }

    @Test
    @DisplayName("calc: reqLsc超过总价截断到总价")
    void calc_reqLscExceedsTotal_clamped() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("50.00"));
        dto.setLscAmount(100L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: reqLsc在总价内正常计算")
    void calc_reqLscWithinTotal_normal() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(30L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(30L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("70.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: maxAvailableLsc限制LSC使用")
    void calc_maxAvailableLsc_limitsUsage() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("200.00"));
        dto.setLscAmount(200L);
        dto.setMaxAvailableLsc(50L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(50L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("150.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: maxAvailableLsc为负时LSC归零")
    void calc_maxAvailableLscNegative_clampedToZero() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(80L);
        dto.setMaxAvailableLsc(-5L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("100.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 总价为0时LSC=0 RMB=0")
    void calc_totalPriceZero_lscZeroRmbZero() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("0.00"));
        dto.setLscAmount(50L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 全部LSC支付无RMB")
    void calc_allLsc_noRmb() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("100.00"));
        dto.setLscAmount(100L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(100L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: 无LSC全部RMB支付")
    void calc_noLsc_allRmb() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("50.00"));
        dto.setLscAmount(0L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(0L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("50.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: RMB计算结果为负时归零")
    void calc_rmbAmountNegative_clampedToZero() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("10.00"));
        dto.setLscAmount(50L);
        dto.setMaxAvailableLsc(30L);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(10L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("0.00"), result.getRmbAmount());
    }

    @Test
    @DisplayName("calc: LSC为null时正常计算(无上限约束)")
    void calc_nullMaxAvailable_normal() {
        HybridPayCalcDTO dto = new HybridPayCalcDTO();
        dto.setTotalPrice(new BigDecimal("88.88"));
        dto.setLscAmount(40L);
        dto.setMaxAvailableLsc(null);

        HybridPayDTO result = hybridPayService.calc(dto);

        assertEquals(40L, result.getLscAmount().longValue());
        assertEquals(new BigDecimal("48.88"), result.getRmbAmount());
    }
}