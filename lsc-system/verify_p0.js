// P0 短期任务验证脚本
const fs = require('fs');
const path = require('path');
const { JSDOM, ResourceLoader, VirtualConsole } = require('jsdom');

const ROOT = __dirname;
const APPS = [
  { name: '平台管理后台', folder: 'platform-admin', url: 'platform-admin/index.html',
    checks: [
      { desc: 'donutChart 函数存在', check: d => typeof d.window.donutChart === 'function' },
      { desc: 'heatmap 函数存在', check: d => typeof d.window.heatmap === 'function' },
      { desc: 'stackedBar 函数存在', check: d => typeof d.window.stackedBar === 'function' },
      { desc: 'radarChart 函数存在', check: d => typeof d.window.radarChart === 'function' },
      { desc: '释放速率图真实时容器 rate-realtime-chart 存在', check: d => { try { d.window.renderAI(); return !!d.window.document.getElementById('rate-realtime-chart'); } catch(e){ return false; }}},
      { desc: '速率指标卡 ID rate-k-val / rate-rate-val / rate-trend 存在', check: d => { try { d.window.renderAI(); const doc=d.window.document; return !!doc.getElementById('rate-k-val') && !!doc.getElementById('rate-rate-val') && !!doc.getElementById('rate-trend');} catch(e){return false;} }},
      { desc: 'AI活动流 feed 容器 ai-activity-feed 存在', check: d => { try { d.window.renderAI(); return !!d.window.document.getElementById('ai-activity-feed'); } catch(e){return false;}}},
      { desc: 'B2B订单状态机 SVG 已渲染', check: d => { try { d.window.renderB2B(); return d.window.document.querySelector('#view svg,svg') !== null; } catch(e){return false;}}},
      { desc: 'window._aiTimers 设置了 2 个 interval(活动流+速率)', check: d => {
          try { d.window.renderAI(); return Array.isArray(d.window._aiTimers) && d.window._aiTimers.length===2; }
          catch(e){return false;}}
      },
      { desc: 'P0-4: appendRatePoint 能追加新点并保持窗口=24', check: d => {
          try {
            d.window.renderAI();
            const n0 = d.window._rateLabels.length;
            d.window.appendRatePoint();
            const n1 = d.window._rateLabels.length;
            return n0===24 && n1===24 && Array.isArray(d.window._aiTimers);
          } catch(e){return false;}
        }
      },
    ] },
  { name: '商家管理后台', folder: 'merchant-admin', url: 'merchant-admin/index.html',
    checks: [
      { desc: '经营总览导航入口存在', check: d => d.window.document.querySelector('nav .nav-item,nav a,.sidebar-item')?.textContent?.includes('经营总览') ?? false },
      { desc: 'renderDashboard 函数存在', check: d => typeof d.window.renderDashboard === 'function' },
      { desc: '商家 donutChart 函数存在', check: d => typeof d.window.donutChart === 'function' },
      { desc: '商家 stackedBar 函数存在', check: d => typeof d.window.stackedBar === 'function' },
      { desc: '商家 lineChart 函数存在', check: d => typeof d.window.lineChart === 'function' },
      { desc: '商家经营总览页面包含 SVG 图表', check: d => { try { d.window.renderDashboard(); return d.window.document.querySelectorAll('svg').length>0; } catch(e){return false;}}},
    ] },
  { name: '移动端APP', folder: 'mobile-app', url: 'mobile-app/index.html',
    checks: [
      { desc: 'renderWallet 函数存在', check: d => typeof d.window.renderWallet === 'function' },
      { desc: '钱包页SVG流转链路图:包含"锁定池"节点文本', check: d => { try { d.window.renderWallet(); const h=d.window.document.getElementById('screen-wallet').innerHTML; return h.includes('锁定池') && h.includes('可用池') && h.includes('<svg'); } catch(e){return false;}}},
      { desc: '钱包页流转链路含5个节点(发行、锁定、可用、消费、推广)', check: d => {
          try {
            d.window.renderWallet();
            const html = d.window.document.getElementById('screen-wallet').innerHTML;
            return ['消费发行','锁定池','可用池','线下消费','推广奖励'].every(t => html.includes(t));
          } catch(e){return false;}
        }
      },
    ] },
  { name: '微信小程序', folder: 'mini-program', url: 'mini-program/index.html',
    checks: [
      { desc: 'renderWallet 函数存在', check: d => typeof d.window.renderWallet === 'function' },
      { desc: '钱包页SVG流转链路图:包含锁定池/可用池节点文本', check: d => { try { d.window.renderWallet(); const h=d.window.document.getElementById('screen-wallet').innerHTML; return h.includes('锁定池') && h.includes('可用池') && h.includes('<svg'); } catch(e){return false;}}},
      { desc: '钱包页流转链路含5个节点', check: d => {
          try {
            d.window.renderWallet();
            const html = d.window.document.getElementById('screen-wallet').innerHTML;
            return ['消费发行','锁定池','可用池','扫码消费','推广奖励'].every(t => html.includes(t));
          } catch(e){return false;}
        }
      },
    ] },
];

async function runVerify() {
  const vc = new VirtualConsole();
  const errors = [];
  const warns = [];
  vc.on('error', e => errors.push(String(e)));
  vc.on('warn', w => warns.push(String(w)));
  vc.on('jsdomError', e => errors.push('JS-DOM: '+String(e.message||e)));

  let passAll = true;
  const results = [];
  for (const app of APPS) {
    const appErrors = [];
    const appWarns  = [];
    const vc2 = new VirtualConsole();
    vc2.on('error', e => appErrors.push(String(e)));
    vc2.on('warn', w => appWarns.push(String(w)));
    vc2.on('jsdomError', e => appErrors.push('JS-DOM: '+String(e.message||e)));

    const htmlPath = path.join(ROOT, app.url);
    const html = fs.readFileSync(htmlPath, 'utf8');
    // 注入共享脚本,手动读取并拼接在 <head> 开头
    const utilsJs = fs.readFileSync(path.join(ROOT, 'shared/app-utils.js'),'utf8');
    const injected = html.replace('<head>', `<head><script>${utilsJs}<\/script>`);
    const dom = new JSDOM(injected, {
      url: 'http://localhost/'+app.url,
      runScripts: 'dangerously',
      resources: 'usable',
      pretendToBeVisual: true,
      virtualConsole: vc2,
    });
    // 等待外部脚本执行(如果有)
    await new Promise(r => setTimeout(r, 200));
    const appResult = { name: app.name, checks: [] };
    for (const c of app.checks) {
      let ok=false, reason='';
      try { ok = !!c.check(dom); }
      catch(e){ ok=false; reason = 'ERR: '+e.message; }
      if (!ok) passAll = false;
      appResult.checks.push({ desc: c.desc, ok, reason });
    }
    // 清理 timers
    try { if (dom.window._aiTimers) { dom.window._aiTimers.forEach(t=>clearInterval(t)); } } catch(_){}
    appResult.runtimeErrors = appErrors.filter(e => !e.includes('Could not parse CSS'));
    appResult.runtimeWarns  = appWarns.slice(0, 3);
    results.push(appResult);
    dom.window.close();
  }
  // 打印摘要
  console.log('\n====================  P0 短期任务验证报告  ====================\n');
  for (const r of results) {
    const total = r.checks.length;
    const okCnt = r.checks.filter(c=>c.ok).length;
    const flag = okCnt===total ? '✅ PASS' : `❌ FAIL (${okCnt}/${total})`;
    console.log(`【${r.name}】 ${flag}`);
    for (const c of r.checks) {
      const prefix = c.ok ? '  ✓' : '  ✗';
      console.log(`${prefix} ${c.desc}${c.reason?'  => '+c.reason:''}`);
    }
    if (r.runtimeErrors && r.runtimeErrors.length) {
      console.log(`  运行时错误 (${r.runtimeErrors.length}):`);
      r.runtimeErrors.slice(0,4).forEach(e=>console.log('    • '+e.slice(0,180)));
    }
    console.log('');
  }
  const totalChecks = results.reduce((a,b)=>a+b.checks.length,0);
  const totalPass   = results.reduce((a,b)=>a+b.checks.filter(c=>c.ok).length,0);
  console.log(`总计: ${totalPass}/${totalChecks} 断言通过  ${passAll?'✅ 全部通过':'❌ 存在失败'}`);
  process.exit(passAll ? 0 : 1);
}
runVerify().catch(e=>{console.error('验证脚本异常:',e);process.exit(2);});
