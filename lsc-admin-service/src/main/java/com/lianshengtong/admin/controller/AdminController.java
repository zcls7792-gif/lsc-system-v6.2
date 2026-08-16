package com.lianshengtong.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.lianshengtong.admin.entity.Admin;
import com.lianshengtong.admin.entity.AdminAuditLog;
import com.lianshengtong.admin.service.AdminAuditService;
import com.lianshengtong.admin.service.AdminService;
import com.lianshengtong.admin.util.AdminJwtUtil;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.common.result.R;
import com.lianshengtong.common.security.RequireAdminRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理员接口(登录/信息/CRUD/审计日志)
 * <p>
 * 与 lsc-admin-web 的 admin.ts 契约对齐：
 * <ul>
 *   <li>登录使用 JSON Body 而非表单参数，统一 RESTful 风格</li>
 *   <li>补全管理员 CRUD 与审计日志查询端点</li>
 * </ul>
 * </p>
 */
@Tag(name = "管理员", description = "管理员登录、信息与CRUD")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AdminAuditService adminAuditService;
    private final AdminJwtUtil adminJwtUtil;

    @Operation(summary = "管理员登录(JSON Body)")
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body,
                                        HttpServletRequest request) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || password == null) {
            throw new BizException(400, "用户名/密码不能为空");
        }
        String clientIp = request.getRemoteAddr();
        Map<String, Object> result = adminService.login(username, password, clientIp);
        adminAuditService.record((Long) result.get("adminId"), "admin", "login",
                String.valueOf(result.get("adminId")), "登录成功", clientIp);
        return R.ok(result);
    }

    @Operation(summary = "退出登录(记录审计日志)")
    @PostMapping("/logout")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String token,
                          HttpServletRequest request) {
        try {
            Long adminId = parseAdminId(token);
            adminAuditService.record(adminId, "admin", "logout",
                    String.valueOf(adminId), "退出登录", request.getRemoteAddr());
        } catch (Exception ignored) {
            // 退出登录即使 token 无效也返回成功，前端清理本地态即可
        }
        return R.ok();
    }

    @Operation(summary = "管理员信息")
    @GetMapping("/info")
    public R<Admin> info(@RequestHeader("Authorization") String token) {
        Long adminId = parseAdminId(token);
        return R.ok(adminService.getAdminInfo(adminId));
    }

    @Operation(summary = "管理员分页列表")
    @GetMapping("/list")
    public R<IPage<Admin>> list(@RequestParam(required = false, defaultValue = "1") Integer page,
                                @RequestParam(required = false, defaultValue = "20") Integer size,
                                @RequestParam(required = false) String keyword,
                                @RequestParam(required = false) Integer role) {
        return R.ok(adminService.listAdmins(page, size, keyword, role));
    }

    @Operation(summary = "新增管理员")
    @PostMapping
    @RequireAdminRole(3)
    public R<Admin> add(@RequestBody Admin admin,
                       @RequestHeader("Authorization") String token,
                       HttpServletRequest request) {
        Long operatorId = parseAdminId(token);
        Admin created = adminService.addAdmin(admin);
        adminAuditService.record(operatorId, "admin", "add_admin",
                String.valueOf(created.getAdminId()), "新增管理员:" + created.getUsername(),
                request.getRemoteAddr());
        return R.ok(created);
    }

    @Operation(summary = "修改管理员")
    @PutMapping("/{id}")
    @RequireAdminRole(2)
    public R<Admin> update(@PathVariable("id") Long id,
                           @RequestBody Admin admin,
                           @RequestHeader("Authorization") String token,
                           HttpServletRequest request) {
        Long operatorId = parseAdminId(token);
        Admin updated = adminService.updateAdmin(id, admin);
        adminAuditService.record(operatorId, "admin", "update_admin",
                String.valueOf(id), "修改管理员信息", request.getRemoteAddr());
        return R.ok(updated);
    }

    @Operation(summary = "删除管理员(软删除)")
    @DeleteMapping("/{id}")
    @RequireAdminRole(3)
    public R<Void> delete(@PathVariable("id") Long id,
                          @RequestHeader("Authorization") String token,
                          HttpServletRequest request) {
        Long operatorId = parseAdminId(token);
        adminService.deleteAdmin(id);
        adminAuditService.record(operatorId, "admin", "delete_admin",
                String.valueOf(id), "删除(禁用)管理员", request.getRemoteAddr());
        return R.ok();
    }

    @Operation(summary = "操作审计日志分页查询")
    @GetMapping("/audit/logs")
    public R<IPage<AdminAuditLog>> auditLogs(@RequestParam(required = false, defaultValue = "1") Integer page,
                                             @RequestParam(required = false, defaultValue = "20") Integer size,
                                             @RequestParam(required = false) Long adminId,
                                             @RequestParam(required = false) Integer aiFlag) {
        return R.ok(adminAuditService.list(page, size, adminId, aiFlag));
    }

    private Long parseAdminId(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (!adminJwtUtil.validateToken(token)) {
            throw new BizException(401, "Token无效或已过期");
        }
        return adminJwtUtil.getAdminId(token);
    }
}
