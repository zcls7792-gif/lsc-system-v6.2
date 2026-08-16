package com.lianshengtong.release;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 释放计算服务启动类
 * <p>
 * 核心专利算法：基于核销率反馈的消费权益凭证动态释放调节方法
 * 驱动分布式批量处理引擎完成全网锁定转可用(L_LOCKED -> L_AVAILABLE)的原子操作。
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.release", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.release", "com.lianshengtong.common"})
@EnableAsync
@MapperScan("com.lianshengtong.release.mapper")
public class ReleaseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReleaseServiceApplication.class, args);
    }
}
