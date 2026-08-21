# 链盛通 LSC 消费权益凭证循环系统 V6.2 (AI增强版)

> 生产级微服务架构系统 · 17 个微服务 · 24,048 行代码 · 2,551 单元测试 · 96.95% 覆盖率

## 项目简介

链盛通 LSC (LianShengTong Consumer Equity Certificate) 是一套基于 Spring Cloud 微服务架构的消费权益凭证循环系统，支持 LSC 凭证的发行、流通、核销、释放、交易全生命周期管理，并集成 AI 智能分析能力。

## 技术栈

| 层级 | 技术 |
|---|---|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 · Spring Cloud 2023.0.1 |
| 注册/配置 | Nacos 2.3.2 |
| 限流 | Spring Cloud Gateway Redis RateLimiter · Sentinel 1.8.7 |
| 分布式事务 | Seata 2.0.0 |
| 数据库 | MySQL 8.0 (分库分表 8库32表) |
| 缓存 | Redis 7 (3主3从) · Caffeine (本地缓存) |
| 消息队列 | RabbitMQ 3.12+ |
| 分库分表 | ShardingSphere 5.4.1 |
| 安全 | JWT 双令牌 · Token 黑名单 · XSS 防护 |
| 监控 | Prometheus + Grafana + Micrometer · Alertmanager |
| 部署 | Docker · Kubernetes · GitHub Actions CI/CD |
| 前端 | Vue 3 + Vite (管理后台 + 商户前台 + 移动端 H5) |

## 微服务模块

| # | 模块 | 端口 | 说明 |
|---|---|---|---|
| 1 | lsc-gateway | 8000 | API 网关 |
| 2 | lsc-user-service | 8101 | 用户管理 |
| 3 | lsc-ledger-service | 8102 | LSC 账本 |
| 4 | lsc-b2b-service | 8103 | B2B 交易 |
| 5 | lsc-order-service | 8104 | 订单管理 |
| 6 | lsc-writeoff-service | 8105 | 核销服务 |
| 7 | lsc-release-service | 8106 | 释放服务 |
| 8 | lsc-promotion-service | 8107 | 促销服务 |
| 9 | lsc-mall-service | 8108 | 权益商城 |
| 10 | lsc-risk-service | 8109 | 风控服务 |
| 11 | lsc-media-service | 8110 | 媒体存储 |
| 12 | lsc-map-service | 8111 | 地图服务 |
| 13 | lsc-reconciliation-service | 8112 | 对账服务 |
| 14 | lsc-evidence-service | 8113 | 区块链存证 |
| 15 | lsc-ai-gateway | 8201 | AI 网关 |
| 16 | lsc-admin-service | 8200 | 管理后台 |
| 17 | lsc-common | — | 公共组件 |

## 快速开始

### 前置条件

- JDK 17+
- Maven 3.9+
- Docker 24.0+
- MySQL 8.0+
- Redis 7.0+

### 构建与运行

```bash
# 1. 克隆代码
git clone https://github.com/zcls7792-gif/lsc-system-v6.2.git
cd lsc-system-v6.2

# 2. 配置密钥
cp docker/.env.example docker/.env
./scripts/rotate-secrets.sh

# 3. 构建后端
mvn clean install -DskipTests

# 4. Docker Compose 启动全部服务
cd docker && docker compose --env-file .env up -d

# 5. 验证服务
curl http://localhost:8000/actuator/health
```

### Kubernetes 部署

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml    # 先填充真实密钥
kubectl apply -f k8s/deployments.yaml
kubectl apply -f k8s/deployments-extra.yaml
kubectl apply -f k8s/services.yaml
kubectl apply -f k8s/pod-disruption-budget.yaml
kubectl apply -f k8s/hpa.yaml
kubectl apply -f k8s/network-policy.yaml
kubectl apply -f k8s/tls-certificates.yaml
```

## 项目结构

```
lsc-system-v6.2/
├── lsc-common/              # 公共组件（安全、缓存、工具类）
├── lsc-gateway/             # API 网关
├── lsc-user-service/        # 用户管理
├── lsc-ledger-service/      # LSC 账本
├── lsc-b2b-service/         # B2B 交易
├── lsc-order-service/       # 订单管理
├── lsc-writeoff-service/    # 核销服务
├── lsc-release-service/     # 释放服务
├── lsc-promotion-service/   # 促销服务
├── lsc-mall-service/        # 权益商城
├── lsc-risk-service/        # 风控服务
├── lsc-media-service/       # 媒体存储
├── lsc-map-service/         # 地图服务
├── lsc-reconciliation-service/  # 对账服务
├── lsc-evidence-service/    # 区块链存证
├── lsc-ai-gateway/          # AI 网关
├── lsc-admin-service/       # 管理后台
├── lsc-admin-web/           # 管理后台前端
├── lsc-merchant-web/        # 商户前台前端
├── lsc-mobile-app/          # 移动端 H5
├── docker/                  # Docker 配置
├── k8s/                     # Kubernetes 清单
├── sql/                     # 数据库初始化脚本
├── config/nacos/            # Nacos 共享配置
├── scripts/                 # 运维脚本
└── pom.xml                  # 父工程
```

## 质量指标

| 指标 | 数值 |
|---|---|
| 单元测试 | 2,551 tests · 0 failures |
| 行覆盖率 | 96.61% |
| 指令覆盖率 | 96.95% |
| 分支覆盖率 | 89.14% |
| 类覆盖率 | 100% |
| BUG 修复 | 27 个 |
| 依赖 CVE | 0 |

## 文档

- [部署运维手册](LSC_V6.2_Deployment_Runbook.md)
- [最终上线报告](LSC_V6.2_Final_GoLive_Report.md)
- [生产就绪审计](LSC_V6.2_Production_Readiness_Audit_Report.md)

## License

Proprietary - 链盛通 (LianShengTong) 2026
