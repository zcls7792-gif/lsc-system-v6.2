package com.lianshengtong.gateway.gray;

import com.lianshengtong.common.result.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase I: Controller 补齐接口验证（pure Java 调用，不走 Spring Web）：
 * - upsert policyId 必填校验
 * - pause / resume / graduate / delete 各自语义
 * - summary 聚合返回结构（repository / policies / counts）
 * - globalHistory 跨策略历史
 * - DELETE: ACTIVE 状态拒绝（409）、ROLLED_BACK/GRADUATED 允许
 */
class GrayReleaseControllerPhase1Test {

    GrayPolicyStore store;
    GrayPolicyService service;
    GrayReleaseController controller;

    @BeforeEach void setUp() {
        store = new GrayPolicyStore();
        // 直接用最小的 Service：注入 InMemory Repository 即可（等价于 Phase I 下无 JDBC 配置场景）
        com.lianshengtong.gateway.gray.spi.InMemoryGrayPolicyRepository repo =
                new com.lianshengtong.gateway.gray.spi.InMemoryGrayPolicyRepository();
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<com.lianshengtong.gateway.gray.spi.GrayPolicyRepository> rp =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(rp.orderedStream()).thenReturn(java.util.stream.Stream.of(repo));

        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jp =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(jp.getIfAvailable()).thenReturn(null);

        service = new GrayPolicyService(store, rp, jp, new com.fasterxml.jackson.databind.ObjectMapper());
        service.init();
        controller = new GrayReleaseController(store, service);
    }

    private GrayPolicyStore.Policy sample(String id, int w) {
        return new GrayPolicyStore.Policy(id, "svc",
                "lb://svc", "lb://svc-canary", w,
                List.of(), Map.of(), null, Instant.now(), Instant.now(), "ops");
    }

    @Test @DisplayName("policyId 为空 → 400")
    void rejectEmptyPolicyId() {
        R<GrayPolicyStore.Policy> r = controller.upsert(sample(null, 1), "ops");
        assertThat(r.getCode()).isEqualTo(400);
    }

    @Test @DisplayName("pause → PAUSED；resume → ACTIVE")
    void pauseResumeCycle() {
        controller.upsert(sample("cy", 10), "ops");
        assertThat(controller.pause("cy", "ops").getData().status()).isEqualTo(GrayPolicyStore.Status.PAUSED);
        assertThat(controller.resume("cy", "ops").getData().status()).isEqualTo(GrayPolicyStore.Status.ACTIVE);
        assertThat(store.get("cy").canaryWeightPercent()).isEqualTo(10); // 权重保持
    }

    @Test @DisplayName("graduate → GRADUATED + 权重 0")
    void graduateMarksGraduated() {
        controller.upsert(sample("g", 100), "ops");
        GrayPolicyStore.Policy grad = controller.graduate("g", "released", "ops").getData();
        assertThat(grad.status()).isEqualTo(GrayPolicyStore.Status.GRADUATED);
        assertThat(grad.canaryWeightPercent()).isZero();
        assertThat(controller.history("g", 10).getData()).extracting(GrayPolicyStore.History::action)
                .contains("GRADUATE");
    }

    @Test @DisplayName("DELETE: ACTIVE → 409 拒绝; ROLLED_BACK/GRADUATED → 允许")
    void deletePolicyGate() {
        // ACTIVE → 409
        controller.upsert(sample("act", 10), "ops");
        R<Boolean> bad = controller.delete("act", "ops");
        assertThat(bad.getCode()).isEqualTo(409);

        // ROLLED_BACK → 允许
        controller.rollback("act", "ops", "oops");
        R<Boolean> ok = controller.delete("act", "ops");
        assertThat(ok.getCode()).isZero();
        assertThat(store.get("act")).isNull();

        // GRADUATED → 允许
        controller.upsert(sample("gd", 100), "ops");
        controller.graduate("gd", "ops", "done");
        assertThat(controller.delete("gd", "ops").getCode()).isZero();
    }

    @Test @DisplayName("summary 聚合：repository/每个策略的 status + totalRequestsProcessed")
    void summaryAggregates() {
        controller.upsert(sample("a", 10), "ops");
        controller.upsert(sample("b", 100), "ops");
        controller.pause("b", "ops");
        // 模拟请求计数（直接写 Stats）
        for (int i = 0; i < 10; i++) store.statsFor("a").baselineHits.incrementAndGet();
        for (int i = 0; i < 20; i++) store.statsFor("a").canaryHits.incrementAndGet();
        for (int i = 0; i < 7; i++) store.statsFor("b").baselineHits.incrementAndGet();

        R<Map<String, Object>> sum = controller.summary();
        assertThat(sum.getCode()).isZero();
        Map<String, Object> d = sum.getData();
        assertThat(d.get("repositoryAvailable")).isEqualTo(false);
        assertThat(d.get("routeCount")).isEqualTo(2);
        assertThat(d.get("totalRequestsProcessed")).isEqualTo(37L); // 10+20+7
        assertThat(d.get("totalRuleForceHits")).isEqualTo(0L);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> policies = (List<Map<String, Object>>) d.get("policies");
        Map<String, Object> b = policies.stream().filter(x -> "b".equals(x.get("policyId"))).findFirst().orElseThrow();
        assertThat(b.get("status")).isEqualTo("PAUSED");
        assertThat(b.get("configuredWeightPercent")).isEqualTo(100);
    }

    @Test @DisplayName("globalHistory: 跨策略历史按时间倒序")
    void globalHistory() {
        controller.upsert(sample("x", 1), "ops1");
        controller.upsert(sample("y", 2), "ops2");
        controller.setWeight("x", 50, "ops3");
        controller.rollback("y", "ops4", "test");

        List<GrayPolicyStore.History> list = controller.globalHistory(50).getData();
        assertThat(list).extracting(GrayPolicyStore.History::action)
                .startsWith("ROLLBACK", "WEIGHT_CHANGE");
        // 4 条历史（y CREATE / x CREATE / x WEIGHT_CHANGE / y ROLLBACK）
        assertThat(list).hasSize(4);
        assertThat(list).extracting(GrayPolicyStore.History::action)
                .containsExactlyInAnyOrder("CREATE", "CREATE", "WEIGHT_CHANGE", "ROLLBACK");
        assertThat(list).extracting(GrayPolicyStore.History::policyId)
                .filteredOn(pid -> "x".equals(pid)).hasSize(2); // x CREATE + x WC
        assertThat(list).extracting(GrayPolicyStore.History::policyId)
                .filteredOn(pid -> "y".equals(pid)).hasSize(2); // y CREATE + y RB
    }
}
