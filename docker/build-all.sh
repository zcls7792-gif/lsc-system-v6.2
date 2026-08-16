#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2 - 统一构建脚本
# 功能: Maven全量构建 + Docker镜像构建
# 用法: ./build-all.sh [OPTIONS]
#   --skip-tests       跳过Maven测试
#   --no-docker        仅构建jar, 不构建Docker镜像
#   --services=xxx,yyy 指定构建的服务(逗号分隔)
#   --help             显示帮助信息
# 环境变量:
#   REGISTRY   镜像仓库地址 (默认: lsc)
#   VERSION    镜像标签版本 (默认: 6.2.0)
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
DOCKER_DIR="${DOCKER_DIR:-/workspace/docker}"
REGISTRY="${REGISTRY:-lsc}"
VERSION="${VERSION:-6.2.0}"
DOCKERFILE="${DOCKERFILE:-${DOCKER_DIR}/Dockerfile}"
DOCKERFILE_FRONTEND="${DOCKERFILE_FRONTEND:-${DOCKER_DIR}/Dockerfile.frontend}"
M2_SETTINGS="${M2_SETTINGS:-${PROJECT_ROOT}/.m2-settings.xml}"

# 后端服务列表
BACKEND_SERVICES=(
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
FRONTEND_PROJECTS=(
    "lsc-admin-web"
    "lsc-merchant-web"
)

# ---- 参数解析 ----
SKIP_TESTS=false
NO_DOCKER=false
SELECTED_SERVICES=""

show_help() {
    cat << EOF
链盛通LSC系统 V6.2 统一构建脚本

用法: $0 [OPTIONS]

选项:
  --skip-tests       Maven构建时跳过单元测试
  --no-docker        仅构建jar包, 不构建Docker镜像
  --services=xxx     指定构建的服务(逗号分隔), 如: lsc-gateway,lsc-user-service
  --help             显示本帮助信息

环境变量:
  REGISTRY    镜像仓库地址, 默认: lsc
  VERSION     镜像标签版本, 默认: 6.2.0
  PROJECT_ROOT  项目根目录, 默认: /workspace
  DOCKER_DIR  Docker文件目录, 默认: /workspace/docker

示例:
  $0                              # 构建全部
  $0 --skip-tests                 # 跳过测试构建全部
  $0 --no-docker                  # 仅构建jar包
  $0 --services=lsc-gateway,lsc-user-service  # 构建指定服务
  REGISTRY=my-registry $0         # 指定镜像仓库
EOF
    exit 0
}

for arg in "$@"; do
    case "$arg" in
        --help)
            show_help
            ;;
        --skip-tests)
            SKIP_TESTS=true
            ;;
        --no-docker)
            NO_DOCKER=true
            ;;
        --services=*)
            SELECTED_SERVICES="${arg#*=}"
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

    if ! command -v mvn &>/dev/null; then
        missing+=("mvn (Maven)")
    fi
    if [ "$NO_DOCKER" = false ]; then
        if ! command -v docker &>/dev/null; then
            missing+=("docker")
        fi
    fi
    if ! command -v java &>/dev/null; then
        missing+=("java (JDK 17+)")
    fi

    if [ ${#missing[@]} -gt 0 ]; then
        log_error "缺少必要依赖: ${missing[*]}"
        exit 1
    fi
    log_success "依赖工具检查通过"
}

# ---- 选择服务 ----
resolve_services() {
    local backend_to_build=()
    local frontend_to_build=()

    if [ -n "$SELECTED_SERVICES" ]; then
        local IFS=','
        for svc in $SELECTED_SERVICES; do
            svc=$(echo "$svc" | xargs)
            if [[ " ${BACKEND_SERVICES[*]} " =~ " $svc " ]]; then
                backend_to_build+=("$svc")
            elif [[ " ${FRONTEND_PROJECTS[*]} " =~ " $svc " ]]; then
                frontend_to_build+=("$svc")
            else
                log_warn "未知服务: $svc (已跳过)"
            fi
        done
    else
        backend_to_build=("${BACKEND_SERVICES[@]}")
        frontend_to_build=("${FRONTEND_PROJECTS[@]}")
    fi
}

# ---- Maven 全量构建 ----
maven_build() {
    local mvn_opts=""
    if [ "$SKIP_TESTS" = true ]; then
        mvn_opts="$mvn_opts -DskipTests"
    fi
    if [ -f "$M2_SETTINGS" ]; then
        mvn_opts="$mvn_opts -s $M2_SETTINGS"
    fi

    log_info "Maven 全量构建 (跳过测试: $SKIP_TESTS)..."
    cd "$PROJECT_ROOT"

    local start_time=$(date +%s)
    mvn clean package $mvn_opts -q 2>&1 | tail -5
    local end_time=$(date +%s)
    local elapsed=$((end_time - start_time))

    log_success "Maven 构建完成 (耗时: ${elapsed}s)"
}

# ---- 检查 Jar 包 ----
check_jars() {
    log_info "检查各服务 jar 包..."
    local failed=()

    for svc in "${BACKEND_SERVICES[@]}"; do
        local jar_file=""
        if [ "$SELECTED_SERVICES" != "" ]; then
            if [[ ! " ${backend_to_build[*]} " =~ " $svc " ]]; then
                continue
            fi
        fi
        jar_file=$(find "${PROJECT_ROOT}/${svc}/target" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" ! -name "*.original" 2>/dev/null | head -1)
        if [ -z "$jar_file" ] || [ ! -f "$jar_file" ]; then
            log_error "  缺少 jar: $svc/target/*.jar"
            failed+=("$svc")
        fi
    done

    if [ ${#failed[@]} -gt 0 ]; then
        log_error "以下服务未生成jar包: ${failed[*]}"
        log_error "请检查Maven构建日志"
        exit 1
    fi
    log_success "所有服务jar包检查通过"
}

# ---- 构建单个后端镜像 ----
build_backend_image() {
    local svc=$1
    local jar_file
    local port

    jar_file=$(find "${PROJECT_ROOT}/${svc}/target" -maxdepth 1 -name "*.jar" ! -name "*-sources.jar" ! -name "*.original" 2>/dev/null | head -1)
    if [ -z "$jar_file" ]; then
        log_error "[$svc] 未找到jar文件"
        return 1
    fi

    port=$(grep -m1 'server.port' "${PROJECT_ROOT}/${svc}/src/main/resources/application.yml" 2>/dev/null | awk -F': ' '{print $2}' | tr -d ' ' || echo "8080")

    log_info "构建后端镜像: ${REGISTRY}/${svc}:${VERSION} (端口: $port)"

    docker build \
        --build-arg JAR_FILE="$(basename "$jar_file")" \
        --build-arg SERVICE_NAME="$svc" \
        --build-arg SERVER_PORT="$port" \
        -f "$DOCKERFILE" \
        -t "${REGISTRY}/${svc}:${VERSION}" \
        "$PROJECT_ROOT/$svc" 2>&1 | tail -3

    if [ $? -eq 0 ]; then
        log_success "[$svc] 镜像构建完成: ${REGISTRY}/${svc}:${VERSION}"
        return 0
    else
        log_error "[$svc] 镜像构建失败"
        return 1
    fi
}

# ---- 构建单个前端镜像 ----
build_frontend_image() {
    local project=$1

    log_info "构建前端镜像: ${REGISTRY}/${project}:${VERSION}"

    docker build \
        -f "$DOCKERFILE_FRONTEND" \
        --build-arg PROJECT_DIR="$project" \
        -t "${REGISTRY}/${project}:${VERSION}" \
        "$PROJECT_ROOT/$project" 2>&1 | tail -3

    if [ $? -eq 0 ]; then
        log_success "[$project] 前端镜像构建完成: ${REGISTRY}/${project}:${VERSION}"
        return 0
    else
        log_error "[$project] 前端镜像构建失败"
        return 1
    fi
}

# ---- 构建 Docker 镜像 ----
build_docker_images() {
    log_info "开始构建 Docker 镜像..."

    local failed_services=()
    local total=0
    local success=0

    # 后端镜像
    local to_build=()
    if [ -n "$SELECTED_SERVICES" ]; then
        to_build=("${backend_to_build[@]}")
    else
        to_build=("${BACKEND_SERVICES[@]}")
    fi

    for svc in "${to_build[@]}"; do
        total=$((total + 1))
        if build_backend_image "$svc"; then
            success=$((success + 1))
        else
            failed_services+=("$svc")
            log_error "[$svc] 构建失败, 立即停止"
            exit 1
        fi
    done

    # 前端镜像
    local frontend_to_build_list=()
    if [ -n "$SELECTED_SERVICES" ]; then
        frontend_to_build_list=("${frontend_to_build[@]}")
    else
        frontend_to_build_list=("${FRONTEND_PROJECTS[@]}")
    fi

    for project in "${frontend_to_build_list[@]}"; do
        total=$((total + 1))
        if build_frontend_image "$project"; then
            success=$((success + 1))
        else
            failed_services+=("$project")
            log_error "[$project] 前端镜像构建失败, 立即停止"
            exit 1
        fi
    done

    log_success "Docker 镜像构建完成: ${success}/${total}"
}

# ---- 输出构建结果 ----
print_summary() {
    echo ""
    echo -e "${BOLD}============================================================${NC}"
    echo -e "${BOLD}${GREEN}  链盛通LSC系统 V6.2 构建结果摘要${NC}${NC}"
    echo -e "${BOLD}============================================================${NC}"
    echo ""

    if [ "$NO_DOCKER" = false ]; then
        log_info "已构建的 Docker 镜像:"
        docker images | grep "$REGISTRY" | grep "$VERSION" | sort
        echo ""
    fi

    log_info "配置信息:"
    echo "  项目根目录: $PROJECT_ROOT"
    echo "  镜像仓库:   $REGISTRY"
    echo "  版本标签:   $VERSION"
    echo "  跳过测试:   $SKIP_TESTS"
    echo "  Docker构建: $([ "$NO_DOCKER" = true ] && echo "跳过" || echo "已启用")"
    echo ""
    log_success "构建流程全部完成!"
}

# ---- 主流程 ----
main() {
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo -e "${BOLD}${CYAN}  链盛通LSC系统 V6.2 统一构建脚本${NC}"
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo ""

    check_deps
    resolve_services

    # Step 1: Maven 构建
    maven_build

    # Step 2: 检查 jar
    check_jars

    # Step 3: 构建 Docker 镜像
    if [ "$NO_DOCKER" = false ]; then
        build_docker_images
    else
        log_warn "--no-docker 已启用, 跳过 Docker 镜像构建"
    fi

    # Step 4: 输出摘要
    print_summary
}

main "$@"