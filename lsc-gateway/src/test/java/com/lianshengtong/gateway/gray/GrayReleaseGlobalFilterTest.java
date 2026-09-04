package com.lianshengtong.gateway.gray;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.RequestPath;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrayReleaseGlobalFilterTest {

    GrayPolicyStore store;
    GrayReleaseGlobalFilter filter;
    @Mock GatewayFilterChain chain;

    @BeforeEach void setUp() {
        store = new GrayPolicyStore();
        filter = new GrayReleaseGlobalFilter(store);
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    GrayPolicyStore.Policy createPolicy(String pid, int weight, List<GrayPolicyStore.Rule> rules) {
        return store.createOrUpdate(GrayPolicyStore.Policy.legacy(pid, "order-service",
                "lb://lsc-order-service", "lb://lsc-order-service-canary",
                weight, rules, Map.of(), null, null, null, null), "ops");
    }

    ServerWebExchange exchangeFor(String routeId, MockServerHttpRequest request) {
        Route route = Route.async()
                .id(routeId).uri(URI.create("lb://lsc-order-service"))
                .order(0)
                .predicate(ex -> true)
                .build();
        MockServerWebExchange ex = MockServerWebExchange.from(request);
        ex.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return ex;
    }

    @Test @DisplayName("无策略：不改 route")
    void noPolicy() {
        MockServerHttpRequest req = MockServerHttpRequest.get("/api/order/list").build();
        ServerWebExchange ex = exchangeFor("order-service", req);
        Route before = (Route) ex.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        filter.filter(ex, chain).block();
        Route after = (Route) ex.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        assertThat(after).isSameAs(before);
    }

    @Test @DisplayName("weight=0 但无强制规则：100% baseline")
    void weightZero() {
        // weight=0 时 active=true 需 rules 非空；传一条"永远 FORCE_BASELINE"的占位规则即可。
        List<GrayPolicyStore.Rule> rules = List.of(
                new GrayPolicyStore.Rule("PATH_PREFIX", null, null, "/api/order/admin", null)
        );
        createPolicy("p", 0, rules);
        for (int i = 0; i < 100; i++) {
            ServerWebExchange ex = exchangeFor("order-service",
                    MockServerHttpRequest.get("/api/order/list").build());
            filter.filter(ex, chain).block();
            assertThat((Object) (String) ex.getAttribute(GrayReleaseGlobalFilter.ATTR_GRAY_VERSION))
                    .isEqualTo("baseline");
        }
        GrayPolicyStore.Stats s = store.statsFor("p");
        assertThat(s.canaryHits.get()).isZero();
        assertThat(s.baselineHits.get()).isEqualTo(100);
    }

    @Test @DisplayName("weight=100：永远 canary，URI 替换为 policy.canaryUri")
    void weight100() {
        createPolicy("p", 100, List.of());
        for (int i = 0; i < 20; i++) {
            ServerWebExchange ex = exchangeFor("order-service",
                    MockServerHttpRequest.get("/api/order/list").build());
            filter.filter(ex, chain).block();
            Route r = ex.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            assertThat(r.getUri().toString()).isEqualTo("lb://lsc-order-service-canary");
            assertThat(ex.getRequest().getHeaders().getFirst("X-Gray-Version")).isEqualTo("canary");
        }
    }

    @Test @DisplayName("weight=50：比例接近 50%")
    void weightFifty() {
        createPolicy("p", 50, List.of());
        int total = 20_000;
        for (int i = 0; i < total; i++) {
            ServerWebExchange ex = exchangeFor("order-service",
                    MockServerHttpRequest.get("/api/order/list").build());
            filter.filter(ex, chain).block();
        }
        GrayPolicyStore.Stats s = store.statsFor("p");
        long can = s.canaryHits.get();
        long base = s.baselineHits.get();
        assertThat(can + base).isEqualTo(total);
        double ratio = can * 100d / total;
        assertThat(Double.valueOf(ratio)).isBetween(Double.valueOf(48.0), Double.valueOf(52.0));
    }

    @Test @DisplayName("RULE HEADER force canary")
    void ruleHeader() {
        List<GrayPolicyStore.Rule> rules = List.of(
                new GrayPolicyStore.Rule("HEADER", "X-Canary", "EQ", "force", null)
        );
        createPolicy("p", 0, rules); // weight=0 但强制命中
        ServerWebExchange ex = exchangeFor("order-service",
                MockServerHttpRequest.get("/any").header("X-Canary", "force").build());
        filter.filter(ex, chain).block();
        assertThat((Object) ex.getAttribute(GrayReleaseGlobalFilter.ATTR_GRAY_VERSION)).isEqualTo("canary");
        assertThat(store.statsFor("p").ruleForceCanary.get()).isEqualTo(1);
    }

    @Test @DisplayName("RULE USER_ID_MOD userId%10==0 → 灰度")
    void ruleUserIdMod() {
        List<GrayPolicyStore.Rule> rules = List.of(
                new GrayPolicyStore.Rule("USER_ID_MOD", null, "MOD_EQ", "0", "10")
        );
        createPolicy("p", 0, rules);
        ServerWebExchange ex = exchangeFor("order-service",
                MockServerHttpRequest.get("/a").header("X-User-Id", "20").build());
        filter.filter(ex, chain).block();
        assertThat((Object) ex.getAttribute(GrayReleaseGlobalFilter.ATTR_GRAY_VERSION)).isEqualTo("canary");
    }

    @Test @DisplayName("RULE PATH_PREFIX /api/order/admin 命中 → 灰度")
    void rulePathPrefix() {
        List<GrayPolicyStore.Rule> rules = List.of(
                new GrayPolicyStore.Rule("PATH_PREFIX", null, null, "/api/order/admin", null)
        );
        createPolicy("p", 0, rules);
        ServerWebExchange ex = exchangeFor("order-service",
                MockServerHttpRequest.get("/api/order/admin/10").build());
        filter.filter(ex, chain).block();
        assertThat((Object) ex.getAttribute(GrayReleaseGlobalFilter.ATTR_GRAY_VERSION)).isEqualTo("canary");

        // 不命中路径 → 权重=0 → baseline
        ServerWebExchange ex2 = exchangeFor("order-service",
                MockServerHttpRequest.get("/api/order/list").build());
        filter.filter(ex2, chain).block();
        assertThat((Object) ex2.getAttribute(GrayReleaseGlobalFilter.ATTR_GRAY_VERSION)).isEqualTo("baseline");
    }
}
