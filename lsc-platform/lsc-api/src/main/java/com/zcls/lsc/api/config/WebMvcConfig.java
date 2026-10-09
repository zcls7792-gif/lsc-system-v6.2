package com.zcls.lsc.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 全局 CORS 配置。
 *
 * 开发期由 Vite 代理处理跨域；生产环境 H5 域名与 API 域名不同时，
 * 需通过 app.cors.allowed-origins 配置允许的来源。
 *
 * 例：app.cors.allowed-origins=https://h5.lsc.example.com,https://m.lsc.example.com
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = allowedOrigins.split(",");
        // allowCredentials=true 时不能用 "*"，需改用 allowedOriginPatterns
        boolean useWildcard = origins.length == 1 && "*".equals(origins[0].trim());
        if (useWildcard) {
            registry.addMapping("/v1/**")
                    .allowedOriginPatterns("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        } else {
            registry.addMapping("/v1/**")
                    .allowedOrigins(origins)
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .maxAge(3600);
        }
    }
}
