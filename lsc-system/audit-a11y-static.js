#!/usr/bin/env node
/**
 * LSC V6.2-AI · A11y 静态审计 (jsdom + axe-core 兜底)
 *
 * 当 chromium 不可用时,使用 jsdom 加载页面 + 注入 axe-core 跑规则。
 * 覆盖 4 个结构性规则:
 *   - landmark-one-main
 *   - page-has-heading-one
 *   - region
 *   - scrollable-region-focusable (依赖 getComputedStyle, jsdom 可能不全)
 *
 * color-contrast 需真实渲染颜色, jsdom 无法解析 CSS 变量与继承, 单独走
 * 静态对比度计算 (design-system.css 变量→前景/背景对比度)。
 *
 * 输出: audit-report/a11y-static.json + 控制台总览
 */
'use strict';
const path = require('path');
const fs = require('fs');
const { JSDOM } = require('jsdom');

const ROOT = __dirname;
const OUT_DIR = path.join(ROOT, 'audit-report');
fs.mkdirSync(OUT_DIR, { recursive: true });
const JSON_OUT = path.join(OUT_DIR, 'a11y-static.json');

const AXE_LOCAL = path.join(OUT_DIR, 'axe.min.js');

const APPS = [
  { id: 'platform', name: '平台管理后台', path: '/platform-admin/index.html' },
  { id: 'merchant', name: '商家管理后台', path: '/merchant-admin/index.html' },
  { id: 'mobile',   name: '移动端 APP',   path: '/mobile-app/index.html'   },
  { id: 'mini',     name: '微信小程序',   path: '/mini-program/index.html' },
];

// WCAG 对比度计算
function hexToRgb(h) {
  h = (h || '').trim().replace(/^#/, '');
  if (h.length === 3) h = h.split('').map(c=>c+c).join('');
  const n = parseInt(h, 16);
  if (isNaN(n)) return null;
  return { r: (n>>16)&255, g: (n>>8)&255, b: n&255 };
}
function srgb(c) { c/=255; return c<=0.03928 ? c/12.92 : Math.pow((c+0.055)/1.055, 2.4); }
function lum(rgb) { return 0.2126*srgb(rgb.r) + 0.7152*srgb(rgb.g) + 0.0722*srgb(rgb.b); }
function contrast(fg, bg) {
  const f = hexToRgb(fg), b = hexToRgb(bg);
  if (!f || !b) return null;
  const L1 = lum(f), L2 = lum(b);
  const hi = Math.max(L1, L2), lo = Math.min(L1, L2);
  return (hi + 0.05) / (lo + 0.05);
}

// 从 design-system.css 提取设计令牌变量
// mode='light': 仅读取顶层的 :root { ... } 块 (不嵌套在 @media / 选择器内)
// mode='dark':  读取 @media (prefers-color-scheme: dark) 中的 :root 块, fallback 到 [data-theme="dark"] 块
function loadCssVars(cssPath, mode = 'light') {
  const txt = fs.readFileSync(cssPath, 'utf8');
  const vars = {};
  let scope = '';

  if (mode === 'light') {
    // 1) 先删除所有 @media 块和属性选择器块, 只保留顶层声明
    let top = txt
      .replace(/@media[^{]*\{[\s\S]*?\n\s*\}/g, '')
      .replace(/\[data-theme=["']\w+["']\]\s*\{[\s\S]*?\n\s*\}/g, '');
    // 2) 再取顶层 :root { ... }
    const rootRe = /(?:^|\n)\s*:root\s*\{([\s\S]*?)\n\s*\}/;
    const m = top.match(rootRe);
    scope = m ? m[1] : top;
  } else if (mode === 'dark') {
    // 优先 @media (prefers-color-scheme: dark) { :root { ... } }
    const mediaRe = /@media\s*\(\s*prefers-color-scheme\s*:\s*dark\s*\)\s*\{([\s\S]*?)\n\s*\}/;
    const mediaMatch = txt.match(mediaRe);
    if (mediaMatch) {
      const inner = mediaMatch[1];
      const rootInMedia = inner.match(/:root\s*\{([\s\S]*?)\n\s*\}/);
      scope = rootInMedia ? rootInMedia[1] : inner;
    } else {
      const attrRe = /\[data-theme=["']dark["']\]\s*\{([\s\S]*?)\n\s*\}/;
      const attrMatch = txt.match(attrRe);
      if (attrMatch) scope = attrMatch[1];
    }
  }

  const re = /--([\w-]+)\s*:\s*([^;]+);/g;
  let m;
  while ((m = re.exec(scope))) {
    vars['--' + m[1]] = m[2].trim();
  }
  return vars;
}

// 跑一个 app 的 jsdom + axe
async function auditOne(app) {
  const filePath = path.join(ROOT, app.path);
  const html = fs.readFileSync(filePath, 'utf8');

  // 加载 app.js (用于执行 a11yEnhance)
  const appJsPath = path.join(path.dirname(filePath), 'app.js');
  const appJs = fs.existsSync(appJsPath) ? fs.readFileSync(appJsPath, 'utf8') : '';
  const sharedUtilsPath = path.join(ROOT, 'shared/app-utils.js');
  const sharedUtils = fs.existsSync(sharedUtilsPath) ? fs.readFileSync(sharedUtilsPath, 'utf8') : '';

  const dom = new JSDOM(html, {
    runScripts: 'outside-only',
    url: 'http://localhost/' + app.path,
    pretendToBeVisual: true,
    resources: 'usable',
  });
  const { window } = dom;
  // 注入 shared 工具与 app.js (在 jsdom 沙箱内执行)
  try {
    window.eval(sharedUtils);
    if (appJs) window.eval(appJs);
    // 显式调用 a11yEnhance (若 app.js 未调用)
    if (window.LSC && typeof window.LSC.a11yEnhance === 'function') {
      try { window.LSC.a11yEnhance(window.document); } catch(_) {}
    }
  } catch (e) {
    // app.js 可能引用未实现 API, 忽略运行时错误, 结构层面已加载
  }

  let axeRes = null;
  if (fs.existsSync(AXE_LOCAL)) {
    try {
      const axeSrc = fs.readFileSync(AXE_LOCAL, 'utf8');
      window.eval(axeSrc);
      if (typeof window.axe !== 'undefined') {
        const r = await window.axe.run(window.document, {
          runOnly: { type: 'tag', values: ['wcag2a','wcag2aa','best-practice'] },
          resultTypes: ['violations','incomplete'],
        });
        axeRes = {
          violations: r.violations.map(v => ({
            id: v.id, impact: v.impact, count: v.nodes.length,
            sample: v.nodes.slice(0, 3).map(n => ({ target: n.target?.[0] || '', html: (n.html||'').slice(0,160) })),
          })),
          passes: r.passes?.length || 0,
          incomplete: r.incomplete?.length || 0,
          incompleteRules: (r.incomplete||[]).map(x => ({
            id: x.id, help: x.helpUrl, impact: x.impact, count: x.nodes?.length || 0,
            sample: (x.nodes||[]).slice(0, 2).map(n => ({ target: n.target?.[0] || '' })),
          })),
        };
      }
    } catch (e) {
      axeRes = { error: String(e && e.message || e) };
    }
  } else {
    axeRes = { error: 'axe.min.js not found at ' + AXE_LOCAL };
  }

  window.close();
  return { app: app.id, appName: app.name, axe: axeRes };
}

// 静态 color-contrast 校验: 基于 CSS 变量直接计算前景/背景对比度
// mode: 'light' (默认浅色模式) 或 'dark' (深色模式)
function verifyContrastStatic(mode = 'light') {
  const cssPath = path.join(ROOT, 'shared/design-system.css');
  const vars = loadCssVars(cssPath, mode);
  const bgWhite = mode === 'dark' ? vars['--c-bg-dark'] || '#070B13' : '#FFFFFF';

  // 通用校验用例 (浅色 / 深色共用同一组变量名组合)
  const cases = [
    { name: '辅助文字 --c-text-3 vs 主背景 --c-bg',           fg: vars['--c-text-3'],      bg: vars['--c-bg'] },
    { name: '辅助文字 --c-text-3 vs 卡片 --c-bg-card',         fg: vars['--c-text-3'],      bg: vars['--c-bg-card'] },
    { name: '鎏金深 --c-accent-deep vs 纯白/纯黑',              fg: vars['--c-accent-deep'], bg: bgWhite },
    { name: '鎏金深 --c-accent-deep vs 主背景 --c-bg',         fg: vars['--c-accent-deep'], bg: vars['--c-bg'] },
    { name: '可用池 --c-available vs 纯白/纯黑',                fg: vars['--c-available'],   bg: bgWhite },
    { name: '可用池 --c-available vs 主背景 --c-bg',           fg: vars['--c-available'],   bg: vars['--c-bg'] },
    { name: '成功色 --c-success vs 纯白/纯黑',                  fg: vars['--c-success'],     bg: bgWhite },
    { name: '主文字 --c-text-1 vs 主背景 --c-bg',              fg: vars['--c-text-1'],     bg: vars['--c-bg'] },
    { name: '次文字 --c-text-2 vs 主背景 --c-bg',              fg: vars['--c-text-2'],     bg: vars['--c-bg'] },
    // 深色模式额外重要的令牌组合
    ...(mode === 'dark' ? [
      { name: '主色系 --c-primary vs 主背景 --c-bg',           fg: vars['--c-primary'],     bg: vars['--c-bg'] },
      { name: '信息蓝 --c-info vs 主背景 --c-bg',               fg: vars['--c-info'],        bg: vars['--c-bg'] },
      { name: '警告色 --c-warning vs 主背景 --c-bg',            fg: vars['--c-warning'],     bg: vars['--c-bg'] },
      { name: '危险色 --c-danger vs 主背景 --c-bg',             fg: vars['--c-danger'],      bg: vars['--c-bg'] },
      { name: '锁定池 --c-locked vs 主背景 --c-bg',             fg: vars['--c-locked'],      bg: vars['--c-bg'] },
    ] : []),
  ];
  return cases.map(c => {
    const r = contrast(c.fg, c.bg);
    return { ...c, fg: c.fg, bg: c.bg, ratio: r ? Number(r.toFixed(2)) : null, pass: r != null && r >= 4.5 };
  });
}

async function main() {
  console.log('[a11y-static] 启动 jsdom + axe 静态审计...');
  const results = [];
  for (const app of APPS) {
    process.stdout.write(`  ${app.id.padEnd(10)} → `);
    const r = await auditOne(app);
    const v = (r.axe && r.axe.violations) ? r.axe.violations.length : '-';
    const pass = (r.axe && r.axe.passes) || 0;
    console.log(`violations=${v}  passes=${pass}`);
    results.push(r);
  }

  // 浅色模式对比度
  const contrastLight = verifyContrastStatic('light');
  let clPass = 0, clFail = 0;
  for (const c of contrastLight) { if (c.pass) clPass++; else clFail++; }

  // 深色模式对比度 (新增)
  const contrastDark = verifyContrastStatic('dark');
  let cdPass = 0, cdFail = 0;
  for (const c of contrastDark) { if (c.pass) cdPass++; else cdFail++; }

  // 汇总
  let totalV = 0, totalP = 0, totalI = 0;
  const byId = {};
  for (const r of results) {
    if (!r.axe || !r.axe.violations) continue;
    for (const v of r.axe.violations) {
      byId[v.id] = (byId[v.id] || 0) + v.count;
      totalV += v.count;
    }
    if (r.axe.passes) totalP += r.axe.passes;
    if (r.axe.incomplete) totalI += r.axe.incomplete;
  }

  console.log('\n=== 静态审计汇总 ===');
  console.log('  违规节点总数:', totalV, '  通过规则:', totalP, '  待核查:', totalI);
  console.log('  按规则分布:', JSON.stringify(byId));
  console.log('  对比度校验(浅色): PASS=' + clPass + ' FAIL=' + clFail);
  if (clFail > 0) {
    console.log('  浅色不达标项:');
    for (const c of contrastLight) if (!c.pass) console.log(`    - ${c.name}: ${c.fg} on ${c.bg} → ${c.ratio}:1`);
  }
  console.log('  对比度校验(深色): PASS=' + cdPass + ' FAIL=' + cdFail);
  if (cdFail > 0) {
    console.log('  深色不达标项:');
    for (const c of contrastDark) if (!c.pass) console.log(`    - ${c.name}: ${c.fg} on ${c.bg} → ${c.ratio}:1`);
  }

  const out = {
    timestamp: new Date().toISOString(),
    method: 'jsdom + axe-core static (light + dark)',
    apps: results,
    contrastLight,
    contrastDark,
    summary: {
      totalViolations: totalV,
      totalPasses: totalP,
      byRule: byId,
      contrastLightPass: clPass,
      contrastLightFail: clFail,
      contrastDarkPass: cdPass,
      contrastDarkFail: cdFail,
    },
  };
  fs.writeFileSync(JSON_OUT, JSON.stringify(out, null, 2));
  console.log('\n[a11y-static] 完成 → ' + JSON_OUT);
}

main().catch(e => { console.error('FATAL', e); process.exit(1); });
