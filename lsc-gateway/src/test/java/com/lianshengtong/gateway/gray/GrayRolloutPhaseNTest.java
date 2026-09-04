package com.lianshengtong.gateway.gray;

import com.lianshengtong.gateway.gray.rollout.*;
import com.lianshengtong.gateway.gray.stats.GrayStatsAggregator;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase N 核心自治理单测：SloGuard + GrayRolloutCoordinator tick 流程
 *  - 纯内存实现，不依赖 Redis/Nacos
 *  - 通过 Mock AggregatedStats 控制基线/灰度数据，验证步进/回滚/保持/SLO 门限行为
 */
class GrayRolloutPhaseNTest {

    private GrayPolicyStore store;
    private GrayPolicyService service;
    private FakeStatsAggregator stats;
    private GrayRolloutProperties props;
    private RolloutMetrics metrics;
    private GrayRolloutCoordinator coordinator;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() throws Exception {
        store = new GrayPolicyStore();
        com.lianshengtong.gateway.gray.spi.InMemoryGrayPolicyRepository repo =
                new com.lianshengtong.gateway.gray.spi.InMemoryGrayPolicyRepository();
        service = new GrayPolicyService(store, repo);
        stats = new FakeStatsAggregator();
        props = new GrayRolloutProperties();
        props.setSteps(List.of(1, 10, 100));
        props.setTickMs(50);
        props.setMinMinutesAtStep(0); // 立刻允许 advance，便于单测
        props.setMaxConsecutiveFailuresBeforeRollback(1);
        props.setMaxErrorDriftPct(0.5);
        props.setMaxP95Ratio(1.3);
        props.setMinSamplesThreshold(10L);
        props.setNodeId("gw-ut");
        metrics = new RolloutMetrics(new SimpleMeterRegistry(), props);
        coordinator = new GrayRolloutCoordinator(store, service, stats, props, metrics);
        service.init();
        // 重置 JVM CAS：避免 prior test 的 leader 遗留阻止本次 tick（JVM_LEASE_MS=55s 远大于单测运行时长）
        java.lang.reflect.Field f = GrayRolloutCoordinator.class.getDeclaredField("JVM_LEADER_CAS");
        f.setAccessible(true);
        ((java.util.concurrent.atomic.AtomicBoolean) f.get(null)).set(false);
    }

    @Test
    @DisplayName("SLO Guard: sample size below threshold → insufficientSamples，不推进不回滚")
    void sloInsufficientSamples() {
        var agg = new FakeStats(0, 0, 0, 0, 0, -1, -1);
        SloGuard.SloResult r = SloGuard.evaluate(agg.asAgg(), props, null);
        assertTrue(r.insufficientSamples(), "应返回 insufficientSamples=true");
        assertFalse(r.overallPass(), "样本不足时 overallPass=false（避免误推进）");
    }

    @Test
    @DisplayName("SLO Guard: canary 错误率漂移 1% > 门限 0.5% → FAIL")
    void sloErrorDriftFail() {
        var agg = new FakeStats(1000, 1000, 5, 15, 200, 200, -1); // err 0.5% vs 1.5%
        SloGuard.SloResult r = SloGuard.evaluate(agg.asAgg(), props, null);
        assertFalse(r.overallPass());
        assertTrue(r.failSummary().contains("error_drift"),
                "失败原因应包含 error_drift：actual=" + r.failSummary());
    }

    @Test
    @DisplayName("SLO Guard: P95 canary=520ms, baseline=400ms → ratio 1.3 等于门限 → PASS（等于视为通过）")
    void sloP95BoundaryPass() {
        var agg = new FakeStats(1000, 1000, 2, 2, 400, 520);
        SloGuard.SloResult r = SloGuard.evaluate(agg.asAgg(), props, null);
        assertTrue(r.overallPass(), "P95 比例 1.3 等于门限时应 PASS：" + r.failSummary());
    }

    @Test
    @DisplayName("SLO Guard: P95 canary=521ms ratio>1.3 → FAIL")
    void sloP95Fail() {
        var agg = new FakeStats(1000, 1000, 2, 2, 400, 521);
        SloGuard.SloResult r = SloGuard.evaluate(agg.asAgg(), props, null);
        assertFalse(r.overallPass());
        assertTrue(r.failSummary().contains("p95_ratio"), "失败原因应包含 p95_ratio：actual=" + r.failSummary());
    }

    @Test
    @DisplayName("Coordinator tick：SLO PASS + weight=1% → advance 到 10%；再 PASS → advance 到 100%，标记 READY_FOR_GRADUATION")
    void rolloutPassTwoStepsAndGraduate() {
        GrayPolicyStore.Policy p = newPolicy("order_ut", 1, GrayPolicyStore.Status.ACTIVE);
        stats.set(p.policyId(), new FakeStats(1000, 500, 2, 1, 200, 200));

        coordinator.tick(); // tick-1: SLO PASS，weight 1→10
        assertEquals(10, store.get(p.policyId()).canaryWeightPercent(),
                "PASS 后应从 1% 推进到 10%：steps=" + SloGuard.mergedSteps(props, null));

        coordinator.tick(); // tick-2: SLO PASS，weight 10→100
        assertEquals(100, store.get(p.policyId()).canaryWeightPercent(),
                "连续 PASS 应从 10% 推进到 100%（最后一步）");

        coordinator.tick(); // tick-3: weight=100，SLO PASS → 标 READY_FOR_GRADUATION
        assertEquals(GrayPolicyStore.Status.READY_FOR_GRADUATION,
                store.get(p.policyId()).status(), "达到 100% weight 后应转 READY_FOR_GRADUATION");

        // 历史记录应有至少 2 次 STEP_ADVANCE + 1 次 READY_FOR_GRADUATION 动作
        List<GrayPolicyStore.History> hist = service.rolloutHistory(p.policyId(), 100);
        long advances = hist.stream()
                .filter(h ->
                        "STEP_ADVANCE".equals(h.action())
                                || "READY_FOR_GRADUATION".equals(h.action())
                                || (h.detail() != null && h.detail().startsWith("STEP_ADVANCE from")))
                .count();
        assertTrue(advances >= 3, "rolloutHistory 至少包含 2 次步进 + 1 次 READY_FOR_GRADUATION 标记，actual=" + advances
                + "\n  history=" + hist.stream().map(h -> h.action() + ":" + h.detail()).limit(20).toList());
    }

    @Test
    @DisplayName("Coordinator tick：SLO FAIL 一次 → 硬回滚（weight=0，status=ROLLED_BACK）并写审计")
    void rolloutFailTriggersHardRollback() {
        GrayPolicyStore.Policy p = newPolicy("order_ut", 10, GrayPolicyStore.Status.ACTIVE);
        // 错误率漂移超过门限：baseline 0.5% vs canary 2.0% → drift 1.5% > 0.5%
        stats.set(p.policyId(), new FakeStats(2000, 1000, 10, 20, 200, 250));

        coordinator.tick();
        assertEquals(GrayPolicyStore.Status.ROLLED_BACK, store.get(p.policyId()).status(),
                "FAIL 达到 maxConsecutiveFailures 后应硬回滚");
        assertEquals(0, store.get(p.policyId()).canaryWeightPercent(),
                "回滚后权重必须为 0");

        List<GrayPolicyStore.History> hist = service.history(p.policyId(), 50);
        assertTrue(hist.stream().anyMatch(h -> "ROLLBACK".equals(h.action())),
                "历史记录中应包含 ROLLBACK 动作");
    }

    @Test
    @DisplayName("Coordinator tick：dataUnavailable（agg 不可达）→ 不推进不回滚，保持当前状态")
    void rolloutDataUnavailableNoAction() {
        GrayPolicyStore.Policy p = newPolicy("order_ut", 10, GrayPolicyStore.Status.ACTIVE);
        stats.makeUnavailable(p.policyId());
        for (int i = 0; i < 5; i++) coordinator.tick();
        assertEquals(10, store.get(p.policyId()).canaryWeightPercent(),
                "dataUnavailable 时 5 次 tick 都不能改变权重");
        assertEquals(GrayPolicyStore.Status.ACTIVE, store.get(p.policyId()).status(),
                "dataUnavailable 时不应回滚");
    }

    @Test
    @DisplayName("manualAdvanceStep：最后一步之前直接推进（无视 hold 时间 / SLO）")
    void manualAdvanceSkipsHold() {
        props.setMinMinutesAtStep(999); // 保持 999 分钟
        GrayPolicyStore.Policy p = newPolicy("order_ut", 1, GrayPolicyStore.Status.ACTIVE);
        stats.set(p.policyId(), new FakeStats(0, 0, 0, 0, -1, -1)); // 样本不足，auto tick 不推进
        coordinator.tick();
        assertEquals(1, store.get(p.policyId()).canaryWeightPercent(),
                "样本不足 + 保持时间超长，auto tick 不应推进");

        GrayPolicyStore.Policy advanced = coordinator.manualAdvanceStep(p.policyId(), "qa-oncall", "加快回归");
        assertEquals(10, advanced.canaryWeightPercent(), "manual advance 直接到下一步 10%");
    }

    @Test
    @DisplayName("SloGuard：policy.rolloutConfig.steps 优先于全局 steps")
    void perPolicyStepsOverride() {
        var override = new GrayPolicyStore.RolloutConfig(
                List.of(5, 50, 100), null, null, null, null, null, null);
        List<Integer> merged = SloGuard.mergedSteps(props, override);
        assertEquals(List.of(5, 50, 100), merged, "policy rollout steps 应完全覆盖全局");
        assertEquals(1, SloGuard.currentStepIndex(merged, 30),
                "weight=30 时 steps[5,50,100] index 应为 1（落在 50 之前）");
    }

    // ================== helpers ==================
    private GrayPolicyStore.Policy newPolicy(String id, int weight, GrayPolicyStore.Status status) {
        GrayPolicyStore.Policy p = GrayPolicyStore.Policy.builder()
                .policyId(id).routeId("svc-" + id).status(status)
                .baselineUri("lb://svc").canaryUri("lb://svc-canary")
                .canaryWeightPercent(weight).build();
        return service.createOrUpdate(p, "ut");
    }

    /** 简单 AggregatedStats 构造：baselineHits / canaryHits / err5xx / p95 */
    static class FakeStats {
        final long baseHits, canHits, baseErr5xx, canErr5xx, p95BaseMs, p95CanMs;
        final long startEpochSec = Instant.now().minus(Duration.ofMinutes(5)).getEpochSecond();
        FakeStats(long baseHits, long canHits, long baseErr, long canErr,
                  long p95BaseMs, long p95CanMs) {
            this(baseHits, canHits, baseErr, canErr, p95BaseMs, p95CanMs, -1);
        }
        FakeStats(long baseHits, long canHits, long baseErr, long canErr,
                  long p95BaseMs, long p95CanMs, long unused) {
            this.baseHits = baseHits; this.canHits = canHits;
            this.baseErr5xx = baseErr; this.canErr5xx = canErr;
            this.p95BaseMs = p95BaseMs; this.p95CanMs = p95CanMs;
        }
        GrayStatsAggregator.AggregatedStats asAgg() {
            return new GrayStatsAggregator.AggregatedStats(
                    baseHits, canHits, 0L, 0L, startEpochSec, 1, true,
                    baseErr5xx, canErr5xx, p95BaseMs, p95CanMs);
        }
    }

    static class FakeStatsAggregator implements GrayStatsAggregator {
        final Map<String, FakeStats> data = new java.util.HashMap<>();
        final java.util.Set<String> unavailable = new java.util.HashSet<>();

        void set(String id, FakeStats s) { data.put(id, s); unavailable.remove(id); }
        void makeUnavailable(String id) { unavailable.add(id); }

        @Override public void record(String policyId, Version version, RuleForce ruleForce) { /* read-only in tests */ }

        @Override public AggregatedStats aggregated(String policyId) {
            if (unavailable.contains(policyId)) {
                return AggregatedStats.legacy(0L, 0L, 0L, 0L, 0L, 0, false);
            }
            FakeStats s = data.get(policyId);
            return s == null ? AggregatedStats.legacy(0L, 0L, 0L, 0L,
                    Instant.now().getEpochSecond(), 1, true) : s.asAgg();
        }
        @Override public Map<String, AggregatedStats> aggregatedAll() {
            Map<String, AggregatedStats> out = new java.util.HashMap<>();
            data.forEach((k, v) -> out.put(k, v.asAgg()));
            return out;
        }
    }
}
