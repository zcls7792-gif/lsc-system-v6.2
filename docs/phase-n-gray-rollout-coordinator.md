# 灰度发布自治理 Coordinator — 运维手册（Phase N）

> 适用范围：`lsc-gateway` 内置的 `GrayRolloutCoordinator`（灰度自治理执行引擎）。
> 该组件按固定 tick 扫描 ACTIVE 灰度策略，基于 SLO 硬门限自动步进权重（1→5→20→50→100），
> 连续突破门限则硬回滚；到达 100% 后置为 `READY_FOR_GRADUATION`（毕业仍走人工/审批）。

---

## 1. 组件概览

| 项 | 说明 |
|---|---|
| 所在服务 | `lsc-gateway` |
| 调度周期 | `gray.rollout.tickMs`（默认 15000 ms） |
| Leader 选举 | 优先 Redisson 公平锁；不可用时降级 JVM `AtomicBoolean` CAS（仅单实例安全） |
| 全局开关 | `gray.rollout.enabled`（默认 `true`；`false` 时 Coordinator Bean 不注册，仅保留只读接口） |
| 权重步进序列 | `gray.rollout.steps`（默认 `1,5,20,50,100`，末步必须为 100） |
| 每步最短保持 | `gray.rollout.minMinutesAtStep`（默认 5 分钟） |
| 毕业动作 | `READY_FOR_GRADUATION` 状态由人工/审批服务调用 `graduate` 接口推进到 `GRADUATED` |

### 1.1 状态机

```
DRAFT ──activate──▶ ACTIVE ──SLO 连续达标 + 保持时间到──▶ (步进 1→5→20→50→100) ──▶ READY_FOR_GRADUATION ──人工 graduate──▶ GRADUATED
                         │
                         └──SLO 连续失败达 maxConsecutiveFailures──▶ ROLLED_BACK（权重清零）
                         │
                         └──人工 pause──▶ PAUSED ──resume──▶ ACTIVE
```

---

## 2. SLO 硬门限

每个 tick 对每个 ACTIVE 策略计算以下三门，**全部通过**才允许步进；**任一门失败**累计 `consecutiveFailures`：

| 门 | 配置项 | 默认值 | 判定规则 |
|---|---|---|---|
| 错误率漂移 | `gray.rollout.maxErrorDriftPct` | 0.5 | `canaryErrPct - baselineErrPct ≤ maxErrorDriftPct`（百分点） |
| P95 倍率 | `gray.rollout.maxP95Ratio` | 1.3 | `canaryP95Ms / baselineP95Ms ≤ maxP95Ratio`（等于视为通过） |
| 最小样本量 | `gray.rollout.minSamplesThreshold` | 500 | canary 5 分钟样本数 ≥ 阈值；不足 → `insufficientSamples`，不步进不回滚 |

- **连续失败阈值**：`gray.rollout.maxConsecutiveFailuresBeforeRollback`（默认 2）。达到即触发硬回滚。
- **数据不可用**：stats 聚合超时/缺失 → `dataUnavailable`，不推进不回滚（避免误判）。
- **策略级覆盖**：`Policy.rolloutConfig` 可逐条覆盖上述任意参数（`null` 继承全局）。

---

## 3. 管理接口

Base path：`/api/gateway/gray`（需带网关鉴权头；`advance-step` 需 `X-Admin-User` 头）。

### 3.1 全局状态

```
GET /api/gateway/gray/rollout/status
```

响应示例：
```json
{
  "coordinatorEnabled": true,
  "tickMs": 15000,
  "nodeId": "gw-7f9c8",
  "leader": true,
  "leaderNodeId": "gw-7f9c8",
  "lastTickSecondsAgo": 8,
  "policiesRunning": 3,
  "policiesReadyForGraduation": 1,
  "policiesApproachingRollback": 0,
  "stepsDefault": [1, 5, 20, 50, 100],
  "minMinutesAtStepDefault": 5,
  "maxErrorDriftPctDefault": 0.5,
  "maxP95RatioDefault": 1.3,
  "minSamplesDefault": 500,
  "maxConsecutiveFailuresDefault": 2
}
```

### 3.2 单策略 rollout 详情

```
GET /api/gateway/gray/policies/{policyId}/rollout
```

返回策略当前权重、所在步进索引、runtime snapshot（enteredStepAt / consecutiveFail / consecutivePass）、SLO 各门明细（canary/baseline 错误率、P95、样本量）、生效配置。

### 3.3 rollout 操作流水

```
GET /api/gateway/gray/policies/{policyId}/rollout/history?limit=50
```

仅返回 `system:rollout` / `system:rollback` 触发的自动操作（`STEP_ADVANCE` / `ROLLBACK` / `READY_FOR_GRADUATION`）。

### 3.4 手动推进一步（强制）

```
POST /api/gateway/gray/policies/{policyId}/rollout/advance-step?reason=加快回归验证
Header: X-Admin-User: oncall-name
```

- 无视当前 hold 时间与 SLO，直接推进到 steps 序列的下一步。
- 若已在最后一步（weight=100），则标记 `READY_FOR_GRADUATION`。
- 操作写入审计历史，operator 取自 `X-Admin-User` 头。

---

## 4. Prometheus 指标

所有指标前缀 `lsc_gray_rollout_`，通过 `/actuator/prometheus` 暴露。

| 指标 | 类型 | 标签 | 说明 |
|---|---|---|---|
| `lsc_gray_rollout_tick_total` | Counter | `action`, `nodeId` | 每次 tick；action ∈ {scan, skip_no_quorum, error, error_per_policy, scan_handled_N} |
| `lsc_gray_rollout_last_tick_epoch_sec` | Gauge | — | 最近一次 tick 的 Unix 秒（leader 才更新） |
| `lsc_gray_rollout_leader` | Gauge | `nodeId` | 1=本实例是 leader，0=否 |
| `lsc_gray_rollout_leader_node_id` | Gauge | `nodeId` | leader 实例标识 |
| `lsc_gray_rollout_slo_result` | Gauge | `policyId`, `gate`, `nodeId` | 1=该门通过，0=失败；gate ∈ {error_drift_pct, p95_ratio, min_samples, slo_unavailable} |
| `lsc_gray_rollout_step_info` | Gauge | `policyId`, `stepIndex`, `weightPct`, `nodeId` | 1=策略当前位于该步 |
| `lsc_gray_rollout_event_total` | Counter | `event`, `policyId` | event ∈ {STEP_ADVANCE, ROLLBACK_TRIGGERED, READY_FOR_GRAD, MANUAL_ADVANCE} |
| `lsc_gray_rollout_consecutive_failures` | Gauge | `policyId` | 当前连续 SLO 失败次数（PASS/回滚/手动推进归零） |
| `lsc_gray_rollout_max_failures_threshold` | Gauge | `policyId` | 触发硬回滚的连续失败上限 |

---

## 5. 告警规则（PrometheusRule）

Helm Chart `helm/lsc-gateway/templates/prometheus-rules-rollout.yaml` 定义 5 条规则：

| 告警 | 级别 | 表达式要点 | 处置 |
|---|---|---|---|
| `GrayRollbackTriggered_P0` | P0 | `increase(event_total{event="ROLLBACK_TRIGGERED"}[10m]) > 0` | 立即排查 canary 版本与业务影响 |
| `GrayRolloutCoordinatorDead_P0` | P0 | `time() - last_tick_epoch_sec > 180` 且存在 leader | 检查 leader POD 日志 / Redisson 锁 |
| `GraySloFailingHigh_P1` | P1 | 某 SLO 门连续 FAIL 5 min | 提前介入：查 canary 日志或主动回滚 |
| `GrayConsecutiveFailsNearThreshold_P2` | P2 | `consecutive_failures >= max_failures_threshold - 1` | 关注，距硬回滚只差 1 tick |
| `GrayRolloutLeaderElectionInstable_P2` | P2 | 20 min 内 leader 切换 ≥ 3 次 | 检查 `leaderLeaseMs`（建议 > 2×tickMs） |

---

## 6. 运维 Runbook

### 6.1 策略 SLO 持续 FAIL，即将硬回滚

1. 查详情：`GET /api/gateway/gray/policies/{id}/rollout`，看 `slo.gates` 哪门失败、`snapshot.consecutiveFail` 计数。
2. 若 canary 版本确有问题：主动回滚
   ```
   POST /api/gateway/gray/policies/{id}/rollback
   ```
3. 若需更多观察时间（不想自动回滚）：先暂停自动步进
   ```
   POST /api/gateway/gray/policies/{id}/pause
   ```
   修复后 `resume`，或手动推进 `advance-step`。

### 6.2 连续硬回滚怎么办

1. 回滚后策略状态为 `ROLLED_BACK`、权重 0，Coordinator 不再处理该策略。
2. 排查 canary 版本根因（错误率 / P95）。
3. 修复后重新激活：将策略改回 `ACTIVE` 并重置权重到 1（通过 upsert 策略接口）。
4. 观察 `GrayConsecutiveFailsNearThreshold_P2` 是否再次接近阈值。

### 6.3 Coordinator 超过 3 分钟未 tick

1. 查 `rollout/status` 的 `lastTickSecondsAgo` 与 `leader` / `leaderNodeId`。
2. 若 leader POD 异常：
   ```
   kubectl -n <ns> logs -l app.kubernetes.io/name=lsc-gateway --tail=500 | grep gray-rollout
   ```
3. 可能原因：Redisson 锁漂移 / Scheduler 线程阻塞 / 实例 OOM。
4. 临时缓解：重启 leader POD 触发重新选举。

### 6.4 Leader 频繁切换

1. 检查 `gray.rollout.leaderLeaseMs`（默认 55000 ms），应 > 2 × `tickMs`。
2. 检查 Redisson 连接稳定性与网络延迟。
3. 若 Redisson 不可用，确认 `allowJvmLeaderFallback=true`（JVM CAS 仅单实例安全，多实例需修 Redisson）。

### 6.5 手动推进步进

```bash
curl -X POST "http://<gateway>/api/gateway/gray/policies/order-svc/rollout/advance-step?reason=加快回归" \
  -H "X-Admin-User: oncall-zhang"
```

---

## 7. 配置项速查

| 配置项 | 环境变量 | 默认 | 说明 |
|---|---|---|---|
| `gray.rollout.enabled` | `GRAY_ROLLOUT_ENABLED` | `true` | 全局开关 |
| `gray.rollout.tickMs` | `GRAY_ROLLOUT_TICK_MS` | `15000` | tick 间隔 ms |
| `gray.rollout.steps` | `GRAY_ROLLOUT_STEPS` | `1,5,20,50,100` | 权重步进序列 |
| `gray.rollout.minMinutesAtStep` | `GRAY_ROLLOUT_MIN_STEP_MIN` | `5` | 每步最短保持分钟 |
| `gray.rollout.maxErrorDriftPct` | `GRAY_ROLLOUT_MAX_ERROR_DRIFT_PCT` | `0.5` | 错误率漂移门限（百分点） |
| `gray.rollout.maxP95Ratio` | `GRAY_ROLLOUT_MAX_P95_RATIO` | `1.3` | P95 倍率门限 |
| `gray.rollout.minSamplesThreshold` | `GRAY_ROLLOUT_MIN_SAMPLES` | `500` | canary 最小样本数 |
| `gray.rollout.maxConsecutiveFailuresBeforeRollback` | `GRAY_ROLLOUT_MAX_CONSEC_FAIL` | `2` | 连续失败回滚阈值 |
| `gray.rollout.leaderLeaseMs` | — | `55000` | Redisson leader 租约 ms |
| `gray.rollout.allowJvmLeaderFallback` | — | `true` | Redisson 不可用时是否允许 JVM CAS |
| `gray.rollout.nodeId` | `POD_NAME` / `HOSTNAME` | `gw-unknown` | 实例标识（指标/日志用） |

---

## 8. 关键类与源码索引

| 类 | 路径 | 职责 |
|---|---|---|
| `GrayRolloutCoordinator` | `lsc-gateway/.../gray/rollout/GrayRolloutCoordinator.java` | 调度器 + Leader 选举 + 步进/回滚决策 |
| `SloGuard` | `lsc-gateway/.../gray/rollout/SloGuard.java` | SLO 三门纯函数判定 |
| `RolloutMetrics` | `lsc-gateway/.../gray/rollout/RolloutMetrics.java` | Micrometer 指标封装 |
| `GrayRolloutProperties` | `lsc-gateway/.../gray/rollout/GrayRolloutProperties.java` | `gray.rollout.*` 配置绑定 |
| `GrayPolicyService` | `lsc-gateway/.../gray/GrayPolicyService.java` | `advanceWeightTo` / `markReadyForGraduation` / `rollback` |
| `GrayReleaseController` | `lsc-gateway/.../gray/GrayReleaseController.java` | 4 个 rollout 只读 + 手动推进端点 |
| `GrayGatewayClient` | `lsc-release-service/.../feign/GrayGatewayClient.java` | 发布服务侧 Feign 观察 rollout 进度 |

单测：`lsc-gateway/src/test/.../gray/GrayRolloutPhaseNTest.java`（9 用例覆盖步进/回滚/保持/手动推进/策略级覆盖）。
