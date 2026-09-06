package com.lianshengtong.lsc.security;

import com.lianshengtong.lsc.common.ErrorCode;
import com.lianshengtong.lsc.common.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        // 放行登录、注册、H2控制台、Actuator端点、静态资源
        if (path.contains("/auth/") || path.contains("/h2-console") || path.contains("/actuator") || path.contains("/error")) {
            chain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            writeUnauthorized(response);
            return;
        }

        try {
            String token = header.substring(7);
            Long userId = jwtUtil.getUserId(token);
            String userType = jwtUtil.getUserType(token);
            UserContext.set(userId, userType);
            chain.doFilter(request, response);
        } catch (Exception e) {
            log.debug("JWT 解析失败: {}", e.getMessage());
            writeUnauthorized(response);
        } finally {
            UserContext.clear();
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        R<Void> r = R.fail(ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage());
        response.getWriter().write(objectMapper.writeValueAsString(r));
    }
}
