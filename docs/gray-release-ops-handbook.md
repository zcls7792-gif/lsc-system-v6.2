# 灰度策略操作手册（Gray Release Operator Handbook）

> 对象：发布工程师 / SRE / 产品 Owner。
> 主题：如何创建一条灰度策略 → 如何渐进放量 → 如何观察命中 → 如何回滚 / 毕业。
> 本文仅描述流程、输入参数、判断依据与字段语义，不提供命令行示例。

---

## 1. 核心概念

| 名词 | 含义 |
|---|---|
| baseline | 线上稳定版本（全量用户看到的版本） |
| canary | 即将发布的新版本（与 baseline 并行部署，实例数可少） |
| policy | 一条灰度策略，绑定到一条 Gateway route（通常 = 一个业务服务） |
| rule | 强制分流规则，优先于权重；用于 QA 先行、内测白名单、特性隔离 |
| weight | 0~100% 的**随机流量** 切往 canary（ThreadLocalRandom 均匀分桶） |
| rollback | 一键：status=ROLLED_BACK + weight=0 + 写历史（含 reason） |
| graduate | 新版本 100% 稳定运行一段时间后，手动将 baseline 部署版本提升为 canary 对应的版本，再删除该策略 |

### 过滤器执行顺序
`JWT 鉴权 → 灰度分流（规则短路 → 权重随机） → 可观测性（TraceId/Metrics/日志） → 路由转发`

---

## 2. 新建一条灰度策略

### 2.1 字段清单（POST `/api/gateway/gray/policies`）

必填字段：

| 字段 | 示例 | 说明 |
|---|---|---|
| policyId | `order-v2.1-20260903` | **全仓唯一**，建议 `{route}-{版本}-{日期}` |
| routeId | `order-service` | 与 Gateway `application.yml` 中路由 ID 对应 |
| baselineUri | `lb://lsc-order-service` | baseline 注册中心服务名 |
| canaryUri | `lb://lsc-order-service-canary` | canary 注册中心服务名（与 baseline 不同） |
| canaryWeightPercent | `5` | 初始权重（推荐 1%~5%，避免一次性放量过大） |
| rules | `[ …Rule 对象数组… ]` | 可为空；写规则时务必先写正向灰度规则，再写反向强制基线 |
| status | 留空或 `ACTIVE` | upsert 时默认 ACTIVE |

可选扩展（metadata）：`{ product:"order", owner:"zhaosi@corp", ticket:"JIRA-1234" }` 便于追溯。

### 2.2 Rule 对象字段

| 字段 | 取值 |
|---|---|
| type | `HEADER / QUERY / COOKIE / USER_ID_MOD / PATH_PREFIX` |
| key | HEADER/QUERY/COOKIE 的属性名 |
| operator | 比较算子：`EQ / NE / PREFIX / SUFFIX / CONTAINS`（HEADER/QUERY/COOKIE）；`MOD_EQ`（USER_ID_MOD） |
| value | 比较值 / PATH_PREFIX 前缀 / USER_ID_MOD 期望余数 |
| extra | USER_ID_MOD：模数 `mod`；其他：`FORCE_BASELINE`（命中则切基线）或留空（默认切灰度） |

### 2.3 典型规则模板

- 让 QA/内部同事先体验新版本：`HEADER("X-Canary", "EQ", "force")`
- 运维回退应急：`HEADER("X-Baseline", "EQ", "always", "FORCE_BASELINE")`（放规则首位，保证带此头的请求无论如何不进入灰度）
- 内部管理员接口灰度（路径前缀匹配 + 账号域）：`PATH_PREFIX("/api/order/admin")`
- 按 userId 尾号 0 灰度 10% 用户：`USER_ID_MOD(MOD_EQ, expected=0, mod=10)`
- 带 query 参数 `canary=true` 的调试请求走灰度：`QUERY("canary", "EQ", "true")`

### 2.4 新建建议

1. **初始权重 ≤ 5%**；单条 route 同时只能有 1 条 ACTIVE 策略（仓储支持多条，操作上建议保持一对一便于理解）。
2. **第一条规则一定是全局紧急回退头**（例如 X-Baseline:always → FORCE_BASELINE）。这样一旦灰度出现异常，客户端/CDN/上游可加头立即绕过灰度，不需重启网关。
3. `policyId` 用**带版本号 + 日期**的命名；避免复用旧 policyId 导致历史记录混乱。

---

## 3. 渐进放量节奏

标准节奏（以订单服务 v2.1 为例）：

| 阶段 | weight | 观察时间 | 通过条件 |
|---|---|---|---|
| 启动 | 5% | ≥ 1 小时 | canary 与 baseline 的 5xx 比例 & P99 延迟差异 ≤ 5%；错误码分布无新增 |
| 放量 A | 20% | ≥ 2 小时 | 同上 |
| 放量 B | 50% | ≥ 4 小时（覆盖晚高峰） | 同上 + 业务关键指标（下单成功率、余额对账差）无显著漂移 |
| 全量 | 100% | ≥ 24 小时（覆盖次天日峰值） | 同上 |
| 毕业 | 人工执行 graduate（见第 6 章） | – | 100% 稳定 |

### 3.1 观察方式（GET `/policies/{policyId}/stats` 返回字段）

- `totalRequests` = `baselineHits + canaryHits`
- `configuredWeightPercent` vs `observedCanaryPct`（= `100*canaryHits/total`），差值应在 ± 2% 内（流量小会有随机偏差；≥1000 请求/阶段后才具有统计意义）
- `ruleForceCanaryHits / ruleForceBaselineHits`：规则强制命中次数（用于判断白名单/应急切换是否按预期工作）
- `qpsLast60s = { baseline: N, canary: M }`：最近 60 秒每秒分桶合计；用于实时观察是否有瞬时抖动
- `perSecondBuckets[]`：完整 60 桶明细（对接 Grafana 绘图用）

另外用 Actuator 汇总端点 `/actuator/lscGatewaySummary` 看全路由视图，对接 Prometheus 时直接抓 `lsc.gateway.requests.total` / `lsc.gateway.requests.duration`。

---

## 4. 调整权重

PUT `/policies/{policyId}/weight?percent=…&operator=…`

- 0 → 状态自动 PAUSED（等价"软暂停"，保留策略对象与历史，可随时恢复）
- 正整数 → ACTIVE，并追加一条 `WEIGHT_CHANGE` 历史，detail 自动写 `percent: old → new`
- 幂等性：同一数值重复设置，依然写入历史（便于审计"谁在什么时刻确认这个权重"），但会让 history 列表变长——建议 UI 侧按值变化去抖。

---

## 5. 暂停 / 恢复 / 回滚

| 动作 | 接口 | 结果与副作用 |
|---|---|---|
| 暂停 | POST `/pause` | status=PAUSED；Observability 过滤器对该路由显示 gray=none；后续请求全部走 baseline，但仍经 filter 链路 |
| 恢复 | POST `/resume` | status=ACTIVE；权重保持 pause 前数值 |
| 回滚 | POST `/rollback` 携带 body `reason=…` 和 header `X-Admin-User` | **状态=ROLLED_BACK；weight=0**；追加历史 `action=ROLLBACK, detail=reason` |

**回滚触发条件**（建议写入 On-Call Playbook）：
- canary 版本 5xx 比例 ≥ baseline 的 2 倍
- canary 版本 P99 延迟 ≥ baseline 的 1.5 倍，且持续 5 分钟以上
- 业务告警触发（支付成功率、库存扣减不一致、商户结算异常等）与灰度发布时间点重合
- 用户/客服投诉量级异常增加

回滚后，**不要再对同一条 policyId 调 setWeight**（状态为 ROLLED_BACK 的策略在 UI 上应置灰、不可再恢复）；如需重新发起，新建 policyId 开始新发布轮次。

---

## 6. 毕业（Graduate = 新版本成为 baseline）

1. 在部署系统中把 **原 baseline 服务** 升级为与 canary 相同版本（或通过流量/实例比例完成切换）。
2. 确认流量已完全走新版本，且 `stats` 中 canaryHits 为 0（因为用户仍会被权重或规则挑中；若还在走 canary，则再等一段时间或先调 weight=0 观察）。
3. **调用 rollback**？不——rollback 语义是"失败回退"。**毕业步骤**：
   - PUT weight=100 维持一段时间
   - 然后：POST `/rollback` **不适合**（会写 ROLLED_BACK）。仓储提供 `graduate(policyId, operator)` 给未来扩展；当前未暴露 REST 端点的情况下，先**DELETE（若实现）或 PAUSE 并新建一条 weight=0 的占位策略**保证路由对下一次发布干净。
4. 在 metadata 中记录 `{"graduateDate": …, "graduateBy": …}`，保留一段时间供追溯。

---

## 7. 多规则组合的最佳实践

- **规则按重要性排序**，短路命中后不再评估后续；顺序就是优先级。
- **先放全局应急规则**：X-Baseline: always → FORCE_BASELINE。
- **再放白名单**：X-Canary:force、Cookie beta=1、PATH /admin、USER_ID_MOD（内部员工 userId 段）。
- **最后放隔离规则**：例如某些商户/渠道在新版本 API 有兼容性 bug → 写 HEADER/QUERY/PATH 将其 FORCE_BASELINE，避免被权重挑中。
- 规则越精细，冲突越难排查。运维侧应在 `/stats` 中监控 `ruleForceBaseline` 是否突然暴涨——通常意味着出现了临时隔离规则在持续生效。

---

## 8. 常见问题

| 问题 | 解答 |
|---|---|
| weight=0 + 空 rules 的策略为什么查询 `findActiveForRoute` 返回空？ | active() 判定需要 "ACTIVE && (weight>0 \|\| rules 非空)"，weight=0 且无规则等同于"无策略"。需要"全 baseline 但审计"时，写一条永远 FORCE_BASELINE 的占位规则。 |
| 网关多实例扩容后，stats/history 对不上？ | 当前实现为内存态，**每实例各自维护**。全局视图靠 Prometheus sum() 聚合；如需强一致策略分发，参考 Phase H 实现说明 §6 中的 Nacos/Redis 替代方案。 |
| canaryUri 指向的 canary 集群实例为 0，是否会 503？ | 是；发布前需确保 canary 集群已注册到 Nacos/注册中心并具有健康实例。灰度不做实例数兜底。 |
| TraceId 不显示？ | 确认请求先后经过 ObservabilityGlobalFilter（order=-80）；在响应头与 `R<T>.traceId` 双回传，取其一即可。下游服务若自行包装响应，需保证未覆盖掉 `R.traceId`。 |
| 规则 FORCE_BASELINE 不生效？ | 可能：(a) 类型顺序在前的规则已短路命中 canary；(b) extra 字段未写 `FORCE_BASELINE`。 |

---

## 9. 变更历史 / 审计

所有策略变更均通过 `History` 条目留下审计痕迹（取最近 N 条用 GET `/policies/{policyId}/history?limit=50`）。

- 每一条含：时间戳、policyId、operator、action、detail（包含 old→new 权重 或 reason）
- 建议接出到安全审计系统：搜索 action=ROLLBACK、operator 非发布值班人、weight 在短时剧烈变动等异常信号。
