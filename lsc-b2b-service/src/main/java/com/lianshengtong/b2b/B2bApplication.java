package com.lianshengtong.b2b;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * LSC B2B 交易服务启动类
 * <p>
 * 职责：管理商家间 LSC 1:1 流转订单全生命周期（创建、对手方确认、流转执行、取消、作废），
 * 调用 AI 网关对贸易真实性进行核验，调用账本服务执行原子化流转。
 * 基于 Seata AT 保障跨服务一致性，Redisson 保障订单级并发安全，
 * MyBatis-Plus 乐观锁(version) 防止订单状态并发覆写。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.b2b", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.b2b", "com.lianshengtong.common"})
@MapperScan("com.lianshengtong.b2b.mapper")
@EnableTransactionManagement
public class B2bApplication {

    public static void main(String[] args) {
        SpringApplication.run(B2bApplication.class, args);
    }
}
