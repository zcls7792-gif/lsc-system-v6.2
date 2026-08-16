#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2 - 开发环境快速启动
# 功能: 本地开发模式启动核心8个服务
# 用法: ./start-dev.sh [OPTIONS]
#   --skip-build    跳过镜像构建
#   --core-only     仅启动核心服务(默认)
#   --all           启动所有服务
#   --help          显示帮助信息
# 环境变量:
#   REGISTRY, VERSION, DEV_COMPOSE_FILE
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
DEV_COMPOSE="${DOCKER_DIR}/docker-compose-dev.yml"
BUILD_SCRIPT="${DOCKER_DIR}/build-all.sh"
INIT_NACOS_SCRIPT="${DOCKER_DIR}/init-nacos.sh"
INIT_DB_SCRIPT="${DOCKER_DIR}/init-db.sh"

# 核心8个服务 (端口分配)
CORE_SERVICES=(
    "lsc-gateway:8000"
    "lsc-user-service:8101"
    "lsc-ledger-service:8102"
    "lsc-order-service:8104"
    "lsc-writeoff-service:8105"
    "lsc-release-service:8106"
    "lsc-b2b-service:8103"
    "lsc-admin-service:8200"
)

# 完整服务列表
ALL_SERVICES=(
    "lsc-gateway:8000"
    "lsc-user-service:8101"
    "lsc-ledger-service:8102"
    "lsc-b2b-service:8103"
    "lsc-order-service:8104"
    "lsc-writeoff-service:8105"
    "lsc-release-service:8106"
    "lsc-promotion-service:8107"
    "lsc-mall-service:8108"
    "lsc-risk-service:8109"
    "lsc-media-service:8110"
    "lsc-map-service:8111"
    "lsc-reconciliation-service:8112"
    "lsc-evidence-service:8113"
    "lsc-admin-service:8200"
    "lsc-ai-gateway:8201"
)

# ---- 参数解析 ----
SKIP_BUILD=false
START_ALL=false

show_help() {
    cat << EOF
链盛通LSC系统 V6.2 开发环境快速启动

用法: $0 [OPTIONS]

选项:
  --skip-build     跳过镜像构建
  --core-only      仅启动核心8个服务 (默认)
  --all            启动所有服务
  --help           显示本帮助信息

环境变量:
  REGISTRY         镜像仓库, 默认: lsc
  VERSION          镜像版本, 默认: 6.2.0
  PROJECT_ROOT     项目根目录, 默认: /workspace

示例:
  $0                               # 启动核心8个服务
  $0 --all                         # 启动所有16个服务
  $0 --skip-build                  使用已有镜像
EOF
    exit 0
}

for arg in "$@"; do
    case "$arg" in
        --help)
            show_help
            ;;
        --skip-build)
            SKIP_BUILD=true
            ;;
        --core-only)
            START_ALL=false
            ;;
        --all)
            START_ALL=true
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
        log_warn "跳过镜像构建 (--skip-build)"
        return 0
    fi

    log_info "[Step 1] 构建核心服务镜像..."
    local services_list=""
    if [ "$START_ALL" = true ]; then
        services_list=$(printf "%s," "${ALL_SERVICES[@]}" | sed 's/:[0-9]*//g' | sed 's/,$//')
    else
        services_list=$(printf "%s," "${CORE_SERVICES[@]}" | sed 's/:[0-9]*//g' | sed 's/,$//')
    fi

    if [ -x "$BUILD_SCRIPT" ]; then
        bash "$BUILD_SCRIPT" --skip-tests --services="$services_list" || {
            log_error "镜像构建失败"
            exit 1
        }
    else
        log_error "构建脚本不存在: $BUILD_SCRIPT"
        exit 1
    fi
    log_success "[Step 1] 核心服务镜像构建完成"
}

# ---- 启动基础设施 ----
step_start_infra() {
    log_info "[Step 2] 启动基础设施..."
    docker_compose -f "$INFRA_COMPOSE" up -d
    log_success "[Step 2] 基础设施启动命令已发送"
}

# ---- 等待基础设施就绪 ----
step_wait_infra() {
    log_info "[Step 3] 等待基础设施就绪..."

    local max_wait=90
    local waited=0

    while [ $waited -lt $max_wait ]; do
        if docker exec lsc-mysql mysql -uroot -p'Lsc@2026#Secure' -e "SELECT 1" &>/dev/null; then
            log_success "  MySQL 已就绪"
            break
        fi
        sleep 3
        waited=$((waited + 3))
        printf "\r  等待MySQL就绪... %ds" $waited
    done
    echo ""

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

    waited=0
    while [ $waited -lt $max_wait ]; do
        if curl -s -o /dev/null -w '%{http_code}' "http://localhost:8848/nacos/console/health/readiness" 2>/dev/null | grep -q '200'; then
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

# ---- 初始化配置 ----
step_init_config() {
    log_info "[Step 4] 初始化Nacos配置..."
    if [ -x "$INIT_NACOS_SCRIPT" ]; then
        bash "$INIT_NACOS_SCRIPT" --host="localhost:8848" || log_warn "Nacos配置导入失败, 请手动导入"
    fi

    log_info "[Step 5] 初始化数据库..."
    if [ -x "$INIT_DB_SCRIPT" ]; then
        bash "$INIT_DB_SCRIPT" --host="localhost" --port="3306" || log_warn "数据库初始化失败"
    fi
    log_success "[Step 4-5] 配置初始化完成"
}

# ---- 生成 dev compose 文件并启动 ----
step_start_app() {
    log_info "[Step 6] 启动开发服务..."

    local compose_file="$DEV_COMPOSE"
    local service_list
    if [ "$START_ALL" = true ]; then
        service_list=$(printf "%s " "${ALL_SERVICES[@]}" | sed 's/:[0-9]*//g')
    else
        service_list=$(printf "%s " "${CORE_SERVICES[@]}" | sed 's/:[0-9]*//g')
    fi

    # 如果dev compose不存在, 从模板生成
    if [ ! -f "$DEV_COMPOSE" ]; then
        log_warn "开发compose文件不存在, 正在生成..."
        generate_dev_compose
    fi

    # 构建启动参数
    docker_compose -f "$DEV_COMPOSE" up -d $service_list
    log_success "[Step 6] 开发服务启动命令已发送"
}

# ---- 生成 docker-compose-dev.yml ----
generate_dev_compose() {
    cat > "$DEV_COMPOSE" << 'ENDOFFILE'
version: '3.8'

services:
  lsc-gateway:
    image: lsc/lsc-gateway:6.2.0
    container_name: dev-lsc-gateway
    restart: unless-stopped
    ports:
      - "8000:8000"
    environment:
      - SERVER_PORT=8000
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-user-service:
    image: lsc/lsc-user-service:6.2.0
    container_name: dev-lsc-user-service
    restart: unless-stopped
    ports:
      - "8101:8101"
    environment:
      - SERVER_PORT=8101
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-ledger-service:
    image: lsc/lsc-ledger-service:6.2.0
    container_name: dev-lsc-ledger-service
    restart: unless-stopped
    ports:
      - "8102:8102"
    environment:
      - SERVER_PORT=8102
      - SPRING_PROFILES_ACTIVE=dev
      - JAVA_OPTS=-Xms512m -Xmx1g -XX:+UseG1GC
    networks:
      - lsc-net

  lsc-b2b-service:
    image: lsc/lsc-b2b-service:6.2.0
    container_name: dev-lsc-b2b-service
    restart: unless-stopped
    ports:
      - "8103:8103"
    environment:
      - SERVER_PORT=8103
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-order-service:
    image: lsc/lsc-order-service:6.2.0
    container_name: dev-lsc-order-service
    restart: unless-stopped
    ports:
      - "8104:8104"
    environment:
      - SERVER_PORT=8104
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-writeoff-service:
    image: lsc/lsc-writeoff-service:6.2.0
    container_name: dev-lsc-writeoff-service
    restart: unless-stopped
    ports:
      - "8105:8105"
    environment:
      - SERVER_PORT=8105
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-release-service:
    image: lsc/lsc-release-service:6.2.0
    container_name: dev-lsc-release-service
    restart: unless-stopped
    ports:
      - "8106:8106"
    environment:
      - SERVER_PORT=8106
      - SPRING_PROFILES_ACTIVE=dev
      - JAVA_OPTS=-Xms512m -Xmx1g -XX:+UseG1GC
    networks:
      - lsc-net

  lsc-promotion-service:
    image: lsc/lsc-promotion-service:6.2.0
    container_name: dev-lsc-promotion-service
    restart: unless-stopped
    ports:
      - "8107:8107"
    environment:
      - SERVER_PORT=8107
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-mall-service:
    image: lsc/lsc-mall-service:6.2.0
    container_name: dev-lsc-mall-service
    restart: unless-stopped
    ports:
      - "8108:8108"
    environment:
      - SERVER_PORT=8108
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-risk-service:
    image: lsc/lsc-risk-service:6.2.0
    container_name: dev-lsc-risk-service
    restart: unless-stopped
    ports:
      - "8109:8109"
    environment:
      - SERVER_PORT=8109
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-media-service:
    image: lsc/lsc-media-service:6.2.0
    container_name: dev-lsc-media-service
    restart: unless-stopped
    ports:
      - "8110:8110"
    environment:
      - SERVER_PORT=8110
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-map-service:
    image: lsc/lsc-map-service:6.2.0
    container_name: dev-lsc-map-service
    restart: unless-stopped
    ports:
      - "8111:8111"
    environment:
      - SERVER_PORT=8111
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-reconciliation-service:
    image: lsc/lsc-reconciliation-service:6.2.0
    container_name: dev-lsc-reconciliation-service
    restart: unless-stopped
    ports:
      - "8112:8112"
    environment:
      - SERVER_PORT=8112
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-evidence-service:
    image: lsc/lsc-evidence-service:6.2.0
    container_name: dev-lsc-evidence-service
    restart: unless-stopped
    ports:
      - "8113:8113"
    environment:
      - SERVER_PORT=8113
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-admin-service:
    image: lsc/lsc-admin-service:6.2.0
    container_name: dev-lsc-admin-service
    restart: unless-stopped
    ports:
      - "8200:8200"
    environment:
      - SERVER_PORT=8200
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

  lsc-ai-gateway:
    image: lsc/lsc-ai-gateway:6.2.0
    container_name: dev-lsc-ai-gateway
    restart: unless-stopped
    ports:
      - "8201:8201"
    environment:
      - SERVER_PORT=8201
      - SPRING_PROFILES_ACTIVE=dev
    networks:
      - lsc-net

networks:
  lsc-net:
    external: true
ENDOFFILE
    log_success "开发compose文件已生成: $DEV_COMPOSE"
}

# ---- 等待健康检查 ----
step_wait_health() {
    log_info "[Step 7] 等待服务健康检查..."

    local max_wait=120
    local waited=0
    local services_to_check
    if [ "$START_ALL" = true ]; then
        services_to_check=("${ALL_SERVICES[@]}")
    else
        services_to_check=("${CORE_SERVICES[@]}")
    fi

    while [ $waited -lt $max_wait ]; do
        local all_ok=true
        local ok_count=0

        for svc_port in "${services_to_check[@]}"; do
            local svc="${svc_port%%:*}"
            local port="${svc_port##*:}"
            local health

            health=$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${port}/actuator/health" 2>/dev/null)
            if [ "$health" = "200" ]; then
                ok_count=$((ok_count + 1))
            else
                all_ok=false
            fi
        done

        local total=${#services_to_check[@]}
        printf "\r  健康检查进度... %d/%d (等待 %ds)" $ok_count $total $waited

        if [ "$all_ok" = true ]; then
            echo ""
            log_success "所有服务健康检查通过!"
            return 0
        fi

        sleep 5
        waited=$((waited + 5))
    done
    echo ""
    log_warn "部分服务健康检查超时"
    log_info "请手动检查: docker compose -f $DEV_COMPOSE ps"
}

# ---- 输出开发环境信息 ----
print_dev_info() {
    echo ""
    echo -e "${BOLD}============================================================${NC}"
    echo -e "${BOLD}${GREEN}  链盛通LSC系统 V6.2 开发环境已启动${NC}${NC}"
    echo -e "${BOLD}============================================================${NC}"
    echo ""

    if [ "$START_ALL" = true ]; then
        log_info "已启动所有 ${#ALL_SERVICES[@]} 个服务:"
        for svc_port in "${ALL_SERVICES[@]}"; do
            local svc="${svc_port%%:*}"
            local port="${svc_port##*:}"
            echo "  ${GREEN}✓${NC} $svc -> http://localhost:$port"
        done
    else
        log_info "已启动核心 ${#CORE_SERVICES[@]} 个服务:"
        for svc_port in "${CORE_SERVICES[@]}"; do
            local svc="${svc_port%%:*}"
            local port="${svc_port##*:}"
            echo "  ${GREEN}✓${NC} $svc -> http://localhost:$port"
        done
        echo ""
        log_info "完整服务列表 (按需启动):"
        for svc_port in "${ALL_SERVICES[@]}"; do
            local svc="${svc_port%%:*}"
            local port="${svc_port##*:}"
            local skip=true
            for core in "${CORE_SERVICES[@]}"; do
                if [ "$core" = "$svc_port" ]; then
                    skip=false
                    break
                fi
            done
            if [ "$skip" = true ]; then
                echo "  ${YELLOW}○${NC} $svc -> http://localhost:$port (使用: docker compose -f $DEV_COMPOSE up -d $svc)"
            fi
        done
    fi

    echo ""
    echo "  ${BOLD}基础设施:${NC}"
    echo "    MySQL:      localhost:3306"
    echo "    Redis:      localhost:7000-7005"
    echo "    RabbitMQ:   localhost:5672 (15672)"
    echo "    Nacos:      http://localhost:8848/nacos"
    echo "    Seata:      localhost:8091"
    echo "    XXL-JOB:    http://localhost:8080/xxl-job-admin"
    echo ""
    echo "  ${BOLD}API网关入口:${NC}"
    echo "    http://localhost:8000  (统一入口, 路由到各微服务)"
    echo ""
    echo "  ${BOLD}开发模式常用命令:${NC}"
    echo "    查看状态:   docker compose -f $DEV_COMPOSE ps"
    echo "    查看日志:   docker compose -f $DEV_COMPOSE logs -f <service>"
    echo "    停止服务:   docker compose -f $DEV_COMPOSE down"
    echo "    重启单个:   docker compose -f $DEV_COMPOSE restart <service>"
    echo ""
    echo "  ${BOLD}本地开发调试:${NC}"
    echo "    # 停止某个容器, 改为本地IDE启动"
    echo "    docker compose -f $DEV_COMPOSE stop lsc-user-service"
    echo "    cd lsc-user-service && mvn spring-boot:run"
}

# ---- 主流程 ----
main() {
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo -e "${BOLD}${CYAN}  链盛通LSC系统 V6.2 开发环境快速启动${NC}"
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo ""

    check_deps
    step_build
    step_start_infra
    step_wait_infra
    step_init_config
    step_start_app
    step_wait_health
    print_dev_info
}

main "$@"