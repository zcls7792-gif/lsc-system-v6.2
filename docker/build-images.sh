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

# 前端项目列表 (project_dir:build_cmd:image_name)
# admin-web / merchant-web 使用标准 npm run build
# mobile-app (uni-app) 使用 npm run build:h5
FRONTEND=(
    "lsc-admin-web:npm run build:lsc-admin-web"
    "lsc-merchant-web:npm run build:lsc-merchant-web"
    "lsc-mobile-app:npm run build:h5:lsc-mobile-web"
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
    # 参数格式: project_dir:build_cmd:image_name
    local entry=$1
    local project=$(echo "$entry" | cut -d: -f1)
    local build_cmd=$(echo "$entry" | cut -d: -f2)
    local image_name=$(echo "$entry" | cut -d: -f3)

    echo "=========================================="
    echo "构建前端项目: $project (cmd: $build_cmd -> image: $image_name)"
    echo "=========================================="

    cd /workspace/$project

    # 构建Nginx镜像 (Dockerfile 内部执行 npm install + build)
    docker build \
        -f /workspace/docker/Dockerfile.frontend \
        --build-arg PROJECT_DIR=$project \
        --build-arg BUILD_CMD="$build_cmd" \
        -t $REGISTRY/$image_name:$VERSION \
        .

    echo "✓ $image_name 镜像构建完成: $REGISTRY/$image_name:$VERSION"
    cd /workspace
}

# ============================================================
# 主逻辑
# ============================================================

if [ -n "$1" ]; then
    # 构建指定服务
    if [[ " ${SERVICES[@]} " =~ " $1 " ]]; then
        build_backend $1
    else
        # 检查是否匹配前端项目名 (entry 第1段)
        MATCHED=false
        for entry in "${FRONTEND[@]}"; do
            project=$(echo "$entry" | cut -d: -f1)
            if [ "$project" = "$1" ]; then
                build_frontend "$entry"
                MATCHED=true
                break
            fi
        done
        if [ "$MATCHED" = false ]; then
            echo "未知服务: $1"
            echo "可用后端: ${SERVICES[@]}"
            echo "可用前端: $(for e in "${FRONTEND[@]}"; do echo $e | cut -d: -f1; done | tr '\n' ' ')"
            exit 1
        fi
    fi
else
    # 构建全部后端
    echo "开始构建全部后端微服务..."
    for svc in "${SERVICES[@]}"; do
        build_backend $svc || echo "⚠ $svc 构建失败，跳过"
    done

    # 构建全部前端
    echo "开始构建全部前端项目..."
    for entry in "${FRONTEND[@]}"; do
        build_frontend "$entry" || echo "⚠ $(echo $entry | cut -d: -f1) 构建失败，跳过"
    done
fi

echo ""
echo "=========================================="
echo "全部镜像构建完成"
echo "=========================================="
docker images | grep $REGISTRY
