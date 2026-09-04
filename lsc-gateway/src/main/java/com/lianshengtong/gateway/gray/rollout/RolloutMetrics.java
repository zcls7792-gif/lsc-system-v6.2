package com.lianshengtong.gateway.gray.rollout;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Phase N：Rollout Micrometer 指标（单例 Bean）。
 * <p>
 * 指标命名遵循 micrometer 建议（snake_case，前缀 lsc_gray）：
 * <ul>
 *   <li>{@code lsc_gray_rollout_tick_total} Counter：每一次 tick；tag: action=(scan/no_quorum/error/one_policy)</li>
 *   <li>{@code lsc_gray_rollout_step_info} Gauge：policyId/stepIndex/stepWeight 三维；值=1 表示当前策略位于该步；0 由 idleness 自动处理（Gauge 懒读 Map）。</li>
 *   <li>{@code lsc_gray_rollout_slo_result} Gauge：policyId + gate=(error_drift_pct/p95_ratio/min_samples/slo_unavailable)；value=1 PASS，0 FAIL。</li>
 *   <li>{@code lsc_gray_rollout_event_total} Counter：policyId + event=(STEP_ADVANCE/ROLLBACK_TRIGGERED/READY_FOR_GRAD/MANUAL_ADVANCE)。</li>
 * </ul>
 */
@Slf4j
public class RolloutMetrics {

    private final MeterRegistry registry;
    private final GrayRolloutProperties props;

    private final Map<String, Counter> tickCounterCache  = new ConcurrentHashMap<>();
    private final Map<String, Counter> eventCounterCache = new ConcurrentHashMap<>();

    // Slo gauge state：key = policyId + "|" + gateName；值 0/1
    private final ConcurrentHashMap<String, AtomicInteger> sloGauges = new ConcurrentHashMap<>();
    // Step gauge state：key = policyId + "|" + stepIndex + "|" + stepWeight；值 0/1
    private final ConcurrentHashMap<String, AtomicInteger> stepGauges = new ConcurrentHashMap<>();

    // Global tick/last-run Gauge：简单 atomicLong
    private final AtomicLong lastTickEpochSec = new AtomicLong(Instant.now().getEpochSecond());
    private final AtomicInteger leaderFlag = new AtomicInteger(0);
    private final AtomicReference<String> leaderNodeIdRef = new AtomicReference<>("");

    public RolloutMetrics(MeterRegistry registry, GrayRolloutProperties props) {
        this.registry = registry;
        this.props = props;
        Gauge.builder("lsc_gray_rollout_last_tick_epoch_sec", lastTickEpochSec, AtomicLong::get)
                .description("Unix epoch seconds of the last coordinator tick (leader only).")
                .register(registry);
        Gauge.builder("lsc_gray_rollout_leader", leaderFlag, AtomicInteger::get)
                .description("1 = the gateway pod is the current rollout coordinator leader; 0 otherwise.")
                .tag("nodeId", nodeId())
                .register(registry);
        Gauge.builder("lsc_gray_rollout_leader_node_id", leaderNodeIdRef, ref -> ref.get() == null || ref.get().isEmpty() ? 0 : 1)
                .description("Non-zero iff this is the elected leader (use label for textual id via tag).")
                .tag("nodeId", nodeId())
                .register(registry);
        // Slo / step gauges：注册懒 supplier 读上面的 Map（每 policy 每 gate 每步动态加入，Map.computeIfAbsent 返回 Atomic）
        // 注意：Gauge 本身是弱引用，但我们持有 Atomic 强引用避免 GC。
        Gauge.builder("lsc_gray_rollout_slo_result", sloGauges, this::sloGaugeMapped)
                .description("1 if the slo gate passed last tick, 0 otherwise.")
                .register(registry);
        Gauge.builder("lsc_gray_rollout_step_info", stepGauges, this::stepGaugeMapped)
                .description("1 if the policy is currently in the given step_index/weight, 0 otherwise.")
                .register(registry);
    }

    private double sloGaugeMapped(Map<String, AtomicInteger> m) {
        // Gauge 需要一个可观察的数值（聚合不太好）。这里我们把单独的 per-policy/per-gate 指标通过
        // Micrometer multi-gauge（io.micrometer.core.instrument.MultiGauge）会更理想，但为了少依赖，
        // 改为直接在 setSloGate() 里动态注册带 tag 的 Gauge。这个 stub 只防止 Gauge 注册报错；实际值通过 Gauges.tag 提供。
        return m.values().stream().mapToInt(AtomicInteger::get).sum();
    }
    private double stepGaugeMapped(Map<String, AtomicInteger> m) { return sloGaugeMapped(m); }

    public void tick(String action) {
        tickCounterCache.computeIfAbsent(action, a -> Counter.builder("lsc_gray_rollout_tick_total")
                        .description("Number of coordinator ticks executed.")
                        .tag("action", a)
                        .tag("nodeId", nodeId())
                        .register(registry))
                .increment();
        lastTickEpochSec.set(Instant.now().getEpochSecond());
    }

    public void event(String event, String policyId) {
        String key = event + "|" + policyId;
        eventCounterCache.computeIfAbsent(key, k -> Counter.builder("lsc_gray_rollout_event_total")
                        .description("Number of rollout events (step-advance/rollback/grad-ready/manual-advance).")
                        .tag("event", event)
                        .tag("policyId", policyId == null ? "" : policyId)
                        .register(registry))
                .increment();
    }

    /** 写 SLO 门结果：pass=1, fail=0；不存在于 sloGauges 时先注册一个带 (policyId,gate) Tag 的独立 Gauge（prometheus 友好）。 */
    public void sloGate(String policyId, String gateName, boolean pass) {
        String key = policyId + "|" + gateName;
        AtomicInteger atom = sloGauges.computeIfAbsent(key, k -> {
            AtomicInteger a = new AtomicInteger(pass ? 1 : 0);
            try {
                Gauge.builder("lsc_gray_rollout_slo_result", a, AtomicInteger::get)
                        .tag("policyId", policyId)
                        .tag("gate", gateName)
                        .tag("nodeId", nodeId())
                        .register(registry);
            } catch (Exception ignore) { /* 重复注册忽略（Micrometer 会抛出） */ }
            return a;
        });
        atom.set(pass ? 1 : 0);
    }

    /** 写 step info：对给定 policy 先把所有旧 step gauge 置 0，再把当前 stepIndex/weight 置 1。*/
    public void setCurrentStep(String policyId, List<Integer> steps, int currentStepIndex) {
        if (policyId == null) return;
        // 清理过期
        for (Map.Entry<String, AtomicInteger> e : stepGauges.entrySet()) {
            if (e.getKey().startsWith(policyId + "|")) e.getValue().set(0);
        }
        if (steps == null || steps.isEmpty()) return;
        int idx = Math.max(0, Math.min(steps.size() - 1, currentStepIndex));
        int weight = steps.get(idx);
        String key = policyId + "|" + idx + "|" + weight;
        AtomicInteger atom = stepGauges.computeIfAbsent(key, k -> {
            AtomicInteger a = new AtomicInteger(1);
            try {
                Gauge.builder("lsc_gray_rollout_step_info", a, AtomicInteger::get)
                        .tag("policyId", policyId)
                        .tag("stepIndex", String.valueOf(idx))
                        .tag("weightPct", String.valueOf(weight))
                        .tag("nodeId", nodeId())
                        .register(registry);
            } catch (Exception ignore) {}
            return a;
        });
        atom.set(1);
    }

    public void markLeader(boolean isLeader, String nodeIdReporting) {
        leaderFlag.set(isLeader ? 1 : 0);
        if (isLeader) leaderNodeIdRef.set(nodeId(nodeIdReporting));
    }

    String nodeId() { return nodeId(null); }
    String nodeId(String override) {
        if (override != null && !override.isBlank()) return override;
        if (props != null && props.getNodeId() != null && !props.getNodeId().isBlank()) return props.getNodeId();
        String env = System.getenv("POD_NAME"); if (env != null && !env.isBlank()) return env;
        env = System.getenv("HOSTNAME"); if (env != null && !env.isBlank()) return env;
        return "gw-node";
    }

    // 方便测试：把所有慢注册（如 Gauge tag 重复）异常吞掉，单测可不 real registry
    public static RolloutMetrics noop() {
        return new RolloutMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry(),
                new GrayRolloutProperties());
    }

    public long lastTickSecondsAgo() {
        return Instant.now().getEpochSecond() - lastTickEpochSec.get();
    }

    public String leaderNodeId() { return leaderNodeIdRef.get(); }
    public boolean isLeader() { return leaderFlag.get() == 1; }
}
