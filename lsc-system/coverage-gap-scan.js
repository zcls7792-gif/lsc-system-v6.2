#!/usr/bin/env node
/**
 * coverage-gap-scan.js — 深度覆盖率缺口扫描
 *
 * 输入: coverage/lcov.info (c8 产物) + 各应用 app.js 源码
 * 输出: 每个应用的未覆盖函数清单 + Top10 热点排行 + 缺口报告 md
 *
 * 步骤:
 *   1. 解析 lcov.info → 每文件未覆盖行号集合
 *   2. 对每个 app.js 解析顶层 function 声明 (含起止行)
 *   3. 计算每个函数的覆盖率 (uncovered/total 行数比)
 *   4. 排序输出 Top10 热点 + 结构化 JSON
 *
 * 用法: node coverage-gap-scan.js [--md <path>] [--json <path>]
 */
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname);
const LCov = path.join(ROOT, 'coverage/lcov.info');
const MD_OUT = (() => { const i = process.argv.indexOf('--md'); return i>=0 ? process.argv[i+1] : path.join(ROOT,'audit-report/coverage-gap.md'); })();
const JSON_OUT = (() => { const i = process.argv.indexOf('--json'); return i>=0 ? process.argv[i+1] : path.join(ROOT,'audit-report/coverage-gap.json'); })();

const APPS = [
  { key:'platform-admin', file:'platform-admin/app.js' },
  { key:'merchant-admin', file:'merchant-admin/app.js' },
  { key:'mobile-app',     file:'mobile-app/app.js' },
  { key:'mini-program',   file:'mini-program/app.js' },
  { key:'shared',         file:'shared/app-utils.js' },
];

// ===== Step 1: parse lcov.info =====
function parseLcov(file) {
  const txt = fs.readFileSync(file, 'utf8');
  const files = {}; // SF:path -> { DA:{line:hit}, LF, LH, FN:[{name,start}], FNDA:[{name,count}] }
  let cur = null;
  for (const line of txt.split('\n')) {
    if (line.startsWith('SF:')) {
      cur = { path: line.slice(3), DA:{}, FN:[], FNDA:[], LF:0, LH:0 };
      files[cur.path] = cur;
    } else if (line.startsWith('DA:')) {
      const [ln, hit] = line.slice(3).split(',').map(Number);
      cur.DA[ln] = hit;
    } else if (line.startsWith('FN:')) {
      const [start, name] = line.slice(3).split(',');
      cur.FN.push({ name, start: +start, end: null });
    } else if (line.startsWith('FNDA:')) {
      const [count, name] = line.slice(5).split(',');
      const f = cur.FN.find(f => f.name === name);
      if (f) f.count = +count;
    } else if (line.startsWith('LF:')) {
      cur.LF = +line.slice(3);
    } else if (line.startsWith('LH:')) {
      cur.LH = +line.slice(3);
    } else if (line === 'end_of_record') {
      cur = null;
    }
  }
  // fill FN.end from source (approximate by next FN start - 1)
  for (const fpath of Object.keys(files)) {
    const f = files[fpath];
    const sorted = [...f.FN].sort((a,b)=>a.start-b.start);
    for (let i=0; i<sorted.length; i++) {
      sorted[i].end = (i+1 < sorted.length) ? sorted[i+1].start - 1 : 99999;
    }
  }
  return files;
}

// ===== Step 2: parse source for top-level functions (as fallback if FN absent) =====
function parseSourceFunctions(file) {
  const src = fs.readFileSync(file, 'utf8');
  const lines = src.split('\n');
  const fns = [];
  // match top-level function decls: "  function name(args) {" or "function name(args) {"
  // also: "window.name = function" "window.name = (args)=>"
  const re = /^(?:window\.)?([a-zA-Z_$][\w$]*)\s*=\s*function\s*\(|^function\s+([a-zA-Z_$][\w$]*)\s*\(/;
  // also arrow: const x = (a) => {
  const reArrow = /^(?:const|let|var)\s+([a-zA-Z_$][\w$]*)\s*=\s*(?:\([^)]*\)|[a-zA-Z_$][\w$]*)\s*=>\s*\{?/;
  for (let i=0; i<lines.length; i++) {
    const m = re.exec(lines[i]) || reArrow.exec(lines[i]);
    if (m) {
      const name = m[1] || m[2];
      if (!name) continue;
      fns.push({ name, start: i+1, end: null });
    }
  }
  // sort & assign end
  fns.sort((a,b)=>a.start-b.start);
  for (let i=0; i<fns.length; i++) {
    fns[i].end = (i+1 < fns.length) ? fns[i+1].start - 1 : 99999;
  }
  return fns;
}

// ===== main =====
if (!fs.existsSync(LCov)) {
  console.error('coverage/lcov.info 不存在，请先 npm run coverage');
  process.exit(1);
}

const lcov = parseLcov(LCov);
const report = { _generatedAt: new Date().toISOString(), apps: {}, topHotspots: [] };

for (const app of APPS) {
  const abs = path.join(ROOT, app.file);
  // 找 lcov 中对应的 SF (c8 通常以 /workspace/.../app.js 绝对路径)
  const sfKey = Object.keys(lcov).find(k => k.endsWith(app.file.replace(/\//g, path.sep)) || k.endsWith(app.file) || k.endsWith(app.file.replace(/\//g,'/')));
  const cov = lcov[sfKey];
  if (!cov) {
    report.apps[app.key] = { error: `lcov 中未找到 ${app.file} (sfKey candidates: ${Object.keys(lcov).filter(k=>k.includes(app.key)).slice(0,3).join('|')})` };
    continue;
  }
  // 未覆盖行集合
  const uncovered = [];
  for (const [ln, hit] of Object.entries(cov.DA)) {
    if (hit === 0) uncovered.push(+ln);
  }
  // 按 FN 信息映射函数 (优先用 lcov FN)
  let srcFns = (cov.FN && cov.FN.length) ? cov.FN : parseSourceFunctions(abs);
  if (!cov.FN || !cov.FN.length) {
    srcFns = parseSourceFunctions(abs);
  } else {
    // 补 end
    srcFns = [...cov.FN].sort((a,b)=>a.start-b.start);
    for (let i=0; i<srcFns.length; i++) {
      srcFns[i].end = (i+1 < srcFns.length) ? srcFns[i+1].start - 1 : 99999;
    }
  }
  // 为每个函数算 uncovered / total
  const fnStats = srcFns.map(f => {
    const range = [];
    let uncov = 0;
    for (const ln of uncovered) {
      if (ln >= f.start && ln <= f.end) { uncov++; range.push(ln); }
    }
    const total = f.end - f.start + 1;
    const hit = typeof f.count === 'number' ? f.count : (uncov === 0 ? 1 : 0);
    return {
      name: f.name,
      start: f.start,
      end: f.end === 99999 ? null : f.end,
      totalLines: total,
      uncoveredLines: uncov,
      uncoveredRange: range,
      hitCount: hit,
      coveragePct: total === 0 ? 100 : +(((total - uncov) / total) * 100).toFixed(1),
    };
  }).filter(f => f.totalLines > 0);

  // 未覆盖函数 (hit=0 or uncoveredLines>0)
  const uncoveredFns = fnStats.filter(f => f.hitCount === 0 || f.uncoveredLines > 0)
    .sort((a,b) => b.uncoveredLines - a.uncoveredLines);

  report.apps[app.key] = {
    file: app.file,
    summary: {
      LF: cov.LF, LH: cov.LH, coveragePct: cov.LF ? +((cov.LH/cov.LF)*100).toFixed(2) : 0,
      totalFns: fnStats.length, uncoveredFnCount: uncoveredFns.filter(f=>f.hitCount===0).length,
      partiallyCoveredFnCount: uncoveredFns.filter(f=>f.hitCount>0 && f.uncoveredLines>0).length,
    },
    uncoveredFns: uncoveredFns.slice(0, 20),
  };
  // 累计 hotspots
  for (const f of uncoveredFns.slice(0, 10)) {
    report.topHotspots.push({ app: app.key, ...f });
  }
}

report.topHotspots.sort((a,b) => b.uncoveredLines - a.uncoveredLines);
report.topHotspots = report.topHotspots.slice(0, 15);

// ===== 终端输出 =====
const red = s=>`\x1b[31m${s}\x1b[0m`, yellow=s=>`\x1b[33m${s}\x1b[0m`, green=s=>`\x1b[32m${s}\x1b[0m`, bold=s=>`\x1b[1m${s}\x1b[0m`, dim=s=>`\x1b[2m${s}\x1b[0m`;
console.log(`\n${bold('========== 覆盖率深度缺口扫描 ==========')}`);
for (const app of APPS) {
  const a = report.apps[app.key];
  if (a.error) { console.log(`\n${bold(app.key)}: ${red(a.error)}`); continue; }
  console.log(`\n${bold(app.key)} [${a.file}]  覆盖率 ${a.summary.coveragePct}%  (LH=${a.summary.LH}/${a.summary.LF})  函数未覆盖=${a.summary.uncoveredFnCount}  部分覆盖=${a.summary.partiallyCoveredFnCount}`);
  console.log(`  Top 未覆盖/部分覆盖函数:`);
  for (const f of a.uncoveredFns.slice(0, 10)) {
    const tag = f.hitCount === 0 ? red('NOCALL') : yellow('PART');
    console.log(`    ${tag}  ${f.name.padEnd(28)}  L${f.start}-${f.end||'?'}  未覆盖=${String(f.uncoveredLines).padStart(3)} / 总=${String(f.totalLines).padStart(3)}  覆盖=${f.coveragePct}%`);
  }
}
console.log(`\n${bold('Top15 跨应用热点:')}`);
for (const h of report.topHotspots) {
  console.log(`  ${yellow(h.uncoveredLines+'行')}  ${h.app.padEnd(16)} ${h.name.padEnd(28)}  L${h.start}-${h.end||'?'}`);
}

// ===== 写产物 =====
fs.mkdirSync(path.dirname(path.resolve(JSON_OUT)), { recursive: true });
fs.writeFileSync(JSON_OUT, JSON.stringify(report, null, 2));
console.log(`\nJSON → ${JSON_OUT}`);

// markdown
function buildMD() {
  const L = [];
  L.push('# 链盛通 LSC 覆盖率深度缺口扫描报告');
  L.push('');
  L.push(`- 生成时间: ${report._generatedAt}`);
  L.push('');
  L.push('## 各应用覆盖率总览');
  L.push('');
  L.push('| 应用 | 文件 | 覆盖率 (LH/LF) | 未覆盖函数 | 部分覆盖函数 |');
  L.push('|---|---|---|---|---|');
  for (const app of APPS) {
    const a = report.apps[app.key];
    if (a.error) { L.push(`| ${app.key} | ${app.file} | ERROR | - | - |`); continue; }
    L.push(`| ${app.key} | ${a.file} | ${a.summary.coveragePct}% (${a.summary.LH}/${a.summary.LF}) | ${a.summary.uncoveredFnCount} | ${a.summary.partiallyCoveredFnCount} |`);
  }
  L.push('');
  L.push('## Top15 跨应用未覆盖热点');
  L.push('');
  L.push('| 排名 | 应用 | 函数名 | 起止行 | 未覆盖行数 |');
  L.push('|---|---|---|---|---|');
  report.topHotspots.forEach((h,i) => {
    L.push(`| ${i+1} | ${h.app} | \`${h.name}\` | L${h.start}-${h.end||'?'} | ${h.uncoveredLines} |`);
  });
  L.push('');
  for (const app of APPS) {
    const a = report.apps[app.key];
    if (a.error) continue;
    L.push(`## ${app.key} 详细未覆盖函数清单`);
    L.push('');
    L.push('| 函数名 | 起止行 | 总行数 | 未覆盖 | 覆盖率 | 状态 |');
    L.push('|---|---|---|---|---|---|');
    for (const f of a.uncoveredFns) {
      const tag = f.hitCount === 0 ? 'NOCALL' : 'PART';
      L.push(`| \`${f.name}\` | L${f.start}-${f.end||'?'} | ${f.totalLines} | ${f.uncoveredLines} | ${f.coveragePct}% | ${tag} |`);
    }
    L.push('');
  }
  return L.join('\n');
}
fs.mkdirSync(path.dirname(path.resolve(MD_OUT)), { recursive: true });
fs.writeFileSync(MD_OUT, buildMD());
console.log(`MD   → ${MD_OUT}`);
