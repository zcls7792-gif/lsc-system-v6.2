# 存证服务 (lsc-evidence-service) 生产环境部署配置清单

> 版本: v1.0.2 | 最后更新: 2026-08-10
> 适用环境: Linux / Kubernetes / Docker

---

## 一、环境前置条件

### 1.1 运行时环境

| 组件 | 最低版本 | 推荐版本 | 备注 |
|------|----------|----------|------|
| JDK | 17.0.2 | 17.0.8+ (LTS) | 必须 JDK 17+，Spring Boot 3.2 要求 |
| OS | Linux Kernel 4.19+ | Ubuntu 22.04 LTS / CentOS 8+ | x86_64 / ARM64 |
| 内存 | 512MB | 2GB+ | 建议 JVM `-Xmx1g -Xms512m` |
| 磁盘 | 5GB | 50GB+ | 日志/临时文件预留 |

### 1.2 中间件依赖

| 中间件 | 版本要求 | 用途 | 连接配置项 |
|--------|----------|------|------------|
| MySQL | 8.0.x | 存证记录持久化 | `MYSQL_HOST` `MYSQL_USER` `MYSQL_PWD` |
| Redis | 6.x / 7.x | 分布式锁、批次计数、缓存 | `REDIS_HOST` `REDIS_PORT` `REDIS_PWD` |
| Nacos | 2.2+ | 服务注册与配置中心 | `NACOS_ADDR` `NACOS_NAMESPACE` |
| 区块链节点 | Geth/Quorum 等 | 哈希上链 | `CHAIN_RPC_URL` `CHAIN_CONTRACT` `CHAIN_PK` |

### 1.3 数据库表结构

生产环境需在 MySQL 中执行以下核心表的 DDL：

```sql
-- 存证记录表
CREATE TABLE IF NOT EXISTS `blockchain_records` (
  `id` BIGINT NOT NULL COMMENT '主键ID (雪花算法)',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型',
  `biz_id` VARCHAR(128) NOT NULL COMMENT '业务ID',
  `data_hash` VARCHAR(128) NOT NULL COMMENT '数据哈希 (0x前缀, 64位十六进制)',
  `data_payload` TEXT COMMENT '原始载荷',
  `chain_tx_hash` VARCHAR(128) COMMENT '链上交易哈希',
  `block_number` BIGINT COMMENT '区块号',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待上链 1=已上链 2=失败',
  `retry_count` TINYINT NOT NULL DEFAULT 0 COMMENT '重试次数',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_biz_type` (`biz_type`),
  KEY `idx_biz_id` (`biz_id`),
  KEY `idx_status_created` (`status`, `created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='存证记录表';

-- 每日快照表
CREATE TABLE IF NOT EXISTS `daily_snapshot_records` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `snapshot_date` DATE NOT NULL COMMENT '快照日期',
  `record_count` BIGINT NOT NULL COMMENT '存证记录数',
  `merkle_root` VARCHAR(128) NOT NULL COMMENT 'Merkle树根哈希',
  `chain_tx_hash` VARCHAR(128) COMMENT '链上交易哈希',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待上链 1=已上链 2=失败',
  `remark` VARCHAR(500) COMMENT '备注',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_snapshot_date` (`snapshot_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日快照表';

-- 故障转移表
CREATE TABLE IF NOT EXISTS `evidence_failover` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `blockchain_record_id` BIGINT COMMENT '关联存证记录ID',
  `biz_type` VARCHAR(32) NOT NULL,
  `biz_id` VARCHAR(128) NOT NULL,
  `data_hash` VARCHAR(128) NOT NULL,
  `fail_reason` VARCHAR(1000) COMMENT '失败原因',
  `retry_count` TINYINT NOT NULL DEFAULT 0,
  `next_retry_at` DATETIME COMMENT '下次重试时间',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '0=待处理 1=成功 2=最终失败',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_status_next_retry` (`status`, `next_retry_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='故障转移表';
```

---

## 二、HTTPS 启用配置

### 2.1 方案选择

| 方案 | 适用场景 | 复杂度 | 推荐度 |
|------|----------|--------|--------|
| **Nginx/Ingress TLS 终止** | Kubernetes / 反向代理 | 低 | ⭐⭐⭐⭐⭐ 推荐 |
| **Spring Boot 自带 SSL** | 单机部署 / 简单场景 | 中 | ⭐⭐⭐ |
| **mTLS 双向证书** | 高安全要求 | 高 | ⭐⭐ (特定场景) |

### 2.2 方案一：Nginx TLS 终止（推荐）

```nginx
# /etc/nginx/conf.d/lsc-evidence.conf
upstream lsc_evidence_backend {
    server 127.0.0.1:8113;
    keepalive 32;
}

server {
    listen 443 ssl http2;
    server_name evidence.prod.example.com;

    # SSL 证书配置 (使用 ACME/Let's Encrypt 或企业 CA)
    ssl_certificate     /etc/nginx/certs/evidence.prod.example.com.pem;
    ssl_certificate_key /etc/nginx/certs/evidence.prod.example.com.key;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers on;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 10m;

    # HSTS 安全头
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;

    # 反向代理到后端服务
    location /lsc-evidence/ {
        proxy_pass http://lsc_evidence_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_connect_timeout 30s;
        proxy_read_timeout 60s;
        proxy_send_timeout 60s;
    }

    # 健康检查端点 (可选不鉴权)
    location /lsc-evidence/actuator/ {
        proxy_pass http://lsc_evidence_backend;
        access_log off;
    }
}

# HTTP 强制跳转 HTTPS
server {
    listen 80;
    server_name evidence.prod.example.com;
    return 301 https://$host$request_uri;
}
```

### 2.3 方案二：Spring Boot 自带 SSL（备选）

在 `application-prod.yml` 中已预置 SSL 配置：

```yaml
server:
  ssl:
    enabled: true
    key-store: /etc/lsc-evidence/certs/server.p12
    key-store-password: ${SSL_KEY_STORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: server
```

**证书生成命令**（开发/测试环境）：
```bash
# 生成自签名证书 (仅用于测试)
keytool -genkeypair -alias server \
  -keyalg RSA -keysize 2048 \
  -keystore /etc/lsc-evidence/certs/server.p12 \
  -storepass ${SSL_KEY_STORE_PASSWORD} \
  -dname "CN=evidence.prod.example.com, OU=Evidence, O=LSC, L=Shanghai, S=Shanghai, C=CN" \
  -validity 3650
```

**生产环境必须使用权威 CA 颁发的证书**（DigiCert、Let's Encrypt 等），禁止使用自签名证书。

### 2.4 TLS 安全加固清单

- [ ] 仅启用 TLS 1.2 和 TLS 1.3，禁用 TLS 1.0/1.1
- [ ] 使用强加密套件 (ECDHE + AES-GCM)
- [ ] 启用 HSTS (HTTP Strict Transport Security)，`max-age >= 31536000`
- [ ] 启用 OCSP Stapling 减少证书校验延迟
- [ ] 定期轮换证书（建议 90 天内）
- [ ] 配置 `X-Content-Type-Options: nosniff` 防止 MIME 嗅探
- [ ] 配置 `X-Frame-Options: DENY` 防止点击劫持

---

## 三、JWT 令牌策略

### 3.1 双令牌模型

| 令牌类型 | 有效期 | 用途 | 存储位置 |
|----------|--------|------|----------|
| **Access Token** | 2 小时 (7200s) | API 接口鉴权 | 内存 / SessionStorage |
| **Refresh Token** | 7 天 (604800s) | 换取新 Access Token | HttpOnly Cookie |

### 3.2 接口认证流程

```
客户端                          服务端
  │                              │
  │── POST /api/auth/login ──────►│  (无认证，白名单)
  │   {username, password}       │
  │                              │── 验证密码 (BCrypt)
  │                              │── 生成 Access Token (type=access, 2h)
  │                              │── 生成 Refresh Token (type=refresh, 7d)
  │◄── {accessToken, refreshToken}──│
  │                              │
  │── GET /api/evidence/list ─────►│  (Authorization: Bearer <accessToken>)
  │                              │── 过滤器验签 + 校验 type=access
  │◄── {data} ───────────────────│
  │                              │
  │   (Access Token 过期)         │
  │── POST /api/auth/refresh ─────►│  (Authorization: Bearer <refreshToken>)
  │                              │── 验证 Refresh Token (type=refresh)
  │◄── {newAccessToken} ─────────│
```

### 3.3 密钥管理

```bash
# 生成 256 位 (32 字节) 随机密钥
openssl rand -base64 32

# 输出示例: Kx7m2NpQ9vR4tY1wZ6aB3cD8eF5gH0jL2mN7oP9qRsT4uV1wX3yZ5aB7cD9eF2gH4=
```

**密钥安全要求**：
- [ ] 密钥长度 ≥ 256 位 (32 字节)，使用加密随机生成
- [ ] 通过环境变量 `JWT_SECRET` 注入，禁止写入代码仓库
- [ ] 使用 Nacos 配置中心管理密钥，支持动态刷新
- [ ] 密钥每 90 天轮换一次（生产环境）
- [ ] 密钥轮换时需保证旧密钥仍可验证（灰度切换期 24h）

### 3.4 过期时间配置

```yaml
# application-prod.yml
lsc:
  evidence:
    jwt:
      # Access Token: 生产建议 30min ~ 2h
      expiration-ms: ${JWT_ACCESS_EXPIRATION_MS:7200000}
      # Refresh Token: 生产建议 7 ~ 30 天
      refresh-expiration-ms: ${JWT_REFRESH_EXPIRATION_MS:604800000}
```

**不同场景推荐值**：

| 场景 | Access Token | Refresh Token |
|------|-------------|---------------|
| 普通 Web 应用 | 2 小时 | 7 天 |
| 金融/支付类 | 30 分钟 | 不使用 Refresh（重新登录） |
| 移动端 App | 1 小时 | 30 天 |
| 后台管理系统 | 2 小时 | 30 天 |

---

## 四、生产环境配置项清单

### 4.1 必须配置的环境变量

| 变量名 | 说明 | 示例 | 必须 |
|--------|------|------|------|
| `MYSQL_HOST` | MySQL 主机 | `10.0.0.1:3306` | ✅ |
| `MYSQL_USER` | MySQL 用户名 | `lsc_evidence` | ✅ |
| `MYSQL_PWD` | MySQL 密码 | (强密码) | ✅ |
| `REDIS_HOST` | Redis 主机 | `10.0.0.2:6379` | ✅ |
| `REDIS_PWD` | Redis 密码 | (强密码) | ✅ |
| `JWT_SECRET` | JWT 签名密钥 | (32字节随机) | ✅ |
| `ADMIN_PASSWORD` | 管理员账户密码 | (强密码) | ✅ |
| `AUDITOR_PASSWORD` | 审计员账户密码 | (强密码) | ✅ |
| `OPERATOR_PASSWORD` | 操作员账户密码 | (强密码) | ✅ |
| `CHAIN_RPC_URL` | 区块链节点 RPC | `https://chain-rpc.example.com` | ✅ |
| `CHAIN_CONTRACT` | 智能合约地址 | `0x...` | ✅ |
| `CHAIN_PK` | 合约调用私钥 | (Hex) | ✅ |
| `NACOS_ADDR` | Nacos 地址 | `10.0.0.3:8848` | ✅ |
| `NACOS_CLUSTER` | Nacos 集群名 | `cn-east-prod` | ✅ |
| `DRUID_MONITOR_PWD` | Druid 监控页密码 | (强密码) | 启用监控页时 |
| `SSL_KEY_STORE` | SSL 证书路径 | `/etc/.../server.p12` | HTTPS 启用时 |
| `SSL_KEY_STORE_PASSWORD` | SSL 证书密码 | | HTTPS 启用时 |
| `LOG_PATH` | 日志输出目录 | `/var/log/lsc-evidence` | 建议 |

### 4.2 可选的环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `JWT_ACCESS_EXPIRATION_MS` | 7200000 (2h) | Access Token 有效期 |
| `JWT_REFRESH_EXPIRATION_MS` | 604800000 (7d) | Refresh Token 有效期 |
| `AUTH_MAX_LOGIN_ATTEMPTS` | 5 | 连续登录失败锁定阈值 |
| `AUTH_LOCKOUT_DURATION_MS` | 300000 (5min) | 账户锁定时长 (毫秒) |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `REDIS_DB` | 13 | Redis 数据库编号 |
| `MYSQL_DB` | `lsc_evidence` | 数据库名 |
| `NACOS_NAMESPACE` | `production` | Nacos 命名空间 |
| `NACOS_GROUP` | `DEFAULT_GROUP` | Nacos 分组 |
| `DRUID_MONITOR_USER` | `admin` | Druid 监控页用户名 |
| `APP_VERSION` | `1.0.0` | 应用版本号（注册元数据） |
| `APP_REGION` | `cn-east` | 应用部署区域（注册元数据） |
| `SSL_KEY_ALIAS` | `server` | SSL 证书别名 |

### 4.3 环境变量详细参考

本节给出每个变量的用途、生成方式、安全注意事项及合规要求。所有敏感变量**禁止**写入代码仓库、镜像构建参数或日志。

#### 4.3.1 鉴权类

##### `JWT_SECRET`

| 属性 | 值 |
|------|----|
| 用途 | HMAC-SHA256 签名密钥，用于签发与校验 Access Token / Refresh Token |
| 类型 | 字符串 (Base64 编码的 32 字节随机数据) |
| 长度要求 | ≥ 32 字节解码后 (≥ 44 字符 Base64) |
| 注入位置 | `lsc.evidence.jwt.secret` |
| 失败行为 | 启动时 `JwtUtil` 校验长度不足即抛异常，应用启动失败 |
| 是否轮换 | 建议 90 天轮换；轮换时需 24h 灰度期同时接受新旧密钥 |

**生成方式**：

```bash
# 方式一: openssl (推荐)
export JWT_SECRET=$(openssl rand -base64 32)
echo "JWT_SECRET=$JWT_SECRET"

# 方式二: /dev/urandom
export JWT_SECRET=$(head -c 32 /dev/urandom | base64)

# 方式三: Java (无 openssl 环境时)
java -e 'System.out.println(java.util.Base64.getEncoder().encodeToString(java.security.SecureRandom.getSeed(32)))'
```

**安全注意事项**：
- 严禁提交到 Git；使用 `.gitignore` 排除 `.env` 文件
- 严禁作为 Docker build arg 或写入 Dockerfile ENV
- K8s 部署必须通过 Secret + secretKeyRef 注入
- 密钥泄漏后必须立即轮换并吊销所有未过期 Token（需配合 Redis Token 黑名单）
- 不同环境（dev/staging/prod）必须使用不同密钥

---

##### `ADMIN_PASSWORD` / `AUDITOR_PASSWORD` / `OPERATOR_PASSWORD`

| 属性 | 值 |
|------|----|
| 用途 | 三类内置账户的登录密码，注入后经 BCrypt 编码存入内存 USER_STORE |
| 类型 | 字符串 (明文) |
| 长度要求 | ≥ 12 字符，建议 ≥ 16 字符 |
| 复杂度要求 | 必须包含大小写字母、数字、特殊符号 |
| 注入位置 | `lsc.evidence.auth.users.{admin,auditor,operator}.password` |
| 失败行为 | 生产环境 (`require-external-credentials: true`) 未配置任一密码则启动失败 |

**生成方式**：

```bash
# 生成 16 字符强密码 (大小写+数字+符号)
generate_password() {
  local chars='A-Za-z0-9!@#$%^&*()-_=+'
  head -c 16 /dev/urandom | tr -dc "$chars" | head -c 16
  echo
}

export ADMIN_PASSWORD=$(generate_password)
export AUDITOR_PASSWORD=$(generate_password)
export OPERATOR_PASSWORD=$(generate_password)

# 或使用 openssl
export ADMIN_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 16)
```

**安全注意事项**：
- 三类账户密码必须各不相同
- 注入后服务端通过 BCrypt (强度 10) 编码存储，明文不落盘
- 日志中严禁打印密码原文
- 建议接入企业 LDAP/SSO 后移除内置账户
- 密码定期轮换（建议 90 天）

**角色权限矩阵**：

| 角色 | 用户名 | 权限范围 |
|------|--------|----------|
| ADMIN | `admin` | 全部接口 + 用户管理 |
| AUDITOR | `auditor` | 只读接口（查询、列表、详情、统计） |
| OPERATOR | `operator` | 存证、补传、快照类写接口 |

---

##### `AUTH_MAX_LOGIN_ATTEMPTS` / `AUTH_LOCKOUT_DURATION_MS`

| 属性 | 值 |
|------|----|
| 用途 | 登录尝试限制：连续失败 `MAX_LOGIN_ATTEMPTS` 次后锁定账户 `LOCKOUT_DURATION_MS` 毫秒 |
| 注入位置 | `lsc.evidence.auth.max-login-attempts` / `lsc.evidence.auth.lockout-duration-ms` |
| 默认值 | 5 次 / 300000ms (5 分钟) |
| 失败行为 | 使用默认值，不影响启动 |

**实现架构 (多副本部署)**：

| 部署模式 | 实现 | Bean 条件 | Redis Key |
|----------|------|-----------|-----------|
| 单机/standalone | `InMemoryLoginAttemptService` | `@ConditionalOnMissingBean(RedisLoginAttemptService.class)` | N/A (内存 Map) |
| 多副本/prod | `RedisLoginAttemptService` | `@ConditionalOnBean(StringRedisTemplate.class)` | `lsc:evidence:login:fail:count:{username}` / `lsc:evidence:login:fail:lock:{username}` |

**安全注意事项**：
- 多副本部署必须配置 Redis，否则各实例独立计数，锁定可被绕过
- 失败计数 TTL = 锁定时长 + 60s 缓冲，避免计数永不消失
- 锁定状态下直接返回 429，不再执行密码比对

---

##### Token 黑名单 (Refresh Token Rotation + Logout)

| 属性 | 值 |
|------|----|
| 用途 | 主动撤销 Access Token / Refresh Token，支持登出和令牌轮换 |
| 实现接口 | `TokenBlacklistService` |
| JTI 计算 | SHA-256(token)，避免在 Redis 中存储原始 Token |

**实现架构 (多副本部署)**：

| 部署模式 | 实现 | Bean 条件 | Redis Key |
|----------|------|-----------|-----------|
| 单机/standalone | `InMemoryTokenBlacklistService` | `@ConditionalOnMissingBean(RedisTokenBlacklistService.class)` | N/A (内存 Map) |
| 多副本/prod | `RedisTokenBlacklistService` | `@ConditionalOnBean(StringRedisTemplate.class)` | `lsc:evidence:token:blacklist:{jti}` |

**使用场景**：
1. **登出** (`POST /api/auth/logout`): 将当前 Access Token 加入黑名单，TTL = 剩余有效期
2. **刷新令牌轮换** (`POST /api/auth/refresh`): 旧 Refresh Token 加入黑名单，签发新 Access + Refresh Token
3. **密钥轮换**: 密钥泄漏后通过黑名单批量撤销未过期 Token

**安全注意事项**：
- 多副本部署必须配置 Redis，否则登出仅对当前实例生效
- `JwtAuthenticationFilter` 和 `/api/auth/refresh` 端点均会检查黑名单
- Token 过期后 Redis Key 自动清理 (TTL 与令牌剩余有效期一致)

---

#### 4.3.2 数据存储类

##### `MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PWD`

| 属性 | 值 |
|------|----|
| 用途 | MySQL 8.0 主库连接 |
| 格式 | `MYSQL_HOST=host:port` (默认端口 3306) |
| 注入位置 | `spring.datasource.url` / `username` / `password` |
| 连接要求 | 生产环境强制 `useSSL=true&requireSSL=true&verifyServerCertificate=true` |

**数据库账户创建**：

```sql
-- 创建专用的最小权限账户
CREATE USER 'lsc_evidence'@'%' IDENTIFIED BY '<强密码>' REQUIRE SSL;

-- 授权（最小权限原则）
GRANT SELECT, INSERT, UPDATE, DELETE ON lsc_evidence.* TO 'lsc_evidence'@'%';

-- 禁止 DDL 权限（schema 变更走 DBA 工单）
-- 禁止 SUPER、PROCESS 等管理员权限
FLUSH PRIVILEGES;
```

**安全注意事项**：
- 应用账户严禁授予 DDL (CREATE/ALTER/DROP) 权限
- 必须启用 SSL 连接，禁止明文传输
- 建议配置主从读写分离（写主库，读从库）
- 密码使用 K8s Secret 或 HashiCorp Vault 管理
- 数据库连接池密码严禁写入 Druid 监控页的明文配置

---

##### `REDIS_HOST` / `REDIS_PORT` / `REDIS_PWD` / `REDIS_DB`

| 属性 | 值 |
|------|----|
| 用途 | Redis 用于分布式锁、批次计数、缓存 |
| 默认端口 | 6379 |
| 默认 DB | 13 (与其它服务隔离) |
| 注入位置 | `spring.data.redis.*` 与 `redisson.config` |

**安全注意事项**：
- 生产环境必须启用 Redis ACL 或 `requirepass`
- 建议启用 Redis TLS (stunnel 或 Redis 6+ 原生 TLS)
- 禁止使用 `FLUSHALL`、`FLUSHDB` 权限的应用账户
- `REDIS_DB` 与其它服务隔离，避免 key 冲突

---

#### 4.3.3 区块链类

##### `CHAIN_RPC_URL`

| 属性 | 值 |
|------|----|
| 用途 | 区块链节点 JSON-RPC 接入地址 |
| 格式 | `http://` 或 `https://` 开头 |
| 注入位置 | `lsc.evidence.chain.rpc-url` |

**安全注意事项**：
- 生产环境建议使用 `https://` 或 VPN 内网访问
- RPC 节点建议部署在专用网络，避免公网暴露
- 配置节点白名单，仅允许应用服务器 IP 访问

---

##### `CHAIN_CONTRACT`

| 属性 | 值 |
|------|----|
| 用途 | 已部署的存证智能合约地址 |
| 格式 | `0x` + 40 位十六进制 (EVM 链) |
| 注入位置 | `lsc.evidence.chain.contract-address` |

**校验**：应用启动时建议校验格式 `^0x[0-9a-fA-F]{40}$`。

---

##### `CHAIN_PK`

| 属性 | 值 |
|------|----|
| 用途 | 调用合约的账户私钥，用于签名上链交易 |
| 格式 | `0x` + 64 位十六进制 (EVM 链) |
| 注入位置 | `lsc.evidence.chain.private-key` |
| 敏感级别 | **最高**（泄漏即资金与签名权全失） |

**安全注意事项**：
- 严禁提交到代码仓库或镜像
- 必须使用 K8s Secret、Vault 或 HSM 管理
- 建议使用专用低权限账户，仅授权调用 `writeHash` 方法
- 服务内存中应仅短暂持有，建议使用后清零
- 监控该账户的链上交易，异常交易立即告警
- 密钥泄漏应急流程：立即转移资产 → 重新部署合约 → 更新 PK 配置

---

#### 4.3.4 服务发现类

##### `NACOS_ADDR` / `NACOS_NAMESPACE` / `NACOS_GROUP` / `NACOS_CLUSTER`

| 属性 | 值 |
|------|----|
| 用途 | Nacos 服务注册与配置中心接入 |
| 格式 | `NACOS_ADDR=host:port` |
| 默认 namespace | `production` |
| 注入位置 | `spring.cloud.nacos.*` |

**安全注意事项**：
- 生产环境 Nacos 必须开启鉴权（`nacos.core.auth.enabled=true`）
- namespace 严禁使用 `public`（默认公开空间）
- 配置中心存放的 `lsc-evidence-prod.yaml` 同样不能包含明文密钥，敏感项应通过 `${VAR}` 引用环境变量

---

#### 4.3.5 HTTPS 类

##### `SSL_KEY_STORE` / `SSL_KEY_STORE_PASSWORD` / `SSL_KEY_ALIAS`

| 属性 | 值 |
|------|----|
| 用途 | Spring Boot 内嵌 SSL (方案二，备选) |
| 格式 | PKCS12 (.p12) |
| 注入位置 | `server.ssl.*` |

**生成与转换**：

```bash
# 1. 从 PEM 转为 PKCS12 (适用于 Let's Encrypt 证书)
openssl pkcs12 -export \
  -in fullchain.pem \
  -inkey privkey.pem \
  -out server.p12 \
  -name server \
  -password pass:${SSL_KEY_STORE_PASSWORD}

# 2. 校验
keytool -list -v -keystore server.p12 -storepass ${SSL_KEY_STORE_PASSWORD}
```

**安全注意事项**：
- 生产环境必须使用 CA 颁发的证书，禁用自签名
- 证书文件权限设为 `600`，属主为运行用户
- 证书到期前 30 天必须续期
- 推荐方案：Nginx/Ingress TLS 终止（参见 2.2），此方案仅作备选

---

#### 4.3.6 监控与日志类

##### `DRUID_MONITOR_USER` / `DRUID_MONITOR_PWD`

| 属性 | 值 |
|------|----|
| 用途 | Druid SQL 监控页 (`/druid/*`) 的访问认证 |
| 默认用户名 | `admin` |
| 注入位置 | `spring.datasource.druid.stat-view-servlet.*` |

**安全注意事项**：
- 生产环境建议直接关闭 Druid 监控页 (`stat-view-servlet.enabled: false`)
- 如必须开启，必须配置强密码并限制 IP 白名单
- 监控页禁止公网访问

---

##### `LOG_PATH`

| 属性 | 值 |
|------|----|
| 用途 | 日志文件输出目录 |
| 默认值 | `/var/log/lsc-evidence` |
| 注入位置 | `logging.file.name` |

**安全注意事项**：
- 日志目录权限 `750`，属主为运行用户
- 日志严禁记录密码、Token、私钥等敏感字段
- 配置日志滚动 (`max-history: 30`, `max-size: 500MB`, `total-size-cap: 5GB`)
- 建议接入集中式日志系统（ELK/Loki），并做脱敏处理

---

### 4.4 环境变量加载顺序与优先级

Spring Boot 配置加载优先级（高 → 低）：

1. **命令行参数** `-Dspring.profiles.active=prod`
2. **环境变量** (本节所有变量)
3. **Nacos 配置中心** `lsc-evidence-prod.yaml` 中的 `${VAR}` 占位由环境变量回填
4. **`application-prod.yml`** 内的默认值

> 关键原则：敏感值只在环境变量中提供，`application-prod.yml` 仅保留 `${VAR}` 占位与默认值兜底（生产环境强制不兜底）。

### 4.5 .env 文件示例 (仅用于本地/开发)

> 严禁将 `.env` 提交到代码仓库；建议加入 `.gitignore`。

```bash
# .env.example (复制为 .env 后填入实际值)

# ============ JWT ============
# 生成: openssl rand -base64 32
JWT_SECRET=__REPLACE_ME__

# ============ 用户凭据 ============
# 至少 12 字符，含大小写+数字+符号
ADMIN_PASSWORD=__REPLACE_ME__
AUDITOR_PASSWORD=__REPLACE_ME__
OPERATOR_PASSWORD=__REPLACE_ME__

# ============ 登录安全 (可选) ============
# 连续失败 5 次后锁定，锁定 5 分钟
AUTH_MAX_LOGIN_ATTEMPTS=5
AUTH_LOCKOUT_DURATION_MS=300000

# ============ MySQL ============
MYSQL_HOST=127.0.0.1:3306
MYSQL_USER=lsc_evidence
MYSQL_PWD=__REPLACE_ME__

# ============ Redis ============
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
REDIS_PWD=__REPLACE_ME__
REDIS_DB=13

# ============ 区块链 ============
CHAIN_RPC_URL=http://127.0.0.1:8545
CHAIN_CONTRACT=0x0000000000000000000000000000000000000000
CHAIN_PK=0x0000000000000000000000000000000000000000000000000000000000000000

# ============ Nacos ============
NACOS_ADDR=127.0.0.1:8848
NACOS_NAMESPACE=production
NACOS_CLUSTER=cn-east-prod

# ============ HTTPS (可选) ============
SSL_KEY_STORE=/etc/lsc-evidence/certs/server.p12
SSL_KEY_STORE_PASSWORD=__REPLACE_ME__
SSL_KEY_ALIAS=server

# ============ 监控 ============
DRUID_MONITOR_USER=admin
DRUID_MONITOR_PWD=__REPLACE_ME__

# ============ 日志 ============
LOG_PATH=/var/log/lsc-evidence
```

加载方式（仅本地调试）：

```bash
cp .env.example .env
# 编辑 .env 填入实际值
set -a && source .env && set +a
java -Dspring.profiles.active=prod -jar lsc-evidence-service.jar
```

### 4.6 JVM 启动参数

```bash
# 标准生产环境 JVM 参数
java \
  -server \
  -Xms512m -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/var/log/lsc-evidence/heapdump.hprof \
  -XX:+ExitOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=prod \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=Asia/Shanghai \
  -jar lsc-evidence-service-1.0.0.jar
```

---

## 五、部署方式

### 5.1 Docker 部署

```dockerfile
# Dockerfile
FROM eclipse-temurin:17-jre-alpine
LABEL maintainer="lsc-evidence-team"

RUN addgroup -S evidence && adduser -S evidence -G evidence

WORKDIR /app

COPY target/lsc-evidence-service-*.jar app.jar
COPY src/main/resources/application-prod.yml application-prod.yml

# 创建证书目录
RUN mkdir -p /etc/lsc-evidence/certs /var/log/lsc-evidence

# 暴露 HTTP 和 HTTPS 端口
EXPOSE 8113

USER evidence

ENTRYPOINT ["java", "-server", \
  "-Xms512m", "-Xmx2g", \
  "-XX:+UseG1GC", \
  "-XX:MaxGCPauseMillis=200", \
  "-XX:+HeapDumpOnOutOfMemoryError", \
  "-XX:HeapDumpPath=/var/log/lsc-evidence/heapdump.hprof", \
  "-Dspring.profiles.active=prod", \
  "-Dfile.encoding=UTF-8", \
  "-Duser.timezone=Asia/Shanghai", \
  "-jar", "app.jar"]
```

```bash
# 构建镜像
docker build -t lsc-evidence-service:1.0.0 .

# 运行容器
docker run -d \
  --name lsc-evidence-prod \
  --restart=always \
  -p 8113:8113 \
  -e MYSQL_HOST=10.0.0.1:3306 \
  -e MYSQL_USER=lsc_evidence \
  -e MYSQL_PWD=strong_password \
  -e JWT_SECRET=<32字节随机密钥> \
  -v /etc/lsc-evidence/certs:/etc/lsc-evidence/certs:ro \
  -v /var/log/lsc-evidence:/var/log/lsc-evidence \
  lsc-evidence-service:1.0.0
```

### 5.2 Kubernetes 部署

```yaml
# k8s-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lsc-evidence-service
  namespace: production
  labels:
    app: lsc-evidence
    version: 1.0.0
spec:
  replicas: 2
  selector:
    matchLabels:
      app: lsc-evidence
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 0
      maxSurge: 1
  template:
    metadata:
      labels:
        app: lsc-evidence
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8113"
        prometheus.io/path: "/actuator/metrics"
    spec:
      containers:
      - name: lsc-evidence
        image: lsc-evidence-service:1.0.0
        ports:
        - containerPort: 8113
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "2"
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: MYSQL_HOST
          valueFrom:
            secretKeyRef:
              name: lsc-evidence-secrets
              key: mysql-host
        - name: MYSQL_USER
          valueFrom:
            secretKeyRef:
              name: lsc-evidence-secrets
              key: mysql-user
        - name: MYSQL_PWD
          valueFrom:
            secretKeyRef:
              name: lsc-evidence-secrets
              key: mysql-pwd
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: lsc-evidence-secrets
              key: jwt-secret
        - name: REDIS_PWD
          valueFrom:
            secretKeyRef:
              name: lsc-evidence-secrets
              key: redis-pwd
        readinessProbe:
          httpGet:
            path: /lsc-evidence/actuator/health
            port: 8113
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /lsc-evidence/actuator/health
            port: 8113
          initialDelaySeconds: 60
          periodSeconds: 30
        volumeMounts:
        - name: certs
          mountPath: /etc/lsc-evidence/certs
          readOnly: true
        - name: logs
          mountPath: /var/log/lsc-evidence
      volumes:
      - name: certs
        secret:
          secretName: lsc-evidence-certs
      - name: logs
        emptyDir: {}
---
apiVersion: v1
kind: Service
metadata:
  name: lsc-evidence-service
  namespace: production
spec:
  selector:
    app: lsc-evidence
  ports:
  - port: 8113
    targetPort: 8113
  type: ClusterIP
```

---

## 六、安全加固检查清单

### 6.1 JWT 安全

- [ ] Access Token 有效期 ≤ 2 小时
- [ ] Refresh Token 有效期 ≤ 30 天
- [ ] 密钥长度 ≥ 256 位 (32 字节)
- [ ] 密钥通过环境变量或配置中心注入，无硬编码
- [ ] 密钥定期轮换（90 天）
- [ ] 签名算法使用 HMAC-SHA256
- [ ] 过滤器强制校验 Token type，Refresh Token 不可用于 API
- [ ] 登录接口实施速率限制（建议 ≤ 5 次/分钟/IP）
- [ ] 连续登录失败 5 次后锁定账户 30 分钟

### 6.2 传输安全

- [ ] 全链路 HTTPS (TLS 1.2+)
- [ ] HSTS 头 (max-age ≥ 1 年)
- [ ] 请求体大小限制（Spring Boot 默认 1MB，建议 ≤ 10MB）
- [ ] 输入校验：所有接口参数使用 Jakarta Validation
- [ ] SQL 注入防护：使用 MyBatis-Plus 参数化查询
- [ ] XSS 防护：响应头 `X-Content-Type-Options: nosniff`
- [ ] CSRF 防护：JWT 方案天然免疫 CSRF

### 6.3 数据安全

- [ ] 数据库连接使用 SSL (`useSSL=true&requireSSL=true`)
- [ ] 密码存储使用 BCrypt (强度 10+)
- [ ] 敏感字段（密码、私钥）不打印到日志
- [ ] 审计日志记录所有存证操作的用户、时间、IP
- [ ] 定期备份数据库（每日全量 + 实时 binlog）
- [ ] 生产环境数据库账户最小权限原则

### 6.4 运维安全

- [ ] 日志级别生产环境设为 WARN
- [ ] 关闭 Actuator Metrics 暴露（或限制为 `health,info`）
- [ ] Swagger/Knife4j 生产环境关闭
- [ ] Druid 监控端点设置独立密码
- [ ] 灰度发布：新版本先在 staging 环境验证
- [ ] 配置熔断降级：链上异常时降级写入故障表

---

## 七、监控与告警

### 7.1 关键指标

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| `evidence_save_latency_p99` | 存证接口 P99 延迟 | > 2000ms |
| `evidence_save_error_rate` | 存证失败率 | > 1% |
| `evidence_chain_rpc_latency` | 区块链 RPC 延迟 | > 5000ms |
| `evidence_jwt_expiry_count` | JWT 过期次数 | 突增时告警 |
| `evidence_db_pool_active` | 数据库连接池活跃数 | > 80% max |
| `evidence_redis_pool_active` | Redis 连接池活跃数 | > 80% max |

### 7.2 健康检查端点

| 端点 | 说明 | 认证 |
|------|------|------|
| `GET /actuator/health` | 服务健康状态 | 否 |
| `GET /api/auth/health` | 认证模块健康 | 否 |
| `GET /api/auth/me` | 当前用户信息 | 是 (Access Token) |

### 7.3 日志配置

```yaml
# application-prod.yml
logging:
  level:
    root: WARN
    com.lianshengtong.evidence: WARN
    com.lianshengtong.common: WARN
    com.baomidou.mybatisplus: ERROR
  file:
    name: /var/log/lsc-evidence/lsc-evidence.log
    max-history: 30
    max-size: 500MB
    total-size-cap: 5GB
```

---

## 八、测试覆盖率

> 详细的覆盖率报告请参考 [COVERAGE_REPORT.md](./COVERAGE_REPORT.md)

### 8.1 覆盖率指标（2026-08-08）

| 指标 | 已覆盖 | 未覆盖 | 覆盖率 |
|------|--------|--------|--------|
| 指令 (Instructions) | 3,070 | 172 | **94.7%** |
| 分支 (Branches) | 205 | 16 | **92.0%** |
| 行 (Lines) | 675 | 56 | **92.3%** |

### 8.2 关键模块覆盖率

| 模块 | 指令覆盖 | 分支覆盖 | 说明 |
|------|----------|----------|------|
| JwtAuthenticationFilter | 100% | 100% | JWT 认证过滤器（安全关键路径） |
| EvidenceFlushScheduler | 100% | 100% | 定时刷新调度器 |
| EvidenceServiceImpl | 97.1% | 91.7% | 业务核心实现 |
| SmartContractServiceImpl | 94.9% | 93.5% | 智能合约交互 |
| AsyncChainWriter | 90.1% | 93.8% | 异步上链写入器 |
| JwtUtil | 94.0% | 86.4% | JWT 工具类 |

### 8.3 运行覆盖率测试

```bash
cd /workspace/lsc-evidence-service
mvn clean test jacoco:report
# 查看报告: target/jacoco/index.html
```

---

## 九、版本与变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0.2 | 2026-08-10 | 新增环境变量详细参考 (4.3)、加载优先级 (4.4)、.env 示例 (4.5)；补全 ADMIN/AUDITOR/OPERATOR_PASSWORD 等凭据变量 |
| v1.0.1 | 2026-08-08 | 新增测试覆盖率章节（第九章），集成 COVERAGE_REPORT.md |
| v1.0.0 | 2026-08-08 | 初始版本：JWT 双令牌模型、HTTPS 配置、生产部署清单 |

---

**文档维护**: lsc-evidence-team | **审核**: 安全组 + 运维组
