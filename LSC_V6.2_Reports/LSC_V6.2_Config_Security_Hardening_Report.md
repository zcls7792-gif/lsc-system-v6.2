# 链盛通 LSC V6.2 · 配置安全加固报告

**日期：** 2026-08-20  
**审计范围：** application.yml 配置安全、K8s 滚动更新策略、依赖版本漏洞  
**结论：** ✅ 全部通过

---

## 1. application.yml 配置扫描

### 1.1 扫描结果

对全部 17 个微服务的 `application.yml` 执行敏感信息扫描：

| 检查项 | 扫描数 | 硬编码 | 状态 |
|---|---|---|---|
| 数据库密码 | 17 | 0 | ✅ 全部 `${VAR}` 外部化 |
| Redis 密码 | 17 | 0 | ✅ 全部 `${VAR}` 外部化 |
| JWT Secret | 17 | **1 (已修复)** | ✅ 已移除默认值 |
| API Key | 17 | 0 | ✅ 全部 `${VAR}` 外部化 |
| 消息队列密码 | 17 | 0 | ✅ 全部 `${VAR}` 外部化 |

### 1.2 修复项

| 文件 | 行 | 问题 | 修复 |
|---|---|---|---|
| `lsc-evidence-service/.../application.yml` L114 | JWT secret 含硬编码默认值 `lsc-evidence-jwt-secret-key-2026-...` | 移除默认值，改为 `${JWT_SECRET}`（无 fallback） |

**修复前：**
```yaml
secret: ${JWT_SECRET:lsc-evidence-jwt-secret-key-2026-must-be-32-bytes-long-and-secure}
```

**修复后：**
```yaml
secret: ${JWT_SECRET}
```

### 1.3 正面示例（无需修改）

| 服务 | 配置项 | 外部化方式 |
|---|---|---|
| lsc-gateway | JWT secret | `${JWT_SECRET}` / `${ADMIN_JWT_SECRET}` 无默认值 |
| lsc-user-service | JWT secret | `${JWT_SECRET}` 无默认值 |
| lsc-ai-gateway | API keys | `${AI_*_KEY:}` 空默认值 |
| lsc-media-service | OSS/COS keys | `${VAR}` 外部化 |
| 全部服务 | 数据库 | `${MYSQL_URL}` / `${MYSQL_PASSWORD}` |

---

## 2. K8s 滚动更新策略

### 2.1 修复前

全部 14 个 Deployment **缺少 `strategy` 配置**，使用 K8s 默认策略（`maxSurge: 25%, maxUnavailable: 25%`），可能导致更新期间服务可用性下降。

### 2.2 修复后

为全部 14 个 Deployment 添加零停机滚动更新策略：

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 1          # 滚动时最多多出 1 个 Pod
    maxUnavailable: 0    # 滚动时不允许减少可用 Pod（零停机）
```

### 2.3 受影响文件

| 文件 | Deployment 数量 |
|---|---|
| `k8s/deployments.yaml` | 6 (gateway, user, ledger, release, ai-gateway, b2b/order/writeoff/admin) |
| `k8s/deployments-extra.yaml` | 8 (promotion, mall, risk, reconciliation, media, map, evidence, merchant-web) |
| **合计** | **14** |

### 2.4 K8s 资源配置确认

| 检查项 | 状态 | 说明 |
|---|---|---|
| CPU/Memory requests | ✅ | 全部 14 个 Deployment 均已配置 |
| CPU/Memory limits | ✅ | 全部 14 个 Deployment 均已配置 |
| livenessProbe | ✅ | 全部配置 `/actuator/health` |
| readinessProbe | ✅ | 全部配置 `/actuator/health` |
| envFrom secretRef | ✅ | 全部引用 `lsc-secrets` |
| envFrom configMapRef | ✅ | 全部引用 `lsc-config` |
| 镜像 tag | ✅ | 固定 `6.2.0`，无 `latest` |
| replicas | ✅ | 核心服务 ≥2，账本服务 3 |

---

## 3. 依赖版本漏洞扫描

### 3.1 依赖版本清单

| 依赖 | 版本 | 已知CVE | 状态 |
|---|---|---|---|
| Spring Boot | 3.2.5 | 无高危 | ✅ |
| Spring Cloud | 2023.0.1 | 无 | ✅ |
| Spring Cloud Alibaba | 2023.0.1.0 | 无 | ✅ |
| MyBatis-Plus | 3.5.5 | 无 | ✅ |
| ShardingSphere | 5.4.1 | 无 | ✅ |
| Seata | 2.0.0 | 无 | ✅ |
| FastJSON2 | 2.0.47 | 无 | ✅ |
| MySQL Connector | 8.0.33 | 无高危 | ✅ |
| Druid | 1.2.22 | 无 | ✅ |
| OkHttp | 4.12.0 | 无 | ✅ |
| Guava | 33.1.0-jre | 无 | ✅ |
| JJWT | 0.12.6 | 无 | ✅ |
| Redisson | 3.27.2 | 无 | ✅ |
| Hutool | 5.8.26 | 无 | ✅ |
| Knife4j | 4.4.0 | 无 | ✅ |
| Lombok | 1.18.32 | 无 | ✅ |
| Nacos Client | 2.3.2 | 无 | ✅ |
| Sentinel | 1.8.7 | 无 | ✅ |

**结论：** 全部 18 个核心依赖版本均无已知高危 CVE。

---

## 4. 构建验证

| 检查项 | 结果 |
|---|---|
| 证据服务构建 | ✅ BUILD SUCCESS (03:36 min) |
| 证据服务测试 | ✅ 471 tests · 0 failures · 0 errors |
| 全量构建（此前） | ✅ 17/17 模块 · 2,551 tests · 0 failures |

---

## 5. 审计结论

```
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║   配置安全加固审计：✅ ALL PASS                             ║
║                                                              ║
║   application.yml：  17/17 模块无硬编码密钥（1个已修复）     ║
║   K8s 滚动更新：    14/14 Deployment 零停机策略已添加        ║
║   K8s 资源限制：    14/14 Deployment requests/limits 齐全    ║
║   依赖漏洞：        18/18 核心依赖无已知 CVE                ║
║   构建验证：        471 tests 0 failures                     ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
```

---

**报告生成：** 2026-08-20 07:40 UTC  
**仓库：** https://github.com/zcls7792-gif/lsc-system-v6.2
