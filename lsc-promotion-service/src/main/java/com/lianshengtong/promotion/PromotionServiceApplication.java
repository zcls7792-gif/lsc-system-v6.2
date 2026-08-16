package com.lianshengtong.promotion;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 推广服务启动类
 * <p>
 * 职责：一级推荐首单判定、奖励计算与划转(推荐人锁定池 -> 可用池)、退款全额回滚、
 * 挂账表(promotion_pending)每日自动补发扫描。
 * 严格限定一级推荐(users.referrer_id 单一外键约束，禁止链式)。
 * 奖励划转通过 Feign 调用 lsc-ledger-service 完成，基于 Seata AT 保障跨服务一致性。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.promotion", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.promotion", "com.lianshengtong.common"})
@EnableScheduling
@MapperScan("com.lianshengtong.promotion.mapper")
@EnableTransactionManagement
public class PromotionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PromotionServiceApplication.class, args);
    }
}
