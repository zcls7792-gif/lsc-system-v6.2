#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2-AI - 密钥轮换脚本
# ============================================================
# 功能:
#   1. 生成新的强随机密钥
#   2. 写入 docker/.env (不覆盖已存在的密钥，除非 --force)
#   3. 输出 K8s Secret 创建命令
#
# 用法:
#   ./scripts/rotate-secrets.sh              # 生成新 .env
#   ./scripts/rotate-secrets.sh --force      # 覆盖已有 .env
#   ./scripts/rotate-secrets.sh --k8s        # 仅输出 K8s secret 命令
#
# 前置: openssl, kubectl (可选)
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/docker/.env"
TEMPLATE="$PROJECT_ROOT/docker/.env.example"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }

FORCE=false
K8S_ONLY=false

for arg in "$@"; do
    case "$arg" in
        --force) FORCE=true ;;
        --k8s)   K8S_ONLY=true ;;
        --help|-h)
            sed -n '3,20p' "$0"; exit 0 ;;
        *) log_error "未知参数: $arg"; exit 1 ;;
    esac
done

# ---- 生成强随机密钥 ----
gen_pwd()  { openssl rand -hex 24; }
gen_token(){ openssl rand -hex 32; }
gen_jwt()  { openssl rand -base64 48 | tr -d '\n'; }

# ---- 生成全部密钥 ----
generate_all() {
    cat > "$ENV_FILE" <<EOF
# ============================================================
# 链盛通LSC系统 V6.2-AI - 自动生成的环境变量
# 生成时间: $(date '+%Y-%m-%d %H:%M:%S')
# ⚠️ 本文件包含敏感信息，已在 .gitignore 中，禁止提交到仓库
# ============================================================

# ---- MySQL ----
MYSQL_ROOT_PASSWORD=$(gen_pwd)
MYSQL_DATABASE=lsc_system
MYSQL_APP_USER=lsc_app
MYSQL_APP_PASSWORD=$(gen_pwd)

# ---- Redis 集群 ----
REDIS_PASSWORD=$(gen_pwd)

# ---- RabbitMQ ----
RABBITMQ_DEFAULT_USER=lsc_admin
RABBITMQ_DEFAULT_PASS=$(gen_pwd)

# ---- Nacos ----
NACOS_AUTH_IDENTITY_KEY=lsc_nacos
NACOS_AUTH_IDENTITY_VALUE=$(gen_pwd)

# ---- Grafana ----
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=$(gen_pwd)

# ---- XXL-JOB ----
XXL_JOB_ACCESS_TOKEN=$(gen_token)

# ---- JWT ----
JWT_ACCESS_SECRET=$(gen_jwt)
JWT_REFRESH_SECRET=$(gen_jwt)

# ---- 应用角色初始密码 ----
ADMIN_PASSWORD=$(gen_pwd)
AUDITOR_PASSWORD=$(gen_pwd)
OPERATOR_PASSWORD=$(gen_pwd)

# ---- 区块链节点 (按需填入) ----
CHAIN_RPC_URL=http://chain-rpc:8545
CHAIN_CONTRACT=
CHAIN_PK=

# ---- AI 服务 (按需填入) ----
AI_API_KEY=
AI_ENDPOINT=

# ---- 对象存储 (按需填入) ----
OSS_ACCESS_KEY_ID=
OSS_ACCESS_KEY_SECRET=
OSS_BUCKET=
OSS_ENDPOINT=

# ---- 服务 profile ----
SPRING_PROFILES_ACTIVE=prod
EOF
    chmod 600 "$ENV_FILE"
}

# ---- 主流程 ----
if [ "$K8S_ONLY" = true ]; then
    if [ ! -f "$ENV_FILE" ]; then
        log_error ".env 文件不存在，请先运行: $0"
        exit 1
    fi
    log_info "输出 K8s Secret 创建命令（请手动执行）:"
    echo ""
    echo "kubectl create secret generic lsc-secrets \\"
    echo "  --from-env-file=$ENV_FILE \\"
    echo "  -n lsc-system"
    echo ""
    echo "# 或使用 dry-run 查看内容:"
    echo "kubectl create secret generic lsc-secrets \\"
    echo "  --from-env-file=$ENV_FILE \\"
    echo "  -n lsc-system --dry-run=client -o yaml"
    exit 0
fi

if [ -f "$ENV_FILE" ] && [ "$FORCE" != true ]; then
    log_warn "已存在 $ENV_FILE"
    log_warn "如需重新生成，请使用: $0 --force"
    log_info "当前 .env 生成时间: $(stat -c %y "$ENV_FILE" 2>/dev/null | cut -d. -f1)"
    exit 0
fi

log_info "生成新的强随机密钥到 $ENV_FILE ..."
generate_all
log_success "密钥已生成: $ENV_FILE"
log_warn "请妥善保管此文件，切勿提交到 Git 仓库"
log_info ""
log_info "下一步:"
log_info "  1. 按需填入 CHAIN/AI/OSS 等业务密钥"
log_info "  2. 启动服务: docker compose -f docker/docker-compose.yml up -d"
log_info "  3. K8s 部署: $0 --k8s"
