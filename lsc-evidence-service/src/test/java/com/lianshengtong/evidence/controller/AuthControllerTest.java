package com.lianshengtong.evidence.controller;

import com.lianshengtong.common.result.R;
import com.lianshengtong.evidence.dto.LoginRequest;
import com.lianshengtong.evidence.dto.LoginResponse;
import com.lianshengtong.evidence.security.JwtUtil;
import com.lianshengtong.evidence.security.LoginAttemptService;
import com.lianshengtong.evidence.security.TokenBlacklistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class},
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false"
        })
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("standalone")
@DisplayName("AuthController 单元测试")
class AuthControllerTest {

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
        // 清理 userStore 实例字段避免测试间干扰
        Field field = AuthController.class.getDeclaredField("userStore");
        field.setAccessible(true);
        ((Map<?, ?>) field.get(authController)).clear();
    }

    // ==================== Login 接口 ====================

    @Test
    @DisplayName("登录 - 用户名密码合法返回 access + refresh token")
    void login_success() throws Exception {
        when(passwordEncoder.matches("admin123", "encoded-admin")).thenReturn(true);
        when(jwtUtil.generateToken("admin", "ADMIN")).thenReturn("access-token-xxx");
        when(jwtUtil.generateRefreshToken("admin", "ADMIN")).thenReturn("refresh-token-xxx");
        injectUser("admin", "encoded-admin", "ADMIN");

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.token").value("access-token-xxx"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token-xxx"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    @DisplayName("登录 - 用户不存在返回 401")
    void login_userNotFound() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("ghost");
        req.setPassword("pwd");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录 - 密码错误返回 401")
    void login_wrongPassword() throws Exception {
        when(passwordEncoder.matches("wrong", "encoded-admin")).thenReturn(false);
        injectUser("admin", "encoded-admin", "ADMIN");

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("wrong");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录 - 用户名为空时触发参数校验")
    void login_usernameBlank() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("");
        req.setPassword("pwd");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("登录 - 密码为空时触发参数校验")
    void login_passwordBlank() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("登录 - 不同用户角色返回正确")
    void login_roles() throws Exception {
        when(passwordEncoder.matches("auditor123", "auditor-encoded")).thenReturn(true);
        when(jwtUtil.generateToken("auditor", "AUDITOR")).thenReturn("auditor-access");
        when(jwtUtil.generateRefreshToken("auditor", "AUDITOR")).thenReturn("auditor-refresh");
        injectUser("auditor", "auditor-encoded", "AUDITOR");

        LoginRequest req = new LoginRequest();
        req.setUsername("auditor");
        req.setPassword("auditor123");

        mockMvc.perform(post(BASE_URL + "/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(jsonPath("$.data.role").value("AUDITOR"));
    }

    // ==================== Refresh 接口 ====================

    @Test
    @DisplayName("刷新 - 使用有效 Refresh Token 返回新 Access Token")
    void refresh_success() throws Exception {
        JwtUtil.Claims claims = new JwtUtil.Claims("admin", "ADMIN", "refresh",
                System.currentTimeMillis() + 3600_000L);
        when(jwtUtil.tokenJti("valid-refresh")).thenReturn("jti-valid");
        when(jwtUtil.validateRefreshToken("valid-refresh")).thenReturn(claims);
        when(jwtUtil.generateToken("admin", "ADMIN")).thenReturn("new-access-token");
        when(jwtUtil.generateRefreshToken("admin", "ADMIN")).thenReturn("new-refresh-token");
        when(tokenBlacklistService.isRevoked("jti-valid")).thenReturn(false);

        mockMvc.perform(post(BASE_URL + "/refresh")
                        .header("Authorization", "Bearer valid-refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").value("new-access-token"))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    @DisplayName("刷新 - 无 Authorization 头返回 401")
    void refresh_noHeader() throws Exception {
        mockMvc.perform(post(BASE_URL + "/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("刷新 - Authorization 不以 Bearer 开头返回 401")
    void refresh_noBearer() throws Exception {
        mockMvc.perform(post(BASE_URL + "/refresh")
                        .header("Authorization", "Basic xxx"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("刷新 - 无效 Refresh Token 返回 401")
    void refresh_invalidToken() throws Exception {
        when(jwtUtil.tokenJti("bad-token")).thenReturn("jti-bad");
        when(jwtUtil.validateRefreshToken("bad-token")).thenReturn(null);
        when(tokenBlacklistService.isRevoked("jti-bad")).thenReturn(false);

        mockMvc.perform(post(BASE_URL + "/refresh")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("刷新 - 使用 Access Token 刷新被拒绝")
    void refresh_accessTokenRejected() throws Exception {
        when(jwtUtil.tokenJti("access-token")).thenReturn("jti-access");
        when(jwtUtil.validateRefreshToken("access-token")).thenReturn(null);
        when(tokenBlacklistService.isRevoked("jti-access")).thenReturn(false);

        mockMvc.perform(post(BASE_URL + "/refresh")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(jsonPath("$.code").value(401));
    }

    // ==================== /me 接口 ====================

    @Test
    @DisplayName("当前用户 - 未认证返回 401")
    void me_unauthenticated() throws Exception {
        mockMvc.perform(get(BASE_URL + "/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("当前用户 - 已认证返回用户名和角色")
    void me_authenticated() throws Exception {
        mockMvc.perform(get(BASE_URL + "/me")
                        .requestAttr("currentUser", "admin")
                        .requestAttr("currentRole", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    // ==================== Health 接口 ====================

    @Test
    @DisplayName("健康检查 - 直接返回 ok")
    void health_ok() throws Exception {
        mockMvc.perform(get(BASE_URL + "/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("ok"))
                .andExpect(jsonPath("$.data.service").value("evidence-auth"));
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过反射向 userStore 实例字段注入测试用户 (UserAccount 为 private record)
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

    // ==================== 响应结构 ====================

    @Test
    @DisplayName("响应结构 - R.success 正确包装")
    void response_structure() throws Exception {
        // 登录成功数据结构已在 login_success 验证
        LoginResponse resp = new LoginResponse("t", 7200L, "u", "R");
        assertEquals("Bearer", resp.getTokenType());
        resp.setRefreshToken("rt");
        resp.setRefreshExpiresIn(3600L);
        assertEquals("rt", resp.getRefreshToken());
        assertEquals(3600L, resp.getRefreshExpiresIn());
    }

    @Test
    @DisplayName("响应结构 - LoginRequest getter/setter 正确")
    void loginRequest_structure() {
        LoginRequest req = new LoginRequest();
        req.setUsername("u");
        req.setPassword("p");
        req.setClientType("web");
        assertEquals("u", req.getUsername());
        assertEquals("p", req.getPassword());
        assertEquals("web", req.getClientType());
    }

    // ==================== 响应字段完整性 ====================

    @Test
    @DisplayName("登录响应 - tokenType 默认为 Bearer")
    void loginResponse_defaultType() {
        LoginResponse r = new LoginResponse();
        assertEquals("Bearer", r.getTokenType());
    }
}
