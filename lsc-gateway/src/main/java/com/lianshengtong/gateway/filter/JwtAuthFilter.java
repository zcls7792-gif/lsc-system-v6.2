package com.lianshengtong.gateway.filter;

import com.alibaba.fastjson2.JSON;
import com.lianshengtong.common.result.R;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * JWT 全局鉴权过滤器
 * <p>
 * 执行顺序 -100 (在限流过滤器之后、路由转发之前)。
 * 流程:
 * <ol>
 *   <li>白名单路径(登录/注册/文档/监控)直接放行</li>
 *   <li>提取 Authorization: Bearer &lt;token&gt;</li>
 *   <li>依次尝试用户令牌(issuer=lsc-user-service)与管理员令牌(issuer=lsc-admin-service)验签</li>
 *   <li>验签通过: 透传 X-User-Id / X-User-Type / X-Admin-Role / X-Client-Ip 至下游,
 *       并保留原始 Authorization 头供下游服务直接读取</li>
 *   <li>验签失败: 返回 401 JSON (R.fail(401, msg))</li>
 * </ol>
 * </p>
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Value("${lsc.jwt.secret}")
    private String userSecret;
    @Value("${lsc.jwt.issuer:lsc-user-service}")
    private String userIssuer;
    @Value("${lsc.admin.jwt.secret}")
    private String adminSecret;
    @Value("${lsc.admin.jwt.issuer:lsc-admin-service}")
    private String adminIssuer;
    @Value("${lsc.gateway.auth-whitelist:}")
    private String whitelistStr;

    private List<String> whitelist;
    private SecretKey userKey;
    private SecretKey adminKey;

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @PostConstruct
    public void init() {
        if (userSecret == null || userSecret.isBlank()) {
            throw new IllegalStateException("生产环境必须配置 lsc.jwt.secret，禁止使用默认值");
        }
        if (adminSecret == null || adminSecret.isBlank()) {
            throw new IllegalStateException("生产环境必须配置 lsc.admin.jwt.secret，禁止使用默认值");
        }
        this.userKey = Keys.hmacShaKeyFor(userSecret.getBytes(StandardCharsets.UTF_8));
        this.adminKey = Keys.hmacShaKeyFor(adminSecret.getBytes(StandardCharsets.UTF_8));
        this.whitelist = (whitelistStr == null || whitelistStr.isBlank())
                ? List.of()
                : java.util.Arrays.stream(whitelistStr.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. 白名单放行
        if (isWhitelisted(path)) {
            return chain.filter(exchange);
        }

        // 2. 提取 Bearer Token
        String auth = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (auth == null || !auth.startsWith("Bearer ")) {
            return unauthorized(exchange, "缺少认证令牌");
        }
        String token = auth.substring(7);
        if (token.isBlank()) {
            return unauthorized(exchange, "认证令牌为空");
        }

        // 3. 验签 (用户令牌 -> 管理员令牌)
        Claims claims;
        try {
            claims = parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT 验签失败 path={} reason={}", path, e.getMessage());
            return unauthorized(exchange, "认证令牌无效或已过期");
        }

        // 4. 透传用户信息至下游
        String clientIp = resolveClientIp(request);
        ServerHttpRequest mutated = request.mutate()
                .header("X-User-Id", claims.getSubject())
                .header("X-User-Type", String.valueOf(claims.getOrDefault("userType", "")))
                .header("X-Client-Ip", clientIp)
                .build();
        // 管理员令牌额外透传角色
        if (adminIssuer.equals(claims.getIssuer()) && claims.get("role") != null) {
            mutated = mutated.mutate()
                    .header("X-Admin-Role", String.valueOf(claims.get("role")))
                    .build();
        }
        return chain.filter(exchange.mutate().request(mutated).build());
    }

    /** 依次尝试用户令牌与管理员令牌验签 */
    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(userKey)
                    .requireIssuer(userIssuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException userEx) {
            return Jwts.parser()
                    .verifyWith(adminKey)
                    .requireIssuer(adminIssuer)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        }
    }

    private boolean isWhitelisted(String path) {
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        for (String pattern : whitelist) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /** 解析真实客户端 IP (优先取 X-Forwarded-For 首段, 兜底 remoteAddress) */
    private String resolveClientIp(ServerHttpRequest request) {
        String xff = request.getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String real = request.getHeaders().getFirst("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getHostString();
        }
        return "unknown";
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = JSON.toJSONString(R.fail(401, message));
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
