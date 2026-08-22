# LSC V6.2 生产安装后安全清单 (SECURITY_POSTINSTALL)

> 适用版本：链盛通 LSC 消费权益凭证循环系统 V6.2 (AI 增强版)
> 关联审计项：audit_report.md I-09
> 执行时机：**首次部署到 staging / prod 环境后 24 小时内完成**，或每次从 SQL 脚本初始化数据库后立即执行

---

## 1. 默认账号盘点与强制轮换

### 1.1 已知默认账号（来自 `sql/lsc_system_v6.2.sql`）

| 表 | username / 标识 | 默认密码 / 凭据 | 角色 | 建议动作 |
|---|---|---|---|---|
| `admins` | `super_admin_01` | `Admin@2026`（开发/演示默认） | super_admin | **❌ 生产必须删除或改密** |
| `admins` | `super_admin_02` | `Admin@2026`（开发/演示默认） | super_admin | **❌ 生产必须删除或改密** |
| MySQL `init-db.sh` 中的 `MYSQL_PWD` / `MYSQL_ROOT_PASSWORD` | 由 `.env` 注入 | — | — | 首次登录后执行 `ALTER USER` 重新生成 root 密码并写入 KMS/Vault |
| Nacos 默认控制台 | `nacos / nacos` | — | — | ❌ 关闭非必要控制台或修改默认凭据 |
| Redis 哨兵/Nacos | 取决于 `docker/.env.example` | — | — | 参考第 4 节轮换所有中间件密码 |

### 1.2 首次登录必做（10 分钟内）

1. 以 `super_admin_01` 登录 lsc-admin-web 管理后台；
2. 进入「系统 → 管理员管理」，将 2 个默认超级管理员：
   - 要么直接删除后新建独立命名的管理员（每人一个账号，不共享）；
   - 要么点击「重置密码」，将密码改为 **≥ 16 位** 的强随机密码（字母大小写 + 数字 + 特殊字符 ≥ 3 类）；
3. 为每个管理员启用「二次登录验证」（若后端已接入 OTP/飞书扫码）；
4. 进入「操作日志」菜单，检查是否存在非本次上线窗口内的登录记录。

---

## 2. 如何重新生成 BCrypt 密码哈希替换 SQL 默认值

如需要在 `sql/lsc_system_v6.2.sql` 中替换默认管理员密码（例如改为 `MyNewStrongPwd@2026`），使用以下任一方式：

**方式 A：项目代码单元 (推荐) —— 与线上 PasswordEncoder 版本完全一致**

```java
// 在任意模块的 test/resources 下临时执行，或写在 lsc-common 的 JwtUtilTest 旁边
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
String raw = "MyNewStrongPwd@2026";
String hash = new BCryptPasswordEncoder(10).encode(raw);
// 把 hash 粘贴回 INSERT 的 password_hash 列
```

**方式 B：命令行 (htpasswd，需 apache2-utils)**

```bash
htpasswd -bnBC 10 "" MyNewStrongPwd@2026 | tr -d ':\n'
# 输出形如 $2a$10$xxx，粘贴回 SQL
```

替换完成后，删除第 457~460 行附近的「⚠️ 生产环境安全警告」块中的 `INSERT` 下方 2 行示例账号（或注释掉），确保 SQL 文件中不再携带任何可直接使用的默认凭据。

---

## 3. 中间件密钥 / 连接串轮换清单（首次部署 + 每 90 天）

全部通过环境变量 / K8s Secret / Vault 注入，**禁止硬编码在 `application.yml` 或 `pom.xml` 里**：

| # | 密钥类型 | 当前占位 | 建议来源 |
|---|---|---|---|
| 1 | MySQL 主库账号密码 | `MYSQL_PWD` | KMS / Vault 动态凭据 |
| 2 | Redis 密码 | `REDIS_PWD` | Vault 生成 32 位随机串 |
| 3 | Nacos 鉴权 token / 控制台密码 | `NACOS_AUTH_TOKEN` / 控制台 | 生产关闭公网访问 + 强密码 |
| 4 | JWT Signing Key（`JwtUtil`） | `JWT_SECRET` | ≥ 256-bit 随机，与 dev 值完全不同 |
| 5 | AES 数据密钥（`AesEncryptUtil`） | `AES_KEY` | ≥ 128-bit，建议使用 KMS envelope encryption |
| 6 | RabbitMQ / Seata / XXL-Job 的控制台密码 | 对应 env | 各自独立，不与其他服务共用 |
| 7 | 高德 / 百度地图 AK（lsc-map-service） | 配置中 | 生产使用应用白名单 + IP 绑定，避免 AK 泄露 |

---

## 4. 管理后台 / 管理 API 访问限制

- lsc-admin-service（8200）仅允许堡垒机 / VPN 网段访问，建议在 `cloud/nginx/lsc-admin.conf` 的 `allow` 段写死公司出口 IP；
- `docs/system-architecture.md` 声明的「管理域」与「业务域」在 K8s 中使用不同的 NetworkPolicy，禁止业务容器主动连接 8200 端口；
- Knife4j / Swagger-UI：在 `application-prod.yml` 中已统一设置 `knife4j.enable=false`，请上线后验证 `/swagger-ui.html` / `/doc.html` 均返回 404 或被鉴权过滤器拦截。

---

## 5. 上线后 48h 安全核对表

| # | 项目 | 核对方式 | 通过 |
|---|---|---|---|
| 1 | 所有默认账号密码已轮换 | 登录失败 3 次 + 新密码登录 1 次 | ☐ |
| 2 | 管理后台无公网裸暴露 | curl 公网IP:8200/doc.html 返回非 200 | ☐ |
| 3 | MySQL root 仅允许本地登录 | `SELECT user,host FROM mysql.user;` 无 `%` root | ☐ |
| 4 | actuator/health 详细信息需鉴权 | 访问 `/actuator/health` 返回摘要而非详情 | ☐ |
| 5 | JWT / AES 密钥与 dev 不同 | 比较 dev/prod 两份加密配置哈希 | ☐ |
| 6 | 管理域与业务域网络隔离 | 从业务 pod `telnet lsc-admin-service 8200` 应超时或拒绝 | ☐ |

---

## 6. 事件 / 联系人

- 发现默认账号仍在使用：按 P2 级别走安全工单，要求 4 小时内改密；
- 密钥疑似泄露：按 P1 级别立即吊销并重新签发，滚动所有相关 token；
- 安全事件联系人：见 `LSC_V6.2_Reports/SECURITY_AUDIT_REPORT.md` 附录。

*文档版本：1.0（2026-08-22，I-09 落实产物）*
