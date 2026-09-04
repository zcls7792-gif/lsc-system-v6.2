# ============================================================
# Phase L2：灰度版本错误率对比 · 观测手册
# 目标：在 Grafana + Loki/LogQL（或 ES DSL）下，对比 canary 与 baseline 两个版本的
# 错误率（5xx / Exception）、P95 延迟、核心交易成功率，并自动生成告警（阈值可由 ops 配置）。
# ============================================================

# ---------- 1. Loki / LogQL ----------
# ① 总请求数（每分钟）按版本切分
sum by (grayVersion, app) (
  rate({app=~"lsc-order-service|lsc-user-service|lsc-gateway"}
       | json
       | unwrap
       | __error__="" [1m]))
  or
count_over_time(
  {app=~"lsc-order-service|lsc-user-service"}
  | json | line_format `{{.message}}` [1m])

# ② 5xx 错误率（按 grayVersion 切分，百分比）
# 非灰度流量日志 grayVersion 为空 → 统一当 baseline
(sum by (app, grayVersion) (
  rate({app=~"lsc-order-service|lsc-user-service"} | json
       | status_code >= 500 [5m]))
 /
 clamp_min(sum by (app, grayVersion) (
   rate({app=~"lsc-order-service|lsc-user-service"} | json [5m])), 1))
 * 100

# ③ Exception 数量（MDC 中含 grayPolicyId 的异常堆栈）
sum by (app, grayPolicyId, grayVersion) (
  rate({app=~"lsc-order-service|lsc-user-service"} | json
       | level =~ "ERROR|WARN"
       | stacktrace !="" [5m]))

# ④ 某策略 canary vs baseline 错误率 Delta（判断是否应触发自动回滚）
# canary > baseline + 3% → Alert to Slack（Ops 面板：需 graduate 前通过这一关）
(
  sum by (app, grayPolicyId) (
    rate({app=~"lsc-order-service|lsc-user-service"} | json | grayVersion="canary" | status_code >= 500 [5m]))
  / clamp_min(sum by (app, grayPolicyId) (rate({app=~"lsc-order-service|lsc-user-service"} | json | grayVersion="canary" [5m])),1)
) - (
  sum by (app, grayPolicyId) (
    rate({app=~"lsc-order-service|lsc-user-service"} | json | grayVersion="baseline" | status_code >= 500 [5m]))
  / clamp_min(sum by (app, grayPolicyId) (rate({app=~"lsc-order-service|lsc-user-service"} | json | grayVersion="baseline" [5m])),1)
)

# ---------- 2. Prometheus / PromQL ----------
# 前提：通过 lsc-gateway /summary 提供的聚合指标，或用 Micrometer @Timed / Counter
#       暴露 http_server_requests_seconds{gray_policy_id,gray_version,...}

# HTTP 5xx 错误率 (gateway 全局 per policy)
sum by (gray_policy_id, gray_version) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
/
sum by (gray_policy_id, gray_version) (rate(http_server_requests_seconds_count[5m]))

# canary 与 baseline 的请求比（should ≈ configured weight + 规则强制偏差；偏差>1.5x 告警）
(
  sum by (gray_policy_id) (rate(http_server_requests_seconds_count{gray_version="canary"}[5m]))
  / clamp_min(sum by (gray_policy_id) (rate(http_server_requests_seconds_count{gray_version="baseline"}[5m])),1)
) > bool 1.5

# Redis 共享统计（由 lsc-gateway /summary 暴露为指标：gray_cluster_hits_total）
# canary ratio 实际 vs 配置权重 diff
(
  sum(rate(gray_cluster_hits_total{gray_version="canary"}[5m])) by (policy_id)
  / clamp_min(sum(rate(gray_cluster_hits_total[5m])) by (policy_id),1)
) - on(policy_id) group_left()
  lsc_gray_config_canary_weight_percent
  > bool 0.05

# ---------- 3. 聚合 SQL（审计用，存到 `gray_policy_audit` 汇总表时的样例）----------
# 每天每策略按版本聚合 5xx/4xx/总请求/P95 延迟
SELECT
  DATE_FORMAT(request_at, '%Y-%m-%d %H:00') AS `hour`,
  app,
  policy_id AS gray_policy_id,
  version   AS gray_version,
  COUNT(*)                                                  AS total_requests,
  SUM(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END)      AS err_5xx,
  SUM(CASE WHEN status_code BETWEEN 400 AND 499 THEN 1 ELSE 0 END) AS err_4xx,
  ROUND(100.0 * SUM(CASE WHEN status_code >= 500 THEN 1 ELSE 0 END) / NULLIF(COUNT(*),0), 2) AS err_5xx_pct,
  ROUND(CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(CAST(SUBSTRING_INDEX(GROUP_CONCAT(latency_ms ORDER BY pos SEPARATOR ',')  AS CHAR),',', 0.95*COUNT(*)+1),',',-1) AS SIGNED)/1000.0, 2) AS p95_latency_sec
FROM lsc_gray_traffic_log
WHERE request_at >= NOW() - INTERVAL 3 DAY
GROUP BY 1,2,3,4
ORDER BY 1 DESC, total_requests DESC;

# ---------- 4. Grafana 面板变量 ----------
#   app: label_values(lsc_order_service_requests_total, app)
#   policy_id: label_values(http_server_requests_seconds_count, gray_policy_id)
#   version: Custom : all,canary,baseline
#   timeFrom: now-6h / timeTo: now
#
# ---------- 5. 告警规则样例（Alertmanager / Grafana Alerting）----------
#   Alert "Canary error rate drift"
#     expr: (canary_5xx_pct - baseline_5xx_pct) > 2  AND canary_total > 500
#     for: 5m
#     labels: { severity: warning }
#     annotations:
#       summary: "灰度策略 {{$labels.gray_policy_id}} canary 错误率领先 baseline {{$value | printf \"%.2f\"}}%"
#       runbook_url: docs/gray-release-ops-handbook.md#自动回滚

# ---------- 6. SLO（策略毕业门槛）----------
#   canary_5xx ≤ baseline_5xx + 0.5%  （连续 2h）
#   canary_p95 ≤ baseline_p95 × 1.3    （连续 2h）
#   core_order_create_success_rate_canary ≥ 99.9%
#   → 满足时触发 lsc-release-service 审批自动通过，调用 lsc-gateway POST /policies/{id}/graduate
