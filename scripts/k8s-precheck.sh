#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2-AI - K8s 部署清单预检脚本
# ============================================================
# 功能:
#   1. 检查 kubectl 是否可用（不可用则用 Python 解析 YAML）
#   2. 校验所有 K8s 清单的 YAML 语法
#   3. 检查关键资源（namespace/configmap/secret/deployment/service/hpa/pdb/networkpolicy）齐备
#   4. 检查镜像地址、资源限制、探针、副本数等关键配置
#   5. 输出预检报告（PASS/WARN/FAIL）
#
# 用法:
#   ./scripts/k8s-precheck.sh
#   ./scripts/k8s-precheck.sh --strict   # WARN 视为 FAIL
# ============================================================

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
K8S_DIR="$PROJECT_ROOT/k8s"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
PASS_COUNT=0; WARN_COUNT=0; FAIL_COUNT=0

log_pass() { echo -e "${GREEN}[PASS]${NC} $*"; ((PASS_COUNT++)); }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $*"; ((WARN_COUNT++)); }
log_fail() { echo -e "${RED}[FAIL]${NC} $*"; ((FAIL_COUNT++)); }
log_info() { echo -e "${CYAN}[INFO]${NC} $*"; }

STRICT=false
[ "${1:-}" = "--strict" ] && STRICT=true

echo "============================================================"
echo "  链盛通 LSC V6.2-AI · K8s 部署清单预检"
echo "  时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo "  目录: $K8S_DIR"
echo "============================================================"
echo ""

# ---- 1. 检查清单文件齐备 ----
log_info "【1/6】检查清单文件齐备性"
REQUIRED_FILES=(
    namespace.yaml
    configmap.yaml
    secrets.yaml
    deployments.yaml
    services.yaml
    hpa.yaml
    pod-disruption-budget.yaml
    network-policy.yaml
    tls-certificates.yaml
)
for f in "${REQUIRED_FILES[@]}"; do
    if [ -f "$K8S_DIR/$f" ]; then
        log_pass "文件存在: $f"
    else
        log_fail "文件缺失: $f"
    fi
done
echo ""

# ---- 2. YAML 语法校验 ----
log_info "【2/6】YAML 语法校验"
HAS_KUBECTL=false
if command -v kubectl &>/dev/null; then
    HAS_KUBECTL=true
    log_info "检测到 kubectl，使用其进行 dry-run 校验"
else
    log_info "未检测到 kubectl，回退到 Python YAML 解析"
fi

if $HAS_KUBECTL; then
    for f in "$K8S_DIR"/*.yaml; do
        name=$(basename "$f")
        if kubectl apply --dry-run=client -f "$f" &>/dev/null; then
            log_pass "YAML 语法正确: $name"
        else
            # namespace 等资源在 dry-run 时可能因已存在而失败，再尝试 parse
            if kubectl apply --dry-run=client --validate=false -f "$f" &>/dev/null; then
                log_warn "YAML 可解析但需集群上下文: $name"
            else
                log_fail "YAML 语法错误: $name"
            fi
        fi
    done
else
    if python3 -c "import yaml" 2>/dev/null; then
        for f in "$K8S_DIR"/*.yaml; do
            name=$(basename "$f")
            if python3 -c "
import yaml, sys
with open('$f') as fp:
    list(yaml.safe_load_all(fp))
" 2>/dev/null; then
                log_pass "YAML 语法正确: $name"
            else
                log_fail "YAML 语法错误: $name"
            fi
        done
    else
        log_warn "Python yaml 模块不可用，跳过语法校验"
    fi
fi
echo ""

# ---- 3. 检查 namespace ----
log_info "【3/6】检查 namespace 声明"
if grep -q "name: lsc-system" "$K8S_DIR/namespace.yaml" 2>/dev/null; then
    log_pass "namespace lsc-system 已声明"
else
    log_fail "未找到 namespace lsc-system"
fi
echo ""

# ---- 4. 检查 Deployment 关键配置 ----
log_info "【4/6】检查 Deployment 关键配置"
DEPLOY_FILE="$K8S_DIR/deployments.yaml"
if [ -f "$DEPLOY_FILE" ]; then
    # 副本数检查
    REPLICA_COUNT=$(grep -c "replicas:" "$DEPLOY_FILE")
    if [ "$REPLICA_COUNT" -ge 8 ]; then
        log_pass "Deployment 副本数声明齐全 ($REPLICA_COUNT 处)"
    else
        log_warn "Deployment 副本声明偏少 ($REPLICA_COUNT 处)"
    fi

    # 滚动更新策略
    if grep -q "RollingUpdate" "$DEPLOY_FILE"; then
        log_pass "已配置滚动更新策略"
    else
        log_warn "未配置滚动更新策略"
    fi

    # 优雅停机
    if grep -q "terminationGracePeriodSeconds" "$DEPLOY_FILE"; then
        log_pass "已配置优雅停机 terminationGracePeriodSeconds"
    else
        log_warn "未配置优雅停机"
    fi

    # 健康检查
    LIVENESS_COUNT=$(grep -c "livenessProbe" "$DEPLOY_FILE")
    READINESS_COUNT=$(grep -c "readinessProbe" "$DEPLOY_FILE")
    if [ "$LIVENESS_COUNT" -ge 8 ] && [ "$READINESS_COUNT" -ge 8 ]; then
        log_pass "健康检查齐全 (liveness=$LIVENESS_COUNT, readiness=$READINESS_COUNT)"
    else
        log_warn "健康检查不完整 (liveness=$LIVENESS_COUNT, readiness=$READINESS_COUNT)"
    fi

    # 资源限制
    REQUESTS_COUNT=$(grep -c "requests:" "$DEPLOY_FILE")
    LIMITS_COUNT=$(grep -c "limits:" "$DEPLOY_FILE")
    if [ "$REQUESTS_COUNT" -ge 8 ] && [ "$LIMITS_COUNT" -ge 8 ]; then
        log_pass "资源限制齐全 (requests=$REQUESTS_COUNT, limits=$LIMITS_COUNT)"
    else
        log_warn "资源限制不全 (requests=$REQUESTS_COUNT, limits=$LIMITS_COUNT)"
    fi

    # 镜像地址
    if grep -q "registry.cn-hangzhou.aliyuncs.com" "$DEPLOY_FILE"; then
        log_pass "镜像仓库地址已配置"
    else
        log_warn "未使用标准镜像仓库地址"
    fi

    # envFrom 注入
    ENVFROM_COUNT=$(grep -c "envFrom:" "$DEPLOY_FILE")
    if [ "$ENVFROM_COUNT" -ge 8 ]; then
        log_pass "ConfigMap/Secret 注入齐全 ($ENVFROM_COUNT 处 envFrom)"
    else
        log_warn "envFrom 注入不全 ($ENVFROM_COUNT 处)"
    fi
else
    log_fail "deployments.yaml 不存在"
fi
echo ""

# ---- 5. 检查 HPA ----
log_info "【5/6】检查 HPA 自动扩缩容"
HPA_FILE="$K8S_DIR/hpa.yaml"
if [ -f "$HPA_FILE" ]; then
    HPA_COUNT=$(grep -c "kind: HorizontalPodAutoscaler" "$HPA_FILE")
    if [ "$HPA_COUNT" -ge 8 ]; then
        log_pass "HPA 配置齐全 ($HPA_COUNT 个)"
    else
        log_warn "HPA 配置偏少 ($HPA_COUNT 个)"
    fi
    if grep -q "maxReplicas" "$HPA_FILE"; then
        log_pass "HPA 已配置 maxReplicas"
    fi
else
    log_fail "hpa.yaml 不存在"
fi
echo ""

# ---- 6. 检查 Secret 模板安全 ----
log_info "【6/6】检查 Secret 模板安全"
SECRET_FILE="$K8S_DIR/secrets.yaml"
if [ -f "$SECRET_FILE" ]; then
    # 占位符检查
    if grep -q 'CHANGE_ME' "$SECRET_FILE" || grep -q '\${' "$SECRET_FILE"; then
        log_pass "Secret 使用占位符，未硬编码真实密钥"
    else
        log_fail "Secret 模板可能包含真实密钥！"
    fi
    # 外部密钥管理标注
    if grep -q "external-secrets\|Sealed\|Vault" "$SECRET_FILE"; then
        log_pass "已标注外部密钥管理方案"
    else
        log_warn "未标注外部密钥管理方案"
    fi
    # 敏感字段齐备
    for key in MYSQL_ROOT_PASSWORD REDIS_PASSWORD JWT_SECRET RABBITMQ_PASSWORD; do
        if grep -q "$key" "$SECRET_FILE"; then
            log_pass "敏感字段已声明: $key"
        else
            log_fail "敏感字段缺失: $key"
        fi
    done
else
    log_fail "secrets.yaml 不存在"
fi
echo ""

# ---- 汇总 ----
echo "============================================================"
echo "  预检汇总"
echo "============================================================"
echo -e "  ${GREEN}PASS${NC}: $PASS_COUNT"
echo -e "  ${YELLOW}WARN${NC}: $WARN_COUNT"
echo -e "  ${RED}FAIL${NC}: $FAIL_COUNT"
echo ""

if [ "$FAIL_COUNT" -gt 0 ]; then
    echo -e "${RED}结论: 存在失败项，需修复后部署${NC}"
    exit 1
elif [ "$STRICT" = true ] && [ "$WARN_COUNT" -gt 0 ]; then
    echo -e "${YELLOW}结论: 严格模式下存在告警项，需处理${NC}"
    exit 1
else
    echo -e "${GREEN}结论: K8s 清单预检通过，可执行部署${NC}"
    exit 0
fi
