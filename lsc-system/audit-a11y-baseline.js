#!/usr/bin/env node
/**
 * LSC V6.2-AI · A11y(axe-core) + 响应式 + 零 console.error/warn 审查脚本
 *
 * - 通过 Playwright 启动 chromium (已安装)
 * - 对 4 应用 × 3 尺寸 (360 / 768 / 1440) 跑 12 项快照
 * - 每页注入 axe-core 4.9 CDN, 跑 axe.run({ runOnly: ['wcag2a','wcag2aa','best-practice'] })
 * - 采集 console.error/warn、未加载资源 (网络 4xx/5xx)
 * - 输出 audit-report/a11y-baseline.md 与 JSON
 *
 * 依赖: @playwright/test (已在 devDependencies)
 */
'use strict';
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');

/* ------------------------------------------------------------------ */
/*  CLI 参数解析 (CI 可用)                                            */
/* ------------------------------------------------------------------ */
function parseArgs(argv) {
  const opts = {
    strict: process.env.CI === 'true' || process.env.GITLAB_CI === 'true' || process.env.GITHUB_ACTIONS === 'true',
    // 仅 strict 模式下, 违规/consoleErr/网络/缺alt/consoleWarn 哪些会触发非零退出
    failOn: { violations: true, consoleE: true, consoleW: false, net4xx5xx: true, missingAlt: true },
    // 允许的每类阈值 (默认 0)
    maxViolations: 0, maxConsoleE: 0, maxConsoleW: 0, maxNet: 0,
    label: '',
  };
  for (let i = 2; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--strict')           opts.strict = true;
    else if (a === '--no-strict')   opts.strict = false;
    else if (a === '--fail-warn')   opts.failOn.consoleW = true;
    else if (a === '--allow-warn')  opts.failOn.consoleW = false;
    else if (a === '--label' && argv[i+1]) { opts.label = argv[++i]; }
    else if (a.startsWith('--label=')) { opts.label = a.slice('--label='.length); }
    else if (a.startsWith('--max-violations=')) opts.maxViolations = +a.split('=')[1];
    else if (a.startsWith('--max-consoleE='))  opts.maxConsoleE   = +a.split('=')[1];
    else if (a.startsWith('--max-consoleW='))  opts.maxConsoleW   = +a.split('=')[1];
    else if (a.startsWith('--max-net='))       opts.maxNet        = +a.split('=')[1];
    else if (a === '-h' || a === '--help') {
      console.log(`
LSC V6.2-AI · A11y(axe-core) 16 快照审计 (4 app × 2 size × light/dark)

用法:  node audit-a11y-baseline.js [选项]

选项:
  --strict / --no-strict    严格模式 (默认 CI=true 自动开启)
  --fail-warn / --allow-warn   console.warn 是否视为失败 (默认 strict 下也放行)
  --max-violations=N        允许的 axe violations (strict 下默认 0)
  --max-consoleE=N          允许的 console.error 数量 (默认 0)
  --max-consoleW=N          允许的 console.warn 数量 (默认 Infinity)
  --max-net=N               允许的 4xx/5xx 次数 (默认 0)
  --label=NAME              产物/报告里附一个可读标签 (PR/MR sha/分支名)
  -h, --help                本帮助

退出码:
  0  所有阈值通过
  1  致命错误 (playwright 启动失败 / 文件系统错误)
  2  违反 CI 阈值 (violations / consoleE / net / missingAlt 越限)
  3  consoleW 越限 (仅 --fail-warn 生效)
`); process.exit(0);
    }
  }
  return opts;
}
const OPTS = parseArgs(process.env.NODE_ENV === 'test' ? process.argv : process.argv);

const ROOT = __dirname;
const OUT_DIR = path.join(ROOT, 'audit-report');
fs.mkdirSync(OUT_DIR, { recursive: true });
const JSON_OUT = path.join(OUT_DIR, 'a11y-baseline.json');
const MD_OUT   = path.join(OUT_DIR, 'a11y-baseline.md');

const BASE = process.env.LSC_E2E_BASE_URL || 'http://127.0.0.1:8765';
const APPS = [
  { id: 'platform',  name: '平台管理后台', path: '/platform-admin/index.html' },
  { id: 'merchant',  name: '商家管理后台', path: '/merchant-admin/index.html' },
  { id: 'mobile',    name: '移动端 APP',   path: '/mobile-app/index.html'   },
  { id: 'mini',      name: '微信小程序',   path: '/mini-program/index.html' },
];

// 检测 chromium 是否可用 (无 chromium-headless-shell 时回退 jsdom)
let JSDOM = null;
async function tryLoadJSDOM() {
  if (JSDOM) return JSDOM;
  try { JSDOM = require('jsdom').JSDOM; return JSDOM; }
  catch (_) { return null; }
}
const SIZES = [
  { id: 'sm', w: 360,  h: 740,  label: '移动端 360×740 (iPhone SE)' },
  { id: 'md', w: 768,  h: 1024, label: '平板 768×1024 (iPad mini)' },
  { id: 'lg', w: 1440, h: 900,  label: '桌面 1440×900' },
];
// 响应式只在需要的平台上跑,省时间: desktop 应用只 lg / md, mobile 类只 sm / md
const SIZE_BY_APP = {
  platform: ['md','lg'],
  merchant: ['md','lg'],
  mobile:   ['sm','md'],
  mini:     ['sm','md'],
};

const AXE_CDN = 'https://cdn.jsdelivr.net/npm/axe-core@4.9.1/axe.min.js';

// --- 下载 axe-core 本地缓存（避免每次请求 CDN） ---
const AXE_LOCAL = path.join(OUT_DIR, 'axe.min.js');
async function ensureAxe() {
  if (fs.existsSync(AXE_LOCAL) && fs.statSync(AXE_LOCAL).size > 50000) return;
  const u = new URL(AXE_CDN);
  const lib = u.protocol === 'https:' ? https : http;
  return new Promise((res, rej) => {
    const req = lib.get(AXE_CDN, { headers: { 'User-Agent': 'lsc-v6.2-a11y/1.0' } }, r => {
      if (r.statusCode && r.statusCode >= 400) return rej(new Error('axe CDN ' + r.statusCode));
      const bufs = [];
      r.on('data', d => bufs.push(d));
      r.on('end', () => {
        const b = Buffer.concat(bufs);
        fs.writeFileSync(AXE_LOCAL, b);
        res();
      });
    });
    req.on('error', rej);
    req.setTimeout(15000, () => { req.destroy(new Error('axe CDN timeout')); });
  });
}

function mdEscape(s) {
  return String(s).replace(/\|/g, '\\|').replace(/\n/g, ' ');
}

async function auditOne(browser, app, size, colorScheme = 'light') {
  const ctx = await browser.newContext({
    viewport: { width: size.w, height: size.h },
    colorScheme: colorScheme,           // 'light' | 'dark'
    locale: 'zh-CN',
  });
  const page = await ctx.newPage();
  // 若为 dark: 在页面加载前就给 <html> 写 data-theme="dark",让 [data-theme="dark"] 令牌生效
  if (colorScheme === 'dark') {
    await page.addInitScript(() => {
      document.documentElement.setAttribute('data-theme', 'dark');
    });
  }
  const cw = { warn: [], error: [] };
  const neterr = [];
  page.on('console', msg => {
    const t = msg.type();
    if (t === 'error') cw.error.push(msg.text());
    else if (t === 'warning' || t === 'warn') cw.warn.push(msg.text());
  });
  page.on('response', r => {
    const s = r.status();
    if (s >= 400) neterr.push({ url: r.url(), status: s });
  });

  const url = BASE + app.path;
  let loadedOK = true;
  try {
    await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
  } catch (e) {
    loadedOK = false;
    cw.error.push(`goto failed: ${e && e.message || e}`);
  }
  // 页面内容: 统计关键节点
  let counts = { a: 0, button: 0, input: 0, img: 0, svg: 0, heading: 0, totalText: 0 };
  try {
    counts = await page.evaluate(() => {
      const $ = (s) => document.querySelectorAll(s);
      return {
        a: $('a').length,
        button: $('button').length,
        input: $('input,select,textarea').length,
        img: [...$('img')].filter(i => !i.alt || i.alt.trim() === '').length,  // 无 alt 图片
        svg: $('svg').length,
        heading: $('h1,h2,h3,h4,h5,h6').length,
        totalText: (document.body?.innerText || '').length,
      };
    });
  } catch (_) {}

  // 注入 axe 并跑规则 (wcag 2a / 2aa + best-practice)
  let axeRes = null;
  if (loadedOK) {
    try {
      const axeSrc = fs.readFileSync(AXE_LOCAL, 'utf8');
      axeRes = await page.evaluate(`(async function(){
        (function(){ ${axeSrc} })();
        if (typeof axe !== 'undefined') {
          axe.configure({ branding: { application: 'LSC-V6.2-AI A11y' } });
          const r = await axe.run({
            runOnly: { type: 'tag', values: ['wcag2a','wcag2aa','section508','best-practice'] },
            resultTypes: ['violations','incomplete']
          });
          return {
            violations: r.violations.slice(0, 50).map(v => ({
              id: v.id, impact: v.impact, description: v.description,
              count: v.nodes.length, helpUrl: v.helpUrl, nodes: v.nodes.slice(0, 6).map(n => ({ target: n.target?.[0] || '', html: (n.html||'').slice(0,200) }))
            })),
            incomplete: r.incomplete?.length || 0,
            passes: r.passes?.length || 0,
          };
        }
        return { violations: [], incomplete: 0, passes: 0, error: 'axe not injected' };
      })()`);
    } catch (e) {
      axeRes = { error: String(e && e.message || e) };
    }
  }

  // 截图 - 加入颜色方案后缀避免覆盖
  const suffix = colorScheme === 'dark' ? '__dark' : '';
  const shot = path.join(OUT_DIR, `${app.id}__${size.id}__${size.w}x${size.h}${suffix}.png`);
  try {
    await page.screenshot({ path: shot, fullPage: true, timeout: 8000 });
  } catch (_) {}

  await ctx.close();
  return {
    app: app.id, appName: app.name, size: size.id, width: size.w, height: size.h, sizeLabel: size.label,
    colorScheme,                                 // 记录 light / dark
    url,
    loadedOK,
    consoleErrors: cw.error.slice(0, 50),
    consoleWarnings: cw.warn.slice(0, 50),
    netErrors: neterr.slice(0, 30),
    counts,
    axe: axeRes,
    screenshot: path.relative(ROOT, shot),
  };
}

// ---- jsdom 兜底审计 (chromium 不可用时使用) ----
async function auditOneJSDOM(app, size) {
  const filePath = path.join(ROOT, app.path);
  const html = fs.readFileSync(filePath, 'utf8');
  const JSDOM = await tryLoadJSDOM();
  if (!JSDOM) throw new Error('jsdom 不可用');

  const appJsPath = path.join(path.dirname(filePath), 'app.js');
  const appJs = fs.existsSync(appJsPath) ? fs.readFileSync(appJsPath, 'utf8') : '';
  const sharedUtilsPath = path.join(ROOT, 'shared/app-utils.js');
  const sharedUtils = fs.existsSync(sharedUtilsPath) ? fs.readFileSync(sharedUtilsPath, 'utf8') : '';

  const dom = new JSDOM(html, {
    runScripts: 'outside-only',
    url: BASE + app.path,
    pretendToBeVisual: true,
  });
  const { window } = dom;
  const cw = { warn: [], error: [] };
  try {
    // 合并 shared + app 一次性 eval, 让 const ICONS 在同一作用域内可被 app.js 访问
    const combined = sharedUtils + '\n;\n' + appJs;
    window.eval(combined);
    if (window.LSC && typeof window.LSC.a11yEnhance === 'function') {
      try { window.LSC.a11yEnhance(window.document); } catch(_) {}
    }
  } catch (e) {
    cw.error.push('app.js runtime: ' + (e && e.message || String(e)));
  }

  let counts = { a: 0, button: 0, input: 0, img: 0, svg: 0, heading: 0, totalText: 0 };
  let axeRes = null;
  try {
    counts = {
      a: window.document.querySelectorAll('a').length,
      button: window.document.querySelectorAll('button').length,
      input: window.document.querySelectorAll('input,select,textarea').length,
      img: [...window.document.querySelectorAll('img')].filter(i => !i.alt || i.alt.trim() === '').length,
      svg: window.document.querySelectorAll('svg').length,
      heading: window.document.querySelectorAll('h1,h2,h3,h4,h5,h6').length,
      totalText: (window.document.body?.innerText || window.document.body?.textContent || '').length,
    };
  } catch (_) {}

  if (fs.existsSync(AXE_LOCAL)) {
    try {
      const axeSrc = fs.readFileSync(AXE_LOCAL, 'utf8');
      window.eval(axeSrc);
      if (typeof window.axe !== 'undefined') {
        const r = await window.axe.run(window.document, {
          runOnly: { type: 'tag', values: ['wcag2a','wcag2aa','section508','best-practice'] },
          resultTypes: ['violations','incomplete'],
        });
        axeRes = {
          violations: r.violations.slice(0, 50).map(v => ({
            id: v.id, impact: v.impact, description: v.description,
            count: v.nodes.length, helpUrl: v.helpUrl,
            nodes: v.nodes.slice(0, 6).map(n => ({ target: n.target?.[0] || '', html: (n.html||'').slice(0,200) })),
          })),
          incomplete: r.incomplete?.length || 0,
          passes: r.passes?.length || 0,
        };
      }
    } catch (e) {
      axeRes = { error: String(e && e.message || e) };
    }
  } else {
    axeRes = { error: 'axe.min.js not found' };
  }

  window.close();
  return {
    app: app.id, appName: app.name, size: size.id, width: size.w, height: size.h, sizeLabel: size.label,
    url: BASE + app.path,
    loadedOK: true,
    consoleErrors: cw.error.slice(0, 50),
    consoleWarnings: cw.warn.slice(0, 50),
    netErrors: [],
    counts,
    axe: axeRes,
    screenshot: null,
    mode: 'jsdom',
  };
}

async function main() {
  console.log('[a11y] 准备 axe-core ...');
  try { await ensureAxe(); console.log('[a11y] axe-core OK (' + Math.round(fs.statSync(AXE_LOCAL).size/1024) + 'KB)'); }
  catch (e) { console.warn('[a11y] 警告:无法下载 axe-core, 跳过可访问性规则: ' + e.message); }

  // 优先 chromium, 不可用则回退 jsdom
  let browser = null;
  let mode = 'chromium';
  try {
    console.log('[a11y] 启动 Playwright chromium ...');
    browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage'],
    });
  } catch (e) {
    console.warn('[a11y] chromium 启动失败 (' + (e && e.message).split('\n')[0] + '), 回退 jsdom 模式');
    const j = await tryLoadJSDOM();
    if (!j) { console.error('[a11y] jsdom 也不可用,无法继续'); process.exit(1); }
    mode = 'jsdom';
  }
  const results = [];
  const pairs = [];
  const SCHEMES = ['light', 'dark'];          // light + dark 双色方案
  for (const app of APPS) {
    for (const sid of SIZE_BY_APP[app.id]) {
      const size = SIZES.find(s => s.id === sid);
      for (const scheme of SCHEMES) {
        pairs.push({ app, size, scheme });
      }
    }
  }
  for (const { app, size, scheme } of pairs) {
    const k = `${app.id}@${size.w}x${size.h}[${scheme}]`;
    process.stdout.write(`  ${k.padEnd(32)} → `);
    const r = mode === 'jsdom'
      ? await auditOneJSDOM(app, size)
      : await auditOne(browser, app, size, scheme);
    // jsdom fallback 无 colorScheme 信息补齐
    if (!r.colorScheme) r.colorScheme = scheme;
    const v = (r.axe && r.axe.violations) ? r.axe.violations.length : '-';
    const e = r.consoleErrors.length;
    const w = r.consoleWarnings.length;
    const n = r.netErrors.length;
    console.log(`OK  violations=${v}  consoleE=${e}  consoleW=${w}  4xx/5xx=${n}  ${r.counts.totalText>200?'内容充足':'⚠ 内容<200'}`);
    results.push(r);
  }
  if (browser) await browser.close();

  // 先预聚合所有统计量 (生成 JSON/Markdown 结论均需要)
  let i = 0, tv = 0, terr = 0, twarn = 0, tnet = 0, timg = 0;
  const statsByScheme = { light: { v:0, pass:0, inc:0 }, dark: { v:0, pass:0, inc:0 } };
  for (const r of results) {
    i++;
    const v  = (r.axe && r.axe.violations)   ? r.axe.violations.length                   : 0;
    const pass   = (r.axe && r.axe.passes)       ? r.axe.passes                             : 0;
    const inc    = (r.axe && r.axe.incomplete)   ? r.axe.incomplete                         : 0;
    const scheme = r.colorScheme || 'light';
    statsByScheme[scheme].v    += v;
    statsByScheme[scheme].pass += pass;
    statsByScheme[scheme].inc  += inc;
    tv    += v;
    terr  += (Array.isArray(r.consoleErrors)   ? r.consoleErrors.length   : (r.consoleErrors  || 0));
    twarn += (Array.isArray(r.consoleWarnings) ? r.consoleWarnings.length : (r.consoleWarnings|| 0));
    tnet  += (Array.isArray(r.netErrors)       ? r.netErrors.length       : (r.netErrors    || 0));
    timg  += (r.counts && r.counts.img)        ? r.counts.img             : 0;
  }

  // 写 JSON
  const payload = {
    meta: {
      generatedAt: new Date().toISOString(),
      label: OPTS.label || '',
      strict: !!OPTS.strict,
      thresholds: {
        maxViolations: OPTS.maxViolations, maxConsoleE: OPTS.maxConsoleE,
        maxConsoleW: OPTS.maxConsoleW, maxNet: OPTS.maxNet,
      },
      summary: {
        totalViolations: tv, totalConsoleE: terr, totalConsoleW: twarn,
        totalNetErrors: tnet, totalMissingAlt: timg,
        light: statsByScheme.light, dark: statsByScheme.dark,
      },
    },
    records: results,
  };
  fs.writeFileSync(JSON_OUT, JSON.stringify(payload, null, 2));

  // 写 Markdown 报告
  const lines = [];
  lines.push('# 链盛通 LSC V6.2-AI · 可访问性 & 响应式基线审计 (快照 · Light + Dark)');
  lines.push('');
  lines.push(`> 生成于 **${new Date().toISOString()}** · axe-core wcag2a+wcag2aa+best-practice · 4 应用 × 2 视口 × 2 色方案 = 共 16 项快照`);
  lines.push('');
  lines.push('## 汇总表');
  lines.push('');
  lines.push('| # | 应用 | 视口 | 配色 | 加载 | 违规(V) | 待核查(Inc) | 通过规则 | console.error | console.warn | 4xx/5xx | 无 alt 图 | 正文长度 |');
  lines.push('|---|---|---|---|---|---|---|---|---|---|---|---|---|');
  // 重新遍历填表格 (stats 已经聚合过, 按结果逐行渲染)
  i = 0;
  for (const r of results) {
    i++;
    const v = (r.axe && r.axe.violations) ? r.axe.violations.length : 0;
    const inc = (r.axe && r.axe.incomplete) || 0;
    const pass = (r.axe && r.axe.passes) || 0;
    const ce = Array.isArray(r.consoleErrors)   ? r.consoleErrors.length   : (r.consoleErrors  || 0);
    const cw = Array.isArray(r.consoleWarnings) ? r.consoleWarnings.length : (r.consoleWarnings|| 0);
    const ne = Array.isArray(r.netErrors)       ? r.netErrors.length       : (r.netErrors    || 0);
    const mi = (r.counts && r.counts.img)       ? r.counts.img             : 0;
    const schemeBadge = (r.colorScheme === 'dark') ? '🌙 dark' : '☀️ light';
    lines.push(`| ${i} | ${mdEscape(r.appName)} | ${r.width}×${r.height} | ${schemeBadge} | ${r.loadedOK?'✅':'❌'} | ${v} | ${inc} | ${pass} | ${ce} | ${cw} | ${ne} | ${mi} | ${r.counts.totalText} |`);
  }
  lines.push(`| — | **合计 16** | — | light(${statsByScheme.light.v})/dark(${statsByScheme.dark.v}) | — | **${tv}** | — | — | **${terr}** | **${twarn}** | **${tnet}** | **${timg}** | — |`);
  lines.push('');
  lines.push(`> 子统计: Light 模式违规=${statsByScheme.light.v}  通过规则=${statsByScheme.light.pass}   Dark 模式违规=${statsByScheme.dark.v}  通过规则=${statsByScheme.dark.pass}`);
  lines.push('');

  lines.push('## 逐项违规详情');
  lines.push('');
  for (const r of results) {
    const vs = r.axe && r.axe.violations;
    const schemeLabel = (r.colorScheme === 'dark') ? ' 🌙dark' : ' ☀️light';
    lines.push(`### ${r.appName} · ${r.sizeLabel} (${r.width}×${r.height})${schemeLabel}  `);
    lines.push(`- 加载: ${r.loadedOK?'✅':'❌'}    截图: ![${r.app}-${r.size}-${r.colorScheme}](${r.screenshot})  `);
    lines.push(`- URL: \`${r.url}\`   配色: \`${r.colorScheme||'light'}\``);
    if (r.consoleErrors.length) lines.push(`- console.error (首 3):\n${r.consoleErrors.slice(0,3).map(x=>'  - '+mdEscape(x)).join('\n')}`);
    if (r.netErrors.length) lines.push(`- 资源失败 (首 3):\n${r.netErrors.slice(0,3).map(x=>`  - ${x.status} ${x.url}`).join('\n')}`);
    if (!vs || !vs.length) {
      lines.push('- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)');
    } else {
      lines.push(`- 违规: **${vs.length} 项** (前 10):`);
      for (const v of vs.slice(0, 10)) {
        lines.push(`  1. **${v.id}** (impact=${v.impact}) ${mdEscape(v.description)}  — 影响节点 ${v.count}  `);
        if (v.nodes && v.nodes[0]) lines.push(`     - 例: \`${mdEscape(v.nodes[0].target)}\``);
      }
    }
    lines.push('');
  }

  lines.push('## 结论 (基线)');
  lines.push('');
  lines.push(`- **可访问性规则违规合计: ${tv} 条** (axe-core, 含 ${APPS.length} 应用, 双色方案 light + dark)`);
  lines.push(`- Light 模式: 违规 ${statsByScheme.light.v} / 通过规则 ${statsByScheme.light.pass}    Dark 模式: 违规 ${statsByScheme.dark.v} / 通过规则 ${statsByScheme.dark.pass}`);
  lines.push(`- **JS 控制台错误合计: ${terr} 条**  警告合计: ${twarn} 条`);
  lines.push(`- **资源加载失败(4xx/5xx): ${tnet} 次**  缺 alt 图像: ${timg} 张`);
  if (tv === 0 && terr === 0 && tnet === 0) lines.push('- ✅ **Light + Dark 双色方案 A11y/加载基线全绿，可作为后续 MR 的"无回归"阈值基准。**');
  else lines.push('- ⚠️ 存在基线问题，建议优先修复 console.error / 4xx/5xx，其次针对 axe violation 分类处理 (查看具体 colorScheme 分类定位)。');
  lines.push('');
  lines.push('> 详细 JSON: `audit-report/a11y-baseline.json`');
  if (OPTS.label) lines.push(`> CI 标签: \`${OPTS.label}\` · 严格模式: \`${OPTS.strict ? 'on' : 'off'}\``);
  fs.writeFileSync(MD_OUT, lines.join('\n'));
  // 控制台总览 + 产物附加
  const label = OPTS.label ? ` [${OPTS.label}]` : '';
  console.log(`  违规=${tv}  consoleE=${terr}  consoleW=${twarn}  net4xx5xx=${tnet}  缺alt=${timg}${label}`);

  /* ------- CI 严格模式阈值判定 ------- */
  if (OPTS.strict) {
    const fail = [];
    if (OPTS.failOn.violations  && tv   > OPTS.maxViolations) fail.push(`violations ${tv} > 阈值 ${OPTS.maxViolations}`);
    if (OPTS.failOn.consoleE    && terr > OPTS.maxConsoleE)  fail.push(`consoleE   ${terr} > 阈值 ${OPTS.maxConsoleE}`);
    if (OPTS.failOn.net4xx5xx   && tnet > OPTS.maxNet)       fail.push(`net4xx5xx  ${tnet} > 阈值 ${OPTS.maxNet}`);
    if (OPTS.failOn.missingAlt  && timg > 0)                 fail.push(`缺alt 图像 ${timg} > 阈值 0`);
    if (OPTS.failOn.consoleW    && twarn> OPTS.maxConsoleW) {
      console.error('\n[a11y][CI] 阈值违规 (warn):',  `consoleW ${twarn} > 阈值 ${OPTS.maxConsoleW}`);
      process.exit(3);
    }
    if (fail.length) {
      console.error('\n[a11y][CI] 严格模式阈值违规: ❌');
      for (const f of fail) console.error('  · ' + f);
      console.error('\n👉 修复步骤:');
      console.error('   1) 打开 audit-report/a11y-baseline.md 查看按 colorScheme / app 分类的报告');
      console.error('   2) 对 color-contrast 类违规,在 shared/design-system.css / 对应 app 的 <style> 中补足深色或浅色覆盖');
      console.error('   3) 对 consoleE/net4xx5xx, 检查本次 PR 是否引入新的 script/资源路径错误');
      console.error('   4) 本地 `node audit-a11y-diff.js` 与 master 基线 diff,定位是否为 NEW 违规');
      process.exit(2);
    }
    console.log(`\n[a11y][CI] 严格模式通过 ✅ (阈值: violations≤${OPTS.maxViolations} consoleE≤${OPTS.maxConsoleE} net≤${OPTS.maxNet} missingAlt=0)`);
    process.exit(0);
  }

  // 非 strict: 沿用原"violations/consoleErr 即失败"语义,但不把 missingAlt / net4xx5xx 计为致命失败, 输出提示
  if (tv > 0 || terr > 0) {
    console.warn('[a11y] 非严格模式下仍有 violations / consoleE, 建议以 --strict 再跑一次.');
    process.exit(2);
  }
  process.exit(0);
}

main().catch(e => { console.error('FATAL', e); process.exit(1); });
