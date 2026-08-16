package com.lianshengtong.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 管理后台服务启动类
 * <p>
 * 职责：管理员登录与权限校验、操作日志记录与AI异常监控、参数变更双人审批流程、
 * 商家审核/处罚、商品审核、B2B核验复核、风控管理、释放监控、存证查询、参数配置。
 * 通过 Feign 代理调用各业务服务完成管理操作。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.admin", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.admin", "com.lianshengtong.common"})
@EnableAsync
@MapperScan("com.lianshengtong.admin.mapper")
@EnableTransactionManagement
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
