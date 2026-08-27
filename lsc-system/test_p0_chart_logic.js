#!/usr/bin/env node
/**
 * P0-4 释放速率图表逻辑 单元测试
 * 目标:覆盖 3 个已修复 bug 的回归用例 + 核心 append/redraw/切页/定时器 等场景
 *
 * 运行:
 *   cd /workspace/lsc-system && node test_p0_chart_logic.js
 *
 * 输出:TAP 格式 + 总结 (CI 友好,非 0 退出码表示失败)
 */
const http = require('http');
const path = require('path');
const fs   = require('fs');
const { JSDOM, VirtualConsole } = require('jsdom');

const ROOT = __dirname;
let PORT = 18766;

/* =========================================================
 *  1. 启动一个最小 HTTP 服务以加载 shared/* 资源
 * ========================================================= */
function startStaticServer(p) {
  const srv = http.createServer((req, res) => {
    let urlPath = decodeURIComponent((req.url || '/').split('?')[0]);
    if (urlPath === '/') urlPath = '/index.html';
    const full = path.join(ROOT, urlPath);
    if (!full.startsWith(ROOT)) { res.writeHead(403); res.end('forbidden'); return; }
    fs.readFile(full, (err, data) => {
      if (err) { res.writeHead(404); res.end('not found'); return; }
      const ext = path.extname(full).toLowerCase();
      const mime = { '.html':'text/html; charset=utf-8', '.js':'application/javascript; charset=utf-8', '.css':'text/css; charset=utf-8' }[ext] || 'application/octet-stream';
      res.writeHead(200, { 'Content-Type': mime });
      res.end(data);
    });
  });
  return new Promise(res => srv.listen(p, '127.0.0.1', () => res(srv)));
}

/* =========================================================
 *  2. 简易 TAP 测试框架 (零依赖)
 * ========================================================= */
let testIdx = 0, passCnt = 0, failCnt = 0, todoCnt = 0;
const tests = [];
function test(name, fn) { tests.push({ name, fn }); }
async function runAll() {
  console.log(`TAP version 14`);
  console.log(`1..${tests.length}`);
  for (const t of tests) {
    testIdx++;
    const out = { ok: true, info: [], diag: null };
    const assert = (cond, desc='assert') => {
      if (!cond) { out.ok = false; out.info.push(`✗ ${desc}`); }
      else out.info.push(`✓ ${desc}`);
    };
    assert.equal = (a, b, desc='equal') => {
      if (a !== b) { out.ok=false; out.info.push(`✗ ${desc}: expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`); }
      else out.info.push(`✓ ${desc}`);
    };
    assert.throws = (fn, desc='throws') => {
      try { fn(); out.ok=false; out.info.push(`✗ ${desc}: expected to throw but did not`); }
      catch(_) { out.info.push(`✓ ${desc}`); }
    };
    assert.regex = (s, re, desc='match') => {
      if (!(re instanceof RegExp ? re.test(s) : String(re).length && String(s).includes(String(re)))) { out.ok=false; out.info.push(`✗ ${desc}`); }
      else out.info.push(`✓ ${desc}`);
    };
    try { await t.fn(assert, out); }
    catch(e) { out.ok=false; out.diag = 'UNCAUGHT: '+e.message + ' | STACK: ' + (e.stack||'').split('\n').slice(0,5).join('  <<  '); }
    if (out.ok) { passCnt++; console.log(`ok ${testIdx} - ${t.name}`); }
    else {
      failCnt++;
      console.log(`not ok ${testIdx} - ${t.name}`);
      if (out.diag) console.log(`  ---\n  message: ${JSON.stringify(out.diag)}\n  ---`);
      out.info.forEach(l => console.log(`    # ${l}`));
    }
  }
  console.log(`# tests=${tests.length} pass=${passCnt} fail=${failCnt}`);
  process.exit(failCnt>0 ? 1 : 0);
}

/* =========================================================
 *  3. 共享工具:创建一个干净的 DOM 环境
 * ========================================================= */
async function newDomSession(server) {
  const errors = [];
  const vc = new VirtualConsole();
  vc.on('error', e => errors.push('console: '+String(e).slice(0, 400)));
  vc.on('warn', () => {});
  vc.on('jsdomError', e => errors.push('jsdom: '+String(e.message||e).slice(0, 400)));
  const dom = await JSDOM.fromURL(`http://127.0.0.1:${server.address().port}/platform-admin/index.html`, {
    runScripts: 'dangerously',
    resources: 'usable',
    pretendToBeVisual: true,
    virtualConsole: vc,
  });
  // 等待脚本稳定
  await new Promise(r => setTimeout(r, 1200));
  return { dom, errors };
}
function cleanupSession(sess) {
  try { if (sess.dom.window._aiTimers) sess.dom.window._aiTimers.forEach(t => clearInterval(t)); } catch(_) {}
  try { sess.dom.window.close(); } catch(_) {}
}

/* =========================================================
 *  4. 测试用例
 * ========================================================= */

// ---- A. 回归 bug #1: 初始模板解构 undefined ----
test('BUG#1 回归:初始渲染 rate 图表,innerHTML 不包含 undefined', async (t) => {
  const srv = await startStaticServer(PORT++);
  const sess = await newDomSession(srv);
  try {
    sess.dom.window.renderAI();
    const html = sess.dom.window.document.getElementById('rate-realtime-chart').innerHTML;
    t(!html.includes('undefined'), 'chart container 不含 undefined 文本');
    t(html.includes('<svg'), 'chart container 内含有 <svg> 元素');
    t(html.includes('rate-realtime-legend'), 'chart container 内含 legend 容器');
    const legend = sess.dom.window.document.getElementById('rate-realtime-legend');
    t(!!legend, 'rate-realtime-legend 元素存在');
    if (legend) {
      t(legend.textContent.includes('k值'), 'legend 显示 k值 名称');
      t(legend.textContent.includes('rate'), 'legend 显示 rate 名称');
    }
    // 指标卡初始值亦不应含 undefined
    const kv = sess.dom.window.document.getElementById('rate-k-val').textContent;
    const rv = sess.dom.window.document.getElementById('rate-rate-val').textContent;
    t(!kv.includes('undefined') && kv.length>0, 'k 值指标卡非空且不含 undefined');
    t(!rv.includes('undefined') && rv.length>0, 'rate 值指标卡非空且不含 undefined');
  } finally { cleanupSession(sess); await new Promise(r => srv.close(r)); }
});

// ---- B. 回归 bug #2: 切页重置数据 (_rateData → _rateSeries) ----
test('BUG#2 回归:再次 renderAI 不重置 _rateSeries (跨视图保留滚动窗口)', async (t) => {
  const srv = await startStaticServer(PORT++);
  const sess = await newDomSession(srv);
  try {
    const w = sess.dom.window;
    w.renderAI();
    const firstKArr = w._rateSeries.k.slice();
    const firstLabels = w._rateLabels.slice();
    t.equal(firstKArr.length, 24, 'k 系列首次为 24 点');
    // 推进 3 个点
    for (let i=0;i<3;i++) w.appendRatePoint();
    t.equal(w._rateLabels.length, 24, '推进后 labels 仍为 24');
    t.equal(w._rateSeries.k.length, 24, '推进后 k 系列仍为 24');
    const firstAfter = w._rateSeries.k[0];
    // 切到商家页,再切回 AI 中心
    if (typeof w.renderMerchant === 'function') w.renderMerchant(); else if (typeof w.renderB2B === 'function') w.renderB2B();
    w.renderAI();
    t.equal(w._rateLabels.length, 24, '切回后 labels 仍为 24 (未被重新 init)');
    t.equal(w._rateSeries.k.length, 24, '切回后 k 系列仍为 24');
    t.equal(w._rateSeries.k[0], firstAfter, '切回后首点未变 (未重置)');
    // 推进 1 个点后首点被推走
    w.appendRatePoint();
    t(w._rateSeries.k[0] !== firstAfter, '再次推进后首点被左滑推走 (窗口继续滚动)');
    // 不应该再出现 undefined 渲染
    const html = w.document.getElementById('rate-realtime-chart').innerHTML;
    t(!html.includes('undefined'), '切回后渲染仍然无 undefined');
  } finally { cleanupSession(sess); await new Promise(r => srv.close(r)); }
});

// ---- C. Bug #3 回归: legend 颜色变量不被错误 replace ----
test('BUG#3 回归:legend tag-dot 仍使用 var(...) CSS 变量,未被 replace 破坏', async (t) => {
  const srv = await startStaticServer(PORT++);
  const sess = await newDomSession(srv);
  try {
    sess.dom.window.renderAI();
    sess.dom.window.redrawRateChart();
    const legend = sess.dom.window.document.getElementById('rate-realtime-legend');
    t(!!legend, 'legend DOM 存在');
    const inner = legend.innerHTML;
    // 颜色变量字符串应该完整: var(--c-locked) / var(--c-accent)
    t(inner.includes('var(--c-locked)'), 'k 系列 legend 保留 var(--c-locked) 变量');
    t(inner.includes('var(--c-accent)'), 'rate 系列 legend 保留 var(--c-accent) 变量');
    // 不应出现变量残缺 (形如 "var(--c-)" 右括号后没分号就收尾,或 var(--c-)color 直接衔接)
    const broken = (inner.match(/var\(--c-[a-z]+\)[^;:"'`>\s]/g) || []).filter(m => !m.endsWith(';') && !m.includes(') ') && !m.includes('),'));
    t(broken.length === 0, '没有残缺的 var(...) 变量 (如 "var(--c-accent)color"),捕获项='+JSON.stringify(broken));
  } finally { cleanupSession(sess); await new Promise(r => srv.close(r)); }
});

// ---- D. 核心 appendRatePoint 滚动窗口正确性 ----
test('appendRatePoint:24 点窗口滚动 + 数值合法区间', async (t) => {
  const srv = await startStaticServer(PORT++);
  const sess = await newDomSession(srv);
  try {
    const w = sess.dom.window;
    w.renderAI();
    const kBefore = w._rateSeries.k.slice();
    const labelsBefore = w._rateLabels.slice();
    w.appendRatePoint();
    t.equal(w._rateLabels.length, 24, 'labels 长度恒定 24');
    t.equal(w._rateSeries.k.length, 24, 'k 系列长度恒定 24');
    t.equal(w._rateSeries.rate.length, 24, 'rate 系列长度恒定 24');
    // 左滑:新 labels 首个应等于旧 labels[1]
    t.equal(w._rateLabels[0], labelsBefore[1], '首个 label 被左滑推走');
    t.equal(w._rateSeries.k[0], kBefore[1], 'k 首个样本被左滑推走');
    // k 值限制在 [0.0045, 0.0085]; rate 在 [0.0025, 0.0055]
    for (let i=0;i<30;i++) w.appendRatePoint();
    const kAllOk = w._rateSeries.k.every(v => v>=0.0045-1e-9 && v<=0.0085+1e-9);
    const rAllOk = w._rateSeries.rate.every(v => v>=0.0025-1e-9 && v<=0.0055+1e-9);
    t(kAllOk, '30 次推进后,所有 k 值仍在 [0.0045, 0.0085]');
    t(rAllOk, '30 次推进后,所有 rate 值仍在 [0.0025, 0.0055]');
  } finally { cleanupSession(sess); await new Promise(r => srv.close(r)); }
});

// ---- E. redrawRateChart 更新指标卡 ----
test('redrawRateChart:同步更新 k/rate/趋势 三个指标卡', async (t) => {
  const srv = await startStaticServer(PORT++);
  const sess = await newDomSession(srv);
  try {
    const w = sess.dom.window;
    w.renderAI();
    const oldK = w.document.getElementById('rate-k-val').textContent;
    const oldR = w.document.getElementById('rate-rate-val').textContent;
    t.regex(oldK, /%$/, '初始 k 值以 % 结尾');
    t.regex(oldR, /%$/, '初始 rate 值以 % 结尾');
    // 连续推进几次,验证数值发生变化 (数值变化反映在 DOM 文本中)
    const oldKval = parseFloat(oldK);
    const oldRval = parseFloat(oldR);
    let kChanged=false, rChanged=false;
    for (let i=0;i<20 && !(kChanged&&rChanged);i++) {
      w.appendRatePoint();
      const kk = parseFloat(w.document.getElementById('rate-k-val').textContent);
      const rr = parseFloat(w.document.getElementById('rate-rate-val').textContent);
      if (Math.abs(kk-oldKval) > 1e-9) kChanged=true;
      if (Math.abs(rr-oldRval) > 1e-9) rChanged=true;
    }
    t(kChanged, '多次推进后 k 值指标卡已更新');
    t(rChanged, '多次推进后 rate 值指标卡已更新');
    const trend = w.document.getElementById('rate-trend').textContent;
    t(['↗ 上升','↘ 下降','→ 平稳'].includes(trend), `趋势文本为三选一,实际="${trend}"`);
    const trendEl = w.document.getElementById('rate-trend');
    const color = trendEl.style.color;
    t(color.startsWith('var(') || color.length>0, '趋势元素有颜色样式 (根据趋势上色)');
  } finally { cleanupSession(sess); await new Promise(r => srv.close(r)); }
});

// ---- F. pushActivity:活动流新条目 slide-down 动画 + 最多 20 条 ----
test('P0-3 pushActivity:首条插入正确 + 最多保留 20 条 + 带透明度动画样式', async (t) => {
  const srv = await startStaticServer(PORT++);
  const sess = await newDomSession(srv);
  try {
    const w = sess.dom.window;
    w.renderAI();
    const feed = w.document.getElementById('ai-activity-feed');
    const initCount = feed.children.length;
    // 同步插入 1 条
    w.pushActivity();
    const after1 = feed.children.length;
    t(after1 === initCount+1, 'pushActivity 成功插入 1 条 (before='+initCount+' after='+after1+')');
    // 首条应该是刚刚插入,带 CSS 动画属性
    const first = feed.firstElementChild;
    const style = first.getAttribute('style') || '';
    t(style.includes('transition'), '新条目内联样式含 transition (slide-down 动画)');
    t(style.includes('opacity'), '新条目内联样式含 opacity (淡入)');
    // 推入 30 条,验证只保留 20 条
    for (let i=0;i<30;i++) w.pushActivity();
    t(feed.children.length <= 20, '溢出裁剪:30 条之后总条数 <= 20, 实际='+feed.children.length);
  } finally { cleanupSession(sess); await new Promise(r => srv.close(r)); }
});

// ---- G. 定时器管理:不泄漏 (2 个 interval + 可清理) ----
test('定时器:renderAI 创建 2 个 interval,再次调用前会被清理', async (t) => {
  const srv = await startStaticServer(PORT++);
  const sess = await newDomSession(srv);
  try {
    const w = sess.dom.window;
    w.renderAI();
    t(Array.isArray(w._aiTimers), 'window._aiTimers 为数组');
    t.equal(w._aiTimers.length, 2, '包含 2 个 interval (活动流 + 速率)');
    const prevIds = w._aiTimers.slice();
    // 二次 renderAI 应该先清旧定时器,再创建新的
    w.renderAI();
    t.equal(w._aiTimers.length, 2, '二次 renderAI 后仍为 2 个');
    // 新 ID 应与之前不同 (说明旧的已清)
    const allNew = w._aiTimers.every(id => !prevIds.includes(id));
    t(allNew, '新的定时器 ID 与之前不同 (旧定时器被清除)');
  } finally { cleanupSession(sess); await new Promise(r => srv.close(r)); }
});

// ---- H. 运行时无未捕获错误 ----
test('运行时 5 分钟模拟:append 循环不应抛出任何异常', async (t) => {
  const srv = await startStaticServer(PORT++);
  const sess = await newDomSession(srv);
  try {
    const w = sess.dom.window;
    w.renderAI();
    let threw = null;
    for (let i=0;i<100;i++) {
      try {
        if (i%5===0) w.pushActivity();
        w.appendRatePoint();
      } catch(e) { threw = e; break; }
    }
    t.equal(threw, null, '100 次混合调用 (pushActivity×20 + appendRatePoint×100) 无异常');
    t.equal(sess.errors.filter(e => !e.includes('Could not load')).length, 0, '无控制台/JS 错误(CSS/JS 加载告警除外)');
  } finally { cleanupSession(sess); await new Promise(r => srv.close(r)); }
});

runAll();
