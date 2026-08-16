package com.lianshengtong.mall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 权益商城服务启动类
 * <p>
 * 职责：商品发布(人民币价格与LSC价格强制一致，共用 price 字段)、上下架、
 * 分页与类目筛选查询、混合支付计算(LSC 0~总价，人民币补足，1:1)、AI审核结果回调更新。
 * 通过 Feign 调用 lsc-ai-gateway(商品AI审核) 与 lsc-order-service(创建订单)。
 * </p>
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.mall", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.mall", "com.lianshengtong.common"})
@MapperScan("com.lianshengtong.mall.mapper")
@EnableTransactionManagement
public class MallServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MallServiceApplication.class, args);
    }
}
