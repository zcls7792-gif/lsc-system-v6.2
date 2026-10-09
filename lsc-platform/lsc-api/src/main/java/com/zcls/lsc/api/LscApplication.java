package com.zcls.lsc.api;

import com.zcls.lsc.common.json.LscJacksonModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * 链盛通 LSC 消费回馈权益系统 V7.7.2 — 主应用入口。
 *
 * 模块装配：common / db / account / order / coupon / api
 */
@SpringBootApplication(scanBasePackages = "com.zcls.lsc")
public class LscApplication {

    public static void main(String[] args) {
        SpringApplication.run(LscApplication.class, args);
    }

    /** 注册 Units/Money 字符串序列化模块，避免 JS 大整数精度损失。 */
    @Bean
    public com.fasterxml.jackson.databind.Module lscJacksonModule() {
        return com.zcls.lsc.common.json.LscJacksonModule.create();
    }
}
