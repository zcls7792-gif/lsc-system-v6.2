#!/usr/bin/env bash
# =========================================================================
#  Priority 1 / Step 3 — XXL-JOB 3 个任务注册脚本
#  =========================================================================
#  使用前置条件：
#   1) 先在 XXL-JOB Admin 浏览器登录拿到 cookie（XXL_JOB_ACOKEN 或 XXL_JOB_LOGIN_IDENTITY 具体名看实际）
#   2) 在「执行器管理」获取 lsc-release-service 对应执行器 ID（app=lsc-release-service）— 填入 APP_HANDLER_ID
#   3) 把下面 4 个变量改对后，直接 bash 03-xxljob-create-jobs.sh 执行
#  执行结果：每个任务会打印 HTTP 200 和返回的 JSON（含新 jobId）
# =========================================================================

set -euo pipefail

# ===== 请修改：生产参数 =====
ADMIN_URL="http://xxljob-prod.lsc.local:9999/xxl-job-admin"
COOKIE="XXL_JOB_LOGIN_IDENTITY=__REPLACE_ME__"       # 从浏览器 DevTools 复制整个 Cookie 字符串
APP_HANDLER_ID=__REPLACE_ME__                         # 执行器 ID（在执行器管理页的"OnLine 机器地址"行前那列）
AUTHOR="lianshengtong-platform"

# =========================================================================
creat_job() {
  # 参数顺序：jobHandler / cron / desc / priority
  local jobHandler="$1" cron="$2" desc="$3" priority="$4"
  echo "→ 创建 jobHandler=${jobHandler}  cron=${cron}"
  curl -sS -X POST "${ADMIN_URL}/jobinfo/add" \
    -H "Cookie: ${COOKIE}" \
    -H 'Content-Type: application/x-www-form-urlencoded' \
    --data-urlencode "jobGroup=${APP_HANDLER_ID}" \
    --data-urlencode "jobDesc=${desc}" \
    --data-urlencode "author=${AUTHOR}" \
    --data-urlencode "scheduleType=CRON" \
    --data-urlencode "scheduleConf=${cron}" \
    --data-urlencode "glueType=BEAN" \
    --data-urlencode "executorHandler=${jobHandler}" \
    --data-urlencode "executorParam=" \
    --data-urlencode "executorRouteStrategy=FIRST" \
    --data-urlencode "misfireStrategy=DO_NOTHING" \
    --data-urlencode "executorBlockStrategy=SERIAL_EXECUTION" \
    --data-urlencode "executorTimeout=0" \
    --data-urlencode "executorFailRetryCount=0" \
    --data-urlencode "triggerStatus=1" | python3 -m json.tool
  echo "-----------------------------------------------------------"
}

echo
echo "=== XXL-JOB：注册 3 个 Phase M/P 任务 ==="
echo

# P1：网关巡检 + SLO/僵尸策略告警（每 10 分钟）
creat_job "grayAuditJob"              "0 0/10 * * * ?"   "[Phase-P] 网关 rollout/status 巡检 + SLO 告警"        P1

# P0：审批流自愈（每 5 分钟，最关键）
creat_job "grayApprovalSelfHealJob"   "0 0/5 * * * ?"    "[Phase-M] EXECUTING 卡住修复 + EXECUTE_FAILED 自动重试"  P0

# P2：日度僵尸策略扫描（凌晨 3:30，骨架任务，空跑也 OK）
creat_job "grayStalePolicyEnforceJob" "0 30 3 * * ?"     "[Phase-P] 日度僵尸策略扫描（下一版激活）"               P2

echo
echo "✅ 3 个任务注册完成（上方 JSON 里的 id 就是 jobId，可在 Console 搜）"
echo "👉 生产建议：注册完立即按以下顺序各点一次『执行一次』验证："
echo "   1) grayApprovalSelfHealJob  → 日志应输出 'grayApprovalSelfHeal done: stuckExecutingFixed=0, autoRetried=0'"
echo "   2) grayAuditJob             → 日志应输出 'grayAuditJob done; policies scanned=' 数字正常"
echo "   3) grayStalePolicyEnforceJob → 允许显示 PLACEHOLDER no-op"
