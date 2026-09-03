package com.lianshengtong.gateway.gray.stats;

import com.lianshengtong.gateway.gray.GrayPolicyStore;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 仅本地兜底：无 Redis 时（例如单实例部署 / Redis 未配 / CI 测试）。
 * <p>
 * record() 为空实现（调用方已经先写了 GrayPolicyStore.Stats），读端直接读本地，
 * 返回的 clusterAvailable=false 让前端/运维面板在 UI 上标灰色（非集群视图）。
 */
public class LocalOnlyGrayStatsAggregator implements GrayStatsAggregator {

    private final GrayPolicyStore store;

    public LocalOnlyGrayStatsAggregator(GrayPolicyStore store) { this.store = store; }

    @Override public void record(String policyId, Version version, RuleForce ruleForce) { /* 本地已在 filter 写入 */ }

    @Override public AggregatedStats aggregated(String policyId) { return localOnly(policyId); }

    @Override public Map<String, AggregatedStats> aggregatedAll() {
        return store.list().stream()
                .map(GrayPolicyStore.Policy::policyId)
                .distinct()
                .collect(Collectors.toMap(Function.identity(), this::localOnly));
    }

    private AggregatedStats localOnly(String policyId) {
        GrayPolicyStore.Stats s = store.statsFor(policyId);
        return new AggregatedStats(
                s.baselineHits.get(),
                s.canaryHits.get(),
                s.ruleForceCanary.get(),
                s.ruleForceBaseline.get(),
                s.startTimeMs.get() / 1000L,
                1,
                false
        );
    }
}
