# 链盛通LSC系统 V6.2 - 云服务器部署操作手册

## 1. 云服务器配置要求

### 1.1 推荐配置方案

#### 方案A: 单机部署 (推荐测试/小型生产)

| 项目 | 规格 | 说明 |
|------|------|------|
| **云服务商** | 阿里云ECS / 腾讯云CVM / 华为云ECS / AWS EC2 | 任选 |
| **操作系统** | Alibaba Cloud Linux 3 / Ubuntu 22.04 LTS | 64位 |
| **CPU** | 8核 vCPU | 推荐使用通用型(g7/l7) |
| **内存** | 32 GB | DDR4 |
| **系统盘** | 100 GB SSD | 云盘 |
| **数据盘** | 500 GB NVMe SSD | 用于MySQL/Redis数据 |
| **带宽** | 100 Mbps (固定) | 按需升级 |
| **地域** | 华东1 (杭州) / 华东2 (上海) | 靠近用户 |

**适用场景**: 日订单 < 10万, 日活用户 < 5万

---

#### 方案B: 集群部署 (推荐中型生产)

| 角色 | 数量 | 规格 | 用途 |
|------|------|------|------|
| **应用服务器** | 3台 | 8C32G | Nginx + 全部微服务 |
| **数据库服务器** | 2台 | 8C32G + 500GB SSD | MySQL主从 |
| **Redis集群** | 3台 | 4C16G + 200GB SSD | Redis Cluster |
| **消息队列** | 1台 | 4C16G | RabbitMQ |
| **配置中心** | 3台 | 4C8G | Nacos集群 |
| **负载均衡** | 1台 | SLB/CLB | 四层+七层转发 |
| **对象存储** | - | OSS/COS | 文件存储 |

**适用场景**: 日订单 10-50万, 日活用户 5-20万

---

#### 方案C: K8s集群 (推荐大型生产)

| 角色 | 数量 | 规格 | 用途 |
|------|------|------|------|
| **K8s Master** | 3台 | 8C16G | Kubernetes控制面 |
| **K8s Worker** | 5-10台 | 16C64G | 应用工作负载 |
| **数据库** | RDS | 高可用 | 托管MySQL |
| **Redis** | 集群版 | 高可用 | 托管Redis |
| **消息队列** | 托管版 | 高可用 | 托管RabbitMQ |
| **Serverless** | FC/CS | 按需 | AI服务/定时任务 |

**适用场景**: 日订单 > 50万, 日活用户 > 20万

---

### 1.2 云平台安全组配置

```
┌─────────────────────────────────────────────────────────┐
│              云安全组 / 防火墙规则                        │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  入站规则(Inbound):                                      │
│  ┌──────────┬─────────┬──────────────┬────────────────┐ │
│  │ 协议     │ 端口    │ 来源          │ 用途            │ │
│  ├──────────┼─────────┼──────────────┼────────────────┤ │
│  │ TCP      │ 22      │ 你的IP       │ SSH远程管理     │ │
│  │ TCP      │ 80      │ 0.0.0.0/0    │ HTTP访问        │ │
│  │ TCP      │ 443     │ 0.0.0.0/0    │ HTTPS访问      │ │
│  │ TCP      │ 8000    │ 内网         │ API网关(内部)   │ │
│  │ TCP      │ 8101-8201 │ 内网       │ 微服务(内部)   │ │
│  │ TCP      │ 9090    │ 内网         │ Prometheus     │ │
│  └──────────┴─────────┴──────────────┴────────────────┘ │
│                                                         │
│  出站规则(Outbound):                                    │
│  ┌──────────┬─────────┬──────────────┬────────────────┐ │
│  │ 协议     │ 端口    │ 目标          │ 用途            │ │
│  ├──────────┼─────────┼──────────────┼────────────────┤ │
│  │ TCP      │ 443     │ 0.0.0.0/0    │ HTTPS出站      │ │
│  │ TCP      │ 80      │ 0.0.0.0/0    │ HTTP出站        │ │
│  │ TCP      │ 3306    │ 数据库服务器   │ MySQL访问      │ │
│  │ TCP      │ 6379    │ Redis集群    │ Redis访问      │ │
│  │ TCP      │ 5672    │ MQ服务器     │ RabbitMQ       │ │
│  │ TCP      │ 8848    │ Nacos集群    │ Nacos配置中心  │ │
│  └──────────┴─────────┴──────────────┴────────────────┘ │
└─────────────────────────────────────────────────────────┘
```

### 1.3 DNS解析配置

```
┌─────────────────────────────────────────────────────────┐
│  DNS 解析记录 (在域名服务商处配置)                         │
├──────────────┬────────┬──────────────────────────────────┤
│ 主机记录       │ 记录类型 │ 指向                          │
├──────────────┼────────┼──────────────────────────────────┤
│ api          │ A      │ 123.45.67.89 或 CNAME到SLB      │
│ admin        │ A      │ 123.45.67.89 或 CNAME到SLB      │
│ merchant     │ A      │ 123.45.67.89 或 CNAME到SLB      │
│ m            │ A      │ 123.45.67.89 或 CNAME到SLB      │
│ *.cdn        │ CNAME  │ 阿里云CDN域名                    │
│ *            │ CNAME  │ 主域名(通配符,可选)               │
└──────────────┴────────┴──────────────────────────────────┘
```

### 1.4 SSL证书配置

| 域名 | 证书类型 | 获取方式 | 建议 |
|------|----------|----------|------|
| api.lianshengtong.com | OV SSL | 云平台证书服务 | 商业级信任 |
| admin.lianshengtong.com | DV SSL | Let's Encrypt / 云平台 | 快速签发 |
| merchant.lianshengtong.com | OV SSL | 云平台证书服务 | 商业级信任 |
| m.lianshengtong.com | DV SSL | Let's Encrypt | 快速签发 |

**Let's Encrypt 自动获取命令**:
```bash
# 安装Certbot
sudo apt install certbot python3-certbot-nginx

# 获取证书
sudo certbot --nginx -d api.lianshengtong.com -d www.lianshengtong.com

# 自动续期已内置: sudo systemctl status certbot.timer
```

---

## 2. 部署步骤

### 2.1 准备阶段

```bash
# 1. 购买云服务器(推荐阿里云ECS)
#    - 区域: 华东1(杭州) cn-hangzhou
#    - 规格: 8C32G (ecs.g7.large)
#    - 系统盘: 100GB SSD
#    - 数据盘: 500GB NVMe SSD
#    - 安全组: 开放80/443/22端口

# 2. 购买或使用已有域名
#    - 域名服务商: 阿里云/腾讯云
#    - 备案: ICP备案 (国内服务器要求)
#    - 配置SSL证书

# 3. SSH连接服务器
ssh -p 22 root@your_server_ip
```

### 2.2 基础环境安装

```bash
# 下载项目代码(已在本地的话跳过)
cd /workspace

# 上传到云服务器
scp -P 2222 -r /workspace user@server:/tmp/lsc-deploy

# 在服务器上执行
cd /tmp/lsc-deploy

# ---- 执行安全加固 ----
sudo bash cloud/scripts/security-hardening.sh
# 按提示设置SSH密钥, 确保安全

# ---- 安装运行环境 ----
sudo bash cloud/scripts/deploy-cloud.sh --skip-build
# 此命令会: 安装Docker/Nginx, 配置目录结构
```

### 2.3 构建与部署

```bash
# ---- 构建应用镜像(本地开发机执行) ----
cd /workspace
bash docker/build-all.sh

# ---- 推送到镜像仓库(可选) ----
# 配置镜像仓库凭据
docker login registry.cn-hangzhou.aliyuncs.com
# 推送所有镜像
for svc in lsc-gateway lsc-user-service ...; do
    docker tag lsc/$svc:6.2.0 registry.cn-hangzhou.aliyuncs.com/lsc/$svc:6.2.0
    docker push registry.cn-hangzhou.aliyuncs.com/lsc/$svc:6.2.0
done

# ---- 在云服务器上拉取并部署 ----
# 方式1: 本地构建后传输
scp docker-compose-app.yml user@server:/opt/lsc/
scp *.jar /opt/lsc/app/

# 方式2: 从镜像仓库拉取
docker login registry.cn-hangzhou.aliyuncs.com
docker-compose -f /opt/lsc/docker-compose-app.yml up -d

# ---- 执行部署脚本 ----
sudo bash cloud/scripts/deploy-cloud.sh
# 此命令会: 部署Nginx配置, 启动所有服务, 验证健康状态
```

### 2.4 配置SSL证书

```bash
# 方式1: 云平台证书托管(推荐)
# 在云平台 -> SSL证书 -> 申请免费DV/付费OV证书
# 下载Nginx格式证书, 放置到:
#   /etc/nginx/ssl/api.lianshengtong.com.pem
#   /etc/nginx/ssl/api.lianshengtong.com.key

# 方式2: Let's Encrypt免费证书
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d api.lianshengtong.com
sudo certbot --nginx -d admin.lianshengtong.com
sudo certbot --nginx -d merchant.lianshengtong.com
sudo certbot --nginx -d m.lianshengtong.com

# 验证证书
sudo certbot certificates

# 手动续期测试
sudo certbot renew --dry-run
```

### 2.5 配置CDN加速 (可选)

```
在阿里云/腾讯云CDN控制台:
1. 添加加速域名: cdn.lianshengtong.com
2. 回源地址: api.lianshengtong.com
3. 缓存规则:
   - 静态资源(js/css/img/woff): 30天
   - HTML: 不缓存
   - API响应: 不缓存
4. HTTPS配置: 启用HTTPS, 证书自动下发
5. 智能压缩: 启用Gzip/Brotli
6. 防护配置: 启用WAF基础防护
```

### 2.6 配置负载均衡 (可选, 集群部署)

```
在云负载均衡(SLB/CLB)控制台:
1. 创建实例:
   - 规格: 性能共享型(s1.small)
   - 监听: HTTP(80) + HTTPS(443)
2. 添加后端服务器组:
   - 协议: HTTP
   - 端口: 80
   - 健康检查: /actuator/health
   - 健康检查间隔: 5秒, 超时: 3秒
3. 配置SSL证书:
   - HTTPS监听绑定SSL证书
   - 启用HTTP/2
4. 开启会话保持:
   - 模式: 源IP哈希 (如需会话保持)
```

---

## 3. 日常运维

### 3.1 服务管理

```bash
# 查看所有服务状态
sudo bash cloud/scripts/deploy-cloud.sh --status

# 重启服务
docker-compose -f /opt/lsc/docker-compose-app.yml restart

# 重启单个服务
docker-compose -f /opt/lsc/docker-compose-app.yml restart lsc-gateway

# 查看服务日志
docker logs -f lsc-gateway --tail 100

# 查看Nginx日志
tail -f /var/log/nginx/access.log
tail -f /var/log/nginx/error.log

# 查看系统日志
journalctl -u nginx -f --no-pager
journalctl -u docker -f --no-pager
```

### 3.2 版本升级

```bash
# 1. 本地构建新版本镜像
cd /workspace
mvn clean package -DskipTests
# 构建Docker镜像并推送到仓库

# 2. 在云服务器上执行滚动更新
# 拉取新镜像
docker-compose -f /opt/lsc/docker-compose-app.yml pull

# 滚动更新(逐个重启服务, 不中断)
docker-compose -f /opt/lsc/docker-compose-app.yml up -d --no-deps
# 或手动逐个重启
for svc in lsc-gateway lsc-user-service ...; do
    docker-compose restart $svc
    sleep 10  # 等待服务启动
done

# 3. 健康检查
curl -sf http://localhost:8000/actuator/health
# 验证各服务正常
```

### 3.3 数据备份

```bash
# 手动全量备份
sudo bash cloud/scripts/backup-restore.sh all

# 定时备份(已在cron中配置)
# 编辑crontab:
sudo crontab -e
# 添加:
0 2 * * * /opt/lsc/cloud/scripts/backup-restore.sh all >> /var/log/lsc-backup.log 2>&1

# 列出备份
sudo bash cloud/scripts/backup-restore.sh list

# 恢复MySQL数据
sudo bash cloud/scripts/backup-restore.sh restore /data/lsc/backup/mysql/lsc_system_20260810_020000.sql.gz

# 清理过期备份
sudo bash cloud/scripts/backup-restore.sh clean
```

### 3.4 紧急故障排查

```bash
# 1. 服务无响应
docker ps -a  # 检查容器状态
docker logs <容器名> --tail 200  # 查看最近日志
# 常见原因: 数据库连接失败, 配置错误, 内存不足

# 2. 性能下降
# 查看CPU/内存/磁盘
top -bn1 | head -20
free -h
df -h

# 查看Nginx状态
nginx -t  # 检查配置
tail -20 /var/log/nginx/error.log  # Nginx错误

# 查看Java进程
jstack <pid> 2>&1 | head -50  # 线程栈
jmap -heap <pid>  # 堆内存

# 3. 数据库问题
# MySQL
mysql -h 127.0.0.1 -u root -p -e "SHOW PROCESSLIST;"
mysql -h 127.0.0.1 -u root -p -e "SHOW STATUS LIKE 'Threads_connected';"

# Redis
redis-cli info stats
redis-cli info memory

# 4. 网络问题
ss -tlnp  # 查看端口监听
netstat -s  # 网络统计
```

---

## 4. 成本估算

### 4.1 单机部署 (方案A)

| 项目 | 规格 | 月费 (华东1) | 年付折扣 |
|------|------|-------------|----------|
| ECS服务器 | 8C32G | ¥1,200-1,500 | ¥12,000-15,000 |
| 数据盘 | 500GB SSD | ¥500 | ¥6,000 |
| 带宽 | 100Mbps固定 | ¥300 | ¥3,600 |
| SSL证书 | OV证书 | ¥1,000 | ¥1,000 |
| 域名 | .com | ¥70 | ¥70 |
| CDN流量 | 按使用 | ¥500-2,000 | 按需 |
| 对象存储 | OSS | ¥100-500 | 按需 |
| **合计** | | **¥3,670-4,870/月** | **¥22,670-32,170/年** |

### 4.2 集群部署 (方案B)

| 项目 | 数量 | 月费 | 年付 |
|------|------|------|------|
| ECS应用节点 | 3台 | ¥3,600 | ¥36,000 |
| RDS MySQL (高可用) | 1套 | ¥2,000 | ¥24,000 |
| Redis集群 | 3节点 | ¥1,500 | ¥18,000 |
| SLB负载均衡 | 1台 | ¥300 | ¥3,600 |
| Nacos集群 | 3台 | ¥1,200 | ¥12,000 |
| 带宽+流量 | - | ¥1,000 | ¥12,000 |
| SSL+域名 | - | ¥2,000 | ¥2,000 |
| 监控日志 | - | ¥1,000 | ¥12,000 |
| **合计** | | **¥11,600/月** | **¥119,600/年** |

---

## 5. 文件清单

### 5.1 云部署配置文件

| 文件路径 | 用途 |
|----------|------|
| `cloud/.env.production` | 生产环境变量配置 |
| `cloud/scripts/security-hardening.sh` | 服务器安全加固脚本 |
| `cloud/scripts/deploy-cloud.sh` | 一键部署脚本 |
| `cloud/scripts/backup-restore.sh` | 备份恢复脚本 |
| `cloud/nginx/nginx.conf` | Nginx主配置 |
| `cloud/nginx/lsc-api.conf` | API网关配置 |
| `cloud/nginx/lsc-admin.conf` | 管理后台配置 |
| `cloud/nginx/lsc-merchant.conf` | 商家前台配置 |
| `cloud/nginx/lsc-mobile.conf` | 移动端配置 |
| `cloud/monitoring/prometheus.yml` | Prometheus监控配置 |
| `cloud/monitoring/alert-rules.yml` | 告警规则 |
| `cloud/monitoring/grafana-datasource.yml` | Grafana数据源配置 |

### 5.2 快速参考命令

```bash
# 一键部署
sudo bash /workspace/cloud/scripts/deploy-cloud.sh

# 查看状态
sudo bash /workspace/cloud/scripts/deploy-cloud.sh --status

# 执行备份
sudo bash /workspace/cloud/scripts/backup-restore.sh all

# 清理安全风险
sudo bash /workspace/cloud/scripts/security-hardening.sh

# 查看Nginx状态
sudo nginx -t && sudo systemctl status nginx

# 重启Docker服务
sudo systemctl restart docker
```

---

## 6. 常见问题

### Q1: Nginx 502 Bad Gateway
**原因**: 后端微服务未启动或端口错误
**排查**: 
```bash
docker ps  # 检查容器状态
docker logs lsc-gateway --tail 50  # 查看日志
curl http://localhost:8000/actuator/health  # 直接测试
```

### Q2: SSL证书不生效
**原因**: 证书路径错误或文件权限问题
**排查**:
```bash
nginx -t  # 检查配置
openssl x509 -in /etc/nginx/ssl/domain.pem -text -noout  # 查看证书
# 确保Nginx用户有权读取证书
chmod 644 /etc/nginx/ssl/*.pem
```

### Q3: 数据库连接超时
**原因**: 防火墙或安全组阻止
**排查**:
```bash
# 本地测试连接
mysql -h MySQL_HOST -u user -p -e "SELECT 1"
# 检查防火墙
firewall-cmd --list-all
# 检查MySQL监听
mysql -e "SHOW VARIABLES LIKE 'bind_address';"
```

### Q4: Docker容器无法拉取镜像
**原因**: 网络问题或镜像仓库认证失败
**解决**:
```bash
# 检查Docker网络
docker run --rm hello-world
# 登录镜像仓库
docker login registry.cn-hangzhou.aliyuncs.com
# 配置镜像加速器(阿里云)
mkdir -p /etc/docker
echo '{"registry-mirrors": ["https://[你的ID].mirror.aliyuncs.com"]}' | sudo tee /etc/docker/daemon.json
sudo systemctl restart docker
```

### Q5: 内存不足导致OOM
**原因**: 应用内存泄漏或配置过大
**解决**:
```bash
# 查看内存使用
free -h
# 调整JVM参数
# 在docker-compose中设置JAVA_OPTS:
# JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC"
# 启用swap(紧急)
sudo fallocate -l 16G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
```

---

## 附录: 技术支持

| 场景 | 联系方式 |
|------|----------|
| 部署故障 | 查看日志 + 监控面板 |
| 配置咨询 | 参考本手册 + 架构文档 |
| 紧急故障 | 联系运维团队 |
| 版本更新 | 关注项目仓库的 Release |