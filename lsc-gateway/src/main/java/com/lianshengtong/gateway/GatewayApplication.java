package com.lianshengtong.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关启动类
 * <p>
 * 基于 Spring Cloud Gateway (WebFlux 反应式架构) 实现:
 * <ul>
 *   <li>统一路由: 15 条路由覆盖全部微服务, 通过 Nacos 服务发现 lb:// 负载均衡</li>
 *   <li>JWT 鉴权: {@link com.lianshengtong.gateway.filter.JwtAuthFilter} 全局过滤器,
 *       兼容用户令牌(lsc-user-service 签发)与管理员令牌(lsc-admin-service 签发),
 *       鉴权通过后将用户信息透传至下游服务(X-User-Id / X-User-Type / X-Admin-Role)</li>
 *   <li>IP 限流: RequestRateLimiter 基于 Redis 令牌桶, 默认 200/s, 核心账务 50/s, AI 接口 20/s</li>
 *   <li>全局 CORS: 支持 Web/小程序/移动端跨域</li>
 * </ul>
 * 仅扫描 gateway 自身包, 不扫描 lsc-common 的 Servlet/MyBatis 配置以避免 WebFlux 冲突。
 * </p>
 */
@SpringBootApplication(scanBasePackages = "com.lianshengtong.gateway")
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
