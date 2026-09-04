package com.lianshengtong.gateway.gray;

import com.lianshengtong.common.result.R;
import com.lianshengtong.gateway.gray.stats.GrayStatsAggregator;
import com.lianshengtong.gateway.gray.stats.LocalOnlyGrayStatsAggregator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase K：验证 /stats /summary 返回 本地视图 + 集群视图（clusterStats）双视图。
 * <p>
 * 注入一个自定义 GrayStatsAggregator：它记录 cluster 命中 = 本地 10 倍（用于清晰区分 instance/cluster），
 * 模拟多实例场景（4 live nodes）。
 */
public class GrayReleaseControllerPhaseKTest {

    private GrayPolicyStore store;
    private GrayReleaseController controller;
    private GrayPolicyService service;
    private GrayStatsAggregator x10Agg;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        store = new GrayPolicyStore();
        // 构造 service（纯内存 Repository，不涉及 DB）
        var repo = new com.lianshengtong.gateway.gray.spi.InMemoryGrayPolicyRepository();
        org.springframework.beans.factory.ObjectProvider<com.lianshengtong.gateway.gray.spi.GrayPolicyRepository> rp =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(rp.orderedStream()).thenReturn(java.util.stream.Stream.of(repo));
        org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate> jp =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(jp.getIfAvailable()).thenReturn(null);
        service = new GrayPolicyService(store, rp, jp, new com.fasterxml.jackson.databind.ObjectMapper());
        service.init();

        // 构造 x10 Aggregator：cluster 值 = 本地值 * 10
        x10Agg = new GrayStatsAggregator() {
            final GrayStatsAggregator delegate = new LocalOnlyGrayStatsAggregator(store);
            @Override public void record(String policyId, Version version, RuleForce ruleForce) { delegate.record(policyId, version, ruleForce); }
            @Override public AggregatedStats aggregated(String policyId) {
                AggregatedStats s = delegate.aggregated(policyId);
                // 复用 legacy 工厂：Phase K 单测不关心 err/p95，默认 -1
                return AggregatedStats.legacy(
                        s.baselineHits() * 10,
                        s.canaryHits() * 10,
                        s.ruleForceCanary() * 10,
                        s.ruleForceBaseline() * 10,
                        s.startEpochSec(),
                        4,
                        true);
            }
            @Override public java.util.Map<String, AggregatedStats> aggregatedAll() {
                java.util.Map<String, AggregatedStats> m = new LinkedHashMap<>();
                for (GrayPolicyStore.Policy p : store.list()) m.put(p.policyId(), aggregated(p.policyId()));
                return m;
            }
        };

        org.springframework.beans.factory.ObjectProvider<GrayStatsAggregator> aggOp =
                org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(aggOp.getIfAvailable(org.mockito.ArgumentMatchers.any(java.util.function.Supplier.class)))
                .thenReturn(x10Agg);
        controller = new GrayReleaseController(store, service, aggOp);
    }

    @Test
    void stats_returnsInstancePlusClusterViews() {
        GrayPolicyStore.Policy p = GrayPolicyStore.Policy.legacy("pk1", "r1", "lb://base", "lb://canary",
                50, List.of(), Map.of(), GrayPolicyStore.Status.ACTIVE,
                Instant.now(), Instant.now(), "tester");
        controller.upsert(p, "tester");
        // 本地写入 80 baseline / 20 canary
        GrayPolicyStore.Stats s = store.statsFor("pk1");
        s.baselineHits.set(80); s.canaryHits.set(20);
        s.ruleForceCanary.set(5); s.ruleForceBaseline.set(1);

        R<Map<String, Object>> r = controller.stats("pk1");
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) r.getData();
        // 本地视图
        assertEquals(100L, body.get("totalRequests"));
        assertEquals(80L, body.get("baselineHits"));
        assertEquals(20L, body.get("canaryHits"));
        // 集群视图（x10）
        @SuppressWarnings("unchecked")
        Map<String, Object> cluster = (Map<String, Object>) body.get("clusterStats");
        assertNotNull(cluster);
        assertEquals(Boolean.TRUE, cluster.get("available"));
        assertEquals(4, cluster.get("liveNodes"));
        assertEquals(1000L, cluster.get("totalRequests"));
        assertEquals(800L, cluster.get("baselineHits"));
        assertEquals(200L, cluster.get("canaryHits"));
        assertEquals(50L, cluster.get("ruleForceCanaryHits"));
        assertEquals(10L, cluster.get("ruleForceBaselineHits"));
    }

    @Test
    void summary_aggregatesBothViews() {
        // 两条策略
        controller.upsert(buildPolicy("pk-a", "ra", 40), "t");
        controller.upsert(buildPolicy("pk-b", "rb", 20), "t");
        bumpLocal("pk-a", 40, 10, 1, 0);  // 本地 50 req
        bumpLocal("pk-b", 200, 50, 3, 2); // 本地 250 req

        R<Map<String, Object>> r = controller.summary();
        assertTrue(r.isSuccess());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) r.getData();

        assertEquals(2, data.get("routeCount"));
        // 本地汇总 = 50 + 250
        assertEquals(300L, data.get("totalRequestsProcessed"));
        // 集群汇总 = 本地 × 10 → 3000
        assertEquals(3000L, data.get("clusterTotalRequests"));
        // ruleForce: local (1+0+3+2)=6 ×10=60
        assertEquals(60L, data.get("clusterRuleForceHits"));
        assertEquals(6L, data.get("totalRuleForceHits"));
        assertTrue((Boolean) data.get("clusterAvailable"));
        assertEquals(4, data.get("observedLiveNodes"));
        String implName = String.valueOf(data.get("statsImplementation"));
        assertFalse(implName.isBlank(), "statsImplementation must not be blank");
        // 由于匿名类名随 JDK / build 工具（compile vs test）可能变化，这里只验证有值且不等于默认 "LocalOnlyGrayStatsAggregator"
        // (我们注入的是 x10Agg，而 LocalOnlyGrayStatsAggregator 是兜底)
        assertNotEquals("LocalOnlyGrayStatsAggregator", implName);
    }

    private GrayPolicyStore.Policy buildPolicy(String pid, String rid, int weight) {
        return GrayPolicyStore.Policy.legacy(pid, rid, "lb://a", "lb://a-c", weight,
                List.of(), Map.of(), GrayPolicyStore.Status.ACTIVE,
                Instant.now(), Instant.now(), "t");
    }

    private void bumpLocal(String pid, long base, long can, long rfc, long rfb) {
        GrayPolicyStore.Stats s = store.statsFor(pid);
        s.baselineHits.set(base);
        s.canaryHits.set(can);
        s.ruleForceCanary.set(rfc);
        s.ruleForceBaseline.set(rfb);
    }
}
