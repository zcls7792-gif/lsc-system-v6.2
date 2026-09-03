package com.lianshengtong.gateway.gray;

import com.lianshengtong.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
@RequiredArgsConstructor
public class GrayReleaseController {

    private final GrayPolicyStore store;
    private final GrayPolicyService service;

    /** 兼容构造函数（只传入 store 时，自动创建一个纯内存 GrayPolicyService，适合单元测试 / 无 Spring 上下文场景）。 */
    public GrayReleaseController(GrayPolicyStore store) {
        this(store, defaultInMemoryService(store));
    }

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
        return R.ok(buildStatsMap(policyId, p, s));
    }

    @GetMapping("/summary")
    public R<Map<String, Object>> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("repository", service.repositoryImplementation());
        out.put("repositoryAvailable", service.repositoryAvailable());

        List<GrayPolicyStore.Policy> all = store.list();
        List<Map<String, Object>> perRoute = new ArrayList<>(all.size());
        long totalBaseline = 0, totalCanary = 0, totalRuleForce = 0;
        for (GrayPolicyStore.Policy p : all) {
            GrayPolicyStore.Stats s = store.statsFor(p.policyId());
            Map<String, Object> r = buildStatsMap(p.policyId(), p, s);
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
        out.put("policies", perRoute);
        return R.ok(out);
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
