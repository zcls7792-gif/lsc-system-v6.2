#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2 - 云服务器一键部署脚本
# 支持: 阿里云ECS / 腾讯云CVM / 华为云ECS / AWS EC2
# 用法: ./deploy-cloud.sh [OPTIONS]
#   --skip-build    跳过构建, 直接部署已有镜像
#   --skip-install  跳过环境安装
#   --rollback      回滚到上一版本
#   --status        查看服务状态
# ============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m'

LOG_FILE="/data/lsc/logs/deploy-$(date +%Y%m%d_%H%M%S).log"
DEPLOY_DIR="/opt/lsc"
CONFIG_DIR="/etc/lsc"
BACKUP_DIR="/data/lsc/backup"

log_info()    { echo -e "${GREEN}[INFO]${NC} $*" | tee -a "$LOG_FILE"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $*" | tee -a "$LOG_FILE"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*" | tee -a "$LOG_FILE"; }
log_step()    { echo -e "\n${BOLD}${CYAN}==== $* ====${NC}" | tee -a "$LOG_FILE"; }

# ---- 参数解析 ----
SKIP_BUILD=false
SKIP_INSTALL=false
ROLLBACK_MODE=false
STATUS_MODE=false

for arg in "$@"; do
    case "$arg" in
        --skip-build)   SKIP_BUILD=true ;;
        --skip-install) SKIP_INSTALL=true ;;
        --rollback)     ROLLBACK_MODE=true ;;
        --status)       STATUS_MODE=true ;;
        --help)
            echo "用法: $0 [--skip-build] [--skip-install] [--rollback] [--status]"
            exit 0
    esac
done

# ============================================================
# 状态查看模式
# ============================================================
if [ "$STATUS_MODE" = true ]; then
    echo -e "${BOLD}${CYAN}链盛通LSC系统 - 服务状态${NC}\n"

    echo "--- Docker 容器状态 ---"
    docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || echo "Docker未运行"

    echo -e "\n--- 系统资源使用 ---"
    free -h | grep Mem
    echo "磁盘: $(df -h / | tail -1 | awk '{print $5}')"

    echo -e "\n--- Nginx状态 ---"
    nginx -t 2>/dev/null && echo "Nginx配置正常" || echo "Nginx配置异常"

    echo -e "\n--- 端口监听 ---"
    ss -tlnp | grep -E '(80|443|8000|8101|8200)' | head -10

    exit 0
fi

# ============================================================
# Step 0: 环境检查
# ============================================================
log_step "Step 0: 环境检查"

# 加载环境变量
if [ -f "$CONFIG_DIR/.env.production" ]; then
    set -a
    source "$CONFIG_DIR/.env.production"
    set +a
    log_info "已加载环境变量配置"
fi

# 检查基础命令
MISSING_COMMANDS=()
for cmd in docker nginx systemctl curl wget openssl; do
    if ! command -v "$cmd" &>/dev/null; then
        MISSING_COMMANDS+=("$cmd")
    fi
done

if [ ${#MISSING_COMMANDS[@]} -gt 0 ] && [ "$SKIP_INSTALL" = false ]; then
    log_warn "缺少基础命令: ${MISSING_COMMANDS[*]}"
    log_info "将自动安装缺失的组件"
fi

# ============================================================
# Step 1: 安装运行环境
# ============================================================
if [ "$SKIP_INSTALL" = false ]; then
    log_step "Step 1: 安装运行环境"

    # 安装基础包
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS_TYPE="$ID"
    fi

    if [ "$OS_TYPE" = "ubuntu" ] || [ "$OS_TYPE" = "debian" ]; then
        apt-get update -qq
        apt-get install -y -qq \
            apt-transport-https ca-certificates curl gnupg lsb-release
        log_info "Ubuntu基础包安装完成"
    elif [ "$OS_TYPE" = "centos" ] || [ "$OS_TYPE" = "rhel" ] || [ "$OS_TYPE" = "almalinux" ] || [ "$OS_TYPE" = "rocky" ] || [ "$OS_TYPE" = "alibaba" ]; then
        yum install -y -qq curl wget ca-certificates
        log_info "CentOS/RHEL基础包安装完成"
    fi

    # 安装 Docker
    if ! command -v docker &>/dev/null; then
        log_info "安装 Docker..."
        if [ "$OS_TYPE" = "ubuntu" ] || [ "$OS_TYPE" = "debian" ]; then
            curl -fsSL https://get.docker.com -o get-docker.sh
            sh get-docker.sh
        elif [ "$OS_TYPE" = "centos" ] || [ "$OS_TYPE" = "rhel" ] || [ "$OS_TYPE" = "almalinux" ] || [ "$OS_TYPE" = "rocky" ] || [ "$OS_TYPE" = "alibaba" ]; then
            yum install -y -qq docker
            systemctl enable docker
            systemctl start docker
        fi
        log_success "Docker安装完成: $(docker --version)"
    else
        log_info "Docker已安装: $(docker --version)"
    fi

    # 安装 Nginx
    if ! command -v nginx &>/dev/null; then
        log_info "安装 Nginx..."
        if [ "$OS_TYPE" = "ubuntu" ] || [ "$OS_TYPE" = "debian" ]; then
            apt-get install -y -qq nginx
        elif [ "$OS_TYPE" = "centos" ] || [ "$OS_TYPE" = "rhel" ] || [ "$OS_TYPE" = "almalinux" ] || [ "$OS_TYPE" = "rocky" ] || [ "$OS_TYPE" = "alibaba" ]; then
            yum install -y -qq nginx
        fi
        systemctl enable nginx
        log_success "Nginx安装完成: $(nginx -v)"
    else
        log_info "Nginx已安装: $(nginx -v)"
    fi

    # 安装 Docker Compose
    if ! command -v docker-compose &>/dev/null && ! docker compose version &>/dev/null; then
        log_info "安装 Docker Compose..."
        COMPOSE_VERSION="2.24.0"
        if [ "$(uname -m)" = "x86_64" ]; then
            curl -fsSL "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/docker-compose-linux-x86_64" \
                -o /usr/local/bin/docker-compose
        else
            curl -fsSL "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/docker-compose-linux-aarch64" \
                -o /usr/local/bin/docker-compose
        fi
        chmod +x /usr/local/bin/docker-compose
        log_success "Docker Compose安装完成: $(docker-compose --version)"
    fi
fi

# ============================================================
# Step 2: 准备部署目录
# ============================================================
log_step "Step 2: 准备部署目录"

mkdir -p "$DEPLOY_DIR"/{app,configs,data,logs}
mkdir -p "$BACKUP_DIR"
mkdir -p "$CONFIG_DIR"/{ssl,conf.d}

# ============================================================
# Step 3: Nginx 配置部署
# ============================================================
log_step "Step 3: Nginx配置部署"

# 备份旧配置
NGINX_CONF="/etc/nginx/nginx.conf"
if [ -f "$NGINX_CONF" ]; then
    cp "$NGINX_CONF" "$BACKUP_DIR/nginx.conf.$(date +%s).bak"
fi

# 部署新配置
if [ -d "/workspace/cloud/nginx" ]; then
    cp /workspace/cloud/nginx/nginx.conf "$NGINX_CONF"
    cp /workspace/cloud/nginx/*.conf /etc/nginx/conf.d/
    log_info "Nginx配置已部署"
fi

# 创建目录
mkdir -p /usr/share/nginx/html/{admin,merchant,mobile}

# SSL证书目录
mkdir -p /etc/nginx/ssl

# 测试Nginx配置
nginx -t 2>/dev/null && log_success "Nginx配置测试通过" || log_error "Nginx配置错误,请检查日志"

# ============================================================
# Step 4: SSL证书配置
# ============================================================
log_step "Step 4: SSL证书检查"

DOMAINS=("api.lianshengtong.com" "admin.lianshengtong.com" "merchant.lianshengtong.com" "m.lianshengtong.com")
CERT_EXISTS=true

for domain in "${DOMAINS[@]}"; do
    CERT_FILE="/etc/nginx/ssl/${domain}.pem"
    KEY_FILE="/etc/nginx/ssl/${domain}.key"
    if [ ! -f "$CERT_FILE" ] || [ ! -f "$KEY_FILE" ]; then
        log_warn "SSL证书缺失: $domain"
        CERT_EXISTS=false
    fi
done

if [ "$CERT_EXISTS" = false ]; then
    log_warn "部分域名SSL证书缺失, HTTPS站点将无法访问"
    log_info "可使用Let's Encrypt自动获取:"
    log_info "  sudo certbot --nginx -d api.lianshengtong.com"
    log_info "  sudo certbot --nginx -d admin.lianshengtong.com"
else
    log_success "SSL证书检查通过"
fi

# ============================================================
# Step 5: 部署应用服务
# ============================================================
log_step "Step 5: 部署应用服务"

# 检查Docker镜像
SERVICES=(
    "lsc-gateway"
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
)

MISSING_IMAGES=()
for svc in "${SERVICES[@]}"; do
    if ! docker image inspect "lsc/${svc}:6.2.0" &>/dev/null; then
        MISSING_IMAGES+=("$svc")
    fi
done

if [ ${#MISSING_IMAGES[@]} -gt 0 ]; then
    log_warn "以下服务镜像缺失: ${MISSING_IMAGES[*]}"
    if [ "$SKIP_BUILD" = false ] && [ -f "/workspace/docker/build-all.sh" ]; then
        log_info "尝试构建缺失的镜像..."
        /workspace/docker/build-all.sh --services="${MISSING_IMAGES[*]}" 2>&1 | tail -5
    else
        log_error "请先上传或构建Docker镜像"
        exit 1
    fi
fi

# 停止旧服务
log_info "停止旧服务..."
docker-compose -f /workspace/docker/docker-compose-app.yml down 2>/dev/null || true

# 部署新服务
log_info "启动应用服务..."
docker-compose -f /workspace/docker/docker-compose-app.yml up -d --remove-orphans 2>&1 | tee -a "$LOG_FILE"

# 等待服务就绪
log_info "等待服务启动(约60秒)..."
sleep 30

# 健康检查
HEALTHY=0
for i in $(seq 1 10); do
    if curl -sf http://localhost:8000/actuator/health &>/dev/null; then
        HEALTHY=1
        log_success "API网关启动成功!"
        break
    fi
    log_info "等待服务就绪... ($i/10)"
    sleep 3
done

if [ "$HEALTHY" = 0 ]; then
    log_error "API网关启动超时,请检查日志"
    docker logs lsc-gateway --tail 50 2>/dev/null | tail -10
fi

# ============================================================
# Step 6: 启动Nginx
# ============================================================
log_step "Step 6: 启动Nginx"

nginx -s stop 2>/dev/null || true
sleep 1
nginx
if [ $? -eq 0 ]; then
    log_success "Nginx启动成功"
else
    log_error "Nginx启动失败"
    journalctl -u nginx --no-pager -n 20 2>/dev/null
fi

# ============================================================
# 部署完成
# ============================================================
echo -e "\n${BOLD}${GREEN}============================================================${NC}"
echo -e "${BOLD}${GREEN}  链盛通LSC系统 V6.2 - 云服务器部署完成${NC}"
echo -e "${BOLD}${GREEN}============================================================${NC}"
echo -e ""
log_info "访问地址:"
echo "  管理后台: https://admin.lianshengtong.com"
echo "  商家前台: https://merchant.lianshengtong.com"
echo "  移动端:   https://m.lianshengtong.com"
echo "  API:      https://api.lianshengtong.com"
echo ""
log_info "常用命令:"
echo "  查看状态: $0 --status"
echo "  查看日志: docker logs -f lsc-gateway"
echo "  重启服务: docker-compose restart"
echo "  停止服务: docker-compose down"
echo ""
log_warn "重要提醒:"
echo "  1. 确保云平台安全组开放 80/443 端口"
echo "  2. 内部端口(8101-8201)仅限内网访问"
echo "  3. 定期备份数据: $BACKUP_DIR"
echo "  4. 监控面板: http://prometheus.lianshengtong.com:9090"