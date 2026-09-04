# =========================================================================
#  Priority 1 / Step 2 附件 — 环境变量模板（env.tpl）
#  =========================================================================
#  使用方法：
#   VM/systemd:    cp env.tpl /etc/lsc-release-service.env
#                  然后 /etc/systemd/system/lsc-release-service.service 加 EnvironmentFile=/etc/lsc-release-service.env
#   K8s:           kubectl create secret generic lsc-release-env -n lsc-production \
#                    --from-literal=ALERT_FEISHU_WEBHOOK='...' --from-literal=MYSQL_PWD='...' ...
#                  Deployment.spec.template.spec.containers[].envFrom: secretRef: name=lsc-release-env
#  =========================================================================

# -------- 灰度审批（Phase M，对应 Nacos gray.approval.*）--------
GRAY_APPROVAL_DEFAULT_REQUIRED=2
GRAY_APPROVAL_ROLE=ROLE_RELEASE_ADMIN
GRAY_APPROVAL_RETRY_MAX=3
GRAY_APPROVAL_AUDIT_CHAIN=false
GRAY_APPROVAL_STALE_EXECUTING_SEC=120
GRAY_APPROVAL_RETRY_INTERVAL_SEC=600
GRAY_APPROVAL_IDLE_REMIND_H=24

# -------- 告警 --------
ALERT_CHANNEL=feishu
# 生产必改：飞书群机器人 webhook（含 sign secret 的完整 URL）
ALERT_FEISHU_WEBHOOK=__REPLACE_ME__
# 英文逗号分隔：飞书 user_id / 邮箱，P0/P1 告警会 @ 这些人
ALERT_RECEIVERS=ou_xxx,ou_yyy

# -------- 基线（沿用 v6.1 不变；生产已经配置好就别重复写）--------
# MYSQL_HOST=mysql-prod.lsc.local
# MYSQL_PORT=3306
# MYSQL_USER=lsc_release_app
# MYSQL_PWD=__REPLACE_ME__
# REDIS_HOST=redis-cluster.lsc.local
# REDIS_PORT=6379
# REDIS_PWD=__REPLACE_ME__
# NACOS_ADDR=nacos-prod.lsc.local:8848
# NACOS_NAMESPACE=prod
# SEATA_ADDR=seata-prod.lsc.local:8091
# XXL_JOB_ADMIN=http://xxljob-prod.lsc.local:9999/xxl-job-admin
# XXL_JOB_TOKEN=__REPLACE_ME__
