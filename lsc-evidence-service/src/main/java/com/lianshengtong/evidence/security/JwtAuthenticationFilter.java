package com.lianshengtong.evidence.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.common.result.R;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final JwtUtil jwtUtil;
    private final TokenBlacklistService tokenBlacklistService;

    private static final Set<String> WHITE_LIST = Set.of(
            "/api/auth/login",
            "/api/auth/health",
            "/api/auth/refresh",
            "/actuator/health",
            "/actuator/info",
            "/swagger-ui.html",
            "/v3/api-docs",
            "/error"
    );

    public JwtAuthenticationFilter(JwtUtil jwtUtil, TokenBlacklistService tokenBlacklistService) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();

        if (isWhitelisted(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "缺少认证令牌，请先登录");
            return;
        }

        String token = authHeader.substring(7);
        JwtUtil.Claims claims = jwtUtil.validateToken(token);
        if (claims == null) {
            writeUnauthorized(response, "认证令牌无效或已过期");
            return;
        }

        // 强制校验：API 请求必须使用 Access Token，Refresh Token 仅限 /api/auth/refresh 端点使用
        if ("refresh".equals(claims.type())) {
            writeUnauthorized(response, "不能使用刷新令牌访问受保护接口");
            return;
        }

        // 检查 Token 是否已被撤销 (登出/刷新后加入黑名单)
        String jti = jwtUtil.tokenJti(token);
        if (tokenBlacklistService.isRevoked(jti)) {
            writeUnauthorized(response, "认证令牌已被撤销，请重新登录");
            return;
        }

        request.setAttribute("currentUser", claims.username());
        request.setAttribute("currentRole", claims.role());
        request.setAttribute("currentTokenType", claims.type());
        filterChain.doFilter(request, response);
    }

    private boolean isWhitelisted(String path) {
        for (String white : WHITE_LIST) {
            if (path.equals(white) || path.startsWith(white + "/")) return true;
        }
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        R<Void> error = R.fail(401, message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
