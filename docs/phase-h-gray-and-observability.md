# Phase H：灰度发布 & 可观测性（实现说明）

> 目标：在 **网关层（Spring Cloud Gateway）** 完成对业务无侵入的灰度发布与全链路可观测能力建设。
> 文档范围：机制契约、关键数据结构、接口约定、调用顺序。不包含构建/部署/验证命令。

---

## 1. 总体架构

```
请求 → JwtAuthFilter (order=-100)
     → GrayReleaseGlobalFilter (order=-90)   // 分流决策：规则 + 权重
     → ObservabilityGlobalFilter (order=-80) // TraceId 生成/透传 + 指标 + 审计日志
     → Gateway Routing (NettyRoutingFilter)
         ├── lb://lsc-order-service          // baseline
         └── lb://lsc-order-service-canary   // canary
下游 ← 响应头 X-Trace-Id / X-Gray-Version 回传
```

- 分流在 **Gateway 过滤器链** 上完成，业务服务不需要改动。
- 灰度决策写回到 request attribute / header，下游服务可通过 header `X-Gray-Version` / `X-Gray-Policy` 继续埋点或审计。

---

## 2. 灰度发布（Gray Release）

### 2.1 策略模型 `GrayPolicyStore.Policy`

| 字段 | 类型 | 说明 |
|---|---|---|
| policyId | String | 策略唯一 ID（如 `order-v2.1-20260903`） |
| routeId | String | 对应 Spring Cloud Gateway 路由 ID |
| baselineUri | String | 基线目标（通常 `lb://<服务名>`） |
| canaryUri | String | 灰度目标（通常 `lb://<服务名>-canary`） |
| canaryWeightPercent | int | 0~100，命中 canary 的加权百分比 |
| rules | `List<Rule>` | 强制规则列表，短路求值（先匹配先生效） |
| metadata | Map<String,Object> | 自由扩展字段（产品/版本号/负责人等） |
| status | `Status` | `ACTIVE / PAUSED / ROLLED_BACK / GRADUATED` |
| createdAt / updatedAt / updatedBy | Instant, String | 审计字段 |

`Policy.active()` 判定：`status == ACTIVE && (weight > 0 || !rules.isEmpty())`。
即 **weight=0 但存在 rules 的策略仍参与决策**（用于"仅规则命中灰度"场景）。

### 2.2 Rule 模型 `GrayPolicyStore.Rule`

| 字段 | 语义 |
|---|---|
| type | `HEADER / QUERY / COOKIE / USER_ID_MOD / PATH_PREFIX` |
| key | 属性名（HEADER/QUERY/COOKIE 时必填） |
| operator | `EQ / NE / PREFIX / SUFFIX / CONTAINS`，HEADER/QUERY/COOKIE 使用；`MOD_EQ` 用于 USER_ID_MOD |
| value | 比较期望值 / PATH_PREFIX 前缀 / USER_ID_MOD 期望余数 |
| extra | USER_ID_MOD 时为模数 `mod`；其他类型为语义标签：`FORCE_BASELINE`（命中则强制基线）或空（默认命中=强制灰度） |

命中返回约定（`matchRule` 返回 `Boolean`）：
- `TRUE` → 强制 canary（`ruleForceCanary++`）
- `FALSE` → 强制 baseline（`ruleForceBaseline++`）
- `null` → 本规则不匹配，继续下一条；全不匹配则退化为**权重随机**

具体规则行为：

| type | 匹配条件 | 命中默认语义 | 支持反向（FORCE_BASELINE） |
|---|---|---|---|
| HEADER | `op(key, value)`，key 不存在 = 不匹配 | 强制灰度 | ✅ extra=`FORCE_BASELINE` |
| QUERY | 解析 querystring 取 key=value，同上 | 强制灰度 | ✅ |
| COOKIE | `op(cookie.value, value)` | 强制灰度 | ✅ |
| USER_ID_MOD | 读取头 `X-User-Id`，`Math.floorMod(id, mod) == expected`；extra=mod / value=expected | 强制灰度 | ✅（通过 extra 显式 `FORCE_BASELINE`；注：`mod` 与语义二选一，后续可扩展复合字段） |
| PATH_PREFIX | `AntPathMatcher.match(prefix + "/**", path) || path.startsWith(prefix)` | 强制灰度 | ✅ extra=`FORCE_BASELINE` |

### 2.3 分流过滤器 `GrayReleaseGlobalFilter`（order = -90）

算法：

```
route = exchange.GATEWAY_ROUTE_ATTR
policy = store.findActiveForRoute(route.id)
if policy == null: 直接放行 baseline

for rule in policy.rules (顺序):
    outcome = matchRule(rule, req)
    if TRUE:  canary = true;  break (短路)
    if FALSE: canary = false; break (短路)
else:
    canary = ThreadLocalRandom.nextInt(100) < policy.canaryWeightPercent
```

命中 canary 时的副作用：

1. `exchange.GATEWAY_ROUTE_ATTR` 替换为 `Route(uri=policy.canaryUri)`，保证 NettyRoutingFilter 转发到灰度集群。
2. 新增请求头：
   - `X-Gray-Policy: {policyId}`
   - `X-Gray-Version: canary`（baseline 分支也会写 `baseline`，方便下游查）
3. exchange attributes：
   - `lsc.gray.version` = `baseline | canary` → 供 Observability 过滤器打标
   - `lsc.gray.policyId` = `policyId`
4. 写入统计：**按 60 秒滚动桶** 更新 `perSecondCanary / perSecondBaseline` 与累计 `canaryHits / baselineHits / ruleForceCanary / ruleForceBaseline`。

### 2.4 管理接口 `GrayReleaseController`（`/api/gateway/gray`）

> 管理员接口；生产环境建议挂内网 admin 网关，或前置 `X-Admin-User` 鉴权。

| 方法 | 路径 | 功能 | 写历史 |
|---|---|---|---|
| POST | `/policies` | upsert；存在则更新为 ACTIVE（按 id） | CREATE / UPDATE |
| GET | `/policies` | 列表所有策略 | – |
| GET | `/policies/{policyId}` | 单条策略详情 | – |
| GET | `/policies/{policyId}/stats` | 实时统计（累计+最近 60s QPS+桶明细） | – |
| PUT | `/policies/{policyId}/weight?percent=50&operator=` | 调整权重；0 → PAUSED；>0 → ACTIVE | WEIGHT_CHANGE |
| POST | `/policies/{policyId}/pause` | 暂停（权重保持不变，改状态 PAUSED） | PAUSE |
| POST | `/policies/{policyId}/resume` | 恢复 ACTIVE | RESUME |
| POST | `/policies/{policyId}/rollback` | **一键回滚**：status=ROLLED_BACK，weight=0；可写 reason | ROLLBACK |
| GET | `/policies/{policyId}/history?limit=50` | 变更历史（最新在前，限制 limit） | – |
| GET | `/summary` | 全路由聚合：每个 routeId 的 active 策略 + 最近 60s QPS 比例 | – |

### 2.5 仓储与历史 `GrayPolicyStore`

- 内存态实现：`ConcurrentHashMap<policyId, AtomicReference<Policy>>`，更新用 CAS 保证并发安全。
- 历史：`ConcurrentLinkedDeque<History>`，**CREATE / UPDATE / WEIGHT_CHANGE / PAUSE / RESUME / ROLLBACK / GRADUATE** 共 7 类动作；每类记录 `(ts, policyId, operator, action, detail)`，detail 可写旧→新权重或回滚原因等。
- 统计：`ConcurrentHashMap<policyId, Stats>`，`Stats` 含 `AtomicLong baselineHits, canaryHits, ruleForceCanary, ruleForceBaseline` + `AtomicInteger[60] perSecondBaseline, perSecondCanary`（分桶取 `(now/1000) % 60`，自然滚动覆盖）。

---

## 3. 可观测性（Observability）

### 3.1 TraceId 链路

入口过滤器：`ObservabilityGlobalFilter`（order = -80，紧跟灰度过滤器之后）。

- 头协议：`X-Trace-Id`
- 生成规则（**网关生成**）：16 字符 hex，格式 `ts(秒,hex,8)+seq(自增 4)+random(4)`，使用 `SecureRandom` + 时间戳 + `AtomicInteger` 组合保证唯一且无冲突。
- 下游服务侧（`lsc-common`）工具：`TraceIdHolder` 基于 SLF4J MDC：
  - `createIfAbsent(上游头)`：无则 UUID.hex，有则继承；写 MDC key = `traceId`。
  - `get / set / clear`：配合 `TaskDecorator / Filter / Interceptor` 在线程切换时透传。
- 响应头 **总是回传** `X-Trace-Id`，客户端、Nginx、CDN 可直接记录。
- 响应体 `R<T>` 新增 `traceId` 字段，由 `GlobalExceptionHandler` 在异常/正常统一响应中回填。异常日志 `log.error(..., traceId, e)` 保证可从错误返回追溯到后台日志。

过滤器链顺序：**JwtAuthFilter → Gray → Observability**。这样 Observability 能读取到 `lsc.gray.version` 为指标打标。

### 3.2 Metrics（Micrometer / Prometheus 友好）

- **请求计数** `lsc.gateway.requests.total` tags=`{routeId, grayVersion, status, family}`
  - `grayVersion` = `baseline | canary | none`（无策略时 none）
  - `status` = HTTP status code（0 表示未收到响应）
  - `family` = `1xx/2xx/3xx/4xx/5xx/unknown`
- **请求耗时** `lsc.gateway.requests.duration` Timer tags=`{routeId, grayVersion}`。
- 生成位置：Observability 过滤器 `doFinally` 阶段（无论成功、取消、错误都执行）。

### 3.3 Actuator 自定义端点 `LscGatewaySummaryEndpoint`

- 访问：`/actuator/lscGatewaySummary`
- 输出聚合：
  - 各 routeId 当前 active 策略（policyId / weight / rules 摘要）
  - 最近 60 秒 baseline & canary 请求数与比例
  - 最近 60 秒 QPS（按秒合计后取均值）
  - ruleForceCanary / ruleForceBaseline 累计

方便 SRE 用 Prometheus + Grafana + 自写抓取脚本联合展示。

### 3.4 结构化审计日志

Observability 过滤器在 `filter()` 进入时打印一条 INFO：

```
traceId=… routeId=… gray=… method=… path=… remote=…
```

命中灰度/发生 5xx 时附加打印（DEBUG/ERROR）策略 ID、命中规则、耗时。日志字段与 MDC 对齐，配合 Logstash / Loki 可直接检索。

---

## 4. 接口与数据契约速查

### 4.1 Rule 模板（常用）

| 场景 | Rule |
|---|---|
| QA 头 "X-Canary: force" 永远灰度 | `HEADER("X-Canary", "EQ", "force", null)` |
| 运维头 "X-Baseline: always" 永远基线 | `HEADER("X-Baseline", "EQ", "always", "FORCE_BASELINE")` |
| `/api/order/admin/**` 灰度（内部管理员先行） | `PATH_PREFIX(null, null, "/api/order/admin", null)` |
| 按 userId 尾号 0 灰度 10% 用户 | `USER_ID_MOD(null, "MOD_EQ", "0", "10")` |
| query 参数 `canary=true` 灰度 | `QUERY("canary", "EQ", "true", null)` |
| cookie `beta=1` 走灰度 | `COOKIE("beta", "EQ", "1", null)` |

### 4.2 状态迁移合法事件

| 当前状态 | 允许事件 | 结果 |
|---|---|---|
| (不存在) | upsert | ACTIVE |
| ACTIVE | setWeight(0) / pause | PAUSED |
| ACTIVE | rollback | ROLLED_BACK |
| ACTIVE | setWeight(>0) | ACTIVE（记录 WEIGHT_CHANGE） |
| PAUSED | resume / setWeight(>0) | ACTIVE |
| PAUSED | rollback | ROLLED_BACK |
| ROLLED_BACK | upsert（新权重/规则） | ACTIVE（等价重新发布） |

### 4.3 异常与错误响应约定

- 所有管理接口统一返回 `R<T>`（code=0 成功；404 策略不存在；400 权重非法等），并带 `traceId`。
- 内部 filter 中 **不得** 写业务错误响应；灰度决策失败（如 store 抛异常）走 `onErrorResume` 放行 baseline 并记 ERROR 日志 + `gray=none`，避免影响主链路。

---

## 5. 测试覆盖（契约级说明）

| 测试类 | 覆盖 |
|---|---|
| `GrayPolicyStoreTest` | CRUD / 历史 / 滚动桶 / 回滚与状态迁移 |
| `GrayReleaseControllerTest` | upsert / get / setWeight / rollback / stats / history |
| `GrayReleaseGlobalFilterTest` | 无策略 / weight=0 / weight=100 / weight=50 大数收敛 / HEADER / USER_ID_MOD / PATH_PREFIX |
| `ObservabilitySmokeTest` | TraceId 透传 & 回传、指标生成、版本打标 |
| `TraceIdHolderTest` | createIfAbsent / MDC / clear |
| **`GrayReleaseE2ETest`** | 多规则组合短路 + 权重 10→50→100 渐进 + 回滚切换 baseline |

---

## 6. K8s 兼容说明（设计层）

- 网关无状态：策略为内存态，水平扩容下各实例 **各自独立记录 stats/history**。若需要全局一致策略，后续可替换 `GrayPolicyStore`：
  - 方案 A：把策略保存到配置中心（Nacos/Apollo）并 watch；QPS 统计仍本地化（由 Prometheus 聚合到 job 级别）。
  - 方案 B：引入 Redis 共享 `(policyId → Stats)` 并让 Controller 读 Redis。
- `canaryUri` 使用 `lb://<服务>-canary`，与 K8s Service + 注册中心双副本（baseline / canary）模型匹配，无需 K8s 侧增加 Service Mesh。
- TraceId 头名 `X-Trace-Id` 与大多数 Ingress / Service Mesh（Istio/APISIX）默认头兼容，可在 Helm values 中统一配置。
