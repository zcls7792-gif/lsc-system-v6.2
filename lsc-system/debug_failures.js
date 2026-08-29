/** 调试6个FAIL断言: 基于coverage_runner.js的buildSession模式 */
const path = require('path');
const fs = require('fs');
const vm = require('vm');
const http = require('http');
const { JSDOM, VirtualConsole } = require('jsdom');
const ROOT = __dirname;
const PORT = 18765;

const COVER_APPS = [
  ['merchant-admin', '/merchant-admin/index.html', ['shared/app-utils.js', 'merchant-admin/app.js']],
  ['mini-program',   '/mini-program/index.html',   ['shared/app-utils.js', 'mini-program/app.js']],
];

function startStaticServer() {
  return new Promise((res, rej) => {
    const srv = http.createServer((req, res) => {
      let p = req.url.split('?')[0];
      let full = path.join(ROOT, p);
      if (!full.startsWith(ROOT)) { res.writeHead(403); res.end('403'); return; }
      fs.readFile(full, (err, d) => {
        if (err) { res.writeHead(404); res.end('404'); return; }
        const ext = path.extname(full).toLowerCase();
        const mime = { '.html':'text/html; charset=utf-8', '.js':'application/javascript; charset=utf-8', '.css':'text/css; charset=utf-8' }[ext] || 'application/octet-stream';
        res.writeHead(200, { 'Content-Type': mime }); res.end(d);
      });
    });
    srv.listen(PORT, '127.0.0.1', () => res(srv));
  });
}

async function buildSession(srv, appEntry) {
  const vc = new VirtualConsole();
  const errors = [];
  vc.on('error', e => errors.push('C: '+String(e)));
  vc.on('warn', () => {});
  vc.on('jsdomError', e => errors.push('J: '+String(e.message||e)));
  const dom = await JSDOM.fromURL(`http://127.0.0.1:${PORT}${appEntry[1]}`, {
    runScripts: 'outside-only', resources: 'usable', pretendToBeVisual: true, virtualConsole: vc,
  });
  const ctx = dom.window;
  for (const rel of appEntry[2]) {
    const abs = path.join(ROOT, rel);
    const src = fs.readFileSync(abs, 'utf8');
    const script = new vm.Script(src + `\n//# sourceURL=file://${abs}`, { filename: abs, displayErrors: true });
    script.runInContext(ctx);
  }
  const exposeHelper = new vm.Script(
    `if (typeof LSC !== 'undefined')   { globalThis.LSC   = LSC;   }
     if (typeof MOCK !== 'undefined')  { globalThis.MOCK  = MOCK;  }
     if (typeof ICONS !== 'undefined') { globalThis.ICONS = ICONS; }`,
    { filename: path.join(ROOT, 'coverage/__LSC_expose__.js'), displayErrors: true }
  );
  exposeHelper.runInContext(ctx);
  // 初始渲染
  if (typeof ctx.renderDashboard === 'function') ctx.renderDashboard();
  if (typeof ctx.navTo === 'function') ctx.navTo('dashboard');
  else if (typeof ctx.showScreen === 'function') ctx.showScreen('home');
  return { dom, errors, app: appEntry[0] };
}

const runVM = (w, code) => new vm.Script(`(function(){ try { return (${code}); } catch(e){ return { __vmError: String(e && e.message || e) }; } })();`, { filename: path.join(ROOT, 'coverage/__vm__.js'), displayErrors: true }).runInContext(w);
const execVM = (w, code) => { const r = runVM(w, `(function(){ ${code}; })()`); if (r && r.__vmError) throw new Error(r.__vmError); return r; };

(async () => {
  const srv = await startStaticServer();
  try {
    console.log('========== 1. merchant-admin: F24 图表全等/空 ==========');
    const s1 = await buildSession(srv, COVER_APPS[0]);
    const mw = s1.dom.window;
    const r24 = execVM(mw, `
      var r = {};
      // 检查 window 对象中图表函数是否存在
      r.hasLine = typeof lineChart === 'function';
      r.hasDonut = typeof donutChart === 'function';
      r.hasStacked = typeof stackedBar === 'function';
      // F24a: 全等数据 lineChart
      try {
        r.a_raw = lineChart({ labels:['D1','D2','D3'], series:[{ name:'s', data:[10,10,10] }] });
        r.a = typeof r.a_raw === 'string' && r.a_raw.includes('<svg');
        if (!r.a) r.a_sub = String(r.a_raw).slice(0, 500);
      } catch(e1){ r.a_err = e1.message; r.a_stack = e1.stack; }
      // F24b: 空 series data
      try {
        r.a2_raw = lineChart({ labels:['D1'], series:[{ name:'s', data:[] }] });
        r.a2 = typeof r.a2_raw === 'string' && r.a2_raw.includes('<svg');
        if (!r.a2) r.a2_sub = String(r.a2_raw).slice(0, 500);
      } catch(e2){ r.a2_err = e2.message; }
      // F24d: stackedBar 全0
      try {
        r.c_raw = stackedBar({ labels:['x','y'], stacks:[{ name:'a', data:[0,0] },{ name:'b', data:[0,0] }] });
        r.c = typeof r.c_raw === 'string' && r.c_raw.includes('<svg');
        if (!r.c) r.c_sub = String(r.c_raw).slice(0, 500);
      } catch(e3){ r.c_err = e3.message; }
      return r;
    `);
    console.log('F24:', JSON.stringify(r24, null, 2));

    console.log('\n========== 2. merchant-admin: F25 range/status ==========');
    const r25 = execVM(mw, `
      var r = {};
      // F25b: 释放趋势全等 range
      try {
        r.b_raw = lineChart({ labels:['D1','D2','D3','D4'], series:[{ name:'x', data:[385,385,385,385] }] });
        r.b = (typeof r.b_raw === 'string' && r.b_raw.includes('<svg')) ? 'ok' : 'fail';
        if (r.b === 'fail') r.b_sub = String(r.b_raw).slice(0, 500);
      } catch(eb){ r.b = 'err:'+eb.message; }
      // F25d: status off → 已下架
      try {
        if (typeof showProductDetail !== 'function') { r.d = 'no_func'; }
        else {
          // 检查 products / MOCK.products 变量
          var useMOCK = false;
          try { if (typeof products !== 'undefined' && products['P_OFFLINE'] !== undefined) useMOCK = false; } catch(_) {}
          try { if (typeof MOCK !== 'undefined') { MOCK.products = MOCK.products || {}; MOCK.products['P_OFFLINE'] = { name:'下架T', price:10, status:'off', video:'none', aiScore:0.5, aiTags:[] }; useMOCK = true; } } catch(_) {}
          showProductDetail('P_OFFLINE');
          var body = document.querySelector('.modal-body');
          r.d_bodyLen = body ? body.innerHTML.length : -1;
          r.d_body = body ? body.innerHTML.slice(0, 2000) : '';
          r.d = r.d_body.includes('已下架') ? 'ok' : 'no_match:'+r.d_bodyLen;
          if (document.getElementById('global-modal')) closeModal();
        }
      } catch(ed){ r.d = 'err:'+ed.message; }
      return r;
    `);
    console.log('F25:', JSON.stringify(r25, (k,v) => typeof v === 'string' && v.length > 800 ? v.slice(0,400)+'...' : v, 2));

    console.log('\n========== 3. mini-program: wxShare mask click ==========');
    const s3 = await buildSession(srv, COVER_APPS[1]);
    const xw = s3.dom.window;
    const r15 = execVM(xw, `
      var r = {};
      // 清理旧modal
      var oldM = document.querySelector('.modal-mask');
      if (oldM) oldM.remove();
      wxShare();
      var maskA = document.querySelector('.modal-mask');
      r.maskABefore = !!maskA;
      if (maskA) {
        // 用原生方式dispatch, target必须等于maskA
        var ev = document.createEvent('Event');
        ev.initEvent('click', false, false);
        // 强制target绑定
        maskA.dispatchEvent(ev);
        r.maskAfterA = !!document.querySelector('.modal-mask');
        // 如果还存在,手动尝试移除方式2: 直接调用listener函数
        if (r.maskAfterA) {
          // 用jQuery方式手动判断e.target===mask
          var maskB = document.querySelector('.modal-mask');
          // 用createEvent + 在 mask 本身上触发
          maskB.click();
          r.maskAfterB = !!document.querySelector('.modal-mask');
          // 终极: 用 Object.defineProperty 伪造 target
          if (r.maskAfterB) {
            var maskC = document.querySelector('.modal-mask');
            var evt = new Event('click', { bubbles: false });
            // 直接触发 maskC remove
            try { maskC.remove(); r.manualRemove = true; } catch(_){}
            r.maskAfterC = !!document.querySelector('.modal-mask');
            // 再次 wxShare, 检查 listener 内部条件
            wxShare();
            var maskD = document.querySelector('.modal-mask');
            r.maskDBefore = !!maskD;
            // 调试: 看maskD的onclick/listeners
            var evtD = new Event('click', { bubbles: false });
            // 调用时用 Object.defineProperty 固定 target
            Object.defineProperty(evtD, 'target', { value: maskD, writable: false, configurable: true });
            maskD.dispatchEvent(evtD);
            r.maskAfterD = !!document.querySelector('.modal-mask');
          }
        }
      }
      return r;
    `);
    console.log('F15a:', JSON.stringify(r15, null, 2));
  } finally {
    srv.close();
  }
})().catch(e => { console.error('ERROR:', e); process.exit(1); });
