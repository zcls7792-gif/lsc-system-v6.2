# 存证服务部署检查清单 (Deployment Checklist)

> 适用版本: v1.0.2 | 生成时间: 2026-08-10
> 依据文档: [DEPLOYMENT.md](./DEPLOYMENT.md)

---

## 使用说明

- 部署前逐项检查，全部标记 `[x]` 后方可上线
- `[必检]` 项为阻塞性检查，未通过禁止发布
- `[建议]` 项为非阻塞性检查，建议修复但不阻断发布

---

## 一、环境前置条件

### 1.1 运行时环境

- [ ] `[必检]` JDK 版本 ≥ 17.0.2，推荐 17.0.8+ (LTS)
- [ ] `[必检]` 操作系统为 Linux Kernel 4.19+ (Ubuntu 22.04 / CentOS 8+)
- [ ] `[必检]` 服务器可用内存 ≥ 2GB
- [ ] `[必检]` 磁盘可用空间 ≥ 50GB
- [ ] `[建议]` JVM 参数配置: `-Xms512m -Xmx2g -XX:+UseG1GC`
- [ ] `[建议]` 时区设置为 `Asia/Shanghai`
- [ ] `[建议]` 文件编码设置为 `UTF-8`

### 1.2 中间件依赖

- [ ] `[必检]` MySQL 8.0.x 已安装且可连接
- [ ] `[必检]` Redis 6.x / 7.x 已安装且可连接
- [ ] `[必检]` Nacos 2.2+ 已安装且服务注册正常
- [ ] `[必检]` 区块链节点 RPC 地址可达 (`CHAIN_RPC_URL`)
- [ ] `[必检]` 智能合约地址已配置 (`CHAIN_CONTRACT`)
- [ ] `[必检]` 合约调用私钥已配置 (`CHAIN_PK`)

### 1.3 数据库表结构

- [ ] `[必检]` `blockchain_records` 表已创建
- [ ] `[必检]` `daily_snapshot_records` 表已创建
- [ ] `[必检]` `evidence_failover` 表已创建
- [ ] `[必检]` 数据库账户仅有 `lsc_evidence` 库的读写权限（最小权限原则）
- [ ] `[建议]` 数据库已配置每日全量备份 + binlog 实时备份

---

## 二、HTTPS / TLS 配置

### 2.1 证书

- [ ] `[必检]` SSL 证书已部署（权威 CA 签发，非自签名）
- [ ] `[必检]` 证书私钥文件权限为 `600`，属主为服务运行用户
- [ ] `[必检]` 证书有效期 > 30 天（否则需提前轮换）
- [ ] `[建议]` 配置证书自动续期（ACME / Let's Encrypt）

### 2.2 TLS 加密

- [ ] `[必检]` 仅启用 TLS 1.2 和 TLS 1.3，禁用 TLS 1.0/1.1
- [ ] `[必检]` 加密套件使用 ECDHE + AES-GCM
- [ ] `[必检]` 启用 HSTS 头: `Strict-Transport-Security: max-age=31536000; includeSubDomains`
- [ ] `[建议]` 启用 OCSP Stapling
- [ ] `[建议]` HTTP 80 端口强制 301 跳转至 HTTPS

### 2.3 安全头

- [ ] `[必检]` `X-Content-Type-Options: nosniff` 已配置
- [ ] `[必检]` `X-Frame-Options: DENY` 已配置

---

## 三、JWT 令牌策略

### 3.1 密钥管理

- [ ] `[必检]` JWT 密钥通过环境变量 `JWT_SECRET` 注入，无硬编码
- [ ] `[必检]` 密钥长度 ≥ 256 位 (32 字节)，使用加密随机生成
- [ ] `[必检]` 代码仓库中不存在密钥明文
- [ ] `[建议]` 密钥通过 Nacos 配置中心管理，支持动态刷新
- [ ] `[建议]` 密钥轮换周期 ≤ 90 天
- [ ] `[建议]` 密钥轮换时旧密钥有 24 小时灰度切换期

### 3.2 令牌过期策略

- [ ] `[必检]` Access Token 有效期 ≤ 2 小时 (7200s)
- [ ] `[必检]` Refresh Token 有效期 ≤ 30 天
- [ ] `[必检]` 过滤器强制校验 Token type，Refresh Token 不可用于 API
- [ ] `[必检]` 签名算法使用 HMAC-SHA256
- [ ] `[建议]` 登录接口速率限制 ≤ 5 次/分钟/IP
- [ ] `[建议]` 连续登录失败 5 次锁定账户 30 分钟

---

## 四、环境变量配置

### 4.1 必须变量

- [ ] `[必检]` `MYSQL_HOST` - MySQL 主机地址已设置
- [ ] `[必检]` `MYSQL_USER` - MySQL 用户名已设置
- [ ] `[必检]` `MYSQL_PWD` - MySQL 密码已设置（强密码）
- [ ] `[必检]` `REDIS_HOST` - Redis 主机地址已设置
- [ ] `[必检]` `REDIS_PWD` - Redis 密码已设置（强密码）
- [ ] `[必检]` `JWT_SECRET` - JWT 签名密钥已设置（32 字节随机，生成方式见 DEPLOYMENT.md 4.3.1）
- [ ] `[必检]` `ADMIN_PASSWORD` - 管理员账户密码已设置（≥12 字符，含大小写+数字+符号）
- [ ] `[必检]` `AUDITOR_PASSWORD` - 审计员账户密码已设置（与 admin 不同）
- [ ] `[必检]` `OPERATOR_PASSWORD` - 操作员账户密码已设置（与上两者不同）
- [ ] `[必检]` `CHAIN_RPC_URL` - 区块链节点 RPC 地址已设置
- [ ] `[必检]` `CHAIN_CONTRACT` - 智能合约地址已设置（格式 `0x` + 40 位 hex）
- [ ] `[必检]` `CHAIN_PK` - 合约调用私钥已设置（最高敏感级别，详见 DEPLOYMENT.md 4.3.3）
- [ ] `[必检]` `NACOS_ADDR` - Nacos 地址已设置
- [ ] `[必检]` `NACOS_CLUSTER` - Nacos 集群名已设置

### 4.2 条件变量（HTTPS 启用时）

- [ ] `[必检]` `SSL_KEY_STORE` - SSL 证书路径已设置（PKCS12 格式）
- [ ] `[必检]` `SSL_KEY_STORE_PASSWORD` - SSL 证书密码已设置
- [ ] `[建议]` `SSL_KEY_ALIAS` - SSL 证书别名（默认 `server`）

### 4.3 监控相关变量

- [ ] `[建议]` `DRUID_MONITOR_USER` - Druid 监控页用户名（默认 `admin`）
- [ ] `[建议]` `DRUID_MONITOR_PWD` - Druid 监控页密码已设置（或直接关闭监控页）
- [ ] `[建议]` `LOG_PATH` - 日志输出目录（默认 `/var/log/lsc-evidence`）

### 4.4 可选变量

- [ ] `[建议]` `JWT_ACCESS_EXPIRATION_MS` - Access Token 有效期（默认 7200000ms = 2h）
- [ ] `[建议]` `JWT_REFRESH_EXPIRATION_MS` - Refresh Token 有效期（默认 604800000ms = 7d）
- [ ] `[建议]` `AUTH_MAX_LOGIN_ATTEMPTS` - 连续登录失败锁定阈值（默认 5 次）
- [ ] `[建议]` `AUTH_LOCKOUT_DURATION_MS` - 账户锁定时长（默认 300000ms = 5 分钟）
- [ ] `[建议]` `MYSQL_DB` - 数据库名（默认 `lsc_evidence`）
- [ ] `[建议]` `REDIS_PORT` - Redis 端口（默认 6379）
- [ ] `[建议]` `REDIS_DB` - Redis 编号（默认 `13`）
- [ ] `[建议]` `NACOS_NAMESPACE` - Nacos 命名空间（默认 `production`，严禁用 `public`）
- [ ] `[建议]` `NACOS_GROUP` - Nacos 分组（默认 `DEFAULT_GROUP`）
- [ ] `[建议]` `APP_VERSION` - 应用版本号（注册元数据，默认 `1.0.0`）
- [ ] `[建议]` `APP_REGION` - 应用部署区域（注册元数据，默认 `cn-east`）

### 4.5 敏感变量管理检查

- [ ] `[必检]` 所有敏感变量未硬编码在代码仓库或 Dockerfile 中
- [ ] `[必检]` K8s 部署通过 Secret + secretKeyRef 注入敏感变量
- [ ] `[必检]` `.env` 文件已加入 `.gitignore`
- [ ] `[必检]` 日志中不打印任何密码、Token、私钥
- [ ] `[建议]` 敏感变量通过 HashiCorp Vault 等密钥管理系统托管

---

## 五、Docker 部署检查

- [ ] `[必检]` Docker 镜像已构建: `docker build -t lsc-evidence-service:1.0.0 .`
- [ ] `[必检]` 镜像使用非 root 用户运行 (`USER evidence`)
- [ ] `[必检]` 基础镜像为 `eclipse-temurin:17-jre-alpine`
- [ ] `[必检]` 容器端口 `8113` 已映射
- [ ] `[必检]` 环境变量通过 `-e` 或 `--env-file` 注入
- [ ] `[必检]` 证书目录挂载为只读: `-v /etc/lsc-evidence/certs:/etc/lsc-evidence/certs:ro`
- [ ] `[必检]` 日志目录已挂载: `-v /var/log/lsc-evidence:/var/log/lsc-evidence`
- [ ] `[建议]` 配置 `--restart=always` 自动重启策略
- [ ] `[建议]` 配置内存限制: `--memory=2g`

---

## 六、Kubernetes 部署检查

- [ ] `[必检]` Secret `lsc-evidence-secrets` 已创建并包含所有敏感字段
- [ ] `[必检]` Secret `lsc-evidence-certs` 已创建（SSL 证书）
- [ ] `[必检]` 副本数 ≥ 2（高可用）
- [ ] `[必检]` 滚动更新策略: `maxUnavailable: 0, maxSurge: 1`
- [ ] `[必检]` readinessProbe 路径: `/lsc-evidence/actuator/health`
- [ ] `[必检]` livenessProbe 路径: `/lsc-evidence/actuator/health`
- [ ] `[必检]` CPU/Memory requests 和 limits 已配置
- [ ] `[必检]` Namespace 为 `production`（非 `default`）
- [ ] `[建议]` 配置 PodDisruptionBudget 保证最小可用副本数
- [ ] `[建议]` 配置 NetworkPolicy 限制网络访问

---

## 七、安全加固

### 7.1 传输安全

- [ ] `[必检]` 全链路 HTTPS (TLS 1.2+)
- [ ] `[必检]` 所有接口参数使用 Jakarta Validation 校验
- [ ] `[必检]` SQL 查询使用 MyBatis-Plus 参数化（防注入）
- [ ] `[必检]` 请求体大小限制已配置（≤ 10MB）

### 7.2 数据安全

- [ ] `[必检]` 数据库连接启用 SSL (`useSSL=true&requireSSL=true`)
- [ ] `[必检]` 用户密码存储使用 BCrypt (强度 10+)
- [ ] `[必检]` 敏感字段（密码、私钥）不打印到日志
- [ ] `[必检]` 审计日志记录: 用户、时间、IP、操作类型
- [ ] `[必检]` 三类账户密码各不相同 (admin/auditor/operator)
- [ ] `[必检]` 生产环境已设置 `lsc.evidence.auth.require-external-credentials: true`
- [ ] `[必检]` 区块链私钥 (`CHAIN_PK`) 通过 K8s Secret / Vault 注入，未落盘
- [ ] `[必检]` 多副本部署已启用 `RedisLoginAttemptService`（Redis 可达时自动激活）
- [ ] `[必检]` 多副本部署已启用 `RedisTokenBlacklistService`（登出/令牌轮换跨实例生效）
- [ ] `[建议]` 数据库账户最小权限原则
- [ ] `[建议]` 接入 LDAP/SSO 后移除内置账户

### 7.3 运维安全

- [ ] `[必检]` 生产环境日志级别设为 `WARN`
- [ ] `[必检]` Swagger/Knife4j 生产环境已关闭
- [ ] `[必检]` Actuator 端点限制为 `health,info`（或需认证）
- [ ] `[建议]` Druid 监控端点设置独立密码
- [ ] `[建议]` 灰度发布: 新版本先在 staging 环境验证
- [ ] `[建议]` 熔断降级: 链上异常时降级写入故障表

---

## 八、监控与告警

- [ ] `[必检]` Prometheus 采集已配置（`/actuator/metrics`）
- [ ] `[必检]` 存证接口 P99 延迟告警阈值: > 2000ms
- [ ] `[必检]` 存证失败率告警阈值: > 1%
- [ ] `[必检]` 区块链 RPC 延迟告警阈值: > 5000ms
- [ ] `[建议]` 数据库连接池活跃数告警: > 80%
- [ ] `[建议]` Redis 连接池活跃数告警: > 80%
- [ ] `[建议]` JWT 过期次数突增告警
- [ ] `[建议]` 日志文件配置: `max-history: 30, max-size: 500MB, total-size-cap: 5GB`

---

## 九、测试覆盖率

- [ ] `[必检]` 所有单元测试通过 (`mvn test`)
- [ ] `[必检]` 指令覆盖率 ≥ 90%
- [ ] `[必检]` 分支覆盖率 ≥ 85%
- [ ] `[必检]` 安全关键路径 (JwtAuthenticationFilter) 覆盖率 100%
- [ ] `[建议]` 查看 JaCoCo HTML 报告确认无关键分支遗漏

### 9.1 链上交互集成测试 (灰度部署前必做)

> 测试链 RPC 地址就绪后，通过环境变量注入运行链上集成测试：
> ```bash
> export CHAIN_RPC_URL=http://<测试链节点>:8545
> export CHAIN_CONTRACT=0x<合约地址>
> export CHAIN_PK=0x<测试账户私钥>
> mvn -o test -Dtest='SmartContractServiceChainIntegrationTest,EvidenceEndToEndChainIntegrationTest'
> ```

- [ ] `[必检]` 测试链节点可达 (`eth_blockNumber` 返回有效区块号)
- [ ] `[必检]` `writeHash` 真实上链成功返回 txHash (以 `0x` 开头)
- [ ] `[必检]` `queryByHash` 查询已上链数据返回结果
- [ ] `[必检]` `queryBlockNumber` 查询已确认交易区块号 ≥ 0
- [ ] `[必检]` 端到端流程: 存证→上链→查询→区块号确认 完整通过
- [ ] `[必检]` 稳定性测试: 连续上链 5 条成功率 ≥ 80%
- [ ] `[建议]` 异常恢复测试: 不可达 RPC 后恢复可达，上链正常

---

## 十、上线前最终确认

- [ ] `[必检]` `application-prod.yml` 中 `spring.profiles.active=prod`
- [ ] `[必检]` 服务启动后健康检查端点返回 `UP`
- [ ] `[必检]` 登录接口 `POST /api/auth/login` 可正常返回 Token
- [ ] `[必检]` 使用 Access Token 访问 `GET /api/evidence/list` 返回 200
- [ ] `[必检]` 无 Token 访问受保护接口返回 401
- [ ] `[必检]` 使用 Refresh Token 访问 API 返回 401（类型不匹配）
- [ ] `[必检]` 登出后使用原 Token 访问 API 返回 401（黑名单生效）
- [ ] `[必检]` 刷新令牌后旧 Refresh Token 不可再次使用（轮换+黑名单）
- [ ] `[必检]` 参数校验异常返回 400 + 结构化错误信息
- [ ] `[必检]` 服务已注册到 Nacos
- [ ] `[必检]` 日志正常输出到指定目录
- [ ] `[建议]` 执行一次完整的存证→查询→校验流程

---

## 签署确认

| 角色 | 姓名 | 日期 | 签字 |
|------|------|------|------|
| 开发负责人 | | | |
| 运维负责人 | | | |
| 安全负责人 | | | |

---

> 检查清单依据: [DEPLOYMENT.md](./DEPLOYMENT.md) v1.0.2
