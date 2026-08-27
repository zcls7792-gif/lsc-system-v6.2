#!/usr/bin/env bash
# ==========================================================================
# 用法：  source ./scripts/gitlab_ci_auth_setup.sh
# 作用：  从 .gitlab-ci.env 读取 3 要素，设置到当前 shell 进程；
#         同时对 Token/项目信息做合法性预检（脱敏打印，不泄露原值）。
# 安全：  所有带 secret 的变量 echo 时只打印前 6 位 + ****
# ==========================================================================
set +e
# 当前脚本所在目录，source 时也能解析
_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
_ROOT="$( cd "$_SCRIPT_DIR/.." && pwd )"
_ENV_FILE="$_ROOT/.gitlab-ci.env"

echo "╔══════════════════════════════════════════════════╗"
echo "║ 🔐 链盛通 LSC · GitLab 凭据加载器 (v1)            ║"
echo "╚══════════════════════════════════════════════════╝"
echo

if [ "$0" = "${BASH_SOURCE[0]}" ]; then
  echo "  ⚠  警告：你用 ./gitlab_ci_auth_setup.sh 直接执行了本脚本"
  echo "        环境变量不会应用到你当前 shell。请用 source 执行："
  echo "              source $_SCRIPT_DIR/gitlab_ci_auth_setup.sh"
  echo
fi

# -------- 1. 找 env file --------
if [ ! -f "$_ENV_FILE" ]; then
  echo "  ✗ 未找到 $_ENV_FILE"
  echo "     → 请先： cp $_ROOT/.gitlab-ci.env.example $_ROOT/.gitlab-ci.env"
  echo "       然后：编辑 .gitlab-ci.env 填入 HOST / PROJECT_ID / TOKEN"
  return 2 2>/dev/null || exit 2
fi
echo "  ✓ 凭据文件位置 : $_ENV_FILE"
echo "    已加入 .gitignore  : $(grep -q '^\.gitlab-ci.env$' "$_ROOT/.gitignore" && echo YES || echo NO，会自动修补)"
if ! grep -q '^\.gitlab-ci.env$' "$_ROOT/.gitignore" 2>/dev/null; then
  echo ".gitlab-ci.env" >> "$_ROOT/.gitignore"
  echo "     （已自动修补 .gitignore 追加忽略行）"
fi
chmod 600 "$_ENV_FILE"
echo "    文件权限(修后): $(stat -c '%a %U:%G' "$_ENV_FILE" 2>/dev/null || stat -f '%Lp %Su:%Sg' "$_ENV_FILE" 2>/dev/null)"

# -------- 2. source 三要素 --------
# shellcheck disable=SC1090
set -a; . "$_ENV_FILE"; set +a

# -------- 3. 预检缺失 --------
MISS=0
for NEED in GITLAB_TOKEN GITLAB_HOST; do
  VAL="${!NEED}"
  if [ -z "$VAL" ]; then
    echo "  ✗ $NEED 未设置"
    MISS=$((MISS+1))
  fi
done
if [ -z "$GITLAB_PROJECT_ID" ] && [ -z "$GITLAB_PROJECT" ]; then
  echo "  ✗ GITLAB_PROJECT_ID 和 GITLAB_PROJECT 至少要填一个（推荐数字 ID）"
  MISS=$((MISS+1))
fi

# -------- 4. 脱敏打印 & 合规检查 --------
echo
echo "  ┌─ 加载结果（脱敏）──────────────────────────────"
if [ -n "$GITLAB_HOST" ]; then
  if [[ "$GITLAB_HOST" =~ ^https?://[A-Za-z0-9._:-]+[^/]$ ]] && [ "${GITLAB_HOST: -1}" != "/" ]; then
    echo "  │ HOST     = $GITLAB_HOST   ✓ 格式 OK（不以 / 结尾）"
  else
    echo "  │ HOST     = $GITLAB_HOST   ⚠ 建议修改为不以 / 结尾，如 https://gitlab.com"
  fi
fi
if [ -n "$GITLAB_PROJECT_ID" ]; then
  if [[ "$GITLAB_PROJECT_ID" =~ ^[0-9]+$ ]]; then
    echo "  │ PROJECT_ID = $GITLAB_PROJECT_ID   ✓ 纯数字 ID（推荐）"
  else
    echo "  │ PROJECT_ID = $GITLAB_PROJECT_ID   ✗ 非纯数字，Project ID 必须是数字"
    MISS=$((MISS+1))
  fi
fi
if [ -n "$GITLAB_PROJECT" ]; then
  echo "  │ PROJECT  = $GITLAB_PROJECT   （source 后将 URLEncode 成 namespace%2Fproject）"
fi
if [ -n "$GITLAB_TOKEN" ]; then
  TOK_LEN=${#GITLAB_TOKEN}
  PREFIX="${GITLAB_TOKEN:0:6}"
  PAT_GL="^glpat-[A-Za-z0-9\-_]{20,}$"
  PAT_OTHER="^.{12,}$"
  if [[ "$GITLAB_TOKEN" =~ $PAT_GL ]]; then
    PAT_STATUS="✓ glpat- 前缀，标准 GitLab PAT"
  elif [[ "$GITLAB_TOKEN" =~ $PAT_OTHER ]]; then
    PAT_STATUS="⚠ 非 glpat- 标准格式（可能是 Project Token / CI_JOB_TOKEN，也能用）"
  else
    PAT_STATUS="✗ 长度/格式异常，请检查复制是否完整"
    MISS=$((MISS+1))
  fi
  echo "  │ TOKEN    = ${PREFIX}****（len=$TOK_LEN） $PAT_STATUS"
fi
echo "  └────────────────────────────────────────────────"

# -------- 5. scope 审计提示 --------
if [ "${GITLAB_AUDIT_SCOPES:-1}" = "1" ]; then
  echo
  echo "  ℹ 下一步：运行进度检查器会调用 /personal_access_tokens/self 只读验证 Token 的 scopes"
  echo "       如果发现 scope != read_api（或含 api/write_*），会提示你重建最小权限 PAT。"
fi

echo
if [ "$MISS" -gt 0 ]; then
  echo "  ✗ 发现 $MISS 项必填缺失，请编辑 $_ENV_FILE 后重新 source"
  return 1 2>/dev/null || exit 1
fi
echo "  ✓ 全部必填已设置。现在可以执行："
echo "      node scripts/gitlab_ci_progress_checker.js"
return 0 2>/dev/null || true
