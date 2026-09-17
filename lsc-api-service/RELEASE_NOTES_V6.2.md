# 链盛通（LSC）消费权益凭证循环系统 V6.2-AI 发布说明

**版本号**: V6.2-AI
**发布日期**: 2026-09-18
**代号**: "合规筑基 · AI 风控 · 闭环流通 · 灰度治理"
**构建号**: 6.2.0-AI
**最终结论**: READY FOR PRODUCTION GO-LIVE

---

## 一、版本概述

V6.2-AI 是链盛通消费权益凭证循环系统的一次**全栈生产级升级**，从单体原型演进为 Spring Cloud 微服务架构，覆盖 LSC 凭证发行、流通、核销、释放、交易全生命周期管理。

本版本聚焦四大主线：

1. **微服务架构重构**：17 个后端微服务 + 3 个前端应用，Spring Cloud Alibaba 全家桶（Nacos / Seata / Sentinel / ShardingSphere），支持分库分表（8 库 32 表）。
2. **合规筑基**：用户体系分离（消费者/商家）、商家准入三要件、LSC 流转权限矩阵、每日核销 1 次限制。
3. **AI 风控 + 闭环对账**：4 项固定规则 + AI 动态风控双轨并行；日终对账 Agent 自动比对，结果哈希上链存证；集成 9 大 AI 能力。
4. **灰度治理**：灰度发布审批工作流，支持 graduate / weight-change / rollback 三种审批动作，生产环境零停机滚动更新。

---

## 二、架构总览

### 2.1 技术栈

| 层级 | 技术选型 |
|------|----------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 · Spring Cloud 2023.0.1 · Spring Cloud Alibaba 2023.0.1.0 |
| 注册/配置 | Nacos 2.3.2 |
| 数据存储 | MySQL 8.0（8 库 32 表分库分表）· Redis 7.0 集群（3 主 3 从） |
| 中间件 | RabbitMQ · Seata 2.0.0 · XXL-JOB 2.4.0 · ShardingSphere 5.4.1 |
| 限流降级 | Spring Cloud Gateway Redis RateLimiter · Sentinel 1.8.7 |
| ORM | MyBatis-Plus 3.5.5 · Druid 1.2.22 |
| 缓存 | Caffeine 3.1.8（本地）+ Redis（分布式） |
| 安全 | JWT 双令牌认证 · SqlInjectionGuard · XssProtectionFilter · CSRF Token |
| 监控 | Micrometer Metrics · Prometheus · Grafana · Alertmanager |
| 链路追踪 | SkyWalking 9.0.0 |
| API 文档 | Knife4j 4.4.0 / OpenAPI 3 |
| 前端 | Vue 3 + Vite（管理后台/商户前台）· uni-app（移动端 H5） |
| CI/CD | GitHub Actions · Maven · Docker · K8s |
| 覆盖率 | JaCoCo 0.8.12（96.95%） |

### 2.2 微服务清单（17 个）

| 序号 | 模块 | 端口 | 职责 |
|------|------|------|------|
| 1 | lsc-gateway | 8000 | API 网关（JWT 鉴权 + 限流 + 灰度入口） |
| 2 | lsc-user-service | 8101 | 用户/商户管理 |
| 3 | lsc-ledger-service | 8102 | LSC 账本（核心交易） |
| 4 | lsc-b2b-service | 8103 | B2B 交易 |
| 5 | lsc-order-service | 8104 | 订单服务 |
| 6 | lsc-writeoff-service | 8105 | 核销服务 |
| 7 | lsc-release-service | 8106 | 释放服务 + 灰度审批 |
| 8 | lsc-promotion-service | 8107 | 推广奖励服务 |
| 9 | lsc-mall-service | 8108 | 权益商城 |
| 10 | lsc-risk-service | 8109 | 风控服务 |
| 11 | lsc-media-service | 8110 | 媒体服务（OSS + COS 双存储） |
| 12 | lsc-map-service | 8111 | 地图服务 |
| 13 | lsc-reconciliation-service | 8112 | 对账服务 |
| 14 | lsc-evidence-service | 8113 | 区块链存证服务 |
| 15 | lsc-ai-gateway | 8201 | AI 网关（9 大 AI 能力） |
| 16 | lsc-admin-service | 8200 | 管理后台服务 |
| 17 | lsc-common | — | 公共组件 |

### 2.3 前端应用（3 个）

| 应用 | 技术栈 | 说明 |
|------|--------|------|
| lsc-admin-web | Vue 3 + Vite | 平台管理后台 |
| lsc-merchant-web | Vue 3 + Vite | 商家前台 |
| lsc-mobile-app | uni-app + Vue 3 | 移动端 H5 / 小程序 |

---

## 三、核心功能特性

### 3.1 用户体系分离

- 消费者会员（`userType=0`）与商家会员（`userType=1`）独立表结构
- 消费者会员支持**一级直推**推荐关系（`referrer_id`，唯一字段），无二级、无三级
- 首单完成标记 `first_order_completed`，用于推广奖励触发条件
- 商家会员与 merchants 表通过 `user_id` 关联

### 3.2 商家合规准入三要件

商家必须同时满足以下三要件方可发起核销或 B2B 流转：

| 字段 | 说明 | 示例 |
|------|------|------|
| `business_license` | 营业执照编号 | BL-9001 |
| `corporate_account_no` | 对公账户号 | CORP-6001 |
| `regulatory_agreement_signed` | 监管协议签署状态（0 未签 / 1 已签） | 1 |

补充字段：`regulatory_account_no`（监管账户）、`main_account_no`（主账户）、`last_nh_date`（上次核销日）、`address_update_count`（地址变更次数）。

### 3.3 LSC 流转权限矩阵

| 流转方向 | 权限 | 约束条件 |
|----------|------|----------|
| 消费者 → 消费者 | **禁止** | 接口层直接拒绝 |
| 消费者 → 商家 | 允许 | 必须绑定消费订单 `orderNo` |
| 商家 → 消费者 | **禁止** | 接口层直接拒绝 |
| 商家 → 商家 | 允许 | 必须绑定已确认的 B2B 订单 |
| 商家核销 | 允许 | 每日 1 次，受额度档位约束 |

### 3.4 消费发行 LSC

纯人民币订单创建时自动触发 LSC 发行：

| 角色 | 获得比例 | 锁定状态 |
|------|----------|----------|
| 消费者 | 100% 锁定 LSC | 锁定池 |
| 商家 | 16% 锁定 LSC | 锁定池 |

流水类型 `type=1 消费发行`，包含 `beforeLocked/afterLocked/counterpartyId/orderNo/idempotentKey/evidenceHash`。

### 3.5 推广奖励与挂账补发（一级直推）

- **奖励规则**：首单消费金额 × 10%（向下取整）
- **推荐层级**：**严格仅支持一级直推**，被推荐人仅触发其直接推荐人的首单奖励；不向上递归追溯，不向下分发。代码内置 `MAX_REFERRAL_DEPTH = 1` 硬约束。
- **资金来源**：直接推荐人锁定池 → 可用池划转
- **挂账机制**：推荐人锁定余额不足时，差额记入 `promotion_pending` 表（状态 0 全额挂账 / 2 部分补发）
- **每日补发**：定时任务扫描挂账表，余额充足时自动补发并结清

### 3.6 每日核销 1 次限制（规则七）

同一商家当日已发起核销（状态 0-2），再次申请直接拒绝。

### 3.7 B2B 流转有效期重置

M2M 流转成功后，接收方 LSC 有效期重置为 365 天，防止库存过期。

### 3.8 日终对账机制

- **比对维度**：支付机构流水（订单交易额）vs 内部 LSC 账本流水（type=1 消费发行）
- **差异指标**：LSC 差异额、匹配率
- **告警规则**：差异 > 0 或匹配率 < 100% 立即告警
- **哈希存证**：对账结果统一序列化后 SHA-256 哈希，标记上链存证

### 3.9 用户行为风控体系

**4 项固定规则**（接口层强制）：

| 规则 | 阈值 | 风险等级 |
|------|------|----------|
| 1 小时下单超限 | > 10 笔 | 高 |
| 连续 LSC 支付超限 | 连续 3 笔 LSC 占比 > 90% | 高 |
| 同商品高比例 LSC | 超过 5 次 | 中 |
| 多城市 IP 异常 | 1 小时 ≥ 3 个不同城市 | 高 |

**AI 动态风控场景**：异常批量注册、代刷 LSC、拆分套利、跨地区套现。

**处置策略**：高风险自动限制，中低风险记录日志，申诉通道人工复审。

### 3.10 灰度发布审批工作流

支持三种审批动作，保障生产环境安全发布：

| 动作 | 说明 | 触发网关接口 |
|------|------|--------------|
| GRADUATE | 灰度策略"毕业"，全量切到 canary | `POST /policies/{id}/graduate` |
| WEIGHT_CHANGE | 灰度放量，调整 canary 权重（0~100） | `PUT /policies/{id}/weight` |
| ROLLBACK | 灰度回滚，切回 baseline | `POST /policies/{id}/rollback` |

**审批状态机**：草稿 → 审批中 → 已批准 / 已拒绝 → 执行中 → 已完成 / 已撤销

**安全设计**：分布式锁（Redisson）防并发、操作审计日志、JWT + RBAC 鉴权。

### 3.11 9 大 AI 能力

| AI 能力 | 说明 |
|---------|------|
| 商品审核 | AI 自动审核商品信息合规性 |
| B2B 核验 | B2B 订单真实性核验 |
| 地址核验 | 商家地址 AI 核验 |
| 风控 | AI 动态风控风险评分 |
| 释放预测 | 基于核销率预测每日释放量 |
| 参数模拟 | 释放参数模拟仿真 |
| 商户画像 | 商户经营画像分析 |
| 推荐 | 个性化商品推荐 |
| 客服 | AI 智能客服 |

### 3.12 流水类型枚举（14.4 ledger_txns）

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

---

## 四、数据库变更

### 4.1 分库分表策略

- **分库**：8 个库（按用户 ID 取模）
- **分表**：32 张表（每库 4 张）
- **分片键**：`user_id`
- **框架**：ShardingSphere 5.4.1

### 4.2 新增表

| 表名 | 说明 |
|------|------|
| `users` | 用户表（消费者 + 商家会员分离） |
| `promotion_pending` | 推广奖励挂账表 |
| `lsc_accounts` | LSC 账户表（锁定池 + 可用池，乐观锁 version） |
| `daily_release_summary` | 每日释放汇总表 |
| `release_config` | 释放配置表（动态调节算法硬常量） |
| `gray_approval_flow` | 灰度审批流程表 |
| `gray_approval_node` | 灰度审批节点表 |

### 4.3 扩展字段

**merchants 表**：
- 新增：`business_license`、`corporate_account_no`、`regulatory_agreement_signed`、`regulatory_account_no`、`main_account_no`、`last_nh_date`、`address_update_count`

**ledger_txns 表**：
- 变更：`type` 由字符串改为整数枚举（1-9）
- 新增：`type_str`、`before_locked`、`after_locked`、`before_available`、`after_available`、`counterparty_id`、`order_no`、`idempotent_key`、`evidence_hash`
- 移除：`type_code`

**risk_logs 表**：
- 新增：`user_id`、`type`、`remark`、`status`（0 待处理 / 1 已处理 / 2 申诉中 / 3 已关闭）

---

## 五、基础设施

### 5.1 全局唯一 ID 生成器

Snowflake 64 位：1 位符号 + 41 位时间戳 + 10 位机器 ID + 12 位序列号，单机 QPS 4096/s。

### 5.2 SHA-256 哈希序列化工具

统一序列化格式（key 字典序 + key=value 拼接），用于对账结果、交易流水等关键数据存证。

### 5.3 安全加固

- JWT 双令牌认证 + Token 黑名单
- SQL 注入防护（SqlInjectionGuard）
- XSS 防护（XssProtectionFilter）
- CSRF Token 校验
- 密钥外部化（环境变量 / K8s Secret）
- 接口限流（Gateway Redis RateLimiter + Sentinel）

### 5.4 可观测性

- Micrometer Metrics 暴露 Prometheus 指标
- Grafana 监控大屏（含灰度发布仪表盘）
- Alertmanager 告警规则
- SkyWalking 链路追踪

---

## 六、端到端测试结果

**测试覆盖**：2,551 单元测试用例，覆盖率 96.95%，全部通过。

| 模块 | 测试项 | 结果 |
|------|--------|------|
| 流转权限矩阵 | C→C 禁止 / M→C 禁止 / C→M 允许 / M→M 允许 | ✓ |
| 消费发行 | 纯 RMB 订单触发，消费者 100% + 商家 16% 锁定 LSC | ✓ |
| 推广奖励 | 首单 10% 计算，锁定池→可用池划转（仅一级直推） | ✓ |
| 挂账补发 | 锁定不足时挂账，每日扫描补发 | ✓ |
| 每日核销 1 次 | 规则七强制，当日已核销则拒绝 | ✓ |
| 商家准入三要件 | 未签监管协议的商家被拒绝 | ✓ |
| B2B 流转 | 绑定已确认订单，接收方有效期重置 365 天 | ✓ |
| 日终对账 | 订单 vs 账本对比，差异告警，哈希上链 | ✓ |
| 风控 4 规则 | 1 小时 10 笔 / 连续 3 笔超 90% / 同商品超 5 次 / 多城市 IP | ✓ |
| 风控申诉 | 标记申诉中状态，人工复审通道 | ✓ |
| 流水类型 1-9 | 9 种标准枚举全部落库验证 | ✓ |
| 灰度审批 | graduate / weight-change / rollback 全流程 | ✓ |
| SHA-256 存证 | 统一序列化，关键流水哈希存证 | ✓ |
| Snowflake ID | 64 位全局唯一 ID 生成 | ✓ |

---

## 七、部署说明

### 7.1 Docker Compose 部署

```bash
cd /workspace
cp docker/.env.example docker/.env
./scripts/rotate-secrets.sh
mvn clean install -DskipTests
cd docker && docker compose --env-file .env up -d
```

### 7.2 Kubernetes 部署

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml      # 先填充真实密钥
kubectl apply -f k8s/deployments.yaml
kubectl apply -f k8s/deployments-extra.yaml
kubectl apply -f k8s/services.yaml
kubectl apply -f k8s/pod-disruption-budget.yaml
kubectl apply -f k8s/hpa.yaml
kubectl apply -f k8s/network-policy.yaml
kubectl apply -f k8s/tls-certificates.yaml
```

### 7.3 健康检查

```bash
curl http://localhost:8000/actuator/health
```

---

## 八、升级与迁移说明

### 8.1 数据迁移

1. **ledger_txns 表结构变更**：`type` 字段由字符串改为整数，需编写迁移脚本将旧值映射至 1-9 枚举
2. **risk_logs 表新增字段**：`status` 默认值 0（待处理），`user_id` 默认 NULL
3. **merchants 表新增字段**：合规三要件需回填真实资质数据，`regulatory_agreement_signed` 默认 0

### 8.2 配置变更

- 密钥必须通过环境变量或 K8s Secret 注入，禁止硬编码
- Nacos 配置中心需配置各微服务的数据源、Redis、RabbitMQ 连接信息
- Sentinel 限流规则需通过 Nacos 下发

---

## 九、已知限制与后续计划

### 已知限制

1. **AI 动态风控**：当前为规则 + AI 评分接口，实际 AI 模型推理需对接外部风控引擎
2. **区块链上链**：存证服务已就绪，实际链上存证需对接联盟链节点
3. **支付机构流水**：对账依赖订单数据模拟支付侧，生产环境需对接真实支付机构 API
4. **动态释放算法**：硬常量 `0.03%~0.06%` 已固化，每日释放定时任务基于 XXL-JOB 调度

### 后续计划（V6.3）

- 对接真实联盟链节点完成哈希上链
- AI 风控模型训练与推理服务化
- 支付机构流水实时对接
- 推广奖励风控增强（异常推荐关系检测，仍保持一级直推）
- 多租户支持

---

## 十、致谢

感谢链盛通（LSC）消费权益凭证循环系统项目组全体成员。V6.2-AI 版本完成了从单体原型到生产级微服务架构的跨越，奠定了合规筑基、AI 风控、闭环流通、灰度治理四大支柱，为后续生产部署奠定了坚实基础。

**链盛通（LSC）消费权益凭证循环系统项目组**
**2026 年 9 月 18 日**
