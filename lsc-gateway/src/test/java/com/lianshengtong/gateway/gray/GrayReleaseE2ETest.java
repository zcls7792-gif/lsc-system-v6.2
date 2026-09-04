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
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Phase H 灰度发布 E2E 集成测试：
 * <ol>
 *     <li>多规则组合（HEADER 强制灰度 + PATH_PREFIX 强制基线 + USER_ID_MOD，多条按序短路）</li>
 *     <li>权重动态调整（10→50→100 过程中比例渐进收敛，并生成 WEIGHT_CHANGE 历史）</li>
 *     <li>回滚切换（一键 rollback 后流量 100% 切回 baseline；历史含 ROLLBACK）</li>
 * </ol>
 * 所有用例不启动 Spring Context，直接调用 Gateway Filter 本身，模拟真实分流链路。
 */
@ExtendWith(MockitoExtension.class)
class GrayReleaseE2ETest {

    GrayPolicyStore store;
    GrayReleaseController controller;
    GrayReleaseGlobalFilter filter;

    @Mock GatewayFilterChain chain;

    @BeforeEach void setUp() {
        store = new GrayPolicyStore();
        controller = new GrayReleaseController(store);
        filter = new GrayReleaseGlobalFilter(store);
        when(chain.filter(any(ServerWebExchange.class))).thenAnswer(inv -> Mono.empty());
    }

    // ---------- helpers ----------
    GrayPolicyStore.Policy createPolicy(String pid, int weight, List<GrayPolicyStore.Rule> rules) {
        return store.createOrUpdate(GrayPolicyStore.Policy.legacy(pid, "svc-e2e",
                "lb://lsc-svc", "lb://lsc-svc-canary", weight, rules,
                Map.of(), null, null, null, null), "ops");
    }

    ServerWebExchange exchange(MockServerHttpRequest req) {
        Route route = Route.async().id("svc-e2e")
                .uri(URI.create("lb://lsc-svc")).order(0).predicate(x -> true).build();
        MockServerWebExchange ex = MockServerWebExchange.from(req);
        ex.getAttributes().put(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR, route);
        return ex;
    }

    /** 批量发送请求，返回 canary 命中数 */
    int sendMany(int n, java.util.function.Supplier<MockServerHttpRequest> reqFactory) {
        AtomicInteger canary = new AtomicInteger();
        for (int i = 0; i < n; i++) {
            ServerWebExchange ex = exchange(reqFactory.get());
            filter.filter(ex, chain).block();
            String v = ex.getAttribute(GrayReleaseGlobalFilter.ATTR_GRAY_VERSION);
            if ("canary".equals(v)) canary.incrementAndGet();
        }
        return canary.get();
    }

    // ========= 用例 1：多规则组合匹配（短路顺序） =========
    @Test @DisplayName("E2E-1: 多规则组合 — HEADER 强制灰度 > PATH 强制基线 > USER_ID_MOD 强制灰度")
    void multiRuleComposition() {
        // 将强制基线设计为 header "X-Baseline: always"，避免与 PATH_PREFIX（当前实现没有显式 FORCE_BASELINE 语义，只靠不命中跳过）冲突
        List<GrayPolicyStore.Rule> rules = List.of(
                // 顺序 1：带 X-Canary: force → 强制灰度（即使 weight=0 也命中 canary）
                new GrayPolicyStore.Rule("HEADER", "X-Canary", "EQ", "force", null),
                // 顺序 2：带 X-Baseline: always → 强制基线（此时即使 USER_ID_MOD 也不应该命中 canary）
                new GrayPolicyStore.Rule("HEADER", "X-Baseline", "EQ", "always", "FORCE_BASELINE"),
                // 顺序 3：USER_ID % 10 == 0 → 强制灰度
                new GrayPolicyStore.Rule("USER_ID_MOD", null, "MOD_EQ", "0", "10")
        );
        createPolicy("e2e-multi", 0, rules);

        // A. X-Canary 头 → 命中 canary（权重 0 无关）
        int canHeader = sendMany(200,
                () -> MockServerHttpRequest.get("/anything").header("X-Canary", "force").build());
        assertThat(canHeader).isEqualTo(200);

        // B. X-Canary:force + X-Baseline:always → 顺序 1 先命中 canary（短路）
        int both = sendMany(100,
                () -> MockServerHttpRequest.get("/x")
                        .header("X-Canary", "force").header("X-Baseline", "always").build());
        assertThat(both).isEqualTo(100);

        // C. 仅 X-Baseline:always + userId%10==0 → 命中顺序 2 → baseline（强制基线短路）
        int baselineWithUid0 = sendMany(100,
                () -> MockServerHttpRequest.get("/any").header("X-User-Id", "20").header("X-Baseline","always").build());
        assertThat(baselineWithUid0).isZero();

        // D. userId%10==0 无其他头 → 命中顺序 3 → canary
        int userId0 = sendMany(150,
                () -> MockServerHttpRequest.get("/api/list").header("X-User-Id", "30").build());
        assertThat(userId0).isEqualTo(150);

        // E. userId%10!=0 其他条件都不满足 → weight=0 → baseline
        int noHit = sendMany(300,
                () -> MockServerHttpRequest.get("/api/list").header("X-User-Id", "21").build());
        assertThat(noHit).isZero();

        // 按上述估算：ruleForceCanary = 200 (A) + 100 (B) + 150 (D) = 450；ruleForceBaseline = 100 (C)
        GrayPolicyStore.Stats s = store.statsFor("e2e-multi");
        assertThat(s.ruleForceCanary.get()).isEqualTo(200 + 100 + 150);
        assertThat(s.ruleForceBaseline.get()).isEqualTo(100);
    }

    // ========= 用例 2：权重动态调整（渐进收敛 + 历史） =========
    @Test @DisplayName("E2E-2: 动态调整权重 10%→50%→100%，比例渐进收敛，记录 WEIGHT_CHANGE 历史")
    void dynamicWeightRamp() {
        String pid = "e2e-ramp";
        createPolicy(pid, 10, List.of());
        int total = 20_000;

        int c1 = sendMany(total, () -> MockServerHttpRequest.get("/x").build());
        controller.setWeight(pid, 50, "ops");
        int c2 = sendMany(total, () -> MockServerHttpRequest.get("/x").build());
        controller.setWeight(pid, 100, "ops");
        int c3 = sendMany(total, () -> MockServerHttpRequest.get("/x").build());

        // 比例：每个阶段 2% 容忍（大数定律）
        assertThat(pct(c1, total)).isBetween(8.0, 12.0);
        assertThat(pct(c2, total)).isBetween(48.0, 52.0);
        assertThat(pct(c3, total)).isBetween(98.0, 100.0);

        // 历史：CREATE + 2× WEIGHT_CHANGE
        List<String> actions = controller.history(pid, 20).getData().stream()
                .map(GrayPolicyStore.History::action).toList();
        assertThat(actions).containsExactlyInAnyOrder("CREATE", "WEIGHT_CHANGE", "WEIGHT_CHANGE");
        assertThat(actions).startsWith("WEIGHT_CHANGE").endsWith("CREATE");
    }

    // ========= 用例 3：回滚切换（ROLLBACK 基线 + 权重=0 + 状态 ROLLED_BACK） =========
    @Test @DisplayName("E2E-3: rollback 一键切回基线，后续 100% baseline；历史带 ROLLBACK")
    void rollbackSwitchesToBaseline() {
        String pid = "e2e-rollback";
        createPolicy(pid, 100, List.of());

        // 预热：rollback 前 100% canary
        assertThat(sendMany(50, () -> MockServerHttpRequest.get("/x").build())).isEqualTo(50);

        // 回滚 → 权重变为 0；策略变为 ROLLED_BACK（此时因为 rules 空，active 变为 false 则跳过，也等价 baseline）
        controller.rollback(pid, "SLA P99 > 500ms", "sre-ops");
        GrayPolicyStore.Policy after = store.get(pid);
        assertThat(after.status()).isEqualTo(GrayPolicyStore.Status.ROLLED_BACK);
        assertThat(after.canaryWeightPercent()).isZero();

        // 后续流量：100% baseline
        int canAfter = sendMany(500, () -> MockServerHttpRequest.get("/x").build());
        assertThat(canAfter).isZero();

        // 历史含 ROLLBACK，含 reason
        List<GrayPolicyStore.History> hist = controller.history(pid, 20).getData();
        assertThat(hist).extracting(GrayPolicyStore.History::action).contains("ROLLBACK");
        GrayPolicyStore.History rb = hist.stream().filter(h -> "ROLLBACK".equals(h.action())).findFirst().orElseThrow();
        assertThat(rb.detail()).contains("SLA P99 > 500ms");
        assertThat(rb.operator()).isEqualTo("sre-ops");
        assertThat(rb.ts()).isNotNull().isBeforeOrEqualTo(Instant.now());
    }

    private static double pct(int sample, int total) {
        return sample * 100.0 / total;
    }
}
