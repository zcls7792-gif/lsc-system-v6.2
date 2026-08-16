#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2 - K8s部署脚本
# 功能: 构建镜像 + 推送仓库 + 部署到K8s集群
# 用法: ./deploy-k8s.sh [OPTIONS]
#   --namespace     K8s命名空间 (默认: lsc-system)
#   --skip-build    跳过镜像构建, 直接推送已有镜像
#   --skip-push     跳过镜像推送 (使用本地镜像)
#   --skip-deploy   跳过K8s部署 (仅构建和推送镜像)
#   --replicas=N    覆盖副本数 (默认: 使用YAML配置)
#   --help          显示帮助信息
# 环境变量:
#   REGISTRY, VERSION, KUBE_CONFIG, NAMESPACE
# ============================================================

set -e

# ---- 颜色定义 ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

# ---- 日志函数 ----
log_info()    { echo -e "${CYAN}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} ${BOLD}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} ${BOLD}[OK]${NC} $*"; }
log_warn()    { echo -e "${YELLOW}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} ${BOLD}[WARN]${NC} $*"; }
log_error()   { echo -e "${RED}[$(date '+%Y-%m-%d %H:%M:%S')]${NC} ${BOLD}[ERROR]${NC} $*"; }

# ---- 配置 ----
PROJECT_ROOT="${PROJECT_ROOT:-/workspace}"
K8S_DIR="${K8S_DIR:-/workspace/k8s}"
DOCKER_DIR="${DOCKER_DIR:-/workspace/docker}"
BUILD_SCRIPT="${DOCKER_DIR}/build-all.sh"

REGISTRY="${REGISTRY:-registry.cn-hangzhou.aliyuncs.com/lsc}"
VERSION="${VERSION:-6.2.0}"
NAMESPACE="${NAMESPACE:-lsc-system}"
REPLICAS_OVERRIDE=""

# K8s 资源文件
NAMESPACE_FILE="${K8S_DIR}/namespace.yaml"
CONFIGMAP_FILE="${K8S_DIR}/configmap.yaml"
DEPLOYMENTS_FILE="${K8S_DIR}/deployments.yaml"

# ---- 参数解析 ----
SKIP_BUILD=false
SKIP_PUSH=false
SKIP_DEPLOY=false

show_help() {
    cat << EOF
链盛通LSC系统 V6.2 K8s部署

用法: $0 [OPTIONS]

选项:
  --namespace=NS      K8s命名空间, 默认: lsc-system
  --skip-build        跳过镜像构建
  --skip-push         跳过镜像推送
  --skip-deploy       跳过K8s部署
  --replicas=N        覆盖所有Deployment的副本数
  --help              显示本帮助信息

环境变量:
  REGISTRY            镜像仓库地址, 默认: registry.cn-hangzhou.aliyuncs.com/lsc
  VERSION             镜像版本标签, 默认: 6.2.0
  NAMESPACE           K8s命名空间, 默认: lsc-system
  KUBE_CONFIG         Kubeconfig文件路径

示例:
  $0                                       # 完整K8s部署
  $0 --skip-build                          使用已有镜像
  $0 --skip-deploy                         仅构建和推送镜像
  $0 --replicas=3                          所有服务3副本
  REGISTRY=my-registry.io/lsc $0           指定仓库
EOF
    exit 0
}

for arg in "$@"; do
    case "$arg" in
        --help)
            show_help
            ;;
        --namespace=*)
            NAMESPACE="${arg#*=}"
            ;;
        --skip-build)
            SKIP_BUILD=true
            ;;
        --skip-push)
            SKIP_PUSH=true
            ;;
        --skip-deploy)
            SKIP_DEPLOY=true
            ;;
        --replicas=*)
            REPLICAS_OVERRIDE="${arg#*=}"
            ;;
        *)
            log_error "未知参数: $arg"
            echo "使用 --help 查看帮助信息"
            exit 1
            ;;
    esac
done

# ---- 依赖检查 ----
check_deps() {
    log_info "检查依赖工具..."
    local missing=()

    if [ "$SKIP_DEPLOY" = false ]; then
        if ! command -v kubectl &>/dev/null; then
            missing+=("kubectl")
        fi
    fi
    if [ "$SKIP_BUILD" = false ] || [ "$SKIP_PUSH" = false ]; then
        if ! command -v docker &>/dev/null; then
            missing+=("docker")
        fi
    fi
    if ! command -v curl &>/dev/null; then
        missing+=("curl")
    fi

    if [ ${#missing[@]} -gt 0 ]; then
        log_error "缺少必要依赖: ${missing[*]}"
        exit 1
    fi

    # 检查kubectl连接
    if [ "$SKIP_DEPLOY" = false ]; then
        if ! kubectl cluster-info &>/dev/null; then
            log_error "无法连接到K8s集群, 请检查kubeconfig配置"
            exit 1
        fi
        log_success "K8s 集群连接正常"
    fi

    log_success "依赖检查通过"
}

# ---- 构建镜像 ----
step_build() {
    if [ "$SKIP_BUILD" = true ]; then
        log_warn "跳过镜像构建 (--skip-build), 使用已有镜像"
        return 0
    fi

    log_info "[Step 1] 构建Docker镜像..."
    if [ -x "$BUILD_SCRIPT" ]; then
        REGISTRY="$REGISTRY" VERSION="$VERSION" bash "$BUILD_SCRIPT" --skip-tests || {
            log_error "镜像构建失败"
            exit 1
        }
    else
        log_error "构建脚本不存在: $BUILD_SCRIPT"
        exit 1
    fi
    log_success "[Step 1] 镜像构建完成"
}

# ---- 推送镜像 ----
step_push() {
    if [ "$SKIP_PUSH" = true ]; then
        log_warn "跳过镜像推送 (--skip-push)"
        return 0
    fi

    log_info "[Step 2] 推送镜像到仓库 ${REGISTRY}..."

    local images
    images=$(docker images --format '{{.Repository}}:{{.Tag}}' | grep "$REGISTRY" | grep "$VERSION" | sort)

    if [ -z "$images" ]; then
        log_warn "未找到本地镜像, 尝试推送所有匹配镜像..."
        images=$(docker images --format '{{.Repository}}:{{.Tag}}' | grep "lsc" | grep "$VERSION" | sort)
    fi

    if [ -z "$images" ]; then
        log_error "没有可推送的镜像"
        exit 1
    fi

    local failed=()
    local count=0
    while IFS= read -r image; do
        [ -z "$image" ] && continue
        count=$((count + 1))
        log_info "推送: $image"
        if ! docker push "$image" 2>&1 | tail -1; then
            failed+=("$image")
            log_error "  推送失败: $image"
        else
            log_success "  推送成功: $image"
        fi
    done <<< "$images"

    echo ""
    if [ ${#failed[@]} -gt 0 ]; then
        log_error "以下镜像推送失败: ${failed[*]}"
        exit 1
    fi
    log_success "[Step 2] 镜像推送完成 (共 ${count} 个)"
}

# ---- 创建命名空间 ----
step_create_namespace() {
    if [ "$SKIP_DEPLOY" = true ]; then
        return 0
    fi

    log_info "[Step 3] 创建命名空间: ${NAMESPACE}..."

    if [ -f "$NAMESPACE_FILE" ]; then
        kubectl apply -f "$NAMESPACE_FILE" 2>/dev/null || true
    else
        kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    fi

    kubectl label namespace "$NAMESPACE" app.kubernetes.io/part-of=lsc-system --overwrite 2>/dev/null || true
    log_success "[Step 3] 命名空间 ${NAMESPACE} 已就绪"
}

# ---- 应用ConfigMap和Secret ----
step_apply_config() {
    if [ "$SKIP_DEPLOY" = true ]; then
        return 0
    fi

    log_info "[Step 4] 应用 ConfigMap 和 Secret..."

    if [ -f "$CONFIGMAP_FILE" ]; then
        kubectl apply -f "$CONFIGMAP_FILE" -n "$NAMESPACE" || {
            log_error "ConfigMap/Secret 应用失败"
            exit 1
        }
        log_success "[Step 4] ConfigMap 和 Secret 已应用"
    else
        log_warn "配置文件不存在: $CONFIGMAP_FILE"
    fi
}

# ---- 应用部署清单 ----
step_apply_deployments() {
    if [ "$SKIP_DEPLOY" = true ]; then
        return 0
    fi

    log_info "[Step 5] 应用部署清单..."

    if [ ! -f "$DEPLOYMENTS_FILE" ]; then
        log_error "部署清单不存在: $DEPLOYMENTS_FILE"
        exit 1
    fi

    local deploy_file="$DEPLOYMENTS_FILE"

    # 如果需要覆盖副本数
    if [ -n "$REPLICAS_OVERRIDE" ]; then
        log_info "覆盖副本数为: $REPLICAS_OVERRIDE"
        deploy_file=$(mktemp /tmp/lsc-deploy-XXXXXX.yaml)
        sed "s/replicas: [0-9]*/replicas: ${REPLICAS_OVERRIDE}/g" "$DEPLOYMENTS_FILE" > "$deploy_file"
    fi

    kubectl apply -f "$deploy_file" -n "$NAMESPACE" || {
        log_error "部署清单应用失败"
        rm -f "$deploy_file"
        exit 1
    }

    rm -f "$deploy_file"
    log_success "[Step 5] 部署清单已应用"
}

# ---- 等待Pod就绪 ----
step_wait_pods() {
    if [ "$SKIP_DEPLOY" = true ]; then
        return 0
    fi

    log_info "[Step 6] 等待所有Pod就绪..."

    local max_wait=300
    local waited=0

    while [ $waited -lt $max_wait ]; do
        local not_ready
        not_ready=$(kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | grep -v 'Running' | grep -v 'Completed' | wc -l)

        if [ "$not_ready" -eq 0 ]; then
            local total
            total=$(kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | wc -l)
            log_success "所有 Pod 已就绪 (共 ${total} 个)"
            return 0
        fi

        # 显示非Running状态的pod
        local problem_pods
        problem_pods=$(kubectl get pods -n "$NAMESPACE" --no-headers 2>/dev/null | grep -v 'Running' | grep -v 'Completed')
        printf "\r  等待中... %ds (非就绪Pod: %d)" $waited "$not_ready"

        if [ -n "$problem_pods" ] && [ $((waited % 30)) -eq 0 ]; then
            echo ""
            log_warn "非就绪Pod:"
            echo "$problem_pods" | while read -r line; do
                log_warn "  $line"
            done
        fi

        sleep 5
        waited=$((waited + 5))
    done
    echo ""
    log_warn "等待超时, 部分Pod可能未就绪"
    log_warn "请检查: kubectl get pods -n $NAMESPACE"
}

# ---- 输出访问地址 ----
print_access_info() {
    if [ "$SKIP_DEPLOY" = true ]; then
        return 0
    fi

    echo ""
    echo -e "${BOLD}============================================================${NC}"
    echo -e "${BOLD}${GREEN}  链盛通LSC系统 V6.2 K8s部署完成${NC}${NC}"
    echo -e "${BOLD}============================================================${NC}"
    echo ""

    log_info "Pod 状态:"
    kubectl get pods -n "$NAMESPACE" -o wide 2>/dev/null || true
    echo ""

    log_info "Service:"
    kubectl get svc -n "$NAMESPACE" 2>/dev/null || true
    echo ""

    log_info "访问地址:"
    echo "  Ingress (API):     https://api.lianshengtong.com"
    echo "  Ingress (Admin):   https://admin.lianshengtong.com"
    echo "  集群内访问:        http://lsc-gateway.${NAMESPACE}.svc.cluster.local:8000"
    echo ""

    log_info "常用命令:"
    echo "  查看Pod:      kubectl get pods -n $NAMESPACE"
    echo "  查看日志:     kubectl logs -f <pod-name> -n $NAMESPACE"
    echo "  查看事件:     kubectl get events -n $NAMESPACE --sort-by=.lastTimestamp | tail -20"
    echo "  进入容器:     kubectl exec -it <pod-name> -n $NAMESPACE -- bash"
    echo "  端口转发:     kubectl port-forward svc/lsc-gateway 8000:8000 -n $NAMESPACE"
    echo ""

    # 检查Ingress
    if kubectl get ingress -n "$NAMESPACE" &>/dev/null; then
        log_info "Ingress 状态:"
        kubectl get ingress -n "$NAMESPACE"
        echo ""
    fi
}

# ---- 主流程 ----
main() {
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo -e "${BOLD}${CYAN}  链盛通LSC系统 V6.2 K8s部署${NC}"
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo ""

    log_info "镜像仓库:   $REGISTRY"
    log_info "版本标签:   $VERSION"
    log_info "命名空间:   $NAMESPACE"
    echo ""

    check_deps
    step_build
    step_push

    if [ "$SKIP_DEPLOY" = false ]; then
        step_create_namespace
        step_apply_config
        step_apply_deployments
        step_wait_pods
        print_access_info
    else
        log_info "已完成镜像构建和推送, 跳过K8s部署"
    fi
}

main "$@"