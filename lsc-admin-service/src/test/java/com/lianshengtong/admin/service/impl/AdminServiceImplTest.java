package com.lianshengtong.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lianshengtong.admin.entity.Admin;
import com.lianshengtong.admin.mapper.AdminMapper;
import com.lianshengtong.admin.util.AdminJwtUtil;
import com.lianshengtong.common.exception.BizException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("管理员服务单元测试")
class AdminServiceImplTest {

    @Mock
    private AdminMapper adminMapper;
    @Mock
    private AdminJwtUtil adminJwtUtil;

    @InjectMocks
    private AdminServiceImpl adminService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private Admin createMockAdmin() {
        Admin admin = new Admin();
        admin.setAdminId(1L);
        admin.setUsername("admin");
        admin.setPasswordHash(encoder.encode("admin123"));
        admin.setRealName("超级管理员");
        admin.setRole(0);
        admin.setStatus(1);
        return admin;
    }

    // ============== login 测试 ==============

    @Test
    @DisplayName("login: 超管登录成功返回Token")
    void login_superAdmin_success() {
        Admin admin = createMockAdmin();
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);
        when(adminJwtUtil.generateToken(1L, 0)).thenReturn("admin-jwt-token-123");

        Map<String, Object> result = adminService.login("admin", "admin123", "127.0.0.1");

        assertEquals("admin-jwt-token-123", result.get("token"));
        assertEquals(1L, result.get("adminId"));
        assertEquals("admin", result.get("username"));
        assertEquals(0, result.get("role"));
    }

    @Test
    @DisplayName("login: 密码错误抛异常")
    void login_wrongPassword_throws() {
        Admin admin = createMockAdmin();
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);

        BizException ex = assertThrows(BizException.class,
                () -> adminService.login("admin", "wrongpassword", "127.0.0.1"));
        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("login: 用户不存在抛异常")
    void login_userNotFound_throws() {
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> adminService.login("nonexistent", "password", "127.0.0.1"));
        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("login: 账号被禁用抛异常")
    void login_accountDisabled_throws() {
        Admin admin = createMockAdmin();
        admin.setStatus(0);
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);

        BizException ex = assertThrows(BizException.class,
                () -> adminService.login("admin", "admin123", "127.0.0.1"));
        assertEquals(403, ex.getCode());
    }

    // ============== getAdminInfo 测试 ==============

    @Test
    @DisplayName("getAdminInfo: 成功获取用户信息(脱敏)")
    void getAdminInfo_success_masked() {
        Admin admin = createMockAdmin();
        when(adminMapper.selectById(1L)).thenReturn(admin);

        Admin result = adminService.getAdminInfo(1L);

        assertNotNull(result);
        assertNull(result.getPasswordHash());
        assertEquals("超级管理员", result.getRealName());
    }

    @Test
    @DisplayName("getAdminInfo: 管理员不存在抛异常")
    void getAdminInfo_notFound_throws() {
        when(adminMapper.selectById(999L)).thenReturn(null);

        assertThrows(BizException.class, () -> adminService.getAdminInfo(999L));
    }

    // ============== checkPermission 测试 ==============

    @Test
    @DisplayName("checkPermission: 超管拥有全部权限")
    void checkPermission_superAdmin_allPermissions() {
        Admin admin = createMockAdmin();
        when(adminMapper.selectById(1L)).thenReturn(admin);

        assertTrue(adminService.checkPermission(1L, 1));
        assertTrue(adminService.checkPermission(1L, 2));
        assertTrue(adminService.checkPermission(1L, 3));
    }

    @Test
    @DisplayName("checkPermission: 运营只有匹配角色权限")
    void checkPermission_operator_matchingRole() {
        Admin admin = createMockAdmin();
        admin.setRole(1);
        when(adminMapper.selectById(2L)).thenReturn(admin);

        assertTrue(adminService.checkPermission(2L, 1));
        assertFalse(adminService.checkPermission(2L, 2));
        assertFalse(adminService.checkPermission(2L, 0));
    }

    @Test
    @DisplayName("checkPermission: 禁用管理员无权限")
    void checkPermission_disabledAdmin_noPermission() {
        Admin admin = createMockAdmin();
        admin.setStatus(0);
        when(adminMapper.selectById(1L)).thenReturn(admin);

        assertFalse(adminService.checkPermission(1L, 1));
    }

    @Test
    @DisplayName("checkPermission: 不存在的管理员无权限")
    void checkPermission_notFound_noPermission() {
        when(adminMapper.selectById(999L)).thenReturn(null);

        assertFalse(adminService.checkPermission(999L, 1));
    }

    // ============== addAdmin 测试 ==============

    @Test
    @DisplayName("addAdmin: 成功新增管理员")
    void addAdmin_success() {
        Admin admin = new Admin();
        admin.setUsername("newadmin");
        admin.setPassword("password123");
        admin.setRealName("新管理员");

        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(adminMapper.insert(any(Admin.class))).thenReturn(1);

        Admin result = adminService.addAdmin(admin);

        assertNull(result.getPasswordHash());
        assertNull(result.getPassword());
        assertEquals(1, result.getRole()); // 默认运营
        assertEquals(1, result.getStatus()); // 默认正常
    }

    @Test
    @DisplayName("addAdmin: 用户名为空抛异常")
    void addAdmin_emptyUsername_throws() {
        Admin admin = new Admin();
        admin.setUsername("");
        admin.setPassword("password123");

        assertThrows(BizException.class, () -> adminService.addAdmin(admin));
    }

    @Test
    @DisplayName("addAdmin: 密码为空抛异常")
    void addAdmin_emptyPassword_throws() {
        Admin admin = new Admin();
        admin.setUsername("newadmin");
        admin.setPassword("");

        assertThrows(BizException.class, () -> adminService.addAdmin(admin));
    }

    @Test
    @DisplayName("addAdmin: 用户名重复抛异常")
    void addAdmin_duplicateUsername_throws() {
        Admin admin = new Admin();
        admin.setUsername("existingadmin");
        admin.setPassword("password123");

        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> adminService.addAdmin(admin));
        assertEquals(409, ex.getCode());
    }

    // ============== deleteAdmin 测试 ==============

    @Test
    @DisplayName("deleteAdmin: 成功删除普通管理员")
    void deleteAdmin_success() {
        Admin admin = createMockAdmin();
        admin.setRole(1); // 运营，非超管
        when(adminMapper.selectById(2L)).thenReturn(admin);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);

        assertDoesNotThrow(() -> adminService.deleteAdmin(2L));
    }

    @Test
    @DisplayName("deleteAdmin: 超管不可删除抛异常")
    void deleteAdmin_superAdmin_throws() {
        Admin admin = createMockAdmin();
        admin.setRole(0);
        when(adminMapper.selectById(1L)).thenReturn(admin);

        assertThrows(BizException.class, () -> adminService.deleteAdmin(1L));
    }

    // ============== listAdmins 测试 ==============

    @Test
    @DisplayName("listAdmins: 分页查询脱敏")
    void listAdmins_success() {
        Admin admin = createMockAdmin();
        Page<Admin> page = new Page<>(1, 20);
        page.setRecords(java.util.Collections.singletonList(admin));
        when(adminMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Admin> result = adminService.listAdmins(null, null, null, null);

        assertNotNull(result);
        assertTrue(result.getRecords().stream().allMatch(a -> a.getPasswordHash() == null));
    }
}
