# 链盛通（LSC）消费权益凭证循环系统 V6.2-AI 发布说明

**版本号**: V6.2-AI
**发布日期**: 2026-09-08
**代号**: "合规筑基 · AI 风控 · 闭环流通"
**构建号**: lsc-api-service-1.0.0

---

## 一、版本概述

V6.2-AI 是链盛通消费权益凭证循环系统的一次重大合规与智能化升级。本版本聚焦三大主线：

1. **用户体系分离**：消费者会员与商家会员独立建模，商家需完成"营业执照 + 对公账户 + 监管协议"三要件准入审核方可具备核销与 B2B 流转资格。
2. **LSC 流转权限矩阵**：明确 C→M、M→M、商家核销三条合法流转路径，禁止 C→C、M→C，从制度层面杜绝代刷、套现等违规场景。
3. **AI 风控 + 日终对账**：固定规则（1 小时 10 笔订单等 4 条）+ AI 动态风控双轨并行；日终对账 Agent 自动比对支付机构流水与内部 LSC 账本，结果哈希上链存证。

同时配套落地 Snowflake ID 生成器、SHA-256 哈希序列化工具、推广奖励挂账与每日补发、商家每日核销 1 次限制、B2B 流转有效期重置等关键能力。

---

## 二、新功能特性

### 2.1 用户体系分离（14.1 users 表）

- 消费者会员（`userType=0`）与商家会员（`userType=1`）独立表结构
- 消费者会员支持一级直推推荐关系（`referrer_id`）
- 首单完成标记 `first_order_completed`（0/1），用于推广奖励触发条件
- 商家会员与 merchants 表通过 `user_id` 关联

**初始化数据**：消费者会员 30 名（20001-20030，链式推荐关系），商家会员 12 名（10001-10012）。

### 2.2 商家合规准入三要件（14.2 merchants 表）

商家必须同时满足以下三要件方可发起核销或 B2B 流转：

| 字段 | 说明 | 示例 |
|------|------|------|
| `business_license` | 营业执照编号 | BL-9001 |
| `corporate_account_no` | 对公账户号 | CORP-6001 |
| `regulatory_agreement_signed` | 监管协议签署状态（0未签/1已签）| 1 |

补充字段：`regulatory_account_no`（监管账户）、`main_account_no`（主账户）、`last_nh_date`（上次核销日）、`address_update_count`（地址变更次数）。

**测试用例**：商家 10006 未签监管协议，B2B 流转被拒绝并返回明确原因。

### 2.3 LSC 流转权限矩阵（P0-7）

| 流转方向 | 权限 | 约束条件 |
|----------|------|----------|
| 消费者 → 消费者 | **禁止** | 接口层直接拒绝，返回原因 |
| 消费者 → 商家 | 允许 | 必须绑定消费订单 `orderNo` |
| 商家 → 消费者 | **禁止** | 接口层直接拒绝，返回原因 |
| 商家 → 商家 | 允许 | 必须绑定已确认的 B2B 订单 |
| 商家核销 | 允许 | 每日 1 次，受额度档位约束 |

**接口**：
- `GET /api/lsc/transfer/matrix` — 查询权限矩阵规则
- `POST /api/lsc/transfer/c2m` — 消费者→商家流转
- `POST /api/lsc/transfer/m2m` — 商家→商家流转
- `GET /api/lsc/transfer/transactions` — 流水查询（按 type/userId/limit 过滤）

### 2.4 消费发行 LSC（订单创建联动）

纯人民币订单创建时自动触发 LSC 发行：

| 角色 | 获得比例 | 锁定状态 |
|------|----------|----------|
| 消费者 | 100% 锁定 LSC | 锁定池 |
| 商家 | 16% 锁定 LSC | 锁定池 |

落库流水类型 `type=1 消费发行`，包含 `beforeLocked/afterLocked/counterpartyId/orderNo/idempotentKey/evidenceHash`。

### 2.5 推广奖励与挂账补发

- **奖励规则**：首单消费金额 × 10%（向下取整）作为推广奖励
- **资金来源**：推荐人锁定池 → 可用池划转
- **挂账机制**：推荐人锁定余额不足时，差额记入 `promotion_pending` 表，状态为 0 全额挂账 / 2 部分补发
- **每日补发**：定时任务扫描挂账表，余额充足时自动补发并结清

**接口**：
- `POST /api/promotion/reward/trigger` — 触发首单奖励
- `POST /api/promotion/pending/scan` — 每日挂账扫描补发
- `GET /api/promotion/pending/list` — 挂账列表
- `GET /api/promotion/summary` — 推广统计

### 2.6 每日核销 1 次限制（规则七）

`WriteoffController.apply` 增加校验：同一商家当日已发起核销（状态 0-2），再次申请直接拒绝。

### 2.7 B2B 流转有效期重置

M2M 流转成功后，接收方 LSC 有效期重置为 365 天，防止库存过期。

### 2.8 日终对账机制（12.3）

- **比对维度**：支付机构流水（订单交易额）vs 内部 LSC 账本流水（type=1 消费发行）
- **差异指标**：LSC 差异额、匹配率
- **告警规则**：差异 > 0 或匹配率 < 100% 立即告警
- **哈希存证**：对账结果统一序列化后 SHA-256 哈希，标记 `blockchainStored=true` 上链

**接口**：
- `GET /api/reconcile/report` — 对账报告
- `GET /api/reconcile/mismatches` — 异常清单

### 2.9 用户行为风控体系（第十一章）

**4 项固定规则**（接口层强制）：

| 规则 | 阈值 | 风险等级 |
|------|------|----------|
| 1 小时下单超限 | > 10 笔 | 高 |
| 连续 LSC 支付超限 | 连续 3 笔 LSC 占比 > 90% | 高 |
| 同商品高比例 LSC | 超过 5 次 | 中 |
| 多城市 IP 异常 | 1 小时 ≥ 3 个不同城市 | 高 |

**AI 动态风控场景**：异常批量注册、代刷 LSC、拆分套利、跨地区套现。

**处置策略**：高风险自动限制，中低风险记录日志，申诉通道人工复审。

**接口**：
- `POST /api/risk/scan` — 实时风控扫描
- `GET /api/risk/dashboard` — 风控仪表盘
- `GET /api/risk/logs` — 日志列表（按 levelCode/merchantId 过滤）
- `POST /api/risk/appeal` — 申诉通道
- `PATCH /api/risk/logs/{id}/status` — 状态变更

### 2.10 流水类型枚举（14.4 ledger_txns）

| type | 名称 | 说明 |
|------|------|------|
| 1 | 消费发行 | 纯 RMB 支付触发 |
| 2 | 每日释放 | 动态释放算法 |
| 3 | 推广奖励释放 | 首单 10% 划转 |
| 4 | 权益商城消费 | C→M 流转 |
| 5 | 线下消费 | 线下扫码 |
| 6 | 过期转回 | 有效期到期 |
| 7 | 商家核销 | M 核销 LSC |
| 8 | B2B 流转支付 | M→M 流转 |
| 9 | 退款发行回滚 | 退款回滚 |

流水字段：`beforeLocked/afterLocked/beforeAvailable/afterAvailable/counterpartyId/orderNo/idempotentKey/evidenceHash`。

### 2.11 基础设施

- **Snowflake ID 生成器（P0-3）**：64 位全局唯一 ID，1 位符号 + 41 位时间戳 + 10 位机器 ID + 12 位序列号，单机 QPS 4096/s
- **SHA-256 哈希序列化工具（P0-5）**：统一序列化格式（key 字典序 + key=value 拼接），用于对账结果、交易流水等关键数据存证

---

## 三、数据库变更

### 3.1 新增表

| 表名 | 说明 |
|------|------|
| `users` | 用户表（消费者 + 商家会员分离） |
| `promotion_pending` | 推广奖励挂账表 |
| `lsc_accounts` | LSC 账户表（锁定池 + 可用池，乐观锁 version） |
| `daily_release_summary` | 每日释放汇总表 |
| `release_config` | 释放配置表（动态调节算法硬常量） |

### 3.2 扩展字段

**merchants 表**（14.2）：
- 新增：`business_license`、`corporate_account_no`、`regulatory_agreement_signed`、`regulatory_account_no`、`main_account_no`、`last_nh_date`、`address_update_count`

**ledger_txns 表**（14.4）：
- 变更：`type` 由字符串改为整数枚举（1-9）
- 新增：`type_str`、`before_locked`、`after_locked`、`before_available`、`after_available`、`counterparty_id`、`order_no`、`idempotent_key`、`evidence_hash`
- 移除：`type_code`

**risk_logs 表**：
- 新增：`user_id`、`type`、`remark`、`status`（0待处理/1已处理/2申诉中/3已关闭）

---

## 四、API 接口清单

### 4.1 新增接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/lsc/transfer/matrix` | LSC 流转权限矩阵 |
| POST | `/api/lsc/transfer/c2m` | 消费者→商家流转 |
| POST | `/api/lsc/transfer/m2m` | 商家→商家流转 |
| GET | `/api/lsc/transfer/transactions` | 流水查询 |
| POST | `/api/promotion/reward/trigger` | 触发推广奖励 |
| POST | `/api/promotion/pending/scan` | 挂账每日扫描 |
| GET | `/api/promotion/pending/list` | 挂账列表 |
| GET | `/api/promotion/summary` | 推广统计 |
| POST | `/api/risk/scan` | 实时风控扫描 |
| GET | `/api/risk/dashboard` | 风控仪表盘 |
| POST | `/api/risk/appeal` | 风控申诉 |
| GET | `/api/reconcile/report` | 日终对账报告 |
| GET | `/api/reconcile/mismatches` | 对账异常清单 |

### 4.2 变更接口

| 方法 | 路径 | 变更说明 |
|------|------|----------|
| POST | `/api/order/create` | 新增 LSC 消费发行联动 |
| POST | `/api/writeoff/apply` | 新增每日 1 次限制校验 |
| GET | `/api/ledger/transactions` | `typeCode` 参数改为 `type`（整数 1-9） |
| GET | `/api/ledger/transaction-types` | 返回 9 种 V6.2 标准枚举 |

---

## 五、端到端测试结果

**测试覆盖**：27 项核心测试用例，全部通过。

| 模块 | 测试项 | 结果 |
|------|--------|------|
| 流转权限矩阵 | C→C 禁止 / M→C 禁止 / C→M 允许 / M→M 允许 | ✓ |
| 消费发行 | 纯 RMB 订单触发，消费者 100% + 商家 16% 锁定 LSC | ✓ |
| 推广奖励 | 首单 10% 计算（150 元 → 15 LSC），锁定池→可用池划转 | ✓ |
| 挂账补发 | 锁定不足时挂账，每日扫描补发 | ✓ |
| 每日核销 1 次 | 规则七强制，当日已核销则拒绝 | ✓ |
| 商家准入三要件 | 未签监管协议的商家被拒绝 | ✓ |
| B2B 流转 | 绑定已确认订单，接收方有效期重置 365 天 | ✓ |
| 日终对账 | 订单 vs 账本对比，差异告警，哈希上链 | ✓ |
| 风控 4 规则 | 1 小时 10 笔 / 连续 3 笔超 90% / 同商品超 5 次 / 多城市 IP | ✓ |
| 风控申诉 | 标记申诉中状态，人工复审通道 | ✓ |
| 流水类型 1-9 | 9 种标准枚举全部落库验证 | ✓ |
| SHA-256 存证 | 统一序列化，关键流水哈希存证 | ✓ |
| Snowflake ID | 64 位全局唯一 ID 生成 | ✓ |

---

## 六、Bug 修复

| 问题 | 修复说明 |
|------|----------|
| `WriteoffController.apply` 中 `merchantId` 变量未定义 | 改为 `body.getMerchantId()` |
| `LedgerController.transactions` 使用已废弃的 `getTypeCode()` | 改为 `getType()`（整数 1-9） |
| `ReconcileController` 中 lambda 引用可变局部变量 | 改为 `final` 变量 `start` / `end` |
| `DataInit.seedLedgerTxns` 字段与 V6.2 实体不匹配 | 对齐 `beforeLocked/afterLocked/counterpartyId` 等新字段 |
| `DataInit.seedRiskLogs` 缺少 `userId/type/status` | 补充 V6.2 风控日志字段 |
| 消费者未初始化 LSC 账户导致 C2M 流转失败 | `seedLscAccounts` 扩展至消费者 20001-20030 |
| Maven 依赖下载失败（网络代理） | 配置 `settings.xml` 走 127.0.0.1:18080 代理 |

---

## 七、升级与迁移说明

### 7.1 数据迁移

本版本为全新部署，无需从旧版本迁移数据。生产环境升级时需注意：

1. **ledger_txns 表结构变更**：`type` 字段由字符串改为整数，需编写迁移脚本将旧值映射至 1-9 枚举
2. **risk_logs 表新增字段**：`status` 默认值 0（待处理），`user_id` 默认 NULL
3. **merchants 表新增字段**：合规三件件需回填真实资质数据，`regulatory_agreement_signed` 默认 0

### 7.2 配置变更

- Maven `settings.xml` 需配置 HTTP/HTTPS 代理（127.0.0.1:18080）
- H2 数据库首次启动自动建表 + 初始化种子数据

### 7.3 启动验证

```bash
cd /workspace/lsc-api-service
mvn clean package -DskipTests -s /workspace/.m2/settings.xml
java -jar target/lsc-api-service-1.0.0.jar
```

启动后日志应显示：
```
[LSC API] 内存数据初始化完成: 商家12 商品24 订单24
[LSC DB] users 表 seed 完成: 42 条
[LSC DB] merchants 表 seed 完成: 12 条
[LSC DB] lsc_accounts 表 seed 完成: 42 条
[LSC DB] ledger_txns 表 seed 完成: 25 条
[LSC DB] risk_logs 表 seed 完成: 15 条
```

---

## 八、已知限制与后续计划

### 已知限制

1. **AI 动态风控**：当前为规则占位接口，实际 AI 模型推理需对接外部风控引擎
2. **区块链上链**：`blockchainStored` 标记为 true，实际链上存证需对接联盟链节点
3. **支付机构流水**：对账依赖订单数据模拟支付侧，生产环境需对接真实支付机构 API
4. **动态释放算法**：硬常量 `0.03%~0.06%` 已固化在 `release_config` 表，但每日释放定时任务尚未实现

### 后续计划（V6.3）

- 对接真实联盟链节点完成哈希上链
- AI 风控模型训练与推理服务化
- 每日释放定时任务（基于核销率 k 动态调节 rate）
- 支付机构流水实时对接
- 多级推荐关系（二级、三级）推广奖励

---

## 九、致谢

感谢链盛通（LSC）消费权益凭证循环系统项目组全体成员。V6.2-AI 版本奠定了合规筑基、AI 风控、闭环流通三大支柱，为后续生产部署奠定了坚实基础。

**链盛通（LSC）消费权益凭证循环系统项目组**
**2026 年 9 月 8 日**
