# lsc-release-service · 灰度审批工作流（V6.2.0）

> 发布域服务：提供「灰度审批」「灰度权重变更」「灰度毕业/回滚」的全链路能力。
> V6.2.0 新增 **审批工作流**：双审批人+自愈+审计+飞书告警+Redisson 双锁+XXL-JOB 兜底+Rollback 脚本。

---

## 一、快速开始

| 动作 | 命令 / 入口 |
|---|---|
| 构建 | `mvn -f /workspace/lsc-release-service -T 1C -DskipTests clean package` → `target/lsc-release-service.jar` |
| 本地（sandbox profile，最小环境 MySQL+Redis，关闭 Nacos/Seata/XXL-JOB/RabbitMQ）| 见 [P2 冒烟脚本 05-smoke.sh](deploy/v6.2.0-gray-approval/scripts/05-smoke.sh) / `runbook` 章节 |
| 生产发布全流程 | [灰度审批工作流-上线运维执行手册](docs/灰度审批工作流-上线运维执行手册.md) |
| 生产交付物包（可直接下发 SRE/DBA）| `deploy/*.tgz`，最近版本见目录下最新 `V6.2.0-GRAY-APPROVAL-build-*-PRODUCTION-PKG.tgz` |

---

## 二、V6.2.0 新增：审批工作流

### 2.1 状态机

```
DRAFT → PENDING_APPROVAL ──全部批准──► APPROVED ──执行(可空)──► EXECUTING ──► SUCCEEDED
       │                      │                                         │
       │                      └──任一拒绝──► REJECTED                   └──失败──► EXECUTE_FAILED
                                              │
                                              (可 DRAFT 重建)
```

节点状态：`WAITING → APPROVED / REJECTED / SKIPPED`

### 2.2 审批人规则

| 配置 | 默认值 | 说明 |
|---|---|---|
| `gray.approval.default-required-approvals` | `2` | 除超管外，至少需要 N 位具备 `approver-role` 的人依次签字 |
| `gray.approval.approver-role` | `ROLE_RELEASE_ADMIN` | 具备审批权限的 RBAC 角色（建议与 UAC/LDAP 对齐）|
| `gray.approval.min-super-admin-bypass` | `SUPER_ADMIN` | 若申请人为 SUPER_ADMIN（或由白名单账号触发），可跳过规则 1 人直签 |

### 2.3 入口 API（相对 `${BASE}/lsc-release`）

| Method | Path | 说明 | 典型 Body/Query |
|---|---|---|---|
| `POST` | `/api/release/gray/approvals` | 创建审批单 | `{flowType, policyId, applicant, requiredApprovals, title}` |
| `PUT`  | `/api/release/gray/approvals/action/approve` | 签字（同意 / 拒绝）| `{flowId, approver, approved, comment}` |
| `GET`  | `/api/release/gray/approvals?pageNo=1&pageSize=10` | 分页查询 | 可选 `flowType / status / policyId / applicant` |
| `GET`  | `/api/release/gray/approvals/{flowId}` | 详情（含 auditRecords）| — |
| `POST` | `/api/release/gray/approvals/{flowId}/cancel` | 取消（仅 DRAFT/PENDING_APPROVAL）| — |

### 2.4 并发 & 一致性保障

- **双锁**：Redisson `redissonClient.getFairLock("ga:flow:" + flowId)` + JVM `ConcurrentHashMap` ReentrantLock 本地兜底
  - Redisson 失败自动降级 JVM 锁（多实例下不保证全局但至少防热点单实例并发）
  - 锁 wait=3s, hold=15s，异常自动释放（看门狗 extend）
- **事务**：MyBatis-Plus 操作统一 `@Transactional(rollbackFor=Throwable)`
- **幂等**：重复签字 / 重复 create（同 policyId + 同 flowType + 同 applicant 且 PENDING_APPROVAL 已存在）返回提示而非 5xx

### 2.5 自愈与过期清理（XXL-JOB，3 任务）

| Handler | 默认 Cron | 职责 |
|---|---|---|
| `grayAuditJob` | `0 0/10 * * * ?` | 校验 audit 记录完整性；缺失时从 flow/node 反推补齐并告警 |
| `grayApprovalSelfHealJob` | `0 0/5 * * * ?` | 找 `EXECUTING > 120s` 的单，依据实际策略调用执行结果回写；重试上限 3 次 → 置 EXECUTE_FAILED 告警 |
| `grayStalePolicyEnforceJob` | `0 30 3 * * ?` | 夜 3:30 清理 DRAFT 超 7 天 / 最终态超 180 天的审批单（归档到 `gray_approval_audit_archive`） |

### 2.6 告警（默认飞书，不可用时降级 LOGGING）

- 通道：`alert.channel=feishu`
- 配置：`ALERT_FEISHU_WEBHOOK` 环境变量 或 `alert.feishu.webhook-url` Nacos
- 告警场景：签字拒绝 / EXECUTE_FAILED / 自愈超限 / 锁 p95 > 500ms / 审计缺口 > 0
- 降级：`FeishuAlertChannel.send()` 抛任意异常 → `LoggingAlertChannel` 立即补打 WARN，保证可追溯

---

## 三、数据库对象（3 表 + 12 索引 + release_config）

| 表 | 说明 | 主键 | 关键索引 |
|---|---|---|---|
| `gray_approval_flow` | 审批单主表 | `id` AUTO_INCREMENT | `idx_status_created`, `idx_policy_flowtype_status`, `idx_applicant_created` |
| `gray_approval_node` | 审批节点（每人一条）| `id` | `uk_flow_approver` UNIQUE, `idx_flow_status` |
| `gray_approval_audit` | 全量审计日志 | `id` | `idx_flow_created`, `idx_action_created` |
| `release_config` | 动态灰度参数（`rate_max/min`、`k_min/max`、`alpha`）| `config_key` | 主键天然 |

DDL：[V6.2.0__gray_approval_flow.sql](src/main/resources/db/migration/V6.2.0__gray_approval_flow.sql) / 回滚：同目录或 tgz 内 `sql/ROLLBACK-DROP-V6.2.0-gray-approval.sql`

---

## 四、回滚 Runbook（≤5 分钟恢复）

| 场景 | 操作 |
|---|---|
| 应用新包启动失败（K8s 金丝雀）| `scripts/04b-k8s-deploy.sh rollback` → 回到上一个 ReplicaSet，灰度入口 service 指向 `previous` |
| 应用新包启动失败（VM systemd）| `scripts/04a-vm-deploy.sh rollback` → 从 `./backup/` 恢复 jar 并 `systemctl restart lsc-release` |
| 审批逻辑异常（误通过/误拦截）| 配置项临时关：Nacos `gray.approval.workflow-enabled=false` 热加载 → 全部放行或按业务开关切「回退模式」；再配合 `stale-executing-seconds=0` 让自愈快速收敛 |
| 数据库 DDL 需退回 | 执行 `sql/ROLLBACK-DROP-V6.2.0-gray-approval.sql`（注意顺序 audit→node→flow），release_config 可选保留 |
| XXL-JOB 误触发告警风暴 | Nacos `alert.silence-seconds=3600` 或 `alert.channel=logging` 立刻静默；再逐任务 `stop` |

---

## 五、FAQ

**Q1：审批人数可以超过默认 2 人吗？**
可以。创建单时传 `requiredApprovals=N`（上限受 `gray.approval.max-required-approvals` 约束，默认 5）。

**Q2：SUPER_ADMIN 1 人直签如何生效？**
需网关或上游中间件在请求头注入 `X-Role: SUPER_ADMIN` 或鉴权解析后 Principal 含该角色。服务端按 `X-Admin-User` + 角色头联合判定。

**Q3：Redisson 连不上怎么办？**
Sandbox 验证过：`RedissonLockProvider` 自动 `logger.warn` + 降级本地 JVM `ConcurrentHashMap<flowId, ReentrantLock>`。生产上 Redis 集群挂了最多 120s，自愈任务会重新回写最终态。

**Q4：飞书 webhook 配错还能追溯吗？**
能。`FeishuAlertChannel` IOException → `LoggingAlertChannel.send()` 立即补打一条完整卡片内容，且 `alert_errors_total` 指标 +1，Grafana 告警面板可见。

---

## 六、相关文件 & 链接

- 运维手册：[`docs/灰度审批工作流-上线运维执行手册.md`](docs/灰度审批工作流-上线运维执行手册.md)
- 交付物目录：[`deploy/v6.2.0-gray-approval/`](deploy/v6.2.0-gray-approval/)
  - `02-nacos-gray-approval.yaml`：Nacos 生产配置
  - `03-xxljob-create-jobs.sh`：XXL-JOB 三任务注册脚本
  - `04a-vm-deploy.sh` / `04b-k8s-deploy.sh`：VM 与 K8s 发布脚本（含回滚）
  - `05-smoke.sh`：5 条冒烟断言
  - `gray-rollout-dashboard.json`：Grafana 仪表盘导入
- DDL & 回滚：[`src/main/resources/db/migration/`](src/main/resources/db/migration/)
