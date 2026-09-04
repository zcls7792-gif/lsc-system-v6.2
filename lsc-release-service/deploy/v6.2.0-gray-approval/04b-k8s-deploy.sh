#!/usr/bin/env bash
# =========================================================================
#  Priority 1 / Step 4b — K8s (Helm / kubectl set image) 金丝雀发布脚本
#  =========================================================================
set -euo pipefail

# ===== 生产必改 =====
NS="${NS:-lsc-production}"
DEPLOY="${DEPLOY:-lsc-release-service}"
CONTAINER="${CONTAINER:-lsc-release-service}"
REGISTRY="${REGISTRY:-registry.lsc.local}"
OLD_IMAGE_TAG="${OLD_IMAGE_TAG:-v6.2-AI-pre-gray-approval}"     # 上线前的 tag，用于回滚点
NEW_IMAGE_TAG="${NEW_IMAGE_TAG:-v6.2.0-gray-approval-m1}"        # 新镜像 tag
CANARY_REPLICAS="${CANARY_REPLICAS:-1}"                          # 1 Pod = ~20% 流量（按 5 Pod 总量估算）
CANARY_WATCH_MINUTES="${CANARY_WATCH_MINUTES:-10}"

log()  { echo "[$(date '+%F %T')] $*"; }
die()  { echo "[$(date '+%F %T')] ❌ $*" >&2; exit 1; }

command -v kubectl >/dev/null 2>&1 || die "kubectl 未安装 / 未在 PATH"

log "==== Step P1-4b-1：发布前状态快照（用于对比 & 回滚）===="
kubectl get deploy/"$DEPLOY" -n "$NS" -o yaml > /tmp/${DEPLOY}-${OLD_IMAGE_TAG}.yaml
kubectl get pods -n "$NS" -o wide | tee /tmp/${DEPLOY}-pre-pods.txt
kubectl rollout history deploy/"$DEPLOY" -n "$NS" | head -20

log "==== Step P1-4b-2：金丝雀 step 1 — 仅更新 Deployment.spec.template.spec.containers[].image，不立即全量 ===="
# 推荐用 kubectl patch 而非 set image，能更原子
SPEC_IMAGE="${REGISTRY}/${DEPLOY}:${NEW_IMAGE_TAG}"
log "新镜像：${SPEC_IMAGE}"
kubectl set image deploy/"$DEPLOY" "${CONTAINER}=${SPEC_IMAGE}" -n "$NS"
# 暂停滚动（这样只有 1 个新 Pod 起来）
kubectl rollout pause deploy/"$DEPLOY" -n "$NS" || true

log "==== Step P1-4b-3：等待金丝雀 Pod Ready（最多 3 分钟）===="
for i in $(seq 1 60); do
  NEW=$(kubectl get pods -n "$NS" -l app="$DEPLOY" -o json 2>/dev/null \
    | python3 -c "import sys,json
pods=json.load(sys.stdin).get('items',[])
new=[p for p in pods if any(x.get('image','').endswith(':${NEW_IMAGE_TAG}') for c in p.get('status',{}).get('containerStatuses',[]) for x in [c])]
ready=[p for p in new if all(s.get('ready',False) for s in p.get('status',{}).get('containerStatuses',[]))]
print(len(new), len(ready))" 2>/dev/null || echo "0 0")
  NEW_CNT=$(echo "$NEW" | awk '{print $1}')
  READY=$(echo "$NEW" | awk '{print $2}')
  log "金丝雀 Pod：已起=${NEW_CNT}  Ready=${READY}/${CANARY_REPLICAS}"
  [ "${READY:-0}" -ge "${CANARY_REPLICAS}" ] && break
  sleep 3
done
[ "${READY:-0}" -ge "${CANARY_REPLICAS}" ] || die "金丝雀 Pod 3 分钟内未 Ready"

log
log "==== Step P1-4b-4：金丝雀观察窗口 ${CANARY_WATCH_MINUTES} min ===="
log "👉 请新开终端并行执行以下命令："
log "   1) kubectl logs -f -n ${NS} deploy/${DEPLOY} --tail=200 | grep -E 'ERROR|GrayApproval'"
log "   2) 观察 Grafana gray-rollout Dashboard：JVM 堆 <75%，锁冲突 false=0"
log "   3) 执行 ./05-smoke.sh（BASE=内网 ClusterIP 或网关）"
log
log "⏳ 在 ${CANARY_WATCH_MINUTES} 分钟结束后，此脚本会自动询问是否全量；"
log "   如需立即回滚，新开终端执行：$(readlink -f "$0") --rollback"
log

# 观察等待（后台每 30s 打印一次 Pod 状态）
END=$((CANARY_WATCH_MINUTES * 60))
STEP=30
elapsed=0
while [ "$elapsed" -lt "$END" ]; do
  sleep "$STEP"
  elapsed=$((elapsed+STEP))
  # 检查重启次数
  RESTARTS=$(kubectl get pods -n "$NS" -l app="$DEPLOY" -o json 2>/dev/null | python3 -c "import sys,json
total=0
for p in json.load(sys.stdin).get('items',[]):
  for c in p.get('status',{}).get('containerStatuses',[]):
    total += int(c.get('restartCount',0))
print(total)" 2>/dev/null || echo "999")
  log "[观察 ${elapsed}/${END}s] 金丝雀重启次数（所有 Pod 之和）=${RESTARTS}"
  [ "${RESTARTS}" -lt 5 ] || { die "金丝雀 Pod 重启次数 ≥5 (${RESTARTS})，建议立即回滚：$(readlink -f "$0") --rollback"; }
done

# ========== 询问全量（此处不用 read，改为打印操作指令，因为脚本默认非交互；用户可执行 resume 来继续） ==========
log
log "✅ 金丝雀观察窗口通过！请决定："
log
log "  ▶ 继续全量（执行 resume）：kubectl rollout resume deploy/${DEPLOY} -n ${NS}  &&  kubectl rollout status deploy/${DEPLOY} -n ${NS}"
log "  ▶ 立即回滚                ：$(readlink -f "$0") --rollback"
log
log "全量完成后执行 ./05-smoke.sh（BASE=管理台生产域名 + 真实 TOKEN）"
log

# ----------- 回滚分支 -----------
if [ "${1:-}" = "--rollback" ]; then
  log "🚨 执行 K8s 回滚到 ${OLD_IMAGE_TAG}..."
  kubectl rollout resume deploy/"$DEPLOY" -n "$NS" 2>/dev/null || true
  kubectl rollout undo deploy/"$DEPLOY" -n "$NS"
  kubectl rollout status deploy/"$DEPLOY" -n "$NS"
  kubectl get pods -n "$NS" -l app="$DEPLOY" -o wide | head -20
  exit 0
fi
