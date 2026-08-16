package com.lianshengtong.order;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * LSC 订单服务启动类
 * <p>
 * 职责：管理线上商城/线下消费订单全生命周期（创建、混合支付、完成、取消、退款、部分退款），
 * 调用账本服务执行 LSC 扣减/退回与发行回滚，调用推广服务通知首单。
 * 基于 Seata AT 保障跨服务一致性，Redisson 保障订单级并发安全。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.order", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.order", "com.lianshengtong.common"})
@MapperScan("com.lianshengtong.order.mapper")
@EnableTransactionManagement
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
