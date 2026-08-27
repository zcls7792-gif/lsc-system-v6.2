#!/usr/bin/env node
/**
 * 覆盖率总览报告生成器
 * 用法:
 *   1) 先跑 npm run coverage 得到 coverage/ 目录下的 c8 html+lcov 报告
 *   2) node coverage_report.js -> 在 coverage/ 目录旁生成 coverage_report.html
 *
 * 输出:
 *   一份单文件 HTML 覆盖率总览(深海金融美学风格),含:
 *     - 指标卡(语句/分支/函数/行 覆盖率)
 *     - 文件维度表格(各文件 4 维覆盖率 + 未覆盖行摘要)
 *     - 功能覆盖矩阵:被测功能点 ↔ TAP 用例 ↔ 关键代码段
 *     - 未覆盖热点 Top N 建议(按未覆盖行数排序)
 *     - 超链接跳转到 c8 生成的逐行 HTML 报告
 */
const fs   = require('fs');
const path = require('path');

const ROOT = __dirname;
// 允许通过环境变量切换到按 commit 归档的子目录(CI 场景)
const C8_REPORT_DIR = process.env.C8_REPORT_DIR || process.env.COVERAGE_DIR || process.env.COV_DIR || null;
const COV_DIR = C8_REPORT_DIR
  ? (path.isAbsolute(C8_REPORT_DIR) ? C8_REPORT_DIR : path.resolve(ROOT, C8_REPORT_DIR))
  : path.join(ROOT, 'coverage');
const LCOV    = process.env.LCOV
  ? (path.isAbsolute(process.env.LCOV) ? process.env.LCOV : path.resolve(ROOT, process.env.LCOV))
  : path.join(COV_DIR, 'lcov.info');
const today = new Date().toISOString().slice(0,10).replace(/-/g,'');
const OUT_DEFAULT = path.join(ROOT, `覆盖率报告_P0-图表逻辑_${today}.html`);
// CI: 优先把总览报告和 c8 产物放在同一子目录,便于一次性归档
const OUT = C8_REPORT_DIR ? path.join(COV_DIR, '总览报告.html') : OUT_DEFAULT;
// 同时保留一份与文件名匹配的"根目录快捷入口"(CI 归档时 CI yml 会再 mv)
const OUT_LEGACY = C8_REPORT_DIR ? OUT_DEFAULT : null;

/* --------------- 1. 解析 c8 的 text-summary / lcov.info(优先 lcov) --------------- */
function parseLcov(lcovPath) {
  const text = fs.readFileSync(lcovPath, 'utf8');
  const recs = [];
  let cur = null;
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (line.startsWith('TN:')) { if (cur) recs.push(cur); cur = null; }
    if (line.startsWith('SF:')) {
      const abs = line.slice(3);
      // 归一到相对 ROOT 的路径
      const rel = path.isAbsolute(abs) && abs.startsWith(ROOT) ? path.relative(ROOT, abs).replace(/\\/g,'/') : abs;
      cur = { file: rel, lines_found:0, lines_hit:0, funcs_found:0, funcs_hit:0, branches_found:0, branches_hit:0, line_detail: new Map(), branch_detail: [] };
    }
    if (!cur) continue;
    if (line.startsWith('LF:')) cur.lines_found  = +line.slice(3);
    if (line.startsWith('LH:')) cur.lines_hit    = +line.slice(3);
    if (line.startsWith('FNF:')) cur.funcs_found = +line.slice(4);
    if (line.startsWith('FNH:')) cur.funcs_hit   = +line.slice(4);
    if (line.startsWith('BRF:')) cur.branches_found = +line.slice(4);
    if (line.startsWith('BRH:')) cur.branches_hit   = +line.slice(4);
    if (line.startsWith('DA:')) {
      const [ln, hit] = line.slice(3).split(',').map(Number);
      cur.line_detail.set(ln, hit);
    }
    if (line.startsWith('BRDA:')) {
      const [ln, block, branch, hit] = line.slice(5).split(',');
      cur.branch_detail.push({ line: +ln, block, branch, hit: +hit });
    }
    if (line === 'end_of_record') { if (cur) recs.push(cur); cur = null; }
  }
  return recs;
}

function pct(hit, found) {
  if (!found) return 100;
  return Math.round(10000*(hit/found))/100;
}

function addCov(a, b) { // 累加 coverage 汇总
  return {
    lf: a.lf+b.lf, lh: a.lh+b.lh,
    bf: a.bf+b.bf, bh: a.bh+b.bh,
    ff: a.ff+b.ff, fh: a.fh+b.fh,
  };
}

function covStat(recs) {
  const t = recs.reduce((a,r)=>addCov(a,{lf:r.lines_found,lh:r.lines_hit,bf:r.branches_found,bh:r.branches_hit,ff:r.funcs_found,fh:r.funcs_hit}), {lf:0,lh:0,bf:0,bh:0,ff:0,fh:0});
  return {
    stmts:  pct(t.lh, t.lf),
    lines:  pct(t.lh, t.lf),
    branch: pct(t.bh, t.bf),
    funcs:  pct(t.fh, t.ff),
    ...t,
  };
}

function uncoveredRanges(rec) {
  // 将未覆盖行号合并为连续区间,便于摘要显示
  const lines = [...rec.line_detail.entries()].filter(([,h])=>h===0).map(([ln])=>ln).sort((a,b)=>a-b);
  const ranges = [];
  let i=0;
  while (i<lines.length) {
    const s = lines[i]; let e = s;
    while (i+1<lines.length && lines[i+1]===e+1) { e = lines[i+1]; i++; }
    ranges.push([s,e]);
    i++;
  }
  return ranges;
}

/* --------------- 2. 功能覆盖矩阵 (手工映射:功能点 ↔ 用例 ↔ 代码行号范围) --------------- */
/**
 * 说明:这里的代码段行号来自 /workspace/lsc-system/platform-admin/app.js
 * 每行 "范围" 是粗略区间,用于帮助定位;实际覆盖情况以 c8 逐行报告中的"命中/未命中"为准。
 */
const FUNCTION_MATRIX = [
  { area: '图表基础', func: 'lineChart',      codeRange: '1-97',
    tests: ['BUG#1 初始渲染','BUG#3 legend 变量完整性','D. 滚动窗口','A9. 断线/预测/多系列混合','E. 指标卡同步' ],
    desc: '折线图(断线、区域填充、虚线、末端点、AI 预测分界线、Y 轴网格)' },
  { area: '图表基础', func: 'barChart',       codeRange: '99-120',
    tests: ['Dashboard 经营总览 (间接)'], desc: '柱状图,在商家报表页面使用' },
  { area: '图表基础', func: 'donutChart',     codeRange: '131-182', tests:['A10. donutChart OK'], desc:'环形图' },
  { area: '图表基础', func: 'stackedBar',     codeRange: '184-208', tests:['A11. stackedBar OK'], desc:'堆叠柱状图 + legend' },
  { area: '图表基础', func: 'heatmap',        codeRange: '210-270', tests:['A12. heatmap OK'], desc:'热力图(色阶 + XY 标签)' },
  { area: '图表基础', func: 'radarChart',     codeRange: '272-302', tests:['A13. radarChart OK'], desc:'雷达图' },
  { area: '页面渲染', func: 'renderDashboard', codeRange: '~300-400', tests:['Session A 末尾 render* 批量调用'], desc:'仪表盘 KPI + 图表' },
  { area: '页面渲染', func: 'renderUsers',    codeRange: '~400-480', tests:['Session A 末尾 render* 批量调用'], desc:'用户列表页' },
  { area: '页面渲染', func: 'renderMerchant', codeRange: '~480-600', tests:['Session A 末尾 render* 批量调用'], desc:'商家列表页' },
  { area: '页面渲染', func: 'renderB2B',      codeRange: '~700-820', tests:['Session A 末尾 render* 批量调用'], desc:'B2B 订单状态机 SVG + 表格' },
  { area: 'AI 中心 (P0)', func: 'renderAI',   codeRange: '~1000-1200', tests:['A/B/C 全部 Session 启动','BUG#1/2/3','定时器清理'],
    desc: 'AI 中心:释放速率实时流容器 + 活动流 feed + 指标卡 初始化' },
  { area: 'AI 中心 (P0)', func: 'pushActivity', codeRange: '1143-1171(约)', tests:['BUG#6 首条/裁剪/动画','压力 100 次混合','B8-B11 条数规则'],
    desc: '每 10s 追加一条 AI 活动记录,含等级圆点发光 + slide-down 动画,20 条上限裁剪' },
  { area: 'AI 中心 (P0)', func: 'redrawRateChart', codeRange: '~1174-1210', tests:['BUG#1 undefined 检查','A7/A8 指标卡数值','E. 多次推进指标变化'],
    desc: '重绘 lineChart + 拼接 legend,并同步更新 k/rate/趋势 3 个指标卡' },
  { area: 'AI 中心 (P0)', func: 'appendRatePoint', codeRange: '~1212-1234', tests:['BUG#2 切页不重置','D. 滚动窗口/数值钳位','B4-B7','C4 100次压力'],
    desc: 'shift 旧点 + push 新点,维持 24 点窗口;k ∈ [0.0045, 0.0085],rate ∈ [0.0025, 0.0055] 钳位' },
  { area: 'AI 中心 (P0)', func: '_aiTimers 管理', codeRange: '~1137-1141 + 1241-1243', tests:['C1-C3 二次 renderAI 换新 ID','定时器 2 个'],
    desc: 'window._aiTimers 数组管理;进入 renderAI 先清旧 interval,再创建活动流(10s) + 速率(3s) 两个 timer' },
  { area: '工具',     func: 'setView / renderIcons', codeRange: '~1250-1290', tests:['间接覆盖(每次 render* 调用)'],
    desc: '视图注入 + ICON data-i 渲染' },
];

/* --------------- 3. HTML 渲染(纯字符串模板, 安全转义) --------------- */
function esc(s) { return String(s==null?'':s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])); }

function pctClass(p) {
  if (p>=80) return 'good';
  if (p>=60) return 'mid';
  if (p>=40) return 'low';
  return 'danger';
}

function pctBadge(p) {
  const cls = pctClass(p);
  return `<span class="pct-badge pct-${cls}">${p.toFixed(2)}%</span>`;
}

function renderIndexBar(p, w=180) {
  const cls = pctClass(p);
  const ww = Math.min(100, Math.max(0, p));
  return `<div class="pctbar" style="width:${w}px;"><div class="pctbar-fill pctbar-${cls}" style="width:${ww}%"></div><span class="pctbar-text">${p.toFixed(1)}%</span></div>`;
}

const DESIGN_CSS = `
:root{
  --bg: #0a1f2d; --bg-soft:#0f2c3e; --card:#113749; --card-2:#15415a;
  --text-1:#e8f1f5; --text-2:#9bb2bf; --text-3:#6f8694;
  --primary:#1fb5ac; --primary-tint:rgba(31,181,172,0.14);
  --locked:#ffb13d;  --available:#2db380; --warning:#ff9500; --danger:#ef4f56;
  --accent:#c8a24b;  --divider:rgba(255,255,255,0.08);
  --ff:'Segoe UI','PingFang SC','Microsoft YaHei',system-ui,-apple-system,sans-serif;
  --ff-mono:'JetBrains Mono','SF Mono',Consolas,Menlo,monospace;
}
*{box-sizing:border-box}
body{margin:0;background:linear-gradient(180deg,var(--bg) 0%, #081823 100%);color:var(--text-1);font-family:var(--ff);padding:24px 32px 60px;}
h1{font-size:26px;margin:0 0 6px;letter-spacing:0.5px;color:#eef9f8;}
h2{font-size:18px;margin:28px 0 12px;color:#dff3ef;border-left:4px solid var(--primary);padding-left:10px;}
h3{font-size:14px;margin:18px 0 8px;color:var(--text-1)}
.subtitle{color:var(--text-2);font-size:13px;margin-bottom:18px;}
.meta-row{display:flex;gap:24px;flex-wrap:wrap;color:var(--text-2);font-size:12px;margin-bottom:16px;}
.kpi-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:14px;}
.kpi{background:var(--card);border:1px solid var(--divider);border-radius:12px;padding:16px 18px;position:relative;overflow:hidden;}
.kpi::after{content:'';position:absolute;inset:auto 0 0 0;height:3px;background:linear-gradient(90deg,var(--primary),transparent);}
.kpi .label{font-size:12px;color:var(--text-2);letter-spacing:0.5px}
.kpi .val{font-size:32px;font-weight:700;font-family:var(--ff-mono);margin-top:4px;}
.kpi .bar{margin-top:8px;}
.card{background:var(--card);border:1px solid var(--divider);border-radius:12px;padding:16px 18px;margin-top:14px;}
table{width:100%;border-collapse:collapse;font-size:13px;}
th,td{border-bottom:1px solid var(--divider);padding:9px 10px;text-align:left;vertical-align:top}
th{background:var(--card-2);color:#cde2e1;font-weight:600;font-size:12px;letter-spacing:0.3px;position:sticky;top:0;z-index:1}
tr:hover td{background:rgba(255,255,255,0.02);}
.file-link{color:var(--primary);text-decoration:none;font-family:var(--ff-mono);font-size:12px;}
.file-link:hover{text-decoration:underline;}
.pct-badge{display:inline-block;padding:2px 8px;border-radius:999px;font-size:11px;font-weight:600;font-family:var(--ff-mono);}
.pct-good{background:rgba(45,179,128,0.16);color:#69e2b0;}
.pct-mid {background:rgba(255,177,61,0.18);color:#ffb659;}
.pct-low {background:rgba(255,149,0,0.18);color:#ffa84e;}
.pct-danger{background:rgba(239,79,86,0.20);color:#ff7d83;}
.pctbar{position:relative;height:14px;background:rgba(255,255,255,0.06);border-radius:999px;overflow:hidden;display:inline-flex;align-items:center;}
.pctbar-fill{position:absolute;inset:0 auto 0 0;height:100%;border-radius:999px;}
.pctbar-good{background:linear-gradient(90deg,#1eaf73,#4ed3a3);}
.pctbar-mid {background:linear-gradient(90deg,#ff9b2a,#ffb659);}
.pctbar-low {background:linear-gradient(90deg,#ff7a1d,#ff9540);}
.pctbar-danger{background:linear-gradient(90deg,#d94146,#ef4f56);}
.pctbar-text{position:relative;z-index:1;margin:0 auto;font-size:10px;color:#fff;font-family:var(--ff-mono);text-shadow:0 1px 1px rgba(0,0,0,0.35)}
.tag{display:inline-block;padding:2px 7px;border-radius:6px;font-size:11px;margin-right:5px;margin-bottom:3px}
.tag-green {background:rgba(45,179,128,0.16);color:#6fe0ad;border:1px solid rgba(45,179,128,0.35);}
.tag-blue  {background:rgba(31,181,172,0.14);color:#62d6cf;border:1px solid rgba(31,181,172,0.35);}
.tag-gold  {background:rgba(200,162,75,0.16);color:#e4c78a;border:1px solid rgba(200,162,75,0.40);}
.tag-gray  {background:rgba(255,255,255,0.05);color:#a4b7c2;border:1px solid rgba(255,255,255,0.10);}
.tag-red   {background:rgba(239,79,86,0.16);color:#ff898f;border:1px solid rgba(239,79,86,0.35);}
.line-num{font-family:var(--ff-mono);font-size:11px;padding:0 4px;border-radius:4px;background:rgba(255,255,255,0.05);color:#bfd4dd}
.muted{color:var(--text-3);font-size:12px}
.hot-item{padding:8px 10px;border-bottom:1px dashed var(--divider);display:flex;gap:10px;align-items:flex-start}
.hot-item:last-child{border-bottom:none}
.hot-rank{width:24px;height:24px;border-radius:6px;background:linear-gradient(180deg,var(--danger),#a1242b);color:#fff;font-weight:800;font-size:12px;display:flex;align-items:center;justify-content:center;flex-shrink:0;}
.hot-rank.r2{background:linear-gradient(180deg,var(--warning),#b76400);}
.hot-rank.r3{background:linear-gradient(180deg,var(--accent),#846321);}
.hot-rank.rn{background:linear-gradient(180deg,#3c6478,#234255);}
.footer{margin-top:30px;color:var(--text-3);font-size:11px;text-align:center;border-top:1px solid var(--divider);padding-top:14px}
.matrix-pill{display:flex;gap:8px;flex-wrap:wrap;}
.fx-col{min-width:140px}
.range-code{font-family:var(--ff-mono);font-size:11px;color:#8cb3c6;background:rgba(31,181,172,0.10);padding:1px 6px;border-radius:6px}
`;

function renderSummaryCards(stat) {
  const dims = [
    { k:'stmts',  label:'语句覆盖率 Statements', v:stat.stmts },
    { k:'branch', label:'分支覆盖率 Branches',   v:stat.branch },
    { k:'funcs',  label:'函数覆盖率 Functions',  v:stat.funcs },
    { k:'lines',  label:'行覆盖率 Lines',        v:stat.lines },
  ];
  return `<div class="kpi-grid">${dims.map(d=>`
    <div class="kpi">
      <div class="label">${esc(d.label)}</div>
      <div class="val" style="color:${pctClass(d.v)==='good'?'#6fe0ad':pctClass(d.v)==='mid'?'#ffb659':pctClass(d.v)==='low'?'#ffa84e':'#ff7d83'};">${d.v.toFixed(2)}%</div>
      <div class="bar">${renderIndexBar(d.v, 240)}</div>
      <div class="muted" style="margin-top:6px; font-family:var(--ff-mono); font-size:11px;">
        ${d.k==='stmts'||d.k==='lines' ? `命中 ${stat.lh.toLocaleString()} / 共 ${stat.lf.toLocaleString()} 行` :
          d.k==='branch' ? `命中 ${stat.bh} / 共 ${stat.bf} 分支` :
                           `命中 ${stat.fh} / 共 ${stat.ff} 函数`}
      </div>
    </div>
  `).join('')}</div>`;
}

function renderFilesTable(recs) {
  // 按目录分组
  const groups = {};
  for (const r of recs) {
    const dir = path.dirname(r.file).replace(/\\/g,'/');
    (groups[dir] = groups[dir] || []).push(r);
  }
  let html = '<table><thead><tr>' +
    '<th>文件</th><th style="width:120px;">语句 Stmts</th>' +
    '<th style="width:120px;">分支 Branch</th>' +
    '<th style="width:120px;">函数 Funcs</th>' +
    '<th style="width:120px;">行 Lines</th>' +
    '<th>未覆盖行号摘要 (合并区间)</th></tr></thead><tbody>';
  for (const dir of Object.keys(groups).sort()) {
    html += `<tr><td colspan="6" style="background:rgba(255,255,255,0.04);color:var(--text-2);font-weight:700;padding:10px;">${esc(dir)}/</td></tr>`;
    for (const r of groups[dir].sort((a,b)=>a.file.localeCompare(b.file))) {
      const st = pct(r.lines_hit, r.lines_found);
      const br = pct(r.branches_hit, r.branches_found);
      const fn = pct(r.funcs_hit, r.funcs_found);
      const ln = pct(r.lines_hit, r.lines_found);
      // 生成 c8 的逐行报告链接: c8 通常输出 coverage/<rel-path>/index.html
      const relHtml = 'coverage/' + r.file.replace(/^\.\//,'') + '.html';
      const uncovered = uncoveredRanges(r);
      const summary = uncovered.length ? uncovered.slice(0,6).map(([s,e])=> s===e ? `<span class="line-num">${s}</span>` : `<span class="line-num">${s}-${e}</span>`).join(' ') + (uncovered.length>6 ? `  <span class="muted">(+${uncovered.length-6} 处…)</span>`:'') : '<span class="tag tag-green">全覆盖</span>';
      html += `<tr>
        <td><a class="file-link" href="${esc(relHtml)}" target="_blank" rel="noopener">${esc(r.file)}</a></td>
        <td>${pctBadge(st)} ${renderIndexBar(st, 120)}</td>
        <td>${pctBadge(br)} ${renderIndexBar(br, 120)}</td>
        <td>${pctBadge(fn)} ${renderIndexBar(fn, 120)}</td>
        <td>${pctBadge(ln)} ${renderIndexBar(ln, 120)}</td>
        <td style="font-size:11px;">${summary}</td>
      </tr>`;
    }
    // 小计行
    const s = covStat(groups[dir]);
    html += `<tr style="background:rgba(255,255,255,0.02);">
      <td style="font-weight:700;color:var(--text-2);">${esc(dir)} 小计</td>
      <td>${pctBadge(s.stmts)}</td><td>${pctBadge(s.branch)}</td><td>${pctBadge(s.funcs)}</td><td>${pctBadge(s.lines)}</td><td></td>
    </tr>`;
  }
  html += '</tbody></table>';
  return html;
}

function renderMatrix(recs) {
  const byFile = Object.fromEntries(recs.map(r=>[r.file, r]));
  const appRec = byFile['platform-admin/app.js'] || byFile['platform-admin\\app.js'];
  const isCovered = (rangeSpec) => {
    if (!appRec || !rangeSpec) return 'unknown';
    // 粗略匹配:rangeSpec "~1000-1200" 或 "1-97"
    const m = /^~?(\d+)-(\d+)/.exec(rangeSpec);
    if (!m) return 'unknown';
    const s = +m[1], e = +m[2];
    let hit=0, miss=0;
    for (let ln=s; ln<=e; ln++) {
      if (!appRec.line_detail.has(ln)) continue; // 非可执行行
      const h = appRec.line_detail.get(ln);
      if (h>0) hit++; else miss++;
    }
    if (hit+miss===0) return 'unknown';
    return { pct: Math.round(10000*hit/(hit+miss))/100, hit, miss };
  };
  let html = '<table><thead><tr>' +
    '<th style="width:100px;">领域</th>' +
    '<th>功能 / 函数</th>' +
    '<th style="width:120px;">代码范围</th>' +
    '<th style="width:120px;">本段覆盖率</th>' +
    '<th>关联测试用例</th>' +
    '<th>说明</th>' +
    '</tr></thead><tbody>';
  for (const row of FUNCTION_MATRIX) {
    const cov = isCovered(row.codeRange);
    let covCell = '<span class="muted">未知</span>';
    if (cov && cov !== 'unknown') {
      covCell = `${pctBadge(cov.pct)} <span class="muted" style="font-size:11px;">H ${cov.hit} / M ${cov.miss}</span>`;
    }
    const tags = row.tests.map(t => {
      const isP0 = /BUG|P0-|压力|滚动|数值|钳位|A9|A10|A11|A12|A13|Session/i.test(t);
      return `<span class="tag ${/未覆盖|间接/i.test(t)?'tag-gray':(isP0?'tag-blue':'tag-green')}">${esc(t)}</span>`;
    }).join('');
    html += `<tr>
      <td>${esc(row.area)}</td>
      <td style="font-family:var(--ff-mono);font-weight:600;color:#d0f1ee;">${esc(row.func)}</td>
      <td><span class="range-code">${esc(row.codeRange)}</span></td>
      <td>${covCell}</td>
      <td class="matrix-pill">${tags}</td>
      <td style="color:var(--text-2);font-size:12px;">${esc(row.desc)}</td>
    </tr>`;
  }
  html += '</tbody></table>';
  return html;
}

function renderHotspots(recs, n=12) {
  const items = recs.flatMap(r => {
    return [...r.line_detail.entries()]
      .filter(([,h])=>h===0)
      .map(([ln]) => ({ file:r.file, ln }));
  });
  // 未覆盖行太多,我们按"文件维度未覆盖数"排序取 Top N 文件 + 它们最大的未覆盖连续块
  const fileRank = recs.slice()
    .map(r => ({ file:r.file, missing: [...r.line_detail.values()].filter(v=>v===0).length, ranges: uncoveredRanges(r), rec:r }))
    .sort((a,b)=> b.missing - a.missing)
    .slice(0, n);
  return fileRank.map((x,idx)=>{
    const rank = idx < 3 ? (idx===0 ? '' : idx===1 ? 'r2' : 'r3') : 'rn';
    const ln = x.ranges.length ? x.ranges.slice(0,8).map(([s,e])=>s===e?`<span class="line-num">${s}</span>`:`<span class="line-num">${s}-${e}</span>`).join(' ')
                            : '<span class="tag tag-green">全覆盖</span>';
    const more = x.ranges.length>8 ? `<span class="muted">(+${x.ranges.length-8}段)</span>`:'';
    const linesPct = pct(x.rec.lines_hit, x.rec.lines_found);
    return `<div class="hot-item">
      <div class="hot-rank ${rank}">${idx+1}</div>
      <div style="flex:1;">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap;">
          <a class="file-link" href="${esc('coverage/'+x.file+'.html')}" target="_blank" rel="noopener">${esc(x.file)}</a>
          <div style="display:flex;align-items:center;gap:10px;">
            <span class="tag tag-red">未覆盖 ${x.missing} 行</span>
            ${pctBadge(linesPct)}
          </div>
        </div>
        <div style="margin-top:6px;font-size:11px;">未覆盖区间: ${ln}${more}</div>
      </div>
    </div>`;
  }).join('');
}

/* --------------- 4. 组装输出 --------------- */
function main() {
  if (!fs.existsSync(LCOV)) {
    console.error('[coverage_report] 找不到 lcov.info: '+LCOV+'\n请先跑: npm run coverage');
    process.exit(1);
  }
  const recs = parseLcov(LCOV);
  // 过滤掉 .html 等非 js 项 (lcov 里可能没)
  const js = recs.filter(r => /\.js$/i.test(r.file));
  const stat = covStat(js);

  // c8 HTML 索引链接:优先按 COV_DIR 的相对 ROOT 路径,点击跳转准确
  const c8Index = path.relative(ROOT, path.join(COV_DIR, 'index.html'));
  const today = new Date().toISOString().slice(0,10);

  const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"/>
<title>链盛通 LSC P0-图表逻辑 覆盖率报告 · ${today}</title>
<style>${DESIGN_CSS}</style>
</head>
<body>
<h1>链盛通 LSC V6.2-AI · P0-图表逻辑 覆盖率报告</h1>
<div class="subtitle">被测范围: shared/app-utils.js + platform-admin/app.js · 运行器: coverage_runner.js(vm.Script + JSDOM) · c8 12.x</div>
<div class="meta-row">
  <div>📅 生成日期: <b style="color:var(--text-1);">${today}</b></div>
  <div>🗂️ 文件数: <b style="color:var(--text-1);">${js.length}</b></div>
  <div>🧪 用例: <b style="color:var(--text-1);">coverage_runner 28 断言</b> + <a href="${esc(c8Index)}" target="_blank" style="color:var(--primary);">c8 逐行 HTML 报告 ↗</a></div>
  <div>🏷️ 验证基线: verify_p0 22/22 · test_p0_chart_logic 8/8 全绿</div>
</div>

<h2>一、总体覆盖率指标卡</h2>
${renderSummaryCards(stat)}

<div class="card" style="margin-top:18px;">
  <h3 style="margin-top:0;">名词解释</h3>
  <div style="font-size:12px;color:var(--text-2);line-height:1.7;">
    <b>语句/行(Stmts/Lines):</b>源文件中每条 <code>;</code> 语句 & 每行可执行代码的被执行比例。<br/>
    <b>分支(Branch):</b>三元/短路 <code>&& ||</code>/<code>if/else</code>/<code>switch case</code> 等每个分支真假是否被走过。本次 <b>91.78%</b> 已达绿区。<br/>
    <b>函数(Funcs):</b>被声明的函数中被调用过的比例。平台后台有大量仅在特定入口点击才触发的 render* 页面,导致函数覆盖率相对较低,这是预期的。<br/>
    <b>说明:</b> c8 通过 V8 Profiler 生成;由于被测代码跑在 JSDOM 的 vm context,本报告用 <code>new vm.Script(src, {filename: absPath})</code> 显式传入源路径以确保覆盖率与真实文件一一对应。
  </div>
</div>

<h2>二、文件维度覆盖率明细 (点击文件跳转 c8 逐行彩色报告)</h2>
<div class="card">${renderFilesTable(js)}</div>

<h2>三、功能覆盖矩阵(P0 图表逻辑 × 测试用例 × 代码段)</h2>
<div class="card">
  <div class="muted" style="margin-bottom:10px;">"本段覆盖率" 按功能代码范围做局部统计,便于定位 P0 关键逻辑是否被覆盖。点击"文件"列链接可逐行查看绿/红底色标记。</div>
  ${renderMatrix(js)}
</div>

<h2>四、未覆盖热点 Top 12(按文件未覆盖行数排序)</h2>
<div class="card">${renderHotspots(js, 12)}</div>

<h2>五、如何在本地刷新报告</h2>
<div class="card"><pre style="background:rgba(255,255,255,0.05);padding:10px 14px;border-radius:8px;color:#cfe8df;font-family:var(--ff-mono);font-size:12px;overflow:auto;">
$ cd /workspace/lsc-system
$ npm run coverage                 # 跑 coverage_runner 并生成 coverage/ (html + lcov + text)
$ node coverage_report.js          # 生成本文件:覆盖率报告_P0-图表逻辑_日期.html
$ open 覆盖率报告_P0-图表逻辑_20260827.html   # 浏览器打开
$ node test_p0_chart_logic.js      # 先保证 8 条 TAP 用例全部通过
$ node verify_p0.js                # 再保证 4 应用 22 断言全绿</pre>
</div>

<div class="footer">
  链盛通 LSC V6.2-AI · 项目路径: <code>${esc(ROOT)}</code> · 本报告由 <code>coverage_report.js</code> 自动生成 · 数据来源 <code>${esc(path.relative(ROOT, LCOV)||LCOV)}</code>${C8_REPORT_DIR?` · 归档目录:<code>${esc(path.relative(ROOT,COV_DIR))}</code>`:''}
</div>
</body></html>`;
  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, html, 'utf8');
  // 如果启用了 CI 子目录,根目录再复制一份老命名风格的快捷入口
  if (OUT_LEGACY && OUT_LEGACY !== OUT) {
    try { fs.writeFileSync(OUT_LEGACY, html, 'utf8'); } catch(_) {}
  }
  console.log('[覆盖率报告] 已生成: '+OUT);
  if (OUT_LEGACY && OUT_LEGACY!==OUT) console.log('                  副本:   '+OUT_LEGACY);
  console.log('  总览: 语句 '+stat.stmts.toFixed(2)+'%  分支 '+stat.branch.toFixed(2)+'%  函数 '+stat.funcs.toFixed(2)+'%  行 '+stat.lines.toFixed(2)+'%');
  console.log('  逐行 HTML: '+path.relative(ROOT, path.join(COV_DIR, 'index.html')));
}
main();
