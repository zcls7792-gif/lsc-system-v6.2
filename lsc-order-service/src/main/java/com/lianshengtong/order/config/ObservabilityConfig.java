package com.lianshengtong.order.config;

import com.lianshengtong.common.observability.GrayObservabilityFilter;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import com.lianshengtong.common.observability.GrayBaggage;
import com.lianshengtong.common.observability.TraceIdHolder;
import org.slf4j.MDC;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.DispatcherType;

/**
 * Phase L2：订单服务可观测性自动配置。
 * <p>
 * 三件事：
 * <ol>
 *   <li>Servlet 入口统一提取 traceId + grayBaggage 放 MDC</li>
 *   <li>Feign 下游请求头自动携带 traceId + 灰度 + W3C baggage（baggage: gray.policy_id=xxx,gray.version=yyy）</li>
 *   <li>管理探针（/actuator/gray/baggage 由 GrayBaggageWebConfig 暴露，暂不需要）。</li>
 * </ol>
 */
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

    /** Feign 下游：X-Trace-Id / X-Gray-* 都带上，并同时写 W3C baggage header。 */
    @Bean
    public RequestInterceptor grayBaggageFeignInterceptor() {
        return new RequestInterceptor() {
            @Override public void apply(RequestTemplate template) {
                String tid = TraceIdHolder.get();
                if (tid != null) {
                    template.header(TraceIdHolder.HEADER, tid);
                    // 兼容 Zipkin 的 B3 单头
                    template.header("b3", tid + "0000000000000001-0000000000000001-1");
                }
                String policy  = GrayBaggage.policyId();
                String version = GrayBaggage.version();
                if (policy  != null) template.header(GrayBaggage.HEADER_POLICY, policy);
                if (version != null) template.header(GrayBaggage.HEADER_VERSION, version);
                // W3C baggage: key1=value1,key2=value2
                if (policy != null || version != null) {
                    StringBuilder sb = new StringBuilder();
                    if (policy  != null) sb.append(GrayBaggage.W3C_BAGGAGE_POLICY).append('=').append(policy);
                    if (version != null) {
                        if (sb.length() > 0) sb.append(',');
                        sb.append(GrayBaggage.W3C_BAGGAGE_VERSION).append('=').append(version);
                    }
                    template.header("baggage", sb.toString());
                }
            }
        };
    }
}
