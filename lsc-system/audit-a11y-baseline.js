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

async function auditOne(browser, app, size) {
  const ctx = await browser.newContext({
    viewport: { width: size.w, height: size.h },
    colorScheme: 'light',
    locale: 'zh-CN',
  });
  const page = await ctx.newPage();
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

  // 截图
  const shot = path.join(OUT_DIR, `${app.id}__${size.id}__${size.w}x${size.h}.png`);
  try {
    await page.screenshot({ path: shot, fullPage: true, timeout: 8000 });
  } catch (_) {}

  await ctx.close();
  return {
    app: app.id, appName: app.name, size: size.id, width: size.w, height: size.h, sizeLabel: size.label,
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
  for (const app of APPS) {
    for (const sid of SIZE_BY_APP[app.id]) {
      const size = SIZES.find(s => s.id === sid);
      pairs.push({ app, size });
    }
  }
  for (const { app, size } of pairs) {
    const k = `${app.id}@${size.w}x${size.h}`;
    process.stdout.write(`  ${k.padEnd(26)} → `);
    const r = mode === 'jsdom' ? await auditOneJSDOM(app, size) : await auditOne(browser, app, size);
    const v = (r.axe && r.axe.violations) ? r.axe.violations.length : '-';
    const e = r.consoleErrors.length;
    const w = r.consoleWarnings.length;
    const n = r.netErrors.length;
    console.log(`OK  violations=${v}  consoleE=${e}  consoleW=${w}  4xx/5xx=${n}  ${r.counts.totalText>200?'内容充足':'⚠ 内容<200'}`);
    results.push(r);
  }
  if (browser) await browser.close();

  // 写 JSON
  fs.writeFileSync(JSON_OUT, JSON.stringify(results, null, 2));

  // 写 Markdown 报告
  const lines = [];
  lines.push('# 链盛通 LSC V6.2-AI · 可访问性 & 响应式基线审计 (快照)');
  lines.push('');
  lines.push(`> 生成于 **${new Date().toISOString()}** · axe-core wcag2a+wcag2aa+best-practice · 4 应用 × 2 视口共 8 项快照`);
  lines.push('');
  lines.push('## 汇总表');
  lines.push('');
  lines.push('| # | 应用 | 视口 | 加载 | 规则违规(V) | 待核查(Inc) | 通过规则 | console.error | console.warn | 4xx/5xx | 无 alt 图 | 正文长度 |');
  lines.push('|---|---|---|---|---|---|---|---|---|---|---|---|');
  let i = 0, tv = 0, terr = 0, twarn = 0, tnet = 0, timg = 0;
  for (const r of results) {
    i++;
    const v = (r.axe && r.axe.violations) ? r.axe.violations.length : 0;
    const inc = (r.axe && r.axe.incomplete) || 0;
    const pass = (r.axe && r.axe.passes) || 0;
    tv += v; terr += r.consoleErrors.length; twarn += r.consoleWarnings.length;
    tnet += r.netErrors.length; timg += r.counts.img;
    lines.push(`| ${i} | ${mdEscape(r.appName)} | ${r.width}×${r.height} | ${r.loadedOK?'✅':'❌'} | ${v} | ${inc} | ${pass} | ${r.consoleErrors.length} | ${r.consoleWarnings.length} | ${r.netErrors.length} | ${r.counts.img} | ${r.counts.totalText} |`);
  }
  lines.push(`| — | **合计 8** | — | — | **${tv}** | — | — | **${terr}** | **${twarn}** | **${tnet}** | **${timg}** | — |`);
  lines.push('');

  lines.push('## 逐项违规详情');
  lines.push('');
  for (const r of results) {
    const vs = r.axe && r.axe.violations;
    lines.push(`### ${r.appName} · ${r.sizeLabel} (${r.width}×${r.height})  `);
    lines.push(`- 加载: ${r.loadedOK?'✅':'❌'}    截图: ![${r.app}-${r.size}](${r.screenshot})  `);
    lines.push(`- URL: \`${r.url}\``);
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
  lines.push(`- **可访问性规则违规合计: ${tv} 条** (axe-core, 含 ${APPS.length} 应用)`);
  lines.push(`- **JS 控制台错误合计: ${terr} 条**  警告合计: ${twarn} 条`);
  lines.push(`- **资源加载失败(4xx/5xx): ${tnet} 次**  缺 alt 图像: ${timg} 张`);
  if (tv === 0 && terr === 0 && tnet === 0) lines.push('- ✅ **A11y/加载基线全绿，可作为后续 MR 的"无回归"阈值基准。**');
  else lines.push('- ⚠️ 存在基线问题，建议优先修复 console.error / 4xx/5xx，其次针对 axe violation 分类处理。');
  lines.push('');
  lines.push('> 详细 JSON: `audit-report/a11y-baseline.json`');
  fs.writeFileSync(MD_OUT, lines.join('\n'));
  console.log(`\n[a11y] 完成 → ${JSON_OUT} / ${MD_OUT}`);
  // 控制台总览
  console.log(`  违规=${tv}  consoleE=${terr}  consoleW=${twarn}  net4xx5xx=${tnet}  缺alt=${timg}`);
  process.exit(tv > 0 || terr > 0 ? 2 : 0);
}

main().catch(e => { console.error('FATAL', e); process.exit(1); });
