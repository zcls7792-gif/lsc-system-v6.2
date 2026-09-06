package com.lianshengtong.lsc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.SystemMetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

@SpringBootApplication
@MapperScan("com.lianshengtong.lsc.mapper")
@EnableAutoConfiguration(exclude = {SystemMetricsAutoConfiguration.class, MetricsAutoConfiguration.class})
public class LscApplication {
    public static void main(String[] args) {
        SpringApplication.run(LscApplication.class, args);
        System.out.println("\n============================================");
        System.out.println("  链盛通 LSC 平台后端启动成功");
        System.out.println("  API 地址: http://localhost:8080/api/v1");
        System.out.println("  H2 控制台: http://localhost:8080/api/v1/h2-console");
        System.out.println("============================================\n");
    }
}
