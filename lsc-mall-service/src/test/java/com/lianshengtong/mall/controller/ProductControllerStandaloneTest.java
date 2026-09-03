package com.lianshengtong.mall.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.common.dto.HybridPayDTO;
import com.lianshengtong.mall.dto.HybridPayCalcDTO;
import com.lianshengtong.mall.dto.ProductPublishDTO;
import com.lianshengtong.mall.entity.Product;
import com.lianshengtong.mall.entity.ProductCategory;
import com.lianshengtong.mall.service.HybridPayService;
import com.lianshengtong.mall.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * G2 集成：商城商品 Controller 的 HTTP 契约、DTO 校验（@NotNull/@Min/@NotBlank）、
 * 路径变量、Query/Body 混合参数。standalone MockMvc，无外部依赖。
 */
@ExtendWith(MockitoExtension.class)
class ProductControllerStandaloneTest {

    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock ProductService productService;
    @Mock HybridPayService hybridPayService;

    @BeforeEach void setUp() {
        ProductController ctrl = new ProductController(productService, hybridPayService);
        LocalValidatorFactoryBean v = new LocalValidatorFactoryBean();
        v.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(ctrl).setValidator(v).build();
    }

    private ProductPublishDTO validPublish() {
        ProductPublishDTO d = new ProductPublishDTO();
        d.setMerchantId(20001L);
        d.setCategoryId(10L);
        d.setName("空气炸锅 5L 智能款");
        d.setDescription("新品上市");
        d.setMainImage("https://img.example.com/prod-1.png");
        d.setPrice(new BigDecimal("299.00"));
        d.setStock(100);
        return d;
    }

    private Product sampleProduct(Long id) {
        Product p = new Product();
        p.setId(id);
        p.setName("空气炸锅 5L 智能款");
        p.setPrice(new BigDecimal("299.00"));
        p.setStock(100);
        p.setStatus(1);
        return p;
    }

    @Nested @DisplayName("POST /api/product/publish")
    class Publish {
        @Test @DisplayName("合法 DTO → 200 并返回 productId")
        void valid() throws Exception {
            when(productService.publishProduct(any())).thenReturn(50001L);
            mvc.perform(post("/api/product/publish")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(validPublish())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value(50001));
            verify(productService).publishProduct(any());
        }

        @Test @DisplayName("缺少 merchantId → 400")
        void missingMerchant() throws Exception {
            ProductPublishDTO d = validPublish(); d.setMerchantId(null);
            mvc.perform(post("/api/product/publish")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(d)))
                    .andExpect(status().isBadRequest());
            verify(productService, never()).publishProduct(any());
        }

        @Test @DisplayName("商品名为空 → 400")
        void blankName() throws Exception {
            ProductPublishDTO d = validPublish(); d.setName("");
            mvc.perform(post("/api/product/publish")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(d)))
                    .andExpect(status().isBadRequest());
        }

        @Test @DisplayName("库存为负 → 400 @Min(0)")
        void negativeStock() throws Exception {
            ProductPublishDTO d = validPublish(); d.setStock(-1);
            mvc.perform(post("/api/product/publish")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(d)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested @DisplayName("PUT /api/product/update")
    class Update {
        @Test @DisplayName("id 通过 query 传递，DTO 通过 body 传递")
        void valid() throws Exception {
            mvc.perform(put("/api/product/update")
                            .param("id", "50001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(validPublish())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            verify(productService).updateProduct(eq(50001L), any());
        }
    }

    @Nested @DisplayName("上下架端点")
    class Shelf {
        @Test @DisplayName("POST /api/product/off-shelf?id=1")
        void offShelf() throws Exception {
            mvc.perform(post("/api/product/off-shelf").param("id", "50001"))
                    .andExpect(status().isOk());
            verify(productService).offShelf(50001L);
        }

        @Test @DisplayName("POST /api/product/on-shelf?id=1")
        void onShelf() throws Exception {
            mvc.perform(post("/api/product/on-shelf").param("id", "50001"))
                    .andExpect(status().isOk());
            verify(productService).onShelf(50001L);
        }

        @Test @DisplayName("DELETE /{id} → 调 offShelf 软删除")
        void deleteProduct() throws Exception {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/product/50001"))
                    .andExpect(status().isOk());
            verify(productService).offShelf(50001L);
        }
    }

    @Nested @DisplayName("GET /api/product/list")
    class ListEndpoint {
        @Test @DisplayName("仅 categoryId/status → 走 listProducts")
        void consumerQuery() throws Exception {
            IPage<Product> p = new Page<Product>(1, 20)
                    .setRecords(List.of(sampleProduct(50001L))).setTotal(1L);
            when(productService.listProducts(1, 20, 10L, 1)).thenReturn(p);
            mvc.perform(get("/api/product/list")
                            .param("categoryId", "10")
                            .param("status", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].id").value(50001));
        }

        @Test @DisplayName("有 keyword → 走 admin 查询分支 listProductsAdmin")
        void adminKeyword() throws Exception {
            IPage<Product> p = new Page<Product>(1, 20).setTotal(0L);
            when(productService.listProductsAdmin(1, 20, "炸锅", 20001L, 1)).thenReturn(p);
            mvc.perform(get("/api/product/list")
                            .param("keyword", "炸锅")
                            .param("merchantId", "20001")
                            .param("status", "1"))
                    .andExpect(status().isOk());
            verify(productService).listProductsAdmin(1, 20, "炸锅", 20001L, 1);
        }
    }

    @Nested @DisplayName("人工审核 POST /audit/{id}")
    class ManualAudit {
        @Test @DisplayName("status=pass → pass=true")
        void pass() throws Exception {
            mvc.perform(post("/api/product/audit/50001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"pass\"}"))
                    .andExpect(status().isOk());
            verify(productService).manualReview(50001L, true, null);
        }

        @Test @DisplayName("status=0 → pass=false 且 reason 透传")
        void reject() throws Exception {
            mvc.perform(post("/api/product/audit/50001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"0\",\"reason\":\"涉黄\"}"))
                    .andExpect(status().isOk());
            verify(productService).manualReview(50001L, false, "涉黄");
        }
    }

    @Nested @DisplayName("POST /hybrid-pay-calc")
    class HybridPayCalc {
        @Test @DisplayName("校验通过 → 200 + DTO（lsc+rmb 总额相等）")
        void valid() throws Exception {
            HybridPayCalcDTO dto = new HybridPayCalcDTO();
            dto.setTotalPrice(new BigDecimal("100.00")); dto.setLscAmount(50L);
            HybridPayDTO out = new HybridPayDTO();
            out.setTotalPrice(new BigDecimal("100.00"));
            out.setLscAmount(50L); out.setRmbAmount(new BigDecimal("50.00"));
            when(hybridPayService.calc(any())).thenReturn(out);

            mvc.perform(post("/api/product/hybrid-pay-calc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.lscAmount").value(50))
                    .andExpect(jsonPath("$.data.rmbAmount").value(50.0));
        }

        @Test @DisplayName("lscAmount < 0 → 400 @Min(0)")
        void negativeLsc() throws Exception {
            HybridPayCalcDTO dto = new HybridPayCalcDTO();
            dto.setTotalPrice(new BigDecimal("100")); dto.setLscAmount(-1L);
            mvc.perform(post("/api/product/hybrid-pay-calc")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
            verify(hybridPayService, never()).calc(any());
        }
    }

    @Nested @DisplayName("类目查询")
    class Categories {
        @Test @DisplayName("返回完整类目树 listCategories")
        void tree() throws Exception {
            ProductCategory c = new ProductCategory();
            c.setId(1L); c.setName("数码"); c.setParentId(0L);
            when(productService.listAllCategories()).thenReturn(List.of(c));
            mvc.perform(get("/api/product/categories"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].name").value("数码"));
        }

        @Test @DisplayName("sub categories 按 parentId")
        void sub() throws Exception {
            ProductCategory c = new ProductCategory();
            c.setId(11L); c.setName("手机"); c.setParentId(1L);
            when(productService.listSubCategories(1L)).thenReturn(List.of(c));
            mvc.perform(get("/api/product/categories/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].parentId").value(1));
        }
    }
}
