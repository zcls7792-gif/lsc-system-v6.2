package com.lianshengtong.risk.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.risk.dto.RiskCheckDTO;
import com.lianshengtong.risk.entity.RiskLog;
import com.lianshengtong.risk.service.RiskControlService;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RiskController MockMvc 分层测试。
 *
 * <p>覆盖：/check, /logs, /logs/{id}, /dashboard, /logs/{id}/handle (JSON 路径)
 * /handle (query 兼容路径) 等 6 组接口，parseAction() 5 个分支（ignore/unblock/push/null/数字字符串/非法字符串）。
 * 由于 Standalone MockMvc 不加载 @RequireAdminRole 切面，这里仅验证业务参数通过路径，不做 RBAC 断言。
 */
@ExtendWith(MockitoExtension.class)
class RiskControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock private RiskControlService riskControlService;
    @InjectMocks private RiskController riskController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(riskController).build();
    }

    private RiskCheckDTO baseDto() {
        RiskCheckDTO dto = new RiskCheckDTO();
        dto.setUserId(1L);
        dto.setOrderNo("ORD-001");
        dto.setProductId(10L);
        dto.setOrderAmount(new BigDecimal("100.00"));
        dto.setLscAmount(50L);
        dto.setClientIp("127.0.0.1");
        dto.setClientCity("Beijing");
        dto.setEnableAi(false);
        return dto;
    }

    private RiskLog logZeroRisk() {
        RiskLog r = new RiskLog();
        r.setId(1L);
        r.setRiskLevel(0);
        r.setHandleStatus(3);
        r.setHitRule("无风险命中");
        return r;
    }

    @Nested
    @DisplayName("POST /api/risk/check")
    class CheckApi {

        @Test
        @DisplayName("标准请求 -> 成功返回 0 级风险")
        void success_riskLevelZero() throws Exception {
            when(riskControlService.check(any(RiskCheckDTO.class))).thenReturn(logZeroRisk());

            mockMvc.perform(post("/api/risk/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(baseDto())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.riskLevel").value(0))
                    .andExpect(jsonPath("$.data.handleStatus").value(3));
        }

        @Test
        @DisplayName("空 body -> 返回默认 dto (未做 @NotNull 校验) 交由 service 处理")
        void emptyBody_passedToService() throws Exception {
            when(riskControlService.check(any(RiskCheckDTO.class))).thenReturn(logZeroRisk());

            mockMvc.perform(post("/api/risk/check")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            verify(riskControlService).check(any(RiskCheckDTO.class));
        }
    }

    @Nested
    @DisplayName("GET /api/risk/logs + /logs/{id}")
    class LogsApi {

        @Test
        @DisplayName("分页 + 过滤参数")
        void logs_paginationAndFilters() throws Exception {
            Page<RiskLog> p = new Page<>(2, 20);
            p.setRecords(Collections.singletonList(logZeroRisk()));
            when(riskControlService.logs(eq(2), eq(20), eq(1L), eq(3), eq(0))).thenReturn(p);

            mockMvc.perform(get("/api/risk/logs")
                            .param("page", "2")
                            .param("size", "20")
                            .param("userId", "1")
                            .param("riskLevel", "3")
                            .param("handleStatus", "0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.current").value(2))
                    .andExpect(jsonPath("$.data.records[0].riskLevel").value(0));
            verify(riskControlService).logs(2, 20, 1L, 3, 0);
        }

        @Test
        @DisplayName("不传过滤值使用默认 1/20")
        void logs_defaultParams() throws Exception {
            Page<RiskLog> p = new Page<>(1, 20);
            p.setRecords(Collections.emptyList());
            when(riskControlService.logs(eq(1), eq(20), isNull(), isNull(), isNull())).thenReturn(p);

            mockMvc.perform(get("/api/risk/logs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records").isArray());
        }

        @Test
        @DisplayName("GET /api/risk/logs/{id} 详情")
        void detail() throws Exception {
            RiskLog l = logZeroRisk();
            l.setId(77L);
            when(riskControlService.getById(77L)).thenReturn(l);

            mockMvc.perform(get("/api/risk/logs/77"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(77));
        }
    }

    @Nested
    @DisplayName("GET /api/risk/dashboard")
    class DashboardApi {

        @Test
        @DisplayName("返回 total + byLevel + byStatus + highRiskPending 四个键")
        void returnsFourKeys() throws Exception {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("total", 100L);
            r.put("byLevel", Map.of("1", 10L, "2", 20L, "3", 30L));
            r.put("byStatus", Map.of("0", 10L, "1", 0L, "2", 0L, "3", 80L, "4", 10L));
            r.put("highRiskPending", 15L);
            when(riskControlService.dashboard()).thenReturn(r);

            mockMvc.perform(get("/api/risk/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(100))
                    .andExpect(jsonPath("$.data.highRiskPending").value(15))
                    .andExpect(jsonPath("$.data.byLevel['1']").value(10))
                    .andExpect(jsonPath("$.data.byStatus['3']").value(80));
        }
    }

    @Nested
    @DisplayName("POST /api/risk/logs/{id}/handle — JSON 路径 parseAction()")
    class HandleByPath {

        @Test
        @DisplayName("action=ignore -> handleStatus=3 （忽略）")
        void ignore_actionMapsTo3() throws Exception {
            String body = om.writeValueAsString(Map.of(
                    "action", "ignore",
                    "remark", "误报"));

            mockMvc.perform(post("/api/risk/logs/1/handle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            verify(riskControlService).handle(eq(1L), eq(3), eq("误报"));
        }

        @Test
        @DisplayName("action=unblock -> handleStatus=4 （解封）")
        void unblock_actionMapsTo4() throws Exception {
            String body = om.writeValueAsString(Map.of("action", "unblock"));

            mockMvc.perform(post("/api/risk/logs/1/handle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
            verify(riskControlService).handle(eq(1L), eq(4), eq(null));
        }

        @Test
        @DisplayName("action=push -> handleStatus=2 （人工审核）")
        void push_actionMapsTo2() throws Exception {
            String body = om.writeValueAsString(Map.of("action", "push", "remark", "需要复核"));
            mockMvc.perform(post("/api/risk/logs/5/handle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
            verify(riskControlService).handle(5L, 2, "需要复核");
        }

        @Test
        @DisplayName("action 缺省/空 -> 默认 3 忽略")
        void emptyAction_defaultsToIgnore() throws Exception {
            mockMvc.perform(post("/api/risk/logs/8/handle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk());
            verify(riskControlService).handle(8L, 3, null);
        }

        @Test
        @DisplayName("action=\"2\" 数字字符串 -> 直接 parse int")
        void numericAction_parsedDirectly() throws Exception {
            String body = om.writeValueAsString(Map.of("action", "0"));
            mockMvc.perform(post("/api/risk/logs/9/handle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
            verify(riskControlService).handle(9L, 0, null);
        }

        @Test
        @DisplayName("action=\"xxx\" 非法字符串 -> 降级为 3")
        void unknownAction_fallsBackToIgnore() throws Exception {
            String body = om.writeValueAsString(Map.of("action", "xxx"));
            mockMvc.perform(post("/api/risk/logs/10/handle")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
            verify(riskControlService).handle(10L, 3, null);
        }
    }

    @Nested
    @DisplayName("POST /api/risk/handle — 兼容 query 参数")
    class HandleQueryCompat {

        @Test
        @DisplayName("必填 query 参数齐全 -> 调用 handle(id, handleStatus, handleRemark)")
        void handle_full() throws Exception {
            mockMvc.perform(post("/api/risk/handle")
                            .param("id", "1")
                            .param("handleStatus", "4")
                            .param("handleRemark", "审批通过"))
                    .andExpect(status().isOk());
            verify(riskControlService).handle(1L, 4, "审批通过");
        }

        @Test
        @DisplayName("不传 handleRemark -> 传 null 到 service")
        void handle_noRemark() throws Exception {
            mockMvc.perform(post("/api/risk/handle")
                            .param("id", "99")
                            .param("handleStatus", "3"))
                    .andExpect(status().isOk());
            verify(riskControlService).handle(99L, 3, null);
        }
    }
}
