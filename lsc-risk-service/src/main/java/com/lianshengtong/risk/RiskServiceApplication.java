package com.lianshengtong.risk;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 风控服务启动类
 * <p>
 * 职责：固定规则风控(批量下单、异常混合支付、高频套利、异地操作) + AI动态风控评分。
 * 高风险自动限制(暂停LSC支付、冻结账户)并推送人工审核；中低风险仅记录日志。
 * 通过 Feign 调用 lsc-ai-gateway 进行动态风控评分。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.risk", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.risk", "com.lianshengtong.common"})
@MapperScan("com.lianshengtong.risk.mapper")
@EnableTransactionManagement
public class RiskServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskServiceApplication.class, args);
    }
}
