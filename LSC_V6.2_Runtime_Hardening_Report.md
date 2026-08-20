# 链盛通 LSC V6.2 · 生产运行时配置加固报告

**日期：** 2026-08-20  
**范围：** 优雅停机、K8s HPA、Actuator 安全、terminationGracePeriod  
**结论：** ✅ 全部通过

---

## 1. 优雅停机 (Graceful Shutdown)

### 1.1 问题

全部 17 个微服务**缺少** `server.shutdown: graceful` 配置。K8s 滚动更新时，Pod 收到 SIGTERM 后立即终止，可能导致正在处理的请求中断。

### 1.2 修复

为所有服务添加优雅停机配置：

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

### 1.3 修复范围

| 配置位置 | 文件 | 覆盖服务 |
|---|---|---|
| K8s ConfigMap | `k8s/configmap.yaml` | 全部 17 个服务（通过 envFrom 注入） |
| application-prod.yml | `lsc-admin-service/.../application-prod.yml` | 管理后台 |
| application-prod.yml | `lsc-user-service/.../application-prod.yml` | 用户服务 |
| application-prod.yml | `lsc-gateway/.../application-prod.yml` | API 网关 |
| application-prod.yml | `lsc-evidence-service/.../application-prod.yml` | 存证服务 |

### 1.4 K8s terminationGracePeriodSeconds

为全部 14 个 Deployment 添加 `terminationGracePeriodSeconds: 45`，确保 30s 优雅停机 + 15s 缓冲：

| 文件 | Deployment 数量 |
|---|---|
| `k8s/deployments.yaml` | 6 |
| `k8s/deployments-extra.yaml` | 8 |

---

## 2. Actuator 端点安全

### 2.1 修复前

K8s ConfigMap 中 `health.show-details: always`，暴露健康检查详情给未授权用户。

### 2.2 修复后

```yaml
management:
  endpoint:
    health:
      show-details: when_authorized  # 仅授权用户可见详情
```

### 2.3 端点暴露确认

| 端点 | 是否暴露 | 安全评估 |
|---|---|---|
| /actuator/health | ✅ | 安全（仅状态） |
| /actuator/info | ✅ | 安全（应用信息） |
| /actuator/metrics | ✅ | 安全（Prometheus 采集） |
| /actuator/prometheus | ✅ | 安全（监控用） |
| /actuator/env | ❌ | 未暴露（安全） |
| /actuator/beans | ❌ | 未暴露（安全） |
| /actuator/configprops | ❌ | 未暴露（安全） |

---

## 3. K8s HPA 自动扩缩容

### 3.1 问题

生产环境缺少 HPA（HorizontalPodAutoscaler），无法根据负载自动扩缩容。

### 3.2 修复

新增 `k8s/hpa.yaml`，为 8 个核心服务配置 HPA：

| 服务 | minReplicas | maxReplicas | CPU阈值 | 内存阈值 |
|---|---|---|---|---|
| lsc-gateway | 2 | 8 | 70% | 80% |
| lsc-user-service | 2 | 6 | 70% | 80% |
| lsc-ledger-service | 3 | 10 | 65% | 75% |
| lsc-mall-service | 2 | 6 | 70% | 80% |
| lsc-evidence-service | 2 | 8 | 70% | 80% |
| lsc-media-service | 2 | 6 | 70% | 80% |
| lsc-ai-gateway | 2 | 6 | 70% | 80% |
| lsc-risk-service | 2 | 5 | 70% | 80% |

**扩容触发：** CPU > 70% 或内存 > 80%  
**缩容触发：** CPU < 70% 且内存 < 80%  
**账本服务阈值更低（65%/75%）：** 交易核心服务更激进扩容

---

## 4. 已确认通过项

| 检查项 | 状态 | 说明 |
|---|---|---|
| CORS 生产限制 | ✅ | `https://*.lianshengtong.com,https://*.chainshangtong.com` |
| 日志级别 | ✅ | root: WARN, 业务: WARN |
| HikariCP 连接池 | ✅ | max-active: 200, initial-size: 10 |
| Knife4j 生产关闭 | ✅ | `knife4j.enable: false` |
| Swagger 生产关闭 | ✅ | `springdoc.swagger-ui.enabled: false` |
| PDB 已配置 | ✅ | `k8s/pod-disruption-budget.yaml` 覆盖核心服务 |
| TLS 证书 | ✅ | `k8s/tls-certificates.yaml` 配置 Let's Encrypt |
| NetworkPolicy | ✅ | 数据层 egress 正确指向 lsc-system |
| ConfigMap | ✅ | Nacos/数据库/Redis/Seata/Gateway 路由全配置 |

---

## 5. 构建验证

| 检查项 | 结果 |
|---|---|
| evidence-service 构建 | ✅ BUILD SUCCESS (02:50 min) |
| evidence-service 测试 | ✅ 471 tests · 0 failures · 0 errors |

---

## 6. 审计结论

```
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║   生产运行时配置加固：✅ ALL PASS                            ║
║                                                              ║
║   优雅停机：   17/17 服务已配置 (server.shutdown: graceful)  ║
║   停机超时：   14/14 Deployment terminationGracePeriod: 45s  ║
║   HPA 扩缩容： 8 个核心服务自动伸缩 (2-10 replicas)         ║
║   Actuator：   show-details: when_authorized (已修复)        ║
║   CORS：       生产限制为 lianshengtong.com 域名             ║
║   日志：       root: WARN (生产级)                            ║
║   PDB：        已配置 (零停机维护)                           ║
║   TLS：        Let's Encrypt 已配置                           ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝
```

---

**报告生成：** 2026-08-20 08:30 UTC  
**仓库：** https://github.com/zcls7792-gif/lsc-system-v6.2
