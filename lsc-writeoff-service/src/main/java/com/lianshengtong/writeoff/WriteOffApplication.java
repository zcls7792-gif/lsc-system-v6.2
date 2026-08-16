package com.lianshengtong.writeoff;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * LSC 核销服务启动类
 * <p>
 * 职责：商家将可用 LSC 核销兑换现金(100:87比例)，执行资格/次数/限额/余额四重校验，
 * 调用支付机构划拨资金、调用账本服务扣减 LSC 并销毁，记录核销流水并更新商家最近核销日期。
 * 基于 Seata AT 保障跨服务一致性，Redisson 保障商家级并发安全，
 * 幂等通过 order_no 唯一索引 + version 乐观锁双重校验。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.writeoff", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.writeoff", "com.lianshengtong.common"})
@MapperScan("com.lianshengtong.writeoff.mapper")
@EnableTransactionManagement
public class WriteOffApplication {

    public static void main(String[] args) {
        SpringApplication.run(WriteOffApplication.class, args);
    }
}
