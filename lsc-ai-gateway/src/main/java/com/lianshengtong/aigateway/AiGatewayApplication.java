package com.lianshengtong.aigateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI网关服务启动类
 * <p>
 * AI模型服务统一入口，负责请求路由、模型版本管理、推理结果缓存及降级熔断。
 * 所有上游业务服务通过 Feign 或 HTTP 调用本网关，由网关屏蔽外部AI模型差异，
 * 统一处理超时(10s)、降级(回退人工审核)、缓存与熔断。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.aigateway", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.aigateway", "com.lianshengtong.common"})
public class AiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiGatewayApplication.class, args);
    }
}
