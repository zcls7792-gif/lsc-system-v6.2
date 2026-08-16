package com.lianshengtong.reconciliation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 对账服务启动类
 * <p>
 * 职责：每日凌晨比对支付机构流水与 LSC 账本流水、差异报告生成、结果哈希上链存证。
 * 通过 Feign 调用 lsc-evidence-service 完成哈希上链。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.reconciliation", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.reconciliation", "com.lianshengtong.common"})
@EnableScheduling
@MapperScan("com.lianshengtong.reconciliation.mapper")
public class ReconciliationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReconciliationServiceApplication.class, args);
    }
}
