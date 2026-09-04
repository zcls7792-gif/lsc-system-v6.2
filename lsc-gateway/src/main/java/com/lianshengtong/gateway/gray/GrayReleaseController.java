package com.lianshengtong.gateway.gray;

import com.lianshengtong.common.result.R;
import com.lianshengtong.gateway.gray.rollout.GrayRolloutCoordinator;
import com.lianshengtong.gateway.gray.rollout.GrayRolloutProperties;
import com.lianshengtong.gateway.gray.rollout.RolloutRuntimeState;
import com.lianshengtong.gateway.gray.rollout.SloGuard;
import com.lianshengtong.gateway.gray.stats.GrayStatsAggregator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 灰度发布管理接口（Phase I 补齐版，含持久化 + pause/resume/graduate/delete/summary/全局历史）。
 * <p>
 * 所有"写入"操作均通过 GrayPolicyService 双写：先内存（保证过滤器热路径立刻生效），再持久化（DB 失败仅告警不回滚）。
 * 所有"读"操作：列表/详情走 GrayPolicyStore 内存，历史走 Repository 优先（JDBC 全量）、退化到内存环。
 *
 * @see GrayPolicyService
 */
@RestController
@RequestMapping("/api/gateway/gray")
public class GrayReleaseController {

    private final GrayPolicyStore store;
    private final GrayPolicyService service;
    private final GrayStatsAggregator statsAggregator;
    private final ObjectProvider<GrayRolloutCoordinator> coordinatorProvider;
    private final ObjectProvider<GrayRolloutProperties> rolloutPropsProvider;

    /** Spring 托管构造：statsAggregator / coordinator 通过 ObjectProvider 可选。 */
    public GrayReleaseController(GrayPolicyStore store,
                                 GrayPolicyService service,
                                 ObjectProvider<GrayStatsAggregator> aggregatorProvider,
                                 ObjectProvider<GrayRolloutCoordinator> coordinatorProvider,
                                 ObjectProvider<GrayRolloutProperties> rolloutPropsProvider) {
        this(store,
                service,
                aggregatorProvider.getIfAvailable(() -> new com.lianshengtong.gateway.gray.stats.LocalOnlyGrayStatsAggregator(store)),
                coordinatorProvider, rolloutPropsProvider);
    }

    /** 向后兼容：老代码只传 3 个参数（stats Aggregator Provider）。 */
    public GrayReleaseController(GrayPolicyStore store,
                                  GrayPolicyService service,
                                  ObjectProvider<GrayStatsAggregator> aggregatorProvider) {
        this(store, service, aggregatorProvider,
                GrayReleaseController.<GrayRolloutCoordinator>nullObjectProvider(),
                GrayReleaseController.<GrayRolloutProperties>nullObjectProvider());
    }
    private GrayReleaseController(GrayPolicyStore store,
                                  GrayPolicyService service,
                                  GrayStatsAggregator statsAggregator,
                                  ObjectProvider<GrayRolloutCoordinator> coordinatorProvider,
                                  ObjectProvider<GrayRolloutProperties> rolloutPropsProvider) {
        this.store = store;
        this.service = service;
        this.statsAggregator = statsAggregator == null
                ? new com.lianshengtong.gateway.gray.stats.LocalOnlyGrayStatsAggregator(store)
                : statsAggregator;
        this.coordinatorProvider = coordinatorProvider == null ? nullObjectProvider() : coordinatorProvider;
        this.rolloutPropsProvider = rolloutPropsProvider == null ? nullObjectProviderProps() : rolloutPropsProvider;
    }

    /** 兼容构造（单测 / 手工实例化场景）。 */
    public GrayReleaseController(GrayPolicyStore store, GrayPolicyService service) {
        this(store, service,
                new com.lianshengtong.gateway.gray.stats.LocalOnlyGrayStatsAggregator(store),
                nullObjectProviderCoord(), nullObjectProviderProps());
    }
    public GrayReleaseController(GrayPolicyStore store) { this(store, defaultInMemoryService(store)); }

    // ---------- helper: null ObjectProvider suppliers ----------
    private static ObjectProvider<GrayRolloutCoordinator> nullObjectProviderCoord() { return nullObjectProvider(); }
    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> nullObjectProvider() {
        return new ObjectProvider<T>() {
            public T getObject() { return null; }
            public T getObject(Object... args) { return null; }
            public T getIfAvailable() { return null; }
            public T getIfUnique() { return null; }
        };
    }
    private static ObjectProvider<GrayRolloutProperties> nullObjectProviderProps() { return nullObjectProvider(); }

    private static GrayPolicyService defaultInMemoryService(GrayPolicyStore store) {
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

        GrayPolicyService svc = new GrayPolicyService(store, rp, jp, new com.fasterxml.jackson.databind.ObjectMapper());
        svc.init();
        return svc;
    }

    // ============== 写入类 ==============

    @PostMapping("/policies")
    public R<GrayPolicyStore.Policy> upsert(@RequestBody GrayPolicyStore.Policy policy,
                                            @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        if (policy.policyId() == null || policy.policyId().isBlank()) {
            return R.fail(400, "policyId is required");
        }
        return R.ok(service.createOrUpdate(policy, operator));
    }

    @PutMapping("/policies/{policyId}/weight")
    public R<GrayPolicyStore.Policy> setWeight(@PathVariable String policyId,
                                                @RequestParam int weight,
                                                @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        GrayPolicyStore.Policy p = service.setWeight(policyId, weight, operator);
        return p == null ? R.fail(404, "policy not found") : R.ok(p);
    }

    @PostMapping("/policies/{policyId}/rollback")
    public R<GrayPolicyStore.Policy> rollback(@PathVariable String policyId,
                                               @RequestParam(required = false, defaultValue = "manual") String reason,
                                               @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        GrayPolicyStore.Policy p = service.rollback(policyId, operator, reason);
        return p == null ? R.fail(404, "policy not found") : R.ok(p);
    }

    @PostMapping("/policies/{policyId}/pause")
    public R<GrayPolicyStore.Policy> pause(@PathVariable String policyId,
                                           @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        GrayPolicyStore.Policy p = service.pause(policyId, operator);
        return p == null ? R.fail(404, "policy not found") : R.ok(p);
    }

    @PostMapping("/policies/{policyId}/resume")
    public R<GrayPolicyStore.Policy> resume(@PathVariable String policyId,
                                            @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        GrayPolicyStore.Policy p = service.resume(policyId, operator);
        return p == null ? R.fail(404, "policy not found") : R.ok(p);
    }

    @PostMapping("/policies/{policyId}/graduate")
    public R<GrayPolicyStore.Policy> graduate(@PathVariable String policyId,
                                              @RequestParam(required = false, defaultValue = "promoted to baseline") String reason,
                                              @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        GrayPolicyStore.Policy p = service.graduate(policyId, operator, reason);
        return p == null ? R.fail(404, "policy not found") : R.ok(p);
    }

    @DeleteMapping("/policies/{policyId}")
    public R<Boolean> delete(@PathVariable String policyId,
                             @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        GrayPolicyStore.Policy cur = store.get(policyId);
        if (cur == null) return R.fail(404, "policy not found");
        boolean removed = service.delete(policyId, operator);
        if (!removed) {
            return R.fail(409, "delete is only allowed for policies with status=GRADUATED or ROLLED_BACK (current=" + cur.status() + ")");
        }
        return R.ok(Boolean.TRUE);
    }

    // ============== 读取类 ==============

    @GetMapping("/policies")
    public R<List<GrayPolicyStore.Policy>> list() {
        return R.ok(store.list());
    }

    @GetMapping("/policies/{policyId}")
    public R<GrayPolicyStore.Policy> get(@PathVariable String policyId) {
        GrayPolicyStore.Policy p = store.get(policyId);
        return p == null ? R.fail(404, "policy not found") : R.ok(p);
    }

    @GetMapping("/policies/{policyId}/history")
    public R<List<GrayPolicyStore.History>> history(@PathVariable String policyId,
                                                     @RequestParam(defaultValue = "50") int limit) {
        return R.ok(service.history(policyId, Math.max(1, Math.min(limit, 500))));
    }

    @GetMapping("/history")
    public R<List<GrayPolicyStore.History>> globalHistory(
            @RequestParam(defaultValue = "100") int limit) {
        return R.ok(service.history(null, Math.max(1, Math.min(limit, 500))));
    }

    @GetMapping("/policies/{policyId}/stats")
    public R<Map<String, Object>> stats(@PathVariable String policyId) {
        GrayPolicyStore.Policy p = store.get(policyId);
        if (p == null) return R.fail(404, "policy not found");
        GrayPolicyStore.Stats s = store.statsFor(policyId);
        GrayStatsAggregator.AggregatedStats cluster = statsAggregator.aggregated(policyId);
        Map<String, Object> out = buildStatsMap(policyId, p, s);
        out.put("clusterStats", clusterStatsMap(cluster));
        return R.ok(out);
    }

    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("repository", service.repositoryImplementation());
        out.put("repositoryAvailable", service.repositoryAvailable());
        String implName = statsAggregator.getClass().getName();
        // 取简单名（匿名类会带 $N；优先去掉包前缀）
        int dot = implName.lastIndexOf('.');
        out.put("statsImplementation", dot >= 0 ? implName.substring(dot + 1) : implName);

        List<GrayPolicyStore.Policy> all = store.list();
        List<Map<String, Object>> perRoute = new ArrayList<>(all.size());
        Map<String, GrayStatsAggregator.AggregatedStats> clusterMap = statsAggregator.aggregatedAll();
        long totalBaseline = 0, totalCanary = 0, totalRuleForce = 0;
        long clusterBaseline = 0, clusterCanary = 0, clusterRuleForce = 0;
        int maxLiveNodes = 1;
        boolean anyCluster = false;
        for (GrayPolicyStore.Policy p : all) {
            GrayPolicyStore.Stats s = store.statsFor(p.policyId());
            Map<String, Object> r = buildStatsMap(p.policyId(), p, s);
            GrayStatsAggregator.AggregatedStats agg = clusterMap.get(p.policyId());
            if (agg != null) {
                r.put("clusterStats", clusterStatsMap(agg));
                clusterBaseline += agg.baselineHits();
                clusterCanary += agg.canaryHits();
                clusterRuleForce += agg.ruleForceCanary() + agg.ruleForceBaseline();
                maxLiveNodes = Math.max(maxLiveNodes, agg.liveNodes());
                if (agg.clusterAvailable()) anyCluster = true;
            }
            r.put("routeId", p.routeId());
            r.put("status", p.status().name());
            perRoute.add(r);
            totalBaseline += s.baselineHits.get();
            totalCanary += s.canaryHits.get();
            totalRuleForce += s.ruleForceCanary.get() + s.ruleForceBaseline.get();
        }
        out.put("routeCount", perRoute.size());
        out.put("totalRequestsProcessed", totalBaseline + totalCanary);
        out.put("totalRuleForceHits", totalRuleForce);
        out.put("clusterTotalRequests", clusterBaseline + clusterCanary);
        out.put("clusterRuleForceHits", clusterRuleForce);
        out.put("clusterAvailable", anyCluster);
        out.put("observedLiveNodes", maxLiveNodes);
        out.put("policies", perRoute);
        return R.ok(out);
    }

    // ======================= Phase N: Rollout 管理端点 =======================

    @GetMapping("/rollout/status")
    public R<Map<String, Object>> rolloutStatus() {
        GrayRolloutCoordinator c = coordinatorProvider.getIfAvailable();
        Map<String, Object> out;
        if (c == null) {
            out = new LinkedHashMap<>();
            out.put("coordinatorEnabled", false);
            out.put("reason", "GrayRolloutCoordinator bean missing (gray.rollout.enabled=false?)");
        } else {
            out = c.statusSnapshot(Instant.now());
        }
        return R.ok(out);
    }

    @GetMapping("/policies/{policyId}/rollout")
    public R<Map<String, Object>> rolloutDetail(@PathVariable String policyId) {
        GrayPolicyStore.Policy p = store.get(policyId);
        if (p == null) return R.fail(404, "policy not found");
        GrayRolloutProperties props = rolloutPropsProvider.getIfAvailable(GrayRolloutProperties::new);
        List<Integer> steps = SloGuard.mergedSteps(props, p.rolloutConfig());
        int stepIdx = SloGuard.currentStepIndex(steps, p.canaryWeightPercent());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyId", policyId);
        out.put("status", p.status().name());
        out.put("weight", p.canaryWeightPercent());
        out.put("steps", steps);
        out.put("stepIndex", stepIdx);
        out.put("stepWeight", steps.get(stepIdx));

        GrayRolloutCoordinator c = coordinatorProvider.getIfAvailable();
        if (c != null) {
            RolloutRuntimeState s = c.runtimeStateFor(policyId);
            RolloutRuntimeState.Snapshot snap = (s == null)
                    ? new RolloutRuntimeState.Snapshot(stepIdx, steps.get(stepIdx), 0L, 0, 0, null, 0L)
                    : s.snapshot(Instant.now());
            out.put("snapshot", snap);
        } else {
            out.put("snapshot", Map.of("note", "Coordinator bean unavailable; runtime snapshot empty."));
        }

        GrayStatsAggregator.AggregatedStats a = statsAggregator.aggregated(policyId);
        SloGuard.SloResult slo = SloGuard.evaluate(a, props, p.rolloutConfig());
        Map<String, Object> sm = new LinkedHashMap<>();
        sm.put("overallPass", slo.overallPass());
        sm.put("insufficientSamples", slo.insufficientSamples());
        sm.put("dataUnavailable", slo.dataUnavailable());
        sm.put("canaryErrPct", Double.isNaN(slo.canaryErrPct()) ? null : slo.canaryErrPct());
        sm.put("baselineErrPct", Double.isNaN(slo.baselineErrPct()) ? null : slo.baselineErrPct());
        sm.put("canaryP95Ms",  slo.canaryP95Ms()  < 0 ? null : slo.canaryP95Ms());
        sm.put("baselineP95Ms", slo.baselineP95Ms() < 0 ? null : slo.baselineP95Ms());
        sm.put("canarySamples", slo.canarySamples());
        sm.put("baselineSamples", slo.baselineSamples());
        sm.put("gates", slo.gates());
        out.put("slo", sm);

        Map<String, Object> ef = new LinkedHashMap<>();
        ef.put("minMinutesAtStep", props.getMinMinutesAtStep());
        ef.put("maxConsecutiveFailuresBeforeRollback", props.getMaxConsecutiveFailuresBeforeRollback());
        ef.put("maxErrorDriftPct", props.getMaxErrorDriftPct());
        ef.put("maxP95Ratio", props.getMaxP95Ratio());
        ef.put("minSamplesThreshold", props.getMinSamplesThreshold());
        ef.put("policyEnabled", p.rolloutConfig() == null || !Boolean.FALSE.equals(p.rolloutConfig().enabled()));
        out.put("effectiveConfig", ef);

        return R.ok(out);
    }

    @GetMapping("/policies/{policyId}/rollout/history")
    public R<List<GrayPolicyStore.History>> rolloutHistory(@PathVariable String policyId,
                                                           @RequestParam(defaultValue = "50") int limit) {
        if (store.get(policyId) == null) return R.fail(404, "policy not found");
        return R.ok(service.rolloutHistory(policyId, Math.max(1, Math.min(limit, 200))));
    }

    @PostMapping("/policies/{policyId}/rollout/advance-step")
    public R<GrayPolicyStore.Policy> rolloutAdvanceStep(@PathVariable String policyId,
                                                         @RequestParam(required = false) String reason,
                                                         @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        GrayRolloutCoordinator c = coordinatorProvider.getIfAvailable();
        GrayPolicyStore.Policy advanced;
        if (c != null) {
            advanced = c.manualAdvanceStep(policyId, operator, reason);
            if (advanced == null) return R.fail(404, "policy not found: " + policyId);
            return R.ok(advanced);
        }
        GrayPolicyStore.Policy p = store.get(policyId);
        if (p == null) return R.fail(404, "policy not found");
        GrayRolloutProperties props = rolloutPropsProvider.getIfAvailable(GrayRolloutProperties::new);
        List<Integer> steps = SloGuard.mergedSteps(props, p.rolloutConfig());
        int stepIdx = SloGuard.currentStepIndex(steps, p.canaryWeightPercent());
        if (stepIdx >= steps.size() - 1) {
            return R.ok(service.markReadyForGraduation(policyId, operator));
        }
        int next = steps.get(stepIdx + 1);
        return R.ok(service.advanceWeightTo(policyId, next, operator,
                reason == null ? "manual advance (no coordinator)" : reason));
    }

    // ============== internals ==============
    private static Map<String, Object> clusterStatsMap(GrayStatsAggregator.AggregatedStats a) {
        long total = a.totalRequests();
        double ratio = total == 0 ? 0d : (a.canaryHits() * 100d / total);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("available", a.clusterAvailable());
        out.put("liveNodes", a.liveNodes());
        out.put("startEpochSec", a.startEpochSec());
        out.put("totalRequests", total);
        out.put("baselineHits", a.baselineHits());
        out.put("canaryHits", a.canaryHits());
        out.put("canaryRatioPercent", String.format("%.2f", ratio));
        out.put("ruleForceCanaryHits", a.ruleForceCanary());
        out.put("ruleForceBaselineHits", a.ruleForceBaseline());
        // Phase N 新增：err5xx + P95 估算
        out.put("err5xxBaseline", a.err5xxBaseline() < 0 ? null : a.err5xxBaseline());
        out.put("err5xxCanary",   a.err5xxCanary()   < 0 ? null : a.err5xxCanary());
        out.put("p95BaselineMs",  a.latencyP95BaselineMs() < 0 ? null : a.latencyP95BaselineMs());
        out.put("p95CanaryMs",    a.latencyP95CanaryMs()   < 0 ? null : a.latencyP95CanaryMs());
        return out;
    }

    // ============== internals ==============
    private Map<String, Object> buildStatsMap(String policyId, GrayPolicyStore.Policy p, GrayPolicyStore.Stats s) {
        long base = s.baselineHits.get();
        long canary = s.canaryHits.get();
        long total = base + canary;
        double canaryRatio = total == 0 ? 0d : (canary * 100d / total);

        long pmBase = 0, pmCanary = 0;
        long[] baseArr = new long[60], canaryArr = new long[60];
        for (int i = 0; i < 60; i++) {
            baseArr[i] = s.perSecondBaseline[i].get();
            canaryArr[i] = s.perSecondCanary[i].get();
            pmBase += baseArr[i];
            pmCanary += canaryArr[i];
        }
        long upSec = Math.max(1L, (System.currentTimeMillis() - s.startTimeMs.get()) / 1000L);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyId", policyId);
        out.put("configuredWeightPercent", p.canaryWeightPercent());
        out.put("totalRequests", total);
        out.put("baselineHits", base);
        out.put("canaryHits", canary);
        out.put("actualCanaryRatioPercent", String.format("%.2f", canaryRatio));
        out.put("ruleForceCanaryHits", s.ruleForceCanary.get());
        out.put("ruleForceBaselineHits", s.ruleForceBaseline.get());
        out.put("avgQpsTotal", String.format("%.2f", (total * 1.0 / upSec)));
        out.put("lastMinuteBaselineHits", pmBase);
        out.put("lastMinuteCanaryHits", pmCanary);
        out.put("upSinceSec", upSec);
        out.put("perSecondBaseline", baseArr);
        out.put("perSecondCanary", canaryArr);
        return out;
    }
}
