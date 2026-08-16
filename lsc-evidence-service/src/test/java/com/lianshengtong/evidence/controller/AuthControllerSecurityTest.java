package com.lianshengtong.evidence.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.evidence.dto.LoginRequest;
import com.lianshengtong.evidence.security.JwtUtil;
import com.lianshengtong.evidence.security.LoginAttemptService;
import com.lianshengtong.evidence.security.TokenBlacklistService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 安全增强测试
 * 覆盖：S11-账户锁定、S12-凭据外部化、S13-时序侧信道、S14-刷新令牌轮换
 * <p>
 * 注意：USER_STORE 已重构为实例字段 userStore (S12-fix)，LOGIN_ATTEMPTS 已抽取为
 * {@link LoginAttemptService}，此处通过 Mock Bean 验证交互契约。
 */
@WebMvcTest(controllers = {AuthController.class},
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false"
        })
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("standalone")
@DisplayName("AuthController 安全增强测试")
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthController authController;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private PasswordEncoder passwordEncoder;

    @MockBean
    private LoginAttemptService loginAttemptService;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedissonClient redissonClient;

    private static final String BASE_URL = "/api/auth";

    @BeforeEach
    void cleanState() throws Exception {
        // 清理 userStore 实例字段 (init() 已在上下文创建时执行过，这里清理避免测试间干扰)
        Field field = AuthController.class.getDeclaredField("userStore");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(authController)).clear();
    }

    /**
     * 通过反射向 userStore 注入测试用户 (UserAccount 为 private record)
     */
    @SuppressWarnings("unchecked")
    private void injectUser(String username, String encoded, String role) throws Exception {
        Class<?> userAccountClass = Arrays.stream(AuthController.class.getDeclaredClasses())
                .filter(c -> "UserAccount".equals(c.getSimpleName()))
                .findFirst()
                .orElseThrow(() -> new ClassNotFoundException("AuthController.UserAccount 未找到"));
        Constructor<?> ctor = userAccountClass.getDeclaredConstructor(
                String.class, String.class, String.class);
        ctor.setAccessible(true);
        Object userAccount = ctor.newInstance(username, encoded, role);

        Field field = AuthController.class.getDeclaredField("userStore");
        field.setAccessible(true);
        Map<String, Object> store = (Map<String, Object>) field.get(authController);
        store.put(username, userAccount);
    }

    // ==================== S11: 账户锁定机制 ====================

    @Test
    @DisplayName("S11: 连续5次登录失败后账户被临时锁定")
    void login_accountLockAfter5Failures() throws Exception {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        // 前5次未锁定，第6次锁定
        when(loginAttemptService.isLocked("admin"))
                .thenReturn(false, false, false, false, false, true);
        injectUser("admin", "encoded-admin", "ADMIN");

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");

        // 前5次返回 401 用户名或密码错误
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(BASE_URL + "/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(jsonPath("$.code").value(401));
        }

        // 第6次返回 429 账户已锁定
        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(429));

        // 验证前5次失败都被记录 (第6次因锁定直接返回，不再调用 recordFailure)
        verify(loginAttemptService, times(5)).recordFailure("admin");
    }

    @Test
    @DisplayName("S11: 登录成功后清除失败计数")
    void login_successClearsFailures() throws Exception {
        when(passwordEncoder.matches("admin123", "encoded-admin")).thenReturn(true);
        when(passwordEncoder.matches("wrong", "encoded-admin")).thenReturn(false);
        when(jwtUtil.generateToken("admin", "ADMIN")).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken("admin", "ADMIN")).thenReturn("refresh-token");
        when(loginAttemptService.isLocked("admin")).thenReturn(false);
        injectUser("admin", "encoded-admin", "ADMIN");

        LoginRequest failReq = new LoginRequest();
        failReq.setUsername("admin");
        failReq.setPassword("wrong");

        // 触发3次失败
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post(BASE_URL + "/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(failReq)));
        }

        // 验证3次失败被记录
        verify(loginAttemptService, times(3)).recordFailure("admin");

        // 用正确密码登录
        LoginRequest okReq = new LoginRequest();
        okReq.setUsername("admin");
        okReq.setPassword("admin123");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(okReq)))
                .andExpect(jsonPath("$.code").value(0));

        // 验证成功后清除了失败计数 (调用 recordSuccess)
        verify(loginAttemptService).recordSuccess("admin");
    }

    // ==================== S12: 凭据外部化 ====================

    @Test
    @DisplayName("S12: userStore 实例字段在 standalone 下可用")
    void init_userStoreAvailable() {
        try {
            Field field = AuthController.class.getDeclaredField("userStore");
            field.setAccessible(true);
            Map<String, Object> store = (Map<String, Object>) field.get(authController);
            assertNotNull(store, "userStore 实例字段应非空");
        } catch (Exception e) {
            fail(e);
        }
    }

    // ==================== S13: 时序侧信道防护 ====================

    @Test
    @DisplayName("S13: 用户不存在时也执行密码比对(避免时序侧信道)")
    void login_nonExistentUserPerformsBcryptMatch() throws Exception {
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);
        when(loginAttemptService.isLocked("ghost_user")).thenReturn(false);

        LoginRequest req = new LoginRequest();
        req.setUsername("ghost_user");
        req.setPassword("any-password");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.code").value(401));

        // 至少调用一次 matches（针对不存在用户的伪比对 DUMMY_BCRYPT_HASH）
        verify(passwordEncoder, atLeastOnce()).matches(anyString(), anyString());
        // 用户不存在也应记录失败
        verify(loginAttemptService).recordFailure("ghost_user");
    }

    // ==================== S14: 刷新令牌轮换 ====================

    @Test
    @DisplayName("S14: 刷新令牌时同时返回新的 refresh_token，旧 Refresh Token 加入黑名单")
    void refresh_rotatesRefreshToken() throws Exception {
        JwtUtil.Claims claims = new JwtUtil.Claims("admin", "ADMIN", "refresh",
                System.currentTimeMillis() + 3600_000L);
        when(jwtUtil.tokenJti("old-refresh")).thenReturn("jti-old");
        when(jwtUtil.validateRefreshToken("old-refresh")).thenReturn(claims);
        when(jwtUtil.generateToken("admin", "ADMIN")).thenReturn("new-access");
        when(jwtUtil.generateRefreshToken("admin", "ADMIN")).thenReturn("new-refresh");
        when(tokenBlacklistService.isRevoked("jti-old")).thenReturn(false);

        mockMvc.perform(post(BASE_URL + "/refresh")
                        .header("Authorization", "Bearer old-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("new-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.data.refreshExpiresIn").exists());

        // 验证旧 Refresh Token 已加入黑名单 (S14-fix: Refresh Token Rotation)
        verify(tokenBlacklistService).revoke(eq("jti-old"), anyLong());
        // 验证同时生成了新的 access 和 refresh token
        verify(jwtUtil).generateToken("admin", "ADMIN");
        verify(jwtUtil).generateRefreshToken("admin", "ADMIN");
    }

    @Test
    @DisplayName("S14: 刷新令牌返回新的 refreshExpiresIn")
    void refresh_returnsRefreshExpiresIn() throws Exception {
        JwtUtil.Claims claims = new JwtUtil.Claims("operator", "OPERATOR", "refresh",
                System.currentTimeMillis() + 7200_000L);
        when(jwtUtil.tokenJti("valid-refresh")).thenReturn("jti-valid");
        when(jwtUtil.validateRefreshToken("valid-refresh")).thenReturn(claims);
        when(jwtUtil.generateToken("operator", "OPERATOR")).thenReturn("access-2");
        when(jwtUtil.generateRefreshToken("operator", "OPERATOR")).thenReturn("refresh-2");
        when(tokenBlacklistService.isRevoked("jti-valid")).thenReturn(false);

        mockMvc.perform(post(BASE_URL + "/refresh")
                        .header("Authorization", "Bearer valid-refresh"))
                .andExpect(jsonPath("$.data.refreshExpiresIn").value(604800L));
    }

    @Test
    @DisplayName("S14: 已撤销的 Refresh Token 拒绝刷新")
    void refresh_revokedTokenRejected() throws Exception {
        when(jwtUtil.tokenJti("revoked-refresh")).thenReturn("jti-revoked");
        when(tokenBlacklistService.isRevoked("jti-revoked")).thenReturn(true);

        mockMvc.perform(post(BASE_URL + "/refresh")
                        .header("Authorization", "Bearer revoked-refresh"))
                .andExpect(jsonPath("$.code").value(401));

        // 已撤销的 token 不应再调用 validateRefreshToken
        verify(jwtUtil, never()).validateRefreshToken(anyString());
    }

    // ==================== 登出 (Logout) ====================

    @Test
    @DisplayName("Logout: 登出时将 Access Token 加入黑名单")
    void logout_addsTokenToBlacklist() throws Exception {
        JwtUtil.Claims claims = new JwtUtil.Claims("admin", "ADMIN", "access",
                System.currentTimeMillis() + 3600_000L);
        when(jwtUtil.validateToken("access-token")).thenReturn(claims);
        when(jwtUtil.tokenJti("access-token")).thenReturn("jti-access");

        mockMvc.perform(post(BASE_URL + "/logout")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(jsonPath("$.code").value(0));

        // 验证 token 已加入黑名单
        verify(tokenBlacklistService).revoke(eq("jti-access"), anyLong());
    }

    @Test
    @DisplayName("Logout: 无 Authorization 头返回 401")
    void logout_noHeader() throws Exception {
        mockMvc.perform(post(BASE_URL + "/logout"))
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("Logout: 无效 token 返回 401")
    void logout_invalidToken() throws Exception {
        when(jwtUtil.validateToken("bad-token")).thenReturn(null);

        mockMvc.perform(post(BASE_URL + "/logout")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(jsonPath("$.code").value(401));

        // 无效 token 不应加入黑名单
        verify(tokenBlacklistService, never()).revoke(anyString(), anyLong());
    }
}
