package com.lianshengtong.evidence;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
@EnableTransactionManagement
@ComponentScan(
    basePackages = {"com.lianshengtong.evidence", "com.lianshengtong.common"},
    excludeFilters = {
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.mq\\..*"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.config\\.RabbitTemplateConfig"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.config\\.CacheConfig"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.config\\.ShardingSphereConfig"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.config\\.MybatisPlusConfig"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.tracing\\..*"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.lock\\..*"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.utils\\.ShardedLockUtil"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.aop\\..*"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.security\\..*"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.common\\.config\\.WebMvcConfig"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.evidence\\.mapper\\..*"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.evidence\\.service\\.impl\\.EvidenceServiceImpl"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.evidence\\.service\\..*"
        ),
        @ComponentScan.Filter(
            type = FilterType.REGEX,
            pattern = "com\\.lianshengtong\\.evidence\\.schedule\\..*"
        )
    }
)
public class EvidenceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EvidenceServiceApplication.class, args);
    }
}