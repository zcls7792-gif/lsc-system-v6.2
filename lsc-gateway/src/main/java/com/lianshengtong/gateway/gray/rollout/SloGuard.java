package com.lianshengtong.gateway.gray.rollout;

import com.lianshengtong.gateway.gray.GrayPolicyStore;
import com.lianshengtong.gateway.gray.stats.GrayStatsAggregator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Phase N：SLO 硬门限判定（纯函数，可单测，不依赖 Spring）。
 * <p>
 * 判定输出：每一个"门"（errorDrift / p95 / minSamples / dataAvailability）会产出
 * 一个 GateResult(name, pass/fail, actual, threshold, note)，便于 UI 表格化展示。
 * 汇总 SloResult 暴露 overallPass / insufficientSamples（前者决定是否 rollback，后者用于"不步进不回滚"）。
 */
public final class SloGuard {

    public record GateResult(String name, boolean pass, double actual, double threshold, String note) {}

    public record SloResult(
            List<GateResult> gates,
            boolean overallPass,
            boolean insufficientSamples,      // 样本不足：不步进也不回滚
            boolean dataUnavailable,          // err/p95 全缺失（statsAggregator 读 Redis 失败）
            double canaryErrPct,
            double baselineErrPct,
            double canaryP95Ms,
            double baselineP95Ms,
            long canarySamples,
            long baselineSamples
    ) {
        public static SloResult unavailable(String reason) {
            GateResult g = new GateResult("gate_slo_unavailable", false, 0d, 0d, reason);
            return new SloResult(List.of(g), false, true, true,
                    Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0L, 0L);
        }

        /** 某门为 FAIL 的摘要（逗号分隔），供 rollback reason。 */
        public String failSummary() {
            List<String> fails = new ArrayList<>();
            for (GateResult g : gates == null ? Collections.<GateResult>emptyList() : gates) {
                if (!g.pass) fails.add(g.name + "(actual=" + format(g.actual) + ",threshold=" + format(g.threshold) + ")");
            }
            return String.join("; ", fails);
        }
        private static String format(double v) {
            if (Double.isNaN(v)) return "n/a";
            return String.format(java.util.Locale.ROOT, "%.3f", v);
        }
    }

    /**
     * 计算策略级 SLO。
     * @param stats    聚合 stats（集群视图优先；空/本地兜底 → dataUnavailable=true）
     * @param global   全局配置（steps/门限等）
     * @param override 策略级 rollout 覆盖，可 null（全继承 global）
     */
    public static SloResult evaluate(GrayStatsAggregator.AggregatedStats stats,
                                     GrayRolloutProperties global,
                                     GrayPolicyStore.RolloutConfig override) {
        if (stats == null) return SloResult.unavailable("aggregated stats is null");

        double driftPct   = firstNonNull(override == null ? null : override.maxErrorDriftPct(),          global.getMaxErrorDriftPct());
        double p95Ratio   = firstNonNull(override == null ? null : override.maxP95Ratio(),            global.getMaxP95Ratio());
        long   minSamples = firstNonNullLong(override == null ? null : override.minSamplesThreshold(),  global.getMinSamplesThreshold());

        long canSamples = stats.canaryHits();
        long baseSamples = stats.baselineHits();

        List<GateResult> gates = new ArrayList<>();

        // 门 1：minSamples（canary 样本量）
        boolean enough = canSamples >= minSamples;
        gates.add(new GateResult("min_samples", enough, canSamples, minSamples,
                "canary rolling count >= " + minSamples));

        double canErr  = stats.canaryErrPct();
        double baseErr = stats.baselineErrPct();

        // 门 2：错误率漂移（仅能计算时才强制；err 字段未采集（-1）视为数据缺失 → SLO result 不回滚不推进）
        boolean errComputable = !Double.isNaN(canErr) && !Double.isNaN(baseErr) && canSamples > 0 && baseSamples > 0;
        if (errComputable) {
            double drift = canErr - baseErr;
            gates.add(new GateResult("error_drift_pct", drift <= driftPct, drift, driftPct,
                    "canaryErrPct - baselineErrPct ≤ " + driftPct + "%"));
        } else {
            gates.add(new GateResult("error_drift_pct", true, Double.NaN, driftPct,
                    "error stats unavailable (err5xx fields not populated); gate skipped"));
        }

        // 门 3：P95 倍率（缺失时跳过）
        long cp = stats.latencyP95CanaryMs();
        long bp = stats.latencyP95BaselineMs();
        boolean p95Computable = cp >= 0 && bp >= 0 && bp > 0;
        if (p95Computable) {
            double ratio = (bp == 0) ? 1d : (double) cp / bp;
            gates.add(new GateResult("p95_ratio", ratio <= p95Ratio, ratio, p95Ratio,
                    "canary_p95Ms / baseline_p95Ms ≤ " + p95Ratio));
        } else {
            gates.add(new GateResult("p95_ratio", true, Double.NaN, p95Ratio,
                    "p95 stats unavailable (latency buckets empty); gate skipped"));
        }

        // overall pass = ∧ all PASS；但"样本不足"或"全部不可用" → overall=false，同时标记 insufficientSamples/dataUnavailable。
        boolean overallPass = gates.stream().allMatch(g -> g.pass);
        boolean insufficient = !enough;
        boolean dataUnavail = !errComputable && !p95Computable && !stats.clusterAvailable();

        return new SloResult(gates, overallPass, insufficient, dataUnavail,
                canErr, baseErr, cp, bp, canSamples, baseSamples);
    }

    // -------- helpers --------

    private static Double firstNonNull(Double a, double b) {
        return a == null ? b : a;
    }

    private static long firstNonNullLong(Long a, long b) {
        return a == null ? b : a;
    }

    /** 单策略的 merged steps：override.steps → global.steps，最后强制含 100，去重排序。 */
    public static List<Integer> mergedSteps(GrayRolloutProperties global, GrayPolicyStore.RolloutConfig override) {
        List<Integer> src = (override != null && override.steps() != null && !override.steps().isEmpty())
                ? override.steps()
                : global.getSteps();
        java.util.TreeSet<Integer> set = new java.util.TreeSet<>();
        for (Integer i : src) if (i != null) set.add(Math.max(0, Math.min(100, i)));
        set.add(100);
        // 排除 0：0 不是步进台阶（rollback=0 另行处理）；但如果用户 steps 只有 [0,100]，保留 1 以便默认推进
        if (set.size() == 1 && set.contains(100)) set.add(1);
        if (set.first() == 0 && set.size() > 1) set.pollFirst();
        return new ArrayList<>(set);
    }

    /** 给定当前 weight，返回 steps 中的下标（找不到则返回"第一个大于 weight 的索引"）。 */
    public static int currentStepIndex(List<Integer> steps, int weight) {
        Objects.requireNonNull(steps);
        if (steps.isEmpty()) return 0;
        for (int i = 0; i < steps.size(); i++) {
            int s = steps.get(i);
            if (weight <= s) return i;
        }
        return steps.size() - 1;
    }
}
