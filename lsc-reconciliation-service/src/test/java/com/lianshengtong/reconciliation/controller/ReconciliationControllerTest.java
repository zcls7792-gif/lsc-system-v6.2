package com.lianshengtong.reconciliation.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.reconciliation.entity.ReconcileReport;
import com.lianshengtong.reconciliation.mapper.ReconcileReportMapper;
import com.lianshengtong.reconciliation.service.ReconciliationService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ReconciliationController MockMvc 测试。
 * 覆盖 /daily, /trigger, /report list & /report/{date} 四个端点及各参数分支。
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock private ReconciliationService reconciliationService;
    @Mock private ReconcileReportMapper reconcileReportMapper;
    @InjectMocks private ReconciliationController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private ReconcileReport buildReport(LocalDate date, int status) {
        ReconcileReport r = new ReconcileReport();
        r.setId(1L);
        r.setReconcileDate(date);
        r.setStatus(status);
        r.setDiffAmount(BigDecimal.ZERO);
        r.setDiffCount(0L);
        return r;
    }

    @Nested
    @DisplayName("POST /api/reconcile/daily - query 参数形式")
    class DailyApi {
        @Test
        @DisplayName("传 date=2026-08-05 -> 透传到 service")
        void withDate() throws Exception {
            LocalDate d = LocalDate.of(2026, 8, 5);
            when(reconciliationService.dailyReconcile(eq(d))).thenReturn(buildReport(d, 1));
            mockMvc.perform(post("/api/reconcile/daily")
                            .param("date", "2026-08-05"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.status").value(1));
            verify(reconciliationService).dailyReconcile(d);
        }

        @Test
        @DisplayName("不传 date -> 传 null，service 决定昨日")
        void noDate() throws Exception {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            when(reconciliationService.dailyReconcile(isNull()))
                    .thenReturn(buildReport(yesterday, 1));

            mockMvc.perform(post("/api/reconcile/daily"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.reconcileDate[0]").value(yesterday.getYear()))
                    .andExpect(jsonPath("$.data.reconcileDate[1]").value(yesterday.getMonthValue()))
                    .andExpect(jsonPath("$.data.reconcileDate[2]").value(yesterday.getDayOfMonth()));
            verify(reconciliationService).dailyReconcile(isNull());
        }

        @Test
        @DisplayName("传非法 date -> 参数转换失败 400")
        void badDate_400() throws Exception {
            mockMvc.perform(post("/api/reconcile/daily").param("date", "not-a-date"))
                    .andExpect(status().isBadRequest());
            verify(reconciliationService, never()).dailyReconcile(any());
        }
    }

    @Nested
    @DisplayName("POST /api/reconcile/trigger - JSON body 形式 (兼容前端)")
    class TriggerApi {

        @Test
        @DisplayName("body.date 传有效日期")
        void bodyDatePresent() throws Exception {
            LocalDate d = LocalDate.of(2026, 9, 3);
            when(reconciliationService.dailyReconcile(eq(d))).thenReturn(buildReport(d, 2));

            String body = om.writeValueAsString(Map.of("date", "2026-09-03"));
            mockMvc.perform(post("/api/reconcile/trigger")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(2));
            verify(reconciliationService).dailyReconcile(d);
        }

        @Test
        @DisplayName("body 为 null (required=false) -> null date")
        void bodyNull() throws Exception {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            when(reconciliationService.dailyReconcile(isNull()))
                    .thenReturn(buildReport(yesterday, 1));

            mockMvc.perform(post("/api/reconcile/trigger"))
                    .andExpect(status().isOk());
            verify(reconciliationService).dailyReconcile(isNull());
        }

        @Test
        @DisplayName("body date 为空/blank -> 视同 null")
        void bodyDateBlankAsNull() throws Exception {
            when(reconciliationService.dailyReconcile(isNull()))
                    .thenReturn(buildReport(LocalDate.now().minusDays(1), 1));

            mockMvc.perform(post("/api/reconcile/trigger")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(Map.of("date", "   "))))
                    .andExpect(status().isOk());
            verify(reconciliationService).dailyReconcile(isNull());
        }

        @Test
        @DisplayName("body 非法 date 字符串 -> 抛 DateTimeParse (Controller 直接解析)")
        void bodyIllegalDate_propagatesException() throws Exception {
            String body = om.writeValueAsString(Map.of("date", "2026-13-40"));
            try {
                mockMvc.perform(post("/api/reconcile/trigger")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                        .andExpect(status().is5xxServerError());
            } catch (Exception e) {
                // 独立 MockMvc 会把 DateTimeParse 包成 NestedServletException
                assertNotNull(e);
            }
            verify(reconciliationService, never()).dailyReconcile(any());
        }
    }

    @Nested
    @DisplayName("GET /api/reconcile/report - 分页列表")
    class ReportListApi {

        @Test
        @DisplayName("不传过滤 -> page=1 size=20 wrapper 全表")
        void defaultParams() throws Exception {
            Page<ReconcileReport> p = new Page<>(1, 20);
            p.setRecords(java.util.Collections.singletonList(buildReport(LocalDate.of(2026,8,5),1)));
            when(reconcileReportMapper.selectPage(any(), any())).thenReturn(p);

            mockMvc.perform(get("/api/reconcile/report"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records").isArray())
                    .andExpect(jsonPath("$.data.records[0].status").value(1));
        }

        @Test
        @DisplayName("带 date + status filter")
        void withDateAndStatusFilters() throws Exception {
            Page<ReconcileReport> p = new Page<>(3, 10);
            p.setRecords(java.util.Collections.emptyList());
            when(reconcileReportMapper.selectPage(any(), any())).thenReturn(p);

            mockMvc.perform(get("/api/reconcile/report")
                            .param("page", "3")
                            .param("size", "10")
                            .param("date", "2026-08-01")
                            .param("status", "2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.current").value(3));
        }

        @Test
        @DisplayName("date 非法字符串 -> DateTimeParse 5xx")
        void badDate_exception() throws Exception {
            try {
                mockMvc.perform(get("/api/reconcile/report").param("date", "xxx"))
                        .andExpect(status().is5xxServerError());
            } catch (Exception e) {
                assertNotNull(e);
            }
            verify(reconcileReportMapper, never()).selectPage(any(), any());
        }
    }

    @Nested
    @DisplayName("GET /api/reconcile/report/{date} - 单日详情")
    class ReportDetailApi {

        @Test
        @DisplayName("合法路径 -> generateReport 返回 1 状态")
        void validDate() throws Exception {
            LocalDate d = LocalDate.of(2026, 8, 5);
            when(reconciliationService.generateReport(eq(d))).thenReturn(buildReport(d, 1));

            mockMvc.perform(get("/api/reconcile/report/2026-08-05"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.reconcileDate[0]").value(2026))
                    .andExpect(jsonPath("$.data.reconcileDate[1]").value(8))
                    .andExpect(jsonPath("$.data.reconcileDate[2]").value(5))
                    .andExpect(jsonPath("$.data.status").value(1));
        }

        @Test
        @DisplayName("格式非法日期 -> 400 参数转换失败")
        void invalidFormat_400() throws Exception {
            mockMvc.perform(get("/api/reconcile/report/09-03"))
                    .andExpect(status().isBadRequest());
            verify(reconciliationService, never()).generateReport(any());
        }

        @Test
        @DisplayName("service 抛 BizException -> 冒泡为 5xx")
        void serviceThrowsBizException() throws Exception {
            when(reconciliationService.generateReport(any()))
                    .thenThrow(new BizException("对账日期不能为空"));

            try {
                mockMvc.perform(get("/api/reconcile/report/2026-08-05"))
                        .andExpect(status().is5xxServerError());
            } catch (Exception e) {
                assertNotNull(e);
            }
        }
    }
}
