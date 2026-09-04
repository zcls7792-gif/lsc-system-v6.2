package com.lianshengtong.gateway.gray.rollout;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Phase N：GrayRollout 装配入口。
 * <ul>
 *   <li>加载 {@link GrayRolloutProperties}（gray.rollout.*）</li>
 *   <li>启用 Spring Scheduling（@Scheduled 由 Coordinator 内使用）</li>
 *   <li>条件化创建 {@link GrayRolloutCoordinator}：当 gray.rollout.enabled=true（默认）</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(GrayRolloutProperties.class)
public class GrayRolloutAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RolloutMetrics rolloutMetrics(io.micrometer.core.instrument.MeterRegistry registry,
                                         GrayRolloutProperties props) {
        return new RolloutMetrics(registry, props);
    }
}
