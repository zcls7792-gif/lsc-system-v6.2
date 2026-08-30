#!/usr/bin/env node
/**
 * 屏幕阅读器兼容性审计 + 人工测试清单生成器
 *
 * 功能:
 *   1. 自动化 DOM 级 ARIA 标记审计 (JSDOM + VM)
 *   2. JS 源码动态内容扫描 (template string 中的 aria 属性)
 *   3. 生成 JSON + Markdown 审计报告
 *   4. 生成 NVDA / VoiceOver 人工测试清单
 *
 * 用法:
 *   node audit-screenreader.js               # 审计 + 生成报告
 *   node audit-screenreader.js --strict       # CI 严格模式 (有 FAIL 时 exit 1)
 *   node audit-screenreader.js --checklist   # 仅生成人工测试清单
 *
 * 输出:
 *   audit-report/screenreader-audit.json      # 结构化审计数据
 *   audit-report/screenreader-audit.md        # 可读审计报告
 *   audit-report/screenreader-checklist.md    # NVDA/VoiceOver 人工测试清单
 */
'use strict';

const http = require('http');
const path = require('path');
const fs   = require('fs');
const vm   = require('vm');
const { JSDOM, VirtualConsole } = require('jsdom');

const ROOT = __dirname;
const PORT = 18910;
const REPORT_DIR = path.join(ROOT, 'audit-report');

const APPS = [
  { id: 'platform-admin', name: '平台管理后台',  url: '/platform-admin/index.html',
    scripts: ['shared/app-utils.js', 'platform-admin/app.js'],
    sr: 'NVDA (Windows)', srKeys: 'Ins+B (朗读全文), Ins+Tab (下一个焦点), Ins+F7 (标题列表), Esc (关闭弹窗)' },
  { id: 'merchant-admin', name: '商家管理后台',  url: '/merchant-admin/index.html',
    scripts: ['shared/app-utils.js', 'merchant-admin/app.js'],
    sr: 'NVDA (Windows)', srKeys: 'Ins+B (朗读全文), Ins+Tab (下一个焦点), Ins+F7 (标题列表), Esc (关闭弹窗)' },
  { id: 'mobile-app',    name: '消费者移动端APP', url: '/mobile-app/index.html',
    scripts: ['shared/app-utils.js', 'mobile-app/app.js'],
    sr: 'VoiceOver (iOS) / TalkBack (Android)', srKeys: 'VO+右 (下一个元素), 双指上滑 (朗读全文), 双指点击 (暂停), 双指Z (关闭)' },
  { id: 'mini-program',  name: '微信小程序端',   url: '/mini-program/index.html',
    scripts: ['shared/app-utils.js', 'mini-program/app.js'],
    sr: 'VoiceOver (iOS) / TalkBack (Android)', srKeys: 'VO+右 (下一个元素), 双指上滑 (朗读全文), 双指点击 (暂停), 双指Z (关闭)' },
];

/* ============== 静态服务器 (复用 coverage_runner 模式) ============== */
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

/* ============== JSDOM 会话构建 (复用 coverage_runner 模式) ============== */
async function buildSession(srv, app) {
  const vc = new VirtualConsole();
  const errors = [];
  vc.on('error', () => {});
  vc.on('warn', () => {});
  vc.on('jsdomError', () => {});

  const dom = await JSDOM.fromURL(`http://127.0.0.1:${PORT}${app.url}`, {
    runScripts: 'outside-only',
    resources: 'usable',
    pretendToBeVisual: true,
    virtualConsole: vc,
  });
  const ctx = dom.window;

  // 执行外部 JS
  for (const rel of app.scripts) {
    const abs = path.join(ROOT, rel);
    const src = fs.readFileSync(abs, 'utf8');
    const script = new vm.Script(src + `\n//# sourceURL=file://${abs}`, { filename: abs, displayErrors: true });
    script.runInContext(ctx);
  }
  // 暴露全局常量
  const exposeHelper = new vm.Script(
    `if (typeof LSC !== 'undefined')   { globalThis.LSC   = LSC;   }
     if (typeof MOCK !== 'undefined')  { globalThis.MOCK  = MOCK;  }
     if (typeof ICONS !== 'undefined') { globalThis.ICONS = ICONS; }`,
    { filename: path.join(ROOT, '__sr_expose__.js'), displayErrors: true }
  );
  exposeHelper.runInContext(ctx);

  // 执行 HTML 内联 script (themeToggle IIFE 等)
  try {
    const doc = dom.window.document;
    const inlineScripts = Array.from(doc.querySelectorAll('script:not([src])'));
    for (let i = 0; i < inlineScripts.length; i++) {
      const inlineSrc = inlineScripts[i].textContent || '';
      if (!inlineSrc.trim()) continue;
      const type = inlineScripts[i].getAttribute('type');
      if (type && type !== 'text/javascript' && type !== 'module' && type !== '') continue;
      const abs = path.join(ROOT, app.id, `__sr_inline_${i}__.js`);
      const s = new vm.Script(inlineSrc + `\n//# sourceURL=file://${abs}`, { filename: abs, displayErrors: true });
      s.runInContext(ctx);
    }
    doc.dispatchEvent(new dom.window.Event('DOMContentLoaded', { bubbles: true }));
  } catch (_) { /* 内联脚本错误不影响静态 DOM 审计 */ }

  return { dom, errors, app };
}

function cleanupSession(sess) {
  try { if (sess.dom.window._aiTimers) sess.dom.window._aiTimers.forEach(t => clearInterval(t)); } catch(_) {}
  try { sess.dom.window.close(); } catch(_) {}
}

/* ============== 无障碍名称计算 (简化版 WAI-ARIA 1.2) ============== */
function getAccessibleName(el, doc) {
  // 1. aria-labelledby (可多引用空格分隔)
  const labelledby = el.getAttribute('aria-labelledby');
  if (labelledby) {
    const refs = labelledby.trim().split(/\s+/).map(id => doc.getElementById(id)).filter(Boolean);
    const text = refs.map(r => (r.textContent || '').trim()).join(' ');
    if (text) return text;
  }
  // 2. aria-label
  const ariaLabel = el.getAttribute('aria-label');
  if (ariaLabel && ariaLabel.trim()) return ariaLabel.trim();
  // 3. alt (img/area)
  const alt = el.getAttribute('alt');
  if (alt !== null) return alt;
  // 4. title (作为兜底)
  const title = el.getAttribute('title');
  if (title && title.trim()) return title.trim();
  // 5. textContent (button, a, span 等)
  const text = (el.textContent || '').trim().replace(/\s+/g, ' ');
  if (text) return text;
  // 6. input: 关联 label[for]
  if (el.tagName === 'INPUT' || el.tagName === 'SELECT' || el.tagName === 'TEXTAREA') {
    const id = el.id;
    if (id) {
      const label = doc.querySelector(`label[for="${CSS.escape(id)}"]`);
      if (label) return (label.textContent || '').trim();
    }
    // 7. 包裹式 label
    const parentLabel = el.closest('label');
    if (parentLabel) return (parentLabel.textContent || '').trim();
    // 8. placeholder 作为最后兜底
    const placeholder = el.getAttribute('placeholder');
    if (placeholder && placeholder.trim()) return placeholder.trim();
  }
  return '';
}

/* ============== 检查定义 ============== */
// 每个检查: { id, sev ('R'=必须 / '·'=建议), label, run(doc, app, jsSrcs) → { pass, detail, items? } }

const CHECKS = [

  /* --- 文档级 --- */
  {
    id: 'doc-lang', sev: 'R', label: '<html lang> 属性存在且有效',
    run(doc) {
      const lang = doc.documentElement.getAttribute('lang');
      if (!lang) return { pass: false, detail: '缺失 lang 属性' };
      if (!/^zh/i.test(lang)) return { pass: false, detail: `lang="${lang}" 非中文` };
      return { pass: true, detail: `lang="${lang}"` };
    }
  },
  {
    id: 'doc-title', sev: 'R', label: '<title> 存在且长度合规 (8~60字符)',
    run(doc) {
      const title = doc.querySelector('title');
      const t = title ? (title.textContent || '').trim() : '';
      if (!t) return { pass: false, detail: '缺失 <title>' };
      if (t.length < 8) return { pass: false, detail: `标题过短: "${t}" (${t.length}字符)` };
      if (t.length > 60) return { pass: false, detail: `标题过长: "${t}" (${t.length}字符)` };
      return { pass: true, detail: `"${t}" (${t.length}字符)` };
    }
  },

  /* --- 跳过链接 --- */
  {
    id: 'skip-links', sev: 'R', label: '跳过链接 (skip-link) 存在且目标有效',
    run(doc) {
      const links = Array.from(doc.querySelectorAll('a.skip-link'));
      if (!links.length) return { pass: false, detail: '未找到 skip-link' };
      const items = [];
      let allValid = true;
      for (const link of links) {
        const href = link.getAttribute('href') || '';
        const label = (link.getAttribute('aria-label') || link.textContent || '').trim();
        if (!href.startsWith('#')) { items.push(`✗ href="${href}" 非锚点`); allValid = false; continue; }
        const target = href.slice(1);
        const el = target ? doc.getElementById(target) : null;
        if (!el) { items.push(`✗ href="${href}" → 目标 #${target} 不存在`); allValid = false; }
        else { items.push(`✓ "${label}" → #${target}`); }
      }
      return { pass: allValid, detail: items.join('; '), items };
    }
  },

  /* --- 地标 --- */
  {
    id: 'landmarks', sev: 'R', label: '关键地标 (navigation + main) 齐全',
    run(doc) {
      const missing = [];
      // navigation: <nav> 或 role="navigation"
      const nav = doc.querySelector('nav, [role="navigation"]');
      if (!nav) missing.push('navigation');
      // main: <main> 或 role="main" 或 .content[role="region"]
      const main = doc.querySelector('main, [role="main"], .content[role="region"]');
      if (!main) missing.push('main/content');
      if (missing.length) return { pass: false, detail: `缺失: ${missing.join(', ')}` };
      return { pass: true, detail: 'navigation + main 齐全' };
    }
  },
  {
    id: 'landmarks-banner', sev: '·', label: 'banner 地标 (header/role=banner) 存在',
    run(doc) {
      const banner = doc.querySelector('[role="banner"], header[aria-label], header[role="group"][aria-label*="顶"]');
      if (!banner) return { pass: false, detail: '缺失 banner/header (移动端可接受)' };
      return { pass: true, detail: 'banner 存在' };
    }
  },

  /* --- 标题层级 --- */
  {
    id: 'heading-order', sev: 'R', label: '标题层级无跳级 (h1→h2, 禁 h1→h3)',
    run(doc) {
      const headings = Array.from(doc.querySelectorAll('h1,h2,h3,h4,h5,h6'));
      if (!headings.length) return { pass: true, detail: '无标题元素 (移动端常见)' };
      let prevLevel = 0;
      const items = [];
      let ok = true;
      for (const h of headings) {
        const level = parseInt(h.tagName[1]);
        const text = (h.textContent || '').trim().slice(0, 30);
        if (prevLevel > 0 && level > prevLevel + 1) {
          items.push(`✗ h${prevLevel}→h${level} 跳级: "${text}"`);
          ok = false;
        } else {
          items.push(`✓ h${level}: "${text}"`);
        }
        prevLevel = level;
      }
      return { pass: ok, detail: items.join('; '), items };
    }
  },

  /* --- 按钮无障碍名称 --- */
  {
    id: 'button-names', sev: 'R', label: '所有 <button> 有无障碍名称',
    run(doc) {
      const buttons = Array.from(doc.querySelectorAll('button'));
      if (!buttons.length) return { pass: true, detail: '无 button 元素' };
      const missing = [];
      for (const btn of buttons) {
        const name = getAccessibleName(btn, doc);
        if (!name) {
          const id = btn.id ? `#${btn.id}` : '';
          const cls = btn.className ? `.${btn.className.split(' ')[0]}` : '';
          missing.push(`${id}${cls || '(无名)'}`);
        }
      }
      if (missing.length) return { pass: false, detail: `${missing.length}/${buttons.length} 个按钮缺失名称: ${missing.slice(0,5).join(', ')}${missing.length>5?'...':''}` };
      return { pass: true, detail: `${buttons.length} 个按钮全部有名称` };
    }
  },

  /* --- 链接无障碍名称 --- */
  {
    id: 'link-names', sev: 'R', label: '所有 <a href> 有无障碍名称',
    run(doc) {
      const links = Array.from(doc.querySelectorAll('a[href]'));
      if (!links.length) return { pass: true, detail: '无链接' };
      const missing = [];
      for (const a of links) {
        const name = getAccessibleName(a, doc);
        if (!name) missing.push(a.getAttribute('href'));
      }
      if (missing.length) return { pass: false, detail: `${missing.length}/${links.length} 个链接缺失名称: ${missing.slice(0,5).join(', ')}` };
      return { pass: true, detail: `${links.length} 个链接全部有名称` };
    }
  },

  /* --- 表单标签 --- */
  {
    id: 'input-labels', sev: 'R', label: '所有 <input>/<select>/<textarea> 有关联标签',
    run(doc) {
      const inputs = Array.from(doc.querySelectorAll('input, select, textarea'));
      if (!inputs.length) return { pass: true, detail: '无表单元素' };
      const missing = [];
      for (const inp of inputs) {
        // type=hidden 豁免
        if (inp.type === 'hidden') continue;
        const name = getAccessibleName(inp, doc);
        if (!name) {
          const id = inp.id ? `#${inp.id}` : '';
          const type = inp.type || inp.tagName.toLowerCase();
          missing.push(`${id}(${type})`);
        }
      }
      if (missing.length) return { pass: false, detail: `${missing.length}/${inputs.length} 个表单元素缺失标签: ${missing.slice(0,5).join(', ')}` };
      return { pass: true, detail: `${inputs.length} 个表单元素全部有标签` };
    }
  },

  /* --- 图片 alt --- */
  {
    id: 'img-alt', sev: 'R', label: '所有 <img> 有 alt 属性',
    run(doc) {
      const imgs = Array.from(doc.querySelectorAll('img'));
      if (!imgs.length) return { pass: true, detail: '无 img 元素' };
      const missing = [];
      for (const img of imgs) {
        if (!img.hasAttribute('alt')) missing.push(img.src ? img.src.split('/').pop() : '(无名)');
      }
      if (missing.length) return { pass: false, detail: `${missing.length}/${imgs.length} 个图片缺失 alt: ${missing.slice(0,5).join(', ')}` };
      return { pass: true, detail: `${imgs.length} 个图片全部有 alt` };
    }
  },

  /* --- 装饰性 SVG --- */
  {
    id: 'svg-hidden', sev: '·', label: '装饰性 <svg> 标记 aria-hidden="true"',
    run(doc) {
      const svgs = Array.from(doc.querySelectorAll('svg'));
      if (!svgs.length) return { pass: true, detail: '无 svg 元素' };
      const missing = [];
      for (const svg of svgs) {
        // 如果 svg 有 title 或 role="img" 则视为有意义的
        if (svg.querySelector('title') || svg.getAttribute('role') === 'img') continue;
        if (svg.getAttribute('aria-hidden') !== 'true') missing.push(svg.getAttribute('class') || '(无类名)');
      }
      if (missing.length) return { pass: false, detail: `${missing.length}/${svgs.length} 个装饰性 svg 缺失 aria-hidden: ${missing.slice(0,5).join(', ')}` };
      return { pass: true, detail: `${svgs.length} 个 svg 全部正确标记` };
    }
  },

  /* --- 弹窗 ARIA --- */
  {
    id: 'dialog-aria', sev: 'R', label: '弹窗有 role="dialog" + aria-modal="true" + 可访问名称',
    run(doc) {
      // 静态 HTML 中的 dialog
      const dialogs = Array.from(doc.querySelectorAll('[role="dialog"], [role="alertdialog"]'));
      if (!dialogs.length) return { pass: true, detail: '无静态 dialog (动态生成见 JS 扫描)' };
      const items = [];
      let ok = true;
      for (const d of dialogs) {
        const role = d.getAttribute('role');
        const modal = d.getAttribute('aria-modal');
        const name = getAccessibleName(d, doc);
        const id = d.id ? `#${d.id}` : '';
        if (modal !== 'true') { items.push(`✗ ${id} aria-modal≠true`); ok = false; }
        else if (!name) { items.push(`✗ ${id} 缺失可访问名称`); ok = false; }
        else { items.push(`✓ ${id} "${name.slice(0,20)}"`); }
      }
      return { pass: ok, detail: items.join('; '), items };
    }
  },

  /* --- aria-hidden 不可聚焦 --- */
  {
    id: 'aria-hidden-focus', sev: 'R', label: 'aria-hidden="true" 元素不含可聚焦子元素 (inert 豁免)',
    run(doc) {
      const hidden = Array.from(doc.querySelectorAll('[aria-hidden="true"]'));
      if (!hidden.length) return { pass: true, detail: '无 aria-hidden 元素' };
      const bad = [];
      for (const el of hidden) {
        // inert 属性豁免: 设置了 inert 的元素及其子元素不可聚焦
        if (el.hasAttribute('inert')) continue;
        // 检查祖先链是否有 inert
        let ancestor = el.parentElement;
        let hasInertAncestor = false;
        while (ancestor) {
          if (ancestor.hasAttribute && ancestor.hasAttribute('inert')) { hasInertAncestor = true; break; }
          ancestor = ancestor.parentElement;
        }
        if (hasInertAncestor) continue;
        const focusable = el.querySelectorAll('button, a[href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
        if (focusable.length) {
          const tag = el.id ? `#${el.id}` : `.${(el.className||'').split(' ')[0]}` || el.tagName;
          bad.push(`${tag} 含 ${focusable.length} 个可聚焦子元素`);
        }
      }
      if (bad.length) return { pass: false, detail: bad.join('; ') };
      return { pass: true, detail: `${hidden.length} 个 aria-hidden 元素无焦点泄漏` };
    }
  },

  /* --- aria-current --- */
  {
    id: 'aria-current', sev: 'R', label: '当前激活导航项标记 aria-current',
    run(doc) {
      const activeNav = doc.querySelector('.nav-item.active, .tab-item.active, .wx-tab.active');
      if (!activeNav) return { pass: true, detail: '无激活导航项' };
      const current = activeNav.getAttribute('aria-current');
      if (!current) return { pass: false, detail: '激活导航项缺失 aria-current' };
      return { pass: true, detail: `aria-current="${current}"` };
    }
  },

  /* --- tabindex 正数检查 --- */
  {
    id: 'tabindex-positive', sev: 'R', label: '无 tabindex 正数 (避免破坏 Tab 顺序)',
    run(doc) {
      const positives = Array.from(doc.querySelectorAll('[tabindex]')).filter(el => {
        const v = parseInt(el.getAttribute('tabindex'));
        return v > 0;
      });
      if (positives.length) return { pass: false, detail: `${positives.length} 个元素 tabindex>0: ${positives.slice(0,5).map(e=>e.id||e.tagName).join(', ')}` };
      return { pass: true, detail: '无正数 tabindex' };
    }
  },

  /* --- 实时区域 (DOM 级) --- */
  {
    id: 'live-regions-dom', sev: 'R', label: '动态状态文本元素有 aria-live 或 role=status/alert',
    run(doc) {
      // 扫描 ID 含 status/notif/result/msg/error/warn 的元素
      // 排除: button (按钮不是状态区), aria-hidden=true (装饰性), input/textarea (表单)
      const candidates = Array.from(doc.querySelectorAll('[id]')).filter(el => {
        const id = (el.id || '').toLowerCase();
        if (!/status|notif|result|msg|error|warn|toast|tip|hint/.test(id)) return false;
        if (el.tagName === 'BUTTON' || el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') return false;
        if (el.getAttribute('aria-hidden') === 'true') return false;
        // 排除 toggle 按钮 (id 含 toggle 但只是开关, 不是状态显示)
        if (/toggle|btn|button/.test(id)) return false;
        return true;
      });
      if (!candidates.length) return { pass: true, detail: '无动态状态元素 (DOM 级)' };
      const items = [];
      let ok = true;
      for (const el of candidates) {
        const hasLive = el.getAttribute('aria-live');
        const hasRole = el.getAttribute('role');
        const isLiveRole = hasRole === 'status' || hasRole === 'alert';
        if (!hasLive && !isLiveRole) {
          // 检查祖先链是否已有 aria-live 或 role=status/alert (继承式 live region)
          let ancestor = el.parentElement;
          let inherited = false;
          while (ancestor && !inherited) {
            if (ancestor.getAttribute && (ancestor.getAttribute('aria-live') || ancestor.getAttribute('role') === 'status' || ancestor.getAttribute('role') === 'alert')) {
              inherited = true;
            }
            ancestor = ancestor.parentElement;
          }
          if (inherited) {
            items.push(`○ #${el.id} (继承祖先 live region)`);
          } else {
            items.push(`✗ #${el.id} 缺失 aria-live/role=status`);
            ok = false;
          }
        } else {
          items.push(`✓ #${el.id} (${hasLive || hasRole})`);
        }
      }
      return { pass: ok, detail: items.join('; '), items };
    }
  },

  /* --- 实时区域 (JS 源码级) --- */
  {
    id: 'live-regions-js', sev: 'R', label: 'JS 动态生成状态文本含 aria-live 或 role=status',
    run(doc, app, jsSrcs) {
      const src = jsSrcs.join('\n');
      // 匹配模板字符串中 id="...status..." 或 id="...notif..." 等
      const idPattern = /id=["']([^"']*(?:status|notif|result|msg|error|warn|toast|tip|hint)[^"']*)["']/gi;
      const matches = [...src.matchAll(idPattern)];
      if (!matches.length) return { pass: true, detail: 'JS 中无动态状态文本元素' };
      const items = [];
      let ok = true;
      for (const m of matches) {
        const fullId = m[1];
        // 检查是否有 .textContent = 或 .innerHTML = 赋值 (动态文本更新)
        // 仅当文本内容被动态修改时才需要 aria-live
        const dynTextPattern = new RegExp(
          `getElementById\\(['"]${fullId.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}['"]\\)\\s*\\.\\s*(textContent|innerHTML)\\s*=`,
          'i'
        );
        if (!dynTextPattern.test(src)) {
          // 文本内容未动态更新，跳过 (如仅 class 变化的步骤指示器)
          continue;
        }
        // 检查同一段模板字符串中是否有 aria-live 或 role="status"/"alert"
        const start = m.index;
        const context = src.slice(Math.max(0, start - 200), start + 300);
        const hasLive = /aria-live/.test(context);
        const hasRole = /role=["'](status|alert)["']/.test(context);
        if (!hasLive && !hasRole) {
          items.push(`✗ id="${fullId}" 缺失 aria-live`);
          ok = false;
        } else {
          items.push(`✓ id="${fullId}"`);
        }
      }
      if (!items.length) return { pass: true, detail: 'JS 中无动态文本更新元素' };
      return { pass: ok, detail: items.join('; '), items };
    }
  },

  /* --- JS 源码: 弹窗 aria 完整性 --- */
  {
    id: 'dialog-aria-js', sev: 'R', label: 'JS 动态生成弹窗含 role=dialog + aria-modal + 可访问名称',
    run(doc, app, jsSrcs) {
      const src = jsSrcs.join('\n');
      // 查找 role="dialog" 的模板
      const dialogPattern = /role=["']dialog["']/gi;
      const matches = [...src.matchAll(dialogPattern)];
      if (!matches.length) return { pass: true, detail: 'JS 中无动态 dialog' };
      const items = [];
      let ok = true;
      for (const m of matches) {
        const start = m.index;
        const context = src.slice(Math.max(0, start - 100), start + 400);
        const hasModal = /aria-modal=["']true["']/.test(context);
        const hasLabel = /aria-label[s]?[=]/.test(context) || /aria-labelledby[=]/.test(context);
        if (!hasModal) { items.push('✗ 动态 dialog 缺失 aria-modal'); ok = false; }
        else if (!hasLabel) { items.push('✗ 动态 dialog 缺失 aria-label/labelledby'); ok = false; }
        else { items.push('✓ 动态 dialog aria 完整'); }
      }
      return { pass: ok, detail: items.join('; '), items };
    }
  },

  /* --- 主题切换按钮可访问性 --- */
  {
    id: 'theme-toggle-a11y', sev: '·', label: '主题切换按钮有 aria-label + data-state',
    run(doc) {
      const toggle = doc.getElementById('themeToggle') || doc.querySelector('.theme-toggle');
      if (!toggle) return { pass: false, detail: '未找到 themeToggle 按钮' };
      const items = [];
      let ok = true;
      const label = toggle.getAttribute('aria-label');
      if (!label) { items.push('✗ 缺失 aria-label'); ok = false; }
      else { items.push(`✓ aria-label="${label}"`); }
      // 检查 SVG 是否有 aria-hidden
      const svgs = toggle.querySelectorAll('svg');
      const unhidden = Array.from(svgs).filter(s => s.getAttribute('aria-hidden') !== 'true');
      if (unhidden.length) { items.push(`△ ${unhidden.length} 个 svg 未标记 aria-hidden`); }
      return { pass: ok, detail: items.join('; '), items };
    }
  },

  /* --- 导航项键盘可操作 --- */
  {
    id: 'nav-keyboard', sev: 'R', label: '导航项支持键盘 (tabindex/role=button)',
    run(doc) {
      const navItems = Array.from(doc.querySelectorAll('.nav-item, .tab-item, .wx-tab'));
      if (!navItems.length) return { pass: true, detail: '无导航项' };
      const missing = [];
      for (const item of navItems) {
        const hasTabindex = item.hasAttribute('tabindex');
        const isButton = item.tagName === 'BUTTON' || item.getAttribute('role') === 'button';
        if (!hasTabindex && !isButton) {
          missing.push(item.getAttribute('data-view') || item.getAttribute('data-screen') || '(无名)');
        }
      }
      if (missing.length) return { pass: false, detail: `${missing.length}/${navItems.length} 个导航项不可键盘聚焦: ${missing.slice(0,5).join(', ')}` };
      return { pass: true, detail: `${navItems.length} 个导航项均可键盘操作` };
    }
  },

  /* --- sr-only 隐藏文本存在性 --- */
  {
    id: 'sr-only-text', sev: '·', label: '使用 .sr-only 为视觉隐藏文本提供屏幕阅读器内容',
    run(doc) {
      const srOnly = doc.querySelectorAll('.sr-only');
      if (!srOnly.length) return { pass: false, detail: '未使用 .sr-only 隐藏文本 (badge 等信息缺失朗读)' };
      const texts = Array.from(srOnly).map(el => (el.textContent || '').trim()).filter(Boolean).slice(0, 5);
      return { pass: true, detail: `${srOnly.length} 处 .sr-only: ${texts.join('; ')}` };
    }
  },

  /* --- color-scheme meta --- */
  {
    id: 'color-scheme', sev: '·', label: '<meta name="color-scheme"> 支持浅/深色',
    run(doc) {
      const meta = doc.querySelector('meta[name="color-scheme"]');
      if (!meta) return { pass: false, detail: '缺失 color-scheme meta' };
      const content = meta.getAttribute('content') || '';
      if (!content.includes('light') || !content.includes('dark')) return { pass: false, detail: `content="${content}" 未含 light+dark` };
      return { pass: true, detail: `content="${content}"` };
    }
  },

];

/* ============== 人工测试清单 (NVDA / VoiceOver) ============== */
const HUMAN_CHECKS = [
  { id: 'SR-01', label: '页面加载朗读', instr: '打开页面后，屏幕阅读器应自动朗读页面标题和主要地标。验证是否朗读了"链盛通LSC"标题及"导航""主内容"等地标。', expected: '朗读页面标题 + landmark 角色' },
  { id: 'SR-02', label: '跳过链接', instr: '页面加载后按 Tab，第一个焦点应为"跳到主内容区"链接。按 Enter 激活，焦点应跳转到主内容区。', expected: '首个 Tab 焦点 = skip-link → Enter → 焦点到 #view/#content' },
  { id: 'SR-03', label: '导航地标朗读', instr: '使用屏幕阅读器地标导航 (NVDA: Ins+D / VO: VO+U 选 landmark)，应列出"导航""主内容""顶部操作栏"等地标。', expected: '地标列表包含 navigation + main/region' },
  { id: 'SR-04', label: '导航项朗读', instr: 'Tab 遍历每个导航项，验证朗读名称+角色。当前激活项应朗读"当前页面"。带 badge 的项应朗读待处理数量（通过 sr-only）。', expected: '每个 nav-item 朗读名称 + "按钮"；激活项朗读"当前页面"；badge 朗读数量' },
  { id: 'SR-05', label: '搜索框', instr: 'Tab 到搜索框，验证朗读关联标签（如"搜索商家、订单或用户 ID"）+ "编辑框"。', expected: '朗读 aria-label 文本 + "编辑框"' },
  { id: 'SR-06', label: '主题切换按钮', instr: 'Tab 到主题切换按钮，验证朗读"切换主题 按钮"。点击切换后状态变化应可被感知（data-state 属性变化）。', expected: '朗读"切换主题 按钮"；三态切换后 data-state 更新' },
  { id: 'SR-07', label: '弹窗打开', instr: '触发弹窗（如点击商家详情/处罚操作），验证：(a) 朗读弹窗标题 (b) 焦点进入弹窗 (c) Tab 键在弹窗内循环。', expected: '弹窗打开时朗读标题 + 焦点进入 + Tab 循环' },
  { id: 'SR-08', label: '弹窗关闭', instr: '按 Esc 或点击关闭按钮关闭弹窗，验证：(a) 弹窗消失 (b) 焦点返回触发弹窗的元素。', expected: 'Esc → 弹窗关闭 + 焦点返回触发按钮' },
  { id: 'SR-09', label: '双人审批弹窗', instr: '触发双人审批弹窗，验证：(a) 朗读"双人审批"标题 (b) 两个输入框朗读标签"第一管理员签名""第二管理员签名" (c) 输入后状态文本变化被朗读。', expected: '标题朗读 + 输入框标签 + 状态变化 live region 朗读' },
  { id: 'SR-10', label: '表单标签', instr: 'Tab 遍历所有表单输入框，验证每个输入框朗读关联的 label 文本。', expected: '每个 input 朗读 label + "编辑框"' },
  { id: 'SR-11', label: 'Tab 顺序', instr: '从页面顶部按 Tab 顺序遍历，验证焦点顺序符合视觉阅读顺序（从上到下、从左到右）。', expected: 'Tab 顺序 = 视觉顺序，无跳跃' },
  { id: 'SR-12', label: '图片/图标替代文本', instr: '使用屏幕阅读器浏览图片和图标区域，验证：装饰性图标不朗读（aria-hidden），有意义图片朗读 alt 文本。', expected: '装饰图标静默；信息图片朗读 alt' },
  { id: 'SR-13', label: '档位/信用分卡片', instr: '在移动端首页浏览商家卡片，验证：档位标签朗读"档位X"，信用分朗读"信用XX分，XX状态"。暂停/关闭的卡片朗读"核销权限受限"。', expected: '档位+信用分+状态均被朗读；禁用卡朗读受限提示' },
  { id: 'SR-14', label: '底部导航', instr: 'Tab 到底部 Tab Bar，验证：每个 Tab 朗读名称 + "按钮"，当前页朗读"当前页面"。', expected: 'Tab 项朗读名称 + 角色；激活项朗读"当前页面"' },
  { id: 'SR-15', label: '动态内容更新', instr: '触发动态内容更新（如通知面板展开、审批状态变化），验证屏幕阅读器是否朗读更新内容。', expected: '状态变化被朗读（需 aria-live 或 role=status）' },
];

/* ============== 报告生成 ============== */
function generateMarkdownReport(results) {
  const lines = [];
  lines.push('# 屏幕阅读器兼容性审计报告');
  lines.push('');
  lines.push(`> 生成时间: ${new Date().toISOString()}`);
  lines.push(`> 审计工具: audit-screenreader.js (JSDOM + VM)`);
  lines.push(`> 标准: WAI-ARIA 1.2 + WCAG 2.1 AA (屏幕阅读器兼容性)`);
  lines.push('');

  // 汇总
  let totalPass = 0, totalFail = 0, totalWarn = 0;
  for (const r of results) {
    for (const c of r.checks) {
      if (c.result.pass) totalPass++;
      else if (c.sev === 'R') totalFail++;
      else totalWarn++;
    }
  }
  lines.push('## 汇总');
  lines.push('');
  lines.push(`| 指标 | 数值 |`);
  lines.push(`|------|------|`);
  lines.push(`| 应用数 | ${results.length} |`);
  lines.push(`| 检查项/应用 | ${CHECKS.length} |`);
  lines.push(`| 总检查数 | ${results.length * CHECKS.length} |`);
  lines.push(`| ✅ PASS | ${totalPass} |`);
  lines.push(`| ❌ FAIL (必须) | ${totalFail} |`);
  lines.push(`| ⚠ WARN (建议) | ${totalWarn} |`);
  lines.push('');

  // 逐应用
  for (const r of results) {
    const fails = r.checks.filter(c => !c.result.pass && c.sev === 'R');
    const warns = r.checks.filter(c => !c.result.pass && c.sev === '·');
    const icon = fails.length ? '❌' : (warns.length ? '⚠' : '✅');
    lines.push(`## ${icon} ${r.appName} [${r.appId}]`);
    lines.push('');
    lines.push(`| 检查ID | 级别 | 检查项 | 结果 | 详情 |`);
    lines.push(`|--------|------|--------|------|------|`);
    for (const c of r.checks) {
      const status = c.result.pass ? '✅ PASS' : (c.sev === 'R' ? '❌ FAIL' : '⚠ WARN');
      const detail = (c.result.detail || '').replace(/\|/g, '\\|').slice(0, 120);
      lines.push(`| ${c.id} | ${c.sev} | ${c.label} | ${status} | ${detail} |`);
    }
    lines.push('');
  }

  lines.push('## 结论');
  lines.push('');
  if (totalFail === 0 && totalWarn === 0) {
    lines.push('✅ 所有 4 应用通过屏幕阅读器兼容性审计，无必须级违规。');
  } else if (totalFail === 0) {
    lines.push(`✅ 无必须级违规。⚠ ${totalWarn} 项建议级改进，不影响屏幕阅读器基本可用性。`);
  } else {
    lines.push(`❌ ${totalFail} 项必须级违规需修复，详见上方 FAIL 行。`);
  }
  lines.push('');
  lines.push('> 人工测试清单见: `audit-report/screenreader-checklist.md`');
  return lines.join('\n');
}

function generateChecklist(results) {
  const lines = [];
  lines.push('# 屏幕阅读器人工测试清单 (NVDA / VoiceOver)');
  lines.push('');
  lines.push(`> 生成时间: ${new Date().toISOString()}`);
  lines.push(`> 测试标准: WAI-ARIA 1.2 + WCAG 2.1 AA`);
  lines.push(`> 前置条件: 在真机/模拟器上部署 LSC 系统，启动屏幕阅读器`);
  lines.push('');

  for (const app of APPS) {
    lines.push(`## ${app.name} [${app.id}]`);
    lines.push('');
    lines.push(`- **URL**: \`http://localhost:${PORT}${app.url}\``);
    lines.push(`- **屏幕阅读器**: ${app.sr}`);
    lines.push(`- **快捷键**: ${app.srKeys}`);
    lines.push('');
    lines.push('| 编号 | 检查项 | 操作步骤 | 预期结果 | 实际结果 | 通过 |');
    lines.push('|------|--------|----------|----------|----------|------|');
    for (const hc of HUMAN_CHECKS) {
      const instr = hc.instr.replace(/\|/g, '\\|');
      const expected = hc.expected.replace(/\|/g, '\\|');
      lines.push(`| ${hc.id} | ${hc.label} | ${instr} | ${expected} | | ☐ |`);
    }
    lines.push('');
  }

  lines.push('## 测试结果汇总');
  lines.push('');
  lines.push('| 应用 | 通过/总数 | 测试人 | 日期 | 备注 |');
  lines.push('|------|-----------|--------|------|------|');
  for (const app of APPS) {
    lines.push(`| ${app.name} | /${HUMAN_CHECKS.length} | | | |`);
  }
  lines.push('');
  lines.push('## 已知限制');
  lines.push('');
  lines.push('- 自动化审计 (audit-screenreader.js) 覆盖 DOM 级 ARIA 标记，无法覆盖屏幕阅读器实际朗读行为');
  lines.push('- 人工测试需在真实硬件上使用 NVDA (Windows) 或 VoiceOver (iOS/macOS) 执行');
  lines.push('- TalkBack (Android) 可作为 VoiceOver 的替代方案测试移动端');
  lines.push('- 弹窗焦点管理 (焦点陷阱 + 焦点恢复) 需人工验证，自动化仅检查 ARIA 属性');
  return lines.join('\n');
}

/* ============== 主流程 ============== */
async function main() {
  const args = process.argv.slice(2);
  const strict = args.includes('--strict');
  const checklistOnly = args.includes('--checklist');

  // 确保报告目录
  if (!fs.existsSync(REPORT_DIR)) fs.mkdirSync(REPORT_DIR, { recursive: true });

  // 仅生成清单模式
  if (checklistOnly) {
    const md = generateChecklist([]);
    fs.writeFileSync(path.join(REPORT_DIR, 'screenreader-checklist.md'), md, 'utf8');
    console.log(`✅ 人工测试清单已生成: audit-report/screenreader-checklist.md`);
    return;
  }

  // 启动服务器
  const srv = await startStaticServer();
  console.log(`[sr] 静态服务器启动: http://127.0.0.1:${PORT}`);

  const results = [];
  let totalFail = 0;

  for (const app of APPS) {
    console.log(`\n[sr] 审计: ${app.name} [${app.id}]`);
    let sess;
    try {
      sess = await buildSession(srv, app);
    } catch (e) {
      console.log(`  ✗ JSDOM 会话失败: ${e.message}`);
      results.push({ appId: app.id, appName: app.name, checks: CHECKS.map(c => ({ ...c, result: { pass: false, detail: 'JSDOM 会话失败: ' + e.message } })) });
      totalFail += CHECKS.length;
      continue;
    }

    const doc = sess.dom.window.document;
    // 读取 JS 源码用于动态内容扫描
    const jsSrcs = app.scripts.map(rel => fs.readFileSync(path.join(ROOT, rel), 'utf8'));

    const checks = [];
    for (const check of CHECKS) {
      try {
        const result = check.run(doc, app, jsSrcs);
        checks.push({ id: check.id, sev: check.sev, label: check.label, result });
        const icon = result.pass ? '✓' : (check.sev === 'R' ? '✗' : '△');
        console.log(`  ${icon} [${check.sev}] ${check.id.padEnd(22)} ${result.pass ? 'PASS' : (check.sev === 'R' ? 'FAIL' : 'WARN')}  ${(result.detail || '').slice(0, 80)}`);
        if (!result.pass && check.sev === 'R') totalFail++;
      } catch (e) {
        checks.push({ id: check.id, sev: check.sev, label: check.label, result: { pass: false, detail: '检查异常: ' + e.message } });
        console.log(`  ✗ [${check.sev}] ${check.id.padEnd(22)} ERROR ${e.message}`);
        totalFail++;
      }
    }

    results.push({ appId: app.id, appName: app.name, checks });
    cleanupSession(sess);
  }

  // 关闭服务器
  srv.close();

  // 生成 JSON 报告
  const jsonData = {
    generatedAt: new Date().toISOString(),
    tool: 'audit-screenreader.js',
    standard: 'WAI-ARIA 1.2 + WCAG 2.1 AA',
    apps: results.length,
    checksPerApp: CHECKS.length,
    totalChecks: results.length * CHECKS.length,
    summary: {
      pass: results.reduce((s, r) => s + r.checks.filter(c => c.result.pass).length, 0),
      fail: results.reduce((s, r) => s + r.checks.filter(c => !c.result.pass && c.sev === 'R').length, 0),
      warn: results.reduce((s, r) => s + r.checks.filter(c => !c.result.pass && c.sev === '·').length, 0),
    },
    results,
  };
  fs.writeFileSync(path.join(REPORT_DIR, 'screenreader-audit.json'), JSON.stringify(jsonData, null, 2), 'utf8');

  // 生成 Markdown 报告
  const md = generateMarkdownReport(results);
  fs.writeFileSync(path.join(REPORT_DIR, 'screenreader-audit.md'), md, 'utf8');

  // 生成人工测试清单
  const checklistMd = generateChecklist(results);
  fs.writeFileSync(path.join(REPORT_DIR, 'screenreader-checklist.md'), checklistMd, 'utf8');

  // 汇总输出
  console.log('\n' + '='.repeat(60));
  console.log(`[sr] 审计完成: PASS=${jsonData.summary.pass}  FAIL=${jsonData.summary.fail}  WARN=${jsonData.summary.warn}`);
  console.log(`[sr] 报告: audit-report/screenreader-audit.json + .md`);
  console.log(`[sr] 清单: audit-report/screenreader-checklist.md`);

  if (strict && totalFail > 0) {
    console.log(`\n[sr][CI] 严格模式未通过 ❌ (${totalFail} 项必须级违规)`);
    process.exit(1);
  }
  console.log(`\n[sr][CI] ${totalFail === 0 ? '严格模式通过 ✅' : '有违规，非严格模式不阻断'}`);
}

main().catch(e => { console.error(e); process.exit(1); });
