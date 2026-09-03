package com.lianshengtong.writeoff.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.common.enums.WriteOffStatusEnum;
import com.lianshengtong.writeoff.dto.WriteOffApplyDTO;
import com.lianshengtong.writeoff.entity.MerchantNhRecord;
import com.lianshengtong.writeoff.service.WriteOffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WriteOffController MockMvc 测试
 * 覆盖 apply / detail / detailById / list / stats / quota
 * 以及 DTO 校验、分页兼容(page/size vs pageNum/pageSize)。
 */
@ExtendWith(MockitoExtension.class)
class WriteOffControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock private WriteOffService writeOffService;
    @InjectMocks private WriteOffController controller;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean v = new LocalValidatorFactoryBean();
        v.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(v).build();
    }

    private MerchantNhRecord buildRecord(String orderNo, int status) {
        MerchantNhRecord r = new MerchantNhRecord();
        r.setId(100L);
        r.setOrderNo(orderNo);
        r.setMerchantId(3001L);
        r.setLscAmount(1000L);
        r.setCashAmount(new BigDecimal("870.00"));
        r.setStatus(status);
        return r;
    }

    @Nested
    @DisplayName("POST /api/writeoff/apply 安全校验")
    class ApplyApi {

        @Test
        @DisplayName("合法 DTO -> 返回 MerchantNhRecord")
        void validDto() throws Exception {
            WriteOffApplyDTO dto = new WriteOffApplyDTO();
            dto.setMerchantId(3001L);
            dto.setLscAmount(1000L);
            

            when(writeOffService.applyWriteOff(any())).thenReturn(buildRecord("WO001", WriteOffStatusEnum.PENDING.getCode()));

            mockMvc.perform(post("/api/writeoff/apply")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.orderNo").value("WO001"))
                    .andExpect(jsonPath("$.data.cashAmount").value(870.00));
        }

        @Test
        @DisplayName("缺少 merchantId -> @NotNull 校验失败 400")
        void missingMerchantId() throws Exception {
            WriteOffApplyDTO dto = new WriteOffApplyDTO();
            dto.setLscAmount(1000L);

            mockMvc.perform(post("/api/writeoff/apply")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
            verify(writeOffService, never()).applyWriteOff(any());
        }

        @Test
        @DisplayName("nhLscAmount 为负 -> 约束 (@Min 或 @Positive) 校验失败 400")
        void negativeNhLscAmount() throws Exception {
            WriteOffApplyDTO dto = new WriteOffApplyDTO();
            dto.setMerchantId(3001L);
            dto.setLscAmount(-50L);

            mockMvc.perform(post("/api/writeoff/apply")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/writeoff/{orderNo} and by-id/{id}")
    class DetailApis {

        @Test
        @DisplayName("按 orderNo 查询")
        void byOrderNo() throws Exception {
            when(writeOffService.getByOrderNo("WO002")).thenReturn(buildRecord("WO002", WriteOffStatusEnum.SUCCESS.getCode()));

            mockMvc.perform(get("/api/writeoff/WO002"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.orderNo").value("WO002"))
                    .andExpect(jsonPath("$.data.status").value(WriteOffStatusEnum.SUCCESS.getCode()));
        }

        @Test
        @DisplayName("按主键 ID 查询")
        void byId() throws Exception {
            MerchantNhRecord r = buildRecord("WO003", WriteOffStatusEnum.SUCCESS.getCode());
            r.setId(888L);
            when(writeOffService.getById(888L)).thenReturn(r);

            mockMvc.perform(get("/api/writeoff/by-id/888"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(888));
        }
    }

    @Nested
    @DisplayName("GET /api/writeoff/list — 分页双风格兼容")
    class ListApi {

        @Test
        @DisplayName("page/size 风格: p=2 s=50")
        void pageSizeStyle() throws Exception {
            Page<MerchantNhRecord> p = new Page<>(2, 50);
            p.setRecords(java.util.List.of(buildRecord("WO004", 1)));
            when(writeOffService.listRecords(eq(2), eq(50), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(p);

            mockMvc.perform(get("/api/writeoff/list")
                            .param("page", "2").param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.current").value(2));
        }

        @Test
        @DisplayName("pageNum/pageSize 风格兼容")
        void pageNumSizeStyle() throws Exception {
            Page<MerchantNhRecord> p = new Page<>(3, 10);
            p.setRecords(java.util.List.of());
            when(writeOffService.listRecords(eq(3), eq(10), eq(3001L), eq(1), any(), any(), any()))
                    .thenReturn(p);

            mockMvc.perform(get("/api/writeoff/list")
                            .param("pageNum", "3")
                            .param("pageSize", "10")
                            .param("merchantId", "3001")
                            .param("status", "1"))
                    .andExpect(status().isOk());
            verify(writeOffService).listRecords(3, 10, 3001L, 1, null, null, null);
        }

        @Test
        @DisplayName("混合传参：page/pageSize 优先 page 和 size")
        void mixedParams_usePageSizeFirst() throws Exception {
            when(writeOffService.listRecords(eq(5), eq(20), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(new Page<>());
            // page=5, pageSize=3000: 期望 p=5 s=20 (size 未传用 pageSize?) 实际实现:
            // p = page != null ? page : pageNum
            // s = size != null ? size : pageSize
            mockMvc.perform(get("/api/writeoff/list")
                            .param("page", "5")
                            .param("pageSize", "20"))
                    .andExpect(status().isOk());
            verify(writeOffService).listRecords(5, 20, null, null, null, null, null);
        }

        @Test
        @DisplayName("带日期范围 batchNo 过滤")
        void withDateAndBatchFilter() throws Exception {
            when(writeOffService.listRecords(isNull(), isNull(), eq(3001L), eq(2), eq("BATCH-1"), eq("2026-09-01"), eq("2026-09-03")))
                    .thenReturn(new Page<>());
            mockMvc.perform(get("/api/writeoff/list")
                            .param("merchantId", "3001")
                            .param("status", "2")
                            .param("batchNo", "BATCH-1")
                            .param("startDate", "2026-09-01")
                            .param("endDate", "2026-09-03"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/writeoff/stats & /quota")
    class StatsAndQuota {

        @Test
        @DisplayName("stats: 全部 merchantId/startDate/endDate 可选项")
        void stats_optionalParams() throws Exception {
            when(writeOffService.stats(isNull(), isNull(), isNull()))
                    .thenReturn(Map.of("totalCount", 5L, "totalAmount", new BigDecimal("4350.00")));
            mockMvc.perform(get("/api/writeoff/stats"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalCount").value(5));
        }

        @Test
        @DisplayName("stats: 带 merchantId 和 日期范围")
        void stats_withFilters() throws Exception {
            when(writeOffService.stats(eq(3001L), eq("2026-08-01"), eq("2026-08-31")))
                    .thenReturn(Map.of("totalCount", 0L));
            mockMvc.perform(get("/api/writeoff/stats")
                            .param("merchantId", "3001")
                            .param("startDate", "2026-08-01")
                            .param("endDate", "2026-08-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalCount").value(0));
        }

        @Test
        @DisplayName("quota: 必填 merchantId=Long, 传 500 返回可用额度")
        void quota_validMerchant() throws Exception {
            when(writeOffService.quota(500L)).thenReturn(Map.of(
                    "availableDaily", 90000L,
                    "todayUsed", 10000L));
            mockMvc.perform(get("/api/writeoff/quota").param("merchantId", "500"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.availableDaily").value(90000));
        }

        @Test
        @DisplayName("quota: 缺 merchantId -> 400 参数转换失败 (Long 不能为 null)")
        void quota_missingMerchant_400() throws Exception {
            mockMvc.perform(get("/api/writeoff/quota"))
                    .andExpect(status().isBadRequest());
            verify(writeOffService, never()).quota(anyLong());
        }
    }
}
