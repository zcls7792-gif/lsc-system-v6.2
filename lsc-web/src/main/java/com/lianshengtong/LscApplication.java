package com.lianshengtong;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 链盛通 LSC 消费回馈权益系统 V7.7.2
 * 模块化单体应用入口
 */
@SpringBootApplication
@MapperScan("com.lianshengtong.**.mapper")
@EnableScheduling
public class LscApplication {
    public static void main(String[] args) {
        SpringApplication.run(LscApplication.class, args);
    }
}
