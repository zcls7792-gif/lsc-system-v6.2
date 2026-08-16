package com.lianshengtong.ledger;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LSC 账本服务启动类
 * <p>
 * 职责：管理锁定/可用 LSC 余额，提供发行、释放、核销、支付、B2B 流转、退款退回、过期转回等
 * 原子化账务操作。基于 Seata AT 模式保障跨服务一致性，Redisson 保障同用户并发安全，
 * MyBatis-Plus 乐观锁(version) 防止余额超扣。
 * </p>
 *
 * @author lsc
 */
@SpringBootApplication(scanBasePackages = {"com.lianshengtong.ledger", "com.lianshengtong.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.lianshengtong.ledger", "com.lianshengtong.common"})
@MapperScan("com.lianshengtong.ledger.mapper")
@EnableTransactionManagement
public class LscLedgerApplication {

    private static final Logger log = LoggerFactory.getLogger(LscLedgerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(LscLedgerApplication.class, args);
        log.info("""

                ====================================================
                  LSC Ledger Service Started Successfully
                  Port: 8102   Database: lsc_ledger
                  Seata: lsc_tx_group   Nacos: lsc-dev
                ====================================================""");
    }
}
