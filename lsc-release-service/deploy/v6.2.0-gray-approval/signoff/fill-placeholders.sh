#!/usr/bin/env bash
# ==============================================================================
# fill-placeholders.sh — V6.2.0 Gray Approval · Gate Sign-Off 批量替换脚本
# ------------------------------------------------------------------------------
# 用法：
#   # 1) 先生成本地值表（gate-values.tpl → gate-values.local）
#   cp gate-values.tpl gate-values.local && vi gate-values.local
#
#   # 2) 运行
#   bash fill-placeholders.sh --values gate-values.local              # 严格模式：缺任何值即报错
#   bash fill-placeholders.sh --values gate-values.local --allow-empty # 允许留空（会填 "TODO-<KEY>" 标记）
#   bash fill-placeholders.sh --demo                                  # 一键填充演示值（快速看 PDF 效果）
#
# 行为（经验 1009788/753718 合规）：
#   ✓ 绝对不修改原文件。所有产物输出到 ./filled/<name>.filled.<ext>
#   ✓ 不用 sed，用 Python str.replace（无正则/转义问题，兼容 HTML/MD 特殊字符）
#   ✓ 自动处理 2 对 KEY 别名：NACOS-NS ↔ NACOS-NAMESPACE、PREV-IMG ↔ PREV-IMG-TAG
#   ✓ 自动为 DATE/NOW/DATETIME 填充今天日期（若用户未显式填）
#   ✓ 执行末尾给出 "剩余未替换占位符数量 = 0 / N" 统计，为 0 才算成功
#   ✓ 输出目录不存在时自动 mkdir -p
# ==============================================================================
set -euo pipefail

SDIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SDIR"

# ---------- 参数解析 ----------
VALUES_FILE=""
ALLOW_EMPTY=0
DEMO=0
while [ $# -gt 0 ]; do
    case "$1" in
        --values)         VALUES_FILE="$2"; shift 2 ;;
        --values=*)       VALUES_FILE="${1#*=}"; shift ;;
        --allow-empty)    ALLOW_EMPTY=1; shift ;;
        --demo)           DEMO=1; shift ;;
        -h|--help)
            sed -n '2,22p' "$0"; exit 0 ;;
        *) echo "未知参数: $1"; echo "见 --help"; exit 2 ;;
    esac
done

if [ "$DEMO" -eq 0 ] && [ -z "$VALUES_FILE" ]; then
    echo "❌ 缺少 --values <文件> 或 --demo。使用 --help 查看用法。" >&2; exit 2
fi

# ---------- 源文件（不改原物） ----------
SRC_MD="$SDIR/生产实施门禁-V6.2.0-GRAY-APPROVAL-签字版.md"
SRC_HTML="$SDIR/生产实施门禁-V6.2.0-GRAY-APPROVAL-可打印PDF版.html"
OUTDIR="$SDIR/filled"
mkdir -p "$OUTDIR"

for f in "$SRC_MD" "$SRC_HTML"; do
  if [ ! -f "$f" ]; then echo "❌ 找不到源文件：$f"; exit 3; fi
done

echo "=========================================================="
echo " V6.2.0 GRAY APPROVAL · Gate 占位符批量替换"
echo "  输出目录  : $OUTDIR"
[ -n "$VALUES_FILE" ] && echo "  值表文件  : $VALUES_FILE"
[ "$DEMO" -eq 1 ]            && echo "  模式      : --demo（内置演示姓名/工号/时间）"
[ "$ALLOW_EMPTY" -eq 1 ]     && echo "  空值策略  : 允许 → 填 TODO-<KEY>"
echo "=========================================================="

# ---------- 调用 Python 引擎 ----------
export SIGNOFF_SRC_MD="$SRC_MD"
export SIGNOFF_SRC_HTML="$SRC_HTML"
export SIGNOFF_OUTDIR="$OUTDIR"
export SIGNOFF_VALUES="$VALUES_FILE"
export SIGNOFF_DEMO="$DEMO"
export SIGNOFF_ALLOW_EMPTY="$ALLOW_EMPTY"

python3 << 'PYEOF'
import os, re, sys
from datetime import datetime

src_md = os.environ["SIGNOFF_SRC_MD"]
src_html = os.environ["SIGNOFF_SRC_HTML"]
outdir = os.environ["SIGNOFF_OUTDIR"]
values_file = os.environ.get("SIGNOFF_VALUES", "") or None
demo = os.environ.get("SIGNOFF_DEMO", "0") == "1"
allow_empty = os.environ.get("SIGNOFF_ALLOW_EMPTY", "0") == "1"

def today():  return datetime.now().strftime("%Y-%m-%d")
def now():    return datetime.now().strftime("%Y-%m-%d %H:%M")

# ========== 1) 构建值表 ==========
values = {}

if demo:
    values = {
        "TGZ-MD5": "88b3a1ff3d6095b2c4e9f87261aaab14 (演示，请以实际 tgz md5sum 为准)",
        "ZIP-MD5": "f2c8d901e77b428807cafbe1390a33aa (演示)",
        "TL-NAME": "张敏", "TL-ID": "100001",
        "PO-NAME": "李伟", "PO-ROLE": "发布域 · 产品负责人",
        "SRE-NAME": "王浩", "SRE-PHONE": "138-0000-0001",
        "DBA-NAME": "赵宇", "DBA-ID": "300007",
        "SEC-NAME": "陈曦", "SEC-ID": "400023",
        "OP1-NAME": "周一", "OP1-ID": "200045",
        "OP2-NAME": "吴桐", "OP2-ID": "200188",
        "ENV": "生产 (Production)",
        "CLUSTER": "prod-cn-shanghai / ns: prod-lsc",
        "NACOS-NS": "prod-lsc-release",
        "NS": "prod-lsc",
        "PREV-IMG": "registry.lsc/lsc-release:v6.1.9-main-abc1234",
        "WINDOW-START": today(), "WINDOW-END": today(),
        "DATE": today(), "NOW": now(), "DATETIME": now(),
    }
elif values_file:
    with open(values_file) as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"): continue
            if "=" not in line:
                print(f"⚠️  忽略值表无法解析的行：{line!r}", file=sys.stderr); continue
            k, v = line.split("=", 1)
            k = k.strip()
            v = v.strip()
            # 去掉首尾引号（若用户填了）
            if len(v) >= 2 and v[0] == v[-1] and v[0] in ('"', "'"):
                v = v[1:-1]
            values[k] = v
else:
    print("❌ 既无 --demo 也无 --values。请择其一。", file=sys.stderr); sys.exit(2)

# ========== 2) 自动默认 + 别名同步 ==========
auto_filled = []
for k, gen in [("DATE", today()), ("NOW", now()), ("DATETIME", now())]:
    if k not in values or not values[k]:
        values[k] = gen; auto_filled.append(f"{k}→{gen}")
# 别名：NACOS-NS ↔ NACOS-NAMESPACE  /  PREV-IMG ↔ PREV-IMG-TAG
if values.get("NACOS-NS") and not values.get("NACOS-NAMESPACE"): values["NACOS-NAMESPACE"] = values["NACOS-NS"]
if values.get("NACOS-NAMESPACE") and not values.get("NACOS-NS"): values["NACOS-NS"] = values["NACOS-NAMESPACE"]
if values.get("PREV-IMG") and not values.get("PREV-IMG-TAG"): values["PREV-IMG-TAG"] = values["PREV-IMG"]
if values.get("PREV-IMG-TAG") and not values.get("PREV-IMG"): values["PREV-IMG"] = values["PREV-IMG-TAG"]

# ========== 3) 空值处理（严格模式 报错） ==========
# 先列出所有占位符（从两份源里取）
pat = re.compile(r"\[\[([A-Z0-9\-]+)\]\]")
with open(src_md) as f: md_keys = set(pat.findall(f.read()))
with open(src_html) as f: html_keys = set(pat.findall(f.read()))
all_keys = sorted(md_keys | html_keys)

missing = []
for k in all_keys:
    if k not in values or values[k] == "":
        if allow_empty:
            values[k] = f"TODO-{k}"
        else:
            missing.append(k)
if missing:
    print("❌ 以下 KEY 未在值表中提供（留空将导致门禁出现 [[xxx]]）。", file=sys.stderr)
    print("   → 解决：1) 在 .local 文件中填入；或 2) 追加 --allow-empty（生成 TODO-<KEY> 占位）。", file=sys.stderr)
    for m in missing: print(f"      · {m}", file=sys.stderr)
    sys.exit(4)

# ========== 4) 执行替换（按 KEY 长度降序，避免 [[KEY-X]] 被 [[KEY]] 先替换截断） ==========
sorted_keys = sorted(values.keys(), key=len, reverse=True)
replaced_total = 0
files = [(src_md, os.path.join(outdir, "生产实施门禁-V6.2.0-GRAY-APPROVAL-签字版.filled.md")),
         (src_html, os.path.join(outdir, "生产实施门禁-V6.2.0-GRAY-APPROVAL-可打印PDF版.filled.html"))]
for src, out in files:
    with open(src) as f: s = f.read()
    before = s.count("[[")
    for k in sorted_keys:
        token = f"[[{k}]]"
        if token not in s: continue
        cnt = s.count(token)
        s = s.replace(token, values[k])
        replaced_total += cnt
    after = s.count("[[")
    with open(out, "w") as f: f.write(s)
    rel_out = os.path.relpath(out, os.path.dirname(src_md))
    print(f"✔  生成 {rel_out}  (替换前 [[ {before} → 替换后 [[ {after} )")

# ========== 5) 总结 ==========
print()
print(f"✅ 完成。共替换 {replaced_total} 处。未替换 [[ ]] 标记剩余：")
remain = 0
for _, out in files:
    with open(out) as f: s = f.read()
    hits = re.findall(r"\[\[[A-Z0-9\-]+\]\]", s)
    remain += len(hits)
    if hits:
        print(f"  ⚠️  {os.path.basename(out)}: 剩 {len(hits)} 处 → {hits[:5]}")
if auto_filled:
    print(f"ℹ️  自动默认日期填充：{'; '.join(auto_filled)}")
if remain == 0:
    print("🎉 零残留 — 产物可直接浏览器打开 filled/*.filled.html → Ctrl+P 打印 PDF。")
    print("   filled/*.filled.md 可粘贴到 Feishu Doc / Confluence 作为电子版附件。")
else:
    print(f"❌ 仍剩 {remain} 处未替换 → 请检查上述 KEY 并填充后重新运行", file=sys.stderr)
    sys.exit(5)
PYEOF
status=$?
echo
if [ "$status" -eq 0 ]; then
  echo "------------------------------------------"
  echo " filled/ 目录（交付本地打开即可用）:"
  ls -1 "$OUTDIR" | sed 's/^/   · /'
fi
exit $status
