/** 快速单测 A39 问题: 为什么 updateSig 不改变 status 文案 */
const http = require('http');
const path = require('path');
const fs   = require('fs');
const vm   = require('vm');
const { JSDOM, VirtualConsole } = require('jsdom');
const ROOT = __dirname;
const PORT = 18999;

async function main(){
  const srv = http.createServer((req, res) => {
    let up = decodeURIComponent((req.url||'/').split('?')[0]);
    if (up === '/') up = '/index.html';
    const full = path.normalize(path.join(ROOT, up));
    if (!full.startsWith(ROOT)) { res.writeHead(403); res.end(); return; }
    fs.readFile(full, (err, d) => {
      if (err) { res.writeHead(404); res.end(); return; }
      const ext = path.extname(full).toLowerCase();
      const mime = { '.html':'text/html; charset=utf-8', '.js':'application/javascript; charset=utf-8', '.css':'text/css; charset=utf-8' }[ext] || 'application/octet-stream';
      res.writeHead(200, { 'Content-Type': mime }); res.end(d);
    });
  }).listen(PORT, '127.0.0.1');
  const vc = new VirtualConsole();
  vc.on('error', e => console.log('[E]', String(e)));
  vc.on('warn', () => {});
  const dom = await JSDOM.fromURL(`http://127.0.0.1:${PORT}/platform-admin/index.html`, {
    runScripts: 'outside-only', resources: 'usable', pretendToBeVisual: true, virtualConsole: vc,
  });
  const w = dom.window;
  for (const rel of ['shared/app-utils.js', 'platform-admin/app.js']) {
    const abs = path.join(ROOT, rel);
    const src = fs.readFileSync(abs, 'utf8');
    new vm.Script(src + `\n//# sourceURL=file://${abs}`, { filename: abs, displayErrors: true }).runInContext(w);
  }
  new vm.Script(`
    if (typeof LSC !== 'undefined')   globalThis.LSC = LSC;
    if (typeof MOCK !== 'undefined')  globalThis.MOCK = MOCK;
    if (typeof ICONS !== 'undefined') globalThis.ICONS = ICONS;
  `, { filename: ROOT+'/coverage/__expose.js' }).runInContext(w);

  // 先打开 dualApprovalModal → 它会赋值 window._dualSig 与 window.updateSig
  w.dualApprovalModal({ title:'test', danger:false, summary:'<div>x</div>', onApprove: ()=>{} });
  new vm.Script(`
    window.__info = {
      dsDesc1: Object.getOwnPropertyDescriptor(window, '_dualSig'),
      dsDesc2: Object.getOwnPropertyDescriptor(Object.getPrototypeOf(window), '_dualSig'),
      hasOwn: Object.prototype.hasOwnProperty.call(window, '_dualSig'),
      keys: Object.keys(window._dualSig || {}),
      props: Object.getOwnPropertyDescriptors(window._dualSig || {}),
      ext: Object.isExtensible(window._dualSig || {}),
      frozen: Object.isFrozen(window._dualSig || {}),
    };
    // 直接用 Object.defineProperty 写 sig1
    Object.defineProperty(window._dualSig, 'sig1', { value: 'DEF111', writable:true, configurable:true, enumerable:true });
    window.__afterDef = { s1: window._dualSig.s1 };
    // 用 globalThis
    globalThis._dualSig.sig2 = 'GT222';
    window.__afterGT = { s2: globalThis._dualSig.sig2 };
    // 再次检查 window._dualSig
    window.__final = { s1: window._dualSig.s1, s2: window._dualSig.sig2 };
  `, { filename: ROOT+'/dbgInline.js' }).runInContext(w);
  console.log('info=', JSON.stringify(w.__info, null, 2));
  console.log('afterDef.s1=', w.__afterDef.s1);
  console.log('afterGT.s2=', w.__afterGT.s2);
  console.log('final=', JSON.stringify(w.__final));

  try { w.closeModal(); srv.close(); } catch(_){}
}
main().catch(e => { console.error('FATAL', e.stack); process.exit(1); });
