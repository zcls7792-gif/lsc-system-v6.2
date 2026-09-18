# LSC V7.3 后端核心账务切片（子项目 A）设计文档

> **范围**：后端 5 个微服务从 V6.2 升级到 V7.3 规格的最小可上线切片。所有前端（C 平台后台 / D 供应商后台 / E 微信小程序 / F 移动 APP）都依赖本切片产出的 API 契约。
>
> **不包含**：供应链采购结算、AI 审核 Agent、风控前置、对账服务、管理后台/供应商后台前端。这些属子项目 B 及后续轮次。
>
> **基准文档**：`/workspace/.uploads/48ec3392-57b9-46f4-8f7b-0a23cb1a6592_链盛通（LSC）消费....txt`（V7.3 spec）

## 1. 背景与目标

V6.2 后端将 LSC 定位为"可 B2B 流转、商家可核销兑现"的权益凭证；V7.3 spec 第 1.3、9.1 条将 LSC 重定位为**消费回馈积分**，性质为折扣权益，**禁止转让 / 兑现 / 提现 / 跨账户流转**，并新增进销差赠送、混合支付仅人民币部分赠送、6 年线性释放、次日释放、一级直推 10% 等核心规则。

本切片目标：在不破坏现有 17 微服务骨架的前提下，对 5 个账务相关服务做**结构性改造**，使其满足 V7.3 P0 合规基线（17 条硬底线）和核心业务规则，并冻结对外 API 契约供前端对接。

## 2. 服务改造总览

| # | 服务 | 改造级别 | 范围摘要 |
|---|---|---|---|
| 1 | [lsc-ledger-service](file:///workspace/lsc-ledger-service) | **大改** | 删除 B2B/核销/跨账户接口；新增 `total_frozen` 字段与冻结/解冻流水；过期从"转回锁定"改为"作废"；流水类型枚举重定义 |
| 2 | [lsc-release-service](file:///workspace/lsc-release-service) | 中改 | 增加"赠送次日"触发条件；二次校验越界终止已有；硬常量保留 |
| 3 | [lsc-mall-service](file:///workspace/lsc-mall-service) | **中改** | 商品表加 `cost_price` / `grant_points`；自动算 `grant_points = price - cost_price`（≤0 为 0，封顶 `price × 1.00`）；对外 DTO 不含 `cost_price` |
| 4 | [lsc-order-service](file:///workspace/lsc-order-service) | **中改** | 混合支付硬上限校验（默认 20% / 硬上限 50%）；订单完成后按 RMB 实付比例算赠送并调 ledger；退款按消法 + LSC 退回/扣回 |
| 5 | [lsc-promotion-service](file:///workspace/lsc-promotion-service) | 中改 | 仅一级直推 10%，被推荐人订单完成时按其**实际获赠积分** × 10% 入推荐人锁定（而非消费金额 × 10%） |

## 3. V6.2 → V7.3 字段映射

### 3.1 流水类型枚举 `LscTransactionTypeEnum`

| V6.2 值 | V6.2 语义 | V7.3 值 | V7.3 语义 | 处置 |
|---|---|---|---|---|
| 1 | 消费发行 | 1 | 消费赠送入锁定 | 语义重定义，保留枚举值 |
| 2 | 每日释放 | 7 | 每日释放 | **值变更**：V6.2 的 `2` 不再使用，V7.3 用 `7` |
| 3 | 推广奖励 | 6 | 推荐奖励入锁定 | **值变更**：V6.2 的 `3` 不再使用，V7.3 用 `6` |
| 4 | 商城消费 | 2 | 订单抵扣 | **值变更**：V6.2 的 `4` 不再使用，V7.3 用 `2` |
| 5 | 线下消费 | — | （V7.3 不区分线上线下） | 枚举移除 |
| 6 | 过期转回 | 5 | 到期作废 | **语义变更**：从"可用→锁定"改为"可用→销毁" |
| 7 | 商家核销 | — | （V7.3 禁止核销兑现） | 枚举移除 |
| 8 | B2B 流转 | — | （V7.3 禁止跨账户流转） | 枚举移除 |
| 9 | 退款退回 | 3 | 退款退回 | **值变更**：V6.2 的 `9` 不再使用，V7.3 用 `3` |
| — | （无） | 4 | 退款回扣 | 新增（退款时扣回已赠送积分） |
| — | （无） | 8 | 风控冻结 | 新增 |
| — | （无） | 9 | 风控解冻 | 新增 |

**V6.2 历史数据迁移策略**：上线前执行一次性 SQL 脚本，按上表把旧 `type` 值映射到新值，未映射的（4/5/7/8）按业务侧归档决策处理。迁移脚本见附录 A。

### 3.2 账户表 `lsc_accounts`

| V6.2 | V7.3 | 变更 |
|---|---|---|
| `total_locked` bigint | `total_locked` bigint | 保留 |
| `total_available` bigint | `total_available` bigint | 保留 |
| — | `total_frozen` bigint DEFAULT 0 | **新增**（风控冻结池） |
| `version` int | `version` int | 保留（乐观锁覆盖三池） |
| `updated_at` datetime(3) | `updated_at` datetime(3) | 保留 |

### 3.3 可用明细 `available_lsc_details`

V6.2 `status`：1 有效 / 2 过期转回 / 3 已使用 / 4 已核销 / 5 退款退回
V7.3 `status`：1 有效 / 2 已作废 / 3 已使用 / 4 退款退回
（移除"已核销"，因 V7.3 禁止核销兑现；新增"已作废"语义）

### 3.4 商品表 `products`

| V6.2 | V7.3 | 变更 |
|---|---|---|
| `price` decimal(18,2) | `price` decimal(18,2) | 保留，对外展示 |
| — | `cost_price` decimal(18,2) | **新增**（进货价，仅后台可见，API 不返回） |
| — | `grant_points` bigint | **新增**（全额 RMB 支付赠送积分，对外展示） |
| `merchant_id` bigint | `merchant_id` bigint | 保留（子项目 B 重定位为 supplier_id） |

### 3.5 订单表 `orders`

| V6.2 | V7.3 | 变更 |
|---|---|---|
| `order_type` 0 线上 / 1 线下 | `order_type` 0 纯 RMB / 1 RMB+LSC 混合 | **语义重定义** |
| `lsc_amount` bigint | `lsc_amount` bigint | 保留 |
| `rmb_amount` decimal | `rmb_amount` decimal | 保留 |
| — | `granted_lsc` bigint DEFAULT 0 | **新增**（本单实际赠送量，订单完成时写入） |
| `status` 0-5 | `status` 0-7 | 见下表 |
| — | `refund_status` tinyint | **新增** |
| — | `refund_lsc_amount` bigint | V6.2 已有，保留 |
| — | `refund_rmb_amount` decimal | V6.2 已有，保留 |

订单状态枚举（V7.3 spec 2.5）：
- 0 待支付 / 1 已支付 / 2 已发货 / 3 已完成 / 4 已取消 / 5 退款中 / 6 已退款 / 7 部分退款

## 4. 详细设计

### 4.1 lsc-ledger-service

#### 4.1.1 接口契约（V7.3）

**保留**：
- `issueLsc(userId, amount, orderNo)` — 消费赠送入锁定（type=1）。语义从 V6.2 的"发行到推荐人锁定池"改为"发行到消费者本人锁定池"。**注意**：V6.2 实现把 LSC 发行到推荐人池，V7.3 必须发行到消费者本人池（spec 3.1）。
- `releaseLsc(userId, amount, orderNo)` — 每日释放（type=7）。保留骨架。
- `refundLsc(userId, amount, orderNo)` — 退款退回（type=3）。语义从"商家退回消费者"改为"订单退款时退回消费者可用"。
- `getBalance(userId)` — 查询锁定/可用/冻结三池余额。
- `transactionList` / `availableDetails` / `recentTrend` / `overview` — 查询接口保留。
- `lockedSummary` / `releaseBatch` — 释放任务调用，保留。

**删除**：
- `payLsc(consumerId, merchantId, ...)` — V7.3 禁止消费者→商家 LSC 流转，删除。
- `b2bTransfer(...)` — V7.3 禁止 B2B 流转，删除。
- `writeOffLsc(...)` — V7.3 禁止商家核销兑现，删除。
- `expireTransfer(userId)` / `expireTransferAll()` — V6.2 是"可用→锁定"，V7.3 改为"可用→作废"。

**新增**：
- `grantLsc(userId, amount, orderNo)` — 消费赠送入锁定（type=1），是 `issueLsc` 的语义化重命名。为兼容现有调用方，保留 `issueLsc` 方法名作为别名。
- `deductLsc(userId, amount, orderNo)` — 订单抵扣（type=2），消费者用可用 LSC 抵扣货款。**注意**：V7.3 抵扣的 LSC 是**销毁**而非转入商家账户（spec 9.1 "积分使用部分由链盛通承担营销成本"）。
- `refundDeductLsc(userId, amount, orderNo)` — 退款回扣（type=4），退款时扣回已赠送但未使用的积分。规则按 spec 2.4：已用部分不计、未用部分退回。具体策略：从锁定池扣回（若锁定池不足则扣可用池，仍不足记挂账待回收）。
- `expireWriteoff(userId)` — 到期作废（type=5），扫描可用明细 `status=1 AND expire_date < today`，汇总后扣减可用余额、写流水、明细置 `status=2`。
- `expireWriteoffAll()` — 全网扫描作废。
- `freezeLsc(userId, amount, orderNo, reason)` — 风控冻结（type=8），可用→冻结。
- `unfreezeLsc(userId, amount, orderNo, reason)` — 风控解冻（type=9），冻结→可用。
- `promotionRewardLsc(referrerId, amount, orderNo)` — 推荐奖励入锁定（type=6），推荐人锁定池增加。

#### 4.1.2 实体变更

`LscAccount` 新增 `totalFrozen` 字段：

```java
/** 冻结LSC总量(风控冻结池) */
private Long totalFrozen;
```

`LscTransaction` 的 `type` 注释更新为 V7.3 枚举语义，字段结构不变。

`AvailableLscDetail.status` 注释更新：1 有效 / 2 已作废 / 3 已使用 / 4 退款退回。

#### 4.1.3 SQL 迁移脚本

新增 `V7.3.0__ledger_v73_migration.sql`：
- `ALTER TABLE lsc_accounts ADD COLUMN total_frozen BIGINT NOT NULL DEFAULT 0 AFTER total_available;`
- `ALTER TABLE lsc_accounts ADD COLUMN total_frozen_snapshot BIGINT NOT NULL DEFAULT 0 AFTER after_available;` 到 `lsc_transactions`（流水快照）
- 数据迁移：见附录 A

#### 4.1.4 测试改造

- 删除 `b2bTransfer` / `writeOffLsc` / `payLsc` 相关契约（`payLsc.groovy`）。
- 新增 `grantLsc.groovy` / `deductLsc.groovy` / `expireWriteoff.groovy` / `freezeLsc.groovy` / `promotionRewardLsc.groovy`。
- 保留 `issueLsc.groovy` / `refundLsc.groovy`（语义已变，重新打桩）。
- 现有单测 `LscLedgerServiceImplTest` / `LscLedgerServiceImplExtendedTest` 移除被删方法的用例，新增冻结/作废/抵扣场景。

### 4.2 lsc-release-service

#### 4.2.1 现状评估

V6.2 已实现：`ReleaseConfig`（rate_max/rate_min/w_min/w_max 全部 editable=0 不可编辑）、`ReleaseCalcServiceImpl`（含动态速率分段计算 + 二次校验越界终止 + 双管理员告警）、`BatchReleaseServiceImpl`（分批 10 万 + 断点续跑 + 全量校验补偿）、灰度审批工作流。

V7.3 spec 3.2 要求与 V6.2 基本一致，**唯一新增**：赠送次日开始释放（spec 3.2 "次日释放机制"）。

#### 4.2.2 改造点

- `ReleaseJobHandler` 的每日任务调度时机不变（每日凌晨），但**扫描范围**从"所有锁定余额 > 0 的账户"细化为"所有 `created_at <= 当日 - 1 天` 的锁定流水对应的账户"。
- 即：T 日赠送的积分，T+1 日开始进入释放计算。
- 实现方式：在 `lsc_transactions` 上加 `release_start_date` 字段（或基于 `created_at + INTERVAL 1 DAY`），释放任务扫描 `release_start_date <= today` 的锁定流水。
- 二次校验已有，保留。
- 灰度审批已有，保留。

#### 4.2.3 测试

- 新增测试：赠送当日不释放、次日才释放。
- 现有 `ReleaseCalcServiceImplTest` / `BatchReleaseServiceImplTest` 保留。

### 4.3 lsc-mall-service

#### 4.3.1 实体变更

`Product` 新增：
```java
/** 进货价(仅后台可见,API 不返回) */
private BigDecimal costPrice;

/** 全额RMB支付赠送积分(=price-costPrice,≤0为0,封顶price*1.00) */
private Long grantPoints;
```

#### 4.3.2 服务变更

`ProductService.publishProduct` / `updateProduct`：
- 入参 DTO `ProductPublishDTO` 新增 `costPrice` 字段（仅供应商/平台后台传入）。
- 校验：`costPrice != null && costPrice >= 0`；`price != null && price > 0`。
- 自动计算：`grantPoints = max(0, price - costPrice)`，并硬上限校验 `grantPoints <= price × grant_rate_absolute_max`（1.00）。越界抛异常。
- `grant_rate_absolute_max` 为**硬编码常量** `1.00`，写在 `ProductConstants` 类中，不可配置。

`ProductService.getProductDetail`：
- 返回 DTO **不含** `costPrice`、不含进销差、不含利润率。返回字段：`id, name, categoryId, price, grantPoints, stock, mainImage, ...`。
- 实现方式：在 `ProductController` 的响应 DTO 上显式 `@JsonIgnore` 或使用独立 VO 类。

`ProductService.listProducts` / `listProductsAdmin`：
- 消费者侧列表不含 `costPrice`。
- 管理后台侧列表可含 `costPrice`（仅平台运营人员可见，子项目 B 加权限校验）。

`HybridPayService.calc`：
- 入参新增 `grantPoints`（按全额 RMB 计算的赠送积分）和 `rmbPaid`（实际人民币支付金额）。
- 输出新增 `expectedGrantPoints`：`grantPoints × (rmbPaid / price)`，向下取整。
- 硬上限校验：`lscAmount <= totalPrice × deduction_rate_max`（0.50 硬编码）。默认上限 `0.20` 从营销配置表读取。

#### 4.3.3 硬常量类

新增 `com.lianshengtong.mall.constant.ProductConstants`：
```java
public final class ProductConstants {
    /** 赠送比例绝对安全上限 100%(硬编码,不可配置) */
    public static final double GRANT_RATE_ABSOLUTE_MAX = 1.00;
    /** 单笔抵扣硬性上限 50%(硬编码,不可配置) */
    public static final double DEDUCTION_RATE_MAX = 0.50;
    /** 单笔抵扣默认上限 20%(可配置,从营销配置表覆盖) */
    public static final double DEDUCTION_RATE_DEFAULT = 0.20;
    private ProductConstants() {}
}
```

#### 4.3.4 SQL 迁移

```sql
ALTER TABLE products ADD COLUMN cost_price DECIMAL(18,2) NOT NULL DEFAULT 0 AFTER price;
ALTER TABLE products ADD COLUMN grant_points BIGINT NOT NULL DEFAULT 0 AFTER cost_price;
-- 历史商品回填：grant_points = 0(因 V6.2 无 cost_price 概念,统一回填为 0,供应商重新上架时再填)
UPDATE products SET grant_points = 0 WHERE grant_points IS NULL;
```

营销配置表预置数据（spec 8.9）：
```sql
INSERT INTO marketing_config(config_key, config_value, editable, description) VALUES
  ('grant_rate_absolute_max', '1.00', 0, '赠送比例绝对安全上限100%'),
  ('deduction_rate_default', '0.20', 1, '单笔抵扣默认上限20%可配置'),
  ('deduction_rate_max', '0.50', 0, '单笔抵扣硬性上限50%编译后不可修改'),
  ('lsc_valid_days', '365', 1, '有效期天数'),
  ('release_period_years', '6', 1, '释放周期约6年'),
  ('mixed_payment_grant_rule', 'rmb_only', 0, '混合支付仅人民币部分赠送积分');
```

### 4.4 lsc-order-service

#### 4.4.1 实体变更

`Order` 新增：
```java
/** 本单实际赠送积分(订单完成时写入) */
private Long grantedLsc;

/** 退款状态 0无退款 1退款中 2已退款 3部分退款 */
private Integer refundStatus;
```

`order_type` 语义重定义：0 纯 RMB / 1 RMB+LSC 混合（原 0 线上 / 1 线下）。

`status` 扩展为 0-7（见 3.5）。

#### 4.4.2 服务变更

`OrderService.createOrder`：
- 入参 DTO 新增 `useLscAmount`（消费者拟用 LSC 数量）。
- 调用 `mall-service.getProductDetail` 拿到 `price` 和 `grantPoints`。
- 调用 `HybridPayService.calc` 计算混合支付拆分 + 硬上限校验。
- 写订单（含 `grantedLsc=0`，待订单完成时再写）。

`OrderService.payOrder`：
- 调用 `ledger.deductLsc` 扣减消费者可用 LSC（type=2）。
- 唤起人民币支付（微信支付）。
- 支付成功后订单状态 0→1（已支付）。

`OrderService.completeOrder`（订单完成，spec 2.3）：
- 状态 1→3（已完成，跳过 2 已发货，本切片不实现物流）。
- **按 RMB 实付比例算赠送**：`grantPoints × (rmbAmount / price)`，向下取整，多商品订单逐商品汇总。
- 调用 `ledger.grantLsc(consumerId, grantedLsc, orderNo)` 写入消费者锁定池（type=1）。
- 写 `order.granted_lsc`。
- 通过 Feign 异步通知 `promotion.notifyFirstOrder(consumerId, orderNo, grantedLsc, status=3, refundAmount)`。

`OrderService.refundOrder` / `partialRefund`（spec 2.4）：
- 状态 3→5（退款中）→6/7。
- LSC 部分处理顺序（spec 2.4）：
  1. 已使用的 LSC（`order.lscAmount`）：按实退回消费者可用池（`ledger.refundLsc` type=3）。
  2. 已赠送的 LSC（`order.grantedLsc`）：按退款商品对应的实际赠送积分扣回（`ledger.refundDeductLsc` type=4）。规则：已用部分不计、未用部分退回。扣回顺序**明确**：(a) 优先从消费者**锁定池**扣回；(b) 锁定池不足时从**可用池**扣回；(c) 可用池仍不足时记挂账（promotion_pending 表复用），由子项目 B 的对账任务定期回收。
- 人民币部分走原支付渠道退款。
- 七天无理由 + 质量问题按消法（本切片只做状态机，售后政策文案属子项目 C）。

`OrderService.cancelOrder`：
- 仅 0 待支付可取消。无 LSC 操作。

#### 4.4.3 硬常量

复用 `mall.ProductConstants.DEDUCTION_RATE_MAX` / `DEDUCTION_RATE_DEFAULT`。

#### 4.4.4 SQL 迁移

```sql
ALTER TABLE orders ADD COLUMN granted_lsc BIGINT NOT NULL DEFAULT 0 AFTER rmb_amount;
ALTER TABLE orders ADD COLUMN refund_status TINYINT NOT NULL DEFAULT 0 AFTER status;
-- order_type 语义变更,不修改列,仅注释
ALTER TABLE orders MODIFY COLUMN order_type TINYINT COMMENT '0纯RMB 1RMB+LSC混合';
-- status 扩展,不修改列,仅注释
ALTER TABLE orders MODIFY COLUMN status TINYINT COMMENT '0待支付 1已支付 2已发货 3已完成 4已取消 5退款中 6已退款 7部分退款';
```

### 4.5 lsc-promotion-service

#### 4.5.1 现状评估

V6.2 已实现一级直推 10%（`PromotionServiceImpl`），但奖励基数是**首单消费金额** × 10%。

V7.3 spec 5.1 要求：奖励 = 被推荐人**实际获赠积分** × 10%。**基数变更**。

#### 4.5.2 改造点

`PromotionService.notifyFirstOrder`：
- 入参从 `(consumerId, orderNo, orderAmount, orderStatus, refundAmount)` 改为 `(consumerId, orderNo, grantedLsc, orderStatus, refundAmount)`。
- 奖励计算：`reward = grantedLsc × 0.10`，向下取整。
- 调用 `ledger.promotionRewardLsc(referrerId, reward, orderNo)` 写入推荐人锁定池（type=6）。
- 失败挂账逻辑保留。

`PromotionService.rollbackReward`：
- 首单全额退款时，扣回已划转给推荐人的奖励。从推荐人**锁定池**扣回（V6.2 是从可用池扣回，V7.3 改为锁定池，因为 V7.3 奖励先入锁定池待释放）。

#### 4.5.3 测试

- `PromotionServiceImplTest` 修改：奖励基数从 `orderAmount` 改为 `grantedLsc`。
- 契约 `notifyFirstOrder.groovy` 重新打桩。

## 5. API 契约冻结（供前端对接）

本切片冻结以下对外 REST 契约，前端（C/D/E/F）按此对接：

### 5.1 商品（mall-service）

| 方法 | 路径 | 入参 | 返回 |
|---|---|---|---|
| GET | `/api/product/list` | `?page&size&categoryId&status` | `IPage<ProductVO>`（无 costPrice） |
| GET | `/api/product/{id}` | — | `ProductVO`（无 costPrice） |
| GET | `/api/category/list` | — | `List<ProductCategory>` |
| POST | `/api/hybridPay/calc` | `HybridPayCalcDTO` | `HybridPayDTO`（含 expectedGrantPoints） |

`ProductVO`：`{id, name, categoryId, price, grantPoints, stock, mainImage, description, salesCount, status}`

`HybridPayCalcDTO`：`{totalPrice, useLscAmount, maxAvailableLsc, grantPoints(按全额RMB), productItems(多商品)}`

`HybridPayDTO`：`{totalPrice, lscAmount, rmbAmount, expectedGrantPoints, deductionRateUsed}`

### 5.2 订单（order-service）

| 方法 | 路径 | 入参 | 返回 |
|---|---|---|---|
| POST | `/api/order/create` | `OrderCreateDTO` | `Order` |
| POST | `/api/order/pay` | `OrderPayDTO` | `Order` |
| POST | `/api/order/complete` | `{orderNo, operatorId}` | `Order` |
| POST | `/api/order/refund` | `OrderRefundDTO` | `Order` |
| POST | `/api/order/partialRefund` | `OrderRefundDTO` | `Order` |
| POST | `/api/order/cancel` | `{orderNo, operatorId}` | `Order` |
| GET | `/api/order/{orderNo}` | — | `Order` |
| GET | `/api/order/list` | `?pageNum&pageSize&userId&status` | `IPage<Order>` |

### 5.3 积分账本（ledger-service）

| 方法 | 路径 | 入参 | 返回 |
|---|---|---|---|
| GET | `/api/ledger/balance/{userId}` | — | `LscAccountVO` |
| GET | `/api/ledger/transactions` | `?userId&page&size&type&startDate&endDate&orderNo` | `IPage<LscTransaction>` |
| GET | `/api/ledger/availableDetails` | `?userId&page&size&status` | `IPage<AvailableLscDetail>` |
| GET | `/api/ledger/overview/{userId}` | — | `Map` |
| GET | `/api/ledger/recentTrend` | `?userId&days` | `List<Map>` |

`LscAccountVO`：`{userId, totalLocked, totalAvailable, totalFrozen, updatedAt}`

### 5.4 释放（release-service）

| 方法 | 路径 | 入参 | 返回 |
|---|---|---|---|
| GET | `/api/release/config` | — | `ReleaseConfigVO` |
| GET | `/api/release/summary/{userId}` | — | `{totalLocked, totalReleased, remainingLocked, expectedCompleteDate}` |
| POST | `/api/release/predict` | `{userId, days}` | `List<Map>` |

### 5.5 推广（promotion-service）

| 方法 | 路径 | 入参 | 返回 |
|---|---|---|---|
| POST | `/api/promotion/notifyFirstOrder` | `FirstOrderCheckDTO` | `RewardResultDTO` |
| GET | `/api/promotion/pending` | `?page&size&status` | `IPage<PromotionPending>` |

## 6. P0 合规硬底线落点清单

| P0 条 | 落点 | 验证 |
|---|---|---|
| 1 不可转让/兑现/提现 | ledger 删除 B2B/核销/跨账户接口 | 接口不存在的静态检查 + 单测 |
| 2 赠送绝对上限 100% 硬编码 | mall `ProductConstants.GRANT_RATE_ABSOLUTE_MAX = 1.00` | 常量是 `static final` 不可配置 |
| 3 商品上架自动算赠送积分 | mall `publishProduct` 中 `grantPoints = max(0, price-costPrice)` | 单测覆盖 5 场景 |
| 4 商品页面不展示进货价/利润率 | mall `ProductVO` 无 `costPrice` 字段 | DTO 字段静态检查 |
| 5 积分规则页不写计算规则 | 前端文案（子项目 C/E），后端 API 不返回计算规则 | — |
| 6 混合支付仅 RMB 部分赠送 | order `completeOrder` 中 `grantPoints × (rmbAmount/price)` | 单测覆盖全额/混合 3 场景 |
| 7 抵扣默认 20% 可配置 / 硬上限 50% | mall `DEDUCTION_RATE_DEFAULT=0.20` / `DEDUCTION_RATE_MAX=0.50` 硬编码 | 常量静态检查 |
| 8 有效期 365 天 | ledger `expireWriteoff` 扫描 `expire_date < today` | 单测覆盖 |
| 9 释放速率 0.03%-0.06% 硬编码 | release `ReleaseConfig.rate_max=0.0006` / `rate_min=0.0003` editable=0 | 已有 |
| 10 次日释放 | release 释放任务扫描 `release_start_date <= today` | 新增单测 |
| 11 幂等键+乐观锁 | ledger `idempotent_key` 唯一索引 + `@Version` | 已有 |
| 12 退款按消法 + LSC 退回 | order `refundOrder` / `partialRefund` | 单测覆盖 |
| 13 供应商仅 RMB 结算 | 子项目 B（供应链服务），本切片不涉及 | — |
| 14 风控前置：异常订单不赠送 | 子项目 B（风控服务），本切片预留 `freezeLsc` 接口 | — |
| 15 日终对账 | 子项目 B（对账服务），本切片预留 `dailySummary` 接口 | — |
| 16 释放周期+混合赠送规则告知消费者 | 前端文案（子项目 C/E） | — |
| 17 积分平台内通用 | ledger `deductLsc` 不绑定商品品类 | 接口无品类参数 |

## 7. 测试策略

- **单元测试**：每个服务每个公开方法至少 3 场景（正常 / 边界 / 异常）。覆盖率目标 ≥ 85%。
- **契约测试**：Spring Cloud Contract，每个对外接口至少 1 个 `.groovy` 契约 + 1 个 stub 测试。
- **集成测试**：5 服务联调，覆盖"下单→支付→完成→赠送→释放→抵扣→退款→回扣"全链路。
- **合规测试**：针对 P0 17 条硬底线各写 1 个合规测试用例。
- **回归**：保留现有 V6.2 测试中仍适用的部分（如乐观锁、幂等键、分布式锁）。

## 8. 风险与回滚

| 风险 | 缓解 |
|---|---|
| V6.2 历史 LSC 数据语义不一致 | 上线前执行附录 A 迁移脚本；迁移失败回滚 V6.2 |
| `payLsc` / `b2bTransfer` 被其他服务调用 | grep 确认无调用方（本切片前已扫描，仅 ledger 自身和测试） |
| 流水 `type` 值变更导致历史流水误读 | 迁移脚本一次性重映射，迁移后立即校验 |
| 释放任务次日触发逻辑错误 | 新增单测覆盖"T 日赠送当日不释放 / T+1 日释放" |
| 商品 `cost_price` 历史为 NULL | 迁移脚本统一回填为 0，供应商重新上架时填实际值 |

回滚：每个服务保留 `rollback_v73.sh`，按服务独立回滚。SQL 迁移用 `V7.3.0__rollback.sql` 反向操作。

## 附录 A：V6.2 → V7.3 流水类型迁移脚本

```sql
-- V7.3.0__ledger_type_migration.sql
-- 注意：先备份再执行。执行前需停服，避免迁移期间新流水写入。

-- 1. 新增 total_frozen 列
ALTER TABLE lsc_accounts ADD COLUMN total_frozen BIGINT NOT NULL DEFAULT 0 AFTER total_available;
ALTER TABLE lsc_transactions ADD COLUMN before_frozen BIGINT NOT NULL DEFAULT 0 AFTER after_available;
ALTER TABLE lsc_transactions ADD COLUMN after_frozen BIGINT NOT NULL DEFAULT 0 AFTER before_frozen;

-- 2. 流水 type 迁移（V6.2 → V7.3）
-- 顺序很重要：先迁移不冲突的，再处理冲突值
-- V6.2: 1=消费发行(保留为1) 2=每日释放 3=推广 4=商城消费 5=线下消费 6=过期转回 7=商家核销 8=B2B 9=退款退回
-- V7.3: 1=消费赠送入锁定 2=订单抵扣 3=退款退回 4=退款回扣 5=到期作废 6=推荐奖励 7=每日释放 8=风控冻结 9=风控解冻

-- 步骤2.1: 先把 V6.2 的 9(退款退回) 改成临时值 90,避免与 V7.3 的 3 冲突
UPDATE lsc_transactions SET type = 90 WHERE type = 9;
-- 步骤2.2: V6.2 的 6(过期转回) 改成 91,避免与 V7.3 的 5 冲突
UPDATE lsc_transactions SET type = 91 WHERE type = 6;
-- 步骤2.3: V6.2 的 4(商城消费) 改成 92,避免与 V7.3 的 2 冲突
UPDATE lsc_transactions SET type = 92 WHERE type = 4;
-- 步骤2.4: V6.2 的 3(推广奖励) 改成 93,避免与 V7.3 的 6 冲突
UPDATE lsc_transactions SET type = 93 WHERE type = 3;
-- 步骤2.5: V6.2 的 2(每日释放) 改成 94,避免与 V7.3 的 7 冲突
UPDATE lsc_transactions SET type = 94 WHERE type = 2;

-- 步骤2.6: 现在可以把临时值映射到 V7.3 终值
UPDATE lsc_transactions SET type = 3 WHERE type = 90;  -- 退款退回
UPDATE lsc_transactions SET type = 5 WHERE type = 91;  -- 到期作废(语义已变,从转回锁定改为作废,但历史流水保留)
UPDATE lsc_transactions SET type = 2 WHERE type = 92;  -- 订单抵扣
UPDATE lsc_transactions SET type = 6 WHERE type = 93;  -- 推荐奖励入锁定
UPDATE lsc_transactions SET type = 7 WHERE type = 94;  -- 每日释放

-- 步骤2.7: V6.2 的 5(线下消费)/7(商家核销)/8(B2B流转) 在 V7.3 已废弃
-- 保守处理:保留原值不动,不删除也不标记,前端按"未知类型"忽略展示
-- 这些类型在 V7.3 不再产生新流水,仅作为历史数据保留

-- 3. 可用明细 status 迁移
-- V6.2: 1有效 2过期转回 3已使用 4已核销 5退款退回
-- V7.3: 1有效 2已作废 3已使用 4退款退回
-- V6.2 的 4(已核销) 在 V7.3 不存在,迁移到 3(已使用)
UPDATE available_lsc_details SET status = 3 WHERE status = 4;
-- V6.2 的 5(退款退回) 迁移到 V7.3 的 4
UPDATE available_lsc_details SET status = 4 WHERE status = 5;
-- V6.2 的 2(过期转回) 在 V7.3 改为 2(已作废),语义已变,但值不变

-- 4. 验证:确认无遗漏
SELECT type, COUNT(*) FROM lsc_transactions GROUP BY type;
SELECT status, COUNT(*) FROM available_lsc_details GROUP BY status;
```

## 附录 B：变更文件清单

### ledger-service
- 改：[LscAccount.java](file:///workspace/lsc-ledger-service/src/main/java/com/lianshengtong/ledger/entity/LscAccount.java)（加 totalFrozen）
- 改：[LscTransaction.java](file:///workspace/lsc-ledger-service/src/main/java/com/lianshengtong/ledger/entity/LscTransaction.java)（type 注释 + 加 before/after_frozen）
- 改：[AvailableLscDetail.java](file:///workspace/lsc-ledger-service/src/main/java/com/lianshengtong/ledger/entity/AvailableLscDetail.java)（status 注释）
- 改：[LscLedgerService.java](file:///workspace/lsc-ledger-service/src/main/java/com/lianshengtong/ledger/service/LscLedgerService.java)（删 B2B/核销/payLsc/expireTransfer，加 grantLsc/deductLsc/refundDeductLsc/expireWriteoff/freezeLsc/unfreezeLsc/promotionRewardLsc）
- 改：[LscLedgerServiceImpl.java](file:///workspace/lsc-ledger-service/src/main/java/com/lianshengtong/ledger/service/impl/LscLedgerServiceImpl.java)（实现新接口）
- 改：[LscLedgerController.java](file:///workspace/lsc-ledger-service/src/main/java/com/lianshengtong/ledger/controller/LscLedgerController.java)（路由）
- 改：mapper xml 3 个（加 frozen 字段映射）
- 改：[LscAccountMapper.xml](file:///workspace/lsc-ledger-service/src/main/resources/mapper/LscAccountMapper.xml) / [LscTransactionMapper.xml](file:///workspace/lsc-ledger-service/src/main/resources/mapper/LscTransactionMapper.xml) / [AvailableLscDetailMapper.xml](file:///workspace/lsc-ledger-service/src/main/resources/mapper/AvailableLscDetailMapper.xml)
- 删：契约 `payLsc.groovy`
- 新增：契约 `grantLsc.groovy` / `deductLsc.groovy` / `expireWriteoff.groovy` / `freezeLsc.groovy` / `promotionRewardLsc.groovy`
- 改：单测 `LscLedgerServiceImplTest.java` / `LscLedgerServiceImplExtendedTest.java` / `LscAccountServiceImplTest.java`
- 新增：SQL `V7.3.0__ledger_v73_migration.sql`

### release-service
- 改：`ReleaseJobHandler.java`（次日释放扫描逻辑）
- 改：`ReleaseCalcServiceImpl.java`（如需基于 release_start_date 过滤）
- 新增：单测覆盖次日释放

### mall-service
- 改：[Product.java](file:///workspace/lsc-mall-service/src/main/java/com/lianshengtong/mall/entity/Product.java)（加 costPrice / grantPoints）
- 改：[ProductPublishDTO.java](file:///workspace/lsc-mall-service/src/main/java/com/lianshengtong/mall/dto/ProductPublishDTO.java)（加 costPrice）
- 改：[ProductServiceImpl.java](file:///workspace/lsc-mall-service/src/main/java/com/lianshengtong/mall/service/impl/ProductServiceImpl.java)（自动计算 grantPoints + 硬上限校验）
- 改：[ProductController.java](file:///workspace/lsc-mall-service/src/main/java/com/lianshengtong/mall/controller/ProductController.java)（返回 VO 不含 costPrice）
- 改：[HybridPayService.java](file:///workspace/lsc-mall-service/src/main/java/com/lianshengtong/mall/service/HybridPayService.java) / [HybridPayServiceImpl.java](file:///workspace/lsc-mall-service/src/main/java/com/lianshengtong/mall/service/impl/HybridPayServiceImpl.java)（加 expectedGrantPoints + 硬上限）
- 新增：`ProductConstants.java`（硬常量）
- 新增：`ProductVO.java`（对外 VO，无 costPrice）
- 改：单测 `ProductServiceImplTest.java` / `HybridPayServiceImplTest.java`
- 新增：SQL 迁移（products 表加列 + 营销配置预置）

### order-service
- 改：[Order.java](file:///workspace/lsc-order-service/src/main/java/com/lianshengtong/order/entity/Order.java)（加 grantedLsc / refundStatus）
- 改：[OrderCreateDTO.java](file:///workspace/lsc-order-service/src/main/java/com/lianshengtong/order/dto/OrderCreateDTO.java)（加 useLscAmount）
- 改：[OrderServiceImpl.java](file:///workspace/lsc-order-service/src/main/java/com/lianshengtong/order/service/impl/OrderServiceImpl.java)（completeOrder 算赠送 + refundOrder LSC 退回/扣回）
- 改：[OrderService.java](file:///workspace/lsc-order-service/src/main/java/com/lianshengtong/order/service/OrderService.java)（接口注释）
- 改：[LscLedgerFeignClient.java](file:///workspace/lsc-order-service/src/main/java/com/lianshengtong/order/feign/LscLedgerFeignClient.java)（加 grantLsc / deductLsc / refundLsc / refundDeductLsc 方法）
- 改：[PromotionFeignClient.java](file:///workspace/lsc-order-service/src/main/java/com/lianshengtong/order/feign/PromotionFeignClient.java)（notifyFirstOrder 签名变更）
- 改：单测 `OrderServiceImplTest.java` / `OrderServiceBranchTest.java`
- 改：契约 `createMallOrder.groovy`
- 新增：SQL 迁移（orders 表加列）

### promotion-service
- 改：[PromotionService.java](file:///workspace/lsc-promotion-service/src/main/java/com/lianshengtong/promotion/service/PromotionService.java)（notifyFirstOrder 签名变更）
- 改：[PromotionServiceImpl.java](file:///workspace/lsc-promotion-service/src/main/java/com/lianshengtong/promotion/service/impl/PromotionServiceImpl.java)（奖励基数改为 grantedLsc × 0.10）
- 改：[FirstOrderCheckDTO.java](file:///workspace/lsc-promotion-service/src/main/java/com/lianshengtong/promotion/dto/FirstOrderCheckDTO.java)（入参变更）
- 改：[LedgerFeignClient.java](file:///workspace/lsc-promotion-service/src/main/java/com/lianshengtong/promotion/feign/LedgerFeignClient.java)（加 promotionRewardLsc 方法）
- 改：单测 `PromotionServiceImplTest.java` / `PromotionServiceBranchTest.java`
- 改：契约 `notifyFirstOrder.groovy`

### 公共
- 改：[lsc-common](file:///workspace/lsc-common) 的 `LscTransactionTypeEnum`（枚举语义重定义）
- 新增：`marketing_config` 表初始化 SQL

## 附录 C：不在本切片范围

明确不在子项目 A 的内容：
- 供应链采购管理（lsc-b2b-service）→ 子项目 B
- AI 审核 Agent（lsc-ai-gateway）→ 子项目 B
- 风控前置（lsc-risk-service）→ 子项目 B（本切片仅预留 freezeLsc/unfreezeLsc 接口）
- 对账服务（lsc-reconciliation-service）→ 子项目 B
- 管理后台前端（lsc-admin-web）→ 子项目 C
- 供应商后台前端（lsc-merchant-web）→ 子项目 D
- 微信小程序 / 移动 APP → 子项目 E / F
- 媒体服务（lsc-media-service）/ 地图服务（lsc-map-service）/ 区块链存证（lsc-evidence-service）→ 暂不涉及
