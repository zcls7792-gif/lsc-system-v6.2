package com.lianshengtong.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 开发模式认证拦截器：从 X-User-Id 请求头读取用户ID并设置为请求属性。
 * 生产环境应替换为 JWT/OAuth 认证过滤器。
 */
@Component
public class DevAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isEmpty()) {
            try {
                request.setAttribute("userId", Long.parseLong(userIdHeader));
            } catch (NumberFormatException ignored) {
            }
        }
        return true;
    }
}
