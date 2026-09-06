#!/usr/bin/env bash
# ============================================================================
# 链盛通LSC平台 V6.2-AI对齐变更 - 应用回滚脚本
# ============================================================================
# 用途：将应用代码从 V6.2-AI最终版 回滚至 V6.2.0-gray-approval 基线
#
# 使用方式：
#   ./rollback_v6.2-ai.sh --dry-run          # 预览回滚操作，不实际执行
#   ./rollback_v6.2-ai.sh --backup-only      # 仅备份当前文件
#   ./rollback_v6.2-ai.sh --db-only          # 仅回滚数据库
#   ./rollback_v6.2-ai.sh --code-only        # 仅回滚应用代码
#   ./rollback_v6.2-ai.sh --all              # 回滚数据库 + 应用代码（默认）
#   ./rollback_v6.2-ai.sh --restore <path>   # 从指定备份目录恢复
#
# 注意事项：
#   1. 执行前务必备份！脚本会自动备份到 rollback_backups/ 目录
#   2. 数据库回滚需要 MySQL 客户端和连接权限
#   3. 应用代码回滚基于 Git，需要确认回滚基线提交
# ============================================================================

set -euo pipefail

# ======================== 配置 ========================
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_ROOT="$PROJECT_ROOT/rollback_backups/$(date +%Y%m%d_%H%M%S)"
LOG_FILE="$SCRIPT_DIR/rollback_$(date +%Y%m%d_%H%M%S).log"

# 数据库配置（从应用配置读取或手动指定）
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-lsc_release}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"

# 回滚基线 Git 提交（需根据实际情况指定）
ROLLBACK_COMMIT="${ROLLBACK_COMMIT:-}"

# 本次修改的文件清单（相对项目根目录）
MODIFIED_FILES=(
    "lsc-backend/src/main/java/com/lianshengtong/lsc/LscApplication.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/entity/ReleaseConfig.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/entity/SysUser.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/entity/Merchant.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/entity/Orders.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/entity/NhRecord.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/entity/B2bOrder.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/entity/Product.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/service/ReleaseService.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/service/LscAccountService.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/service/NhService.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/service/B2bService.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/service/OrderService.java"
    "lsc-backend/src/main/java/com/lianshengtong/lsc/service/ProductService.java"
    "lsc-backend/src/main/resources/schema.sql"
    "lsc-backend/src/main/resources/data.sql"
)

# 数据库回滚SQL脚本
DB_ROLLBACK_SQL="$SCRIPT_DIR/rollback_v6.2-ai.sql"

# ======================== 工具函数 ========================
log() {
    local level="$1"; shift
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*"
    echo "$msg" | tee -a "$LOG_FILE"
}

log_info()  { log "INFO"  "$@"; }
log_warn()  { log "WARN"  "$@"; }
log_error() { log "ERROR" "$@"; }
log_step()  { log "STEP"  "==== $* ===="; }

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_banner() {
    echo -e "${BLUE}"
    echo "============================================================"
    echo "  链盛通LSC平台 V6.2-AI对齐变更 - 回滚脚本"
    echo "============================================================"
    echo -e "${NC}"
}

# ======================== 模式解析 ========================
DRY_RUN=false
BACKUP_ONLY=false
DB_ONLY=false
CODE_ONLY=false
RESTORE_PATH=""
ALL=true

parse_args() {
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --dry-run)      DRY_RUN=true; shift ;;
            --backup-only)  BACKUP_ONLY=true; ALL=false; shift ;;
            --db-only)      DB_ONLY=true; ALL=false; shift ;;
            --code-only)    CODE_ONLY=true; ALL=false; shift ;;
            --all)          ALL=true; shift ;;
            --restore)      RESTORE_PATH="$2"; shift 2 ;;
            --help)
                sed -n '2,30p' "$0"
                exit 0
                ;;
            *)
                log_error "未知参数: $1"
                exit 1
                ;;
        esac
    done
}

# ======================== 备份函数 ========================
backup_files() {
    log_step "备份当前修改的文件到: $BACKUP_ROOT"
    mkdir -p "$BACKUP_ROOT"

    local count=0
    for f in "${MODIFIED_FILES[@]}"; do
        local src="$PROJECT_ROOT/$f"
        if [[ -f "$src" ]]; then
            local dir
            dir="$(dirname "$BACKUP_ROOT/$f")"
            mkdir -p "$dir"
            cp "$src" "$BACKUP_ROOT/$f"
            log_info "  已备份: $f"
            count=$((count + 1))
        else
            log_warn "  文件不存在，跳过: $f"
        fi
    done
    log_info "共备份 $count 个文件"

    # 保存备份清单
    echo "$BACKUP_ROOT" > "$PROJECT_ROOT/rollback_backups/LATEST"
    log_info "备份路径已记录到: rollback_backups/LATEST"
}

# ======================== 数据库回滚 ========================
rollback_database() {
    log_step "执行数据库回滚"

    if [[ ! -f "$DB_ROLLBACK_SQL" ]]; then
        log_error "数据库回滚SQL脚本不存在: $DB_ROLLBACK_SQL"
        return 1
    fi

    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] 将执行: mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p*** $DB_NAME < $DB_ROLLBACK_SQL"
        log_info "[DRY-RUN] 回滚SQL内容预览（前20行）:"
        head -20 "$DB_ROLLBACK_SQL" | sed 's/^/  /'
        return 0
    fi

    # 检查 MySQL 客户端
    if ! command -v mysql &>/dev/null; then
        log_error "未找到 mysql 客户端，请先安装或手动执行SQL脚本"
        log_info "手动执行命令: mysql -h$DB_HOST -P$DB_PORT -u$DB_USER -p $DB_NAME < $DB_ROLLBACK_SQL"
        return 1
    fi

    # 构建连接参数
    local mysql_args=(-h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER")
    if [[ -n "$DB_PASS" ]]; then
        mysql_args+=(-p"$DB_PASS")
    fi

    log_info "正在连接数据库 $DB_HOST:$DB_PORT/$DB_NAME ..."

    # 执行回滚
    if mysql "${mysql_args[@]}" "$DB_NAME" < "$DB_ROLLBACK_SQL" 2>>"$LOG_FILE"; then
        log_info "数据库回滚执行成功"
        # 验证回滚结果
        log_info "验证回滚结果..."
        local new_tables
        new_tables=$(mysql "${mysql_args[@]}" -N -e "
            SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES
            WHERE TABLE_SCHEMA = '$DB_NAME'
            AND TABLE_NAME IN ('available_lsc_details','merchant_violations','risk_logs',
              'blockchain_records','daily_snapshot_records','admin_audit_logs','tx_exception_log');" 2>/dev/null)
        if [[ "$new_tables" == "0" ]]; then
            log_info "验证通过：7张新表已全部删除"
        else
            log_warn "验证异常：仍有 $new_tables 张新表存在，请手动检查"
        fi
    else
        log_error "数据库回滚执行失败，请查看日志: $LOG_FILE"
        return 1
    fi
}

# ======================== 应用代码回滚 ========================
rollback_code() {
    log_step "执行应用代码回滚"

    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] 将回滚以下 ${#MODIFIED_FILES[@]} 个文件:"
        for f in "${MODIFIED_FILES[@]}"; do
            echo "  - $f"
        done
        log_info "[DRY-RUN] 回滚方式: 从备份目录恢复"
        return 0
    fi

    # 优先从备份恢复
    if [[ -n "$RESTORE_PATH" ]]; then
        log_info "从指定备份目录恢复: $RESTORE_PATH"
        restore_from_backup "$RESTORE_PATH"
        return $?
    fi

    # 尝试从最近一次备份恢复
    local latest_backup
    if [[ -f "$PROJECT_ROOT/rollback_backups/LATEST" ]]; then
        latest_backup="$(cat "$PROJECT_ROOT/rollback_backups/LATEST")"
        log_info "从最近备份恢复: $latest_backup"
        restore_from_backup "$latest_backup"
        return $?
    fi

    # 尝试 Git 回滚
    if [[ -n "$ROLLBACK_COMMIT" ]]; then
        log_info "使用 Git 回滚到提交: $ROLLBACK_COMMIT"
        rollback_with_git "$ROLLBACK_COMMIT"
        return $?
    fi

    # 无可用回滚方式
    log_error "未找到可用的回滚基线"
    log_info "请选择以下方式之一："
    log_info "  1. 先执行 --backup-only 备份，再执行回滚"
    log_info "  2. 指定 ROLLBACK_COMMIT 环境变量为基线提交哈希"
    log_info "  3. 使用 --restore <backup_path> 从指定备份恢复"
    return 1
}

restore_from_backup() {
    local backup_dir="$1"

    if [[ ! -d "$backup_dir" ]]; then
        log_error "备份目录不存在: $backup_dir"
        return 1
    fi

    local count=0
    for f in "${MODIFIED_FILES[@]}"; do
        local backup_file="$backup_dir/$f"
        local target_file="$PROJECT_ROOT/$f"
        if [[ -f "$backup_file" ]]; then
            cp "$backup_file" "$target_file"
            log_info "  已恢复: $f"
            count=$((count + 1))
        else
            log_warn "  备份中不存在，跳过: $f"
        fi
    done
    log_info "共恢复 $count 个文件"
}

rollback_with_git() {
    local commit="$1"

    if ! git -C "$PROJECT_ROOT" rev-parse "$commit" &>/dev/null; then
        log_error "Git 提交不存在: $commit"
        return 1
    fi

    log_info "使用 git checkout 恢复文件到提交 $commit"
    for f in "${MODIFIED_FILES[@]}"; do
        if git -C "$PROJECT_ROOT" show "$commit:$f" &>/dev/null; then
            git -C "$PROJECT_ROOT" checkout "$commit" -- "$f"
            log_info "  已恢复: $f"
        else
            log_warn "  提交中不存在，跳过: $f"
        fi
    done
}

# ======================== 编译验证 ========================
verify_build() {
    log_step "验证应用编译"
    if [[ "$DRY_RUN" == true ]]; then
        log_info "[DRY-RUN] 将执行: cd $PROJECT_ROOT/lsc-backend && mvn compile -q"
        return 0
    fi

    log_info "执行 Maven 编译验证..."
    if (cd "$PROJECT_ROOT/lsc-backend" && mvn compile -q 2>>"$LOG_FILE"); then
        log_info "编译验证通过"
    else
        log_error "编译验证失败，请查看日志: $LOG_FILE"
        return 1
    fi
}

# ======================== 主流程 ========================
main() {
    print_banner
    parse_args "$@"

    log_info "项目根目录: $PROJECT_ROOT"
    log_info "日志文件: $LOG_FILE"

    if [[ "$BACKUP_ONLY" == true ]]; then
        backup_files
        log_info "备份完成，退出"
        exit 0
    fi

    # 执行备份（除非是从备份恢复）
    if [[ -z "$RESTORE_PATH" ]]; then
        backup_files
    fi

    # 执行回滚
    if [[ "$ALL" == true || "$DB_ONLY" == true ]]; then
        rollback_database
    fi

    if [[ "$ALL" == true || "$CODE_ONLY" == true ]]; then
        rollback_code
    fi

    # 编译验证（仅代码回滚时）
    if [[ "$ALL" == true || "$CODE_ONLY" == true ]]; then
        verify_build
    fi

    log_step "回滚流程结束"
    log_info "日志文件: $LOG_FILE"
    log_info "备份目录: $BACKUP_ROOT"
    echo ""
    echo -e "${GREEN}回滚脚本执行完成${NC}"
}

main "$@"
