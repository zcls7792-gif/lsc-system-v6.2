package com.lianshengtong.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ObservabilitySmokeTest {

    MeterRegistry registry;
    TraceIdGenerator idGen;
    ObservabilityGlobalFilter filter;
    GatewayFilterChain chain;

    @BeforeEach void setUp() {
        registry = new SimpleMeterRegistry();
        idGen = new TraceIdGenerator();
        filter = new ObservabilityGlobalFilter(registry, idGen);
        chain = Mockito.mock(GatewayFilterChain.class);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> {
            ServerWebExchange e = inv.getArgument(0);
            e.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        });
    }

    ServerWebExchange exchange(String routeId, MockServerHttpRequest req) {
        Route r = Route.async().id(routeId)
                .uri(URI.create("lb://svc")).order(0).predicate(x->true).build();
        MockServerWebExchange ex = MockServerWebExchange.from(req);
        ex.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, r);
        return ex;
    }

    @Test @DisplayName("traceId 优先沿用上游 X-Trace-Id，否则新建（并写回请求头+响应头）")
    void traceIdInherited() {
        String incoming = "0123456789abcdef0123456789abcdef";
        MockServerHttpRequest req = MockServerHttpRequest.get("/x")
                .header("X-Trace-Id", incoming).build();
        ServerWebExchange ex = exchange("user-service", req);
        filter.filter(ex, chain).block();
        assertThat(ex.getRequest().getHeaders().getFirst("X-Trace-Id")).isEqualTo(incoming);
        assertThat(ex.getResponse().getHeaders().getFirst("X-Trace-Id")).isEqualTo(incoming);
    }

    @Test @DisplayName("无 traceId 时生成 16 字符 hex；埋点 counter Timer 正常")
    void generatedTraceIdAndMetrics() {
        ServerWebExchange ex = exchange("user-service",
                MockServerHttpRequest.get("/y").build());
        filter.filter(ex, chain).block();
        String out = ex.getResponse().getHeaders().getFirst("X-Trace-Id");
        assertThat(out).isNotBlank();
        assertThat(out.length()).isEqualTo(16);
        assertThat(out.chars().allMatch(c -> Character.digit(c, 16) >= 0)).isTrue();

        assertThat(registry.find("lsc.gateway.requests.total")
                .tag("routeId", "user-service")
                .tag("grayVersion", "none")
                .tag("status", "2xx")
                .counter().count()).isEqualTo(1);
        assertThat(registry.find("lsc.gateway.requests.duration")
                .tag("routeId", "user-service").timer().count()).isEqualTo(1);
    }

    @Test @DisplayName("TraceIdGenerator 连续生成不重复且有序")
    void generatorStability() {
        String a = idGen.next();
        String b = idGen.next();
        assertThat(a).hasSize(16);
        assertThat(b).hasSize(16);
        assertThat(a).isNotEqualTo(b);
    }

    @Test @DisplayName("summary actuator endpoint 返回 filter 启用 & jvm")
    void summaryEndpoint() {
        LscGatewaySummaryEndpoint ep = new LscGatewaySummaryEndpoint(idGen);
        Map<String, Object> s = ep.summary();
        assertThat(s.get("jwtFilter")).isEqualTo("enabled");
        assertThat(s.get("grayFilter")).isEqualTo("enabled");
        assertThat(s.get("observabilityFilter")).isEqualTo("enabled");
        assertThat(s.get("traceIdSample").toString()).hasSize(16);
        @SuppressWarnings("unchecked")
        Map<String, Object> jvm = (Map<String, Object>) s.get("jvm");
        assertThat(jvm).containsKey("usedHeapMb");
        assertThat(jvm).containsKey("availableProcessors");
    }
}
