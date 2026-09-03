package com.lianshengtong.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.admin.entity.ParamChangeApproval;
import com.lianshengtong.admin.service.AdminAuditService;
import com.lianshengtong.admin.service.ParamChangeService;
import com.lianshengtong.admin.util.AdminJwtUtil;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * G2 集成：参数配置双人审批 Controller 的参数解析 + token 鉴权 + 审计日志。
 */
@ExtendWith(MockitoExtension.class)
class ParamConfigControllerStandaloneTest {

    private MockMvc mvc;

    @Mock ParamChangeService paramChangeService;
    @Mock AdminAuditService adminAuditService;

    @BeforeEach void setUp() throws Exception {
        AdminJwtUtil u = new AdminJwtUtil();
        java.lang.reflect.Field secret = AdminJwtUtil.class.getDeclaredField("secret");
        secret.setAccessible(true);
        secret.set(u, "admin-secret-must-be-32-bytes-long-test-ok!");
        java.lang.reflect.Field exp = AdminJwtUtil.class.getDeclaredField("expireMillis");
        exp.setAccessible(true); exp.setLong(u, 7_200_000L);
        java.lang.reflect.Field issuer = AdminJwtUtil.class.getDeclaredField("issuer");
        issuer.setAccessible(true); issuer.set(u, "lsc-admin-service");
        u.init();

        ParamConfigController ctrl = new ParamConfigController(paramChangeService, adminAuditService, u);
        mvc = MockMvcBuilders.standaloneSetup(ctrl).build();
    }

    private String token(long id, int role) throws Exception {
        AdminJwtUtil u = new AdminJwtUtil();
        java.lang.reflect.Field secret = AdminJwtUtil.class.getDeclaredField("secret");
        secret.setAccessible(true);
        secret.set(u, "admin-secret-must-be-32-bytes-long-test-ok!");
        java.lang.reflect.Field exp = AdminJwtUtil.class.getDeclaredField("expireMillis");
        exp.setAccessible(true); exp.setLong(u, 7_200_000L);
        java.lang.reflect.Field issuer = AdminJwtUtil.class.getDeclaredField("issuer");
        issuer.setAccessible(true); issuer.set(u, "lsc-admin-service");
        u.init();
        return u.generateToken(id, role);
    }

    private ParamChangeApproval approval(Long id, String key, String val, int status) {
        ParamChangeApproval a = new ParamChangeApproval();
        a.setId(id); a.setConfigKey(key); a.setNewValue(val);
        a.setStatus(status); a.setCreatedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        return a;
    }

    @Nested @DisplayName("POST /api/admin/param/submit")
    class Submit {
        @Test @DisplayName("发起审批 → 200 + 审批对象 + 审计日志")
        void ok() throws Exception {
            String tok = token(1L, 2);
            when(paramChangeService.submit("LSC_RELEASE_RATE", "0.05", 1L, "更新释放比例"))
                    .thenReturn(approval(10L, "LSC_RELEASE_RATE", "0.05", 0));
            mvc.perform(post("/api/admin/param/submit")
                            .header("Authorization", "Bearer " + tok)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("configKey", "LSC_RELEASE_RATE")
                            .param("newValue", "0.05")
                            .param("remark", "更新释放比例"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.id").value(10));
            verify(paramChangeService).submit("LSC_RELEASE_RATE", "0.05", 1L, "更新释放比例");
            verify(adminAuditService).record(eq(1L), eq("param"), eq("submit"),
                    eq("10"), contains("发起参数变更"), any());
        }
    }

    @Nested @DisplayName("POST /approve & /reject")
    class ApproveReject {
        @Test @DisplayName("双人签名批准 → 调用 approve + audit log")
        void approve() throws Exception {
            String tok = token(2L, 2);
            when(paramChangeService.approve(10L, 2L, "SIG:0x123"))
                    .thenReturn(approval(10L, "LSC_RELEASE_RATE", "0.05", 1));
            mvc.perform(post("/api/admin/param/approve")
                            .header("Authorization", "Bearer " + tok)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("approvalId", "10")
                            .param("signature", "SIG:0x123"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(1));
            verify(paramChangeService).approve(10L, 2L, "SIG:0x123");
        }

        @Test @DisplayName("拒绝审批 → 透传 reason")
        void reject() throws Exception {
            String tok = token(2L, 2);
            mvc.perform(post("/api/admin/param/reject")
                            .header("Authorization", "Bearer " + tok)
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("approvalId", "10")
                            .param("reason", "风险未评估"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            verify(paramChangeService).reject(10L, 2L, "风险未评估");
        }
    }

    @Nested @DisplayName("GET /api/admin/param/list")
    class ListEndpoint {
        @Test @DisplayName("查询审批列表（page/size/status）→ 返回 IPage")
        void ok() throws Exception {
            String tok = token(1L, 2);
            IPage<ParamChangeApproval> p = new Page<ParamChangeApproval>(1, 20)
                    .setRecords(List.of(approval(10L, "LSC_RELEASE_RATE", "0.05", 1)))
                    .setTotal(1L);
            when(paramChangeService.list(1, 20, 1)).thenReturn(p);
            mvc.perform(get("/api/admin/param/list")
                            .header("Authorization", "Bearer " + tok)
                            .param("status", "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].id").value(10));
        }
    }
}
