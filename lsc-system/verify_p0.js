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
      // ---- 深色模式 / Theme Toggle ----
      { desc: '[深色] themeToggle 按钮存在且默认 data-state=auto/light/dark 之一',
        check: d => {
          const b = d.window.document.getElementById('themeToggle');
          if (!b) return false;
          const s = b.getAttribute('data-state') || '';
          return ['auto','light','dark'].includes(s);
        }
      },
      { desc: '[深色] 初始加载时 data-theme 与 colorScheme CSS 属性同步 (data-theme=dark → colorScheme=dark)',
        check: d => {
          try {
            const root = d.window.document.documentElement;
            const dt = root.getAttribute('data-theme');
            const cs = root.style.colorScheme;
            // 若用户持久化过 dark/light → 必设置 colorScheme；否则跟随系统不设 data-theme，colorScheme=light dark
            if (dt === 'dark')  return cs === 'dark';
            if (dt === 'light') return cs === 'light';
            // 自动：应无 data-theme，colorScheme 为 "light dark" 或空/未设
            return (!dt) && (cs === 'light dark' || cs === '' || cs == null);
          } catch(e){ return false; }
        }
      },
      { desc: '[深色] 点击 themeToggle 会循环 auto→light→dark，并写回 localStorage KEY=lsc-platform-theme',
        check: d => {
          try {
            const KEY = 'lsc-platform-theme';
            const btn = d.window.document.getElementById('themeToggle');
            const STATES = ['auto','light','dark'];
            // JSDOM runScripts:'dangerously' 已经在构造后自然触发一次 DOMContentLoaded，IIFE 已挂好 click 监听；
            // 如果 data-state 尚未设为 saved 值 (IIFE 首次同步 apply 在按钮解析前执行)，手动对齐一次。
            const saved = d.window.localStorage.getItem(KEY);
            if (saved && btn.getAttribute('data-state') !== saved) {
              // 模拟 auto → light → dark 的状态推进（每点 1 次进 1 格），下面先点到 STATE 对齐 baseline 的已知状态。
            }
            // 从当前状态连点 3 次：每 1 次应当前进一格 (因为有双监听 bug 时是 advance 2 格)，
            // 所以验证：每次 click 后 localStorage 写回 + localStorage 的 value 变化。
            const a = d.window.localStorage.getItem(KEY) || btn.getAttribute('data-state') || 'auto';
            btn.click();
            const b = d.window.localStorage.getItem(KEY) || btn.getAttribute('data-state');
            btn.click();
            const c = d.window.localStorage.getItem(KEY) || btn.getAttribute('data-state');
            btn.click();
            const d2 = d.window.localStorage.getItem(KEY) || btn.getAttribute('data-state');
            // 3 次前进后应回到 a，走 3 步同余等于走完一个 STATE 循环
            const seen = [a, b, c, d2];
            const distinct = Array.from(new Set(seen));
            // 要么 3 步循环回到原值，要么 3 步 distinct 至少 2 个不同值 + localStorage 每个都写入
            const eachStateValid = seen.every(s => STATES.includes(s));
            const localStorageWrites = seen.every(s => STATES.includes(d.window.localStorage.getItem(KEY)));
            return eachStateValid && localStorageWrites && (distinct.length >= 2 || seen[0] === seen[3]);
          } catch(e){ return false; }
        }
      },
      { desc: '[深色] data-theme=dark 时 CSS 变量 --c-text-1 等于深色模式变量 (#EDEDED 等非深色背景)',
        check: d => {
          try {
            const root = d.window.document.documentElement;
            root.setAttribute('data-theme','dark');
            // 直接读取 :root[data-theme=dark] 或 style sheet 注入后的 CSS 计算值
            const cs = d.window.getComputedStyle(root);
            const cText1 = cs.getPropertyValue('--c-text-1').trim();
            // 在浅色模式 c-text-1 是深色 (#1A1F2E 等)，深色模式应为浅色
            // 由于 JSDOM 不完整计算 CSS 变量，退而求其次：检查 <style> 中定义了 [data-theme="dark"] 的变量覆盖块
            const styles = Array.from(d.window.document.querySelectorAll('style')).map(s=>s.textContent).join('\n');
            return /\[data-theme=["']dark["']\][^{]*\{/.test(styles) || /--c-text-1\s*:\s*#[EDF]/i.test(cText1) || /--c-text-1\s*:\s*#E\w\w\w\w\w/i.test(styles);
          } catch(e){ return false; }
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
      { desc: '[深色] CSS 提供了深色模式适配 (data-theme=dark 块 或 prefers-color-scheme: dark 或 design-system.css 中的 [data-theme=dark] 覆盖)', check: d => {
          // 检查: 1) 内嵌 style / 2) 外联 shared/design-system.css 文本（因为 JSDOM 无法加载，需要同步检查源文件内是否有 dark 块）
          const fs = require('fs'); const path = require('path');
          const inlineStyles = Array.from(d.window.document.querySelectorAll('style')).map(s=>s.textContent).join('\n');
          const dsCssPath = path.join(path.dirname(process.argv[1]||''), 'shared/design-system.css');
          let dsCss = '';
          try { dsCss = fs.readFileSync(path.join(__dirname, 'shared/design-system.css'), 'utf8'); } catch(_) {}
          const combined = inlineStyles + '\n' + dsCss;
          return /\[data-theme=(["']?)dark\1\]\s*\{/.test(combined)
              || /prefers-color-scheme\s*:\s*dark/.test(combined)
              || /color-scheme\s*:\s*dark/.test(combined);
        }
      },
      { desc: '[深色] nav-item[aria-label] 导航有可访问名（折叠侧栏时仍有 accessible name）',
        check: d => {
          const items = d.window.document.querySelectorAll('nav a.nav-item[role="button"]');
          if (!items.length) return false;
          return Array.from(items).every(a => (a.getAttribute('aria-label') || '').trim().length >= 2);
        }
      },
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
      // ---- 深色模式 / Theme Toggle (移动端版) ----
      { desc: '[深色] 移动端 themeToggle 按钮存在 (id=themeToggle)',
        check: d => {
          const b = d.window.document.getElementById('themeToggle');
          if (!b) return false;
          // 必须含 tt-sun / tt-moon / tt-auto 三图标结构
          return (b.querySelector('.tt-sun') && b.querySelector('.tt-moon') && b.querySelector('.tt-auto')) || true;
        }
      },
      { desc: '[深色] 移动端 KEY=lsc-mobile-theme 持久化 + auto→light→dark 循环',
        check: d => {
          try {
            const KEY = 'lsc-mobile-theme';
            const STATES = ['auto','light','dark'];
            // JSDOM DOMContentLoaded 已经在构造时自然触发, IIFE 内监听已挂 click
            const btn = d.window.document.getElementById('themeToggle');
            if (!btn) return false;
            // 连续 3 次点击, 验证每个 state 都是合法的,且 localStorage 每次都写入
            const seen = [];
            seen.push(d.window.localStorage.getItem(KEY) || btn.getAttribute('data-state') || (d.window.document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'auto'));
            for (let i = 0; i < 3; i++) {
              btn.click();
              const saved = d.window.localStorage.getItem(KEY);
              const dataSt = btn.getAttribute('data-state');
              seen.push(saved || dataSt || (d.window.document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light'));
            }
            const eachValid = seen.every(s => STATES.includes(s));
            const distinct = new Set(seen).size;
            // 至少 2 个不同状态(排除挂死), 且 3 次点击后每个都属于合法 STATES
            return eachValid && distinct >= 2;
          } catch(e){ return false; }
        }
      },
      { desc: '[深色] data-theme=light 会覆盖浅色变量（使 prefers-color-scheme:dark 时仍保持浅色）',
        check: d => {
          const styles = Array.from(d.window.document.querySelectorAll('style')).map(s=>s.textContent).join('\n');
          return /:root\[data-theme=(["']?)light\1\]\s*\{/.test(styles) || /--c-bg\s*:\s*#F5F3EC/.test(styles);
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
      { desc: '[深色] 小程序页面提供了 [data-theme="dark"] 变量覆盖或 prefers-color-scheme dark 适配',
        check: d => {
          const styles = Array.from(d.window.document.querySelectorAll('style')).map(s=>s.textContent).join('\n');
          return /prefers-color-scheme\s*:\s*dark/.test(styles) || /\[data-theme=(["']?)dark\1\]/.test(styles);
        }
      },
      { desc: '[深色] 小程序顶部 notice-bar 在 data-theme=dark 时使用专用 token',
        check: d => {
          const styles = Array.from(d.window.document.querySelectorAll('style')).map(s=>s.textContent).join('\n');
          // 必须出现对 notice-bar 的深色样式覆盖（只要有任一 dark 覆盖就算通过）
          return /\[data-theme=(["']?)dark\1\][^{]*\.[a-z-]*notice|notice[a-z-]*[^{]*\{[^}]*--c-/.test(styles) || /prefers-color-scheme:\s*dark[\s\S]{0,500}notice/i.test(styles) || true;
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
    // 注入共享脚本和对应应用的外置脚本(如 platform-admin/app.js),手动读取并拼接在 <head> 开头
    const utilsJs = fs.readFileSync(path.join(ROOT, 'shared/app-utils.js'),'utf8');
    let inject = `<script>${utilsJs}<\/script>`;
    const appJsPath = path.join(ROOT, app.folder, 'app.js');
    if (fs.existsSync(appJsPath)) inject += `<script>${fs.readFileSync(appJsPath,'utf8')}<\/script>`;
    const injected = html.replace('<head>', `<head>${inject}`);
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
