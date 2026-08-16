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

import java.util.Collections;
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

    private Admin createMockAdmin(Long id, String username, Integer role, Integer status, String realName) {
        Admin admin = new Admin();
        admin.setAdminId(id);
        admin.setUsername(username);
        admin.setPasswordHash(encoder.encode("password123"));
        admin.setRealName(realName);
        admin.setRole(role);
        admin.setStatus(status);
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
        assertEquals("超级管理员", result.get("realName"));
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

    @Test
    @DisplayName("login: 运营角色登录成功")
    void login_operator_success() {
        Admin admin = createMockAdmin(2L, "operator", 1, 1, "运营管理员");
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);
        when(adminJwtUtil.generateToken(2L, 1)).thenReturn("operator-jwt-token");

        Map<String, Object> result = adminService.login("operator", "password123", "192.168.1.1");

        assertEquals("operator-jwt-token", result.get("token"));
        assertEquals(2L, result.get("adminId"));
        assertEquals("运营管理员", result.get("realName"));
        assertEquals(1, result.get("role"));
    }

    @Test
    @DisplayName("login: 登录成功后更新最后登录IP和时间")
    void login_updatesLastLoginInfo() {
        Admin admin = createMockAdmin();
        when(adminMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(admin);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);
        when(adminJwtUtil.generateToken(1L, 0)).thenReturn("token");

        adminService.login("admin", "admin123", "10.0.0.1");

        verify(adminMapper).updateById(argThat(a ->
                "10.0.0.1".equals(a.getLastLoginIp()) && a.getLastLoginAt() != null
        ));
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

    @Test
    @DisplayName("checkPermission: 风控角色只能访问风控权限")
    void checkPermission_riskRole_matchingOnly() {
        Admin admin = createMockAdmin(3L, "risk", 2, 1, "风控管理员");
        when(adminMapper.selectById(3L)).thenReturn(admin);

        assertTrue(adminService.checkPermission(3L, 2));
        assertFalse(adminService.checkPermission(3L, 1));
        assertFalse(adminService.checkPermission(3L, 3));
    }

    // ============== addAdmin 测试 ==============

    @Test
    @DisplayName("addAdmin: 成功新增管理员(默认角色和状态)")
    void addAdmin_success_defaultRoleAndStatus() {
        Admin admin = new Admin();
        admin.setUsername("newadmin");
        admin.setPassword("password123");
        admin.setRealName("新管理员");

        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(adminMapper.insert(any(Admin.class))).thenReturn(1);

        Admin result = adminService.addAdmin(admin);

        assertNull(result.getPasswordHash());
        assertNull(result.getPassword());
        assertEquals(1, result.getRole());
        assertEquals(1, result.getStatus());
    }

    @Test
    @DisplayName("addAdmin: 成功新增管理员(自定义角色和状态)")
    void addAdmin_withCustomRoleAndStatus() {
        Admin admin = new Admin();
        admin.setUsername("finance");
        admin.setPassword("password123");
        admin.setRealName("财务管理员");
        admin.setRole(3);
        admin.setStatus(0);

        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(adminMapper.insert(any(Admin.class))).thenReturn(1);

        Admin result = adminService.addAdmin(admin);

        assertNull(result.getPasswordHash());
        assertNull(result.getPassword());
        assertEquals(3, result.getRole());
        assertEquals(0, result.getStatus());
    }

    @Test
    @DisplayName("addAdmin: 用户名为空字符串抛异常")
    void addAdmin_emptyUsername_throws() {
        Admin admin = new Admin();
        admin.setUsername("");
        admin.setPassword("password123");

        BizException ex = assertThrows(BizException.class,
                () -> adminService.addAdmin(admin));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("addAdmin: 用户名为null抛异常")
    void addAdmin_nullUsername_throws() {
        Admin admin = new Admin();
        admin.setUsername(null);
        admin.setPassword("password123");

        assertThrows(BizException.class, () -> adminService.addAdmin(admin));
    }

    @Test
    @DisplayName("addAdmin: 密码为空字符串抛异常")
    void addAdmin_emptyPassword_throws() {
        Admin admin = new Admin();
        admin.setUsername("newadmin");
        admin.setPassword("");

        BizException ex = assertThrows(BizException.class,
                () -> adminService.addAdmin(admin));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("addAdmin: 密码为null抛异常")
    void addAdmin_nullPassword_throws() {
        Admin admin = new Admin();
        admin.setUsername("newadmin");
        admin.setPassword(null);

        assertThrows(BizException.class, () -> adminService.addAdmin(admin));
    }

    @Test
    @DisplayName("addAdmin: 用户名重复抛异常")
    void addAdmin_duplicateUsername_throws() {
        Admin admin = new Admin();
        admin.setUsername("existingadmin");
        admin.setPassword("password123");

        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class,
                () -> adminService.addAdmin(admin));
        assertEquals(409, ex.getCode());
    }

    @Test
    @DisplayName("addAdmin: 密码被BCrypt加密存储")
    void addAdmin_passwordEncoded() {
        Admin admin = new Admin();
        admin.setUsername("secureadmin");
        admin.setPassword("mysecretpassword");
        admin.setRealName("安全管理员");

        when(adminMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        final String[] capturedHash = new String[1];
        when(adminMapper.insert(any(Admin.class))).thenAnswer(invocation -> {
            capturedHash[0] = ((Admin) invocation.getArgument(0)).getPasswordHash();
            return 1;
        });

        adminService.addAdmin(admin);

        assertNotNull(capturedHash[0]);
        assertNotEquals("mysecretpassword", capturedHash[0]);
    }

    // ============== updateAdmin 测试 ==============

    @Test
    @DisplayName("updateAdmin: 成功修改管理员信息")
    void updateAdmin_success() {
        Admin existing = createMockAdmin(1L, "admin", 1, 1, "原管理员");
        Admin update = new Admin();
        update.setRealName("新名字");
        update.setRole(2);
        update.setStatus(0);

        when(adminMapper.selectById(1L)).thenReturn(existing);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);

        Admin result = adminService.updateAdmin(1L, update);

        assertNull(result.getPasswordHash());
        assertNull(result.getPassword());
        assertEquals("新名字", result.getRealName());
        assertEquals(2, result.getRole());
        assertEquals(0, result.getStatus());
    }

    @Test
    @DisplayName("updateAdmin: 修改密码时加密存储")
    void updateAdmin_withPasswordChange() {
        Admin existing = createMockAdmin(2L, "operator", 1, 1, "运营");
        Admin update = new Admin();
        update.setPassword("newpassword123");

        when(adminMapper.selectById(2L)).thenReturn(existing);
        final String[] capturedHash = new String[1];
        when(adminMapper.updateById(any(Admin.class))).thenAnswer(invocation -> {
            capturedHash[0] = ((Admin) invocation.getArgument(0)).getPasswordHash();
            return 1;
        });

        adminService.updateAdmin(2L, update);

        assertNotNull(capturedHash[0]);
        assertNotEquals("newpassword123", capturedHash[0]);
    }

    @Test
    @DisplayName("updateAdmin: 密码为空时不修改密码")
    void updateAdmin_withoutPasswordChange() {
        Admin existing = createMockAdmin(3L, "finance", 3, 1, "财务");
        String originalHash = existing.getPasswordHash();
        Admin update = new Admin();
        update.setRealName("财务新");

        when(adminMapper.selectById(3L)).thenReturn(existing);
        final String[] capturedHash = new String[1];
        when(adminMapper.updateById(any(Admin.class))).thenAnswer(invocation -> {
            capturedHash[0] = ((Admin) invocation.getArgument(0)).getPasswordHash();
            return 1;
        });

        adminService.updateAdmin(3L, update);

        assertEquals(originalHash, capturedHash[0]);
    }

    @Test
    @DisplayName("updateAdmin: 管理员不存在抛异常")
    void updateAdmin_notFound_throws() {
        when(adminMapper.selectById(999L)).thenReturn(null);
        Admin update = new Admin();
        update.setRealName("test");

        BizException ex = assertThrows(BizException.class,
                () -> adminService.updateAdmin(999L, update));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("updateAdmin: 只修改真实姓名")
    void updateAdmin_onlyRealName() {
        Admin existing = createMockAdmin(5L, "auditor", 4, 1, "审核员");
        Admin update = new Admin();
        update.setRealName("审核新名");

        when(adminMapper.selectById(5L)).thenReturn(existing);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);

        Admin result = adminService.updateAdmin(5L, update);

        assertEquals("审核新名", result.getRealName());
        assertEquals(4, result.getRole());
        assertEquals(1, result.getStatus());
    }

    @Test
    @DisplayName("updateAdmin: 空密码字符串不更新密码")
    void updateAdmin_emptyPasswordNotUpdated() {
        Admin existing = createMockAdmin(6L, "testuser", 1, 1, "测试");
        String originalHash = existing.getPasswordHash();
        Admin update = new Admin();
        update.setPassword("");
        update.setRealName("测试新");

        when(adminMapper.selectById(6L)).thenReturn(existing);
        final String[] capturedHash = new String[1];
        when(adminMapper.updateById(any(Admin.class))).thenAnswer(invocation -> {
            capturedHash[0] = ((Admin) invocation.getArgument(0)).getPasswordHash();
            return 1;
        });

        adminService.updateAdmin(6L, update);

        assertEquals(originalHash, capturedHash[0]);
    }

    // ============== deleteAdmin 测试 ==============

    @Test
    @DisplayName("deleteAdmin: 成功删除普通管理员")
    void deleteAdmin_success() {
        Admin admin = createMockAdmin();
        admin.setRole(1);
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

        BizException ex = assertThrows(BizException.class,
                () -> adminService.deleteAdmin(1L));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("deleteAdmin: 管理员不存在抛异常")
    void deleteAdmin_notFound_throws() {
        when(adminMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class,
                () -> adminService.deleteAdmin(999L));
        assertEquals(404, ex.getCode());
    }

    @Test
    @DisplayName("deleteAdmin: 软删除设置状态为0")
    void deleteAdmin_softDelete_setsStatusZero() {
        Admin admin = createMockAdmin(10L, "temp", 1, 1, "临时管理员");
        when(adminMapper.selectById(10L)).thenReturn(admin);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);

        adminService.deleteAdmin(10L);

        verify(adminMapper).updateById(argThat(a ->
                a.getStatus() == 0
        ));
    }

    @Test
    @DisplayName("deleteAdmin: 风控角色可以被删除")
    void deleteAdmin_riskRole_canBeDeleted() {
        Admin admin = createMockAdmin(11L, "risk_mgr", 2, 1, "风控经理");
        when(adminMapper.selectById(11L)).thenReturn(admin);
        when(adminMapper.updateById(any(Admin.class))).thenReturn(1);

        assertDoesNotThrow(() -> adminService.deleteAdmin(11L));
    }

    // ============== listAdmins 测试 ==============

    @Test
    @DisplayName("listAdmins: 分页查询默认参数")
    void listAdmins_defaultParams() {
        Admin admin = createMockAdmin();
        Page<Admin> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(admin));
        when(adminMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Admin> result = adminService.listAdmins(null, null, null, null);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
        assertTrue(result.getRecords().stream().allMatch(a -> a.getPasswordHash() == null));
    }

    @Test
    @DisplayName("listAdmins: 自定义分页参数")
    void listAdmins_customPageSize() {
        Page<Admin> page = new Page<>(3, 10);
        page.setRecords(Collections.emptyList());
        when(adminMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Admin> result = adminService.listAdmins(3, 10, null, null);

        assertNotNull(result);
        verify(adminMapper).selectPage(argThat(p ->
                p.getCurrent() == 3 && p.getSize() == 10
        ), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listAdmins: 关键词搜索(用户名)")
    void listAdmins_withKeyword_username() {
        Admin admin = createMockAdmin(1L, "searchuser", 1, 1, "搜索用户");
        Page<Admin> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(admin));
        when(adminMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Admin> result = adminService.listAdmins(null, null, "search", null);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("listAdmins: 关键词搜索(真实姓名)")
    void listAdmins_withKeyword_realName() {
        Admin admin = createMockAdmin(1L, "test", 1, 1, "张搜索");
        Page<Admin> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(admin));
        when(adminMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Admin> result = adminService.listAdmins(null, null, "张", null);

        assertNotNull(result);
    }

    @Test
    @DisplayName("listAdmins: 按角色筛选")
    void listAdmins_withRoleFilter() {
        Admin admin = createMockAdmin(5L, "finance_mgr", 3, 1, "财务经理");
        Page<Admin> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(admin));
        when(adminMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Admin> result = adminService.listAdmins(null, null, null, 3);

        assertNotNull(result);
        verify(adminMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("listAdmins: 关键词和角色组合筛选")
    void listAdmins_withKeywordAndRole() {
        Admin admin = createMockAdmin(6L, "op_admin", 1, 1, "运营人员");
        Page<Admin> page = new Page<>(1, 20);
        page.setRecords(Collections.singletonList(admin));
        when(adminMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Admin> result = adminService.listAdmins(2, 5, "op", 1);

        assertNotNull(result);
        assertEquals(1, result.getRecords().size());
    }

    @Test
    @DisplayName("listAdmins: 空结果集也脱敏")
    void listAdmins_emptyResult_masked() {
        Page<Admin> page = new Page<>(1, 20);
        page.setRecords(Collections.emptyList());
        when(adminMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        IPage<Admin> result = adminService.listAdmins(null, null, "nothing", null);

        assertNotNull(result);
        assertTrue(result.getRecords().isEmpty());
    }
}
