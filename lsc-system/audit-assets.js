#!/usr/bin/env node
/**
 * audit-assets.js — 前端资源体量分析与门控告警
 *
 * 扫描 4 应用目录下的 CSS/JS 资源:
 *   - 文件数、原始大小 (raw)
 *   - gzip 估算大小 (node zlib gzip level 9)
 *   - 按资源类型、应用维度汇总
 *
 * 阈值 (字节)：
 *   默认阈值 (可通过环境变量覆盖)：
 *     ASSETS_LIMIT_JS_PER_APP       # 单应用 JS 压缩后大小 (默认 200 KiB)
 *     ASSETS_LIMIT_CSS_PER_APP      # 单应用 CSS 压缩后大小 (默认 50 KiB)
 *     ASSETS_LIMIT_ANY_FILE_GZIP    # 单文件 gzip 绝对上限 (默认 180 KiB)
 *     ASSETS_LIMIT_TOTAL_GZIP       # 全部 4 应用+共享 gzip 总和上限 (默认 800 KiB)
 *
 * 用法: node audit-assets.js [--strict] [--json <path>] [--md <path>]
 *   --strict  : 任何 WARN 按 FAIL 处理 (默认 WARN 仅打印)
 *
 * 退出: 0=全部通过  1=存在 FAIL (strict 下含 WARN)
 */
'use strict';
const fs = require('fs');
const path = require('path');
const zlib = require('zlib');

const ROOT = path.resolve(__dirname);
const STRICT = process.argv.includes('--strict') || process.env.ASSETS_AUDIT_STRICT === '1';
const JSON_OUT = (() => { const i=process.argv.indexOf('--json'); return i>=0 ? process.argv[i+1] : null; })();
const MD_OUT   = (() => { const i=process.argv.indexOf('--md');   return i>=0 ? process.argv[i+1] : null; })();

// 默认阈值 (字节)
const KiB = (n) => n * 1024;
const TH = {
  jsPerApp:     parseInt(process.env.ASSETS_LIMIT_JS_PER_APP     || '') || KiB(200),
  cssPerApp:    parseInt(process.env.ASSETS_LIMIT_CSS_PER_APP    || '') || KiB(50),
  anyFileGzip:  parseInt(process.env.ASSETS_LIMIT_ANY_FILE_GZIP  || '') || KiB(180),
  totalGzip:    parseInt(process.env.ASSETS_LIMIT_TOTAL_GZIP     || '') || KiB(800),
};

const APPS = [
  { key:'platform-admin', title:'平台管理后台' },
  { key:'merchant-admin', title:'商家管理后台' },
  { key:'mobile-app',     title:'消费者移动端APP' },
  { key:'mini-program',   title:'微信小程序端' },
  { key:'shared',         title:'共享资源 (design-system.css/app-utils.js)' },
];

function fmtBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes/1024).toFixed(2)} KiB`;
  return `${(bytes/1024/1024).toFixed(2)} MiB`;
}
function ratioPercent(orig, gz) {
  if (orig === 0) return '0.0%';
  return `${((gz/orig)*100).toFixed(1)}%`;
}

function listFiles(dir, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const fp = path.join(dir, e.name);
    if (e.isDirectory()) listFiles(fp, out);
    else out.push(fp);
  }
  return out;
}

function measure(file) {
  const raw = fs.readFileSync(file);
  const gz = zlib.gzipSync(raw, { level: 9 });
  return { raw: raw.length, gzip: gz.length };
}

// ===== 采集 & 汇总 =====
const allAppResults = {};
const allFiles = [];
let totalGzip = 0, totalRaw = 0;

for (const app of APPS) {
  const dir = app.key === 'shared'
    ? path.join(ROOT, 'shared')
    : path.join(ROOT, app.key);
  const files = listFiles(dir).filter(f => /\.(css|js)$/i.test(f));
  const per = {
    title: app.title,
    dir: path.relative(ROOT, dir),
    count: 0,
    files: [],
    raw: { total: 0, js: 0, css: 0 },
    gzip: { total: 0, js: 0, css: 0 },
  };
  for (const f of files) {
    const { raw, gzip } = measure(f);
    const ext = (/\.([a-z0-9]+)$/i.exec(f) || [,''])[1].toLowerCase();
    per.count++;
    per.raw.total += raw;
    per.gzip.total += gzip;
    if (ext === 'js')  { per.raw.js += raw;  per.gzip.js += gzip; }
    if (ext === 'css') { per.raw.css += raw; per.gzip.css += gzip; }
    per.files.push({
      file: path.relative(ROOT, f),
      ext, raw, gzip,
      ratio: raw === 0 ? 0 : Math.round((gzip/raw)*10000)/100,
    });
    allFiles.push({ app: app.key, ext, raw, gzip, file: path.relative(ROOT, f) });
  }
  // 按 gzip 大小 desc
  per.files.sort((a,b) => b.gzip - a.gzip);
  totalRaw += per.raw.total;
  totalGzip += per.gzip.total;
  allAppResults[app.key] = per;
}

// ===== 告警/断言 =====
const alerts = []; // { level, app?, file?, kind, msg, have, limit }
function push(level, opts) { alerts.push({ level, ...opts }); }

// 1. 应用维度 JS/CSS gzip 上限 (shared 只算绝对单文件)
for (const app of APPS) {
  const p = allAppResults[app.key];
  if (app.key !== 'shared') {
    if (p.gzip.js  > TH.jsPerApp)  push(STRICT ? 'FAIL' : 'WARN', {
      app: app.key, kind:'JS_PER_APP', have: p.gzip.js,  limit: TH.jsPerApp,
      msg: `${app.title} JS gzip 总和 ${fmtBytes(p.gzip.js)} > 阈值 ${fmtBytes(TH.jsPerApp)}`
    });
    if (p.gzip.css > TH.cssPerApp) push(STRICT ? 'FAIL' : 'WARN', {
      app: app.key, kind:'CSS_PER_APP', have: p.gzip.css, limit: TH.cssPerApp,
      msg: `${app.title} CSS gzip 总和 ${fmtBytes(p.gzip.css)} > 阈值 ${fmtBytes(TH.cssPerApp)}`
    });
  }
}
// 2. 单文件 gzip 绝对上限
for (const f of allFiles) {
  if (f.gzip > TH.anyFileGzip) push(STRICT ? 'FAIL' : 'WARN', {
    file: f.file, kind:'SINGLE_FILE_GZIP', have: f.gzip, limit: TH.anyFileGzip,
    msg: `文件 ${f.file} gzip ${fmtBytes(f.gzip)} > 单文件上限 ${fmtBytes(TH.anyFileGzip)}`
  });
}
// 3. 全部资源 gzip 总和
if (totalGzip > TH.totalGzip) push(STRICT ? 'FAIL' : 'WARN', {
  kind:'TOTAL_GZIP', have: totalGzip, limit: TH.totalGzip,
  msg: `全部应用+共享 gzip 总和 ${fmtBytes(totalGzip)} > 阈值 ${fmtBytes(TH.totalGzip)}`
});

const failCount = alerts.filter(a => a.level === 'FAIL').length;
const warnCount = alerts.filter(a => a.level === 'WARN').length;
const passScore = 100 - Math.min(100, Math.round(
  (alerts.filter(a=>a.level==='FAIL').length * 15 + alerts.filter(a=>a.level==='WARN').length * 5)
));

// ===== 终端输出 =====
const red = s => `\x1b[31m${s}\x1b[0m`;
const green = s => `\x1b[32m${s}\x1b[0m`;
const yellow = s => `\x1b[33m${s}\x1b[0m`;
const dim = s => `\x1b[2m${s}\x1b[0m`;
const bold = s => `\x1b[1m${s}\x1b[0m`;

console.log(`\n${bold('========== 链盛通 LSC 前端资产体量审计 ==========')}  strict=${STRICT?'ON':'OFF'}`);
console.log(`阈值: 单应用JS≤${fmtBytes(TH.jsPerApp)}  CSS≤${fmtBytes(TH.cssPerApp)} | 单文件 gzip≤${fmtBytes(TH.anyFileGzip)} | 全量 gzip≤${fmtBytes(TH.totalGzip)}`);
console.log('');
for (const app of APPS) {
  const p = allAppResults[app.key];
  console.log(`${bold(p.title)} [${p.dir}]  count=${p.count}`);
  console.log(`  RAW:  total=${fmtBytes(p.raw.total)}  js=${fmtBytes(p.raw.js)}  css=${fmtBytes(p.raw.css)}`);
  console.log(`  GZIP: total=${fmtBytes(p.gzip.total)} js=${fmtBytes(p.gzip.js)} css=${fmtBytes(p.gzip.css)}  (压缩率 total=${ratioPercent(p.raw.total,p.gzip.total)})`);
  // Top 3 最大
  if (p.files.length) {
    console.log(`  Top3 by gzip:`);
    p.files.slice(0,3).forEach(f => {
      const over = f.gzip > TH.anyFileGzip;
      console.log(`    ${over?red('▌'):' '}  ${fmtBytes(f.gzip).padEnd(12)} raw=${fmtBytes(f.raw).padEnd(12)} ${f.ratio}%  ${dim(f.file)}`);
    });
  }
}
console.log('');
console.log(`${bold('合计:')} RAW=${fmtBytes(totalRaw)}   GZIP=${fmtBytes(totalGzip)}   压缩率=${ratioPercent(totalRaw, totalGzip)}`);
console.log('');
if (alerts.length === 0) {
  console.log(green('✓ 无告警。所有体量指标均低于阈值。'));
} else {
  console.log(`${bold('告警列表:')}`);
  for (const a of alerts) {
    const col = a.level === 'FAIL' ? red : yellow;
    console.log(`  ${col(a.level.padEnd(4))}  ${a.kind.padEnd(16)}  ${a.msg}`);
  }
}
const overall = failCount>0 ? red('FAIL') : (warnCount>0?yellow('WARN'):green('PASS'));
console.log(`\n${bold('结果:')} ${overall}   FAIL=${failCount} WARN=${warnCount}   Score=${passScore}/100`);

// ===== Markdown =====
function buildMD() {
  const L = [];
  L.push('# 链盛通 LSC 前端资产体量审计报告');
  L.push('');
  L.push(`- 生成时间: ${new Date().toISOString()}`);
  L.push(`- 严格模式: ${STRICT ? '开启' : '关闭'}`);
  L.push(`- 结果: FAIL=${failCount} WARN=${warnCount}  Score=${passScore}/100`);
  L.push(`- 合计: RAW=${fmtBytes(totalRaw)}  GZIP=${fmtBytes(totalGzip)}  压缩率=${ratioPercent(totalRaw,totalGzip)}`);
  L.push('');
  L.push('## 阈值');
  L.push('');
  L.push(`| 项 | 阈值 | 实际 | 状态 |`);
  L.push(`|---|---|---|---|`);
  const checks = [
    ['单应用 JS gzip 总和', `≤${fmtBytes(TH.jsPerApp)}`, '', ''],
    ['单应用 CSS gzip 总和', `≤${fmtBytes(TH.cssPerApp)}`, '', ''],
    ['单文件 gzip 绝对上限', `≤${fmtBytes(TH.anyFileGzip)}`, '', ''],
    ['全部应用+共享 gzip 总和', `≤${fmtBytes(TH.totalGzip)}`, fmtBytes(totalGzip), totalGzip<=TH.totalGzip?'PASS':'FAIL'],
  ];
  checks.forEach(r => L.push(`| ${r[0]} | ${r[1]} | ${r[2]||'-'} | ${r[3]||'-'} |`));
  L.push('');
  for (const app of APPS) {
    const p = allAppResults[app.key];
    L.push(`## ${p.title} (${p.dir})`);
    L.push('');
    L.push(`- 资源文件数: ${p.count}`);
    L.push(`- RAW  total: ${fmtBytes(p.raw.total)}  (JS ${fmtBytes(p.raw.js)} / CSS ${fmtBytes(p.raw.css)})`);
    L.push(`- GZIP total: ${fmtBytes(p.gzip.total)} (JS ${fmtBytes(p.gzip.js)} / CSS ${fmtBytes(p.gzip.css)})`);
    L.push(`- 压缩率: ${ratioPercent(p.raw.total,p.gzip.total)}`);
    L.push('');
    if (p.files.length) {
      L.push('| 文件 | 类型 | RAW | GZIP | 压缩率 |');
      L.push('|---|---|---:|---:|---:|');
      p.files.forEach(f => {
        L.push(`| \`${f.file}\` | ${f.ext.toUpperCase()} | ${fmtBytes(f.raw)} | ${fmtBytes(f.gzip)} | ${f.ratio}% |`);
      });
      L.push('');
    }
  }
  if (alerts.length) {
    L.push('## 告警');
    L.push('');
    L.push('| 级别 | 类型 | 说明 |');
    L.push('|---|---|---|');
    alerts.forEach(a => L.push(`| ${a.level} | ${a.kind} | ${a.msg} |`));
    L.push('');
  }
  return L.join('\n') + '\n';
}

// ===== 写产物 =====
if (JSON_OUT) {
  const out = JSON.stringify({
    strict: STRICT,
    thresholds: TH,
    score: passScore,
    counts: { fail: failCount, warn: warnCount, files: allFiles.length },
    totals: { raw: totalRaw, gzip: totalGzip, compressRatio: totalRaw?+(totalGzip/totalRaw).toFixed(4):0 },
    apps: allAppResults,
    alerts,
    _generatedAt: new Date().toISOString(),
  }, null, 2);
  fs.mkdirSync(path.dirname(path.resolve(JSON_OUT)), { recursive: true });
  fs.writeFileSync(JSON_OUT, out);
  console.log(`\nJSON → ${JSON_OUT}`);
}
if (MD_OUT) {
  fs.mkdirSync(path.dirname(path.resolve(MD_OUT)), { recursive: true });
  fs.writeFileSync(MD_OUT, buildMD());
  console.log(`MD   → ${MD_OUT}`);
}

const exitCode = failCount > 0 ? 1 : 0;
console.log(`\nexit=${exitCode}`);
process.exit(exitCode);
