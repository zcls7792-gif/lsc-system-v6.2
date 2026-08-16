package com.lianshengtong.common.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @ConditionalOnMissingBean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(30, TimeUnit.MINUTES)
                .recordStats());
        return manager;
    }

    public static final String MERCHANT_INFO_CACHE = "merchantInfo";
    public static final String PRODUCT_INFO_CACHE = "productInfo";
    public static final String SYSTEM_CONFIG_CACHE = "systemConfig";
    public static final String RISK_RULE_CACHE = "riskRule";
    public static final String MEDIA_URL_CACHE = "mediaUrl";
}
