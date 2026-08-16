# 链盛通LSC 消费权益凭证循环系统 V6.2 (AI增强版) 架构文档

## 1. 系统概述

链盛通LSC系统是一套面向消费权益凭证全生命周期管理的分布式微服务系统，覆盖凭证发行、流通、核销、结算、对账等核心业务环节。V6.2 AI增强版集成了AI智能网关、智能风控、精准营销等AI能力。

### 1.1 核心业务能力

| 能力域 | 功能描述 |
|--------|----------|
| 用户中心 | 注册/登录、实名认证、商家资质审核、推荐关系绑定 |
| 凭证账本 | 凭证发行、库存管理、批次追踪、生命周期管理 |
| 流通交易 | B2B采购、订单管理、支付结算、分账清分 |
| 核销履约 | 线下核销、扫码验证、履约确认、核销退款 |
| 营销推广 | 智能推荐、优惠券、活动管理、裂变分享 |
| 风控安全 | 交易风控、反欺诈、黑名单、异常检测 |
| AI能力 | 智能客服、语义理解、精准营销、风险预测 |
| 链上存证 | 交易存证、哈希上链、审计追踪、合规证明 |

### 1.2 技术架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                          客户端层                                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │  管理后台     │  │  商家前台     │  │  移动端H5小程序         │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────────┘  │
└─────────┼─────────────────┼──────────────────────┼──────────────────┘
          │                 │                      │
┌─────────┼─────────────────┼──────────────────────┼──────────────────┐
│         ▼                 ▼                      ▼                  │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │                    Nginx 反向代理 / CDN                      │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
│                             │                                        │
│  ┌──────────────────────────▼──────────────────────────────────┐    │
│  │                   Spring Cloud Gateway (8000)                │    │
│  │  路由转发 │ 限流熔断 │ 鉴权验签 │ 日志追踪 │ 负载均衡        │    │
│  └──────────────────────────┬──────────────────────────────────┘    │
│                             │                                        │
│         ┌───────────────────┼───────────────────┐                  │
│         ▼                   ▼                   ▼                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐             │
│  │  业务服务集群  │  │  AI网关集群   │  │  管理服务集群  │             │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘             │
│         │                  │                  │                    │
│  ┌──────┴──────────────────┴──────────────────┴───────┐           │
│  │                  中间件基础设施                       │           │
│  │  Nacos │ Redis集群 │ RabbitMQ │ Seata │ XXL-JOB    │           │
│  └─────────────────────────────────────────────────────┘           │
│                             │                                        │
│  ┌──────────────────────────▼──────────────────────────────────┐    │
│  │                    数据存储层                                │    │
│  │  MySQL分库分表(8库32表) │ Redis缓存 │ Elasticsearch          │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

## 2. 微服务清单

### 2.1 服务端口映射

| 服务名 | 端口 | 描述 | 副本数 | 关键依赖 |
|--------|------|------|--------|----------|
| lsc-gateway | 8000 | API网关 | 2 | Nacos, Redis |
| lsc-user-service | 8101 | 用户服务 | 2 | MySQL, Nacos |
| lsc-ledger-service | 8102 | 账本服务(核心) | 3 | MySQL, Redis, Nacos |
| lsc-b2b-service | 8103 | B2B商户服务 | 2 | Nacos |
| lsc-order-service | 8104 | 订单服务 | 2 | MySQL, RabbitMQ, Nacos |
| lsc-writeoff-service | 8105 | 核销服务 | 2 | Nacos |
| lsc-release-service | 8106 | 权益释放服务 | 1 | MySQL, Nacos |
| lsc-promotion-service | 8107 | 营销推广服务 | 2 | Nacos |
| lsc-mall-service | 8108 | 商城服务 | 2 | Nacos |
| lsc-risk-service | 8109 | 风控服务 | 2 | Nacos |
| lsc-media-service | 8110 | 媒体服务 | 2 | Nacos |
| lsc-map-service | 8111 | 地图定位服务 | 2 | Nacos |
| lsc-reconciliation-service | 8112 | 对账服务 | 2 | Nacos |
| lsc-evidence-service | 8113 | 链上存证服务 | 2 | Nacos |
| lsc-admin-service | 8200 | 管理后台服务 | 2 | MySQL, Nacos |
| lsc-ai-gateway | 8201 | AI智能网关 | 2 | Nacos |

### 2.2 服务依赖关系

```
lsc-gateway ──┬──> lsc-user-service (鉴权)
               ├──> lsc-ai-gateway (AI能力)
               ├──> lsc-admin-service (后台管理)
               ├──> lsc-ledger-service (账本)
               ├──> lsc-b2b-service (B2B)
               ├──> lsc-order-service (订单)
               ├──> lsc-writeoff-service (核销)
               ├──> lsc-release-service (释放)
               ├──> lsc-promotion-service (营销)
               ├──> lsc-mall-service (商城)
               ├──> lsc-risk-service (风控)
               ├──> lsc-media-service (媒体)
               ├──> lsc-map-service (地图)
               ├──> lsc-reconciliation-service (对账)
               └──> lsc-evidence-service (存证)

lsc-order-service ──> lsc-ledger-service (扣减库存)
                   ──> lsc-risk-service (风控校验)
                   ──> lsc-promotion-service (优惠券)
                   ──> RabbitMQ (异步通知)

lsc-release-service ──> lsc-ledger-service (权益释放)
                    ──> XXL-JOB (定时释放)

lsc-writeoff-service ──> lsc-ledger-service (核销扣减)
                     ──> lsc-evidence-service (核销存证)

lsc-reconciliation-service ──> lsc-ledger-service (对账数据)
                           ──> XXL-JOB (定时对账)
```

## 3. 技术栈详情

### 3.1 后端技术栈

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 语言 | Java | 17 | 主开发语言 |
| 框架 | Spring Boot | 3.2.5 | 应用基础框架 |
| 框架 | Spring Cloud | 2023.0.1 | 微服务治理 |
| 框架 | Spring Cloud Alibaba | 2023.0.1.0 | 阿里云微服务组件 |
| 网关 | Spring Cloud Gateway | 4.1.1 | API网关 |
| 注册中心 | Nacos | 2.3.2 | 服务注册/发现/配置 |
| ORM | MyBatis-Plus | 3.5.5 | 数据访问 |
| 数据库 | MySQL | 8.0 | 主数据库 |
| 分库分表 | ShardingSphere | 5.4.1 | 8库32表分库分表 |
| 分布式事务 | Seata | 2.0.0 | AT模式分布式事务 |
| 消息队列 | RabbitMQ | 3.13 | 异步消息/事件驱动 |
| 缓存 | Redis | 7.2 | 集群缓存/会话存储 |
| Redis客户端 | Redisson | 3.27.2 | Redis客户端/分布式锁 |
| 任务调度 | XXL-JOB | 2.4.0 | 分布式任务调度 |
| 安全 | JJWT | 0.12.6 | JWT双令牌认证 |
| 加密 | BCrypt | - | 密码加密 |
| API文档 | Knife4j | 4.4.0 | OpenAPI3文档 |
| 工具 | Hutool | 5.8.26 | 通用工具库 |
| JSON | FastJSON2 | 2.0.47 | JSON序列化 |

### 3.2 前端技术栈

| 项目 | 技术 | 描述 |
|------|------|------|
| lsc-admin-web | Vue3 + Vite + Element Plus | 管理后台 |
| lsc-merchant-web | Vue3 + Vite + Ant Design Vue | 商家前台 |
| lsc-mobile-app | Vue3 + Vant + TypeScript | 移动端H5 |

### 3.3 基础设施

| 分类 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 容器化 | Docker | 24+ | 镜像构建/容器运行 |
| 容器编排 | Kubernetes | 1.28+ | 生产环境编排 |
| CI/CD | GitHub Actions | - | 持续集成/部署 |
| 反向代理 | Nginx | 1.25+ | 前端托管/SSL终结 |
| 监控 | Prometheus + Grafana | - | 指标监控/可视化 |
| 日志 | ELK Stack | - | 日志收集分析 |
| 链路追踪 | SkyWalking | 9.0.0 | 分布式链路追踪 |

## 4. 数据库设计

### 4.1 分库分表策略

```
lsc_system (主库 - 全局配置)
├── lsc_user (用户库)
│   ├── t_user_0 ~ t_user_3 (用户表 x4)
│   ├── t_merchant_0 ~ t_merchant_3 (商家表 x4)
│   └── t_realname_0 ~ t_realname_3 (实名认证表 x4)
├── lsc_ledger (账本库)
│   ├── t_voucher_batch_0 ~ t_voucher_batch_3 (批次表 x4)
│   ├── t_voucher_stock_0 ~ t_voucher_stock_3 (库存表 x4)
│   └── t_voucher_flow_0 ~ t_voucher_flow_3 (流水表 x4)
├── lsc_order (订单库)
│   ├── t_order_0 ~ t_order_7 (订单表 x8)
│   └── t_order_item_0 ~ t_order_item_7 (订单明细表 x8)
├── lsc_promotion (营销库)
│   ├── t_coupon_0 ~ t_coupon_3 (优惠券表 x4)
│   └── t_activity_0 ~ t_activity_3 (活动表 x4)
├── lsc_b2b (B2B库)
│   ├── t_b2b_order_0 ~ t_b2b_order_3 (B2B订单 x4)
│   └── t_contract_0 ~ t_contract_3 (合同表 x4)
├── lsc_settlement (结算库)
│   ├── t_settlement_0 ~ t_settlement_3 (结算表 x4)
│   └── t_reconciliation_0 ~ t_reconciliation_3 (对账表 x4)
├── lsc_risk (风控库)
│   ├── t_blacklist (黑名单表)
│   └── t_risk_event (风控事件表)
└── nacos_config (Nacos配置库)
```

### 4.2 核心数据模型

**用户表 (t_user)**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 雪花ID |
| phone | varchar(20) | 手机号(加密存储) |
| password | varchar(100) | BCrypt密码 |
| status | tinyint | 状态(0禁用/1启用) |
| realname_verified | tinyint | 实名认证标记 |
| invite_code | varchar(10) | 推荐码 |
| created_at | datetime | 创建时间 |

**凭证批次表 (t_voucher_batch)**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 雪花ID |
| batch_no | varchar(32) | 批次编号 |
| type | tinyint | 类型(1电子/2实物) |
| total_qty | int | 发行总量 |
| remain_qty | int | 剩余数量 |
| unit_value | decimal(10,2) | 面值 |
| expire_date | date | 有效期 |
| status | tinyint | 状态 |

**订单表 (t_order)**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 雪花ID |
| order_no | varchar(32) | 订单号 |
| user_id | bigint | 用户ID |
| merchant_id | bigint | 商家ID |
| amount | decimal(12,2) | 订单金额 |
| pay_type | tinyint | 支付方式 |
| status | tinyint | 订单状态 |
| created_at | datetime | 创建时间 |

## 5. 安全设计

### 5.1 认证授权

```
┌─────────────┐    密码验证    ┌─────────────┐
│  客户端      │ ──────────────> │ 用户服务     │
│             │                 │             │
│  用户名+密码 │                 │ BCrypt校验  │
└──────┬──────┘                 └──────┬──────┘
       │                               │
       │         JWT双令牌             │
       │<──────────────────────────────┤
       │  access_token (30min)          │
       │  refresh_token (7d)            │
       │                               │
       ▼                               │
┌─────────────┐    Token验证    ┌──────┴──────┐
│  API网关     │ ──────────────> │ Redis黑名单  │
│             │                 │             │
│  JWT签名验证 │                 │ Token版本   │
│ 请求限流    │                 │ 登录次数限制│
└──────┬──────┘                 └─────────────┘
       │
       ▼
┌─────────────┐
│  业务服务    │
│  权限校验    │
│  数据权限    │
└─────────────┘
```

### 5.2 安全机制清单

| 机制 | 实现方式 | 说明 |
|------|----------|------|
| 密码加密 | BCrypt | 自适应哈希，加盐存储 |
| 会话管理 | JWT双令牌 | access_token + refresh_token |
| 登录限制 | Redis计数器 | 5次失败锁定30分钟 |
| Token黑名单 | Redis ZSET | 登出/修改密码时失效 |
| 接口限流 | Gateway + Sentinel | 网关层+服务层双重限流 |
| 数据加密 | AES-256-GCM | 敏感字段加密存储 |
| SQL注入防护 | MyBatis-Plus参数化 | 全局参数化查询 |
| XSS防护 | 输入过滤+输出编码 | Gateway统一处理 |
| CSRF防护 | Token验证 | 关键操作需验证Token |
| 操作审计 | 日志记录 | 关键操作全量审计 |
| 传输加密 | TLS 1.3 | 全链路HTTPS |
| 密钥管理 | K8s Secrets | 敏感配置Secret管理 |

## 6. 部署架构

### 6.1 Docker Compose 部署 (开发/测试环境)

```bash
# 1. 启动基础设施
cd docker
docker-compose up -d

# 2. 初始化数据库和Nacos
./init-db.sh
./init-nacos.sh

# 3. 启动应用服务
docker-compose -f docker-compose-app.yml up -d

# 或一键启动
./deploy-local.sh
```

### 6.2 Kubernetes 部署 (生产环境)

```bash
# 1. 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 部署基础设施 (MySQL/Redis/RabbitMQ/Nacos等)
# 需先在K8s集群中部署基础设施服务

# 3. 部署配置
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml

# 4. 部署微服务
kubectl apply -f k8s/deployments.yaml
kubectl apply -f k8s/deployments-extra.yaml

# 5. 暴露服务
kubectl apply -f k8s/services.yaml
kubectl apply -f k8s/ingress.yaml

# 6. 一键部署
./deploy-k8s.sh
```

### 6.3 环境要求

| 环境 | CPU | 内存 | 磁盘 | 说明 |
|------|-----|------|------|------|
| 开发环境 | 4核 | 8GB | 50GB | Docker Compose单机 |
| 测试环境 | 8核 | 16GB | 100GB | Docker Compose/K8s |
| 生产环境 | 32核+ | 64GB+ | 500GB+ | K8s集群(3节点+) |

### 6.4 端口分配

| 端口范围 | 用途 |
|----------|------|
| 80 | HTTP (前端) |
| 443 | HTTPS (前端) |
| 3306 | MySQL |
| 5672 | RabbitMQ |
| 8000 | API网关 |
| 8080 | XXL-JOB Admin |
| 8101-8113 | 业务微服务 |
| 8200-8201 | 后台/AI服务 |
| 8848 | Nacos |
| 9090 | Prometheus |
| 9200 | Elasticsearch |

## 7. 配置管理

### 7.1 Nacos配置清单

| 配置文件 | 说明 |
|----------|------|
| lsc-common-datasource.yaml | 数据源+分库分表配置 |
| lsc-common-redis.yaml | Redis集群配置 |
| lsc-common-infra.yaml | 中间件公共配置 |
| lsc-gateway-routes.yaml | API网关路由规则 |
| {service}-prod.yaml | 各服务生产配置 |

### 7.2 关键配置项

**分库分表规则**
```yaml
shardingsphere:
  rules:
    readwrite-splitting:
      data-sources:
        lsc-user-ds:
          write-data-source-name: lsc-user-master
          read-data-source-names: lsc-user-slave-0,lsc-user-slave-1
    sharding:
      tables:
        t_user:
          actual-data-nodes: lsc-user-$->{0..3}.t_user_$->{0..3}
          database-strategy:
            standard:
              sharding-column: id
              sharding-algorithm-name: user-db-mod
          table-strategy:
            standard:
              sharding-column: id
              sharding-algorithm-name: user-table-mod
```

**Redis集群**
```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-1:7000
          - redis-2:7001
          - redis-3:7002
          - redis-4:7003
          - redis-5:7004
          - redis-6:7005
      password: Lsc@Redis2026
      lettuce:
        pool:
          max-active: 100
          max-idle: 50
          min-idle: 20
```

## 8. 监控与可观测性

### 8.1 监控指标

| 维度 | 指标 | 工具 |
|------|------|------|
| 基础设施 | CPU/内存/磁盘/网络 | Prometheus + Node Exporter |
| 应用指标 | QPS/延迟/错误率 | Micrometer + Prometheus |
| JVM | GC/堆内存/线程数 | JMX Exporter |
| 数据库 | 连接数/慢查询/QPS | MySQL Exporter |
| Redis | 命中率/内存/慢查询 | Redis Exporter |
| 业务指标 | 订单量/核销量/交易额 | 自定义业务指标 |

### 8.2 链路追踪

```
用户请求 → API网关 → 业务服务A → 业务服务B → 数据库
    │          │          │          │          │
    ▼          ▼          ▼          ▼          ▼
  TraceID   SpanID     SpanID     SpanID     SpanID
    └─────────────────────────────────────────────────┘
                   SkyWalking 统一追踪
```

### 8.3 日志规范

| 日志级别 | 用途 |
|----------|------|
| ERROR | 系统错误、业务异常 |
| WARN | 警告信息、降级触发 |
| INFO | 关键业务节点 |
| DEBUG | 调试信息 |
| TRACE | 详细追踪(开发环境) |

日志格式:
```
[时间] [级别] [TraceID] [类名] [方法] - 消息
2026-04-10 14:30:00.123 INFO  [abc123] c.l.s.UserController [login] - 用户登录成功: userId=10001
```

## 9. AI增强能力

### 9.1 AI网关架构

```
┌─────────────────────────────────────────────┐
│              LSC AI Gateway (8201)          │
├─────────────────────────────────────────────┤
│  智能路由 │ 语义理解 │ Prompt工程 │ 模型管理  │
├─────────────────────────────────────────────┤
│  本地模型 │ 云端API │ 向量检索 │ 缓存优化   │
├─────────────────────────────────────────────┤
│  ┌─────────────────────────────────────┐    │
│  │  智能客服子系统                      │    │
│  │  - 多轮对话                          │    │
│  │  - 意图识别                          │    │
│  │  - 知识检索(RAG)                     │    │
│  │  - 情感分析                          │    │
│  └─────────────────────────────────────┘    │
│  ┌─────────────────────────────────────┐    │
│  │  智能营销子系统                      │    │
│  │  - 用户画像分析                      │    │
│  │  - 个性化推荐                        │    │
│  │  - 活动智能生成                      │    │
│  │  - A/B测试优化                       │    │
│  └─────────────────────────────────────┘    │
│  ┌─────────────────────────────────────┐    │
│  │  智能风控子系统                      │    │
│  │  - 异常交易检测                      │    │
│  │  - 欺诈行为预测                      │    │
│  │  - 风险评级                          │    │
│  │  - 自动处置策略                      │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

### 9.2 AI模型清单

| 模型 | 类型 | 用途 | 部署方式 |
|------|------|------|----------|
| 对话大模型 | LLM | 智能客服/语义理解 | 云端API |
| 推荐模型 | 排序模型 | 个性化推荐 | 本地部署 |
| 风控模型 | 分类模型 | 交易风险预测 | 本地部署 |
| NER模型 | 命名实体识别 | 信息抽取 | 本地部署 |

## 10. 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| V6.0 | 2025-Q1 | 基础微服务架构，17个微服务 |
| V6.1 | 2025-Q2 | 分布式事务、链路追踪、灰度发布 |
| V6.2 | 2026-Q1 | AI增强版：AI网关、智能风控、精准营销、链路存证 |

## 附录

### A. 构建与部署脚本

| 脚本 | 路径 | 用途 |
|------|------|------|
| build-all.sh | docker/build-all.sh | Maven+Docker全量构建 |
| deploy-local.sh | docker/deploy-local.sh | 本地一键部署 |
| deploy-k8s.sh | docker/deploy-k8s.sh | K8s部署脚本 |
| init-db.sh | docker/init-db.sh | 数据库初始化 |
| init-nacos.sh | docker/init-nacos.sh | Nacos配置初始化 |
| start-dev.sh | docker/start-dev.sh | 开发环境启动 |

### B. 配置文件清单

| 文件 | 路径 | 用途 |
|------|------|------|
| lsc-common-datasource.yaml | config/nacos/ | 分库分表数据源 |
| lsc-common-redis.yaml | config/nacos/ | Redis集群配置 |
| lsc-gateway-routes.yaml | config/nacos/ | API网关路由 |
| admin-web.conf | docker/config/nginx/ | 管理后台Nginx |
| merchant-web.conf | docker/config/nginx/ | 商家前台Nginx |
| mobile-web.conf | docker/config/nginx/ | 移动端Nginx |
| gateway.conf | docker/config/nginx/ | API网关Nginx |

### C. SQL脚本

| 文件 | 路径 | 用途 |
|------|------|------|
| lsc_system_v6.2.sql | sql/ | 全量建表语句 |
| lsc_sharding.sql | sql/ | 分库分表初始化 |