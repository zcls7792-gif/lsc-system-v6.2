#!/usr/bin/env bash
# =============================================================================
# 链盛通 LSC 平台 — 三项功能回滚脚本
# 回滚范围：订单退款功能 + 核销唯一索引 + Postman 集合
# 用法：  bash rollback-features.sh [--dry-run]
# =============================================================================
set -euo pipefail

# ---------- 配置 ----------
PROJECT_DIR="/workspace/lsc-release-service"
BACKEND_DIR="${PROJECT_DIR}/lsc-backend"
DOCS_DIR="${PROJECT_DIR}/docs"
POSTMAN_FILE="${DOCS_DIR}/链盛通LSC平台-Postman集合.json"
ORDER_SERVICE="${BACKEND_DIR}/src/main/java/com/lianshengtong/lsc/service/OrderService.java"
ORDER_CONTROLLER="${BACKEND_DIR}/src/main/java/com/lianshengtong/lsc/controller/OrderController.java"
SCHEMA_SQL="${BACKEND_DIR}/src/main/resources/schema.sql"

DB_HOST="127.0.0.1"
DB_PORT="3306"
DB_NAME="lsc"
DB_USER="lsc"
DB_PASS="lsc@2026"

# 颜色
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'
ok()   { echo -e "${GREEN}[OK]${NC} $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $*"; }
err()  { echo -e "${RED}[FAIL]${NC} $*"; }

DRY_RUN=false
[[ "${1:-}" == "--dry-run" ]] && DRY_RUN=true

# ---------- 工具函数 ----------
run_sql() {
    # 执行 SQL，使用 </dev/null 避免 stdin 被消耗
    local sql="$1"
    if $DRY_RUN; then
        echo "  [DRY-RUN] mysql -e \"${sql}\""
    else
        mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" -e "${sql}" </dev/null
    fi
}

backup_file() {
    local f="$1"
    [[ -f "$f" ]] || return 0
    local bak="${f}.$(date +%Y%m%d%H%M%S).bak"
    cp "$f" "$bak"
    ok "已备份: $bak"
}

# ---------- 主流程 ----------
echo "============================================================"
echo " 链盛通 LSC 平台回滚脚本"
echo " 范围: 退款功能 + 核销唯一索引 + Postman 集合"
$DRY_RUN && echo " 模式: DRY-RUN (不实际执行)"
echo "============================================================"

# ===== 第 1 步：回滚数据库索引 =====
echo ""
echo "【1/4】回滚 nh_record 唯一索引 → 普通索引"
echo "  当前索引检查..."
CURRENT_IDX=$(mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USER}" -p"${DB_PASS}" "${DB_NAME}" -N -e \
    "SHOW INDEX FROM nh_record WHERE Key_name='uk_nh_merchant_date';" 2>/dev/null | head -1 || true)

if [[ -n "$CURRENT_IDX" ]]; then
    echo "  发现唯一索引 uk_nh_merchant_date，准备回滚..."
    backup_file /dev/null  # no-op for clarity
    # 删除唯一索引
    run_sql "DROP INDEX uk_nh_merchant_date ON nh_record;"
    ok "已删除唯一索引 uk_nh_merchant_date"
    # 重建普通索引
    run_sql "CREATE INDEX idx_nh_merchant_date ON nh_record(merchant_id, nh_date);"
    ok "已重建普通索引 idx_nh_merchant_date"
else
    warn "未找到唯一索引 uk_nh_merchant_date，跳过"
fi

# ===== 第 2 步：回滚 schema.sql =====
echo ""
echo "【2/4】回滚 schema.sql 索引定义"
if grep -q "CREATE UNIQUE INDEX uk_nh_merchant_date" "$SCHEMA_SQL"; then
    backup_file "$SCHEMA_SQL"
    if $DRY_RUN; then
        echo "  [DRY-RUN] 将 UNIQUE INDEX uk_nh_merchant_date 改回 INDEX idx_nh_merchant_date"
    else
        sed -i 's/CREATE UNIQUE INDEX uk_nh_merchant_date ON nh_record(CREATE INDEX idx_nh_merchant_date ON nh_record(/' "$SCHEMA_SQL"
        # sed 替换更稳妥的写法
        sed -i 's/CREATE UNIQUE INDEX uk_nh_merchant_date/CREATE INDEX idx_nh_merchant_date/' "$SCHEMA_SQL"
        ok "schema.sql 索引定义已回滚"
    fi
else
    warn "schema.sql 中未找到唯一索引定义，跳过"
fi

# ===== 第 3 步：回滚代码（退款功能） =====
echo ""
echo "【3/4】回滚代码：删除退款方法与接口"

# 3a. OrderService.java — 删除 refund() 方法
if grep -q "public Map<String, Object> refund(Long userId" "$ORDER_SERVICE"; then
    backup_file "$ORDER_SERVICE"
    if $DRY_RUN; then
        echo "  [DRY-RUN] 从 OrderService.java 删除 refund() 方法"
    else
        # 使用 Python 精确删除 refund 方法块（从注释行到 return result; } 结束）
        python3 << 'PYEOF'
import re
f = "/workspace/lsc-release-service/lsc-backend/src/main/java/com/lianshengtong/lsc/service/OrderService.java"
with open(f) as fh:
    src = fh.read()
# 删除从 "/**\n     * 订单退款" 到 "return result;\n    }\n\n" 的整块
pattern = r'    /\*\*\n     \* 订单退款.*?return result;\n    \}\n\n'
src = re.sub(pattern, '', src, count=1, flags=re.DOTALL)
with open(f, "w") as fh:
    fh.write(src)
print("  refund() 方法已删除")
PYEOF
        ok "OrderService.java 已回滚"
    fi
else
    warn "OrderService.java 中未找到 refund() 方法，跳过"
fi

# 3b. OrderController.java — 删除 refund 接口
if grep -q "@PostMapping(\"/{orderNo}/refund\")" "$ORDER_CONTROLLER"; then
    backup_file "$ORDER_CONTROLLER"
    if $DRY_RUN; then
        echo "  [DRY-RUN] 从 OrderController.java 删除 refund 接口"
    else
        python3 << 'PYEOF'
f = "/workspace/lsc-release-service/lsc-backend/src/main/java/com/lianshengtong/lsc/controller/OrderController.java"
with open(f) as fh:
    src = fh.read()
# 删除 refund 接口方法块
old = '''    @PostMapping("/{orderNo}/refund")
    public R<Map<String, Object>> refund(@PathVariable String orderNo) {
        return R.ok(orderService.refund(UserContext.getUserId(), orderNo));
    }
'''
src = src.replace(old, "")
with open(f, "w") as fh:
    fh.write(src)
print("  refund 接口已删除")
PYEOF
        ok "OrderController.java 已回滚"
    fi
else
    warn "OrderController.java 中未找到 refund 接口，跳过"
fi

# ===== 第 4 步：清理 Postman 集合（可选） =====
echo ""
echo "【4/4】清理 Postman 集合文件"
if [[ -f "$POSTMAN_FILE" ]]; then
    if $DRY_RUN; then
        echo "  [DRY-RUN] 将删除 $POSTMAN_FILE"
    else
        rm -f "$POSTMAN_FILE"
        ok "已删除 Postman 集合文件"
    fi
else
    warn "Postman 集合文件不存在，跳过"
fi

# ===== 重新构建 =====
echo ""
echo "============================================================"
if $DRY_RUN; then
    echo " DRY-RUN 完成，未执行实际操作"
    echo " 执行正式回滚请运行: bash $0"
else
    echo " 代码回滚完成，开始重新构建..."
    cd "$BACKEND_DIR"
    mvn clean package -DskipTests -B 2>&1 | tail -3
    ok "构建完成"

    echo ""
    echo " 重启后端服务..."
    pkill -f "lsc-backend-1.0.0.jar" 2>/dev/null || true
    sleep 2
    nohup env SPRING_PROFILES_ACTIVE=prod \
        MYSQL_HOST="${DB_HOST}" MYSQL_PORT="${DB_PORT}" \
        MYSQL_DATABASE="${DB_NAME}" MYSQL_USER="${DB_USER}" \
        MYSQL_PASSWORD="${DB_PASS}" \
        java -jar -Xms256m -Xmx512m target/lsc-backend-1.0.0.jar \
        > /tmp/lsc-backend-rollback.log 2>&1 &
    sleep 15

    # 健康检查
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/v1/actuator/health || echo "000")
    if [[ "$HTTP_CODE" == "200" ]]; then
        ok "服务已恢复，健康检查通过 (HTTP 200)"
    else
        err "服务启动异常，请检查 /tmp/lsc-backend-rollback.log"
        exit 1
    fi
fi

echo ""
echo "============================================================"
echo " 回滚完成"
echo " 已恢复：数据库普通索引 + 代码无退款功能"
echo " 如需恢复功能，重新执行对应部署即可"
echo "============================================================"
