#!/usr/bin/env node
/**
 * audit-a11y-diff.js
 *
 * 对比 PR / 本地构建生成的 a11y-baseline.json 与 master 已知-good 基线,
 * 定位 "新增违规 / 回归项 / 修复项"。
 *
 * 设计原则:
 *   - 基线: 项目根目录 `audit-report/a11y-baseline.baseline.json`
 *        或 `--baseline <path>` 传入 (CI 中可用下载好的 master artifact)
 *   - 当前: 默认 `audit-report/a11y-baseline.json`, 或 `--current <path>`
 *   - 匹配键: `${app}:${width}x${height}:${colorScheme}:${violationId}:${nodeTarget.join('|')}`
 *        (这样即使同类 violation 在不同浏览器/不同 PR 下多次出现, 也能精准对齐)
 *
 * 退出码:
 *   0 = 无新增违规 (允许存在"修复项"即 baseline 有而当前没有, 算作改进 ✅)
 *   1 = 缺少 baseline 或 baseline / current 解析失败
 *   2 = 检测到新增违规 → CI 直接失败, 防止回归
 *
 * CI 用法示例:
 *   node audit-a11y-baseline.js --strict --label="$CI_COMMIT_SHORT_SHA"
 *   node audit-a11y-diff.js --baseline=artifacts/master/a11y-baseline.json --current=audit-report/a11y-baseline.json
 */
'use strict';
const path = require('path');
const fs = require('fs');

const ROOT = __dirname;
const DEF_BASELINE = path.join(ROOT, 'audit-report', 'a11y-baseline.baseline.json');
const DEF_CURRENT  = path.join(ROOT, 'audit-report', 'a11y-baseline.json');

function parseArgs(argv) {
  const opts = { baseline: process.env.A11Y_BASELINE_PATH || DEF_BASELINE,
                 current:  DEF_CURRENT,
                 quiet: false, allowNewMissingAlt: false, failOnFixRegressions: true,
                 markdownOut: path.join(ROOT, 'audit-report', 'a11y-diff.md'),
                 jsonOut:     path.join(ROOT, 'audit-report', 'a11y-diff.json') };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--baseline' && argv[i+1]) opts.baseline = argv[++i];
    else if (a.startsWith('--baseline=')) opts.baseline = a.slice('--baseline='.length);
    else if (a === '--current' && argv[i+1]) opts.current = argv[++i];
    else if (a.startsWith('--current=')) opts.current = a.slice('--current='.length);
    else if (a === '--quiet' || a === '-q') opts.quiet = true;
    else if (a === '--allow-new-missing-alt') opts.allowNewMissingAlt = true;
    else if (a === '--md' && argv[i+1]) opts.markdownOut = argv[++i];
    else if (a === '--json' && argv[i+1]) opts.jsonOut = argv[++i];
    else if (a === '-h' || a === '--help') {
      console.log(`
LSC V6.2-AI · A11y 基线 Diff 工具
用法: node audit-a11y-diff.js [选项]

选项:
  --baseline <path>  master / 上一次绿版 JSON (默认 audit-report/a11y-baseline.baseline.json,
                     也可通过环境变量 $A11Y_BASELINE_PATH 注入)
  --current  <path>  当前 PR / 本地生成 JSON (默认 audit-report/a11y-baseline.json)
  --allow-new-missing-alt   若新增缺 alt 图像, 仅告警不失败
  --md <path>        输出 Markdown Diff 报告 (默认 audit-report/a11y-diff.md)
  --json <path>      输出 JSON Diff (默认 audit-report/a11y-diff.json)
  -q, --quiet        仅打印最终 PASS/FAIL, 不输出详情
  -h, --help         本帮助

退出码: 0=无新增违规; 1=baseline/current 读失败; 2=检测到新增违规
`); process.exit(0);
    }
  }
  return opts;
}
const OPTS = parseArgs(process.argv);

function tryLoadJSON(p) {
  if (!fs.existsSync(p)) return { err: '文件不存在: ' + p };
  try {
    const raw = fs.readFileSync(p, 'utf8');
    const d = JSON.parse(raw);
    // 同时兼容旧版 (纯 records array) 与新版 ({meta, records})
    const records = Array.isArray(d) ? d : (d.records || []);
    const summary = (d && d.meta && d.meta.summary) ? d.meta.summary : null;
    return { err: null, records, summary, path: p };
  } catch (e) {
    return { err: '解析失败 (' + p + '): ' + e.message };
  }
}

/**
 * 构建 violation 指纹集合 (per snapshot + global)
 *   key = `${app}:${W}x${H}:${colorScheme}:${vId}:${target}`
 */
function snapshotKey(r) { return `${r.app}:${r.width}x${r.height}:${r.colorScheme}`; }
function flattenViolations(records) {
  const map = new Map(); // snapshotKey -> Map(violationFp -> node)
  const global = new Set();
  for (const r of records) {
    const sk = snapshotKey(r);
    const per = new Map();
    if (r.axe && Array.isArray(r.axe.violations)) {
      for (const v of r.axe.violations) {
        for (const n of (v.nodes || [])) {
          const target = Array.isArray(n.target) ? n.target.join('|') : String(n.target || '');
          const fp = `${sk}:${v.id}:${target}`;
          per.set(fp, { violationId: v.id, impact: v.impact, desc: v.description,
                       target, html: (n.html||'').slice(0,200),
                       failureSummary: (n.failureSummary||'').slice(0,300) });
          global.add(fp);
        }
      }
    }
    map.set(sk, per);
  }
  return { map, global,
    aggregate: (r) => ({
      missingAlt: (r.counts && (r.counts.missingAlt ?? r.counts.img ?? r.counts.altMissing)) || 0,
      net4xx5xx:   Array.isArray(r.netErrors) ? r.netErrors.length : (r.netErrors || 0),
      consoleE:    Array.isArray(r.consoleErrors) ? r.consoleErrors.length : (r.consoleErrors || 0),
      consoleW:    Array.isArray(r.consoleWarnings) ? r.consoleWarnings.length : (r.consoleWarnings || 0),
      violations:  (r.axe && r.axe.violations) ? r.axe.violations.reduce((s,v)=>s+(typeof v.count==='number'?v.count:v.nodes? v.nodes.length : 0),0) : 0,
    })
  };
}

function run() {
  const base = tryLoadJSON(OPTS.baseline);
  const cur  = tryLoadJSON(OPTS.current);

  if (base.err) { console.error('[a11y-diff] BASELINE 读取失败: ' + base.err + '\n'
    + '👉 首次使用请先 `cp audit-report/a11y-baseline.json audit-report/a11y-baseline.baseline.json` 将当前绿版固化为基线。'); process.exit(1); }
  if (cur.err)  { console.error('[a11y-diff] CURRENT 读取失败: ' + cur.err);  process.exit(1); }

  const B = flattenViolations(base.records);
  const C = flattenViolations(cur.records);

  /* --- 1) 快照完整性: 16 × 2 主题 必须都在 --- */
  const expectedKeys = new Set([...B.map.keys(), ...C.map.keys()]);
  const missingInCur = []; for (const k of B.map.keys()) if (!C.map.has(k)) missingInCur.push(k);
  const missingInBas = []; for (const k of C.map.keys()) if (!B.map.has(k)) missingInBas.push(k);

  /* --- 2) 新增 / 修复 violation --- */
  const newViolations = [];   // 当前比基线多
  const fixedViolations = []; // 当前比基线少 (修复)
  for (const fp of C.global) if (!B.global.has(fp)) {
      const [app,sz,cs,vId,target] = fp.split(/:(?=[0-9]+x[0-9]+:)|:(?=light$|dark$)/).length > 1 ? fp.split(':') : fp.split(/::?/);
      // 更鲁棒: 从 C 记录里重新拿元信息
      for (const [sk, per] of C.map.entries()) {
        if (per.has(fp)) { const node = per.get(fp);
          newViolations.push({ fp, snapshot: sk, node });
          break; } }
  }
  for (const fp of B.global) {
    if (!C.global.has(fp)) {
      for (const [sk, per] of B.map.entries()) {
        if (per.has(fp)) { fixedViolations.push({ fp, snapshot: sk, node: per.get(fp) }); break; }
      }
    }
  }

  /* --- 3) 其他指标增量 (consoleE / net / missingAlt) --- */
  const bSnap = new Map(base.records.map(r=>[snapshotKey(r),r]));
  const cSnap = new Map(cur.records.map(r=>[snapshotKey(r),r]));
  const metricDeltas = [];
  const perRecAggC = new Map();
  const perRecAggB = new Map();
  for (const [sk, r] of cSnap) perRecAggC.set(sk, C.aggregate(r));
  for (const [sk, r] of bSnap) perRecAggB.set(sk, B.aggregate(r));
  for (const sk of new Set([...bSnap.keys(), ...cSnap.keys()])) {
    const b = perRecAggB.get(sk) || {missingAlt:0,net4xx5xx:0,consoleE:0,consoleW:0,violations:0};
    const c = perRecAggC.get(sk) || {missingAlt:0,net4xx5xx:0,consoleE:0,consoleW:0,violations:0};
    const delta = { sk,
      missingAlt: [b.missingAlt, c.missingAlt, c.missingAlt - b.missingAlt],
      net4xx5xx:   [b.net4xx5xx,   c.net4xx5xx,   c.net4xx5xx   - b.net4xx5xx  ],
      consoleE:    [b.consoleE,    c.consoleE,    c.consoleE    - b.consoleE   ],
      consoleW:    [b.consoleW,    c.consoleW,    c.consoleW    - b.consoleW   ],
    };
    if (delta.missingAlt[2] !== 0 || delta.net4xx5xx[2] !== 0 || delta.consoleE[2] !== 0) metricDeltas.push(delta);
  }
  const sumB = base.summary  || { totalViolations:0, totalConsoleE:0, totalNetErrors:0, totalMissingAlt:0, totalConsoleW:0 };
  const sumC = cur.summary   || { totalViolations:0, totalConsoleE:0, totalNetErrors:0, totalMissingAlt:0, totalConsoleW:0 };
  const totals = {
    violations:  [sumB.totalViolations  ||0, sumC.totalViolations  ||0, (sumC.totalViolations||0) - (sumB.totalViolations||0)],
    consoleE:    [sumB.totalConsoleE    ||0, sumC.totalConsoleE    ||0, (sumC.totalConsoleE    ||0) - (sumB.totalConsoleE    ||0)],
    consoleW:    [sumB.totalConsoleW    ||0, sumC.totalConsoleW    ||0, (sumC.totalConsoleW    ||0) - (sumB.totalConsoleW    ||0)],
    net4xx5xx:   [sumB.totalNetErrors   ||0, sumC.totalNetErrors   ||0, (sumC.totalNetErrors   ||0) - (sumB.totalNetErrors   ||0)],
    missingAlt:  [sumB.totalMissingAlt  ||0, sumC.totalMissingAlt  ||0, (sumC.totalMissingAlt  ||0) - (sumB.totalMissingAlt  ||0)],
  };

  /* --- 4) 控制台输出 --- */
  if (!OPTS.quiet) {
    console.log('\n[a11y-diff] =========================================================');
    console.log(`  baseline : ${OPTS.baseline}`);
    console.log(`  current  : ${OPTS.current}`);
    console.log('------------------------------------------------------------------');
    console.log(`  快照 baseline=${bSnap.size}  current=${cSnap.size}`);
    if (missingInCur.length) console.warn(`  ⚠ current 缺少快照: ${missingInCur.join(', ')} (CI 视为异常)`);
    if (missingInBas.length) console.warn(`  ⓘ baseline 没有的快照: ${missingInBas.join(', ')} (视为新增覆盖范围)`);
    console.log('------------------------------------------------------------------');
    console.log(`  指标         baseline → current  (delta)`);
    for (const k of ['violations','consoleE','consoleW','net4xx5xx','missingAlt']) {
      const [b,c,d] = totals[k];
      const sign = d > 0 ? '+' : '';
      const col  = d > 0 ? '❌' : d < 0 ? '✅' : '·';
      console.log(`  ${col} ${k.padEnd(12)} ${String(b).padStart(6)} → ${String(c).padStart(6)}  (${sign}${d})`);
    }
    console.log('------------------------------------------------------------------');
    console.log(`  🆕 新增违规 (${newViolations.length}) :`);
    if (newViolations.length === 0) console.log('     ✅ 0');
    else for (const v of newViolations.slice(0, 40)) {
      console.log(`     · [${v.snapshot}] ${v.node.violationId} (${v.node.impact||'-'}) ${v.node.target}`);
    }
    if (newViolations.length > 40) console.log(`     ... and ${newViolations.length - 40} more`);
    console.log('------------------------------------------------------------------');
    console.log(`  ✅ 修复项 (baseline 存在, current 已修复: ${fixedViolations.length}) :`);
    if (!fixedViolations.length) console.log('     (无修复项)');
    else for (const v of fixedViolations.slice(0, 20)) {
      console.log(`     ✅ [${v.snapshot}] ${v.node.violationId} ${v.node.target}`);
    }
    if (metricDeltas.length) {
      console.log('------------------------------------------------------------------');
      console.log(`  指标显著变动快照数: ${metricDeltas.length}`);
      for (const d of metricDeltas.slice(0, 12)) {
        const parts = [];
        for (const m of ['consoleE','net4xx5xx','missingAlt']) {
          const [b,c,dd] = d[m]; if (dd !== 0) parts.push(`${m}:${b}→${c} (${dd>0?'+':''}${dd})`);
        }
        if (parts.length) console.log(`     · ${d.sk}   ${parts.join('  ')}`);
      }
    }
    console.log('[a11y-diff] =========================================================\n');
  }

  /* --- 5) 输出 JSON + Markdown --- */
  fs.mkdirSync(path.dirname(OPTS.jsonOut),     { recursive: true });
  fs.mkdirSync(path.dirname(OPTS.markdownOut), { recursive: true });
  const resultObj = {
    generatedAt: new Date().toISOString(),
    baseline: { path: OPTS.baseline, snapshots: bSnap.size, summary: sumB },
    current:  { path: OPTS.current,  snapshots: cSnap.size, summary: sumC },
    snapshotIntegrity: { missingInCur, missingInBas },
    totals,
    counts: { newViolations: newViolations.length, fixedViolations: fixedViolations.length,
              changedMetricsSnapshots: metricDeltas.length },
    newViolations,
    fixedViolations,
    metricDeltas,
  };
  fs.writeFileSync(OPTS.jsonOut, JSON.stringify(resultObj, null, 2));

  const mdLines = [];
  mdLines.push('# 链盛通 LSC V6.2-AI · A11y 基线 Diff 报告');
  mdLines.push('');
  mdLines.push(`> 生成于 ${resultObj.generatedAt}`);
  mdLines.push(`>`);
  mdLines.push(`> Baseline (master/绿版): \`${OPTS.baseline}\``);
  mdLines.push(`> Current  (PR/本地构建): \`${OPTS.current}\``);
  mdLines.push('');
  mdLines.push('## 一、总体指标对比');
  mdLines.push('');
  mdLines.push('| 指标 | Baseline | Current | Delta | 判定 |');
  mdLines.push('|---|---|---|---|---|');
  for (const k of ['violations','consoleE','consoleW','net4xx5xx','missingAlt']) {
    const [b,c,d] = totals[k];
    const judge = d > 0 ? (k === 'consoleW' ? '⚠ 警告增加' : '❌ 恶化') : d < 0 ? '✅ 改善' : '＝ 无变化';
    const sign  = d > 0 ? '+' : '';
    mdLines.push(`| ${k} | ${b} | ${c} | ${sign}${d} | ${judge} |`);
  }
  mdLines.push('');
  mdLines.push(`- 新增违规数: **${newViolations.length}**`);
  mdLines.push(`- 修复项数: **${fixedViolations.length}**`);
  if (missingInCur.length) mdLines.push(`- ⚠️ current 缺失快照 (${missingInCur.length}): ${missingInCur.join(', ')}`);
  if (missingInBas.length) mdLines.push(`- ⓘ baseline 缺失快照 (${missingInBas.length}, 视为新增覆盖): ${missingInBas.join(', ')}`);
  mdLines.push('');
  if (newViolations.length) {
    mdLines.push('## 二、新增违规详情（PR 引入回归 → ❌ 阻塞合入）');
    mdLines.push('');
    for (let i = 0; i < newViolations.length; i++) {
      const v = newViolations[i];
      mdLines.push(`### 2.${i+1} \`${v.node.violationId}\` · **${v.snapshot}**`);
      mdLines.push('- **Impact**: ' + (v.node.impact || '-'));
      mdLines.push('- **Description**: ' + v.node.desc);
      mdLines.push('- **Target**: `' + v.node.target + '`');
      if (v.node.html)          mdLines.push('- **HTML 片段**: `' + v.node.html.replace(/`/g, '\'') + '`');
      if (v.node.failureSummary) {
        mdLines.push('<details><summary>axe failureSummary</summary><pre>' + v.node.failureSummary.replace(/</g,'&lt;') + '</pre></details>');
      }
      mdLines.push('');
    }
  } else {
    mdLines.push('## 二、新增违规 ✅ 无');
    mdLines.push('');
    mdLines.push('本次 PR / 构建没有引入任何新的 axe-core 违规，可放心合入。');
    mdLines.push('');
  }
  if (fixedViolations.length) {
    mdLines.push('## 三、已修复项 (Baseline → Current) ✨');
    mdLines.push('');
    mdLines.push('| # | 快照 | Violation ID | Target |');
    mdLines.push('|---|---|---|---|');
    fixedViolations.forEach((v,i)=> mdLines.push(`| ${i+1} | ${v.snapshot} | \`${v.node.violationId}\` | \`${v.node.target.replace(/`/g,'')}\` |`));
    mdLines.push('');
  }
  if (metricDeltas.length) {
    mdLines.push('## 四、其他指标发生变化的快照');
    mdLines.push('');
    mdLines.push('| 快照 | consoleE (B→C Δ) | net4xx5xx (B→C Δ) | missingAlt (B→C Δ) |');
    mdLines.push('|---|---|---|---|');
    for (const d of metricDeltas) {
      const ce = `${d.consoleE[0]}→${d.consoleE[1]} (${d.consoleE[2]>0?'+':''}${d.consoleE[2]})`;
      const ne = `${d.net4xx5xx[0]}→${d.net4xx5xx[1]} (${d.net4xx5xx[2]>0?'+':''}${d.net4xx5xx[2]})`;
      const ma = `${d.missingAlt[0]}→${d.missingAlt[1]} (${d.missingAlt[2]>0?'+':''}${d.missingAlt[2]})`;
      mdLines.push(`| ${d.sk} | ${ce} | ${ne} | ${ma} |`);
    }
    mdLines.push('');
  }
  mdLines.push('---');
  mdLines.push('');
  mdLines.push('_本报告由 `node audit-a11y-diff.js` 自动生成,配合 `.gitlab-ci.yml` / `.github/workflows/a11y-audit.yml` 在每次 MR/PR 中作为合入门禁。_');
  fs.writeFileSync(OPTS.markdownOut, mdLines.join('\n'));

  /* --- 6) 退出判定: 新增违规 > 0 或 missingInCur 或 new net/consoleE (除 missingAlt 由开关控制)  --- */
  let fail = false;
  const reasons = [];
  if (newViolations.length > 0) { fail = true; reasons.push(`新增 axe 违规 ${newViolations.length} 项`); }
  if (missingInCur.length > 0)  { fail = true; reasons.push(`快照缺失 ${missingInCur.length} 项 (PR 可能移除了原有应用/视口)`); }
  if (totals.consoleE[2] > 0)  { fail = true; reasons.push(`console.error 新增 +${totals.consoleE[2]} 条`); }
  if (totals.net4xx5xx[2] > 0) { fail = true; reasons.push(`资源 4xx/5xx 新增 +${totals.net4xx5xx[2]} 次`); }
  if (!OPTS.allowNewMissingAlt && totals.missingAlt[2] > 0) { fail = true; reasons.push(`新增缺 alt 图像 +${totals.missingAlt[2]} 张 (--allow-new-missing-alt 可豁免)`); }

  if (fail) {
    console.error('[a11y-diff] ❌ FAIL, 阻塞原因:');
    for (const r of reasons) console.error('  · ' + r);
    console.error(`\n👉 详情报告: ${OPTS.markdownOut}\n👉 JSON: ${OPTS.jsonOut}`);
    process.exit(2);
  }
  console.log('[a11y-diff] ✅ PASS — 无新增违规,可合入。');
  console.log(`   详情: ${OPTS.markdownOut} / JSON: ${OPTS.jsonOut}`);
  process.exit(0);
}

try { run(); }
catch (e) { console.error('[a11y-diff] 致命错误:', e); process.exit(1); }
