package com.lianshengtong.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.user.dto.LoginDTO;
import com.lianshengtong.user.dto.RegisterDTO;
import com.lianshengtong.user.dto.VerifyDTO;
import com.lianshengtong.user.entity.User;
import com.lianshengtong.user.service.UserService;
import com.lianshengtong.user.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G2 集成：UserController 的 HTTP 契约 + Jackson + JSR303 校验（standalone MockMvc 层）。
 * <p>不启动 Spring Boot ApplicationContext，避免 Nacos/Seata/MyBatis 等外部依赖。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerStandaloneTest {

    private MockMvc mvc;
    private final ObjectMapper om = new ObjectMapper();

    @Mock private UserService userService;
    @Mock private StringRedisTemplate srt;
    @Mock private ValueOperations<String, String> valueOps;

    private JwtUtil realJwtUtil() throws Exception {
        // 反射/手工构造一个真实 JwtUtil（因为 @PostConstruct 在非容器上下文下需要手动触发）
        JwtUtil util = new JwtUtil();
        java.lang.reflect.Field secret = JwtUtil.class.getDeclaredField("secret");
        secret.setAccessible(true); secret.set(util, "test-secret-must-be-at-least-32-bytes-long-ok!");
        java.lang.reflect.Field issuer = JwtUtil.class.getDeclaredField("issuer");
        issuer.setAccessible(true); issuer.set(util, "lsc-user-service");
        java.lang.reflect.Field exp = JwtUtil.class.getDeclaredField("expireMillis");
        exp.setAccessible(true); exp.setLong(util, 86_400_000L);
        util.init();
        return util;
    }

    @BeforeEach
    void setUp() throws Exception {
        JwtUtil jwt = realJwtUtil();
        UserController ctrl = new UserController(userService, jwt, srt);
        LocalValidatorFactoryBean v = new LocalValidatorFactoryBean();
        v.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(ctrl).setValidator(v).build();
        when(srt.opsForValue()).thenReturn(valueOps);
    }

    private RegisterDTO validRegister() {
        RegisterDTO r = new RegisterDTO();
        r.setMobile("13800138000");
        r.setPassword("password123");
        r.setNickname("测试用户");
        r.setReferralCode("REF-1");
        return r;
    }

    private User sampleUser(Long id) {
        User u = new User();
        u.setUserId(id);
        u.setMobile("13800138000");
        u.setNickname("测试用户");
        return u;
    }

    private String signedTokenFor(JwtUtil jwtUtil, String subject) {
        return jwtUtil.generateToken(subject, "consumer", Map.of("role", "c"));
    }

    private JwtUtil jwtu() throws Exception {
        // 给断言用的同 secret 实例
        return realJwtUtil();
    }

    @Nested
    @DisplayName("POST /api/user/register")
    class Register {
        @Test @DisplayName("合法 DTO → 200 + user JSON + 调用 register")
        void valid() throws Exception {
            when(userService.register(any())).thenReturn(sampleUser(10001L));
            mvc.perform(post("/api/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(validRegister())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userId").value(10001))
                    .andExpect(jsonPath("$.data.mobile").value("13800138000"));
            verify(userService).register(any());
        }

        @Test @DisplayName("非法手机号 → 400 @Pattern 校验失败，不调用 service")
        void invalidMobile() throws Exception {
            RegisterDTO r = validRegister(); r.setMobile("12345");
            mvc.perform(post("/api/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(r)))
                    .andExpect(status().isBadRequest());
            verify(userService, never()).register(any());
        }

        @Test @DisplayName("密码长度 <6 → 400 @Size 校验失败")
        void shortPassword() throws Exception {
            RegisterDTO r = validRegister(); r.setPassword("12");
            mvc.perform(post("/api/user/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(r)))
                    .andExpect(status().isBadRequest());
            verify(userService, never()).register(any());
        }
    }

    @Nested
    @DisplayName("POST /api/user/login")
    class Login {
        @Test @DisplayName("登录成功 → 200 + 带 token 的字符串")
        void loginSuccess() throws Exception {
            when(userService.login(any())).thenReturn("tok-xxx");
            LoginDTO dto = new LoginDTO();
            dto.setAccount("13800138000"); dto.setPassword("password123");
            mvc.perform(post("/api/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value("tok-xxx"));
            verify(userService).login(any());
        }

        @Test @DisplayName("缺少 password → 400")
        void missingPassword() throws Exception {
            LoginDTO dto = new LoginDTO(); dto.setAccount("13800138000");
            mvc.perform(post("/api/user/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
            verify(userService, never()).login(any());
        }
    }

    @Nested
    @DisplayName("POST /api/user/logout")
    class Logout {
        @Test @DisplayName("带有效 token → 200 且将其写入 Redis 黑名单")
        void validTokenBlacklist() throws Exception {
            JwtUtil u = jwtu();
            String tok = signedTokenFor(u, "10001");
            mvc.perform(post("/api/user/logout").header("Authorization", "Bearer " + tok))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            // 检查是否调用了 Redis opsForValue().set(...)
            verify(valueOps).set(
                    eq(com.lianshengtong.common.utils.RedisKeyPrefix.TOKEN_BLACKLIST + tok),
                    eq("1"),
                    any(Duration.class));
        }

        @Test @DisplayName("完全没传 token → 也 200（Controller 内有 try/catch）")
        void noToken() throws Exception {
            mvc.perform(post("/api/user/logout"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Nested
    @DisplayName("POST /api/user/verify")
    class Verify {
        @Test @DisplayName("实名信息合法 → 200")
        void valid() throws Exception {
            VerifyDTO dto = new VerifyDTO();
            dto.setUserId(10001L);
            dto.setRealName("张三");
            dto.setIdCardNo("110101199003071234");
            mvc.perform(post("/api/user/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
            verify(userService).verify(any());
        }

        @Test @DisplayName("身份证格式非法 → 400")
        void invalidIdCard() throws Exception {
            VerifyDTO dto = new VerifyDTO();
            dto.setUserId(10001L); dto.setRealName("张三"); dto.setIdCardNo("abc");
            mvc.perform(post("/api/user/verify")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest());
            verify(userService, never()).verify(any());
        }
    }

    @Nested
    @DisplayName("GET /api/user/info")
    class Info {
        @Test @DisplayName("无参调用 service.getUserInfo")
        void viaService() throws Exception {
            when(userService.getUserInfo(10001L)).thenReturn(sampleUser(10001L));
            mvc.perform(get("/api/user/info").param("userId", "10001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.userId").value(10001));
            verify(userService).getUserInfo(10001L);
        }
    }
}
