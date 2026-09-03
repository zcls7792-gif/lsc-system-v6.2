package com.lianshengtong.gateway.gray;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 灰度发布相关 bean 声明。
 * <p>GrayPolicyStore 使用独立 bean，方便后续替换为 Redis/Nacos 持久化实现。</p>
 */
@Configuration
public class GrayReleaseConfig {

    @Bean
    public GrayPolicyStore grayPolicyStore() {
        return new GrayPolicyStore();
    }
}
