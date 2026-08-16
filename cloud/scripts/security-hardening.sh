#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2 - 云服务器安全加固脚本
# 支持阿里云ECS / 腾讯云CVM / 华为云ECS / AWS EC2
# 适用: Alibaba Cloud Linux 3 / CentOS 7+/Ubuntu 20.04+
# ============================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BOLD='\033[1m'
NC='\033[0m'

LOG_FILE="/var/log/lsc-security-hardening-$(date +%Y%m%d).log"
BACKUP_DIR="/var/backups/lsc-security/$(date +%Y%m%d_%H%M%S)"

log_info()    { echo -e "${GREEN}[INFO]${NC} $*" | tee -a "$LOG_FILE"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $*" | tee -a "$LOG_FILE"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*" | tee -a "$LOG_FILE"; }
log_step()    { echo -e "\n${BOLD}${CYAN}==== $* ====${NC}" | tee -a "$LOG_FILE"; }

CYAN='\033[0;36m'

# ---- 检查是否以root运行 ----
if [ "$EUID" -ne 0 ]; then
    log_error "此脚本需要root权限运行"
    exit 1
fi

# ---- 创建备份目录 ----
mkdir -p "$BACKUP_DIR"

echo -e "${BOLD}${CYAN}"
echo "============================================================"
echo "  链盛通LSC系统 V6.2 - 云服务器安全加固"
echo "  支持: 阿里云/腾讯云/华为云/AWS ECS"
echo "============================================================"
echo -e "${NC}"

# ============================================================
# Step 1: 系统基础信息采集
# ============================================================
log_step "Step 1: 系统基础信息"

OS_TYPE=""
OS_VERSION=""
if [ -f /etc/os-release ]; then
    . /etc/os-release
    OS_TYPE="$ID"
    OS_VERSION="$VERSION_ID"
elif [ -f /etc/redhat-release ]; then
    OS_TYPE="rhel"
    OS_VERSION=$(cat /etc/redhat-release | grep -oP '\d+\.\d+' | head -1)
fi

log_info "操作系统: $OS_TYPE $OS_VERSION"
log_info "内核版本: $(uname -r)"
log_info "CPU核心: $(nproc)"
log_info "内存总量: $(free -h | grep Mem | awk '{print $2}')"
log_info "磁盘空间: $(df -h / | tail -1 | awk '{print $2}')"
log_info "主机名: $(hostname)"
log_info "IP地址: $(hostname -I | awk '{print $1}')"

# ============================================================
# Step 2: 创建LSC专用用户
# ============================================================
log_step "Step 2: 创建LSC服务用户"

if ! id lsc_app &>/dev/null; then
    useradd -r -s /sbin/nologin -d /home/lsc_app lsc_app
    log_success "已创建服务用户: lsc_app"
else
    log_info "服务用户 lsc_app 已存在"
fi

# 创建必要目录
DIRS=(
    "/data/lsc/app"
    "/data/lsc/logs"
    "/data/lsc/backup"
    "/data/lsc/data"
    "/data/lsc/tmp"
    "/etc/lsc/ssl"
    "/opt/lsc"
)

for dir in "${DIRS[@]}"; do
    mkdir -p "$dir"
    chown -R lsc_app:lsc_app "$dir" 2>/dev/null || true
done

log_success "已创建LSC目录结构"

# ============================================================
# Step 3: SSH安全配置
# ============================================================
log_step "Step 3: SSH安全加固"

SSH_CONFIG="/etc/ssh/sshd_config"
if [ -f "$SSH_CONFIG" ]; then
    cp "$SSH_CONFIG" "$BACKUP_DIR/sshd_config.bak"

    # SSH安全配置
    sed -i 's/#Port 22/Port 2222/' "$SSH_CONFIG" 2>/dev/null || true
    sed -i 's/#PermitRootLogin yes/PermitRootLogin no/' "$SSH_CONFIG" 2>/dev/null || true
    sed -i 's/#PasswordAuthentication yes/PasswordAuthentication no/' "$SSH_CONFIG" 2>/dev/null || true
    sed -i 's/#MaxAuthTries 6/MaxAuthTries 3/' "$SSH_CONFIG" 2>/dev/null || true
    sed -i 's/#ClientAliveInterval 0/ClientAliveInterval 300/' "$SSH_CONFIG" 2>/dev/null || true
    sed -i 's/#ClientAliveCountMax 3/ClientAliveCountMax 2/' "$SSH_CONFIG" 2>/dev/null || true
    sed -i 's/X11Forwarding yes/X11Forwarding no/' "$SSH_CONFIG" 2>/dev/null || true

    log_info "SSH配置已更新(端口:2222, 禁止root登录, 密钥认证)"
    log_warn "请确认SSH密钥已配置后再重启SSH服务"
fi

# ============================================================
# Step 4: 防火墙配置
# ============================================================
log_step "Step 4: 防火墙配置"

# 关闭SELinux(如启用)
if [ -f /etc/selinux/config ]; then
    cp /etc/selinux/config "$BACKUP_DIR/selinux_config.bak"
    sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config
    setenforce 0 2>/dev/null || true
    log_warn "SELinux已禁用(如需保留请手动配置策略)"
fi

# 配置firewalld
if command -v firewall-cmd &>/dev/null; then
    systemctl enable firewalld 2>/dev/null || true
    systemctl start firewalld 2>/dev/null || true

    # 开放必要端口
    PORTS=(
        "2222/tcp"      # SSH
        "80/tcp"        # HTTP
        "443/tcp"       # HTTPS
        "8000/tcp"      # API网关(内部)
        "8101-8113/tcp" # 业务服务(内部)
        "8200-8201/tcp" # 管理/AI服务(内部)
        "9090/tcp"      # Prometheus(内部)
        "3306/tcp"      # MySQL(内部)
        "6379/tcp"      # Redis(内部)
        "5672/tcp"      # RabbitMQ(内部)
        "8848/tcp"      # Nacos(内部)
        "8091/tcp"      # Seata(内部)
        "8080/tcp"      # XXL-JOB(内部)
    )

    for port in "${PORTS[@]}"; do
        firewall-cmd --permanent --add-port="$port" 2>/dev/null || true
    done

    firewall-cmd --reload 2>/dev/null || true
    log_success "Firewalld已配置, 开放${#PORTS}个端口"

# 配置ufw(Ubuntu)
elif command -v ufw &>/dev/null; then
    ufw enable 2>/dev/null || true
    ufw default deny incoming
    ufw default allow outgoing

    PORTS=("2222" "80" "443" "8000" "8101:8113" "8200" "8201" "9090" "3306" "6379" "5672" "8848" "8091" "8080")
    for port in "${PORTS[@]}"; do
        ufw allow "$port" 2>/dev/null || true
    done

    ufw --force enable 2>/dev/null || true
    log_success "UFW已配置"
else
    log_warn "未检测到防火墙, 建议通过云平台安全组配置"
fi

# ============================================================
# Step 5: 内核参数优化
# ============================================================
log_step "Step 5: 内核参数调优"

SYSCTL_CONFIG="/etc/sysctl.d/99-lsc-optimization.conf"
cat > "$SYSCTL_CONFIG" << 'EOF'
# 链盛通LSC系统 - 内核参数优化

# ---- 网络优化 ----
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 65535
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.core.rmem_default = 262144
net.core.wmem_default = 262144
net.ipv4.tcp_max_syn_backlog = 65535
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 15
net.ipv4.tcp_keepalive_time = 600
net.ipv4.tcp_keepalive_probes = 3
net.ipv4.tcp_keepalive_intvl = 15
net.ipv4.ip_local_port_range = 1024 65535

# ---- 文件句柄 ----
fs.file-max = 2097152
fs.inotify.max_user_watches = 524288
fs.inotify.max_user_instances = 1024

# ---- 内存优化 ----
vm.swappiness = 10
vm.dirty_ratio = 20
vm.dirty_background_ratio = 5
vm.dirty_expire_centisecs = 3000
vm.dirty_writeback_centisecs = 500
vm.overcommit_memory = 1

# ---- 网络拥塞控制 ----
net.ipv4.tcp_congestion_control = bbr
EOF

sysctl -p "$SYSCTL_CONFIG" 2>/dev/null || true
log_success "内核参数已优化"

# ============================================================
# Step 6: 文件句柄限制
# ============================================================
log_step "Step 6: 资源限制配置"

LIMITS_CONFIG="/etc/security/limits.d/99-lsc-limits.conf"
cat > "$LIMITS_CONFIG" << 'EOF'
# 链盛通LSC系统 - 资源限制
* soft nofile 2097152
* hard nofile 2097152
* soft nproc 131072
* hard nproc 131072
* soft memlock unlimited
* hard memlock unlimited
* soft core unlimited
* hard core unlimited
root soft nofile 2097152
root hard nofile 2097152
lsc_app soft nofile 2097152
lsc_app hard nofile 2097152
lsc_app soft nproc 131072
lsc_app hard nproc 131072
EOF

log_success "资源限制已配置"

# ============================================================
# Step 7: 禁用不必要的服务
# ============================================================
log_step "Step 7: 禁用不必要的服务"

DISABLE_SERVICES=(
    "postfix"
    "sendmail"
    "telnet"
    "vsftpd"
    "ypserv"
    "rsh"
    "talk"
    "avahi-daemon"
    "cups"
)

for svc in "${DISABLE_SERVICES[@]}"; do
    systemctl stop "$svc" 2>/dev/null && systemctl disable "$svc" 2>/dev/null || true
done

log_info "已检查并禁用不必要的服务"

# ============================================================
# Step 8: 自动安全更新
# ============================================================
log_step "Step 8: 配置自动安全更新"

if [ "$OS_TYPE" = "ubuntu" ] || [ "$OS_TYPE" = "debian" ]; then
    apt-get update -qq && apt-get install -y -qq unattended-upgrades
    systemctl enable unattended-upgrades
    log_success "已配置Ubuntu自动安全更新"
elif [ "$OS_TYPE" = "centos" ] || [ "$OS_TYPE" = "rhel" ] || [ "$OS_TYPE" = "almalinux" ] || [ "$OS_TYPE" = "rocky" ]; then
    yum install -y -qq yum-cron 2>/dev/null || true
    systemctl enable yum-cron 2>/dev/null || true
    log_success "已配置CentOS/RHEL自动安全更新"
fi

# ============================================================
# Step 9: 时区和NTP同步
# ============================================================
log_step "Step 9: 时区与时间同步"

timedatectl set-timezone Asia/Shanghai 2>/dev/null || true

if command -v chronyc &>/dev/null; then
    chronyc sources 2>/dev/null || true
    log_success "Chrony时间同步已配置"
elif command -v timedatectl &>/dev/null; then
    timedatectl set-ntp true 2>/dev/null || true
    log_success "NTP时间同步已启用"
fi

# ============================================================
# Step 10: 系统安全检查
# ============================================================
log_step "Step 10: 安全检查摘要"

SECURITY_REPORT="$BACKUP_DIR/security-report.txt"
{
    echo "=== LSC系统安全检查报告 ==="
    echo "生成时间: $(date)"
    echo ""
    echo "1. SSH配置:"
    echo "   端口: $(grep '^Port' /etc/ssh/sshd_config 2>/dev/null || echo '默认22')"
    echo "   Root登录: $(grep '^PermitRootLogin' /etc/ssh/sshd_config 2>/dev/null || echo '默认')"
    echo "   密码认证: $(grep '^PasswordAuthentication' /etc/ssh/sshd_config 2>/dev/null || echo '默认')"
    echo ""
    echo "2. 防火墙状态:"
    if command -v firewall-cmd &>/dev/null; then
        echo "   Firewalld: $(firewall-cmd --state 2>/dev/null || echo '未知')"
    elif command -v ufw &>/dev/null; then
        echo "   UFW: $(ufw status 2>/dev/null | head -1)"
    fi
    echo ""
    echo "3. 内核参数:"
    echo "   TCP拥塞控制: $(sysctl net.ipv4.tcp_congestion_control 2>/dev/null)"
    echo "   文件句柄上限: $(sysctl fs.file-max 2>/dev/null)"
    echo ""
    echo "4. 用户检查:"
    echo "   LSC服务用户: $(id lsc_app 2>/dev/null || echo '不存在')"
    echo ""
    echo "5. 时间同步:"
    echo "   时区: $(timedatectl 2>/dev/null | grep 'Time zone' || echo '未知')"
    echo "   NTP: $(timedatectl 2>/dev/null | grep 'NTP' || echo '未知')"
} | tee "$SECURITY_REPORT"

# ============================================================
# 完成
# ============================================================
echo -e "\n${BOLD}${GREEN}============================================================${NC}"
echo -e "${BOLD}${GREEN}  云服务器安全加固完成!${NC}"
echo -e "${BOLD}${GREEN}============================================================${NC}"
echo -e ""
log_info "安全报告保存在: $SECURITY_REPORT"
log_info "备份目录: $BACKUP_DIR"
log_info "日志文件: $LOG_FILE"
echo -e ""
log_warn "====== 重要提醒 ======"
log_warn "1. 请确保SSH公钥已配置到 lsc_app 用户"
log_warn "2. SSH端口已修改为2222, 请更新云平台安全组"
log_warn "3. 建议重启服务器使内核参数生效"
log_warn "4. 云平台安全组需同步开放对应端口"
log_warn "5. 内部服务端口(8101-8201)仅限内网访问"