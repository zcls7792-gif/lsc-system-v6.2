package com.zcls.lsc.api.auth;

import com.zcls.lsc.api.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.List;

/**
 * 鉴权过滤器：
 *  - 白名单路径放行（C 端公开 GET + 管理端 + Swagger + 测试端点）
 *  - 其他路径要求 Authorization: Bearer <token>
 *  - 解析 token 注入 userId 到 requestAttribute
 *
 * 设计遵循经验：单点鉴权，边界稳定（白名单以路径前缀匹配）。
 */
@Configuration
public class AuthFilterConfig {

    /** 白名单路径前缀（精确前缀匹配，避免 contains 误判）。 */
    private static final List<String> WHITELIST = List.of(
            "/v1/auth/login",             // 登录入口（短信/微信）
            "/v1/products",              // C 端商品公开接口（GET，无登录）
            "/v1/skus",                   // C 端 SKU 公开接口
            "/v1/coupons",                // 优惠券公开查询
            "/admin/risk-rules",          // 风控规则查询（管理端测试用）
            "/admin/legal-entities",      // 法律主体查询（管理端测试用）
            "/admin/business-profiles",   // B 端资质查询（管理端测试用）
            "/admin/admin-roles",        // 角色查询（管理端测试用）
            "/v3/api-docs",               // OpenAPI
            "/swagger",                   // Swagger UI
            "/internal/"                  // 内部回调（支付/退款回调，由网关隔离）
    );

    private static final ObjectMapper JSON = new ObjectMapper();

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter() {
        FilterRegistrationBean<AuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new AuthFilter());
        reg.addUrlPatterns("/*");
        reg.setName("authFilter");
        reg.setOrder(1);
        return reg;
    }

    /** 真实过滤器实现。 */
    public static class AuthFilter implements Filter {

        @Override
        public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest httpReq = (HttpServletRequest) req;
            HttpServletResponse httpResp = (HttpServletResponse) res;

            String path = httpReq.getRequestURI();

            // 1) 白名单放行
            if (isWhitelisted(path)) {
                chain.doFilter(req, res);
                return;
            }

            // 2) 解析 Authorization 头
            String auth = httpReq.getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                reject(httpResp, 401, "missing or invalid Authorization header");
                return;
            }

            String token = auth.substring(7).trim();
            try {
                long userId = JwtUtil.parseUserId(token);
                httpReq.setAttribute("userId", userId);
                chain.doFilter(req, res);
            } catch (JwtException e) {
                reject(httpResp, 401, "invalid token: " + e.getMessage());
            } catch (Exception e) {
                reject(httpResp, 500, "auth error: " + e.getMessage());
            }
        }

        private boolean isWhitelisted(String path) {
            for (String prefix : WHITELIST) {
                if (path.startsWith(prefix)) return true;
            }
            return false;
        }

        private void reject(HttpServletResponse resp, int code, String msg) throws IOException {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(JSON.writeValueAsString(
                    ApiResponse.<Object>fail(code, msg)));
        }
    }
}
