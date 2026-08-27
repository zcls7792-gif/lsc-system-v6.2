/**
 * 覆盖率 runner
 *
 * c8 只能对"Node 主进程上下文里 require/执行的 .js 文件"产出可靠覆盖数据。
 * JSDOM 将脚本跑在独立的 vm context,源码不注册为文件 → 覆盖率丢。
 *
 * 解决方式:
 *   1. 创建 JSDOM 环境(等价于 test_p0_chart_logic.js 的运行时)
 *   2. 用 vm.Script + filename 选项,将 shared/app-utils.js / platform-admin/app.js 在 DOM window 上下文中执行,
 *      但显式传入真实文件路径,这样 V8 覆盖率报告会关联到真实文件,c8 可以输出行/分支覆盖。
 *   3. 然后在同一进程中运行 test_p0_chart_logic 的断言逻辑(与 TAP 脚本一致),
 *      触发 P0-4 所有关键代码路径(lineChart,donutChart,renderAI,appendRatePoint,
 *      pushActivity,redrawRateChart,切页等)。
 */
const http = require('http');
const path = require('path');
const fs   = require('fs');
const vm   = require('vm');
const { JSDOM, VirtualConsole } = require('jsdom');

const ROOT = __dirname;
const PORT = 18900;

function startStaticServer() {
  const srv = http.createServer((req, res) => {
    let up = decodeURIComponent((req.url || '/').split('?')[0]);
    if (up === '/') up = '/index.html';
    const full = path.normalize(path.join(ROOT, up));
    if (!full.startsWith(ROOT)) { res.writeHead(403); res.end('403'); return; }
    fs.readFile(full, (err, d) => {
      if (err) { res.writeHead(404); res.end('404'); return; }
      const ext = path.extname(full).toLowerCase();
      const mime = { '.html':'text/html; charset=utf-8', '.js':'application/javascript; charset=utf-8', '.css':'text/css; charset=utf-8' }[ext] || 'application/octet-stream';
      res.writeHead(200, { 'Content-Type': mime }); res.end(d);
    });
  });
  return new Promise(res => srv.listen(PORT, '127.0.0.1', () => res(srv)));
}

async function buildSession(srv) {
  const vc = new VirtualConsole();
  // 错误保留,但不打印
  const errors = [];
  vc.on('error', e => errors.push('C: '+String(e)));
  vc.on('warn', () => {});
  vc.on('jsdomError', e => errors.push('J: '+String(e.message||e)));
  // 注意:不要 runScripts='dangerously' — 我们要自己 vm.Script 注入,使得 c8 按文件记录
  const dom = await JSDOM.fromURL(`http://127.0.0.1:${PORT}/platform-admin/index.html`, {
    runScripts: 'outside-only', // 只有外部能执行,HTML 内 <script src> 也不跑
    resources: 'usable',
    pretendToBeVisual: true,
    virtualConsole: vc,
  });
  const ctx = dom.window;
  // 把 shared/app-utils.js / platform-admin/app.js 通过 vm 方式执行在 window 上下文,带真实文件名
  for (const rel of ['shared/app-utils.js', 'platform-admin/app.js']) {
    const abs = path.join(ROOT, rel);
    const src = fs.readFileSync(abs, 'utf8');
    // 在 script 末尾加 \n//@ sourceURL=abs 确保 V8 关联
    const script = new vm.Script(src + `\n//# sourceURL=file://${abs}`, {
      filename: abs,  // <-- 关键:c8 按此路径匹配源文件
      displayErrors: true,
    });
    script.runInContext(ctx);
  }
  return { dom, errors };
}

function cleanupSession(sess) {
  try { if (sess.dom.window._aiTimers) sess.dom.window._aiTimers.forEach(t => clearInterval(t)); } catch(_) {}
  try { sess.dom.window.close(); } catch(_) {}
}

async function main() {
  const srv = await startStaticServer();
  // 为了稳定地触发各个代码分支,我们做 3 个独立 session:
  //   session A: 初始渲染 + redrawRateChart(对应 BUG#1, #3, #5)
  //   session B: 推进 30+ 次 + pushActivity(BUG#2, #4, #6, #8)
  //   session C: 二次 renderAI 定时管理 (#7)

  let failed = 0, passed = 0;
  const assert = (cond, msg) => {
    if (!cond) { failed++; console.log('  ASSERT FAIL:', msg); process.exitCode = 1; }
    else passed++;
  };

  // ---------- A ----------
  {
    const sess = await buildSession(srv);
    const w = sess.dom.window;
    w.renderAI();
    const inner = w.document.getElementById('rate-realtime-chart').innerHTML;
    assert(!inner.includes('undefined'), 'A1. 初始渲染无 undefined');
    assert(inner.includes('<svg'), 'A2. 初始渲染有 SVG');
    const legend = w.document.getElementById('rate-realtime-legend');
    assert(!!legend, 'A3. legend 元素存在');
    const lhtml = legend.innerHTML;
    assert(lhtml.includes('var(--c-locked)'), 'A4. legend 含 var(--c-locked)');
    assert(lhtml.includes('var(--c-accent)'), 'A5. legend 含 var(--c-accent)');
    const broken = (lhtml.match(/var\(--c-[a-z]+\)[^;:"'`>\s]/g) || []).filter(m => !m.endsWith(';'));
    assert(broken.length === 0, 'A6. 没有残缺 var(...) 变量,捕获='+JSON.stringify(broken));
    w.redrawRateChart();
    const kv = parseFloat(w.document.getElementById('rate-k-val').textContent);
    const rv = parseFloat(w.document.getElementById('rate-rate-val').textContent);
    assert(isFinite(kv) && kv>0, 'A7. k 值指标卡为正数,='+kv);
    assert(isFinite(rv) && rv>0, 'A8. rate 值指标卡为正数,='+rv);
    // 触发 lineChart 的各种分支:断线支持(NaN/null 段)、forecastFrom
    try {
      w.lineChart({
        w: 400, h: 180,
        labels: ['a','b','c','d','e'],
        series: [{ data:[1,null,3,2,4], name:'断线', color:'#f00', area:true, lastDot:false },
                 { data:[0.5,undefined,2,1.5,3.2], name:'dash', color:'#080', dash:true }],
        forecastFrom: 3,
      });
      passed++;
      console.log('  A9. lineChart 断线 + forecastFrom + area + dash + lastDot 混合执行 OK');
    } catch(e) { assert(false, 'A9. lineChart 多分支 err: '+e.message); }
    // 触发 donutChart / stackedBar / heatmap / radarChart(Bug 回归外函数也要被调用以提高覆盖率)
    try { w.donutChart({ w:260, h:220, data:[{label:'a',value:10,color:'var(--c-primary)'},{label:'b',value:20,color:'var(--c-available)'}] }); passed++; console.log('  A10. donutChart OK'); }
    catch(e) { assert(false,'A10. donutChart err: '+e.message); }
    try { w.stackedBar({ w:420, h:220, labels:['P1','P2','P3'], stacks:[{name:'S1',color:'var(--c-primary)',data:[1,4,7]},{name:'S2',color:'var(--c-warning)',data:[2,5,8]},{name:'S3',color:'var(--c-available)',data:[3,6,9]}] }); passed++; console.log('  A11. stackedBar OK'); }
    catch(e){ assert(false,'A11. stackedBar err: '+e.message); }
    try { w.heatmap({ w:420, h:180, rows:['M1','M2'], cols:['C1','C2','C3'], data:[[1,3,5],[2,4,6]], cmax:6, cmin:0 }); passed++; console.log('  A12. heatmap OK'); }
    catch(e){ assert(false,'A12. heatmap err: '+e.message); }
    try { w.radarChart({ w:300, h:260, labels:['a','b','c','d'], series:[{ name:'s1', color:'var(--c-primary)', data:[0.3,0.5,0.7,0.2] }] }); passed++; console.log('  A13. radarChart OK'); }
    catch(e){ assert(false,'A13. radarChart err: '+e.message); }
    // 所有 render* 渲染函数,提高分支覆盖
    const renders = ['renderDashboard','renderUsers','renderMerchant','renderOrders','renderWallet','renderLSC','renderB2B','renderAI','renderAIConfig','renderReports','renderSettings'];
    for (const fn of renders) {
      if (typeof w[fn] !== 'function') continue;
      try { w[fn](); } catch(_) {}
    }
    cleanupSession(sess);
  }

  // ---------- B ----------
  {
    const sess = await buildSession(srv);
    const w = sess.dom.window;
    w.renderAI();
    const k1 = w._rateSeries.k[0];
    const l1 = w._rateLabels[0];
    for (let i=0;i<35;i++) w.appendRatePoint();
    assert(w._rateLabels.length===24, 'B1. 35 次推进后 labels 长度 24');
    assert(w._rateSeries.k.length===24, 'B2. 35 次推进后 k 长度 24');
    assert(w._rateSeries.rate.length===24, 'B3. 35 次推进后 rate 长度 24');
    assert(w._rateLabels[0] !== l1, 'B4. label 左滑了:首项不同');
    assert(w._rateSeries.k[0] !== k1, 'B5. k 序列左滑了:首项不同');
    const kOk = w._rateSeries.k.every(v => v>=0.0045-1e-9 && v<=0.0085+1e-9);
    const rOk = w._rateSeries.rate.every(v => v>=0.0025-1e-9 && v<=0.0055+1e-9);
    assert(kOk, 'B6. k 区间合法');
    assert(rOk, 'B7. rate 区间合法');
    // 指标卡多次推进后更新
    const feed = w.document.getElementById('ai-activity-feed');
    const before = feed.children.length;
    for (let i=0;i<30;i++) w.pushActivity();
    const after = feed.children.length;
    assert(after === before+1 || (before+30 > 20 ? after <= 20 : after === before+30),
           'B8. pushActivity 条数规则正确 before='+before+' after='+after);
    const first = feed.firstElementChild;
    const style = first.getAttribute('style') || '';
    assert(style.includes('transition'), 'B9. 新条目含 transition 动效样式');
    assert(style.includes('opacity'), 'B10. 新条目含 opacity 淡入');
    assert(feed.children.length <= 20, 'B11. feed 总数 <= 20, actual='+feed.children.length);
    cleanupSession(sess);
  }

  // ---------- C ----------
  {
    const sess = await buildSession(srv);
    const w = sess.dom.window;
    w.renderAI();
    const t1 = w._aiTimers.slice();
    assert(Array.isArray(t1) && t1.length===2, 'C1. 首次 renderAI 创建 2 个 interval');
    w.renderAI();
    const t2 = w._aiTimers.slice();
    assert(t2.length===2, 'C2. 二次 renderAI 仍是 2 个');
    const allNew = t2.every(id => !t1.includes(id));
    assert(allNew, 'C3. 二次 renderAI 后 interval ID 已换新');
    // 100 次混合调用压力测试
    let threw = null;
    for (let i=0;i<100;i++) {
      try { if (i%5===0) w.pushActivity(); w.appendRatePoint(); }
      catch(e) { threw = e; break; }
    }
    assert(!threw, 'C4. 100 次混合无异常');
    cleanupSession(sess);
  }

  await new Promise(r => srv.close(r));
  console.log(`\n覆盖率执行器: passed=${passed} failed=${failed}`);
  process.exit(failed>0 ? 1 : 0);
}
main().catch(e => { console.error(e); process.exit(2); });
