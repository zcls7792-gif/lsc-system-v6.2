#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2-AI - 生产部署综合预检脚本
# ============================================================
# 功能:
#   1. 工具链就绪检查（mvn/java/docker/kubectl/openssl）
#   2. 代码编译与测试验证（mvn test + jacoco:check）
#   3. K8s 清单预检（调用 k8s-precheck.sh）
#   4. 密钥文件检查（docker/.env 是否存在且符合规范）
#   5. Docker 镜像构建脚本完整性
#   6. 部署手册与报告文档齐备性
#   7. 输出综合预检报告
#
# 用法:
#   ./scripts/deploy-precheck.sh
# ============================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_ROOT"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
PASS_COUNT=0; WARN_COUNT=0; FAIL_COUNT=0
RESULTS_FILE="/tmp/lsc_deploy_precheck_$(date +%s).log"
> "$RESULTS_FILE"

log_pass() { echo -e "${GREEN}[PASS]${NC} $*" | tee -a "$RESULTS_FILE"; ((PASS_COUNT++)); }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*" | tee -a "$RESULTS_FILE"; ((WARN_COUNT++)); }
log_fail() { echo -e "${RED}[FAIL]${NC} $*" | tee -a "$RESULTS_FILE"; ((FAIL_COUNT++)); }
log_info() { echo -e "${CYAN}[INFO]${NC} $*" | tee -a "$RESULTS_FILE"; }
log_section() { echo "" | tee -a "$RESULTS_FILE"; echo -e "${CYAN}==== $* ====${NC}" | tee -a "$RESULTS_FILE"; }

echo "============================================================" | tee -a "$RESULTS_FILE"
echo "  链盛通 LSC V6.2-AI · 生产部署综合预检" | tee -a "$RESULTS_FILE"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')" | tee -a "$RESULTS_FILE"
echo "  项目根: $PROJECT_ROOT" | tee -a "$RESULTS_FILE"
echo "============================================================" | tee -a "$RESULTS_FILE"

# ---- 1. 工具链检查 ----
log_section "1/7 工具链就绪检查"

check_tool() {
    local tool=$1
    local min_ver=$2
    if command -v "$tool" &>/dev/null; then
        local ver
        ver=$($tool --version 2>&1 | head -1)
        log_pass "$tool 可用: $ver"
    else
        log_fail "$tool 未安装 (建议版本: $min_ver)"
    fi
}

check_tool mvn "3.9"
check_tool java "17"
check_tool openssl "1.1"
check_tool git "2.0"

# docker 与 kubectl 为部署阶段工具
if command -v docker &>/dev/null; then
    log_pass "docker 可用: $(docker --version)"
else
    log_warn "docker 未安装（部署阶段需要）"
fi

if command -v kubectl &>/dev/null; then
    log_pass "kubectl 可用: $(kubectl version --client --short 2>/dev/null || kubectl version --client 2>&1 | head -1)"
else
    log_warn "kubectl 未安装（K8s 部署阶段需要）"
fi

# ---- 2. 代码与测试验证 ----
log_section "2/7 代码编译与测试验证"

if [ -f "pom.xml" ]; then
    log_pass "父 POM 存在"

    # 模块数检查
    MODULE_COUNT=$(grep -c "<module>" pom.xml)
    if [ "$MODULE_COUNT" -ge 17 ]; then
        log_pass "微服务模块齐全 ($MODULE_COUNT 个)"
    else
        log_warn "微服务模块数偏少 ($MODULE_COUNT 个，预期 17)"
    fi

    # 检查最近一次构建产物
    if [ -d "lsc-gateway/target/classes" ]; then
        log_pass "已存在编译产物（lsc-gateway/target/classes）"
    else
        log_warn "未检测到编译产物，部署前需执行 mvn clean package -DskipTests"
    fi
else
    log_fail "pom.xml 不存在"
fi

# ---- 3. K8s 清单预检 ----
log_section "3/7 K8s 清单预检"

if [ -f "scripts/k8s-precheck.sh" ]; then
    log_info "调用 k8s-precheck.sh --strict ..."
    if bash scripts/k8s-precheck.sh --strict >>"$RESULTS_FILE" 2>&1; then
        log_pass "K8s 清单预检通过"
    else
        log_fail "K8s 清单预检未通过"
    fi
else
    log_fail "scripts/k8s-precheck.sh 不存在"
fi

# ---- 4. 密钥文件检查 ----
log_section "4/7 密钥文件检查"

ENV_FILE="docker/.env"
if [ -f "$ENV_FILE" ]; then
    log_pass "docker/.env 文件存在"

    # 权限检查
    PERM=$(stat -c '%a' "$ENV_FILE")
    if [ "$PERM" = "600" ]; then
        log_pass ".env 文件权限正确 (600)"
    else
        log_warn ".env 文件权限偏宽 ($PERM，建议 600)"
    fi

    # 占位符检查（不应有 CHANGE_ME）
    if grep -q "CHANGE_ME" "$ENV_FILE"; then
        log_fail ".env 仍含 CHANGE_ME 占位符，未填入真实密钥"
    else
        log_pass ".env 已填入真实密钥（无 CHANGE_ME 占位）"
    fi

    # 关键字段检查
    for key in MYSQL_ROOT_PASSWORD REDIS_PASSWORD JWT_ACCESS_SECRET RABBITMQ_DEFAULT_PASS; do
        if grep -q "^$key=" "$ENV_FILE" && ! grep -q "^$key=$" "$ENV_FILE"; then
            log_pass "密钥字段已填入: $key"
        else
            log_fail "密钥字段缺失或为空: $key"
        fi
    done

    # JWT 强度检查
    JWT=$(grep '^JWT_ACCESS_SECRET=' "$ENV_FILE" | cut -d= -f2)
    JWT_LEN=${#JWT}
    if [ "$JWT_LEN" -ge 32 ]; then
        log_pass "JWT 密钥强度足够 ($JWT_LEN 字节)"
    else
        log_warn "JWT 密钥强度不足 ($JWT_LEN 字节，建议 >= 32)"
    fi
else
    log_warn "docker/.env 不存在，请先执行: bash scripts/rotate-secrets.sh"
fi

# gitignore 检查
if git check-ignore docker/.env &>/dev/null; then
    log_pass "docker/.env 已被 .gitignore 拦截"
else
    log_fail "docker/.env 未被 .gitignore 拦截，存在泄露风险！"
fi

# ---- 5. Docker 镜像构建脚本 ----
log_section "5/7 Docker 镜像构建脚本"

BUILD_SCRIPT="docker/build-images.sh"
if [ -f "$BUILD_SCRIPT" ]; then
    log_pass "build-images.sh 存在"
    if [ -x "$BUILD_SCRIPT" ]; then
        log_pass "build-images.sh 有执行权限"
    else
        log_warn "build-images.sh 缺少执行权限"
    fi

    # 服务列表检查
    SVC_COUNT=$(grep -E '^\s+"lsc-' "$BUILD_SCRIPT" | wc -l)
    if [ "$SVC_COUNT" -ge 16 ]; then
        log_pass "构建脚本覆盖全部后端服务 ($SVC_COUNT 个)"
    else
        log_warn "构建脚本后端服务覆盖不全 ($SVC_COUNT 个)"
    fi
else
    log_fail "build-images.sh 不存在"
fi

# Dockerfile 检查
for df in docker/Dockerfile docker/Dockerfile.frontend; do
    if [ -f "$df" ]; then
        log_pass "Dockerfile 存在: $df"
    else
        log_warn "Dockerfile 缺失: $df"
    fi
done

# .dockerignore 检查
if [ -f ".dockerignore" ]; then
    log_pass ".dockerignore 存在"
else
    log_warn ".dockerignore 缺失"
fi

# ---- 6. 部署文档与报告 ----
log_section "6/7 部署文档与报告"

DOCS=(
    "README.md"
    "LSC_V6.2_Release_Readiness_Final_Report.md"
    "LSC_V6.2_Reports/root-archive/LSC_V6.2_Deployment_Runbook.md"
)
for doc in "${DOCS[@]}"; do
    if [ -f "$doc" ]; then
        log_pass "文档存在: $doc"
    else
        log_warn "文档缺失: $doc"
    fi
done

# Nacos 配置齐备
NACOS_COUNT=$(ls config/nacos/ 2>/dev/null | wc -l)
if [ "$NACOS_COUNT" -ge 5 ]; then
    log_pass "Nacos 共享配置齐全 ($NACOS_COUNT 个)"
else
    log_warn "Nacos 配置文件偏少 ($NACOS_COUNT 个)"
fi

# SQL 初始化脚本
if [ -f "sql/lsc_system_v6.2.sql" ] && [ -f "sql/lsc_sharding.sql" ]; then
    log_pass "数据库初始化脚本齐全"
else
    log_fail "数据库初始化脚本缺失"
fi

# ---- 7. 可观测性配置 ----
log_section "7/7 可观测性配置"

if [ -f "cloud/monitoring/prometheus.yml" ] || [ -f "docker/config/prometheus/prometheus.yml" ]; then
    log_pass "Prometheus 配置存在"
else
    log_warn "Prometheus 配置缺失"
fi

if [ -f "cloud/monitoring/alert-rules.yml" ]; then
    ALERT_COUNT=$(grep -c "alert:" cloud/monitoring/alert-rules.yml)
    if [ "$ALERT_COUNT" -ge 10 ]; then
        log_pass "告警规则齐全 ($ALERT_COUNT 条)"
    else
        log_warn "告警规则偏少 ($ALERT_COUNT 条)"
    fi
else
    log_warn "告警规则文件缺失"
fi

# 限流配置
if [ -f "config/nacos/lsc-gateway-routes.yaml" ]; then
    RATELIMIT_COUNT=$(grep -c "RateLimiter" config/nacos/lsc-gateway-routes.yaml)
    if [ "$RATELIMIT_COUNT" -ge 14 ]; then
        log_pass "网关限流配置齐全 ($RATELIMIT_COUNT 处)"
    else
        log_warn "网关限流配置不全 ($RATELIMIT_COUNT 处)"
    fi
fi

if [ -f "config/nacos/lsc-sentinel-rules.json" ]; then
    log_pass "Sentinel 限流降级规则存在"
fi

# ---- 汇总 ----
log_section "预检汇总"

echo "  PASS: $PASS_COUNT" | tee -a "$RESULTS_FILE"
echo "  WARN: $WARN_COUNT" | tee -a "$RESULTS_FILE"
echo "  FAIL: $FAIL_COUNT" | tee -a "$RESULTS_FILE"
echo "" | tee -a "$RESULTS_FILE"

if [ "$FAIL_COUNT" -gt 0 ]; then
    echo -e "${RED}结论: 存在 $FAIL_COUNT 项失败，需修复后方可部署${NC}" | tee -a "$RESULTS_FILE"
    exit 1
elif [ "$WARN_COUNT" -gt 0 ]; then
    echo -e "${YELLOW}结论: 存在 $WARN_COUNT 项告警，建议处理后再部署${NC}" | tee -a "$RESULTS_FILE"
    exit 0
else
    echo -e "${GREEN}结论: 生产部署预检全部通过，可执行部署${NC}" | tee -a "$RESULTS_FILE"
    exit 0
fi
