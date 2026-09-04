# ======================================================================
# gate-values.tpl  —  V6.2.0 GRAY APPROVAL · 生产实施门禁变量填充表
# ----------------------------------------------------------------------
# 使用方法：
#   1) 复制本文件： cp gate-values.tpl gate-values.local
#   2) 编辑 gate-values.local：把每个 KEY 后面的 "" / <示例> 改成真实值。
#      (以 # 开头的行会被忽略；空值的 key 若未被 --allow-empty 显式允许 → 脚本将报错并终止)
#   3) 运行：   bash fill-placeholders.sh --values gate-values.local
#      产物输出到 filled/ 子目录（原文件不被修改）
#
# KEY 与 [[占位符]] 一一对应。例如 KEY=OP1-NAME → 替换文件中所有 [[OP1-NAME]]。
# 别名机制（内置自动处理，无需重复填）：
#   NACOS-NS       ↔  NACOS-NAMESPACE    （两值相同）
#   PREV-IMG       ↔  PREV-IMG-TAG       （两值相同）
# ======================================================================

# ---------------- 1. 交付物校验和 (2) ----------------
TGZ-MD5=""            # 最新 PRODUCTION-PKG.tgz 的 MD5（例：88b3a1ff…）
ZIP-MD5=""            # 附件 ZIP 的 MD5（归档当天计算后填入）

# ---------------- 2. 干系人（15 字段 · 7 类人） ----------------
# -- 技术/产品/值班 --
TL-NAME=""           # Tech Lead 姓名（例：张敏）
TL-ID=""             # Tech Lead 工号（例：100001）
PO-NAME=""           # Product Owner 姓名（例：李伟）
PO-ROLE=""           # Product Owner 角色（例：发布域产品负责人）
SRE-NAME=""          # SRE On-Call 姓名
SRE-PHONE=""         # SRE On-Call 手机号（例：138-0000-0001 或 +86 138…）
DBA-NAME=""          # DBA 姓名
DBA-ID=""            # DBA 工号
SEC-NAME=""          # 安全评审 姓名
SEC-ID=""            # 安全评审 工号
# -- 实施双主责 --
OP1-NAME=""          # 实施人 姓名（操作命令的人）
OP1-ID=""            # 实施人 工号
OP2-NAME=""          # 复核人 姓名（独立观察的人；不得与 OP1 同一人）
OP2-ID=""            # 复核人 工号

# ---------------- 3. 环境 / 拓扑 (5) ----------------
ENV=""               # 目标环境：Production / Pre-Prod / 容灾演练
CLUSTER=""           # 集群 / 机房：例 prod-cn-shanghai / ns: prod-lsc
NACOS-NS=""          # Nacos Namespace：例 prod-lsc-release
NS=""                # K8s Namespace 或 VM 主机组：例 prod-lsc
PREV-IMG=""          # 上一稳定版镜像 tag 或 VM tar 路径（Rollback 锚点，例：registry.lsc/lsc-release:v6.1.9-main-abc1234）

# ---------------- 4. 日期 / 时间窗口 (5) ----------------
WINDOW-START=""      # 发布窗口开始日期 YYYY-MM-DD（例：2026-09-05）
WINDOW-END=""        # 发布窗口结束日期 YYYY-MM-DD（同日发布可 = WINDOW-START）
DATE=""              # 签字当日（格式 YYYY-MM-DD）；留空 → 自动取当天
NOW=""               # Phase 0.2 实际时钟（格式：YYYY-MM-DD HH:MM）；留空 → 自动取当前时间
DATETIME=""          # 每次签字时间（格式 YYYY-MM-DD HH:MM）；留空 → 自动取当前时间
