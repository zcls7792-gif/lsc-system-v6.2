package com.lianshengtong.gateway.gray.stats;

import com.lianshengtong.gateway.gray.GrayPolicyStore;

import java.time.Duration;
import java.util.Map;

/**
 * Phase K：灰度命中统计聚合器（支持 Redis 集群共享 + 本地内存实例视图两层）。
 * <p>
 * 设计要点：
 * <ol>
 *   <li>热路径写入（单请求）仅做 INCR：本地 AtomicLong 直接写入 + Redis INCR 异步/同步 best-effort；
 *       Redis 写入失败不抛错，只记录 WARN，不影响网关主链路。</li>
 *   <li>读路径（/stats / /summary / Actuator）：同时返回 instanceLocal（当前网关）和 cluster（Redis 聚合）
 *       两张视图；运维可据此判断"是否某实例异常高 canary 命中比例"。</li>
 *   <li>秒级桶（60 桶）不跨实例共享（数据量过大），只在本地 GrayPolicyStore.Stats 维护。</li>
 * </ol>
 *
 * 键命名（可通过 {@code lsc.gray.stats.prefix} 覆盖，默认 lsc:gray:stats）：
 * <pre>
 *   {prefix}:policy:{policyId}:baseline_hits
 *   {prefix}:policy:{policyId}:canary_hits
 *   {prefix}:policy:{policyId}:rule_force_canary
 *   {prefix}:policy:{policyId}:rule_force_baseline
 *   {prefix}:policy:{policyId}:start_ts  (epoch seconds, 实例首次写入时写入集群总窗口起点；如已存在则不动)
 *   {prefix}:policy:{policyId}:live_nodes  (Redis Set：活跃实例 host:port；每次写入加 SADD，30 分钟不更新由 TTL 自动淘汰：单个键写入时 EXPIRE 节点)
 * </pre>
 */
public interface GrayStatsAggregator {

    /** 写入一次命中（在 GrayReleaseGlobalFilter 中调用）。
     * @param policyId    策略 ID
     * @param version     baseline / canary（对应 baseline_hits / canary_hits）
     * @param ruleForce   null / ruleForceCanary / ruleForceBaseline：命中规则方向则加一
     * @param statusCode  HTTP 状态码（5xx 用于错误率统计；0/负数视为"暂不可用"跳过 SloGate 的 err 门限）
     * @param latencyMs   请求耗时（毫秒；负数忽略不写入 p95 桶）
     */
    default void record(String policyId, Version version, RuleForce ruleForce,
                        int statusCode, long latencyMs) {
        // 默认兼容：旧实现仍可只调用 record(policyId, version, ruleForce) 即可
        record(policyId, version, ruleForce);
    }

    /** 兼容热路径：只写命中计数，不传入 status/latency（SloGuard 会降级为不做错误率/P95 判断，以样本不足通过）。 */
    void record(String policyId, Version version, RuleForce ruleForce);

    /** 读取单个策略的聚合结果（用于 /stats）。 */
    AggregatedStats aggregated(String policyId);

    /** 读取全部策略的聚合快照（用于 /summary 聚类）。 */
    Map<String, AggregatedStats> aggregatedAll();

    /** 启动时可选：通过 TTL 清理老键；实现按需。 */
    default void sweep(Duration retention) { }

    enum Version { BASELINE, CANARY }

    enum RuleForce { NONE, FORCE_CANARY, FORCE_BASELINE }

    /**
     * 聚合结果：本地实例 + 集群视图。
     * <p>
     * 新增 Phase N 字段：err5xxXxx（5xx 数量） + latencyP95XxxMs（P95 毫秒）。
     * 若实现无法提供 err/p95 估算（如 Redis 端未建桶），字段保持 -1；SloGuard 会把该门视为"数据不足"。
     */
    record AggregatedStats(
            long baselineHits,
            long canaryHits,
            long ruleForceCanary,
            long ruleForceBaseline,
            long startEpochSec,
            int liveNodes,
            boolean clusterAvailable,
            // Phase N 新增：错误计数（-1 表示未采集）
            long err5xxBaseline,
            long err5xxCanary,
            // Phase N 新增：P95 估算（毫秒；-1 未采集）
            long latencyP95BaselineMs,
            long latencyP95CanaryMs
    ) {
        /** 本地兜底 + 历史兼容构造（缺 err/p95 时填 -1）。 */
        public static AggregatedStats legacy(long baselineHits, long canaryHits,
                                             long ruleForceCanary, long ruleForceBaseline,
                                             long startEpochSec, int liveNodes, boolean clusterAvailable) {
            return new AggregatedStats(baselineHits, canaryHits, ruleForceCanary, ruleForceBaseline,
                    startEpochSec, liveNodes, clusterAvailable, -1, -1, -1, -1);
        }
        public long totalRequests() { return baselineHits + canaryHits; }
        /** canary 错误率（百分比）；未采集返回 Double.NaN */
        public double canaryErrPct() {
            if (err5xxCanary < 0 || canaryHits <= 0) return Double.NaN;
            return 100.0 * err5xxCanary / canaryHits;
        }
        /** baseline 错误率（百分比）；未采集返回 Double.NaN */
        public double baselineErrPct() {
            if (err5xxBaseline < 0 || baselineHits <= 0) return Double.NaN;
            return 100.0 * err5xxBaseline / baselineHits;
        }
    }
}
