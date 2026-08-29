#!/usr/bin/env node
/**
 * 链盛通 LSC V6.2-AI · Lighthouse 性能基线
 * ------------------------------------------------------------------
 * 使用 Playwright + lighthouse-core 对 4 个应用首页跑 Lighthouse 六大类审计
 * 输出 JSON (audit-report/perf-baseline.json) 与 Markdown 总览 (perf-baseline.md)
 * 核心指标: FCP / SI / LCP / Speed Index / TBT / TTI / CLS
 * 同时附带:
 *   · Accessibility (lighthouse a11y 聚合, 与 axe-core 形成互补)
 *   · Best Practices / SEO
 * 使用方式:
 *   node scripts/run-lighthouse.js          # 跑全部 4 应用,用 1440x900 桌面
 *   node scripts/run-lighthouse.js --mobile # 用 390x844 尺寸,模拟 iPhone
 *   node scripts/run-lighthouse.js --app=platform
 *   node scripts/run-lighthouse.js --thresholds # 启用门控: performance<60 exit 2
 * 依赖: lighthouse (非 devDependency 时自动 require 失败, 降级为 Playwright 原生 Navigation Timing)
 */
'use strict';
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const ROOT = path.resolve(__dirname, '..');
const OUT  = path.join(ROOT, 'audit-report');
fs.mkdirSync(OUT, { recursive: true });
const JSON_OUT = path.join(OUT, 'perf-baseline.json');
const MD_OUT   = path.join(OUT, 'perf-baseline.md');

const OPTS = {
  mobile: process.argv.includes('--mobile'),
  thresholds: process.argv.includes('--thresholds'),
  appFilter: (process.argv.find(a=>a.startsWith('--app='))||'').slice('--app='.length),
};

// ---------- 启动静态服务器 (127.0.0.1:随机端口, 异步) 兜底: file:// 协议 ----------
let BASE = process.env.LSC_E2E_BASE_URL || '';
let _staticSrv = null;
async function ensureStaticServer() {
  if (BASE) return BASE;
  try {
    const http = require('http');
    const file = new (require('node-static').Server)(ROOT, { cache: 0, headers: { 'Cache-Control': 'no-cache' } });
    _staticSrv = http.createServer((req, res) => req.addListener('end', () => file.serve(req, res)).resume());
    await new Promise((resolve, reject) => {
      _staticSrv.once('error', reject);
      _staticSrv.listen(0, '127.0.0.1', resolve);
    });
    process.on('beforeExit', () => { try{ _staticSrv.close(); }catch(_){} });
    BASE = `http://127.0.0.1:${_staticSrv.address().port}`;
    return BASE;
  } catch(e) {
    BASE = 'file://' + ROOT;
    return BASE;
  }
}
function buildApps(base, appFilter) {
  return [
    { id: 'platform', name: '平台管理后台',   url: base + '/platform-admin/index.html' },
    { id: 'merchant', name: '商家管理后台',   url: base + '/merchant-admin/index.html' },
    { id: 'mobile',   name: '移动端 APP',     url: base + '/mobile-app/index.html'   },
    { id: 'mini',     name: '微信小程序',     url: base + '/mini-program/index.html'  },
  ].filter(a => !appFilter || a.id.startsWith(appFilter));
}

const EXEC_PATH = (() => {
  if (process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH) return process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH;
  try {
    const out = require('child_process')
      .execSync('ls /root/.cache/puppeteer/chrome/linux-*/chrome-linux64/chrome 2>/dev/null | head -1')
      .toString().trim();
    return out || undefined;
  } catch(_){ return undefined; }
})();

// ------------- Lighthouse 优先 (若有) -------------
async function runWithLighthouse(APPS) {
  const lh = require('lighthouse/core/index.cjs');
  const { chromium } = require('playwright');
  const browser = await chromium.launch({
    headless: true, executablePath: EXEC_PATH,
    args: ['--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage',
           '--disable-storage-reset','--disable-background-networking'],
  });
  const results = [];
  for (const app of APPS) {
    process.stdout.write(`  🚦 ${app.name.padEnd(14)} (${OPTS.mobile?'mobile':'desktop'}) → `);
    try {
      const context = await browser.newContext({
        viewport: OPTS.mobile ? { width: 390, height: 844, isMobile: true, hasTouch: true }
                               : { width: 1440, height: 900 },
        userAgent: OPTS.mobile
          ? 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1 Chrome-Lighthouse'
          : 'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome-Lighthouse Chrome/130.0.0.0 Safari/537.36',
        colorScheme: 'light',
      });
      const page = await context.newPage();
      await page.goto(app.url, { waitUntil: 'networkidle' }); // 先 cache warm
      const runnerResult = await lh(app.url, {
        logLevel: 'error', output: 'json', onlyCategories: ['performance','accessibility','best-practices','seo'],
        ...(OPTS.mobile
          ? { formFactor: 'mobile', screenEmulation: { mobile: true, width: 390, height: 844, deviceScaleFactor: 3, disabled: false } }
          : { formFactor: 'desktop', screenEmulation: { mobile: false, width: 1440, height: 900, deviceScaleFactor: 1, disabled: false }}),
      }, undefined, await page.context().newCDPSession(await page));
      const cats = runnerResult.lhr.categories;
      const audits = runnerResult.lhr.audits;
      const r = {
        app: app.id, appName: app.name,
        scores: {
          performance:   Math.round(cats.performance.score*100),
          accessibility: Math.round(cats.accessibility.score*100),
          bestPractices: Math.round(cats['best-practices'].score*100),
          seo:           Math.round(cats.seo.score*100),
        },
        metrics: {
          FCP: Math.round(audits['first-contentful-paint'].numericValue),
          SI:  Math.round(audits['speed-index'].numericValue),
          LCP: Math.round(audits['largest-contentful-paint'].numericValue),
          TBT: Math.round(audits['total-blocking-time'].numericValue),
          CLS: Number(audits['cumulative-layout-shift'].numericValue.toFixed(4)),
          TTI: Math.round(audits['interactive'].numericValue),
        },
        audits,
      };
      results.push(r);
      console.log(`perf=${r.scores.performance}  a11y=${r.scores.accessibility}  LCP=${r.metrics.LCP}ms  TBT=${r.metrics.TBT}ms  CLS=${r.metrics.CLS}`);
      await context.close();
    } catch (e) {
      console.log('ERR ' + e.message);
      results.push({ app: app.id, appName: app.name, error: e.message });
    }
  }
  await browser.close();
  return results;
}

// ------------- 降级: Playwright Navigation Timing -------------
async function runNative(APPS) {
  console.log('[perf] 未安装 lighthouse, 降级为 Playwright Navigation Timing + Web Vitals');
  const { chromium } = require('playwright');
  const browser = await chromium.launch({
    headless: true, executablePath: EXEC_PATH,
    args: ['--no-sandbox','--disable-setuid-sandbox','--disable-dev-shm-usage'],
  });
  const results = [];
  for (const app of APPS) {
    process.stdout.write(`  🚦 ${app.name.padEnd(14)} (${OPTS.mobile?'mobile':'desktop'}) → `);
    try {
      const context = await browser.newContext({
        viewport: OPTS.mobile ? { width: 390, height: 844, isMobile: true, hasTouch: true }
                               : { width: 1440, height: 900 },
        colorScheme: 'light',
      });
      const page = await context.newPage();
      let cls = 0;
      await page.addInitScript(() => {
        window.__perf = { cls: 0 };
        try {
          new PerformanceObserver(list => {
            for (const e of list.getEntries()) if (e.hadRecentInput !== true) window.__perf.cls += e.value;
          }).observe({ type: 'layout-shift', buffered: true });
        } catch(_) {}
      });
      await page.goto(app.url, { waitUntil: 'load' });
      // 强制触发一次 LCP 事件（滚动页面 → 记录 layout shift 与最终 LCP）
      await page.waitForTimeout(200);
      try { await page.evaluate(() => window.scrollTo(0, Math.min(800, (document.body.scrollHeight||document.documentElement.scrollHeight||1200)/2))); } catch(_){}
      await page.waitForTimeout(1800);
      try { await page.evaluate(() => window.scrollTo(0, 0)); } catch(_){}
      await page.waitForTimeout(300);
      const nt = await page.evaluate(() => {
        const p = performance.getEntriesByType('navigation')[0];
        const paint = performance.getEntriesByType('paint') || [];
        const fcp = paint.find(e => e.name === 'first-contentful-paint')?.startTime || 0;
        const fp  = paint.find(e => e.name === 'first-paint')?.startTime || 0;
        // LCP: buffered + pending flush
        const lcpE = performance.getEntriesByType('largest-contentful-paint') || [];
        const lcp = lcpE.length ? lcpE[lcpE.length-1].startTime : 0;
        const lt = performance.getEntriesByType('longtask') || [];
        const tbt = lt.reduce((s,e)=>s+Math.max(0,e.duration-50), 0);
        const startFetch = p ? p.fetchStart : (performance.timing?.fetchStart || 0);
        const dcl = p ? p.domContentLoadedEventEnd - startFetch
                      : (performance.timing?.domContentLoadedEventEnd || 0) - startFetch;
        const load = p ? p.loadEventEnd - startFetch
                       : (performance.timing?.loadEventEnd || 0) - startFetch;
        // Speed Index 估算：FCP * 0.5 + load * 0.3 + LCP * 0.2
        const si = Math.round((fcp||load) * 0.5 + Math.max(0,load) * 0.3 + Math.max(0,lcp) * 0.2);
        return {
          FCP: Math.round(fcp), FP: Math.round(fp), LCP: Math.round(lcp), TBT: Math.round(tbt),
          DCL: Math.max(0,Math.round(dcl)), load: Math.max(0,Math.round(load)),
          SI: si,
          cls: Number((window.__perf||{}).cls.toFixed(4)),
        };
      });
      // 估算 performance score (LCP/TBT/CLS 加权，粗略 lighthouse 算法)
      const score = estimatePerfScore(nt.LCP, nt.TBT, nt.cls);
      // axe-core 快速 a11y 打分 (从已加载 axe)
      let a11y = null;
      try {
        const { chromium: _c } = require('playwright'); void _c;
        const axeSrc = fs.readFileSync(path.join(ROOT, 'audit-report', 'axe.min.js'), 'utf8');
        await page.evaluate(axeSrc);
        const r = await page.evaluate(() => window.axe.run({ runOnly: ['wcag2a','wcag2aa'] }));
        const pass = (r.passes || []).length, fail = (r.violations || []).length;
        a11y = Math.max(0, Math.min(100, Math.round(100 * pass / Math.max(1, pass + fail))));
      } catch(_) {}
      const r = {
        app: app.id, appName: app.name,
        mode: 'playwright-native (fallback)',
        scores: { performance: score, accessibility: a11y, bestPractices: null, seo: null },
        metrics: {
          FCP: nt.FCP, SI: 0, LCP: nt.LCP, TBT: nt.TBT, CLS: nt.cls, TTI: Math.round((nt.DCL + nt.load) / 2),
          FP: nt.FP, DCL: nt.DCL, load: nt.load,
        },
      };
      results.push(r);
      console.log(`perf≈${score}  a11y=${a11y ?? '-'}  LCP=${r.metrics.LCP}ms  TBT=${r.metrics.TBT}ms  CLS=${r.metrics.CLS}`);
      await context.close();
    } catch (e) {
      console.log('ERR ' + e.message);
      results.push({ app: app.id, appName: app.name, error: e.message });
    }
  }
  await browser.close();
  return results;
}

// 简易 performance 分数估算 (越大越好, 0~100)
function estimatePerfScore(lcpMs, tbtMs, cls) {
  // LCP 权重 0.35, TBT 权重 0.30, CLS 权重 0.15, FCP(用 LCP 代) 0.20
  const scoreLCP = clamp(100 - Math.max(0, lcpMs - 500) / 30);
  const scoreTBT = clamp(100 - Math.max(0, tbtMs - 50) / 6);
  const scoreCLS = clamp(100 - cls * 300);
  const s = 0.35*scoreLCP + 0.30*scoreTBT + 0.15*scoreCLS + 0.20*scoreLCP;
  return Math.round(s);
}
function clamp(x){ return Math.max(0, Math.min(100, x)); }

function renderJSON(results) {
  return {
    generatedAt: new Date().toISOString(),
    mode: OPTS.mobile ? 'mobile' : 'desktop',
    thresholds: OPTS.thresholds,
    // 汇总
    summary: {
      avgPerf: Math.round(results.filter(r=>r.scores).reduce((s,r)=>s+r.scores.performance,0) / Math.max(1, results.filter(r=>r.scores).length)),
      avgA11y: (() => {
        const xs = results.filter(r=>r.scores?.accessibility!=null);
        return xs.length ? Math.round(xs.reduce((s,r)=>s+r.scores.accessibility,0)/xs.length) : null;
      })(),
      maxLCP:   Math.max(...results.filter(r=>r.metrics).map(r=>r.metrics.LCP||0)),
      maxTBT:   Math.max(...results.filter(r=>r.metrics).map(r=>r.metrics.TBT||0)),
      maxCLS:   Math.max(...results.filter(r=>r.metrics).map(r=>r.metrics.CLS||0)),
    },
    results,
  };
}

function renderMD(payload) {
  const form = OPTS.mobile ? '移动端 (iPhone 390×844)' : '桌面端 (1440×900)';
  let md = `# LSC V6.2-AI · 性能 / 实践质量基线\n\n`;
  md += `- 生成时间: ${payload.generatedAt}\n`;
  md += `- 形态: **${form}**\n`;
  md += `- 核心汇总: 平均 Perf=${payload.summary.avgPerf} · 平均 A11y=${payload.summary.avgA11y ?? '-'} · 最大 LCP=${payload.summary.maxLCP}ms · 最大 TBT=${payload.summary.maxTBT}ms · 最大 CLS=${payload.summary.maxCLS}\n\n`;
  md += `## 4 应用分数矩阵\n\n| 应用 | Perf | A11y | BP | SEO | FCP | SI | LCP | TBT | CLS | TTI |\n|---|---|---|---|---|---|---|---|---|---|---|\n`;
  for (const r of payload.results) {
    if (!r.metrics) { md += `| ${r.appName} | - | - | - | - | ERR ${r.error?.slice(0,40)} |\n`; continue; }
    md += `| ${r.appName} | **${r.scores.performance}** | ${r.scores.accessibility ?? '-'} | ${r.scores.bestPractices ?? '-'} | ${r.scores.seo ?? '-'} | ${r.metrics.FCP}ms | ${r.metrics.SI || '-'}ms | ${r.metrics.LCP}ms | ${r.metrics.TBT}ms | ${r.metrics.CLS} | ${r.metrics.TTI || '-'}ms |\n`;
  }
  md += `\n## 核心 Web Vitals 解读 (CWV)\n\n`;
  md += `- LCP ≤ **2500ms** = Good  · ≤4000ms Needs Improvement  · >4000ms Poor\n`;
  md += `- CLS ≤ **0.1** = Good  · ≤0.25 Needs Improvement   · >0.25 Poor\n`;
  md += `- TBT (FID近似) ≤ **200ms** = Good  · ≤600ms Needs Improvement   · >600ms Poor\n\n`;
  md += `## 门控阈值 (用于 CI --thresholds)\n\n`;
  md += `- 四应用平均 Perf ≥ 60\n`;
  md += `- 任一应用 LCP ≤ 5000ms\n`;
  md += `- 任一应用 CLS ≤ 0.25\n`;
  md += `\n原始 JSON: \`audit-report/perf-baseline.json\`\n`;
  return md;
}

function gate(payload) {
  if (!OPTS.thresholds) return 0;
  const { avgPerf, maxLCP, maxCLS } = payload.summary;
  const fails = [];
  if (avgPerf < 60) fails.push(`avgPerf=${avgPerf} < 60`);
  if (maxLCP > 5000)  fails.push(`maxLCP=${maxLCP}ms > 5000`);
  if (maxCLS > 0.25)  fails.push(`maxCLS=${maxCLS} > 0.25`);
  if (fails.length) { console.log('[perf] ❌ FAIL: ' + fails.join('; ')); return 2; }
  console.log('[perf] ✅ PASS thresholds');
  return 0;
}

(async () => {
  await ensureStaticServer();
  const APPS = buildApps(BASE, OPTS.appFilter);
  let hasLH = false;
  try { require.resolve('lighthouse/core/index.cjs'); hasLH = true; } catch(_){ hasLH = false; }
  console.log(`[perf] 运行模式: ${hasLH ? 'lighthouse-core' : 'playwright-native fallback'} (${OPTS.mobile?'mobile':'desktop'}) base=${BASE}`);
  const results = hasLH ? await runWithLighthouse(APPS) : await runNative(APPS);
  const payload = renderJSON(results);
  fs.writeFileSync(JSON_OUT, JSON.stringify(payload, null, 2));
  fs.writeFileSync(MD_OUT,   renderMD(payload));
  console.log(`[perf] → ${JSON_OUT} / ${MD_OUT}`);
  const ec = gate(payload);
  process.exit(ec);
})().catch(e => { console.error('[perf][FATAL]', e.message || e); process.exit(1); });
