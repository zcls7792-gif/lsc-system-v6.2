package com.lianshengtong.evidence.config;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caffeine 缓存 Micrometer Metrics 配置
 * <p>
 * 将 EvidenceCaffeineCache 的底层 Caffeine Cache 注册到 Micrometer MeterRegistry，
 * 自动导出到 Prometheus。
 * </p>
 * <p>
 * 通过 /actuator/prometheus 暴露的指标:
 * <ul>
 *   <li>caffeine_cache_hit_count{name="evidence"} - 缓存命中总次数</li>
 *   <li>caffeine_cache_miss_count{name="evidence"} - 缓存未命中总次数</li>
 *   <li>caffeine_cache_eviction_count{name="evidence"} - 缓存淘汰总次数</li>
 *   <li>caffeine_cache_size{name="evidence"} - 当前缓存条目数</li>
 *   <li>caffeine_cache_hit_rate{name="evidence"} - 缓存命中率</li>
 *   <li>caffeine_cache_miss_rate{name="evidence"} - 缓存未命中率</li>
 *   <li>caffeine_cache_put_count{name="evidence"} - 缓存写入次数</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(CaffeineCacheMetrics.class)
public class EvidenceCacheMetricsConfig {

    private static final Logger log = LoggerFactory.getLogger(EvidenceCacheMetricsConfig.class);

    @Value("${spring.application.name:lsc-evidence-service}")
    private String applicationName;

    @Bean
    @ConditionalOnBean(EvidenceCaffeineCache.class)
    public Cache<String, EvidenceCaffeineCache.TtlValue> evidenceMonitoredCache(
            EvidenceCaffeineCache caffeineCache,
            MeterRegistry meterRegistry) {

        Cache<String, EvidenceCaffeineCache.TtlValue> cache = caffeineCache.getUnderlyingCache();

        CaffeineCacheMetrics.monitor(
                meterRegistry,
                cache,
                "evidence",
                "application", applicationName,
                "cache.type", "caffeine"
        );

        log.info("[Metrics] Caffeine cache metrics registered: name=evidence, application={}", applicationName);

        return cache;
    }
}
