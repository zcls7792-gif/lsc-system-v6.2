package com.lianshengtong.gateway.gray.rollout;

import com.lianshengtong.gateway.gray.GrayPolicyService;
import com.lianshengtong.gateway.gray.GrayPolicyStore;
import com.lianshengtong.gateway.gray.stats.GrayStatsAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Phase N：灰度发布自治理调度器（Gateway 内单例）。
 * <p>
 * 典型运行链路：
 * <pre>
 * 每 tick (15s)：
 *   1. 拿 leader 锁（Redisson → 不可用降级 JVM AtomicBoolean CAS）；非 leader：metrics.tick(skip_no_quorum) return
 *   2. 遍历 store.list()：
 *      status in {ACTIVE, READY_FOR_GRADUATION} 且 (rollout.enabled != false) → handleOne
 *   3. handleOne(policy)：
 *      a. 取 mergedSteps（全局 override + policy.rolloutConfig）
 *      b. currentStepIndex = 当前 steps.index(steps[step] >= weight)
 *      c. 维护 runtime[policyId]（enteredStepAt, consecutiveFail, consecutivePass）
 *      d. 读 statsAggregator.aggregated(policyId)（最多 2s 超时保护；超过 → slo.unavailable）
 *      e. SloGuard.evaluate()：
 *          - PASS 且 hold 秒 >= minMinutesAtStep×60 → advanceWeightTo(nextStep) / 100% 则 markReadyForGraduation
 *          - FAIL 且 !insufficientSamples → consecutiveFail++，达到 maxConsecFailuresBeforeRollback → rollback
 *          - insufficientSamples / dataUnavailable → 不推进不回滚，pass/fail counter 清零
 *   4. 写 metrics（tick/step/slo/event）
 * </pre>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "gray.rollout.enabled", matchIfMissing = true)
public class GrayRolloutCoordinator {

    private final GrayPolicyStore store;
    private final GrayPolicyService service;
    private final GrayStatsAggregator statsAggregator;
    private final GrayRolloutProperties props;
    private final RolloutMetrics metrics;
    /** Redisson 分布式锁 Bean（可选；Class.forName + reflection 调用，避免 pom 硬依赖）。 */
    private final ObjectProvider<Object> redissonClientProvider;

    /** policyId → runtime state（所有实例都会维护，便于 UI rollout/status 全实例可读；只有 leader 写权重）*/
    final ConcurrentHashMap<String, RolloutRuntimeState> runtime = new ConcurrentHashMap<>();

    /** Rollout 状态外部只读访问（Controller / Actuator）。 */
    public RolloutRuntimeState runtimeStateFor(String policyId) {
        return runtime.get(policyId);
    }

    // --- JVM 兜底 leader 选举（Redisson 不可用时启用）---
    private static final AtomicBoolean JVM_LEADER_CAS = new AtomicBoolean(false);
    private volatile boolean jvmLeader = false;
    private volatile long lastLeaderRefreshMs = 0L;
    private static final long JVM_LEASE_MS = 55_000L;
    /** 手动推进步骤时的本地串行化：防止同时触发 + 自动 tick 竞争写权重 */
    private final ReentrantLock policyWeightWriteLock = new ReentrantLock();

    /** Spring 构造注入（RedissonClient Bean 名通过 ObjectProvider<Object> 懒加载，缺类也能启动）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public GrayRolloutCoordinator(GrayPolicyStore store,
                                   GrayPolicyService service,
                                   GrayStatsAggregator statsAggregator,
                                   GrayRolloutProperties props,
                                   RolloutMetrics metrics,
                                   ObjectProvider<Object> redissonClientProvider) {
        this.store = store;
        this.service = service;
        this.statsAggregator = statsAggregator;
        this.props = props;
        this.metrics = metrics;
        this.redissonClientProvider = redissonClientProvider;
    }

    /** 单测/手工构造（无 Redisson）。 */
    public GrayRolloutCoordinator(GrayPolicyStore store,
                                   GrayPolicyService service,
                                   GrayStatsAggregator statsAggregator,
                                   GrayRolloutProperties props,
                                   RolloutMetrics metrics) {
        this(store, service, statsAggregator, props, metrics, new ObjectProvider<Object>() {
            public Object getObject() { return null; }
            public Object getObject(Object... args) { return null; }
            public Object getIfAvailable() { return null; }
            public Object getIfUnique() { return null; }
        });
    }

    @Scheduled(fixedDelayString = "${gray.rollout.tickMs:15000}")
    public void tick() {
        policyWeightWriteLock.lock();
        try {
            if (!amILeader()) {
                metrics.tick("skip_no_quorum");
                return;
            }
            metrics.tick("scan");
            Instant now = Instant.now();
            int handled = 0;
            for (GrayPolicyStore.Policy p : store.list()) {
                try {
                    if (p.status() != GrayPolicyStore.Status.ACTIVE
                            && p.status() != GrayPolicyStore.Status.READY_FOR_GRADUATION) continue;
                    if (p.rolloutConfig() != null && Boolean.FALSE.equals(p.rolloutConfig().enabled())) continue;
                    handleOne(p, now);
                    handled++;
                } catch (Exception ex) {
                    log.warn("[gray-rollout] handleOne policy={} failed: {}", p.policyId(), ex.getMessage());
                    metrics.tick("error_per_policy");
                }
            }
            if (handled > 0) metrics.tick("scan_handled_" + Math.min(handled, 99));
        } catch (Exception ex) {
            log.error("[gray-rollout] tick error: {}", ex.getMessage());
            metrics.tick("error");
        } finally {
            policyWeightWriteLock.unlock();
        }
    }

    // ========================================================================================
    // 单策略决策
    // ========================================================================================
    void handleOne(GrayPolicyStore.Policy policy, Instant now) {
        List<Integer> steps = SloGuard.mergedSteps(props, policy.rolloutConfig());
        int w = policy.canaryWeightPercent();
        int stepIdx = SloGuard.currentStepIndex(steps, w);
        int stepWeight = steps.get(stepIdx);

        // 初始化/修正 runtime state：weight 不在当前步 → 重置 enteredStepAt
        RolloutRuntimeState state = runtime.computeIfAbsent(policy.policyId(), k -> new RolloutRuntimeState());
        boolean stepChanged = (state.currentStepIndex != stepIdx || state.currentStepWeight != stepWeight);
        if (state.enteredStepAt == null || stepChanged) {
            state.currentStepIndex = stepIdx;
            state.currentStepWeight = stepWeight;
            state.enteredStepAt = now;
            state.consecutiveSloFailures = 0;
            state.consecutiveSloPasses = 0;
        }
        state.lastTickAt = now;
        metrics.setCurrentStep(policy.policyId(), steps, stepIdx);

        // --- 读取聚合 stats（最多 statsTimeoutMs 毫秒）---
        GrayStatsAggregator.AggregatedStats agg;
        try {
            agg = java.util.concurrent.CompletableFuture
                    .supplyAsync(() -> statsAggregator.aggregated(policy.policyId()))
                    .get(props.getStatsTimeoutMs(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            log.warn("[gray-rollout] aggregated stats timeout/unavailable for policy={}: {}", policy.policyId(), ex.getMessage());
            agg = null;
        }
        SloGuard.SloResult slo = SloGuard.evaluate(agg, props, policy.rolloutConfig());
        // 写 slo metrics（per gate）
        for (SloGuard.GateResult g : slo.gates()) metrics.sloGate(policy.policyId(), g.name(), g.pass());
        state.lastSloSummary = slo.failSummary();

        if (slo.dataUnavailable() && slo.insufficientSamples()) {
            // 数据完全不可用 → 不推进不回滚（避免误判）；只打 WARN
            log.debug("[gray-rollout] policy={} slo data unavailable, skip this tick", policy.policyId());
            return;
        }

        // 单策略 override 参数（与 SloGuard 同名同语义，再次合并避免错读）
        int minMin = firstNonNullInt(policy.rolloutConfig() == null ? null : policy.rolloutConfig().minMinutesAtStep(), props.getMinMinutesAtStep());
        int maxFails = firstNonNullInt(policy.rolloutConfig() == null ? null : policy.rolloutConfig().maxConsecutiveFailuresBeforeRollback(), props.getMaxConsecutiveFailuresBeforeRollback());

        // 样本不足 → 不步进不回滚；但如果 SLO 同时整体 PASS，也不累计 pass（避免一上来就把 consecutivePasses 冲很高）
        if (slo.insufficientSamples()) return;

        if (slo.overallPass()) {
            state.consecutiveSloFailures = 0;
            state.consecutiveSloPasses = Math.min(1_000_000, state.consecutiveSloPasses + 1);
            long holdSec = Duration.between(state.enteredStepAt, now).getSeconds();
            long needSec = (long) minMin * 60L;
            if (holdSec >= needSec) {
                // 到达步进时机
                if (stepIdx >= steps.size() - 1) {
                    // 最后一步（weight=100）→ 切 READY_FOR_GRADUATION
                    if (policy.status() != GrayPolicyStore.Status.READY_FOR_GRADUATION) {
                        GrayPolicyStore.Policy marked = service.markReadyForGraduation(policy.policyId(), "system:rollout");
                        if (marked != null) {
                            metrics.event("READY_FOR_GRAD", policy.policyId());
                            log.info("[gray-rollout] policy={} READY_FOR_GRADUATION after hold {}s >= need {}s", policy.policyId(), holdSec, needSec);
                        }
                    }
                } else {
                    int nextW = steps.get(stepIdx + 1);
                    GrayPolicyStore.Policy advanced = service.advanceWeightTo(policy.policyId(), nextW, "system:rollout",
                            String.format("SLO_PASS hold %ds, step %d%%->%d%%", holdSec, stepWeight, nextW));
                    if (advanced != null) {
                        metrics.event("STEP_ADVANCE", policy.policyId());
                        log.info("[gray-rollout] policy={} step advance {}% -> {}% (hold {}s)", policy.policyId(), stepWeight, nextW, holdSec);
                    }
                }
            }
        } else {
            state.consecutiveSloPasses = 0;
            state.consecutiveSloFailures = Math.min(1000, state.consecutiveSloFailures + 1);
            if (state.consecutiveSloFailures >= Math.max(1, maxFails)) {
                // 硬回滚
                String reason = "SLO_BREAK " + slo.failSummary();
                GrayPolicyStore.Policy rolled = service.rollback(policy.policyId(), "system:rollback", reason);
                if (rolled != null) {
                    runtime.remove(policy.policyId());
                    metrics.event("ROLLBACK_TRIGGERED", policy.policyId());
                    log.warn("[gray-rollout] policy={} HARD ROLLBACK triggered (consecFail={}): {}",
                            policy.policyId(), state.consecutiveSloFailures, reason);
                }
            } else {
                log.debug("[gray-rollout] policy={} slo fail consec={}/{} : {}",
                        policy.policyId(), state.consecutiveSloFailures, maxFails, slo.failSummary());
            }
        }
    }

    // ========================================================================================
    // Leader Election：优先 Redisson fair lock (reflection) → 不可用则 JVM CAS + 租约过期自动重选举
    // 注：故意不 import RedissonClient / RLock 硬类（pom 默认排除 redisson starter），通过反射容错。
    // ========================================================================================
    public boolean amILeader() {
        // 1) 优先 Redisson 分布式锁（公平锁，tryLock 1s wait + lease = props.leaderLeaseMs）
        Object r = redissonClientProvider.getIfAvailable();
        if (r != null && CLASS_REDISSON_CLIENT.isInstance(r)) {
            try {
                Object lock = M_GET_FAIR_LOCK.invoke(r, "lsc:gray:rollout:coordinator:leader");
                boolean got = (Boolean) M_TRY_LOCK.invoke(lock,
                        1L, Math.max(30_000L, props.getLeaderLeaseMs()), java.util.concurrent.TimeUnit.MILLISECONDS);
                if (got) {
                    metrics.markLeader(true, metrics.nodeId());
                    return true;
                }
            } catch (Exception ex) {
                log.warn("[gray-rollout] redisson leader lock unavailable, fallback to JVM CAS: {}", ex.getMessage());
            }
        }
        // 2) JVM 兜底：只有 allowJvmLeaderFallback=true 才允许
        if (!props.isAllowJvmLeaderFallback()) {
            metrics.markLeader(false, metrics.nodeId());
            return false;
        }
        long now = System.currentTimeMillis();
        if (jvmLeader) {
            if (now - lastLeaderRefreshMs > JVM_LEASE_MS) {
                jvmLeader = false;
                JVM_LEADER_CAS.set(false);
            } else {
                lastLeaderRefreshMs = now;
                metrics.markLeader(true, metrics.nodeId());
                return true;
            }
        }
        boolean acquired = JVM_LEADER_CAS.compareAndSet(false, true);
        if (acquired) {
            jvmLeader = true;
            lastLeaderRefreshMs = now;
            metrics.markLeader(true, metrics.nodeId());
            return true;
        }
        metrics.markLeader(false, metrics.nodeId());
        return false;
    }

    /** 运维/测试专用：强制放弃 leader（便于切换或 HA 演练）。*/
    public void forceResignLeader() {
        try {
            Object r = redissonClientProvider.getIfAvailable();
            if (r != null && CLASS_REDISSON_CLIENT.isInstance(r)) {
                Object lock = M_GET_FAIR_LOCK.invoke(r, "lsc:gray:rollout:coordinator:leader");
                Boolean held = (Boolean) M_IS_HELD_BY_CURR_THREAD.invoke(lock);
                if (Boolean.TRUE.equals(held)) M_UNLOCK.invoke(lock);
            }
        } catch (Exception ignore) {}
        if (jvmLeader) {
            jvmLeader = false;
            JVM_LEADER_CAS.set(false);
        }
        metrics.markLeader(false, metrics.nodeId());
    }

    // ================= Redisson 反射桥接（懒加载，classpath 缺类时所有字段均为 null → 回退 JVM CAS）=================
    private static final Class<?> CLASS_REDISSON_CLIENT;
    private static final Class<?> CLASS_R_LOCK;
    private static final java.lang.reflect.Method M_GET_FAIR_LOCK;
    private static final java.lang.reflect.Method M_TRY_LOCK;
    private static final java.lang.reflect.Method M_IS_HELD_BY_CURR_THREAD;
    private static final java.lang.reflect.Method M_UNLOCK;
    static {
        Class<?> rc = null, rl = null;
        java.lang.reflect.Method m1 = null, m2 = null, m3 = null, m4 = null;
        try {
            rc = Class.forName("org.redisson.api.RedissonClient");
            rl = Class.forName("org.redisson.api.RLock");
            m1 = rc.getMethod("getFairLock", String.class);
            m2 = rl.getMethod("tryLock", long.class, long.class, java.util.concurrent.TimeUnit.class);
            m3 = rl.getMethod("isHeldByCurrentThread");
            m4 = rl.getMethod("unlock");
        } catch (Throwable ignore) {
            // Redisson 未部署 → 全部 null，后续 amILeader 自动走 JVM CAS fallback
        }
        CLASS_REDISSON_CLIENT = rc;
        CLASS_R_LOCK = rl;
        M_GET_FAIR_LOCK = m1;
        M_TRY_LOCK = m2;
        M_IS_HELD_BY_CURR_THREAD = m3;
        M_UNLOCK = m4;
    }

    /** advance-step 手动推进：Coordinator 不单独判断 SLO（相信 ops），仅写入审计 + 调用 service.advanceWeightTo，再重置当前 runtime。 */
    public GrayPolicyStore.Policy manualAdvanceStep(String policyId, String operator, String reason) {
        policyWeightWriteLock.lock();
        try {
            GrayPolicyStore.Policy cur = store.get(policyId);
            if (cur == null) return null;
            if (cur.rolloutConfig() != null && Boolean.FALSE.equals(cur.rolloutConfig().enabled())) {
                throw new IllegalStateException("rollout disabled for policy " + policyId + " (policy.rolloutConfig.enabled=false)");
            }
            List<Integer> steps = SloGuard.mergedSteps(props, cur.rolloutConfig());
            int stepIdx = SloGuard.currentStepIndex(steps, cur.canaryWeightPercent());
            if (stepIdx >= steps.size() - 1) {
                GrayPolicyStore.Policy ready = service.markReadyForGraduation(policyId, operator == null ? "ops" : operator);
                metrics.event("MANUAL_ADVANCE", policyId);
                service.appendHistory(policyId, operator == null ? "ops" : operator,
                        "MANUAL advance at max step -> READY_FOR_GRADUATION (" + (reason == null ? "" : reason) + ")");
                return ready;
            }
            int next = steps.get(stepIdx + 1);
            GrayPolicyStore.Policy advanced = service.advanceWeightTo(policyId, next,
                    operator == null ? "ops" : operator, reason == null ? "manual advance" : reason);
            metrics.event("MANUAL_ADVANCE", policyId);
            RolloutRuntimeState s = runtime.get(policyId);
            if (s == null) { s = new RolloutRuntimeState(); runtime.put(policyId, s); }
            s.currentStepIndex = stepIdx + 1;
            s.currentStepWeight = next;
            s.enteredStepAt = Instant.now();
            s.consecutiveSloFailures = 0;
            s.consecutiveSloPasses = 0;
            return advanced;
        } finally {
            policyWeightWriteLock.unlock();
        }
    }

    // =============== helpers ===============
    private static int firstNonNullInt(Integer a, int b) { return a == null ? b : a; }

    /** Controller 专用：返回 leader 信息 + 正在 rollout 的 policy 数 / 连续失败策略数。 */
    public Map<String, Object> statusSnapshot(Instant now) {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("coordinatorEnabled", true);
        out.put("tickMs", props.getTickMs());
        out.put("nodeId", metrics.nodeId());
        out.put("leader", metrics.isLeader());
        out.put("leaderNodeId", metrics.leaderNodeId());
        out.put("lastTickSecondsAgo", metrics.lastTickSecondsAgo());
        int running = 0, failing = 0, readyGrad = 0;
        for (GrayPolicyStore.Policy p : store.list()) {
            if (p.status() == GrayPolicyStore.Status.READY_FOR_GRADUATION) readyGrad++;
            if (p.status() != GrayPolicyStore.Status.ACTIVE
                    && p.status() != GrayPolicyStore.Status.READY_FOR_GRADUATION) continue;
            if (p.rolloutConfig() != null && Boolean.FALSE.equals(p.rolloutConfig().enabled())) continue;
            running++;
            RolloutRuntimeState s = runtime.get(p.policyId());
            if (s != null && s.consecutiveSloFailures >= Math.max(1, firstNonNullInt(
                    p.rolloutConfig() == null ? null : p.rolloutConfig().maxConsecutiveFailuresBeforeRollback(),
                    props.getMaxConsecutiveFailuresBeforeRollback()))) failing++;
        }
        out.put("policiesRunning", running);
        out.put("policiesReadyForGraduation", readyGrad);
        out.put("policiesApproachingRollback", failing);
        out.put("stepsDefault", props.getSteps());
        out.put("minMinutesAtStepDefault", props.getMinMinutesAtStep());
        out.put("maxErrorDriftPctDefault", props.getMaxErrorDriftPct());
        out.put("maxP95RatioDefault", props.getMaxP95Ratio());
        out.put("minSamplesDefault", props.getMinSamplesThreshold());
        out.put("maxConsecutiveFailuresDefault", props.getMaxConsecutiveFailuresBeforeRollback());
        return out;
    }
}
