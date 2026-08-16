#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2 - 本地一键部署脚本
# 功能: 启动基础设施 + 初始化配置 + 部署应用服务
# 用法: ./deploy-local.sh [OPTIONS]
#   --infra-only     仅部署基础设施
#   --app-only       仅部署应用服务(需基础设施已就绪)
#   --skip-build     跳过镜像构建(使用已有镜像)
#   --skip-init-db   跳过数据库初始化
#   --skip-init-nacos 跳过Nacos配置导入
#   --help           显示帮助信息
# 环境变量:
#   REGISTRY, VERSION, MYSQL_HOST, NACOS_HOST 等
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

INFRA_COMPOSE="${DOCKER_DIR}/docker-compose.yml"
APP_COMPOSE="${DOCKER_DIR}/docker-compose-app.yml"
DEV_COMPOSE="${DOCKER_DIR}/docker-compose-dev.yml"
BUILD_SCRIPT="${DOCKER_DIR}/build-all.sh"
INIT_NACOS_SCRIPT="${DOCKER_DIR}/init-nacos.sh"
INIT_DB_SCRIPT="${DOCKER_DIR}/init-db.sh"

MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
NACOS_HOST="${NACOS_HOST:-localhost:8848}"

# ---- 参数解析 ----
INFRA_ONLY=false
APP_ONLY=false
SKIP_BUILD=false
SKIP_INIT_DB=false
SKIP_INIT_NACOS=false

show_help() {
    cat << EOF
链盛通LSC系统 V6.2 本地一键部署

用法: $0 [OPTIONS]

选项:
  --infra-only       仅部署基础设施 (MySQL/Redis/Nacos等)
  --app-only         仅部署应用服务 (需基础设施已就绪)
  --skip-build       跳过镜像构建, 使用本地已有镜像
  --skip-init-db    跳过数据库初始化
  --skip-init-nacos 跳过Nacos配置导入
  --help             显示本帮助信息

环境变量:
  REGISTRY           镜像仓库, 默认: lsc
  VERSION            镜像版本, 默认: 6.2.0
  MYSQL_HOST         MySQL地址, 默认: localhost
  NACOS_HOST         Nacos地址, 默认: localhost:8848
  PROJECT_ROOT       项目根目录, 默认: /workspace

示例:
  $0                              # 完整部署: 构建+基础设施+应用
  $0 --skip-build                 # 使用已有镜像部署
  $0 --infra-only                 # 仅部署基础设施
  $0 --app-only                   # 仅部署应用服务
EOF
    exit 0
}

for arg in "$@"; do
    case "$arg" in
        --help)
            show_help
            ;;
        --infra-only)
            INFRA_ONLY=true
            ;;
        --app-only)
            APP_ONLY=true
            ;;
        --skip-build)
            SKIP_BUILD=true
            ;;
        --skip-init-db)
            SKIP_INIT_DB=true
            ;;
        --skip-init-nacos)
            SKIP_INIT_NACOS=true
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

    if ! command -v docker &>/dev/null; then
        missing+=("docker")
    fi
    if ! command -v docker compose &>/dev/null && ! command -v docker-compose &>/dev/null; then
        missing+=("docker compose 或 docker-compose")
    fi
    if [ "$SKIP_BUILD" = false ]; then
        if ! command -v mvn &>/dev/null; then
            missing+=("mvn")
        fi
    fi

    if [ ${#missing[@]} -gt 0 ]; then
        log_error "缺少必要依赖: ${missing[*]}"
        exit 1
    fi
    log_success "依赖检查通过"
}

# ---- Docker Compose 命令封装 ----
docker_compose() {
    if command -v docker compose &>/dev/null; then
        docker compose "$@"
    else
        docker-compose "$@"
    fi
}

# ---- 构建镜像 ----
step_build() {
    if [ "$SKIP_BUILD" = true ]; then
        log_warn "跳过镜像构建 (--skip-build), 使用已有镜像"
        return 0
    fi

    log_info "[Step 1] 构建Docker镜像..."
    if [ -x "$BUILD_SCRIPT" ]; then
        bash "$BUILD_SCRIPT" --skip-tests || {
            log_error "镜像构建失败"
            exit 1
        }
    else
        log_error "构建脚本不存在: $BUILD_SCRIPT"
        exit 1
    fi
    log_success "[Step 1] 镜像构建完成"
}

# ---- 启动基础设施 ----
step_start_infra() {
    log_info "[Step 2] 启动基础设施 (MySQL/Redis/RabbitMQ/Nacos/Seata/XXL-JOB)..."

    if [ ! -f "$INFRA_COMPOSE" ]; then
        log_error "基础设施compose文件不存在: $INFRA_COMPOSE"
        exit 1
    fi

    docker_compose -f "$INFRA_COMPOSE" up -d
    log_success "[Step 2] 基础设施启动命令已发送"
}

# ---- 等待基础设施就绪 ----
step_wait_infra() {
    log_info "[Step 3] 等待基础设施就绪..."

    local max_wait=120
    local waited=0

    while [ $waited -lt $max_wait ]; do
        # 检查MySQL
        if docker exec lsc-mysql mysql -uroot -p'Lsc@2026#Secure' -e "SELECT 1" &>/dev/null; then
            log_success "  MySQL 已就绪"
            break
        fi
        sleep 3
        waited=$((waited + 3))
        printf "\r  等待MySQL就绪... %ds" $waited
    done
    echo ""

    # 检查Redis集群
    waited=0
    while [ $waited -lt $max_wait ]; do
        if docker exec lsc-redis-1 redis-cli -a 'Lsc@Redis2026' --no-auth-warning ping 2>/dev/null | grep -q 'PONG'; then
            log_success "  Redis 已就绪"
            break
        fi
        sleep 3
        waited=$((waited + 3))
        printf "\r  等待Redis就绪... %ds" $waited
    done
    echo ""

    # 等待Nacos
    waited=0
    while [ $waited -lt $max_wait ]; do
        if curl -s -o /dev/null -w '%{http_code}' "http://${NACOS_HOST}/nacos/console/health/readiness" 2>/dev/null | grep -q '200'; then
            log_success "  Nacos 已就绪"
            break
        fi
        sleep 3
        waited=$((waited + 3))
        printf "\r  等待Nacos就绪... %ds" $waited
    done
    echo ""

    log_success "[Step 3] 基础设施全部就绪"
}

# ---- 导入Nacos配置 ----
step_init_nacos() {
    if [ "$SKIP_INIT_NACOS" = true ]; then
        log_warn "跳过Nacos配置导入 (--skip-init-nacos)"
        return 0
    fi

    log_info "[Step 4] 导入Nacos配置..."
    if [ -x "$INIT_NACOS_SCRIPT" ]; then
        bash "$INIT_NACOS_SCRIPT" --host="$NACOS_HOST" || {
            log_error "Nacos配置导入失败"
            exit 1
        }
    else
        log_warn "Nacos初始化脚本不存在: $INIT_NACOS_SCRIPT"
    fi
    log_success "[Step 4] Nacos配置导入完成"
}

# ---- 初始化数据库 ----
step_init_db() {
    if [ "$SKIP_INIT_DB" = true ]; then
        log_warn "跳过数据库初始化 (--skip-init-db)"
        return 0
    fi

    log_info "[Step 5] 初始化数据库..."
    if [ -x "$INIT_DB_SCRIPT" ]; then
        bash "$INIT_DB_SCRIPT" --host="$MYSQL_HOST" --port="$MYSQL_PORT" || {
            log_error "数据库初始化失败"
            exit 1
        }
    else
        log_warn "数据库初始化脚本不存在: $INIT_DB_SCRIPT"
    fi
    log_success "[Step 5] 数据库初始化完成"
}

# ---- 启动应用服务 ----
step_start_app() {
    log_info "[Step 6] 启动应用服务..."

    if [ ! -f "$APP_COMPOSE" ]; then
        log_error "应用服务compose文件不存在: $APP_COMPOSE"
        log_error "请先创建 docker-compose-app.yml"
        exit 1
    fi

    docker_compose -f "$APP_COMPOSE" up -d
    log_success "[Step 6] 应用服务启动命令已发送"
}

# ---- 等待健康检查通过 ----
step_wait_health() {
    log_info "[Step 7] 等待应用服务健康检查通过..."

    local max_wait=180
    local waited=0
    local all_ready=false

    while [ $waited -lt $max_wait ]; do
        all_ready=true

        # 检查网关
        local gateway_health
        gateway_health=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8000/actuator/health" 2>/dev/null)
        if [ "$gateway_health" != "200" ]; then
            all_ready=false
        fi

        # 检查用户服务
        local user_health
        user_health=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:8101/actuator/health" 2>/dev/null)
        if [ "$user_health" != "200" ]; then
            all_ready=false
        fi

        if [ "$all_ready" = true ]; then
            log_success "  应用服务健康检查通过"
            return 0
        fi

        sleep 5
        waited=$((waited + 5))
        printf "\r  等待应用就绪... %ds (网关:%s 用户:%s)" $waited "$gateway_health" "$user_health"
    done
    echo ""
    log_warn "部分服务健康检查超时, 请手动检查服务状态"
}

# ---- 输出访问地址 ----
print_access_urls() {
    echo ""
    echo -e "${BOLD}============================================================${NC}"
    echo -e "${BOLD}${GREEN}  链盛通LSC系统 V6.2 部署完成${NC}${NC}"
    echo -e "${BOLD}============================================================${NC}"
    echo ""
    log_info "访问地址:"
    echo ""
    echo "  ${BOLD}基础设施:${NC}"
    echo "    MySQL:        ${MYSQL_HOST}:${MYSQL_PORT}"
    echo "    Redis集群:    localhost:7000-7005"
    echo "    RabbitMQ:     localhost:5672 (管理: localhost:15672)"
    echo "    Nacos:        http://localhost:8848/nacos"
    echo "    Seata:        localhost:8091"
    echo "    XXL-JOB:      http://localhost:8080/xxl-job-admin"
    echo "    Prometheus:   http://localhost:9090"
    echo "    Grafana:      http://localhost:3000 (admin / Lsc@Grafana2026)"
    echo ""
    echo "  ${BOLD}应用服务:${NC}"
    echo "    API网关:      http://localhost:8000"
    echo "    管理后台:     http://localhost:8200"
    echo "    用户服务:     http://localhost:8101"
    echo "    账本服务:     http://localhost:8102"
    echo "    B2B服务:      http://localhost:8103"
    echo "    订单服务:     http://localhost:8104"
    echo "    核销服务:     http://localhost:8105"
    echo "    释放服务:     http://localhost:8106"
    echo "    促销服务:     http://localhost:8107"
    echo "    AI网关:       http://localhost:8201"
    echo ""
    echo "  ${BOLD}前端页面:${NC}"
    echo "    管理后台UI:   http://localhost:80 (或通过网关访问)"
    echo "    商家管理UI:   http://localhost:81 (或通过网关访问)"
    echo ""
    log_info "常用命令:"
    echo "  查看服务状态:   docker compose -f ${APP_COMPOSE} ps"
    echo "  查看日志:       docker compose -f ${APP_COMPOSE} logs -f [service]"
    echo "  停止应用:       docker compose -f ${APP_COMPOSE} down"
    echo "  停止全部:       docker compose -f ${INFRA_COMPOSE} down"
    echo ""
}

# ---- 主流程 ----
main() {
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo -e "${BOLD}${CYAN}  链盛通LSC系统 V6.2 本地一键部署${NC}"
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo ""

    check_deps

    # 仅基础设施模式
    if [ "$INFRA_ONLY" = true ]; then
        step_start_infra
        step_wait_infra
        step_init_nacos
        step_init_db
        print_access_urls
        exit 0
    fi

    # 仅应用模式
    if [ "$APP_ONLY" = true ]; then
        step_start_app
        step_wait_health
        print_access_urls
        exit 0
    fi

    # 完整部署
    step_build
    step_start_infra
    step_wait_infra
    step_init_nacos
    step_init_db
    step_start_app
    step_wait_health
    print_access_urls
}

main "$@"