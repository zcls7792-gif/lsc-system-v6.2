package com.lianshengtong.gateway.gray;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GrayPolicyStoreTest {

    GrayPolicyStore store;

    @BeforeEach void setUp() { store = new GrayPolicyStore(); }

    GrayPolicyStore.Policy sample(String id, int w) {
        return new GrayPolicyStore.Policy(id, "order-service",
                "lb://lsc-order-service", "lb://lsc-order-service-canary",
                w, List.of(), Map.of(), null, null, null, null);
    }

    @Test @DisplayName("create policy 填充默认元数据 createdAt/updatedAt")
    void createDefaults() {
        GrayPolicyStore.Policy p = store.createOrUpdate(sample("order-v2", 5), "ops");
        assertThat(p.policyId()).isEqualTo("order-v2");
        assertThat(p.canaryWeightPercent()).isEqualTo(5);
        assertThat(p.status()).isEqualTo(GrayPolicyStore.Status.ACTIVE);
        assertThat(p.createdAt()).isNotNull();
        assertThat(p.updatedAt()).isNotNull();
        assertThat(p.updatedBy()).isEqualTo("ops");
        assertThat(p.active()).isTrue();
    }

    @Test @DisplayName("权重 clamp 0..100")
    void weightClamp() {
        GrayPolicyStore.Policy a = store.createOrUpdate(sample("a", -5), "ops");
        GrayPolicyStore.Policy b = store.createOrUpdate(sample("b", 999), "ops");
        assertThat(a.canaryWeightPercent()).isZero();
        assertThat(b.canaryWeightPercent()).isEqualTo(100);
        assertThat(a.active()).isFalse();
    }

    @Test @DisplayName("rollback → 权重 0 + 状态 ROLLED_BACK + 历史记录")
    void rollback() {
        store.createOrUpdate(sample("p", 30), "u1");
        GrayPolicyStore.Policy p = store.rollback("p", "u2", "error rate >5%");
        assertThat(p.status()).isEqualTo(GrayPolicyStore.Status.ROLLED_BACK);
        assertThat(p.canaryWeightPercent()).isZero();
        List<GrayPolicyStore.History> hist = store.history("p", 10);
        assertThat(hist).extracting(GrayPolicyStore.History::action)
                .containsExactly("ROLLBACK", "CREATE");
        assertThat(hist.get(0).operator()).isEqualTo("u2");
    }

    @Test @DisplayName("setWeight 变更高权 + history WEIGHT_CHANGE")
    void setWeight() {
        store.createOrUpdate(sample("p", 10), "ops");
        GrayPolicyStore.Policy next = store.setWeight("p", 70, "ops2");
        assertThat(next.canaryWeightPercent()).isEqualTo(70);
        assertThat(next.status()).isEqualTo(GrayPolicyStore.Status.ACTIVE);
        List<GrayPolicyStore.History> hist = store.history("p", 10);
        assertThat(hist).extracting(GrayPolicyStore.History::action)
                .startsWith("WEIGHT_CHANGE");
    }

    @Test @DisplayName("findActiveForRoute 只返回 ACTIVE 且 weight>0")
    void findActiveForRoute() {
        store.createOrUpdate(sample("order-canary", 0), "ops"); // PAUSED
        assertThat(store.findActiveForRoute("order-service")).isNull();

        store.setWeight("order-canary", 10, "ops");
        assertThat(store.findActiveForRoute("order-service").policyId()).isEqualTo("order-canary");

        store.rollback("order-canary", "ops", "rollback");
        assertThat(store.findActiveForRoute("order-service")).isNull();
    }

    @Test @DisplayName("statsFor 统计命中 + 60s 桶")
    void stats() {
        store.createOrUpdate(sample("p", 50), "ops");
        GrayPolicyStore.Stats s = store.statsFor("p");
        for (int i = 0; i < 40; i++) s.canaryHits.incrementAndGet();
        for (int i = 0; i < 60; i++) s.baselineHits.incrementAndGet();
        s.perSecondCanary[0].addAndGet(7);
        assertThat(s.canaryHits.get()).isEqualTo(40);
        assertThat(s.baselineHits.get()).isEqualTo(60);
        assertThat(s.perSecondCanary[0].get()).isEqualTo(7);
    }

    @Test @DisplayName("history 全量 + 条数限制")
    void historyLimit() {
        for (int i = 0; i < 5; i++) {
            store.createOrUpdate(sample("p"+i, 10), "ops");
        }
        List<GrayPolicyStore.History> all = store.history(null, 100);
        assertThat(all).hasSize(5);
        List<GrayPolicyStore.History> top = store.history(null, 2);
        assertThat(top).hasSize(2);
    }
}
