# 链盛通 LSC V6.2 · 部署运维手册 (Runbook)

**版本：** 6.2.0-AI  
**日期：** 2026-08-20  

---

## 一、环境要求

### 1.1 硬件要求

| 环境 | CPU | 内存 | 磁盘 | 节点数 |
|---|---|---|---|---|
| 生产环境 | 8C+ | 32GB+ | 500GB SSD | 3+ |
| 预发布环境 | 4C | 16GB | 200GB SSD | 1 |
| 开发环境 | 2C | 8GB | 100GB | 1 |

### 1.2 软件要求

| 组件 | 版本 |
|---|---|
| Kubernetes | 1.28+ |
| Docker | 24.0+ |
| JDK | 17 |
| Maven | 3.9+ |
| MySQL | 8.0 |
| Redis | 7.0 |
| RabbitMQ | 3.12+ |
| Nacos | 2.3.2 |

---

## 二、部署步骤

### 2.1 代码获取

```bash
git clone https://github.com/zcls7792-gif/lsc-system-v6.2.git
cd lsc-system-v6.2
```

### 2.2 密钥配置

```bash
# 1. 复制环境变量模板
cp docker/.env.example docker/.env

# 2. 执行密钥轮换脚本（生成强随机密钥）
chmod +x scripts/rotate-secrets.sh
./scripts/rotate-secrets.sh

# 3. 验证 .env 文件（确认无空值）
grep -E "=$|^$" docker/.env && echo "存在空值!" || echo "密钥配置完成"
```

### 2.3 构建镜像

```bash
# 构建全部后端 + 前端镜像
chmod +x docker/build-images.sh
./docker/build-images.sh --all

# 或指定服务构建
./docker/build-images.sh --backend lsc-gateway,lsc-user-service
./docker/build-images.sh --frontend lsc-admin-web
```

### 2.4 数据库初始化

```bash
# 方式一：Docker Compose 自动初始化（推荐开发环境）
# SQL 文件已挂载到 docker-entrypoint-initdb.d/
# 按顺序执行：01-schema.sql → 02-sharding.sql

# 方式二：手动初始化（推荐生产环境）
chmod +x docker/init-db.sh
./docker/init-db.sh
```

### 2.5 Docker Compose 部署（开发/测试环境）

```bash
cd docker
docker compose --env-file .env up -d
```

### 2.6 Kubernetes 部署（生产环境）

```bash
# 1. 创建命名空间
kubectl apply -f k8s/namespace.yaml

# 2. 创建配置和密钥
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secrets.yaml    # 先填充真实密钥

# 3. 部署有状态服务（MySQL/Redis/RabbitMQ/Nacos）
# 建议使用云厂商托管服务或 Helm Chart

# 4. 部署微服务
kubectl apply -f k8s/deployments.yaml
kubectl apply -f k8s/deployments-extra.yaml

# 5. 部署服务发现
kubectl apply -f k8s/services.yaml

# 6. 部署高可用配置
kubectl apply -f k8s/pod-disruption-budget.yaml
kubectl apply -f k8s/hpa.yaml

# 7. 部署网络策略
kubectl apply -f k8s/network-policy.yaml

# 8. 部署 TLS 证书
kubectl apply -f k8s/tls-certificates.yaml

# 9. 验证部署
kubectl get pods -n lsc-system
kubectl get hpa -n lsc-system
kubectl get pdb -n lsc-system
```

---

## 三、环境变量清单

### 3.1 必须配置的变量

| 变量名 | 说明 | 示例 |
|---|---|---|
| MYSQL_ROOT_PASSWORD | MySQL root 密码 | (随机生成) |
| MYSQL_PASSWORD | 应用数据库密码 | (随机生成) |
| REDIS_PASSWORD | Redis 密码 | (随机生成) |
| RABBITMQ_PASSWORD | RabbitMQ 密码 | (随机生成) |
| JWT_SECRET | JWT 签名密钥 | (32+ 字符随机) |
| ADMIN_JWT_SECRET | 管理后台 JWT 密钥 | (32+ 字符随机) |
| JWT_SECRET_EVIDENCE | 存证服务 JWT 密钥 | (32+ 字符随机) |
| AI_OPENAI_KEY | OpenAI API Key | sk-... |
| AI_CLAUDE_KEY | Claude API Key | sk-ant-... |
| AI_QWEN_KEY | 通义千问 API Key | sk-... |
| OSS_ACCESS_KEY_ID | 阿里云 OSS Key | LTAI... |
| OSS_ACCESS_KEY_SECRET | 阿里云 OSS Secret | (随机) |
| COS_SECRET_ID | 腾讯云 COS ID | AKID... |
| COS_SECRET_KEY | 腾讯云 COS Key | (随机) |
| AMAP_KEY | 高德地图 Key | (随机) |
| CHAIN_PK | 区块链私钥 | 0x... |

### 3.2 可选配置

| 变量名 | 默认值 | 说明 |
|---|---|---|
| NACOS_AUTH_IDENTITY_VALUE | nacos | Nacos 认证值 |
| SEATA_REGISTRY_TYPE | nacos | Seata 注册类型 |

---

## 四、健康检查

### 4.1 服务健康检查

```bash
# 检查所有 Pod 状态
kubectl get pods -n lsc-system

# 检查服务健康端点
for svc in gateway user-service ledger-service; do
  echo "--- lsc-$svc ---"
  curl -s http://lsc-$svc:8080/actuator/health | jq .
done
```

### 4.2 数据库连接检查

```bash
kubectl exec -it -n lsc-system deploy/lsc-user-service -- \
  curl -s localhost:8080/actuator/health/db | jq .
```

### 4.3 Redis 连接检查

```bash
kubectl exec -it -n lsc-system deploy/lsc-user-service -- \
  redis-cli -h redis-0.redis -a $REDIS_PASSWORD ping
```

---

## 五、监控与告警

### 5.1 Prometheus 指标

```bash
# 访问 Prometheus
kubectl port-forward -n monitoring svc/prometheus 9090:9090

# 查看 LSC 指标
# http://localhost:9090 → 搜索 lsc_
```

### 5.2 Grafana 面板

```bash
kubectl port-forward -n monitoring svc/grafana 3000:3000
# 默认账号: admin/admin
```

### 5.3 告警规则

| 告警 | 条件 | 严重级别 |
|---|---|---|
| Pod 重启 | rate(kube_pod_container_status_restarts_total[5m]) > 0 | warning |
| CPU 使用率 | avg(rate(container_cpu_usage_seconds_total[5m])) > 0.8 | warning |
| 内存使用率 | container_memory_usage_bytes / container_spec_memory_limit_bytes > 0.8 | warning |
| 数据库连接 | hikaricp_connections_active / hikaricp_connections_max > 0.8 | critical |
| 服务宕机 | up == 0 | critical |

---

## 六、故障排查

### 6.1 Pod 启动失败

```bash
# 查看 Pod 事件
kubectl describe pod <pod-name> -n lsc-system

# 查看日志
kubectl logs <pod-name> -n lsc-system --previous
```

### 6.2 服务间通信失败

```bash
# 检查 NetworkPolicy
kubectl get networkpolicy -n lsc-system

# 检查 DNS 解析
kubectl exec -it <pod-name> -n lsc-system -- nslookup lsc-user-service
```

### 6.3 数据库连接超时

```bash
# 检查数据库 Pod
kubectl get pods -n lsc-system | grep mysql

# 检查连接池
curl http://<service>:8080/actuator/metrics/hikaricp.connections.active
```

---

## 七、回滚流程

### 7.1 K8s 回滚

```bash
# 查看发布历史
kubectl rollout history deployment/lsc-gateway -n lsc-system

# 回滚到上一版本
kubectl rollout undo deployment/lsc-gateway -n lsc-system

# 回滚到指定版本
kubectl rollout undo deployment/lsc-gateway -n lsc-system --to-revision=2
```

### 7.2 数据库回滚

```bash
# 备份当前数据库
mysqldump -h <host> -u root -p lsc_system > backup_$(date +%Y%m%d).sql

# 恢复数据库
mysql -h <host> -u root -p lsc_system < backup_YYYYMMDD.sql
```

---

## 八、日常运维

### 8.1 日志查看

```bash
# 实时日志
kubectl logs -f <pod-name> -n lsc-system

# ELK 日志查询
# 访问 Kibana → 索引 lsc-*
```

### 8.2 扩缩容

```bash
# 手动扩容
kubectl scale deployment lsc-gateway --replicas=4 -n lsc-system

# HPA 自动扩容（已配置）
kubectl get hpa -n lsc-system
```

### 8.3 密钥轮换

```bash
# 定期执行密钥轮换
./scripts/rotate-secrets.sh

# 更新 K8s Secret
kubectl create secret generic lsc-secrets \
  --from-env-file=docker/.env \
  --dry-run=client -o yaml | kubectl apply -f - -n lsc-system
```

---

## 九、上线检查清单

- [ ] 密钥已生成并配置
- [ ] 数据库已初始化
- [ ] 镜像已构建并推送
- [ ] K8s 资源已部署
- [ ] 全部 Pod Running
- [ ] 健康检查通过
- [ ] Prometheus 采集正常
- [ ] Grafana 面板正常
- [ ] Alertmanager 告警配置正常
- [ ] TLS 证书已配置
- [ ] 灰度发布验证通过
- [ ] 回滚方案已确认

---

**手册版本：** 1.0  
**最后更新：** 2026-08-20
