#!/usr/bin/env node
/**
 * audit-meta.js — HTML/安全元数据深度扫描器
 * 覆盖:
 *   - 4 应用 HTML head meta charset/viewport/description/keywords/theme-color
 *   - OpenGraph (og:title/og:description/og:image/og:type)
 *   - Twitter Card
 *   - Content-Security-Policy / X-Content-Type-Options / Referrer meta
 *   - favicon / apple-touch-icon / theme-color
 *   - 语言 <html lang> / <title>
 *   - 结构化数据 (ld+json) - 可选
 *
 * 用法: node audit-meta.js [--strict] [--json <path>] [--md <path>]
 *        --strict : 缺失 meta 时输出 FAIL 并退出 1
 *
 * 退出码: 0=全部通过 (或 soft mode 允许缺失时 info), 1=FAIL (strict)
 */

'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname);
const APPS = [
  { key:'platform-admin', title:'平台管理后台',     html: path.join(ROOT,'platform-admin/index.html') },
  { key:'merchant-admin', title:'商家管理后台',     html: path.join(ROOT,'merchant-admin/index.html') },
  { key:'mobile-app',     title:'移动端消费者APP', html: path.join(ROOT,'mobile-app/index.html') },
  { key:'mini-program',   title:'微信小程序端',     html: path.join(ROOT,'mini-program/index.html') },
];

const STRICT = process.argv.includes('--strict') || process.env.META_AUDIT_STRICT === '1';
const JSON_OUT = (() => {
  const i = process.argv.indexOf('--json'); return i>=0 ? process.argv[i+1] : null;
})();
const MD_OUT = (() => {
  const i = process.argv.indexOf('--md'); return i>=0 ? process.argv[i+1] : null;
})();

// ===== 规则集 (name -> 规则) =====
const RULES = {
  // --- 必须满足 ---
  charset: {
    required: true,
    desc: '<meta charset="UTF-8"> (必须 HTML5)',
    check: (M, raw) => M.metas.some(m => m.attrs.charset && m.attrs.charset.toUpperCase() === 'UTF-8'),
  },
  viewport: {
    required: true,
    desc: '<meta name="viewport"> 必须含 width=device-width 且 initial-scale',
    check: (M) => {
      const v = M.metas.find(m => m.attrs.name === 'viewport')?.attrs.content || '';
      return /width\s*=\s*device-width/.test(v) && /initial-scale\s*=\s*1(\.0)?/.test(v);
    },
  },
  title: {
    required: true,
    desc: '<title> 长度 8~60 字符 (SEO/展示建议)',
    check: (M) => {
      const t = M.title;
      return typeof t === 'string' && t.length >= 8 && t.length <= 60;
    },
  },
  htmlLang: {
    required: true,
    desc: '<html lang="zh-CN"> 可访问性/本地化',
    check: (M) => typeof M.htmlLang === 'string' && M.htmlLang.length >= 2,
  },
  favicon: {
    required: true,
    desc: '<link rel="icon"> 防止 /favicon.ico 404',
    check: (M) => M.links.some(l => (l.attrs.rel || '').toLowerCase().split(/\s+/).includes('icon')),
  },
  // --- 推荐 (strict=WARN unless STRICT) ---
  description: {
    required: false,
    desc: '<meta name="description"> 长度 50~160 (SEO/OG fallback)',
    check: (M) => {
      const v = M.metas.find(m => m.attrs.name === 'description')?.attrs.content || '';
      return v.length >= 50 && v.length <= 160;
    },
  },
  keywords: {
    required: false,
    desc: '<meta name="keywords"> (兼容性)',
    check: (M) => {
      const v = M.metas.find(m => m.attrs.name === 'keywords')?.attrs.content || '';
      return v.length > 0 && v.split(',').length >= 3;
    },
  },
  themeColor: {
    required: false,
    desc: '<meta name="theme-color"> (移动端 UI 适配, 需 media=(prefers-color-scheme) 双色)',
    check: (M) => {
      const themeMetas = M.metas.filter(m => m.attrs.name === 'theme-color');
      if (themeMetas.length === 0) return false;
      // 至少 1 条带默认颜色；若有两条推荐 dark 适配
      return themeMetas.some(m => typeof m.attrs.content === 'string' && /^#?[0-9A-Fa-f]{3,8}$/.test(m.attrs.content));
    },
  },
  csp: {
    required: false,
    desc: '<meta http-equiv="Content-Security-Policy"> (含 default-src 且 unsafe-inline 非必须但无 *)',
    check: (M) => {
      const c = M.metas.find(m => (m.attrs['http-equiv']||'').toLowerCase() === 'content-security-policy')?.attrs.content || '';
      if (!c) return false;
      // 建议至少定义 default-src 或 script-src
      return /default-src|script-src/.test(c);
    },
  },
  contentTypeNosniff: {
    required: false,
    desc: '<meta http-equiv="X-Content-Type-Options"> nosniff',
    check: (M) => {
      const c = M.metas.find(m => (m.attrs['http-equiv']||'').toLowerCase() === 'x-content-type-options')?.attrs.content || '';
      return /nosniff/i.test(c);
    },
  },
  referrer: {
    required: false,
    desc: '<meta name="referrer"> strict-origin-when-cross-origin (隐私)',
    check: (M) => {
      const v = M.metas.find(m => m.attrs.name === 'referrer')?.attrs.content || '';
      return /^(no-referrer|strict-origin-when-cross-origin|no-referrer-when-downgrade|origin-when-cross-origin|same-origin)$/.test(v);
    },
  },
  // --- OpenGraph ---
  ogTitle: {
    required: false,
    desc: '<meta property="og:title">',
    check: (M) => (M.og['og:title'] || '').length >= 6,
  },
  ogDescription: {
    required: false,
    desc: '<meta property="og:description">',
    check: (M) => (M.og['og:description'] || '').length >= 10,
  },
  ogImage: {
    required: false,
    desc: '<meta property="og:image"> 链接可访问',
    check: (M) => /^https?:\/\/|^\//.test(M.og['og:image'] || ''),
  },
  ogType: {
    required: false,
    desc: '<meta property="og:type"> website/...',
    check: (M) => /^(website|article|profile|product)$/.test(M.og['og:type'] || ''),
  },
  twitterCard: {
    required: false,
    desc: '<meta name="twitter:card"> summary/summary_large_image',
    check: (M) => /^summary(_large_image)?$/.test(M.metas.find(m=>m.attrs.name==='twitter:card')?.attrs.content || ''),
  },
  appleTouchIcon: {
    required: false,
    desc: '<link rel="apple-touch-icon"> (iOS 主屏)',
    check: (M) => M.links.some(l => (l.attrs.rel || '').toLowerCase().split(/\s+/).includes('apple-touch-icon')),
  },
  // --- CSS/JS 资源 preconnect ---
  designSystemCSS: {
    required: true,
    desc: '<link rel="stylesheet"> 加载 design-system.css',
    check: (M) => M.links.some(l => /design-system\.css(\?|$)/.test(l.attrs.href || '')),
  },
  // --- 结构化数据 可选 ---
  jsonLd: {
    required: false,
    desc: '存在 <script type="application/ld+json"> 结构化数据',
    check: (M) => M.jsonLdCount > 0,
  },
};

// ===== 解析 HTML (轻量, 避免依赖) =====
function parseMeta(html, filePath) {
  const result = {
    path: filePath,
    htmlLang: '',
    title: '',
    metas: [], // { attrs: {name,content,charset,...} }
    links: [], // { attrs: {rel,href,...} }
    scripts: [], // { attrs: {type,src,...}, text:bool }
    jsonLdCount: 0,
    og: {}, // { "og:title": "...", ... }
  };
  // html lang
  {
    const m = html.match(/<html\b([^>]*)>/i);
    if (m) {
      const l = m[1].match(/\blang\s*=\s*["']([^"']+)["']/i);
      if (l) result.htmlLang = l[1];
    }
  }
  // title
  {
    const m = html.match(/<title>\s*([\s\S]*?)\s*<\/title>/i);
    if (m) result.title = m[1];
  }
  // 收集 <meta> <link> <script> 标签属性
  const tagRegex = /<\s*(meta|link|script|base|title)\b([^>]*?)(?:>([\s\S]*?)<\s*\/\s*\1\s*>|\s*\/?>|>)/gi;
  let m;
  while ((m = tagRegex.exec(html)) !== null) {
    const tag = m[1].toLowerCase();
    const attrStr = m[2] || '';
    const inner = m[3] || '';
    const attrs = {};
    const attrRegex = /([\w:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))/g;
    let am;
    while ((am = attrRegex.exec(attrStr)) !== null) {
      const key = am[1].toLowerCase();
      const val = (am[2] ?? am[3] ?? am[4] ?? '');
      attrs[key] = val;
    }
    if (tag === 'meta') {
      result.metas.push({ attrs });
      if (attrs.property) {
        // og:/article:/fb: 等 property=content
        result.og[attrs.property] = attrs.content ?? '';
      }
    } else if (tag === 'link') {
      result.links.push({ attrs });
    } else if (tag === 'script') {
      if ((attrs.type || '').toLowerCase() === 'application/ld+json') result.jsonLdCount++;
      result.scripts.push({ attrs, hasInnerText: inner.trim().length > 0 });
    }
  }
  // 为 meta 同时补 name/property 映射到 og 键 (有些站用 name=og:title)
  for (const m of result.metas) {
    if (!m.attrs.property && m.attrs.name && /^(og|twitter|article|fb):/.test(m.attrs.name)) {
      result.og[m.attrs.name] ??= (m.attrs.content ?? '');
    }
  }
  return result;
}

// ===== 评估每应用 =====
let passed = 0, warn = 0, fail = 0;
const REPORT = { apps: {}, strict: STRICT, _generatedAt: new Date().toISOString() };
const ruleOrder = Object.keys(RULES);

for (const app of APPS) {
  const raw = fs.readFileSync(app.html, 'utf-8');
  const parsed = parseMeta(raw, app.html);
  const results = [];
  for (const key of ruleOrder) {
    const rule = RULES[key];
    const ok = rule.check(parsed, raw);
    let level;
    if (ok) level = 'PASS';
    else if (rule.required) level = STRICT ? 'FAIL' : 'FAIL';
    else level = STRICT ? 'FAIL' : 'WARN';
    if (level === 'PASS') passed++;
    else if (level === 'WARN') warn++;
    else fail++;
    results.push({ key, level, ok, desc: rule.desc, required: rule.required });
  }
  REPORT.apps[app.key] = {
    title: app.title,
    file: path.relative(ROOT, app.html),
    titleText: parsed.title,
    htmlLang: parsed.htmlLang,
    metaCount: parsed.metas.length,
    linkCount: parsed.links.length,
    jsonLdCount: parsed.jsonLdCount,
    rules: results,
  };
}

// ===== 终端输出 =====
const red = (s) => `\x1b[31m${s}\x1b[0m`;
const green = (s) => `\x1b[32m${s}\x1b[0m`;
const yellow = (s) => `\x1b[33m${s}\x1b[0m`;
const dim = (s) => `\x1b[2m${s}\x1b[0m`;
const bold = (s) => `\x1b[1m${s}\x1b[0m`;

console.log(`\n${bold('========== 链盛通 LSC HTML/安全元数据审计 ==========')} (strict=${STRICT ? 'ON' : 'OFF'})`);
for (const appKey of Object.keys(REPORT.apps)) {
  const a = REPORT.apps[appKey];
  const appPass = a.rules.filter(r=>r.level==='PASS').length;
  const appWarn = a.rules.filter(r=>r.level==='WARN').length;
  const appFail = a.rules.filter(r=>r.level==='FAIL').length;
  console.log(`\n${bold(a.title)}  [${a.file}]  <html lang="${a.htmlLang||''}">  <title>: ${JSON.stringify(a.titleText)}`);
  console.log(`  metas=${a.metaCount} links=${a.linkCount} json-ld=${a.jsonLdCount}`);
  for (const r of a.rules) {
    const sign = r.level === 'PASS' ? green('✓') : (r.level === 'WARN' ? yellow('!') : red('✗'));
    const req = r.required ? dim('[R]') : dim('[·]');
    console.log(`   ${sign} ${req} ${r.level.padEnd(4)} ${r.key.padEnd(22)} ${r.desc}`);
  }
  const sumColor = appFail > 0 ? red : (appWarn > 0 ? yellow : green);
  console.log(`  小计: ${sumColor(`PASS=${appPass}  WARN=${appWarn}  FAIL=${appFail}`)}`);
}
console.log(`\n${bold('总合计:')} ${green(`PASS=${passed}`)} ${warn?yellow(`WARN=${warn}`):''} ${fail?red(`FAIL=${fail}`):''}`);

// ===== Markdown 报告 =====
function buildMD() {
  const lines = [];
  lines.push(`# 链盛通 LSC 系统 HTML/安全元数据审计报告`);
  lines.push('');
  lines.push(`- 生成时间: ${REPORT._generatedAt}`);
  lines.push(`- 严格模式: ${REPORT.strict ? '开启 (WARN 计为 FAIL)' : '关闭'}`);
  lines.push(`- 结果: PASS=${passed}  WARN=${warn}  FAIL=${fail}`);
  lines.push('');
  for (const appKey of Object.keys(REPORT.apps)) {
    const a = REPORT.apps[appKey];
    lines.push(`## ${a.title}`);
    lines.push('');
    lines.push(`- 文件: \`${a.file}\``);
    lines.push(`- &lt;html lang&gt; : \`${a.htmlLang||'(missing)'}\``);
    lines.push(`- &lt;title&gt; : ${JSON.stringify(a.titleText)}`);
    lines.push(`- meta 总数: ${a.metaCount}  link 总数: ${a.linkCount}  JSON-LD 块: ${a.jsonLdCount}`);
    lines.push('');
    lines.push('| 规则 | 级别 | 通过 | 说明 | 必需 |');
    lines.push('|---|---|---|---|---|');
    for (const r of a.rules) {
      lines.push(`| \`${r.key}\` | ${r.level} | ${r.ok ? '✓' : '✗'} | ${r.desc} | ${r.required?'Y':''} |`);
    }
    lines.push('');
  }
  return lines.join('\n') + '\n';
}

// ===== 写产物 =====
if (JSON_OUT) {
  const out = JSON.stringify({ passed, warn, fail, ...REPORT }, null, 2);
  fs.mkdirSync(path.dirname(path.resolve(JSON_OUT)), { recursive: true });
  fs.writeFileSync(JSON_OUT, out);
  console.log(`\nJSON → ${JSON_OUT}`);
}
if (MD_OUT) {
  const out = buildMD();
  fs.mkdirSync(path.dirname(path.resolve(MD_OUT)), { recursive: true });
  fs.writeFileSync(MD_OUT, out);
  console.log(`MD   → ${MD_OUT}`);
}

// 退出码
const exitCode = STRICT ? (fail+warn > 0 ? 1 : 0) : (fail > 0 ? 1 : 0);
console.log(`\nexit=${exitCode}${exitCode?` (${STRICT?'严格模式':'普通模式'} 存在 ${STRICT?fail+warn:fail} 项不达标)`:''}`);
process.exit(exitCode);
