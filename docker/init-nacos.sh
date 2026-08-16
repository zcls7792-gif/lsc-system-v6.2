#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2 - Nacos配置初始化脚本
# 功能: 自动连接Nacos API并上传共享配置
# 用法: ./init-nacos.sh [OPTIONS]
#   --host       Nacos地址 (默认: localhost:8848)
#   --username   Nacos用户名 (默认: lsc_nacos)
#   --password   Nacos密码 (默认: Lsc@Nacos2026)
#   --namespace  命名空间ID (默认: public)
#   --group      配置分组 (默认: LSC_GROUP)
#   --help       显示帮助信息
# 环境变量:
#   NACOS_HOST, NACOS_USERNAME, NACOS_PASSWORD, NACOS_NAMESPACE
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
NACOS_HOST="${NACOS_HOST:-localhost:8848}"
NACOS_USERNAME="${NACOS_USERNAME:-lsc_nacos}"
NACOS_PASSWORD="${NACOS_PASSWORD:-Lsc@Nacos2026}"
NACOS_NAMESPACE="${NACOS_NAMESPACE:-public}"
NACOS_GROUP="${NACOS_GROUP:-LSC_GROUP}"
CONFIG_DIR="${CONFIG_DIR:-/workspace/config/nacos}"
NACOS_BASE_URL="http://${NACOS_HOST}/nacos/v1"

# 需要上传的配置文件
declare -A CONFIG_FILES
CONFIG_FILES["lsc-common-datasource.yaml"]="lsc-common-datasource.yaml"
CONFIG_FILES["lsc-common-infra.yaml"]="lsc-common-infra.yaml"
CONFIG_FILES["lsc-common-redis.yaml"]="lsc-common-redis.yaml"
CONFIG_FILES["lsc-gateway-routes.yaml"]="lsc-gateway-routes.yaml"

# ---- 参数解析 ----
show_help() {
    cat << EOF
链盛通LSC系统 V6.2 Nacos配置初始化

用法: $0 [OPTIONS]

选项:
  --host         Nacos服务器地址, 默认: localhost:8848
  --username     Nacos用户名, 默认: lsc_nacos
  --password     Nacos密码, 默认: Lsc@Nacos2026
  --namespace    Nacos命名空间ID, 默认: public
  --group        配置分组, 默认: LSC_GROUP
  --config-dir   配置文件目录, 默认: /workspace/config/nacos
  --help         显示本帮助信息

环境变量:
  NACOS_HOST, NACOS_USERNAME, NACOS_PASSWORD, NACOS_NAMESPACE, NACOS_GROUP

示例:
  $0                                    # 使用默认配置
  $0 --host=10.0.0.1:8848               # 指定Nacos地址
  NACOS_HOST=10.0.0.1:8848 $0            # 通过环境变量指定
EOF
    exit 0
}

for arg in "$@"; do
    case "$arg" in
        --help)
            show_help
            ;;
        --host=*)
            NACOS_HOST="${arg#*=}"
            NACOS_BASE_URL="http://${NACOS_HOST}/nacos/v1"
            ;;
        --username=*)
            NACOS_USERNAME="${arg#*=}"
            ;;
        --password=*)
            NACOS_PASSWORD="${arg#*=}"
            ;;
        --namespace=*)
            NACOS_NAMESPACE="${arg#*=}"
            ;;
        --group=*)
            NACOS_GROUP="${arg#*=}"
            ;;
        --config-dir=*)
            CONFIG_DIR="${arg#*=}"
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
    if ! command -v curl &>/dev/null; then
        log_error "缺少依赖: curl"
        exit 1
    fi
    if ! command -v jq &>/dev/null; then
        log_warn "建议安装 jq 以获得更好的JSON解析支持"
    fi
    log_success "依赖检查通过"
}

# ---- 等待 Nacos 就绪 ----
wait_nacos() {
    local max_wait=60
    local waited=0

    log_info "等待 Nacos 服务就绪 (${NACOS_HOST})..."

    while [ $waited -lt $max_wait ]; do
        if curl -s -o /dev/null -w '%{http_code}' "${NACOS_BASE_URL}/console/health/readiness" 2>/dev/null | grep -q '200'; then
            log_success "Nacos 服务已就绪"
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
        printf "\r  等待中... %ds" $waited
    done
    echo ""
    log_error "等待 Nacos 超时 (${max_wait}s)"
    exit 1
}

# ---- Nacos 登录 ----
nacos_login() {
    log_info "登录 Nacos (用户: ${NACOS_USERNAME})..."

    local response
    response=$(curl -s -X POST "${NACOS_BASE_URL}/auth/users/login" \
        -d "username=${NACOS_USERNAME}&password=${NACOS_PASSWORD}" 2>/dev/null)

    if echo "$response" | grep -q '"accessToken"'; then
        NACOS_TOKEN=$(echo "$response" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
        log_success "Nacos 登录成功"
    else
        log_warn "Nacos 登录返回: $response"
        NACOS_TOKEN=""
    fi
}

# ---- 上传单个配置 ----
upload_config() {
    local data_id=$1
    local file_name=$2
    local file_path="${CONFIG_DIR}/${file_name}"

    if [ ! -f "$file_path" ]; then
        log_warn "配置文件不存在, 跳过: $file_path"
        return 0
    fi

    log_info "上传配置: ${data_id} (文件: ${file_name})"

    local content
    content=$(cat "$file_path")

    local auth_params=""
    if [ -n "$NACOS_TOKEN" ]; then
        auth_params="&accessToken=${NACOS_TOKEN}"
    fi

    local response
    response=$(curl -s -X POST \
        "${NACOS_BASE_URL}/configs?tenant=${NACOS_NAMESPACE}&group=${NACOS_GROUP}&dataId=${data_id}${auth_params}" \
        --data-urlencode "content=${content}" 2>/dev/null)

    if echo "$response" | grep -q '"ok"'; then
        log_success "  ${data_id} 上传成功"
        return 0
    else
        log_error "  ${data_id} 上传失败: $response"
        return 1
    fi
}

# ---- 主流程 ----
main() {
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo -e "${BOLD}${CYAN}  链盛通LSC系统 V6.2 Nacos配置初始化${NC}"
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo ""

    log_info "Nacos 地址: ${NACOS_HOST}"
    log_info "命名空间:   ${NACOS_NAMESPACE}"
    log_info "配置分组:   ${NACOS_GROUP}"
    log_info "配置目录:   ${CONFIG_DIR}"
    echo ""

    check_deps
    wait_nacos
    nacos_login

    echo ""
    log_info "开始上传配置文件..."
    echo ""

    local failed=()
    for data_id in "${!CONFIG_FILES[@]}"; do
        local file_name="${CONFIG_FILES[$data_id]}"
        if ! upload_config "$data_id" "$file_name"; then
            failed+=("$data_id")
        fi
    done

    echo ""
    if [ ${#failed[@]} -eq 0 ]; then
        log_success "所有 Nacos 配置上传完成!"
    else
        log_error "以下配置上传失败: ${failed[*]}"
        exit 1
    fi

    echo ""
    log_info "验证配置..."
    for data_id in "${!CONFIG_FILES[@]}"; do
        local auth_params=""
        if [ -n "$NACOS_TOKEN" ]; then
            auth_params="&accessToken=${NACOS_TOKEN}"
        fi
        local check
        check=$(curl -s "${NACOS_BASE_URL}/configs?tenant=${NACOS_NAMESPACE}&group=${NACOS_GROUP}&dataId=${data_id}${auth_params}" 2>/dev/null)
        if [ -n "$check" ] && echo "$check" | grep -q -v '"errcode"'; then
            log_success "  ${data_id} - 已验证"
        else
            log_warn "  ${data_id} - 验证异常"
        fi
    done

    echo ""
    log_success "Nacos 配置初始化完成!"
    log_info "请访问 Nacos 控制台确认: http://${NACOS_HOST}/nacos"
}

main "$@"