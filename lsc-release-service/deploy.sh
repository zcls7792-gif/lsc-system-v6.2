#!/bin/bash
set -e

echo "============================================"
echo "  链盛通 LSC 平台 · 生产部署脚本"
echo "============================================"

# 1. 停止旧服务
echo "[1/5] 停止旧服务..."
docker compose down 2>/dev/null || true

# 2. 构建后端 JAR
echo "[2/5] 构建后端 JAR..."
cd lsc-backend
mvn clean package -DskipTests -B
cd ..

# 3. 构建并启动容器
echo "[3/5] 构建并启动容器..."
docker compose up -d --build

# 4. 等待服务就绪
echo "[4/5] 等待服务就绪..."
for i in $(seq 1 30); do
    if curl -sf http://localhost/api/v1/actuator/health > /dev/null 2>&1; then
        echo "    后端健康检查通过"
        break
    fi
    echo "    等待中... ($i/30)"
    sleep 3
done

# 5. 验证
echo "[5/5] 部署验证..."
echo "  前端: http://localhost/"
echo "  后端: http://localhost/api/v1"
echo "  健康: http://localhost/api/v1/actuator/health"
echo "  数据库: mysql://localhost:3306/lsc"
echo ""
echo "测试账号:"
echo "  消费者: 13800138000 / 123456"
echo "  商家:   13900139000 / 123456"
echo "  管理员: admin / 123456"
echo ""
echo "============================================"
echo "  ✅ 部署完成"
echo "============================================"
