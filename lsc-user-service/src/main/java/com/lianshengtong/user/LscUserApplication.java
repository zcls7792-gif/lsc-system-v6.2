package com.lianshengtong.user;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务微服务启动类
 * <p>
 * 负责用户注册、登录、实名认证、商家资质审核、推荐关系绑定。
 * 通过 {@code @EnableFeignClients} 开启跨服务调用能力，可调用 lsc-ledger-service
 * 等下游服务完成账户初始化、商家信用联动等操作。
 * </p>
 *
 * @author lsc
 */
@Slf4j
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.user", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.user", "com.lianshengtong.common"})
@MapperScan("com.lianshengtong.user.mapper")
public class LscUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(LscUserApplication.class, args);
        log.info("""

                ====================================================
                  LSC User Service Started Successfully
                  Port: 8101   Database: lsc_user
                  Nacos: lsc-dev / LSC_GROUP
                ====================================================""");
    }
}
