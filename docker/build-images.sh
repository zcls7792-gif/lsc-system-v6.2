#!/bin/bash
# ============================================================
# 链盛通LSC系统 - Docker镜像构建脚本
# 用法: ./build-images.sh [service-name]
# 不传参数则构建全部后端服务
# ============================================================

set -e

PROJECT_NAME="lsc-system"
VERSION="6.2.0"
REGISTRY="registry.cn-hangzhou.aliyuncs.com/lsc"

# 后端服务列表
SERVICES=(
    "lsc-user-service"
    "lsc-ledger-service"
    "lsc-b2b-service"
    "lsc-order-service"
    "lsc-writeoff-service"
    "lsc-release-service"
    "lsc-promotion-service"
    "lsc-mall-service"
    "lsc-risk-service"
    "lsc-media-service"
    "lsc-map-service"
    "lsc-reconciliation-service"
    "lsc-evidence-service"
    "lsc-admin-service"
    "lsc-ai-gateway"
    "lsc-gateway"
)

# 前端项目列表
FRONTEND=(
    "lsc-admin-web"
    "lsc-merchant-web"
)

build_backend() {
    local svc=$1
    echo "=========================================="
    echo "构建后端服务: $svc"
    echo "=========================================="

    cd /workspace/$svc

    # Maven打包(跳过测试)
    mvn clean package -DskipTests -q

    # 获取jar文件名
    JAR_FILE=$(find target -name "*.jar" -not -name "*-sources.jar" | head -1)
    if [ -z "$JAR_FILE" ]; then
        echo "✗ 未找到jar文件: $svc"
        return 1
    fi

    # 获取端口
    PORT=$(grep -m1 'server.port' src/main/resources/application.yml | awk -F': ' '{print $2}' | tr -d ' ')

    # 构建Docker镜像
    docker build \
        --build-arg JAR_FILE=$(basename $JAR_FILE) \
        --build-arg SERVICE_NAME=$svc \
        --build-arg SERVER_PORT=$PORT \
        -f /workspace/docker/Dockerfile \
        -t $REGISTRY/$svc:$VERSION \
        .

    echo "✓ $svc 镜像构建完成: $REGISTRY/$svc:$VERSION"
    cd /workspace
}

build_frontend() {
    local project=$1
    echo "=========================================="
    echo "构建前端项目: $project"
    echo "=========================================="

    cd /workspace/$project

    # npm安装依赖并构建
    npm install --silent
    npm run build

    # 构建Nginx镜像
    docker build \
        -f /workspace/docker/Dockerfile.frontend \
        --build-arg PROJECT_DIR=$project \
        -t $REGISTRY/$project:$VERSION \
        .

    echo "✓ $project 镜像构建完成: $REGISTRY/$project:$VERSION"
    cd /workspace
}

# ============================================================
# 主逻辑
# ============================================================

if [ -n "$1" ]; then
    # 构建指定服务
    if [[ " ${SERVICES[@]} " =~ " $1 " ]]; then
        build_backend $1
    elif [[ " ${FRONTEND[@]} " =~ " $1 " ]]; then
        build_frontend $1
    else
        echo "未知服务: $1"
        echo "可用: ${SERVICES[@]} ${FRONTEND[@]}"
        exit 1
    fi
else
    # 构建全部后端
    echo "开始构建全部后端微服务..."
    for svc in "${SERVICES[@]}"; do
        build_backend $svc || echo "⚠ $svc 构建失败，跳过"
    done

    # 构建全部前端
    echo "开始构建全部前端项目..."
    for project in "${FRONTEND[@]}"; do
        build_frontend $project || echo "⚠ $project 构建失败，跳过"
    done
fi

echo ""
echo "=========================================="
echo "全部镜像构建完成"
echo "=========================================="
docker images | grep $REGISTRY
