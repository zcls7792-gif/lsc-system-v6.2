#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2 - 数据库初始化脚本
# 功能: 等待MySQL就绪, 执行SQL初始化脚本
# 用法: ./init-db.sh [OPTIONS]
#   --host       MySQL主机 (默认: localhost)
#   --port       MySQL端口 (默认: 3306)
#   --user       MySQL用户名 (默认: root)
#   --password   MySQL密码 (默认: Lsc@2026#Secure)
#   --database   目标数据库 (默认: lsc_system)
#   --skip-wait  跳过等待MySQL就绪
#   --help       显示帮助信息
# 环境变量:
#   MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD
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
MYSQL_HOST="${MYSQL_HOST:-localhost}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-Lsc@2026#Secure}"
MYSQL_DATABASE="${MYSQL_DATABASE:-lsc_system}"
SQL_DIR="${SQL_DIR:-/workspace/sql}"
SKIP_WAIT=false
WAIT_TIMEOUT=${WAIT_TIMEOUT:-60}

SQL_SCHEMA="${SQL_DIR}/lsc_system_v6.2.sql"
SQL_SHARDING="${SQL_DIR}/lsc_sharding.sql"

# ---- 参数解析 ----
show_help() {
    cat << EOF
链盛通LSC系统 V6.2 数据库初始化

用法: $0 [OPTIONS]

选项:
  --host=HOST       MySQL主机地址, 默认: localhost
  --port=PORT       MySQL端口, 默认: 3306
  --user=USER       MySQL用户名, 默认: root
  --password=PASS   MySQL密码, 默认: Lsc@2026#Secure
  --database=DB     目标数据库, 默认: lsc_system
  --sql-dir=DIR     SQL脚本目录, 默认: /workspace/sql
  --skip-wait       跳过等待MySQL就绪
  --help            显示本帮助信息

环境变量:
  MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE

示例:
  $0                                    # 使用默认配置
  $0 --host=10.0.0.1 --password=MyPASS  # 指定MySQL连接
  MYSQL_HOST=10.0.0.1 $0                # 通过环境变量指定
EOF
    exit 0
}

for arg in "$@"; do
    case "$arg" in
        --help)
            show_help
            ;;
        --host=*)
            MYSQL_HOST="${arg#*=}"
            ;;
        --port=*)
            MYSQL_PORT="${arg#*=}"
            ;;
        --user=*)
            MYSQL_USER="${arg#*=}"
            ;;
        --password=*)
            MYSQL_PASSWORD="${arg#*=}"
            ;;
        --database=*)
            MYSQL_DATABASE="${arg#*=}"
            ;;
        --sql-dir=*)
            SQL_DIR="${arg#*=}"
            SQL_SCHEMA="${SQL_DIR}/lsc_system_v6.2.sql"
            SQL_SHARDING="${SQL_DIR}/lsc_sharding.sql"
            ;;
        --skip-wait)
            SKIP_WAIT=true
            ;;
        *)
            log_error "未知参数: $arg"
            echo "使用 --help 查看帮助信息"
            exit 1
            ;;
    esac
done

MYSQL_CMD="mysql -h ${MYSQL_HOST} -P ${MYSQL_PORT} -u ${MYSQL_USER} -p${MYSQL_PASSWORD}"

# ---- 依赖检查 ----
check_deps() {
    log_info "检查依赖工具..."
    if ! command -v mysql &>/dev/null; then
        log_error "缺少依赖: mysql (MySQL客户端)"
        log_error "请安装: apt-get install mysql-client 或 yum install mysql"
        exit 1
    fi
    log_success "依赖检查通过"
}

# ---- 等待 MySQL 就绪 ----
wait_mysql() {
    if [ "$SKIP_WAIT" = true ]; then
        log_warn "跳过等待MySQL (--skip-wait)"
        return 0
    fi

    log_info "等待 MySQL 服务就绪 (${MYSQL_HOST}:${MYSQL_PORT})..."
    local waited=0

    while [ $waited -lt $WAIT_TIMEOUT ]; do
        if $MYSQL_CMD -e "SELECT 1" &>/dev/null; then
            log_success "MySQL 服务已就绪"
            return 0
        fi
        sleep 2
        waited=$((waited + 2))
        printf "\r  等待中... %ds" $waited
    done
    echo ""
    log_error "等待 MySQL 超时 (${WAIT_TIMEOUT}s)"
    log_error "请检查MySQL服务是否启动: ${MYSQL_HOST}:${MYSQL_PORT}"
    exit 1
}

# ---- 初始化数据库 ----
init_database() {
    log_info "创建主数据库: ${MYSQL_DATABASE}"
    $MYSQL_CMD -e "CREATE DATABASE IF NOT EXISTS \`${MYSQL_DATABASE}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null
    log_success "数据库 ${MYSQL_DATABASE} 已就绪"
}

# ---- 执行SQL脚本 ----
exec_sql_file() {
    local file=$1
    local desc=$2

    if [ ! -f "$file" ]; then
        log_warn "SQL文件不存在, 跳过: $file"
        return 0
    fi

    log_info "执行 SQL: ${desc}"
    log_info "  文件: $file"

    local start_time=$(date +%s)

    if $MYSQL_CMD "${MYSQL_DATABASE}" < "$file" 2>&1; then
        local end_time=$(date +%s)
        local elapsed=$((end_time - start_time))
        log_success "  ${desc} 执行成功 (耗时: ${elapsed}s)"
        return 0
    else
        log_error "  ${desc} 执行失败"
        return 1
    fi
}

# ---- 验证表结构 ----
verify_tables() {
    log_info "验证数据库表结构..."

    local tables
    tables=$($MYSQL_CMD "${MYSQL_DATABASE}" -e "SHOW TABLES;" 2>/dev/null | wc -l)

    if [ "$tables" -gt 1 ]; then
        local table_count=$((tables - 1))
        log_success "数据库 ${MYSQL_DATABASE} 中共有 ${table_count} 张表"

        log_info "核心表检查:"
        local core_tables=("users" "lsc_accounts" "lsc_transactions" "orders" "merchant_extensions" "b2b_orders" "release_config")
        for tbl in "${core_tables[@]}"; do
            if $MYSQL_CMD "${MYSQL_DATABASE}" -e "SHOW TABLES LIKE '${tbl}';" 2>/dev/null | grep -q "$tbl"; then
                log_success "  ${tbl} - 存在"
            else
                log_warn "  ${tbl} - 未找到 (可能是分库分表表)"
            fi
        done
    else
        log_warn "数据库 ${MYSQL_DATABASE} 中未找到表"
    fi
}

# ---- 主流程 ----
main() {
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo -e "${BOLD}${CYAN}  链盛通LSC系统 V6.2 数据库初始化${NC}"
    echo -e "${BOLD}${CYAN}============================================================${NC}"
    echo ""

    log_info "MySQL 连接: ${MYSQL_USER}@${MYSQL_HOST}:${MYSQL_PORT}"
    log_info "目标数据库: ${MYSQL_DATABASE}"
    log_info "SQL 目录:   ${SQL_DIR}"
    echo ""

    check_deps
    wait_mysql
    init_database

    echo ""
    log_info "开始执行 SQL 脚本..."
    echo ""

    local failed=()

    # Step 1: 执行主库建表脚本
    if ! exec_sql_file "$SQL_SCHEMA" "主库建表 (lsc_system_v6.2.sql)"; then
        failed+=("lsc_system_v6.2.sql")
    fi

    # Step 2: 执行分库分表脚本
    if ! exec_sql_file "$SQL_SHARDING" "分库分表 (lsc_sharding.sql)"; then
        failed+=("lsc_sharding.sql")
    fi

    # Step 3: 验证
    if [ ${#failed[@]} -eq 0 ]; then
        echo ""
        verify_tables
    fi

    echo ""
    if [ ${#failed[@]} -eq 0 ]; then
        log_success "数据库初始化完成!"
        log_info "分库分表物理库: lsc_db_0 ~ lsc_db_7"
        log_info "其他数据库:    nacos_config, xxl_job, seata"
    else
        log_error "以下SQL脚本执行失败: ${failed[*]}"
        exit 1
    fi
}

main "$@"