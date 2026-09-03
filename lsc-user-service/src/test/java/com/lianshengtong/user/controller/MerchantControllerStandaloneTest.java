package com.lianshengtong.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.user.dto.MerchantApplyDTO;
import com.lianshengtong.user.entity.MerchantExtension;
import com.lianshengtong.user.feign.RiskFeignClient;
import com.lianshengtong.user.service.MerchantService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * G2 集成：商家 Controller HTTP 契约与 DTO 校验（standalone MockMvc 层）。
 */
@ExtendWith(MockitoExtension.class)
class MerchantControllerStandaloneTest {

    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock MerchantService merchantService;
    @Mock RiskFeignClient riskFeignClient;

    @BeforeEach void setUp() {
        MerchantController ctrl = new MerchantController(merchantService, riskFeignClient);
        LocalValidatorFactoryBean v = new LocalValidatorFactoryBean();
        v.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(ctrl).setValidator(v).build();
    }

    private MerchantApplyDTO validApply() {
        MerchantApplyDTO a = new MerchantApplyDTO();
        a.setMerchantId(20001L);
        a.setBusinessLicense("91110000MA0123456X");
        a.setBusinessLicenseImg("https://oss.example.com/lic-1.png");
        a.setStoreName("便利之星旗舰店");
        a.setProvince("广东省"); a.setCity("深圳市"); a.setDistrict("南山区");
        a.setAddressDetail("高新南一道1号");
        a.setContactPhone("13800138000");
        a.setBusinessHours("09:00-22:00");
        return a;
    }

    private MerchantExtension ext(Long id) {
        MerchantExtension e = new MerchantExtension();
        e.setMerchantId(id);
        e.setStoreName("便利之星旗舰店");
        e.setAuditStatus(1);
        e.setCreditScore(100);
        return e;
    }

    @Nested @DisplayName("POST /api/merchant/register")
    class Register {
        @Test @DisplayName("合法 DTO → 200 + 返回审核通过的商家扩展信息")
        void valid() throws Exception {
            when(merchantService.register(any())).thenReturn(ext(20001L));
            mvc.perform(post("/api/merchant/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(validApply())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.merchantId").value(20001))
                    .andExpect(jsonPath("$.data.creditScore").value(100));
            verify(merchantService).register(any());
        }

        @Test @DisplayName("缺少营业执照号 → 400，不调用 service")
        void missingLicense() throws Exception {
            MerchantApplyDTO a = validApply();
            a.setBusinessLicense(null);
            mvc.perform(post("/api/merchant/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(a)))
                    .andExpect(status().isBadRequest());
            verify(merchantService, never()).register(any());
        }
    }

    @Nested @DisplayName("POST /api/merchant/audit")
    class AuditParam {
        @Test @DisplayName("merchantId + auditStatus 通过 query 传递")
        void queryParams() throws Exception {
            when(merchantService.audit(20001L, 1, "ok")).thenReturn(ext(20001L));
            mvc.perform(post("/api/merchant/audit")
                            .param("merchantId", "20001")
                            .param("auditStatus", "1")
                            .param("remark", "ok"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.merchantId").value(20001));
            verify(merchantService).audit(20001L, 1, "ok");
        }
    }

    @Nested @DisplayName("POST /api/merchant/audit/{id}")
    class AuditPath {
        @Test @DisplayName("body 含 status=pass → 通过审核")
        void passViaBody() throws Exception {
            when(merchantService.audit(20001L, 1, null)).thenReturn(ext(20001L));
            mvc.perform(post("/api/merchant/audit/20001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"pass\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            verify(merchantService).audit(20001L, 1, null);
        }

        @Test @DisplayName("body 含 status=reject → 拒绝")
        void rejectViaBody() throws Exception {
            when(merchantService.audit(20001L, 2, "不合规")).thenReturn(ext(20001L));
            mvc.perform(post("/api/merchant/audit/20001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"status\":\"no\",\"reason\":\"不合规\"}"))
                    .andExpect(status().isOk());
            verify(merchantService).audit(20001L, 2, "不合规");
        }
    }

    @Nested @DisplayName("GET /api/merchant/list")
    class ListApi {
        @Test @DisplayName("返回分页数据（含默认 page/size）")
        void defaults() throws Exception {
            IPage<MerchantExtension> p = new Page<MerchantExtension>(1, 20)
                    .setRecords(List.of(ext(20001L), ext(20002L)))
                    .setTotal(2L);
            when(merchantService.listMerchants(1, 20, null, null, null, null)).thenReturn(p);
            mvc.perform(get("/api/merchant/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].merchantId").value(20001))
                    .andExpect(jsonPath("$.data.total").value(2));
        }
    }

    @Nested @DisplayName("GET /api/merchant/{id}")
    class Detail {
        @Test @DisplayName("按 path id 查详情")
        void byId() throws Exception {
            when(merchantService.getMerchantInfo(20001L)).thenReturn(ext(20001L));
            mvc.perform(get("/api/merchant/20001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.storeName").value("便利之星旗舰店"));
        }
    }

    @Nested @DisplayName("PUT /api/merchant/credit")
    class CreditUpdate {
        @Test @DisplayName("merchantId + creditScore query 参数传递")
        void queryParams() throws Exception {
            when(merchantService.updateCredit(20001L, 95)).thenReturn(ext(20001L));
            mvc.perform(put("/api/merchant/credit")
                            .param("merchantId", "20001")
                            .param("creditScore", "95"))
                    .andExpect(status().isOk());
            verify(merchantService).updateCredit(20001L, 95);
        }
    }
}
