package com.lianshengtong.release;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 释放计算服务启动类
 * <p>
 * 核心专利算法：基于核销率反馈的消费权益凭证动态释放调节方法
 * 驱动分布式批量处理引擎完成全网锁定转可用(L_LOCKED -> L_AVAILABLE)的原子操作。
 * </p>
 * <p>
 * 【为何要 excludeFilters】：lsc-common 包中的若干 @Configuration 依赖了 RabbitMQ/Cache 等
 * 生产环境通常会额外启动的组件，当这些组件的 starter 未被显式引入时，Spring 在扫描过程中
 * 会因 Class 内省（ReflectionUtils.getDeclaredMethods）抛出 NoClassDefFoundError 导致应用启动失败。
 * 这里使用 REGEX 模式按类名字符串过滤，不需要目标类被加载，是最安全的处理方式。
 * </p>
 */
@SpringBootApplication
@ComponentScan(
    basePackages = {"com.lianshengtong.release", "com.lianshengtong.common"},
    excludeFilters = {
        // 1. common.mq 子包：RabbitMQConfig 等（依赖 spring-amqp / spring-rabbit，release 未传）
        @ComponentScan.Filter(type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.mq\\..*"),
        // 2. common.config 下所有「中间件/可选」配置类 —— Class 内省会触及未传依赖的符号：
        //      Rabbit/Amqp: org.springframework.amqp.*
        //      Cache/Redis: org.springframework.data.redis.*
        //      Rocket/Kafka/Mq: 其他 MQ
        //      FastJson/HttpMessage/Web: com.alibaba.fastjson2.support.spring6.* (缺 extension)
        //      XxlJob: 重复 XxlJobConfig Bean 冲突
        @ComponentScan.Filter(type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.config\\..*(Rabbit|Amqp|[Cc]ache|[Rr]edis|[Rr]ocket|[Kk]afka|Mq|MQ|FastJson|Fastjson|HttpMessage|WebConfig|XxlJob).*"),
        // 3. 兜底：无论 XxlJobConfig 在什么包名，明确按类名继续排除一份（防未来 common 改包路径）
        @ComponentScan.Filter(type = FilterType.REGEX,
            pattern = ".*XxlJobConfig.*")
    }
)
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.release", "com.lianshengtong.common"})
@EnableAsync
@MapperScan("com.lianshengtong.release.mapper")
public class ReleaseServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReleaseServiceApplication.class, args);
    }
}
