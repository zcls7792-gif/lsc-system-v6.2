#!/bin/bash
# ============================================================
# 链盛通LSC系统 V6.2-AI - Git 历史密钥清理脚本
# ============================================================
# 功能:
#   扫描 Git 历史中泄露的密钥，并生成 BFG/git-filter-repo 清理命令。
#
# ⚠️ 重要警告:
#   1. Git 历史清理会重写所有提交哈希，是破坏性操作
#   2. 必须在所有协作者知情并暂停开发后执行
#   3. 执行后需强制推送 (git push --force)，所有协作者须重新 clone
#   4. 即使清理了历史，密钥仍已泄露，必须轮换全部密钥
#
# 用法:
#   ./scripts/clean-git-history.sh --scan       # 仅扫描，不修改 (安全)
#   ./scripts/clean-git-history.sh --generate   # 生成清理命令但不执行
#   ./scripts/clean-git-history.sh --execute    # 执行清理 (危险!)
#
# 前置: git-filter-repo (推荐) 或 BFG Repo-Cleaner
#   pip install git-filter-repo
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
log_info()    { echo -e "${CYAN}[INFO]${NC} $*"; }
log_success() { echo -e "${GREEN}[OK]${NC} $*"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $*"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $*"; }

MODE="--scan"
for arg in "$@"; do
    case "$arg" in
        --scan)      MODE="--scan" ;;
        --generate)  MODE="--generate" ;;
        --execute)   MODE="--execute" ;;
        --help|-h)   sed -n '3,20p' "$0"; exit 0 ;;
        *) log_error "未知参数: $arg"; exit 1 ;;
    esac
done

cd "$PROJECT_ROOT"

# ---- 已知泄露的密钥模式 ----
# 这些是历史提交中硬编码过的密码
LEAKED_SECRETS=(
    "Lsc@2026#Secure"
    "Lsc@Redis2026"
    "Lsc@MQ2026"
    "Lsc@Nacos2026"
    "Lsc@Grafana2026"
    "Lsc2026JwtSecretKeyMustBe32BytesAtLeast!!"
    "Admin@2026#Secure"
    "Auditor@2026#Secure"
    "Operator@2026#Secure"
    "Lsc@XxlJob2026Token"
)

# ---- 扫描模式 ----
if [ "$MODE" = "--scan" ]; then
    log_info "扫描 Git 历史中泄露的密钥..."
    log_info "搜索 ${#LEAKED_SECRETS[@]} 个已知密钥模式"
    echo ""

    FOUND=0
    for secret in "${LEAKED_SECRETS[@]}"; do
        # 在所有历史提交中搜索
        commits=$(git log --all --oneline -S "$secret" 2>/dev/null | wc -l)
        if [ "$commits" -gt 0 ]; then
            log_warn "发现泄露: '$secret' 出现在 $commits 个提交中"
            git log --all --oneline -S "$secret" 2>/dev/null | head -3 | sed 's/^/    /'
            FOUND=$((FOUND + 1))
            echo ""
        fi
    done

    if [ "$FOUND" -gt 0 ]; then
        log_error "共发现 $FOUND 个密钥泄露在 Git 历史中"
        log_warn "建议执行: $0 --generate  查看清理命令"
        log_warn "清理后必须轮换所有密钥（轮换脚本: scripts/rotate-secrets.sh --force）"
    else
        log_success "未在 Git 历史中发现已知密钥泄露"
    fi
    exit 0
fi

# ---- 生成清理命令模式 ----
if [ "$MODE" = "--generate" ]; then
    log_info "生成 Git 历史清理命令..."
    echo ""
    echo "============================================================"
    echo "方法一: git-filter-repo (推荐)"
    echo "============================================================"
    echo ""
    echo "# 1. 安装 git-filter-repo"
    echo "pip install git-filter-repo"
    echo ""
    echo "# 2. 创建替换规则文件"
    RULES_FILE=$(mktemp)
    for secret in "${LEAKED_SECRETS[@]}"; do
        echo "$secret==>REDACTED_SECRET" >> "$RULES_FILE"
    done
    echo "cat > /tmp/lsc-secret-replacements.txt <<'EOF'"
    for secret in "${LEAKED_SECRETS[@]}"; do
        echo "$secret==>REDACTED_SECRET"
    done
    echo "EOF"
    echo ""
    echo "# 3. 执行清理 (会重写所有历史)"
    echo "git filter-repo --replace-text /tmp/lsc-secret-replacements.txt"
    echo ""
    echo "# 4. 强制推送 (危险! 通知所有协作者)"
    echo "git push origin --force --all"
    echo "git push origin --force --tags"
    echo ""
    echo "============================================================"
    echo "方法二: BFG Repo-Cleaner"
    echo "============================================================"
    echo ""
    echo "# 1. 下载 BFG: https://rtyley.github.io/bfg-repo-cleaner/"
    echo "wget https://repo1.maven.org/maven2/com/madgag/bfg/1.14.0/bfg-1.14.0.jar"
    echo ""
    echo "# 2. 创建密钥文件"
    echo "cat > /tmp/lsc-secrets.txt <<'EOF'"
    for secret in "${LEAKED_SECRETS[@]}"; do
        echo "$secret"
    done
    echo "EOF"
    echo ""
    echo "# 3. 执行清理 (需先 git clone --mirror)"
    echo "java -jar bfg-1.14.0.jar --replace-text /tmp/lsc-secrets.txt lsc-system.git"
    echo "cd lsc-system.git && git reflog expire --expire=now --all && git gc --prune=now --aggressive"
    echo "git push --force"
    echo ""
    log_warn "清理后必须:"
    log_warn "  1. 轮换全部密钥: scripts/rotate-secrets.sh --force"
    log_warn "  2. 通知所有协作者重新 clone 仓库"
    log_warn "  3. 更新 CI/CD 中的密钥引用"
    exit 0
fi

# ---- 执行模式 ----
if [ "$MODE" = "--execute" ]; then
    log_error "即将重写 Git 历史，这是破坏性操作!"
    log_warn "请确认:"
    log_warn "  1. 已通知所有协作者暂停开发"
    log_warn "  2. 已备份当前仓库"
    log_warn "  3. 已准备好轮换密钥"
    echo ""
    read -p "输入 YES 确认执行: " CONFIRM
    if [ "$CONFIRM" != "YES" ]; then
        log_info "已取消"
        exit 0
    fi

    if ! command -v git-filter-repo &>/dev/null; then
        log_error "未找到 git-filter-repo，请安装: pip install git-filter-repo"
        exit 1
    fi

    RULES_FILE=$(mktemp)
    for secret in "${LEAKED_SECRETS[@]}"; do
        echo "$secret==>REDACTED_SECRET" >> "$RULES_FILE"
    done

    log_info "执行 git-filter-repo 清理..."
    git filter-repo --replace-text "$RULES_FILE"

    rm -f "$RULES_FILE"
    log_success "Git 历史清理完成"
    log_warn "下一步:"
    log_warn "  1. 强制推送: git push origin --force --all && git push origin --force --tags"
    log_warn "  2. 轮换密钥: scripts/rotate-secrets.sh --force"
    log_warn "  3. 通知协作者重新 clone"
fi
