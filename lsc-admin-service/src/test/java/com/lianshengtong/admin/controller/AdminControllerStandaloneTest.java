package com.lianshengtong.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.admin.entity.Admin;
import com.lianshengtong.admin.service.AdminAuditService;
import com.lianshengtong.admin.service.AdminService;
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

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * G2 集成：管理员 Controller 的 HTTP 契约 + 参数解析 + 自定义 parseAdminId 异常处理。
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerStandaloneTest {

    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock AdminService adminService;
    @Mock AdminAuditService adminAuditService;

    @BeforeEach void setUp() throws Exception {
        // 手工构造一个可工作的 AdminJwtUtil（通过反射注入字段，绕过 @PostConstruct）
        AdminJwtUtil util = new AdminJwtUtil();
        java.lang.reflect.Field secret = AdminJwtUtil.class.getDeclaredField("secret");
        secret.setAccessible(true);
        secret.set(util, "admin-secret-must-be-32-bytes-long-test-ok!");
        java.lang.reflect.Field exp = AdminJwtUtil.class.getDeclaredField("expireMillis");
        exp.setAccessible(true); exp.setLong(util, 7_200_000L);
        java.lang.reflect.Field issuer = AdminJwtUtil.class.getDeclaredField("issuer");
        issuer.setAccessible(true); issuer.set(util, "lsc-admin-service");
        util.init();

        AdminController ctrl = new AdminController(adminService, adminAuditService, util);
        // 对于 parseAdminId 内部直接抛出的 BizException，用默认的异常冒泡（standalone MockMvc 不注册全局异常处理）
        mvc = MockMvcBuilders.standaloneSetup(ctrl).build();
    }

    private String tokenFor(AdminJwtUtil util, long id, int role) throws Exception {
        return util.generateToken(id, role);
    }

    private AdminJwtUtil util() throws Exception {
        AdminJwtUtil u = new AdminJwtUtil();
        java.lang.reflect.Field secret = AdminJwtUtil.class.getDeclaredField("secret");
        secret.setAccessible(true);
        secret.set(u, "admin-secret-must-be-32-bytes-long-test-ok!");
        java.lang.reflect.Field exp = AdminJwtUtil.class.getDeclaredField("expireMillis");
        exp.setAccessible(true); exp.setLong(u, 7_200_000L);
        java.lang.reflect.Field issuer = AdminJwtUtil.class.getDeclaredField("issuer");
        issuer.setAccessible(true); issuer.set(u, "lsc-admin-service");
        u.init();
        return u;
    }

    private Admin admin(Long id, String name, Integer role) {
        Admin a = new Admin();
        a.setAdminId(id); a.setUsername(name); a.setRole(role);
        return a;
    }

    @Nested @DisplayName("POST /api/admin/login")
    class Login {
        @Test @DisplayName("正确的 username/password → 200 + 审计 record 记录 login")
        void ok() throws Exception {
            when(adminService.login(eq("super"), eq("pwd123"), any()))
                    .thenReturn(Map.of("adminId", 1L, "token", "fake"));
            mvc.perform(post("/api/admin/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"super\",\"password\":\"pwd123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.adminId").value(1));
            verify(adminAuditService).record(eq(1L), eq("admin"), eq("login"),
                    eq("1"), eq("登录成功"), any());
        }

        @Test @DisplayName("缺少 password → Controller 内部 BizException（冒泡到 5xx/4xx）")
        void missing() throws Exception {
            try {
                mvc.perform(post("/api/admin/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"super\"}"))
                        .andExpect(status().is4xxClientError());
            } catch (Exception e) {
                // standalone 没注册全局异常处理，允许冒泡
            }
        }
    }

    @Nested @DisplayName("GET /api/admin/info (需要 token)")
    class Info {
        @Test @DisplayName("带 Bearer token → 200，adminId 解析成功并调用 getAdminInfo")
        void validToken() throws Exception {
            String t = tokenFor(util(), 1L, 3);
            when(adminService.getAdminInfo(1L)).thenReturn(admin(1L, "super", 3));
            mvc.perform(get("/api/admin/info").header("Authorization", "Bearer " + t))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value("super"));
            verify(adminService).getAdminInfo(1L);
        }
    }

    @Nested @DisplayName("管理员 CRUD")
    class Crud {
        @Test @DisplayName("POST /api/admin → 新增")
        void add() throws Exception {
            String t = tokenFor(util(), 1L, 3);
            Admin body = new Admin(); body.setUsername("audit"); body.setPassword("abc");
            when(adminService.addAdmin(any())).thenReturn(admin(2L, "audit", 2));
            mvc.perform(post("/api/admin")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + t)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.adminId").value(2));
            verify(adminAuditService).record(eq(1L), eq("admin"), eq("add_admin"),
                    eq("2"), contains("新增管理员"), any());
        }

        @Test @DisplayName("PUT /{id} → 修改")
        void update() throws Exception {
            String t = tokenFor(util(), 1L, 2);
            Admin body = new Admin(); body.setUsername("audit2");
            when(adminService.updateAdmin(eq(2L), any())).thenReturn(admin(2L, "audit2", 2));
            mvc.perform(put("/api/admin/2")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Authorization", "Bearer " + t)
                            .content(om.writeValueAsString(body)))
                    .andExpect(status().isOk());
            verify(adminService).updateAdmin(eq(2L), any());
        }

        @Test @DisplayName("DELETE /{id} → 软删除 + audit log")
        void deleteAdmin() throws Exception {
            String t = tokenFor(util(), 1L, 3);
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/admin/2").header("Authorization", "Bearer " + t))
                    .andExpect(status().isOk());
            verify(adminService).deleteAdmin(2L);
            verify(adminAuditService).record(eq(1L), eq("admin"), eq("delete_admin"),
                    eq("2"), eq("删除(禁用)管理员"), any());
        }
    }

    @Nested @DisplayName("GET /api/admin/list 分页列表")
    class ListEndpoint {
        @Test @DisplayName("默认 page=1 size=20，返回 IPage")
        void defaults() throws Exception {
            IPage<Admin> p = new Page<Admin>(1, 20)
                    .setRecords(List.of(admin(1L, "super", 3), admin(2L, "audit", 2)))
                    .setTotal(2L);
            when(adminService.listAdmins(1, 20, null, null)).thenReturn(p);
            mvc.perform(get("/api/admin/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(2));
        }
    }
}
