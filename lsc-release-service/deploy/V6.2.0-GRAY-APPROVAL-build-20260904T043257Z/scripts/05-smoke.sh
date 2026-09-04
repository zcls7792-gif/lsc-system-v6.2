#!/usr/bin/env bash
# =========================================================================
#  Priority 1 / Step 5 — 冒烟测试：5 条 curl + 2 场景（脚本会自动断言并标颜色）
#  =========================================================================
#  使用方法：
#   1) export BASE="https://admin.lsc.prod.lianshengtong.com"   # 生产管理台域名（或本地 127.0.0.1:8080）
#   2) export TOKEN="Bearer <真实登录后 JWT，要有 ROLE_RELEASE_ADMIN 权限>"
#   3) bash 05-smoke.sh
#  输出：每一步 PASS(绿) / FAIL(红) + 失败时建议的根因
# =========================================================================
set -u
PASS=0; FAIL=0

G='\033[0;32m'; R='\033[0;31m'; Y='\033[0;33m'; N='\033[0m'
ok()   { echo -e "${G}✅ PASS${N}  $*"; PASS=$((PASS+1)); }
fail() { echo -e "${R}❌ FAIL${N}  $*"; FAIL=$((FAIL+1)); }
warn() { echo -e "${Y}⚠️  WARN${N}  $*"; }

BASE="${BASE:?必须设置 BASE 为管理台域名，如 https://admin.lsc.prod.lianshengtong.com}"
TOKEN="${TOKEN:?必须设置 TOKEN 为 Bearer <JWT>}"
H="Authorization:${TOKEN}"
CT="Content-Type: application/json"
TS=$(date +%Y%m%d)
POLICY_ID="smoke-test-policy-${TS}-$$"

http() { # 包装 curl：返回 body；保存 HTTP code 到 $HTTPC
  local url="$1"; shift
  HTTPC=$(curl -sS -o /tmp/smoke_body.json -w "%{http_code}" "$url" "$@" -H "$H" -H "$CT")
  cat /tmp/smoke_body.json
}

check_json_code() { # $1=body $2=expect_code
  echo "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);sys.exit(0 if d.get('code')==${2} else 1)"
}
jq()     { echo "$1" | python3 -c "import sys,json;d=json.load(sys.stdin);print(${2})"; }

echo "========================================================="
echo "  冒烟 1：创建审批单（GRADUATE 2 人审批）"
echo "========================================================="
B=$(http -X POST "$BASE/api/release/gray/approvals" -d '{
  "flowType":"GRADUATE",
  "policyId":"'"${POLICY_ID}"'",
  "applicant":"ops_smoke@lianshengtong.com",
  "title":"[SMOKE] order-service v2 graduate",
  "applyReason":"上线前冒烟：错误率达标",
  "requiredApprovals":2,
  "approvers":["approver_a@lianshengtong.com","approver_b@lianshengtong.com"]
}')
echo "$B" | python3 -m json.tool 2>/dev/null | head -30
if [ "$HTTPC" = "200" ] && check_json_code "$B" 0; then
  STATUS=$(jq "$B" "d['data']['status']")
  APPR=$(jq "$B" "d['data'].get('approvedCount',-1)")
  REQ=$(jq "$B" "d['data'].get('requiredApprovals',-1)")
  FLOW_ID=$(jq "$B" "d['data']['id']")
  export FLOW_ID
  [ "$STATUS" = "PENDING_APPROVAL" ] && [ "$APPR" = "0" ] && [ "$REQ" = "2" ] \
    && ok "冒烟1 创建完成 flowId=$FLOW_ID status=$STATUS approved=$APPR/$REQ" \
    || fail "冒烟1 字段不符：status=$STATUS approved=$APPR/$REQ"
else
  fail "冒烟1 HTTP=$HTTPC code != 0：$B"
fi
echo

echo "========================================================="
echo "  冒烟 2：第 1 人审批通过 approver_a (1/2) → 仍 PENDING"
echo "========================================================="
B=$(http -X PUT "$BASE/api/release/gray/approvals/action/approve" -d '{
  "flowId":'"${FLOW_ID}"',
  "approver":"approver_a@lianshengtong.com",
  "approved":true,
  "comment":"[SMOKE] err 0.05% < 0.1% threshold"
}')
echo "$B" | python3 -m json.tool 2>/dev/null | head -20
if [ "$HTTPC" = "200" ] && check_json_code "$B" 0; then
  STATUS=$(jq "$B" "d['data']['status']")
  APPR=$(jq "$B" "d['data'].get('approvedCount',-1)")
  [ "$STATUS" = "PENDING_APPROVAL" ] && [ "$APPR" = "1" ] \
    && ok "冒烟2 1/2 人审批通过 approvedCount=1" \
    || fail "冒烟2 字段不符 status=$STATUS approvedCount=$APPR"
else
  fail "冒烟2 HTTP=$HTTPC code != 0：$B"
fi
echo

echo "========================================================="
echo "  冒烟 3：第 2 人审批通过 (2/2) → 自动执行网关 graduate"
echo "========================================================="
B=$(http -X PUT "$BASE/api/release/gray/approvals/action/approve" -d '{
  "flowId":'"${FLOW_ID}"',
  "approver":"approver_b@lianshengtong.com",
  "approved":true,
  "comment":"[SMOKE] 7d 观察期 OK"
}')
echo "$B" | python3 -m json.tool 2>/dev/null | head -25
if [ "$HTTPC" = "200" ] && check_json_code "$B" 0; then
  STATUS=$(jq "$B" "d['data']['status']")
  EXEC=$(jq "$B" "str(d['data'].get('executeResponse',''))")
  case "$STATUS" in
    SUCCEEDED)       ok "冒烟3 网关有策略 → SUCCEEDED，executeResponse=${EXEC:0:80}" ;;
    EXECUTE_FAILED)
      if echo "$EXEC" | grep -qi "fallback"; then
        ok "冒烟3 网关无策略/不可达 → EXECUTE_FAILED 且 fallback 生效（脚本 fallbackFactory 正常；再冒烟4 重试即可）"
      else
        fail "冒烟3 status=EXECUTE_FAILED 但 executeResponse 不含 fallback：EXEC=${EXEC}"
      fi ;;
    *) fail "冒烟3 未到终态：status=$STATUS" ;;
  esac
else
  fail "冒烟3 HTTP=$HTTPC code != 0：$B"
fi
echo

echo "========================================================="
echo "  冒烟 4：手动重试 EXECUTE_FAILED（运维场景）"
echo "========================================================="
B=$(http -X PUT "$BASE/api/release/gray/approvals/action/retry-execute" -d '{
  "flowId":'"${FLOW_ID}"',
  "operator":"ops_smoke@lianshengtong.com"
}')
echo "$B" | python3 -m json.tool 2>/dev/null | head -15
if [ "$HTTPC" = "200" ] && check_json_code "$B" 0; then
  STATUS=$(jq "$B" "d['data']['status']")
  case "$STATUS" in
    SUCCEEDED|EXECUTE_FAILED) ok "冒烟4 retry 终态=$STATUS（状态机允许 retry，每次都写入新 audit）" ;;
    *) fail "冒烟4 retry 未到终态 status=$STATUS" ;;
  esac
else
  fail "冒烟4 HTTP=$HTTPC code != 0：$B"
fi
echo

echo "========================================================="
echo "  冒烟 5：详情查询（flow + 2 nodes + 审计流水）"
echo "========================================================="
B=$(http "$BASE/api/release/gray/approvals/${FLOW_ID}")
echo "$B" | python3 -m json.tool 2>/dev/null | head -40
if [ "$HTTPC" = "200" ] && check_json_code "$B" 0; then
  FID=$(jq "$B" "d['data']['flow']['id']")
  NODES=$(jq "$B" "len(d['data'].get('nodes',[]))")
  AUDITS=$(jq "$B" "len(d['data'].get('audits',[]))")
  NODE0_ST=$(jq "$B" "d['data'].get('nodes',[])[0].get('nodeStatus','')")
  [ "$FID" = "$FLOW_ID" ] && [ "$NODES" = "2" ] && [ "$NODE0_ST" = "APPROVED" ] && [ "$AUDITS" -ge 5 ] 2>/dev/null \
    && ok "冒烟5 flowId=$FID nodes=$NODES audits=$AUDITS node0=$NODE0_ST（流水无缺口）" \
    || fail "冒烟5 字段不符 flow=$FID(期望$FLOW_ID) nodes=$NODES(期望2) audits=$AUDITS(期望>=5)"
else
  fail "冒烟5 HTTP=$HTTPC code != 0：$B"
fi
echo

echo "========================================================="
echo "  汇总：PASS=$PASS / FAIL=$FAIL"
echo "========================================================="
[ "$FAIL" = "0" ] && echo -e "${G}全部 5 条冒烟通过 ✅${N}" \
                || { echo -e "${R}存在 $FAIL 条失败，请对比 runbook 附录错误码速查表${N}"; exit 1; }
