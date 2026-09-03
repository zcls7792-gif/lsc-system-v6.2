package com.lianshengtong.gateway.gray;

import com.lianshengtong.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 灰度发布管理接口（管理员使用；生产环境可挂 Gateway actuator endpoint 或在 admin 网关走 admin 鉴权 token）。
 * <p>典型使用流程：
 * <pre>
 *   1) POST /api/gateway/gray/policies  创建 order 服务 v2.1 灰度，权重 5%
 *   2) GET  /api/gateway/gray/policies/{policyId}/stats 监控命中比例
 *   3) PUT  /api/gateway/gray/policies/{policyId}/weight 渐进调权重到 20% / 50% / 100%
 *   4) POST /api/gateway/gray/policies/{policyId}/rollback 异常一键回滚（权重 0% 并标记 ROLLED_BACK + history）
 * </pre>
 */
@RestController
@RequestMapping("/api/gateway/gray")
@RequiredArgsConstructor
public class GrayReleaseController {

    private final GrayPolicyStore store;

    
    @PostMapping("/policies")
    public R<GrayPolicyStore.Policy> upsert(@RequestBody GrayPolicyStore.Policy policy,
                                            @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        return R.ok(store.createOrUpdate(policy, operator));
    }

    
    @GetMapping("/policies")
    public R<List<GrayPolicyStore.Policy>> list() {
        return R.ok(store.list());
    }

    
    @GetMapping("/policies/{policyId}")
    public R<GrayPolicyStore.Policy> get(@PathVariable String policyId) {
        GrayPolicyStore.Policy p = store.get(policyId);
        return p == null ? R.fail(404, "policy not found") : R.ok(p);
    }

    
    @PutMapping("/policies/{policyId}/weight")
    public R<GrayPolicyStore.Policy> setWeight(@PathVariable String policyId,
                                                @RequestParam int weight,
                                                @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        GrayPolicyStore.Policy p = store.setWeight(policyId, weight, operator);
        return p == null ? R.fail(404, "policy not found") : R.ok(p);
    }

    
    @PostMapping("/policies/{policyId}/rollback")
    public R<GrayPolicyStore.Policy> rollback(@PathVariable String policyId,
                                               @RequestParam(required = false, defaultValue = "manual") String reason,
                                               @RequestHeader(value = "X-Admin-User", defaultValue = "ops") String operator) {
        GrayPolicyStore.Policy p = store.rollback(policyId, operator, reason);
        return p == null ? R.fail(404, "policy not found") : R.ok(p);
    }

    
    @GetMapping("/policies/{policyId}/history")
    public R<List<GrayPolicyStore.History>> history(@PathVariable String policyId,
                                                     @RequestParam(defaultValue = "50") int limit) {
        return R.ok(store.history(policyId, Math.max(1, Math.min(limit, 500))));
    }

    
    @GetMapping("/policies/{policyId}/stats")
    public R<Map<String, Object>> stats(@PathVariable String policyId) {
        GrayPolicyStore.Policy p = store.get(policyId);
        if (p == null) return R.fail(404, "policy not found");
        GrayPolicyStore.Stats s = store.statsFor(policyId);
        long base = s.baselineHits.get();
        long canary = s.canaryHits.get();
        long total = base + canary;
        double canaryRatio = total == 0 ? 0d : (canary * 100d / total);

        // 最近 60s 每桶求和 ≈ 每分钟 QPS 近似
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
        return R.ok(out);
    }
}
