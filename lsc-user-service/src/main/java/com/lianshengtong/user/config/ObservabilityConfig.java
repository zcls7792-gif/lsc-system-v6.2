package com.lianshengtong.user.config;

import com.lianshengtong.common.observability.GrayBaggage;
import com.lianshengtong.common.observability.GrayObservabilityFilter;
import com.lianshengtong.common.observability.TraceIdHolder;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.DispatcherType;

/** Phase L2：用户服务可观测性自动配置（对应 order-service ObservabilityConfig 同款）。 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public FilterRegistrationBean<GrayObservabilityFilter> grayObservabilityFilter() {
        FilterRegistrationBean<GrayObservabilityFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new GrayObservabilityFilter());
        bean.setOrder(-100);
        bean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
        bean.addUrlPatterns("/*");
        return bean;
    }

    @Bean
    public RequestInterceptor grayBaggageFeignInterceptor() {
        return (RequestTemplate template) -> {
            String tid = TraceIdHolder.get();
            if (tid != null) {
                template.header(TraceIdHolder.HEADER, tid);
                template.header("b3", tid + "0000000000000001-0000000000000001-1");
            }
            String policy  = GrayBaggage.policyId();
            String version = GrayBaggage.version();
            if (policy  != null) template.header(GrayBaggage.HEADER_POLICY, policy);
            if (version != null) template.header(GrayBaggage.HEADER_VERSION, version);
            if (policy != null || version != null) {
                StringBuilder sb = new StringBuilder();
                if (policy  != null) sb.append(GrayBaggage.W3C_BAGGAGE_POLICY).append('=').append(policy);
                if (version != null) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(GrayBaggage.W3C_BAGGAGE_VERSION).append('=').append(version);
                }
                template.header("baggage", sb.toString());
            }
        };
    }
}
