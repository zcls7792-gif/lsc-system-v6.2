---
name: "springboot-approval-workflow"
description: "Spring Boot+MyBatis-Plus 审批工作流端到端落地：设计文档→Entity/Mapper/Service(状态机+双级锁)/Controller/Feign熔断/Properties/Metrics/SelfHealJob/ExceptionHandler/UnitTest→Jar→运维手册。用户提到审批流/会签/多级审批或要求新增改造审批模块时调用。"
---

# Spring Boot 审批工作流端到端落地 Skill

> **何时调用本 Skill**：用户提到「审批流」「会签 / 或签」「多级审批」「加个审批功能」「XX 模块需要走审批」「工作流 + 审批」，或要求在 Java/Spring Boot/MyBatis-Plus 项目中新开/改造一个审批相关模块时，第一步就加载本 Skill。

本 Skill 固化了一条**12 个阶段**的可复用流水线，从「需求澄清」到「上线运维手册」，避免常见错误：
1. 前后端 Controller 路由前后不一致 → 404（见经验）
2. 状态机边界漏写 → 能取消已经成功的审批单
3. `Map.of(null)` 空值 NPE、分布式锁降级缺失、并发 approvedCount 超卖
4. 设计文档缺 **DB 字段变更清单 + 接口 IDL 完整定义**，评审被打回
5. 直接从"代码实现"开始，最后倒推文档，遗漏架构图/时序图/错误码/回滚预案

---

## 📋 阶段总览（12 Steps Check）

| #   | Stage                        | 产出物（可复用模板见下方 §3）                               | 验收（DoD）                                                                 |
| --- | ---------------------------- | ------------------------------------------------------------ | --------------------------------------------------------------------------- |
| 1   | 需求澄清 & 边界冻结         | 决策问题清单（4+） + 用户确认                                 | 明确 flowType 全集 / 审批人来源 / 是否多级会签 / 执行端是谁 / 交付格式       |
| 2   | 代码事实校准                | 现有 6 类入口扫描结果 + 无歧义状态迁移表                    | 列出：写入点 / 进入条件 / 存储落点；对"死状态"标注「预留/不可达」            |
| 3   | 生成详细设计文档（Design Doc） | 15 章 + 2 附录 Markdown                                      | 含：架构图、状态机合法性矩阵、ER 图+索引策略、接口IDL、ADR、完整错误码     |
| 4   | 数据模型 & DDL               | 3 张表 Entity + Flyway SQL（Flow/Node/Audit）                | 索引策略含去重/待办/查询/审计时间线；DDL 执行后 SELECT 逐字段校验           |
| 5   | Controller / DTO / API 契约  | @RestController + Request/Response DTO + 参数校验            | Controller 路径 = 前端 API 前缀单一真源（§5.1），禁止前后端各自假设          |
| 6   | Service 实现 & 状态机 & 锁  | ServiceImpl（15+ 方法）+ 双级分布式锁 + DB 真实值复核        | 状态机方法前合法性检查；并发下 approvedCount 不超卖；JVM 锁做 Redisson 降级   |
| 7   | 跨服务执行端集成            | Feign Client + fallbackFactory + 执行编排                     | **绝不静默假成功**，fallback 返回 R.fail(503, …) 走 EXECUTE_FAILED         |
| 8   | 配置 / 告警 / 自愈任务       | @ConfigurationProperties + AlertChannel(Feishu) + XXL-JOB ×2 | GrayApprovalSelfHeal：EXECUTING 回查修复 + EXECUTE_FAILED ≤N 自动重试       |
| 9   | 观测 / 异常 / RBAC           | Micrometer 埋点(Counter/Gauge/Timer) + @RestControllerAdvice + 角色矩阵 | 异常映射到 §15 错误码；Metrics 前缀 gray_approval_*；角色矩阵 8 操作        |
| 10  | 单元测试（全覆盖）           | ≥8 条状态机 + 并发 + 边界 clip 用例                           | create clip / 2-approve→Succeeded / reject→409 / cancel 边界 / retry 边界 / 网关 5xx retry / 并发不超卖 / 非法迁移 |
| 11  | 最终打包 & JaCoCo             | Jar + 131 tests pass / 0 failures                             | `mvn test` 全部变绿；JaCoCo Service 包 line ≥ 80% 目标                       |
| 12  | 运维上线手册                 | 6 大章节 Runbook + 5 curl 冒烟 + 4 类回滚                    | Nacos/XXL-JOB 逐字段登记模板；K8s+VM 双发布模板；SQL+代码+配置三类回滚       |

---

## 🚀 阶段 1 — 需求澄清 & 边界冻结（强制）

**不做本阶段直接写代码 → 立即回退到这里重新来过。**
先用**最少决策问题**冻结 4 个以上关键问题：

```text
> 需求澄清问题（一次性问清）：
1. 本次审批覆盖的 flowType 有哪些？（如：参数变更/灰度毕业/放量/回滚/合同/费用）
2. 审批形态：并行或签（任一通过即过）/ 并行会签（全部通过）/ 串行会签（先A后B）？
3. 默认审批人来源：指定的 approvers 列表 / 按角色池 (ROLE_*) 先到先签 / 按部门经理逐级上报？
4. 审批通过后，"执行动作"调用哪个服务？执行失败后怎么办？（手动重试/自动 N 次/转人工）
5. 交付形态：飞书文档 + 画板 / 本地 Markdown / 只落地代码？（决定是否走 doc-create Skill）
```

用户明确答复后，本 Skill 后面所有阶段只在边界内做。

---

## 📐 阶段 2 — 代码事实校准（避免"死状态"误判）

当用户项目已有一套状态/Action 枚举或 FSM（有限状态机）时，**禁止仅根据枚举出现的条目就声称某路径"已生效 / 专属"**。

校验三要素：**写入点 + 进入条件 + 存储落点**

```text
校准模板（每个"看起来是可达状态"都回答）：
✍ 写入点：谁在什么方法/Handler 里给 status 赋了这个值？grep 确认。
🕹 进入条件：哪个 Action / API 调用会走到这条赋值？
💾 存储落点：赋值后写 draft / verified / history 哪一张表？

→ 三者缺任何一条 → 在设计文档中明确标注「预留/不可达」。
```

---

## 📑 阶段 3 — 详细设计文档结构（15 章 + 2 附录）

> 输出到 `{service-module}/docs/{xxx}-详细设计文档.md`，章节命名保持一致，便于跨项目评审对照。

| 章节 # | 标题                                       | 必含内容（不可省）                                                                                                   |
| ------ | ------------------------------------------ | -------------------------------------------------------------------------------------------------------------------- |
| 1      | 文档概述                                   | 目的 / 范围 / 读者对象                                                                                               |
| 2      | 总体架构设计                               | **C4 上下文图**（ASCII / Mermaid 均可）+ 模块职责矩阵（8~12 模块）+ 数据流向 ASCII                                   |
| 3      | 状态机设计                                 | 主流程 8 状态图 + 节点 4 状态 + **合法性迁移矩阵（✅×完整）**                                                         |
| 4      | 数据模型设计                               | **ER 图** + DDL（3 表）+ 索引策略（去重/待办/查询/审计时间线）+ ≥3 条 ADR 决策记录（字段为什么这么设计 / 权衡）        |
| 5      | 接口规范                                   | 7+ 个 REST API：**请求 JSON / 响应 JSON / 失败场景 3+ 条** + Feign Client 接口表 + Sentinel fallback                   |
| 6      | 核心流程 & 并发控制                        | 创建时序图 + **双级锁（Redisson fairLock → JVM ReentrantLock 降级）** + approvedCount DB count 真实值复核 伪代码     |
| 7      | 审计与合规                                 | 10+ Audit Action 全集 + **append-only / 事务 / 链上存证** 三大约束                                                   |
| 8      | 定时任务                                   | XXL-JOB Handler 2+ 条：调度巡检 / 自愈 / 僵尸扫描                                                                   |
| 9      | 安全与权限                                 | **RBAC 角色 × 操作 矩阵**（4 角色 × 7 操作）+ 越权二次防御                                                            |
| 10     | 可靠性与失败处理                           | ≥5 类失败点恢复手段 + 自愈任务（EXECUTING 卡死回查 + 自动重试）伪代码                                                |
| 11     | 可观测性                                   | ≥7 项 Metrics：Counter/Gauge/Timer + Grafana 建议 + MDC 规范（flowNo/policyId/operator）                              |
| 12     | 完整场景示例                               | 3+ 条场景时间线：正常通过 / 拒绝终止 / 网关超时手动重试                                                                 |
| 13     | 部署与配置                                 | application.yml 关键配置清单 + 滚动升级兼容步骤 + bypass 紧急降级开关                                                 |
| 14     | 测试要点                                   | 单测 ≥8 条 / 集成 3 条 / 故障演练 3 项                                                                                |
| 15     | 错误码全集                                 | 10+ 条：HTTP / code / message / 触发点 / 处理建议                                                                      |
| 附录 A | 关键代码导航                               | 每个文件绝对路径（file://…）                                                                                         |
| 附录 B | Roadmap / 演进方向                        | ≥6 项：串行会签 / 飞书卡片通知 / SLO 门禁 / LAUNCH 支持 / 链上存证 / 动态审批池                                        |

> ⚠️ 评审被打回高频原因：**缺少 4.1 DB 字段变更清单** 和 **5.1 接口 IDL 完整定义**。这两项必须以"可复制粘贴到接口平台"的精度写出来。

---

## 💾 阶段 4 — 数据模型（3 张审批主表模板）

**命名**：`{prefix}_approval_flow` / `{prefix}_approval_node` / `{prefix}_approval_audit`  
（prefix 替换为业务前缀，如 `param_`、`expense_`、`gray_`，避免跨项目同名冲突）

```text
flow（主流程）：
  id              BIGINT PK 自增
  flow_no         VARCHAR(32) UK  业务编号：{PREFIX}{yyyyMMdd}{6seq}
  flow_type       ENUM  全在 §1 决策中冻结
  policy_id / biz_id  VARCHAR   关联业务对象（灰度策略ID/费用单号…）
  payload_json    JSON     flowType 参数差异化承载（ADR-1：不要拆多个 column 空值）
  applicant       VARCHAR
  title / apply_reason
  status          ENUM(DRAFT/PENDING_APPROVAL/APPROVED/REJECTED/CANCELLED/EXECUTING/SUCCEEDED/EXECUTE_FAILED)
  required_approvals  TINYINT  [1..5]，用户传 clip 边界（0→1 / 6→5）
  approved_count / total_nodes   ← 冗余字段 + DB 真实 count 二次复核
  execute_response (MEDIUMTEXT)   ADR-2：容纳 gateway stacktrace 长文本
  execute_cost_ms  BIGINT
  approved_at / created_at / updated_at / updated_by

索引：pk / uk_flow_no / idx_biz_status / idx_applicant / idx_status_created

node（审批节点，1 flow ↔ N nodes）：
  id / flow_id(FK) / node_order / approver_role / approver
  node_status(WAITING/APPROVED/REJECTED/SKIPPED)
  comment / signature / decided_at

索引：idx_flow_order / idx_approver_status

audit（审计流水，append-only）：
  id / flow_id / flow_no / action(10+) / operator / detail_json
  chain_tx_hash 预留（合规链上存证）/ created_at

索引：idx_flow_id / idx_action_time / idx_flow_no

⚠ 所有 mapper 只暴露 insert+select，禁止 update/delete。Service 事务性保证 flow/node/audit 同生同灭。
```

---

## 🛰 阶段 5 — Controller / DTO / API 契约（单一真源）

**强制规则**：后端 `@RequestMapping("/api/{service}/{module}")` 的前缀 = 前端 axios 调用前缀 = 文档 §5 基路径，三者**完全等价**。每开一个 API，在 3 处同步更新，做不到就写脚本统一生成。

DTO 强制分 CreateRequest / ApproveRequest / CancelRequest / RetryRequest / Query / Detail，**不要复用 Request = Response**。

---

## ⚙ 阶段 6 — ServiceImpl 关键实现伪代码

```java
// 双级锁：优先 Redisson fairLock；getIfAvailable==null 降级 JVM ReentrantLock
try (AcquiredLock lock = tryLock(flowId)) {
  flow = selectById(flowId);
  checkStatus(flow.status, ALLOWED_OPERATIONS_MAP.get(action)); // → 状态机矩阵
  WAITING node = findFirstWaitingMatchingApprover();
  if (node == null) throw "no waiting node"; // → 409
  approveOrRejectOneNode(node);               // → set APPROVED/REJECTED, set approver
  if (REJECTED) → flow.status=REJECTED audit&return
  approved = REAL DB COUNT: select count(*) where flowId=X and APPROVED
  // 关键：不用内存里 +1，读库真实值防止超卖
  flow.approvedCount = approved;
  if (approved >= required) executeApprovedFlow(); // Feign 编排
}
```

**NPE 陷阱**：`Map.of(key, null)` 抛 NPE → 一律改用 `HashMap<>()` 塞 `rMap.put("data", r.getData())`（允许 null）。

---

## 🔌 阶段 7 — Feign 执行端集成 + Fallback

```java
@FeignClient(name="execution-service",
             fallbackFactory = ApprovalExecutionFallbackFactory.class)
```

**FallbackFactory 规则**：**不能静默 return R.ok()**，会出现"审批单 SUCCEEDED 但业务实际未执行"的假成功。必须返回 `R.fail(503, "fallback: ...")`，让审批单进入 EXECUTE_FAILED。

---

## 🧯 阶段 8 — 配置 + 告警 + 自愈（KPI）

1. **`@ConfigurationProperties(prefix="{prefix}.approval")`**：defaultRequired / approverRole / retryMax / staleExecutingSec / retryIntervalSec
2. **AlertChannel SPI**：
   - LoggingAlertChannel (default, `@ConditionalOnMissingBean`)
   - FeishuAlertChannel (`@ConditionalOnProperty(name="alert.channel", havingValue="feishu")`)
   - webhook-url 走环境变量注入，禁止明文 YAML
3. **GrayApprovalSelfHealJob XXL-JOB（每 5min）**：
   - **路径 A**：EXECUTING > staleExecutingSec → 调执行端 `policyStats` → 比对真实状态回填 SUCCEEDED / EXECUTE_FAILED
   - **路径 B**：EXECUTE_FAILED > retryIntervalSec → 次数 ≤ retryMax → 自动 retryExecute；超限 → 推送 **P0 告警**

---

## 🔍 阶段 9 — 观测 / 异常 / RBAC

### Metrics 命名：`{prefix}_approval_*`
- flow_created_total / node_decision_total / execute_fail_total / lock_contention_total / audit_write_total
- flow_status_total{status=8 状态} Gauge
- execute_latency_ms{flowType, success} Timer Histogram p95/p99

### 异常统一处理：`@RestControllerAdvice(basePackages=...)`
`IllegalStateException` → 409 Conflict / 精确识别 "not pending / no waiting node / cannot cancel" / `IllegalArgumentException` → 404 / `UnsupportedOperationException` → 500 LAUNCH 类型 / 参数校验 → 400。

### RBAC：4 角色 × 7 操作矩阵

| 角色                  | 查看详情 | 创建 | 审批 | 撤销本人 | 撤销全部 | 重试执行 | forceStatus |
| --------------------- | :------: | :--: | :--: | :------: | :------: | :------: | :---------: |
| ROLE_USER             |   仅本人  |  ✅  |  ❌  |    ✅    |    ❌    |    ❌    |      ❌     |
| ROLE_{PREFIX}_VIEWER  |    全部   |  ❌  |  ❌  |    ❌    |    ❌    |    ❌    |      ❌     |
| ROLE_{PREFIX}_ADMIN   |    全部   |  ✅  |  ✅  |    ✅    |    ❌    |    ✅    |      ❌     |
| ROLE_SUPER_ADMIN      |    全部   |  ✅  |  ✅  |    ✅    |    ✅    |    ✅    |  仅测试/排障 |

---

## 🧪 阶段 10 — 单元测试（8 条最小覆盖用例）

在 `{service}/src/test/java/.../service/impl/` 创建 `{Prefix}ApprovalServiceStateMachineTest.java`：

```text
1) create_basicAndClip              flowNo 前缀 / 0→1 / 6→5 / default=配置值
2) approve_twoApprovals → Succeeded 1/2 PENDING, 2/2 → APPROVED → SUCCEEDED + response/ms ≠ null
3) reject_thenSecondApproveFails     第一人拒绝→REJECTED → 第二人审批抛 409
4) cancel_boundaries                 DRAFT/PENDING OK → 再次撤销 CANCELLED → 抛错
5) retryExecute_boundaries           PENDING retry → not retryable status
6) retryExecute_failThenSuccess      gateway 5xx → EXECUTE_FAILED → retry → SUCCEEDED（注意 Map.of(null) 陷阱已修复）
7) concurrentApprove_noOverSell      Threads=4 × required=2 → success ∈ [1,2]；success+fail=4；绝不超卖
8) illegalTransitions_matrix         SUCCEEDED retry → 抛错 / REJECTED cancel → 抛错
```

> 并发用例断言**不要**写死 successCount==2（偶发线程时序导致 1+3 也合法），改软约束：`s∈[1,2] && s+f==4 && approvedCount<=2`。

---

## 📦 阶段 11 — 打包 & 验证最终命令

```bash
cd /workspace
# ① 编译+测试-编译（含 lsc-common 依赖模块）
mvn -f pom.xml -pl {service-module} -am compile test-compile -DskipTests

# ② 专跑 Approval 状态机 8 条测试 3 轮 防 flaky
for i in 1 2 3; do mvn -f pom.xml -pl {service-module} test \
  -Dtest={Prefix}ApprovalServiceStateMachineTest -DfailIfNoTests=false; done

# ③ 全量回归（本 service 所有 test）
mvn -f pom.xml -pl {service-module} test
# ✅ 验收：Tests run: N, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS

# ④ 打 jar
mvn -f pom.xml -pl {service-module} -am package -DskipTests -q
# ✅ 验收：ls -lh target/*.jar → 120M+ 量级
```

---

## 🛠 阶段 12 — 运维上线 Runbook 6 大章模板

输出到 `{service-module}/docs/{xxx}-上线运维执行手册.md`，结构如下：

```
🔴 T-1 准备      6 交付物 + Nacos 配置模板 + XXL-JOB 3 任务登记卡 + 15 env var
🟠 T-Day 执行    6 Steps Step0变更会议/Step1 Flyway+手跑+校验SQL/Step2 Nacos发布/Step3 滚动/Step4 任务首跑/Step5 冒烟/Step6 观察30分钟
🟡 冒烟 5 curl    创建 → 1/2 审批 → 2/2 审批 → retryExecute → 详情；+2 场景：放量/回滚
🔵 回滚 4 类      A 代码回滚(k8s undo + systemd) / B 配置(Nacos 历史版本+紧急降级键) / C DDL(RENAME 3 表, 7 天保留) / D XXL-JOB 停用
🟣 观察 7 指标    Day0-Day30: SelfHeal/创建量/拒绝率/p99延迟/锁冲突率/审计完整性
🟤 错误码速查 10+ 常见 409/404/503/500 排查路径
```

---

## ⚠️ 常见失败 & 规避速查（踩坑总结）

| #  | 陷阱                                                        | 后果                                             | 规避方法（已固化进 Skill）                                             |
| -- | ----------------------------------------------------------- | ------------------------------------------------ | ---------------------------------------------------------------------- |
| 1  | 枚举里看到某状态直接宣称"已生效专属路径"                    | 被追问写入点时查不到 → 结论回滚                 | 阶段 2：写入点 + 进入条件 + 存储落点 三要素缺一不可标注「预留/不可达」  |
| 2  | 设计文档缺 DB 字段清单 + 接口 IDL                           | 评审被打回                                       | 阶段 3 章节 4.1/5.1 强制，没写不算通过                                |
| 3  | 前端 API 前缀与 @RequestMapping 不一致                     | 404 / 代理不命中                                 | 阶段 5：单一真源，3 处同步；每开一 API 3 处改                          |
| 4  | `Map.of(key, null)` 存 gateway 返回 data==null             | 生产 NPE 栈直接打到用户 / CI 50% 通过率         | 阶段 6：HashMap 安全塞值                                                |
| 5  | FallbackFactory 返回 R.ok() 伪装成功                       | 审批假成功 SUCCEEDED 但业务没执行（极严重事故） | 阶段 7：强制 R.fail(503,…)                                             |
| 6  | approvedCount 仅靠内存 +1 不查 DB                          | 并发下超卖：2 人审批计数 = 3 / 4                 | 阶段 6：DB 真实值 SELECT COUNT 二次复核                                |
| 7  | 并发断言 successCount==2 写死                              | Flaky 测试，CI 间歇性失败                        | 阶段 10：软约束 s∈[1,2] + s+f==线程总数                                |
| 8  | 用户说"可以/好的/执行吧"直接进入部署/DDL 覆盖              | 范围不明的高风险操作（经验 2118999）              | 高风险动作前二次冻结能力边界：能做/需用户凭据做                        |
| 9  | 复用 Skill 时表名用通用 `approval_*` 与宿主项目冲突        | Flyway 迁移冲突                                  | 阶段 4：可配置前缀 `{prefix}_approval_*`                               |
| 10 | 未检查 `@MapperScan` 范围直接写 Mapper                     | 启动注入失败，Mapper 找不到 Bean                  | 阶段 4：同步检查 @MapperScan basePackage                               |

---

## 🧾 Skill 使用 Checklist（每次调用本 Skill 最后勾选一遍）

```text
☐ 阶段1 需求澄清问题问过，用户明确答复
☐ 阶段2 状态枚举 三要素校准完毕，死状态标注清楚
☐ 阶段3 设计文档 15+2 章节齐全，含 ER 图 + 状态矩阵 + 错误码
☐ 阶段4 DDL：flow/node/audit 3 表 + 5 类索引 + ADR 记录
☐ 阶段5 Controller 路径 = 前端 axios 基路径 = 文档路径 三相等价
☐ 阶段6 双级锁 + DB COUNT 真实值复核
☐ 阶段7 FallbackFactory R.fail(503,…) 绝不假成功
☐ 阶段8 SelfHealJob EXECUTING 卡死回查 + 超限 P0 告警
☐ 阶段9 ExceptionHandler 映射错误码；Metrics 6+ 项；RBAC 4×7 矩阵
☐ 阶段10 8 条状态机测试通过，并发 3 轮无 flaky
☐ 阶段11 Tests all green + Jar 产物生成
☐ 阶段12 Runbook 6 章完成，4 类回滚有精确步骤
```

---

*本 Skill 为当前 workspace 专用（springboot-approval-workflow）。
复用步骤：下次用户提到「审批流 / 工作流审批 / 会签」时，先调用本 Skill → 按 Checklist 12 steps 顺序推进即可。*
