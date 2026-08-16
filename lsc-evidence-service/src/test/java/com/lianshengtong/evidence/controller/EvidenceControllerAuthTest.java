package com.lianshengtong.evidence.controller;

import com.lianshengtong.common.result.R;
import com.lianshengtong.evidence.config.EvidenceGlobalExceptionHandler;
import com.lianshengtong.evidence.security.JwtAuthenticationFilter;
import com.lianshengtong.evidence.security.JwtUtil;
import com.lianshengtong.evidence.service.EvidenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.redisson.api.RedissonClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {EvidenceController.class},
        includeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.lianshengtong\\.common\\.exception\\..*"
        ),
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.nacos.config.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false"
        })
@Import({EvidenceGlobalExceptionHandler.class, JwtAuthenticationFilter.class})
@AutoConfigureMockMvc(addFilters = true)
@ActiveProfiles("standalone")
@DisplayName("EvidenceController 集成鉴权测试")
class EvidenceControllerAuthTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EvidenceService evidenceService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private com.lianshengtong.evidence.security.LoginAttemptService loginAttemptService;

    @MockBean
    private com.lianshengtong.evidence.security.TokenBlacklistService tokenBlacklistService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private RedissonClient redissonClient;

    private static final String BASE_URL = "/api/evidence";
    private String accessToken;

    @BeforeEach
    void setUp() {
        accessToken = "valid-access-token";
        JwtUtil.Claims claims = new JwtUtil.Claims("alice", "ADMIN", "access",
                System.currentTimeMillis() + 3600_000L);
        when(jwtUtil.validateToken(accessToken)).thenReturn(claims);

        // 其他 token 验证返回 null (针对 refresh token 等)
        when(jwtUtil.validateToken("expired-token")).thenReturn(null);
        when(jwtUtil.validateToken("invalid-token")).thenReturn(null);
    }

    private MockHttpServletRequestBuilder auth(String token) {
        return token == null ? get("/x") : get("/x").header("Authorization", "Bearer " + token);
    }

    @Test
    @DisplayName("GET /list - 未带 Token 返回 401")
    void list_unauthorized() throws Exception {
        mockMvc.perform(get(BASE_URL + "/list").param("page", "1").param("size", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /list - 带 Access Token 成功")
    void list_authorized() throws Exception {
        when(evidenceService.listPage(1, 20, null, null, null, null, null))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>());

        mockMvc.perform(get(BASE_URL + "/list")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("GET /list - 带无效 Token 返回 401")
    void list_invalidToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/list")
                        .header("Authorization", "Bearer " + "invalid-token")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /list - 带过期 Token 返回 401")
    void list_expiredToken() throws Exception {
        mockMvc.perform(get(BASE_URL + "/list")
                        .header("Authorization", "Bearer " + "expired-token")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /list - 带 Refresh Token 被拒绝访问")
    void list_refreshTokenRejected() throws Exception {
        // Refresh token 的 validateToken 仍会返回 claims，但类型为 refresh
        JwtUtil.Claims refreshClaims = new JwtUtil.Claims("bob", "AUDITOR", "refresh",
                System.currentTimeMillis() + 3600_000L);
        when(jwtUtil.validateToken("refresh-token")).thenReturn(refreshClaims);

        mockMvc.perform(get(BASE_URL + "/list")
                        .header("Authorization", "Bearer " + "refresh-token")
                        .param("page", "1").param("size", "20"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /save - 未认证返回 401")
    void save_unauthorized() throws Exception {
        mockMvc.perform(post(BASE_URL + "/save")
                        .param("bizType", "ORDER")
                        .param("bizId", "ORD-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /save - 带有效 Token 成功")
    void save_authorized() throws Exception {
        when(evidenceService.saveEvidence("ORDER", "ORD-1", null, null)).thenReturn("REC-001");

        mockMvc.perform(post(BASE_URL + "/save")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("bizType", "ORDER")
                        .param("bizId", "ORD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("REC-001"));
    }

    @Test
    @DisplayName("GET /query - 带有效 Token 成功")
    void query_authorized() throws Exception {
        when(evidenceService.query("ORDER", "ORD-1")).thenReturn(List.of());

        mockMvc.perform(get(BASE_URL + "/query")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("bizType", "ORDER")
                        .param("bizId", "ORD-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("GET /{id} - 未带 Token 返回 401")
    void detail_unauthorized() throws Exception {
        mockMvc.perform(get(BASE_URL + "/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /{id} - 带有效 Token 成功")
    void detail_authorized() throws Exception {
        com.lianshengtong.evidence.entity.BlockchainRecord rec = new com.lianshengtong.evidence.entity.BlockchainRecord();
        rec.setId(1L);
        rec.setBizType("ORDER");
        rec.setBizId("ORD-1");
        when(evidenceService.getById(1L)).thenReturn(rec);

        mockMvc.perform(get(BASE_URL + "/1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @DisplayName("POST /snapshot - 带 Token 成功")
    void snapshot_authorized() throws Exception {
        com.lianshengtong.evidence.entity.DailySnapshotRecord snap = new com.lianshengtong.evidence.entity.DailySnapshotRecord();
        snap.setStatus(1);
        when(evidenceService.dailySnapshot(java.util.Optional.ofNullable(java.time.LocalDate.parse("2026-08-07")).orElse(null)))
                .thenReturn(snap);

        mockMvc.perform(post(BASE_URL + "/snapshot")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST /snapshot - 未认证返回 401")
    void snapshot_unauthorized() throws Exception {
        mockMvc.perform(post(BASE_URL + "/snapshot")
                        .param("date", "2026-08-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /verify-report - 带 Token 成功")
    void verifyReport_authorized() throws Exception {
        Map<String, Object> result = Map.of("total", 10, "passed", 10);
        when(evidenceService.verifyReport(java.time.LocalDate.parse("2026-08-07"))).thenReturn(result);

        mockMvc.perform(get(BASE_URL + "/verify-report")
                        .header("Authorization", "Bearer " + accessToken)
                        .param("date", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST /verify - 带 Token 成功")
    void verifyPost_authorized() throws Exception {
        when(evidenceService.verify(java.time.LocalDate.parse("2026-08-07"))).thenReturn(true);

        Map<String, String> body = Map.of("date", "2026-08-07");
        mockMvc.perform(post(BASE_URL + "/verify")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @DisplayName("未认证 - Bearer 前缀为空返回 401")
    void unauth_emptyBearer() throws Exception {
        mockMvc.perform(post(BASE_URL + "/save")
                        .header("Authorization", "Bearer ")
                        .param("bizType", "ORDER")
                        .param("bizId", "ORD-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("未认证 - 非 Bearer 格式返回 401")
    void unauth_badFormat() throws Exception {
        mockMvc.perform(post(BASE_URL + "/save")
                        .header("Authorization", "Basic xyz")
                        .param("bizType", "ORDER")
                        .param("bizId", "ORD-1"))
                .andExpect(status().isUnauthorized());
    }
}
