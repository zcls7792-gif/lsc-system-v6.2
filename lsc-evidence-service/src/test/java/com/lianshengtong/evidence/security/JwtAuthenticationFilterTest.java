package com.lianshengtong.evidence.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("JwtAuthenticationFilter 单元测试")
class JwtAuthenticationFilterTest {

    private JwtUtil jwtUtil;
    private TokenBlacklistService tokenBlacklistService;
    private JwtAuthenticationFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private StringWriter sw;
    private PrintWriter pw;

    @BeforeEach
    void setUp() throws Exception {
        jwtUtil = new JwtUtil("test-secret-key-2026-must-be-32-bytes", 3600_000L, 7 * 24 * 3600_000L);
        tokenBlacklistService = mock(TokenBlacklistService.class);
        when(tokenBlacklistService.isRevoked(anyString())).thenReturn(false);
        filter = new JwtAuthenticationFilter(jwtUtil, tokenBlacklistService);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        sw = new StringWriter();
        pw = new PrintWriter(sw);
        doReturn(pw).when(response).getWriter();
    }

    private String responseBody() {
        pw.flush();
        return sw.toString();
    }

    @Test
    @DisplayName("白名单 - /api/auth/login 应放行")
    void whitelist_login() throws Exception {
        when(request.getServletPath()).thenReturn("/api/auth/login");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("白名单 - /api/auth/health 应放行")
    void whitelist_health() throws Exception {
        when(request.getServletPath()).thenReturn("/api/auth/health");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("白名单 - /api/auth/refresh 应放行")
    void whitelist_refresh() throws Exception {
        when(request.getServletPath()).thenReturn("/api/auth/refresh");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("白名单 - /actuator/health 应放行")
    void whitelist_actuator() throws Exception {
        when(request.getServletPath()).thenReturn("/actuator/health");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("白名单 - /v3/api-docs 应放行")
    void whitelist_openapi() throws Exception {
        when(request.getServletPath()).thenReturn("/v3/api-docs");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("白名单 - /error 应放行")
    void whitelist_error() throws Exception {
        when(request.getServletPath()).thenReturn("/error");
        filter.doFilterInternal(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("未认证 - 无 Authorization 头返回 401")
    void unauth_noHeader() throws Exception {
        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("未认证 - Authorization 不以 Bearer 开头返回 401")
    void unauth_noBearer() throws Exception {
        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn("Basic xxx");

        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("未认证 - 空 Bearer Token 返回 401")
    void unauth_emptyToken() throws Exception {
        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn("Bearer ");

        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("未认证 - 过期 Access Token 返回 401")
    void unauth_expiredToken() throws Exception {
        JwtUtil shortUtil = new JwtUtil("test-secret-key-2026-secure", 50L, 50L);
        String token = shortUtil.generateToken("alice", "ADMIN");
        filter = new JwtAuthenticationFilter(shortUtil, tokenBlacklistService);

        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        Thread.sleep(120L);
        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("安全 - Refresh Token 不可访问受保护 API")
    void security_refreshBlocked() throws Exception {
        String refresh = jwtUtil.generateRefreshToken("alice", "ADMIN");

        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + refresh);

        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("安全 - 无效签名的 Token 返回 401")
    void security_invalidSignature() throws Exception {
        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn("Bearer this.is.not.a.valid.jwt");

        filter.doFilterInternal(request, response, filterChain);
        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("认证成功 - Access Token 有效时注入 currentUser/currentRole")
    void auth_success() throws Exception {
        String token = jwtUtil.generateToken("alice", "ADMIN");

        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute("currentUser", "alice");
        verify(request).setAttribute("currentRole", "ADMIN");
        verify(request).setAttribute("currentTokenType", "access");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("认证成功 - 多个角色场景正确注入")
    void auth_roles() throws Exception {
        String token = jwtUtil.generateToken("auditor", "AUDITOR");

        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute("currentUser", "auditor");
        verify(request).setAttribute("currentRole", "AUDITOR");
    }

    @Test
    @DisplayName("未认证 - 响应体为 JSON 结构")
    void unauth_responseBody() throws Exception {
        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilterInternal(request, response, filterChain);

        String body = responseBody();
        assertTrue(body.contains("401"), "响应体应包含 401");
        assertTrue(body.contains("缺少认证令牌"), "响应体应包含错误描述");
    }

    @Test
    @DisplayName("构造器 - JwtUtil 注入正确")
    void constructor_injection() {
        JwtUtil util = new JwtUtil("k", 1000L);
        JwtAuthenticationFilter f = new JwtAuthenticationFilter(util, tokenBlacklistService);
        assertNotNull(f);
    }

    @Test
    @DisplayName("安全 - 已撤销(黑名单)的 Token 被拒绝")
    void security_revokedTokenBlocked() throws Exception {
        String token = jwtUtil.generateToken("alice", "ADMIN");
        String jti = jwtUtil.tokenJti(token);

        when(tokenBlacklistService.isRevoked(jti)).thenReturn(true);
        when(request.getServletPath()).thenReturn("/api/evidence/list");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    @DisplayName("安全 - Token JTI 计算并传递给黑名单检查")
    void security_jtiComputedAndChecked() throws Exception {
        String token = jwtUtil.generateToken("bob", "AUDITOR");
        String expectedJti = jwtUtil.tokenJti(token);

        when(request.getServletPath()).thenReturn("/api/evidence/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(tokenBlacklistService.isRevoked(expectedJti)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        verify(tokenBlacklistService).isRevoked(expectedJti);
        verify(filterChain).doFilter(request, response);
    }
}

