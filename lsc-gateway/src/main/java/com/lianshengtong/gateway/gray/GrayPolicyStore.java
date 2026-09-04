package com.lianshengtong.gateway.gray;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 灰度策略仓储（内存态，单实例场景快速打通闭环；多实例场景建议接入 Redis/Nacos）。
 * <p>
 * 数据模型：
 * <ul>
 *   <li>policyId：业务语义化，例 "order-service-v2.1"</li>
 *   <li>routeId：对应 application.yml 中的路由 id（user-service / ledger-service / order-service …）</li>
 *   <li>baselineUri：基线版本 lb://{serviceId}</li>
 *   <li>canaryUri：灰度版本 lb://{serviceId}-canary 或 lb://{serviceId}{-suffix}（K8s 下可对应不同 Deployment/Service subset）</li>
 *   <li>canaryWeightPercent：0~100 整数灰度权重，命中比例；0 = 关闭灰度，100 = 全量切</li>
 *   <li>rules：硬命中规则（X-Canary=force / userId%N=0 / header:X-User-Type=merchant …）</li>
 *   <li>status：ACTIVE / PAUSED / ROLLED_BACK</li>
 * </ul>
 * </p>
 */
public class GrayPolicyStore {

    public enum Status { DRAFT, ACTIVE, READY_FOR_GRADUATION, PAUSED, ROLLED_BACK, GRADUATED, DELETED }

    /** 灰度策略命中规则 */
    public record Rule(
            String type,             // HEADER / QUERY / COOKIE / USER_ID_MOD / PATH_PREFIX
            String key,              // 键: 头名 / 参数名 / cookie 名 / null
            String operator,         // EQ / NE / PREFIX / MOD_EQ
            String value,            // 值: 字符串 / "mod==0" 中的余
            String extra             // 额外配置: USER_ID_MOD 中 mod=N → "N"
    ) {}

    /**
     * Phase N：单策略 rollout 覆盖配置（null = 继承全局 GrayRolloutProperties 默认）。
     * 允许单策略自定义步进台阶、SLO 门限、或禁用自动推进（enabled=false）。
     */
    public record RolloutConfig(
            /* 非空则覆盖全局 steps，最后一项强制 <=100 并在缺失时补 100 */
            List<Integer> steps,
            Integer minMinutesAtStep,
            Double maxErrorDriftPct,
            Double maxP95Ratio,
            Long minSamplesThreshold,
            Integer maxConsecutiveFailuresBeforeRollback,
            /* null=true=继承；false=该策略禁用自动步进（仅允许 advance-step 手动推进 / rollback） */
            Boolean enabled
    ) {}

    /** 灰度策略快照（不可变） */
    public record Policy(
            String policyId,
            String routeId,
            String baselineUri,
            String canaryUri,
            int canaryWeightPercent,          // 0..100
            List<Rule> rules,
            Map<String, String> meta,         // { author, ticketId, reason, k8sCanaryDeployment }
            Status status,
            Instant createdAt,
            Instant updatedAt,
            String updatedBy,
            /* Phase N: 单策略 rollout 覆盖；null=全用全局配置 */
            RolloutConfig rolloutConfig
    ) {
        /** 策略是否处于"可参与分流决策"的状态（注意：weight=0 但有 rules 时仍需参与，
         * 因为 rules 允许强制切基线/灰度；此时权重随机分支会落在 baseline）。 */
        public boolean active() {
            return (status == Status.ACTIVE || status == Status.READY_FOR_GRADUATION)
                    && (canaryWeightPercent > 0 || (rules != null && !rules.isEmpty()));
        }

        /** Lombok-like toBuilder：构造基于当前 Policy 的新副本 */
        public PolicyBuilder toBuilder() { return new PolicyBuilder(this); }

        /** builder 兼容（Policy.record 原生不带 @Builder，手写轻量 Builder 以降低侵入性）*/
        public static PolicyBuilder builder() { return new PolicyBuilder(); }

        /** 向后兼容工厂：缺失 rolloutConfig（第 12 个字段）时填 null。等价于老版本的 11 参数构造。
         *  迁移指南：老代码 new Policy(pid,routeId,base,can,weight,rules,meta,status,ca,ua,ub) → 替换为 Policy.legacy(...) 或使用 builder。 */
        public static Policy legacy(String policyId,
                                     String routeId,
                                     String baselineUri,
                                     String canaryUri,
                                     int canaryWeightPercent,
                                     List<Rule> rules,
                                     Map<String, String> meta,
                                     Status status,
                                     Instant createdAt,
                                     Instant updatedAt,
                                     String updatedBy) {
            return new Policy(policyId, routeId, baselineUri, canaryUri, canaryWeightPercent,
                    rules == null ? List.of() : rules,
                    meta  == null ? Map.of() : meta,
                    status == null ? Status.ACTIVE : status,
                    createdAt == null ? Instant.now() : createdAt,
                    updatedAt == null ? Instant.now() : updatedAt,
                    updatedBy, null);
        }
    }

    public static class PolicyBuilder {
        String policyId; String routeId; String baselineUri; String canaryUri;
        int canaryWeightPercent; List<Rule> rules; Map<String,String> meta;
        Status status; Instant createdAt; Instant updatedAt; String updatedBy;
        RolloutConfig rolloutConfig;
        PolicyBuilder() {}
        PolicyBuilder(Policy src) {
            this.policyId = src.policyId; this.routeId = src.routeId;
            this.baselineUri = src.baselineUri; this.canaryUri = src.canaryUri;
            this.canaryWeightPercent = src.canaryWeightPercent;
            this.rules = src.rules(); this.meta = src.meta(); this.status = src.status();
            this.createdAt = src.createdAt(); this.updatedAt = src.updatedAt();
            this.updatedBy = src.updatedBy(); this.rolloutConfig = src.rolloutConfig();
        }
        public PolicyBuilder policyId(String v) { policyId = v; return this; }
        public PolicyBuilder routeId(String v) { routeId = v; return this; }
        public PolicyBuilder baselineUri(String v) { baselineUri = v; return this; }
        public PolicyBuilder canaryUri(String v) { canaryUri = v; return this; }
        public PolicyBuilder canaryWeightPercent(int v) { canaryWeightPercent = v; return this; }
        public PolicyBuilder rules(List<Rule> v) { rules = v; return this; }
        public PolicyBuilder meta(Map<String,String> v) { meta = v; return this; }
        public PolicyBuilder status(Status v) { status = v; return this; }
        public PolicyBuilder createdAt(Instant v) { createdAt = v; return this; }
        public PolicyBuilder updatedAt(Instant v) { updatedAt = v; return this; }
        public PolicyBuilder updatedBy(String v) { updatedBy = v; return this; }
        public PolicyBuilder rolloutConfig(RolloutConfig v) { rolloutConfig = v; return this; }
        public Policy build() {
            return new Policy(policyId, routeId, baselineUri, canaryUri, canaryWeightPercent,
                    rules == null ? List.of() : rules,
                    meta  == null ? Map.of() : meta,
                    status == null ? Status.ACTIVE : status,
                    createdAt == null ? Instant.now() : createdAt,
                    updatedAt == null ? Instant.now() : updatedAt,
                    updatedBy, rolloutConfig);
        }
    }

    /** 变更历史条目 */
    public record History(
            Instant ts,
            String policyId,
            String operator,
            String action,   // CREATE / UPDATE / ROLLBACK / PAUSE / RESUME / WEIGHT_CHANGE
            String detail
    ) {}

    /** 命中统计 */
    public static class Stats {
        public final AtomicLong baselineHits = new AtomicLong();
        public final AtomicLong canaryHits = new AtomicLong();
        public final AtomicLong ruleForceCanary = new AtomicLong();
        public final AtomicLong ruleForceBaseline = new AtomicLong();
        public final AtomicLong startTimeMs = new AtomicLong(System.currentTimeMillis());
        /** 每分钟窗口（秒级桶简化：最近 60s 命中计数器） */
        public final AtomicLong[] perSecondBaseline = new AtomicLong[60];
        public final AtomicLong[] perSecondCanary   = new AtomicLong[60];
        {
            for (int i = 0; i < 60; i++) {
                perSecondBaseline[i] = new AtomicLong();
                perSecondCanary[i]   = new AtomicLong();
            }
        }
    }

    private final Map<String, AtomicReference<Policy>> policies = new ConcurrentHashMap<>();
    private final Map<String, Stats> stats = new ConcurrentHashMap<>();
    private final java.util.Deque<History> history = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public Policy createOrUpdate(Policy in, String operator) {
        Instant now = Instant.now();
        Policy toSave = in.createdAt() == null
                ? Policy.builder().policyId(in.policyId()).routeId(in.routeId())
                    .baselineUri(in.baselineUri()).canaryUri(in.canaryUri())
                    .canaryWeightPercent(clamp(in.canaryWeightPercent()))
                    .rules(in.rules()).meta(in.meta())
                    .status(in.status() == null ? Status.ACTIVE : in.status())
                    .createdAt(now).updatedAt(now).updatedBy(operator)
                    .rolloutConfig(in.rolloutConfig()).build()
                : Policy.builder().policyId(in.policyId()).routeId(in.routeId())
                    .baselineUri(in.baselineUri()).canaryUri(in.canaryUri())
                    .canaryWeightPercent(clamp(in.canaryWeightPercent()))
                    .rules(in.rules()).meta(in.meta())
                    .status(in.status() == null ? Status.ACTIVE : in.status())
                    .createdAt(in.createdAt()).updatedAt(now).updatedBy(operator)
                    .rolloutConfig(in.rolloutConfig()).build();

        AtomicReference<Policy> ref = policies.computeIfAbsent(toSave.policyId(),
                k -> new AtomicReference<>());
        Policy prev = ref.get();
        ref.set(toSave);
        stats.computeIfAbsent(toSave.policyId(), k -> new Stats());
        history.addFirst(new History(now, toSave.policyId(), operator,
                prev == null ? "CREATE" : "UPDATE", describeDiff(prev, toSave)));
        trimHistory();
        return toSave;
    }

    public Policy get(String policyId) {
        AtomicReference<Policy> ref = policies.get(policyId);
        return ref == null ? null : ref.get();
    }

    public List<Policy> list() {
        List<Policy> out = new ArrayList<>(policies.size());
        for (AtomicReference<Policy> r : policies.values()) {
            Policy p = r.get();
            if (p != null) out.add(p);
        }
        return out;
    }

    /** 按 routeId 查找 ACTIVE 策略（Gateway filter 中使用，热点路径） */
    public Policy findActiveForRoute(String routeId) {
        if (routeId == null) return null;
        for (AtomicReference<Policy> r : policies.values()) {
            Policy p = r.get();
            if (p != null && p.active() && routeId.equals(p.routeId())) return p;
        }
        return null;
    }

    public Policy rollback(String policyId, String operator, String reason) {
        AtomicReference<Policy> ref = policies.get(policyId);
        if (ref == null) return null;
        Policy cur = ref.get();
        Policy rolledBack = cur.toBuilder()
                .canaryWeightPercent(0).status(Status.ROLLED_BACK)
                .updatedAt(Instant.now()).updatedBy(operator).build();
        ref.set(rolledBack);
        history.addFirst(new History(Instant.now(), policyId, operator, "ROLLBACK",
                "canaryWeight 0%; reason=" + reason));
        trimHistory();
        return rolledBack;
    }

    public Policy setWeight(String policyId, int weight, String operator) {
        AtomicReference<Policy> ref = policies.get(policyId);
        if (ref == null) return null;
        Policy cur = ref.get();
        int w = clamp(weight);
        Status nextStatus = w == 0
                ? (cur.status() == Status.READY_FOR_GRADUATION ? Status.READY_FOR_GRADUATION : Status.PAUSED)
                : (cur.status() == Status.READY_FOR_GRADUATION ? Status.READY_FOR_GRADUATION : Status.ACTIVE);
        Policy next = cur.toBuilder().canaryWeightPercent(w)
                .status(nextStatus)
                .updatedAt(Instant.now()).updatedBy(operator).build();
        ref.set(next);
        history.addFirst(new History(Instant.now(), policyId, operator,
                "WEIGHT_CHANGE", "newWeight=" + w + "%"));
        trimHistory();
        return next;
    }

    public List<History> history(String policyId, int limit) {
        List<History> out = new ArrayList<>(Math.min(limit, history.size()));
        int n = 0;
        for (History h : history) {
            if (policyId == null || policyId.equals(h.policyId())) {
                out.add(h);
                if (++n >= limit) break;
            }
        }
        return out;
    }

    // ========== 新增：pause / resume / graduate / delete ==========

    /** 暂停：仅改状态 PAUSED，权重保持不变（保留发布意图）；用于临时冻结再观察。 */
    public Policy pause(String policyId, String operator) {
        AtomicReference<Policy> ref = policies.get(policyId);
        if (ref == null) return null;
        Policy cur = ref.get();
        if (cur.status() == Status.PAUSED) return cur;
        Policy next = cur.toBuilder().status(Status.PAUSED).updatedAt(Instant.now()).updatedBy(operator).build();
        ref.set(next);
        history.addFirst(new History(Instant.now(), policyId, operator, "PAUSE",
                "status=" + cur.status() + " -> PAUSED"));
        trimHistory();
        return next;
    }

    /** 恢复：PAUSED / ROLLED_BACK → ACTIVE（ROLLED_BACK 恢复权重默认置为 0，等价"重新开启但不放量"，
     *  实际需要时再 setWeight 逐步调整；避免恢复后立刻进入旧权重冲击线上）。 */
    public Policy resume(String policyId, String operator) {
        AtomicReference<Policy> ref = policies.get(policyId);
        if (ref == null) return null;
        Policy cur = ref.get();
        int w = cur.status() == Status.ROLLED_BACK ? 0 : cur.canaryWeightPercent();
        Status target = Status.ACTIVE;
        Policy next = cur.toBuilder().status(target).canaryWeightPercent(w)
                .updatedAt(Instant.now()).updatedBy(operator).build();
        ref.set(next);
        history.addFirst(new History(Instant.now(), policyId, operator, "RESUME",
                "status=" + cur.status() + " -> ACTIVE; weight=" + cur.canaryWeightPercent() + "% -> " + w + "%"));
        trimHistory();
        return next;
    }

    /** 毕业：新版本已经成为 baseline，标记 GRADUATED 并清权重（不再参与分流决策；保留审计查询）。 */
    public Policy graduate(String policyId, String operator, String reason) {
        AtomicReference<Policy> ref = policies.get(policyId);
        if (ref == null) return null;
        Policy cur = ref.get();
        Policy next = cur.toBuilder().canaryWeightPercent(0).status(Status.GRADUATED)
                .updatedAt(Instant.now()).updatedBy(operator).build();
        ref.set(next);
        history.addFirst(new History(Instant.now(), policyId, operator, "GRADUATE",
                reason == null ? "promoted to baseline" : reason));
        trimHistory();
        return next;
    }

    /** Phase N 辅助：把策略从 ACTIVE / READY_FOR_GRADUATION 状态切到 READY_FOR_GRADUATION 并保留 weight 不变。 */
    public Policy markReadyForGraduation(String policyId, String operator, String reason) {
        AtomicReference<Policy> ref = policies.get(policyId);
        if (ref == null) return null;
        Policy cur = ref.get();
        if (cur.status() != Status.ACTIVE && cur.status() != Status.READY_FOR_GRADUATION) return cur;
        Policy next = cur.toBuilder().status(Status.READY_FOR_GRADUATION)
                .updatedAt(Instant.now()).updatedBy(operator).build();
        ref.set(next);
        history.addFirst(new History(Instant.now(), policyId, operator, "READY_FOR_GRADUATION",
                reason == null ? "100% SLO hold passed" : reason));
        trimHistory();
        return next;
    }

    /** 物理删除（仅限 GRADUATED / ROLLED_BACK 的策略；ACTIVE/PAUSED 策略拒绝删除以避免误操作）。 */
    public boolean delete(String policyId, String operator) {
        AtomicReference<Policy> ref = policies.get(policyId);
        if (ref == null) return false;
        Policy cur = ref.get();
        if (cur.status() != Status.GRADUATED && cur.status() != Status.ROLLED_BACK) return false;
        policies.remove(policyId);
        stats.remove(policyId);
        history.addFirst(new History(Instant.now(), policyId, operator, "DELETE",
                "removed policy with status=" + cur.status()));
        trimHistory();
        return true;
    }

    /** Phase N：Coordinator/Service 侧额外写一条纯审计 History（不修改 policy；用于 append SLO 流水）。 */
    public void appendExternalHistory(History h) {
        if (h == null) return;
        history.addFirst(h);
        trimHistory();
    }

    public Stats statsFor(String policyId) {
        return stats.computeIfAbsent(policyId, k -> new Stats());
    }

    // ---------- internals ----------
    private static int clamp(int w) { return Math.max(0, Math.min(100, w)); }

    private static String describeDiff(Policy prev, Policy next) {
        if (prev == null) return "new policy for route=" + next.routeId()
                + " weight=" + next.canaryWeightPercent() + "%";
        return "weight " + prev.canaryWeightPercent() + "% -> " + next.canaryWeightPercent() + "%; "
                + "status " + prev.status() + " -> " + next.status();
    }

    private void trimHistory() {
        // keep latest 500 entries
        while (history.size() > 500) history.pollLast();
    }
}
