#!/usr/bin/env bash
# =========================================================================
#  Priority 1 / Step 4a — VM (systemd) 发布脚本（含自动备份 + 回滚）
#  =========================================================================
#  前置：
#   - 目标机：systemd + 已存在 /etc/systemd/system/lsc-release-service.service
#   - 服务名固定：lsc-release-service
#   - Jar 路径：/opt/lsc-release-service.jar（与现有基线一致）
#   - 环境文件：/etc/lsc-release-service.env（env.tpl 填好）
#   - 发布机上先 scp / rsync 本目录下的 lsc-release-service.jar 到 /opt/lsc-release-service.jar.new
#  =========================================================================
set -euo pipefail

OLD_TAG="${OLD_TAG:-v6.1}"
NEW_TAG="${NEW_TAG:-v6.2.0-gray-approval-m1}"
SVC="lsc-release-service"
JAR_PATH="/opt/lsc-release-service.jar"
NEW_JAR="${JAR_PATH}.new"

log()  { echo "[$(date '+%F %T')] $*"; }
die()  { echo "[$(date '+%F %T')] ❌ $*" >&2; exit 1; }

[ -f "$NEW_JAR" ] || die "未找到新 Jar: $NEW_JAR (先 scp 本目录 target/*.jar → $NEW_JAR)"

log "==== Step P1-4a-1：备份现有 Jar 到 ${JAR_PATH}.bak.${OLD_TAG} ===="
sudo cp -a "$JAR_PATH" "${JAR_PATH}.bak.${OLD_TAG}"
ls -lh "${JAR_PATH}.bak.${OLD_TAG}" "$NEW_JAR"
log "新 Jar checksum："
sha256sum "$NEW_JAR"

log "==== Step P1-4a-2：停止服务（预留 30s 优雅停机）===="
sudo systemctl stop "$SVC"
sleep 2
# 确认真的停了
if systemctl is-active --quiet "$SVC"; then
  sudo systemctl kill --kill-who=main --signal=SIGTERM "$SVC"
  sleep 5
fi
! systemctl is-active --quiet "$SVC" || die "30s 内未停止，手动 kill"

log "==== Step P1-4a-3：替换 Jar ===="
sudo mv "$NEW_JAR" "$JAR_PATH"
sudo chown root:root "$JAR_PATH"
sudo chmod 0644 "$JAR_PATH"

log "==== Step P1-4a-4：启动服务 + 健康观察 ===="
sudo systemctl start "$SVC"
for i in $(seq 1 30); do
  STATE=$(systemctl is-active "$SVC" 2>/dev/null || true)
  [ "$STATE" = "active" ] && break
  sleep 2
  log "启动中... ($i/30)"
done
systemctl is-active --quiet "$SVC" || { sudo journalctl -u "$SVC" -n 80 --no-pager; die "服务未 active，见上日志"; }

log "==== Step P1-4a-5：关键日志校验（无 ERROR / 有 Started GrayApproval 字样）===="
sleep 5
LOGS=$(sudo journalctl -u "$SVC" -n 200 --no-pager || true)
echo "$LOGS" | grep -iE "GrayApprovalMetrics|GrayApprovalServiceImpl|Started .+ in [0-9]+ sec" | head -10 || true
ERR=$(echo "$LOGS" | grep -ciE " ERROR |Exception|Caused by" || true)
log "过去 200 行日志 ERROR/Exception 行数：${ERR}"
[ "$ERR" -le 3 ] || die "ERROR 日志过多（${ERR} 行），建议立即回滚"

log "==== Step P1-4a-6：5 条本地健康检测（用 127.0.0.1:8080 或管理端口） ===="
MGMT="${MGMT_URL:-http://127.0.0.1:8080/actuator/health}"
for i in 1 2 3 4 5 6 7 8; do
  CODE=$(curl -sS -o /dev/null -w "%{http_code}" "$MGMT" 2>/dev/null || echo "000")
  [ "$CODE" = "200" ] && { log "✅ health endpoint OK"; break; }
  sleep 3
done
[ "$CODE" = "200" ] || log "⚠️  健康端点没返回 200（可能禁用了 actuator），跳过该断言"

log
log "✅ VM 发布完成，建议接下来："
log "   1) 观察 10 分钟日志：journalctl -u lsc-release-service -f | grep -E 'ERROR|GrayApproval'"
log "   2) 再执行 05-smoke.sh 跑冒烟"
log "   3) 如需回滚，直接运行：$(readlink -f "$0") --rollback ${OLD_TAG}"
log
log "==== 回滚用法：$(basename "$0") --rollback <旧bak版本号> ===="

# ----------- 回滚分支 -----------
if [ "${1:-}" = "--rollback" ]; then
  BAK_TAG="${2:-$OLD_TAG}"
  log "🚨 执行回滚到 ${BAK_TAG}..."
  sudo systemctl stop "$SVC" || true
  sudo cp -a "${JAR_PATH}.bak.${BAK_TAG}" "$JAR_PATH"
  sudo systemctl start "$SVC"
  sleep 10
  systemctl is-active --quiet "$SVC" && log "✅ 回滚后服务已 active" || die "回滚后服务没起来"
  exit 0
fi
