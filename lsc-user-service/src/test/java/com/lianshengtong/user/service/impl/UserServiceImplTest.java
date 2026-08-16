package com.lianshengtong.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lianshengtong.common.enums.UserTypeEnum;
import com.lianshengtong.common.exception.BizException;
import com.lianshengtong.user.dto.LoginDTO;
import com.lianshengtong.user.dto.RegisterDTO;
import com.lianshengtong.user.dto.VerifyDTO;
import com.lianshengtong.user.entity.User;
import com.lianshengtong.user.mapper.UserMapper;
import com.lianshengtong.user.util.JwtUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务单元测试")
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private UserServiceImpl userService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "aesRawKey", "LscIdCardAesKey2026!!");
        ReflectionTestUtils.invokeMethod(userService, "initAesKey");
    }

    // ============== register 测试 ==============

    @Test
    @DisplayName("register: 成功注册消费者用户")
    void register_success_consumer() {
        RegisterDTO dto = new RegisterDTO();
        dto.setMobile("13800138000");
        dto.setPassword("test123456");
        dto.setUserType(0);

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        User result = userService.register(dto);

        assertNotNull(result);
        assertNull(result.getPasswordHash());
        assertEquals(0, result.getUserType());
        assertEquals("13800138000", result.getMobile());
        assertNotNull(result.getReferralCode());
        assertEquals(0, result.getIsVerified());
        assertEquals(1, result.getStatus());
    }

    @Test
    @DisplayName("register: 手机号已注册抛异常")
    void register_duplicateMobile_throws() {
        RegisterDTO dto = new RegisterDTO();
        dto.setMobile("13800138000");
        dto.setPassword("test123456");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BizException ex = assertThrows(BizException.class, () -> userService.register(dto));
        assertEquals(400, ex.getCode());
    }

    @Test
    @DisplayName("register: 推荐码有效时绑定推荐人")
    void register_withReferralCode_success() {
        RegisterDTO dto = new RegisterDTO();
        dto.setMobile("13800138001");
        dto.setPassword("test123456");
        dto.setReferralCode("ref123");

        User referrer = new User();
        referrer.setUserId(100L);
        referrer.setMobile("13900139000");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(referrer);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        User result = userService.register(dto);

        assertNotNull(result);
        assertEquals(100L, result.getReferrerId());
    }

    @Test
    @DisplayName("register: 推荐码无效抛异常")
    void register_invalidReferralCode_throws() {
        RegisterDTO dto = new RegisterDTO();
        dto.setMobile("13800138002");
        dto.setPassword("test123456");
        dto.setReferralCode("invalid_code");

        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> userService.register(dto));
        assertEquals(400, ex.getCode());
        assertTrue(ex.getMessage().contains("推荐码无效"));
    }

    // ============== login 测试 ==============

    @Test
    @DisplayName("login: 登录成功返回Token")
    void login_success_returnsToken() {
        LoginDTO dto = new LoginDTO();
        dto.setAccount("13800138000");
        dto.setPassword("test123456");

        User user = new User();
        user.setUserId(1L);
        user.setMobile("13800138000");
        user.setPasswordHash(passwordEncoder.encode("test123456"));
        user.setUserType(0);
        user.setStatus(1);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        Map<String, Object> extra = new HashMap<>();
        extra.put("mobile", "13800138000");
        when(jwtUtil.generateToken(anyString(), anyString(), any())).thenReturn("token-jwt-123");

        String token = userService.login(dto);

        assertEquals("token-jwt-123", token);
    }

    @Test
    @DisplayName("login: 密码错误抛异常")
    void login_wrongPassword_throws() {
        LoginDTO dto = new LoginDTO();
        dto.setAccount("13800138000");
        dto.setPassword("wrongpassword");

        User user = new User();
        user.setUserId(1L);
        user.setPasswordHash(passwordEncoder.encode("correctpassword"));
        user.setStatus(1);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        BizException ex = assertThrows(BizException.class, () -> userService.login(dto));
        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("login: 用户不存在抛异常")
    void login_userNotFound_throws() {
        LoginDTO dto = new LoginDTO();
        dto.setAccount("13800138000");
        dto.setPassword("test123456");

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> userService.login(dto));
        assertEquals(401, ex.getCode());
    }

    @Test
    @DisplayName("login: 账号被禁用抛异常")
    void login_userDisabled_throws() {
        LoginDTO dto = new LoginDTO();
        dto.setAccount("13800138000");
        dto.setPassword("test123456");

        User user = new User();
        user.setUserId(1L);
        user.setPasswordHash(passwordEncoder.encode("test123456"));
        user.setStatus(0);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        BizException ex = assertThrows(BizException.class, () -> userService.login(dto));
        assertEquals(403, ex.getCode());
    }

    // ============== verify 测试 ==============

    @Test
    @DisplayName("verify: 实名认证成功")
    void verify_success() {
        VerifyDTO dto = new VerifyDTO();
        dto.setUserId(1L);
        dto.setRealName("张三");
        dto.setIdCardNo("110101199003071234");

        User user = new User();
        user.setUserId(1L);
        user.setIsVerified(0);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        assertDoesNotThrow(() -> userService.verify(dto));
    }

    @Test
    @DisplayName("verify: 已认证用户幂等返回")
    void verify_alreadyVerified_idempotent() {
        VerifyDTO dto = new VerifyDTO();
        dto.setUserId(1L);
        dto.setRealName("张三");
        dto.setIdCardNo("110101199003071234");

        User user = new User();
        user.setUserId(1L);
        user.setIsVerified(1);

        when(userMapper.selectById(1L)).thenReturn(user);

        assertDoesNotThrow(() -> userService.verify(dto));
        verify(userMapper, never()).updateById(any());
    }

    // ============== changePassword 测试 ==============

    @Test
    @DisplayName("changePassword: 修改密码成功")
    void changePassword_success() {
        User user = new User();
        user.setUserId(1L);
        user.setPasswordHash(passwordEncoder.encode("oldpassword123"));

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        assertDoesNotThrow(() -> userService.changePassword(1L, "oldpassword123", "newpassword456"));
    }

    @Test
    @DisplayName("changePassword: 旧密码错误抛异常")
    void changePassword_wrongOldPassword_throws() {
        User user = new User();
        user.setUserId(1L);
        user.setPasswordHash(passwordEncoder.encode("oldpassword123"));

        when(userMapper.selectById(1L)).thenReturn(user);

        BizException ex = assertThrows(BizException.class,
                () -> userService.changePassword(1L, "wrongold", "newpassword456"));
        assertEquals(401, ex.getCode());
    }

    // ============== getUserInfo 测试 ==============

    @Test
    @DisplayName("getUserInfo: 成功获取用户信息(脱敏)")
    void getUserInfo_success_masked() {
        User user = new User();
        user.setUserId(1L);
        user.setMobile("13800138000");
        user.setNickname("测试用户");
        user.setPasswordHash("hashed_password");

        when(userMapper.selectById(1L)).thenReturn(user);

        User result = userService.getUserInfo(1L);

        assertNotNull(result);
        assertNull(result.getPasswordHash());
        assertEquals("13800138000", result.getMobile());
    }

    @Test
    @DisplayName("getUserInfo: 用户不存在抛异常")
    void getUserInfo_notFound_throws() {
        when(userMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> userService.getUserInfo(999L));
        assertEquals(404, ex.getCode());
    }
}
