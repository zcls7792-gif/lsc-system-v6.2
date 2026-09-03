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
     */
    void record(String policyId, Version version, RuleForce ruleForce);

    /** 读取单个策略的聚合结果（用于 /stats）。 */
    AggregatedStats aggregated(String policyId);

    /** 读取全部策略的聚合快照（用于 /summary 聚类）。 */
    Map<String, AggregatedStats> aggregatedAll();

    /** 启动时可选：通过 TTL 清理老键；实现按需。 */
    default void sweep(Duration retention) { }

    enum Version { BASELINE, CANARY }

    enum RuleForce { NONE, FORCE_CANARY, FORCE_BASELINE }

    /** 聚合结果：同时暴露本地实例视图与 Redis 集群视图。 */
    record AggregatedStats(
            long baselineHits,
            long canaryHits,
            long ruleForceCanary,
            long ruleForceBaseline,
            long startEpochSec,   // 该策略在 Redis 中的最早写入时间（秒），缺失 0
            int liveNodes,        // 当前观测到的活跃实例数（近似，可能浮动）
            boolean clusterAvailable   // 集群层是否存在值：true=Redis 可用并能读到；false=仅本地兜底
    ) {
        public long totalRequests() { return baselineHits + canaryHits; }
    }
}
