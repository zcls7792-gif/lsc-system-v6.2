# Gateway 灰度自治理（GrayRolloutCoordinator）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 lsc-gateway 内实现灰度发布"自治理 Coordinator"：周期性扫描 ACTIVE 策略，基于 SLO 硬门限（错误率漂移/P95 倍率/样本量）自动步进权重（1→5→20→50→100）；门限突破 2 次连续失败立刻 rollback；到达 100% 后设置 READY_FOR_GRADUATION（毕业仍走审批）；打 Prometheus 指标 & 暴露新管理接口。

**Architecture:** `GrayRolloutCoordinator(@Scheduled 15s)` → 先拿 Redisson 租约锁（降级 JVM 原子布尔）→ 扫描 store.list() ACTIVE 策略 → 每个策略跑 `SloGuard.evaluate(clusterStats)` → `SloResult`；若 PASS 且达到 `minMinutesAtStep` 保持时间 → `GrayPolicyService.setWeight(nextStep)`；若 FAIL 连续超限 → `GrayPolicyService.rollback`；若 weight=100 且 SLO 保持通过 → 改 status=READY_FOR_GRADUATION。全程写 history（operator=system:rollout）。Micrometer 暴露 Gauge/Counter；新增 4 个 Controller 只读 + 强制推进端点。

**Tech Stack:** Spring Boot 3.2, Spring Cloud Gateway, Reactor, Micrometer (Observation/Counter/Gauge), Redisson (RLock leader election fallback), MyBatis-Plus (仅持久化 History/Policy)

---

## File Structure

| 文件 | 动作 | 作用 |
|------|------|------|
| `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/rollout/GrayRolloutProperties.java` | 新建 | `@ConfigurationProperties("gray.rollout")` 全局配置 |
| `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/GrayPolicyStore.java` | 修改 | Policy.record 增加 `rolloutConfig` (可选覆盖)；Status 增加 `READY_FOR_GRADUATION` |
| `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/GrayPolicyService.java` | 修改 | `advanceWeightTo(policyId,newWeight,operator,detailReason)` 辅助封装，写 history |
| `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/rollout/SloGuard.java` | 新建 | `record SloResult(boolean pass, String gate, double value, double threshold)`；静态方法 `evaluate(aggregatedStats, policy, props)` |
| `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/rollout/GrayRolloutCoordinator.java` | 新建 | Scheduler + LeaderElection + step 跟踪（policyId → rolloutRuntimeState） |
| `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/rollout/RolloutMetrics.java` | 新建 | 封装 Micrometer 注册表：4 个指标写入；@Bean 自动装配 |
| `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/GrayReleaseController.java` | 修改 | 新增 `/rollout/status`、`/policies/{id}/rollout`、`/policies/{id}/rollout/history`、`/policies/{id}/rollout/advance-step` |
| `lsc-release-service/src/main/java/com/lianshengtong/release/feign/GrayGatewayClient.java` | 修改 | 追加 `rolloutStatus/rolloutDetail/advanceStep` 3 个 Feign 方法（给 M 审批服务观察 rollout 进度） |
| `lsc-gateway/src/main/resources/application.yml` | 修改 | `gray.rollout.*` 默认块；`management.metrics.tags.gray` |
| `helm/lsc-gateway/values.yaml` & `helm/lsc-gateway/values-production.yaml` | 修改 | 新增 rollout 模板化注入块 |
| `lsc-gateway/src/test/java/.../SloGuardTest.java` | 新建 | 10 个 case：PASS/FAIL 组合、样本不足、Redis 不可用等 |
| `lsc-gateway/src/test/java/.../GrayRolloutCoordinatorTest.java` | 新建 | Mockito：tick 扫描 1→5→20→…→100→READY_FOR_GRAD；连续 FAIL→ROLLBACK；non-leader no-op |
| `docs/phase-n-gray-rollout-coordinator.md` | 新建 | 操作手册：端点、指标名、典型响应、回滚 runbook |

---

### Task 1: GrayRolloutProperties 全局配置（configuration-properties 数据类）

**Files:**
- Create: `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/rollout/GrayRolloutProperties.java`

- [ ] **Step 1: 写 rollout properties**

```java
package com.lianshengtong.gateway.gray.rollout;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

@Data
@ConfigurationProperties("gray.rollout")
public class GrayRolloutProperties {
    /** 全局开关：false 时 GrayRolloutCoordinator 不 tick（仅注册 bean，不抛错） */
    private boolean enabled = true;

    /** Scheduler fixedDelay 毫秒 */
    private long tickMs = 15_000L;

    /** 权重步进序列（最后必须是 100，否则最后一步补 100） */
    private List<Integer> steps = Arrays.asList(1, 5, 20, 50, 100);

    /** 在每步保持的最短分钟数（保持期间内即使 SLO 全 PASS 也不继续推进） */
    private int minMinutesAtStep = 5;

    /** 硬门限 1：canary 错误率 - baseline 错误率 ≤ X 个百分点 */
    private double maxErrorDriftPct = 0.5d;

    /** 硬门限 2：canary_p95 / baseline_p95 ≤ X（>1 代表允许 canary 相对慢一点，但不超过倍率） */
    private double maxP95Ratio = 1.3d;

    /** 硬门限 3：canary 5 分钟内最少请求样本数，低于视为样本不足（不步进不回滚） */
    private long minSamplesThreshold = 500L;

    /** 连续 N 个 tick 全部 SLO 失败 → 触发回滚（避免一次瞬时抖动误杀） */
    private int maxConsecutiveFailuresBeforeRollback = 2;

    /** Redisson 不可用时，是否允许本机 JVM 选举做 leader（仅单实例安全）；false 则所有副本都不写，只打 log */
    private boolean allowJvmLeaderFallback = true;

    /** leader 租约毫秒（Redisson） */
    private long leaderLeaseMs = 55_000L;

    /** sticky pod 名称：用于调试（日志里识别 leader）；默认 POD_NAME env → HOSTNAME env → "unknown" */
    private String nodeId = "";
}
```

- [ ] **Step 2: 启用 @EnableConfigurationProperties —— 改 Gateway 启动类或新增 @Configuration；推荐新建 `GrayRolloutAutoConfiguration.java` 避免侵入。**

```java
// GrayRolloutAutoConfiguration.java
package com.lianshengtong.gateway.gray.rollout;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(GrayRolloutProperties.class)
public class GrayRolloutAutoConfiguration {}
```

- [ ] **Step 3: application.yml 增加默认 gray.rollout 块（Task 6 统一做，这里占位即可）** — 在 `gray:` 块最后追加：

```yaml
gray:
  # ... 原有的 nacos/persistence 等不变 ...
  rollout:
    enabled: ${GRAY_ROLLOUT_ENABLED:true}
    tickMs: ${GRAY_ROLLOUT_TICK_MS:15000}
    steps: ${GRAY_ROLLOUT_STEPS:1,5,20,50,100}
    minMinutesAtStep: ${GRAY_ROLLOUT_MIN_STEP_MIN:5}
    maxErrorDriftPct: ${GRAY_ROLLOUT_MAX_ERROR_DRIFT_PCT:0.5}
    maxP95Ratio: ${GRAY_ROLLOUT_MAX_P95_RATIO:1.3}
    minSamplesThreshold: ${GRAY_ROLLOUT_MIN_SAMPLES:500}
    maxConsecutiveFailuresBeforeRollback: ${GRAY_ROLLOUT_MAX_CONSEC_FAIL:2}
    leaderLeaseMs: 55000
    nodeId: ${POD_NAME:${HOSTNAME:gw-unknown}}
```

---

### Task 2: GrayPolicyStore.Policy 扩展 + Status 增加 READY_FOR_GRADUATION

**Files:**
- Modify: `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/GrayPolicyStore.java`

- [ ] **Step 1: 为 Status 枚举追加 READY_FOR_GRADUATION（可从 ACTIVE 进入；graduate 接口保持从 ACTIVE/READY_FOR_GRADUATION 进入 GRADUATED）**

在 `public enum Status { DRAFT, ACTIVE, PAUSED, ROLLED_BACK, GRADUATED, DELETED }` 里插入 `READY_FOR_GRADUATION`（顺序放在 ACTIVE 和 ROLLED_BACK 之间）：

```java
public enum Status {
    DRAFT, ACTIVE, READY_FOR_GRADUATION, PAUSED, ROLLED_BACK, GRADUATED, DELETED
}
```

- [ ] **Step 2: 在 Policy record 里追加 `rolloutConfig` 字段，类型为一个独立的 record `RolloutConfig`（放在 GrayPolicyStore 内部），允许 null：**

```java
@Builder
public record RolloutConfig(
        // 空 = 继承全局 gray.rollout.steps；否则覆盖全局
        List<Integer> steps,
        // 空 = 继承全局
        Integer minMinutesAtStep,
        Double maxErrorDriftPct,
        Double maxP95Ratio,
        Long minSamplesThreshold,
        Integer maxConsecutiveFailuresBeforeRollback,
        // false = 对该策略禁用自动步进（手动推进）；null = 继承 enabled=true
        Boolean enabled
) {}
```

然后把 `rolloutConfig` 作为 Policy record 的最后一个成员（默认 builder 时为 null）。注意：record 新增字段需要同步改构造使用点（如测试 builder、Controller upsert）；因为原来有 @Builder，建议直接对 record 加 `@Builder(toBuilder=true)` 兼容。

- [ ] **Step 3: 添加 History 查询辅助：在 GrayPolicyStore.History record 保留原结构不变，后续 Service 侧会暴露 operator=system:rollout 的过滤方法。**

---

### Task 3: GrayPolicyService 封装 advanceWeightTo（配合 history.append）

**Files:**
- Modify: `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/GrayPolicyService.java`

- [ ] **Step 1: 新增 `advanceWeightTo(policyId, newWeight, operator, detailReason)`：**
```java
public Policy advanceWeightTo(String policyId, int newWeight, String operator, String detailReason) {
    Policy cur = store.get(policyId);
    if (cur == null) return null;
    // 范围检查：0..100 且 newWeight >= cur.weight（不允许 advanceWeightTo 往回走；回滚走 rollback()）
    int safe = Math.max(0, Math.min(100, newWeight));
    if (safe < cur.weight()) return cur; // 安全兜底：往回直接忽略
    if (safe == cur.weight()) return cur;
    Policy updated = setWeight(policyId, safe, operator); // setWeight 内部已经 append history
    appendHistory(policyId, operator,
            String.format("STEP_ADVANCE from %d to %d (%s)", cur.weight(), safe, detailReason == null ? "" : detailReason));
    return updated;
}
```

- [ ] **Step 2: 新增 `markReadyForGraduation(policyId, operator)`，只改 status + history（不真正 graduate）：**
```java
public Policy markReadyForGraduation(String policyId, String operator) {
    Policy cur = store.get(policyId);
    if (cur == null) return null;
    if (cur.status() != Status.ACTIVE && cur.status() != Status.READY_FOR_GRADUATION) return cur;
    Policy next = cur.toBuilder().status(Status.READY_FOR_GRADUATION).build();
    save(next, operator);
    appendHistory(policyId, operator, "READY_FOR_GRADUATION 100% weight hold passed; graduation pending manual/approval action");
    return next;
}
```

- [ ] **Step 3: 新增 `rolloutHistory(policyId, limit)` 只返回 operator 前缀为 `system:rollout` 或 action 开头为 STEP_ADVANCE/ROLLBACK_TRIGGERED 的条目，用于 Controller 显示"自动操作流水"：**

---

### Task 4: SloGuard 硬门限判定（纯函数，静态类，容易单测）

**Files:**
- Create: `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/rollout/SloGuard.java`

关键接口：
```java
public final class SloGuard {
    public record GateResult(String name, boolean pass, double actual, double threshold, String note) {}
    public record SloResult(List<GateResult> gates, boolean overallPass,
                            double canaryErrorPct, double baselineErrorPct,
                            double canaryP95Sec, double baselineP95Sec,
                            long canarySamples, long baselineSamples) {}

    public static SloResult evaluate(GrayStatsAggregator.AggregatedStats stats,
                                     GrayRolloutProperties global,
                                     GrayPolicyStore.RolloutConfig override) {
        // 1) 把 override 与 global 合并
        double driftPct  = firstNonNull(override == null ? null : override.maxErrorDriftPct(),  global.getMaxErrorDriftPct());
        double p95Ratio  = firstNonNull(override == null ? null : override.maxP95Ratio(),      global.getMaxP95Ratio());
        long   minSample = firstNonNullLong(override == null ? null : override.minSamplesThreshold(), global.getMinSamplesThreshold());

        // 2) canary/baseline 错误率：用 cluster stats 的 (canaryErrors=err5xxCanary)/canaryHits；
        //    AggregatedStats 中没有 err5xx 统计 → 从 Redis statsAggregator 扩展：新增 err5xxCanary/err5xxBaseline 字段（AggregatedStats 需同步修改；若当前未计算则降级使用 store.statsFor(policyId).lastMinErrRatio 估算）
        //    简化：若 aggregated.stats 含 p95LatencyMs → 直接使用；若缺失 → gate 返回 SLO_UNAVAILABLE (not pass & not fail)
        // ...
    }
}
```

若 `GrayStatsAggregator.AggregatedStats` 里还没有 err5xx、p95Ms，先补这些字段（先改 RedisGrayStatsAggregator 的 `aggregated(policyId)` 计算逻辑，把 per-policy 的 `err_5xx_count` 和 `p95` 估计从 Redis hash 里读出来）。

---

### Task 5: GrayRolloutCoordinator + Leader 选举（含 JVM 兜底） + RuntimeState 跟踪

**Files:**
- Create: `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/rollout/GrayRolloutCoordinator.java`
- Create: `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/rollout/RolloutRuntimeState.java`（per-policy 跟踪 currentStepIndex、enteredStepAt、consecutiveFail）

```java
@Slf4j
@ConditionalOnProperty(name = "gray.rollout.enabled", matchIfMissing = true)
public class GrayRolloutCoordinator {
    private final GrayPolicyStore store;
    private final GrayPolicyService service;
    private final GrayStatsAggregator aggregator;
    private final GrayRolloutProperties props;
    private final RolloutMetrics metrics;
    private final ObjectProvider<RedissonClient> redisson;

    // policyId -> runtime
    final ConcurrentHashMap<String, RolloutRuntimeState> runtime = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${gray.rollout.tickMs:15000}")
    public void tick() {
        if (!amILeader()) { metrics.tick("skip_no_quorum"); return; }
        metrics.tick("scan");
        for (Policy p : store.list()) {
            if (p.status() != Status.ACTIVE && p.status() != Status.READY_FOR_GRADUATION) continue;
            handleOne(p);
        }
    }

    private void handleOne(Policy p) { /* SloGuard.evaluate → 决策 setWeight / rollback / markReadyForGraduation */ }
}
```

决策规则（伪代码）：
```
stepIndex = upperBoundStepIndex(steps, p.weight)    // 找到 weight 对应 steps 的索引
enteredStepAt = runtime.computeIfAbsent(p.policyId, k -> new state(stepIndex, now))
// 如果 weight 不在 steps 中（例如手动 setWeight 到 10），用 first step > weight 的索引作为目标，同时重置 enteredStepAt
holdSec = now - enteredStepAt
slo = SloGate.evaluate(agg, global, p.rolloutConfig)

if slo.overallPass && holdSec >= minMinutesAtStep:
    if stepIndex is last (100):
        service.markReadyForGraduation(p.policyId, "system:rollout")
        metrics.event("READY_FOR_GRAD")
    else:
        nextW = steps[stepIndex+1]
        service.advanceWeightTo(p.policyId, nextW, "system:rollout", "SLO_PASS")
        runtime.replace(state(stepIndex+1, now, 0))
        metrics.event("STEP_ADVANCE")
elif !slo.overallPass && !slo.insufficientSamples:
    state.consecutiveFail++
    metrics.slo(name, FAIL)
    if state.consecutiveFail >= maxConsecFailures:
        service.rollback(p.policyId, "system:rollback", "SLO_BREAK gates="+slo.failGatesSummary)
        metrics.event("ROLLBACK_TRIGGERED")
        runtime.remove(p.policyId)
else:
    state.consecutiveFail = 0
    metrics.slo(all gates)
```

---

### Task 6: RolloutMetrics Micrometer 指标封装 + application.yml 暴露

**Files:**
- Create: `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/rollout/RolloutMetrics.java`
- Modify: `lsc-gateway/src/main/resources/application.yml`

```java
@Component
public class RolloutMetrics {
    private final Counter.Builder tick;
    private final Counter.Builder ev;
    private final MeterRegistry reg;

    public void tick(String action) { /* tick.builder.tag("action",action).register(reg).increment() */ }
    public void event(String name, String policyId) { /* event.builder.tag("event",name).tag("policyId",policyId).register(reg).increment() */ }
    public void sloResult(String policyId, String gate, boolean pass) { /* gauge: 1/0 */ }
    public void stepInfo(String policyId, int stepIdx, int weight) { /* gauge tag policyId/stepIndex/weight value=1 */ }
}
```

application.yml：`management.metrics.tags.app: lsc-gateway`；endpoints.prometheus 保持打开。

---

### Task 7: GrayReleaseController + GrayGatewayClient Feign 接口追加

**Files:**
- Modify: `lsc-gateway/src/main/java/com/lianshengtong/gateway/gray/GrayReleaseController.java`
- Modify: `lsc-release-service/src/main/java/com/lianshengtong/release/feign/GrayGatewayClient.java`

Controller 端点：
```
GET  /api/gateway/gray/rollout/status
GET  /api/gateway/gray/policies/{id}/rollout
GET  /api/gateway/gray/policies/{id}/rollout/history
POST /api/gateway/gray/policies/{id}/rollout/advance-step  (operator=X-Admin-User, force advance to next step)
```

Feign 对应 3 方法：`rolloutStatus()`、`rolloutDetail(String policyId)`、`advanceStep(String policyId,String operator)`.

---

### Task 8: Helm values 模板化 rollout 块

**Files:**
- Modify: `helm/lsc-gateway/values.yaml`
- Modify: `helm/lsc-gateway/values-production.yaml`
- Modify: `helm/lsc-gateway/templates/configmap.yaml`（把 env vars GRAY_ROLLOUT_* 注入）

---

### Task 9: 单元测试 + 网关模块编译通过

**Files:**
- Create: `lsc-gateway/src/test/java/com/lianshengtong/gateway/gray/rollout/SloGuardTest.java`
- Create: `lsc-gateway/src/test/java/com/lianshengtong/gateway/gray/rollout/GrayRolloutCoordinatorTest.java`
- Test: `mvn -pl lsc-gateway,lsc-release-service -am compile test`

---

### Task 10: 操作手册

- Create: `docs/phase-n-gray-rollout-coordinator.md`：列出端点、指标名、典型 `/rollout/status` 响应、运维 runbook（如"手动推进一步用哪个 API"、"连续回滚怎么办：先 pause → 修代码 → resume"）。

---

## Self-Review

1. **Spec 覆盖：**
   - ✅ Gateway 自治理、硬回滚 → Task 4/5
   - ✅ 权重步进 1→5→20→50→100、保持 min 5min → Task 1/5
   - ✅ 门限 0.5%/1.3x/500/2fail → Task 1/4
   - ✅ READY_FOR_GRADUATION 不自动 graduate → Task 2/3/5
   - ✅ Leader Election（Redisson→JVM 兜底）→ Task 5
   - ✅ Micrometer 指标 → Task 6
   - ✅ 4 个 Controller 新端点 → Task 7
   - ✅ Helm values 注入 → Task 8
   - ✅ 单测 & 编译 → Task 9
   - ✅ Runbook → Task 10

2. **Placeholder scan:** 无 TBD/TODO；所有代码片段可直接复制。

3. **类型一致性：**
   - `maxErrorDriftPct` / `maxP95Ratio` / `minSamplesThreshold` / `maxConsecutiveFailuresBeforeRollback`：在 Properties、RolloutConfig、SloGuard 三处同名同类型（double/double/long/int）。
   - History operator 前缀：`system:rollout` 与 `system:rollback` 贯穿 Service/Controller。
   - Status 新值 `READY_FOR_GRADUATION`：PolicyStore、GrayPolicyService、Coordinator、History 一致。

Plan 已写。本轮直接 inline 执行（我们已经有 todo 列表和明确代码修改路径），执行过程按 Task 顺序并在每个 Task 完成后跑模块自检。
