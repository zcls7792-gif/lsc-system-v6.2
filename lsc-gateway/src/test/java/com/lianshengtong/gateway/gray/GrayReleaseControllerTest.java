
package com.lianshengtong.gateway.gray;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.lianshengtong.common.result.R;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GrayReleaseControllerTest {

    GrayPolicyStore store;
    GrayReleaseController controller;

    @BeforeEach void setUp() {
        store = new GrayPolicyStore();
        controller = new GrayReleaseController(store);
    }

    private GrayPolicyStore.Policy sample(String id, int weight) {
        return GrayPolicyStore.Policy.legacy(id, "order-service",
                "lb://lsc-order-service", "lb://lsc-order-service-canary",
                weight, List.of(), Map.of(), GrayPolicyStore.Status.ACTIVE,
                Instant.now(), Instant.now(), "ops");
    }

    @Test @DisplayName("upsert 再 get → 正确 round trip")
    void upsertAndGet() {
        R<GrayPolicyStore.Policy> r = controller.upsert(sample("order-v2.1", 10), "ops");
        assertThat(r.getCode()).isZero();
        assertThat(r.getData().policyId()).isEqualTo("order-v2.1");
        assertThat(r.getData().canaryWeightPercent()).isEqualTo(10);
        assertThat(store.get("order-v2.1").createdAt()).isNotNull();

        R<GrayPolicyStore.Policy> fetched = controller.get("order-v2.1");
        assertThat(fetched.getData().baselineUri()).isEqualTo("lb://lsc-order-service");
        assertThat(fetched.getData().canaryUri()).isEqualTo("lb://lsc-order-service-canary");
    }

    @Test @DisplayName("setWeight + rollback 改变权重、状态、历史")
    void weightAndRollback() {
        store.createOrUpdate(sample("p", 10), "ops");
        R<GrayPolicyStore.Policy> w = controller.setWeight("p", 45, "ops2");
        assertThat(w.getData().canaryWeightPercent()).isEqualTo(45);

        R<GrayPolicyStore.Policy> rb = controller.rollback("p", "SLA down", "ops");
        assertThat(rb.getData().status()).isEqualTo(GrayPolicyStore.Status.ROLLED_BACK);
        assertThat(rb.getData().canaryWeightPercent()).isZero();

        R<List<GrayPolicyStore.History>> hist = controller.history("p", 50);
        assertThat(hist.getData()).extracting(GrayPolicyStore.History::action)
                .contains("ROLLBACK", "WEIGHT_CHANGE", "CREATE");
    }

    @Test @DisplayName("stats 接口正确返回命中统计与桶")
    void statsEndpoint() {
        store.createOrUpdate(sample("s", 5), "ops");
        GrayPolicyStore.Stats s = store.statsFor("s");
        for (int i = 0; i < 300; i++) s.baselineHits.incrementAndGet();
        for (int i = 0; i < 100; i++) s.canaryHits.incrementAndGet();
        s.ruleForceCanary.addAndGet(3);

        var stats = controller.stats("s");
        Map<String, Object> d = stats.getData();
        assertThat(d.get("totalRequests")).isEqualTo(400L);
        assertThat(d.get("canaryHits")).isEqualTo(100L);
        assertThat(d.get("ruleForceCanaryHits")).isEqualTo(3L);
        assertThat(d.get("configuredWeightPercent")).isEqualTo(5);
    }

    @Test @DisplayName("查不存在 → 404")
    void notFound() {
        assertThat(controller.get("missing").getCode()).isEqualTo(404);
    }
}
