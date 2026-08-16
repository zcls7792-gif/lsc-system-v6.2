#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2 - 数据备份与恢复脚本
# 支持: MySQL逻辑备份、Redis RDB/AOF备份、配置文件备份
# 存储: 本地 + 阿里云OSS对象存储
# ============================================================

set -e

BACKUP_ID=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="${BACKUP_DIR:-/data/lsc/backup}"
OSS_BUCKET="${OSS_BACKUP_BUCKET:-lsc-backup-2026}"
OSS_ENDPOINT="${OBS_ENDPOINT:-https://oss-cn-hangzhou.aliyuncs.com}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-7}"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD}"
MYSQL_DATABASES="${MYSQL_DATABASES:-lsc_system,lsc_user,lsc_ledger,lsc_order}"

REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REDIS_PASSWORD="${REDIS_PASSWORD}"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }

usage() {
    echo "用法: $0 [COMMAND]"
    echo ""
    echo "命令:"
    echo "  mysql       备份MySQL数据库"
    echo "  redis       备份Redis数据"
    echo "  config      备份配置文件"
    echo "  all         全量备份"
    echo "  restore     恢复MySQL数据"
    echo "  list        列出备份"
    echo "  clean       清理过期备份"
    echo "  help        显示帮助"
    echo ""
    echo "示例:"
    echo "  $0 all                  # 执行全量备份"
    echo "  $0 restore /path/to/backup.sql  # 恢复MySQL"
    echo "  $0 clean                # 清理过期备份"
}

# ============================================================
# MySQL 备份
# ============================================================
backup_mysql() {
    log_info "开始MySQL备份..."
    mkdir -p "$BACKUP_DIR/mysql"

    DB_ARRAY=($(echo "$MYSQL_DATABASES" | tr ',' ' '))
    for db in "${DB_ARRAY[@]}"; do
        BACKUP_FILE="$BACKUP_DIR/mysql/${db}_${BACKUP_ID}.sql.gz"
        log_info "  备份数据库: $db -> $BACKUP_FILE"

        if [ -n "$MYSQL_PASSWORD" ]; then
            mysqldump -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
                --password="$MYSQL_PASSWORD" \
                --single-transaction \
                --routines \
                --triggers \
                --events \
                --hex-blob \
                --set-gtid-purged=OFF \
                --databases "$db" | gzip > "$BACKUP_FILE"
        else
            mysqldump -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" \
                --single-transaction --routines --triggers --events \
                --hex-blob --set-gtid-purged=OFF \
                --databases "$db" | gzip > "$BACKUP_FILE"
        fi

        if [ $? -eq 0 ]; then
            SIZE=$(du -h "$BACKUP_FILE" | awk '{print $1}')
            log_info "    $db 备份完成 ($SIZE)"
        else
            log_error "    $db 备份失败"
        fi
    done

    log_info "MySQL备份完成"
}

# ============================================================
# Redis 备份
# ============================================================
backup_redis() {
    log_info "开始Redis备份..."
    mkdir -p "$BACKUP_DIR/redis"

    REDIS_CLI="redis-cli -h $REDIS_HOST -p $REDIS_PORT"
    [ -n "$REDIS_PASSWORD" ] && REDIS_CLI="$REDIS_CLI -a $REDIS_PASSWORD"

    # 触发BGSAVE
    $REDIS_CLI BGSAVE 2>/dev/null
    sleep 5

    # 复制RDB文件
    RDB_FILE=$(find /var/lib/redis /usr/local/etc/redis -name "dump.rdb" 2>/dev/null | head -1)
    if [ -z "$RDB_FILE" ]; then
        RDB_FILE=$($REDIS_CLI CONFIG GET dir 2>/dev/null | tail -1)/dump.rdb
    fi

    if [ -f "$RDB_FILE" ]; then
        cp "$RDB_FILE" "$BACKUP_DIR/redis/dump_${BACKUP_ID}.rdb"
        SIZE=$(du -h "$BACKUP_DIR/redis/dump_${BACKUP_ID}.rdb" | awk '{print $1}')
        log_info "Redis RDB备份完成 ($SIZE)"
    else
        log_warn "Redis RDB文件未找到"
    fi

    # 导出关键Key
    KEYS_FILE="$BACKUP_DIR/redis/keys_${BACKUP_ID}.txt"
    $REDIS_CLI --no-auth-warning KEYS '*' > "$KEYS_FILE" 2>/dev/null
    KEY_COUNT=$(wc -l < "$KEYS_FILE" 2>/dev/null || echo 0)
    log_info "Redis Key导出完成 (${KEY_COUNT}个Key)"
}

# ============================================================
# 配置文件备份
# ============================================================
backup_config() {
    log_info "开始配置文件备份..."
    mkdir -p "$BACKUP_DIR/configs"

    CONFIG_ARCHIVE="$BACKUP_DIR/configs/configs_${BACKUP_ID}.tar.gz"
    tar czf "$CONFIG_ARCHIVE" \
        /etc/lsc/ \
        /etc/nginx/ \
        /workspace/config/ \
        /opt/lsc/configs/ \
        2>/dev/null || true

    SIZE=$(du -h "$CONFIG_ARCHIVE" | awk '{print $1}')
    log_info "配置文件备份完成 ($SIZE)"
}

# ============================================================
# 全量备份
# ============================================================
backup_all() {
    echo -e "\n${BOLD}开始全量备份 - $BACKUP_ID${NC}\n"

    backup_mysql
    backup_redis
    backup_config

    # 生成备份清单
    MANIFEST_FILE="$BACKUP_DIR/manifest_${BACKUP_ID}.txt"
    {
        echo "LSC系统全量备份清单"
        echo "备份时间: $(date)"
        echo "备份ID: $BACKUP_ID"
        echo ""
        echo "=== MySQL备份 ==="
        ls -lh "$BACKUP_DIR/mysql/"*"${BACKUP_ID}"* 2>/dev/null || echo "无"
        echo ""
        echo "=== Redis备份 ==="
        ls -lh "$BACKUP_DIR/redis/"*"${BACKUP_ID}"* 2>/dev/null || echo "无"
        echo ""
        echo "=== 配置备份 ==="
        ls -lh "$BACKUP_DIR/configs/"*"${BACKUP_ID}"* 2>/dev/null || echo "无"
    } > "$MANIFEST_FILE"

    # 上传到OSS
    if command -v ossutil &>/dev/null || command -v ossutil64 &>/dev/null; then
        log_info "上传备份到OSS..."
        OSS_UTIL=$(command -v ossutil64 || command -v ossutil)

        $OSS_UTIL cp "$BACKUP_DIR" "oss://${OSS_BUCKET}/backups/${BACKUP_ID}/" -r 2>/dev/null && \
            log_info "OSS上传完成" || log_warn "OSS上传失败"
    else
        log_warn "ossutil未安装,跳过OSS上传"
    fi

    echo -e "\n${GREEN}${BOLD}全量备份完成 - $BACKUP_ID${NC}"
    echo "备份目录: $BACKUP_DIR"
    echo "备份清单: $MANIFEST_FILE"
}

# ============================================================
# 恢复MySQL
# ============================================================
restore_mysql() {
    local BACKUP_FILE=$1
    if [ -z "$BACKUP_FILE" ]; then
        log_error "请指定备份文件路径"
        echo "用法: $0 restore /path/to/backup.sql.gz"
        exit 1
    fi

    if [ ! -f "$BACKUP_FILE" ]; then
        log_error "备份文件不存在: $BACKUP_FILE"
        exit 1
    fi

    log_warn "即将恢复MySQL数据, 此操作将清空现有数据!"
    read -p "确认恢复? (yes/no): " CONFIRM
    [ "$CONFIRM" != "yes" ] && { log_info "已取消"; exit 0; }

    log_info "开始恢复: $BACKUP_FILE"

    if [[ "$BACKUP_FILE" == *.gz ]]; then
        if [ -n "$MYSQL_PASSWORD" ]; then
            gunzip < "$BACKUP_FILE" | mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD"
        else
            gunzip < "$BACKUP_FILE" | mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER"
        fi
    else
        if [ -n "$MYSQL_PASSWORD" ]; then
            mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" < "$BACKUP_FILE"
        else
            mysql -h "$MYSQL_HOST" -P "$MYSQL_PORT" -u "$MYSQL_USER" < "$BACKUP_FILE"
        fi
    fi

    log_info "MySQL恢复完成"
}

# ============================================================
# 列出备份
# ============================================================
list_backups() {
    echo -e "${BOLD}LSC系统备份列表${NC}\n"
    echo "备份目录: $BACKUP_DIR"
    echo ""

    if [ -d "$BACKUP_DIR" ]; then
        echo "--- 最近的备份 ---"
        find "$BACKUP_DIR" -name "manifest_*.txt" -exec basename {} \; 2>/dev/null | sort | tail -10
        echo ""

        echo "--- 数据库备份 ---"
        ls -lh "$BACKUP_DIR"/mysql/*.sql.gz 2>/dev/null | tail -5 || echo "  无备份"
        echo ""

        echo "--- 总备份大小 ---"
        du -sh "$BACKUP_DIR" 2>/dev/null || echo "  0"
    else
        echo "备份目录不存在"
    fi
}

# ============================================================
# 清理过期备份
# ============================================================
clean_backups() {
    log_info "清理${RETENTION_DAYS}天前的备份..."

    DELETED=0
    # 清理过期文件
    find "$BACKUP_DIR" -type f -mtime +${RETENTION_DAYS} -delete 2>/dev/null && \
        DELETED=$(find "$BACKUP_DIR" -type f -mtime +${RETENTION_DAYS} 2>/dev/null | wc -l)

    log_info "已清理 $DELETED 个过期文件"

    # 清理空目录
    find "$BACKUP_DIR" -type d -empty -delete 2>/dev/null
}

# ============================================================
# 主入口
# ============================================================
ACTION=${1:-all}

case "$ACTION" in
    mysql)    backup_mysql ;;
    redis)    backup_redis ;;
    config)   backup_config ;;
    all)      backup_all ;;
    restore)  restore_mysql "$2" ;;
    list)     list_backups ;;
    clean)    clean_backups ;;
    help|--help|-h) usage ;;
    *)        usage ;;
esac