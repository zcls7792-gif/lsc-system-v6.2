package com.lianshengtong.common.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.util.Properties;


/**
 * ShardingSphere分库分表配置
 * 8库32表，按user_id取模32路由
 *
 * 库序号 = (user_id % 32) / 4  -> 0..7
 * 表序号 = (user_id % 32) % 4  -> 0..3
 *
 * 实际配置在Nacos共享配置 lsc-common-datasource.yaml 中
 * 注意：ShardingSphere的实际装配由其Spring Boot Starter自动配置完成，
 *      各业务服务按需在自身pom.xml中引入shardingsphere-jdbc-core-spring-boot-starter。
 */
@Configuration
@ConditionalOnProperty(prefix = "shardingsphere", name = "enabled", havingValue = "true", matchIfMissing = false)
public class ShardingSphereConfig {

    @Autowired
    private Environment env;

    /**
     * ShardingSphere数据源占位
     * 实际由ShardingSphere Spring Boot Starter自动配置接管，此处仅作为配置入口声明。
     * 当ShardingSphere Starter在classpath时，其AutoConfiguration会覆盖/接管此Bean。
     */
    @Bean
    public DataSource shardingDataSource() {
        Properties props = new Properties();
        props.setProperty("spring.shardingsphere.datasource.names",
                env.getProperty("spring.shardingsphere.datasource.names", "ds0"));
        // 由ShardingSphereAutoConfiguration接管，此处返回null仅作为配置入口
        return null;
    }


    public ShardingSphereConfig() {}

    public ShardingSphereConfig(Environment env) {
        this.env = env;
    }

    public Environment getEnv() { return env; }
    public void setEnv(Environment env) { this.env = env; }
}
