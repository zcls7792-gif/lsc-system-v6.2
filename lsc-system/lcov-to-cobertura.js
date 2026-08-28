#!/usr/bin/env node
/**
 * lcov.info → Cobertura XML 转换器 (零依赖,纯 Node)
 *
 * 用途:
 *   - GitLab 15.3+ MR diff 逐行覆盖率高亮 (artifacts.reports.coverage_report.cobertura)
 *   - Jenkins Cobertura 插件 / SonarQube generic coverage
 *
 * 用法:
 *   node lcov-to-cobertura.js [inputLcov] [outputXml] [sourceRootPrefix]
 *
 *   inputLcov          默认 coverage/lcov.info
 *   outputXml          默认 coverage/cobertura.xml
 *   sourceRootPrefix   可选:在 SF:xxx 前加上的路径前缀(例如 lsc-system/)
 *
 * Cobertura DTD 要点 (gitlab 真正消费的字段):
 *   - <package><class name="path/to/file.js" filename="path/to/file.js">
 *   - <lines><line number="N" hits="H" branch="0|1" condition-coverage="P% (X/Y)"/></lines>
 *   - line-rate / branch-rate 放在 package 与 class 上,GitLab 会聚合
 *
 * 参考: coveragepy.cobertura / v8-to-istanbul + lcov
 */
'use strict';
const fs = require('fs');
const path = require('path');

function pct(h, f) {
  if (!f) return 1.0;
  const v = h / f;
  if (v < 0) return 0;
  if (v > 1) return 1;
  return v;
}
function toFixed(n, d) {
  return (+(n.toFixed(d))).toString();
}
function esc(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}

function parseLcov(text) {
  const files = [];
  let cur = null;
  const pushCur = () => {
    if (cur) {
      cur.brLines ||= {};
      cur.fnLines ||= {};
      files.push(cur);
    }
    cur = null;
  };
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trimEnd();
    if (!line) continue;
    if (line === 'end_of_record') { pushCur(); continue; }
    const colon = line.indexOf(':');
    if (colon < 0) continue;
    const key = line.slice(0, colon);
    const val = line.slice(colon + 1);
    switch (key) {
      case 'TN': break;
      case 'SF':
        if (cur) pushCur();
        cur = { file: val, lines: {}, branches: {}, fns: {} };
        break;
      case 'LF': cur.totalLines = +val; break;
      case 'LH': cur.hitLines = +val; break;
      case 'FNF': cur.totalFns = +val; break;
      case 'FNH': cur.hitFns = +val; break;
      case 'BRF': cur.totalBranches = +val; break;
      case 'BRH': cur.hitBranches = +val; break;
      case 'DA': {
        const [ln, hits, _rest] = val.split(',');
        const n = +ln;
        cur.lines[n] = (cur.lines[n] || 0) + (+hits);
        break;
      }
      case 'FNDA': {
        const [hits, name] = val.split(',');
        if (name) cur.fns[name] = (cur.fns[name] || 0) + (+hits);
        break;
      }
      case 'FN': {
        // lcov FN:<lineNum>,<name> → 仅记录函数声明所在行,给 XML class 的 methods 可选
        const [ln, name] = val.split(',');
        if (name && !cur.fnLines) cur.fnLines = {};
        if (name) cur.fnLines[name] = +ln;
        break;
      }
      case 'BRDA': {
        // BRDA:<line>,<block>,<branch>,<taken>     taken 可为 '-'
        const parts = val.split(',');
        const ln = +parts[0];
        const taken = parts[3] === '-' ? 0 : +parts[3];
        if (!cur.branches[ln]) cur.branches[ln] = [];
        cur.branches[ln].push(taken);
        break;
      }
    }
  }
  pushCur();
  return files;
}

function buildCobertura(files, sourceRoot) {
  const ts = Math.floor(Date.now() / 1000);
  // 全局聚合
  let aggLines = 0, aggLinesH = 0, aggBranches = 0, aggBranchesH = 0, aggFns = 0, aggFnsH = 0;
  const fileXml = [];
  for (const f of files) {
    const fname = sourceRoot ? `${sourceRoot.replace(/\/+$/,'')}/${f.file.replace(/^\/+/,'')}` : f.file;
    const linesNums = Object.keys(f.lines).map(Number).sort((a,b)=>a-b);
    const lineRate = pct(f.hitLines ?? linesNums.filter(n => f.lines[n] > 0).length,
                        f.totalLines ?? linesNums.length);
    // 分支级:对每个含分支的 line 计算
    const brEntries = Object.entries(f.branches || {});
    const brTotal = brEntries.reduce((s,[,arr])=>s+arr.length, 0);
    const brHit   = brEntries.reduce((s,[,arr])=>s+arr.filter(v=>v>0).length, 0);
    const branchRate = brTotal ? pct(brHit, brTotal) : 1;
    const fnKeys = Object.keys(f.fns || {});
    const fnTotal = f.totalFns ?? fnKeys.length;
    const fnHit   = f.hitFns ?? fnKeys.filter(k => (f.fns[k]||0) > 0).length;
    const fnRate = pct(fnHit, fnTotal);

    aggLines += (f.totalLines ?? linesNums.length);
    aggLinesH += (f.hitLines ?? linesNums.filter(n => f.lines[n] > 0).length);
    aggBranches += brTotal;
    aggBranchesH += brHit;
    aggFns += fnTotal;
    aggFnsH += fnHit;

    const linesXml = [];
    for (const n of linesNums) {
      const hits = f.lines[n] | 0;
      const hasBr = Array.isArray(f.branches[n]);
      if (hasBr) {
        const arr = f.branches[n];
        const t = arr.filter(v => v > 0).length;
        const cc = Math.round(1000 * t / arr.length) / 10;
        linesXml.push(`<line number="${n}" hits="${hits}" branch="true" condition-coverage="${cc}% (${t}/${arr.length})"/>`);
      } else {
        linesXml.push(`<line number="${n}" hits="${hits}" branch="false"/>`);
      }
    }

    // methods 段 (按 lcov FN / FNDA 汇总,可选;GitLab 其实不消费,但标准 Cobertura 更完整)
    const methodsXml = fnKeys.map(name => {
      const hits = f.fns[name] | 0;
      const ln = (f.fnLines && f.fnLines[name]) || 0;
      return `<method name="${esc(name)}" signature="" line-rate="${toFixed(hits>0?1:0,16)}" branch-rate="1.0" complexity="0"><lines><line number="${ln}" hits="${hits}"/></lines></method>`;
    }).join('');

    fileXml.push({
      fname,
      lineRate: toFixed(lineRate, 16),
      branchRate: toFixed(branchRate, 16),
      fnRate: toFixed(fnRate, 16),
      body:
`<class name="${esc(fname)}" filename="${esc(fname)}" line-rate="${toFixed(lineRate,16)}" branch-rate="${toFixed(branchRate,16)}" complexity="0">
<methods>${methodsXml}</methods>
<lines>
${linesXml.join('\n')}
</lines>
</class>`
    });
  }

  // package 级: 按文件所在的父目录分组 (GitLab 不在乎分组,但标准里必须有一层 package)
  const groups = new Map();
  for (const x of fileXml) {
    const dir = (x.fname.includes('/') ? x.fname.slice(0, x.fname.lastIndexOf('/')) : '.') || '.';
    if (!groups.has(dir)) groups.set(dir, []);
    groups.get(dir).push(x);
  }
  const packagesXml = [];
  for (const [pkg, items] of groups) {
    const pLineRate = pct(aggLinesH, aggLines); // 用整体而不是包内聚合,GitLab 更稳
    packagesXml.push(
`<package name="${esc(pkg.replace(/^\./,'(root)'))}" line-rate="${toFixed(pLineRate,16)}" branch-rate="${toFixed(pct(aggBranchesH,aggBranches),16)}" complexity="0">
<classes>
${items.map(x=>x.body).join('\n')}
</classes>
</package>`
    );
  }

  const gl = toFixed(pct(aggLinesH, aggLines), 16);
  const gb = toFixed(pct(aggBranchesH, aggBranches), 16);
  const gm = toFixed(pct(aggFnsH, aggFns), 16);

  return `<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE coverage SYSTEM "http://cobertura.sourceforge.net/xml/coverage-04.dtd">
<coverage line-rate="${gl}" branch-rate="${gb}" complexity="0" version="lcov-to-cobertura-lsc-v6" lines-covered="${aggLinesH}" lines-valid="${aggLines}" branches-covered="${aggBranchesH}" branches-valid="${aggBranches}" timestamp="${ts}">
<sources>
<source>${esc(sourceRoot || '.')}</source>
</sources>
<packages>
${packagesXml.join('\n')}
</packages>
</coverage>
`;
}

function main() {
  const args = process.argv.slice(2);
  const inPath  = args[0] || process.env.LCOV_INPUT  || 'coverage/lcov.info';
  const outPath = args[1] || process.env.COBERTURA_OUTPUT || 'coverage/cobertura.xml';
  const prefix  = args[2] || process.env.COVERAGE_SOURCE_PREFIX || '';

  if (!fs.existsSync(inPath)) {
    console.error('[lcov-to-cobertura] input not found:', inPath);
    process.exit(1);
  }
  const text = fs.readFileSync(inPath, 'utf8');
  const files = parseLcov(text);
  const xml = buildCobertura(files, prefix);
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, xml);

  // 汇总打印
  let lh=0,lf=0,bh=0,bf=0,fh=0,ff=0;
  for (const f of files) {
    const nums = Object.keys(f.lines).map(Number);
    lf += (f.totalLines ?? nums.length);
    lh += (f.hitLines ?? nums.filter(n => f.lines[n] > 0).length);
    const entries = Object.entries(f.branches || {});
    for (const [,arr] of entries) { bf += arr.length; bh += arr.filter(v=>v>0).length; }
    const ks = Object.keys(f.fns || {});
    ff += (f.totalFns ?? ks.length);
    fh += (f.hitFns ?? ks.filter(k => (f.fns[k]||0) > 0).length);
  }
  const p = (h,f)=> (f? (100*h/f).toFixed(2): '100.00') + '%';
  console.log(`[lcov-to-cobertura] OK: ${files.length} files → ${outPath}`);
  console.log(`  lines:    ${p(lh,lf)}  (${lh}/${lf})`);
  console.log(`  branches: ${p(bh,bf)}  (${bh}/${bf})`);
  console.log(`  funcs:    ${p(fh,ff)}  (${fh}/${ff})`);
}

if (require.main === module) main();

module.exports = { parseLcov, buildCobertura };
