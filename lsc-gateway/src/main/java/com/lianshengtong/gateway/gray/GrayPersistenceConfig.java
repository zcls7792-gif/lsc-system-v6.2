package com.lianshengtong.gateway.gray;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lianshengtong.gateway.gray.spi.GrayPolicyRepository;
import com.lianshengtong.gateway.gray.spi.InMemoryGrayPolicyRepository;
import com.lianshengtong.gateway.gray.spi.JdbcGrayPolicyRepository;
import com.lianshengtong.gateway.gray.stats.GrayStatsAggregator;
import com.lianshengtong.gateway.gray.stats.LocalOnlyGrayStatsAggregator;
import com.lianshengtong.gateway.gray.stats.RedisGrayStatsAggregator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Phase I：灰度持久化 Bean 装配。
 * <p>
 * 装配策略（与 GrayPolicyService.init() 中 pickRepository 的 Bean 发现保持一致）：
 * <ol>
 *   <li>GrayPolicyStore：单例内存态（GrayReleaseGlobalFilter 热路径直接用）。</li>
 *   <li>存在外部 DataSource → Spring Boot 会自动创建 JdbcTemplate，这里额外注册 JDBC 仓储 Bean。</li>
 *   <li>若用户显式注册了自定义 GrayPolicyRepository Bean（Redis/Nacos），会在 GrayPolicyService 中优先被 pick。</li>
 *   <li>始终兜底提供 InMemoryGrayPolicyRepository 作为默认 fallback bean。</li>
 * </ol>
 */
@Configuration
public class GrayPersistenceConfig {

    @Bean
    public GrayPolicyStore grayPolicyStore() {
        return new GrayPolicyStore();
    }

    @Bean
    @ConditionalOnClass(JdbcTemplate.class)
    @ConditionalOnBean(JdbcTemplate.class)
    public GrayPolicyRepository jdbcGrayPolicyRepository(JdbcTemplate jdbcTemplate, ObjectProvider<ObjectMapper> mapperProvider) {
        ObjectMapper mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
        return new JdbcGrayPolicyRepository(jdbcTemplate, mapper);
    }

    @Bean
    @ConditionalOnMissingBean(GrayPolicyRepository.class)
    public GrayPolicyRepository inMemoryGrayPolicyRepository() {
        return new InMemoryGrayPolicyRepository();
    }

    // ============= Phase K: Stats Aggregator =============
    @Bean
    @ConditionalOnClass(ReactiveStringRedisTemplate.class)
    @ConditionalOnBean(ReactiveStringRedisTemplate.class)
    public GrayStatsAggregator redisGrayStatsAggregator(
            ReactiveStringRedisTemplate redis,
            GrayPolicyStore store,
            @Value("${lsc.gray.stats.prefix:lsc:gray:stats}") String prefix) {
        return new RedisGrayStatsAggregator(redis, store, prefix);
    }

    @Bean
    @ConditionalOnMissingBean(GrayStatsAggregator.class)
    public GrayStatsAggregator localOnlyGrayStatsAggregator(GrayPolicyStore store) {
        return new LocalOnlyGrayStatsAggregator(store);
    }
}
