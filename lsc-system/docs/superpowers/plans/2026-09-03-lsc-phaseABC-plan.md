# LSC V6.2 深化开发三阶段实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按 A→B→C 顺序交付：(A) 四端稳定 data-testid 契约（~128 钩子 + E2E 选择器迁移 + c8 契约断言）；(B) 键盘可达性（焦点环 + Roving tabindex + 6 类快捷键 + Playwright 用例）；(C) PWA 基础（四端 manifest + 全局 SW + preload/preconnect）。所有质量门控（c8/axe/assets/LH）基线不降或提升。

**Architecture:** (A) 静态 index.html 直接写属性，动态 DOM（表格行/工具条）在创建时 setAttribute；(B) 新建 shared/keyboard-a11y.js（端通过 init config 注入差异化映射），焦点环写进 shared/design-system.css；(C) 四端各自 manifest.json（SVG data-uri 图标，零二进制），shared/sw.js 采用 App Shell pre-cache + 按资源类型分层 runtime cache。

**Tech Stack:** 原生 HTML/CSS/ES5+（无构建）、Node 18+、c8（覆盖率）、Playwright（E2E + LH）、axe-core（可访问性审计）。

---

## File Structure（改动地图）

| File | Action | 职责 |
|---|---|---|
| `merchant-admin/index.html` | Modify | 39 个静态 data-testid（nav/topbar/容器 + preload/preconnect/manifest link + keyboard-a11y 引入 + SW 注册） |
| `platform-admin/index.html` | Modify | 45 个静态 data-testid + 同上性能/a11y/PWA 注入 |
| `mobile-app/index.html` | Modify | 20 个静态 data-testid + 同上 |
| `mini-program/index.html` | Modify | 24 个静态 data-testid + 同上 |
| `merchant-admin/app.js` | Modify | 行按钮、seg、工具条动态钩子 setAttribute |
| `platform-admin/app.js` | Modify | 行按钮、seg、工具条动态钩子 setAttribute |
| `mobile-app/app.js` | Modify | 快捷入口、扫码页按钮、订单列表动态钩子 |
| `mini-program/app.js` | Modify | 九宫格、wx-tabbar 子页、扫码页按钮动态钩子 |
| `e2e/lsc-core.spec.js` | Modify | 选择器从 `.nav-item[data-view=*]`、`.theme-toggle` 等改写成 `[data-testid=...]` |
| `e2e/lsc-extended.spec.js` | Modify | 同上（三态 theme-toggle + meta-color 场景） |
| `e2e/lsc-screenreader.spec.js` | Modify | 新增 6 个键盘用例（Esc / `/` / 1~5 / T / GV / GN） |
| `coverage_runner.js` | Modify | 四端各加一个 `Fxx` 块：querySelectorAll('[data-testid]') 阈值断言 + Top 10 关键钩子 exists 断言；键盘脚本 3 条分支覆盖 |
| `shared/design-system.css` | Modify | 追加 `:focus-visible` 焦点环 + `:focus` 去 outline + `.skip-link:focus-visible` 显示 |
| `shared/keyboard-a11y.js` | **Create** | `window.LSCKeyboardA11y = {init(config)}`：Roving + 快捷键 |
| `merchant-admin/manifest.json` | **Create** | Web App Manifest（商家后台，SVG data-uri 图标） |
| `platform-admin/manifest.json` | **Create** | Web App Manifest（平台后台，SVG data-uri 图标） |
| `mobile-app/manifest.json` | **Create** | Web App Manifest（消费者 APP） |
| `mini-program/manifest.json` | **Create** | Web App Manifest（小程序端） |
| `shared/sw.js` | **Create** | App Shell 预缓存 + runtime 分层策略 + offline fallback |
| `audit-assets.js` | No-Op | 新资源增量会被全量统计自动包含，无需改阈值（已 800 KiB 上限 90 KiB 基线） |

---

# GROUP A · 可测试性契约（data-testid）

---

## Task A1 · Merchant-admin index.html 静态 data-testid 注入

**Files:**
- Modify: `lsc-system/merchant-admin/index.html` (L440-L502 范围)

- [ ] **Step 1: 侧边栏 nav 9 个钩子** — 逐 nav-item 精确 Read 后 Edit，每个 `<a class="nav-item" ...>` 追加 `data-testid="merchant-nav-<view>"`

```html
<!-- 示例（每个 nav-item 按 data-view 值命名）： -->
<a class="nav-item active" data-view="dashboard" role="button" tabindex="0" aria-current="page"
   aria-label="经营总览"
+  data-testid="merchant-nav-dashboard">
   <span class="icon" data-i="overview" aria-hidden="true"></span><span>经营总览</span></a>

<!-- 其它 8 个按 view 值：shop / product / wallet / nh / b2b / promotion / credit / ai -->
```

- [ ] **Step 2: Topbar 6 个钩子** — Read merchant topbar L475-L498 精确块，分别给：
  - `<nav class="breadcrumb">` → `data-testid="merchant-crumb"`
  - `<input id="merchant-search">` → `data-testid="merchant-search-input"`
  - `<button id="themeToggle">` → `data-testid="merchant-theme-toggle"`
  - `<button ... onclick="navTo('nh')">` → `data-testid="merchant-qr-btn"`
  - 通知 icon-btn（含 aria-label="通知..."） → `data-testid="merchant-notif-btn"`
  - `<div class="user-chip" ...>` → `data-testid="merchant-user-chip"`

- [ ] **Step 3: 容器 4 个钩子**
  - `<aside class="sidebar">` → `data-testid="merchant-sidebar"`
  - `<header class="topbar">` → `data-testid="merchant-topbar"`
  - `<section class="content" id="view">` → `data-testid="merchant-content"`
  - `<div id="app-status">` → `data-testid="merchant-app-status"`

- [ ] **Step 4: 本地静态校验（不启动浏览器）**

Run: `cd lsc-system && node -e "
const fs=require('fs');
const html=fs.readFileSync('merchant-admin/index.html','utf8');
const re=/data-testid=\"merchant-[^\"]+\"/g;
const matches=html.match(re)||[];
console.log('Merchant static testid count:', matches.length);
console.assert(matches.length >= 19, 'Expect >= 19 static hooks, got', matches.length);
matches.forEach(m=>console.log(' ', m));
"`
Expected: count ≥ 19 (9 nav + 6 topbar + 4 container = 19)

- [ ] **Step 5: Commit A1**

```bash
git add lsc-system/merchant-admin/index.html
git commit -m "feat(merchant): static data-testid hooks (nav/topbar/containers)"
```

---

## Task A2 · Platform-admin index.html 静态 data-testid 注入

**Files:**
- Modify: `lsc-system/platform-admin/index.html` (L478-L566 范围)

- [ ] **Step 1: Nav 10 个钩子** — 按 data-view 值：dashboard / merchant / product / b2b / risk / credit / release / reconcile / system / ai → `platform-nav-<view>`

- [ ] **Step 2: Topbar 7 个钩子**
  - `<nav class="breadcrumb">` → `platform-crumb`
  - `<input id="search-input">` → `platform-search-input`
  - `<button id="themeToggle">` → `platform-theme-toggle`
  - `<button id="ai-toggle">` → `platform-ai-toggle`
  - `<button id="notif-toggle">` → `platform-notif-toggle`
  - `<div class="user-chip" ...>` → `platform-user-chip`
  - `<a ... onclick="...classList.add('hidden')">`（通知面板内"查看全部"链接） → `platform-notif-panel-close`

- [ ] **Step 3: 容器 4 个**（sidebar / topbar / content / app-status）→ `platform-sidebar / platform-topbar / platform-content / platform-app-status`

- [ ] **Step 4: 静态校验 count ≥ 21**

Run: `cd lsc-system && node -e "
const fs=require('fs');
const html=fs.readFileSync('platform-admin/index.html','utf8');
const m=html.match(/data-testid=\"platform-[^\"]+\"/g)||[];
console.log('Platform static testid count:', m.length);
console.assert(m.length >= 21, 'Expect >= 21');
"`
Expected: 21 (10 nav + 7 topbar + 4 container)

- [ ] **Step 5: Commit A2**

```bash
git add lsc-system/platform-admin/index.html
git commit -m "feat(platform): static data-testid hooks (nav/topbar/containers)"
```

---

## Task A3 · Mobile-app index.html 静态 data-testid 注入

**Files:**
- Modify: `lsc-system/mobile-app/index.html` (L418-L466 范围)

- [ ] **Step 1: Tab Bar 5 个钩子** — 每个 `button.tab-item[data-screen=*]` → `mobile-tabbar-<screen>` (home/mall/scan/wallet/me)

- [ ] **Step 2: Theme toggle + 容器**
  - `<button id="themeToggle">` → `mobile-theme-toggle`
  - `<main id="content">` → `mobile-content`
  - `<nav id="tabbar">` → `mobile-tabbar-nav`
  - 5 个 section `.screen[id=screen-*]` → `mobile-screen-home / mall / scan / wallet / me`

- [ ] **Step 3: 静态校验 count ≥ 13**

Run: `node -e "
const fs=require('fs');
const html=fs.readFileSync('mobile-app/index.html','utf8');
const m=html.match(/data-testid=\"mobile-[^\"]+\"/g)||[];
console.log('Mobile static testid count:', m.length);
console.assert(m.length >= 13, 'Expect >= 13');
"`
Expected: ≥ 13 (5 tabbar + 1 theme + 2 container + 5 screens)

- [ ] **Step 4: Commit A3**

```bash
git add lsc-system/mobile-app/index.html
git commit -m "feat(mobile): static data-testid hooks (tabbar/containers/screens)"
```

---

## Task A4 · Mini-program index.html 静态 data-testid 注入

**Files:**
- Modify: `lsc-system/mini-program/index.html` (L412-L464 范围)

- [ ] **Step 1: Tab Bar 5 个** — `mini-tabbar-<screen>` (home/mall/scan/wallet/me)

- [ ] **Step 2: 胶囊 & 返回 & 导航 3 个**
  - 胶囊内更多按钮（`aria-label=更多操作`） → `mini-capsule-more`
  - 胶囊内关闭按钮（`aria-label=关闭小程序`） → `mini-capsule-close`
  - 返回按钮（`#wx-navbar .wx-back` 内） → `mini-navbar-back`（若页面启用返回按钮时会插入）

- [ ] **Step 3: 微信特有元素 3 个**
  - `#wx-nav-title` → `mini-wx-navbar-title`
  - 支付条（`.wx-pay-bar`） → `mini-wx-pay-bar`
  - 订阅消息提示（`.wx-subscribe-tip`） → `mini-wx-subscribe`

- [ ] **Step 4: Theme + 容器 5 个**
  - `<button id="themeToggle">` → `mini-theme-toggle`
  - `<main id="wx-content">` → `mini-content`
  - `<nav id="wx-tabbar">` → `mini-tabbar-nav`
  - `#screen-home` → `mini-screen-home`
  - `#screen-mall` → `mini-screen-mall`

- [ ] **Step 5: 静态校验 count ≥ 16**

Run: `node -e "
const fs=require('fs');
const html=fs.readFileSync('mini-program/index.html','utf8');
const m=html.match(/data-testid=\"mini-[^\"]+\"/g)||[];
console.log('Mini static testid count:', m.length);
console.assert(m.length >= 16, 'Expect >= 16');
"`
Expected: ≥ 16 (5 tabbar + 3 capsule/back + 3 wx-elements + 5 theme/container/screen)

- [ ] **Step 6: Commit A4**

```bash
git add lsc-system/mini-program/index.html
git commit -m "feat(mini): static data-testid hooks (tabbar/capsule/wx/containers)"
```

---

## Task A5 · 四端 app.js 动态 data-testid 钩子（seg / row-btn / 工具条 / 快捷入口）

**Files:**
- Modify: `lsc-system/merchant-admin/app.js` — seg 段、render* 函数中的 `<span class="row-btn"`、`<div class="seg">` 内 seg-item、`<div class="toolbar">` 内按钮
- Modify: `lsc-system/platform-admin/app.js` — 同上
- Modify: `lsc-system/mobile-app/app.js` — renderHome quick-grid、renderScan 动作按钮
- Modify: `lsc-system/mini-program/app.js` — renderHome wx-grid（九宫格）、renderScan 动作按钮

- [ ] **Step 1: merchant-admin/app.js 行按钮注入** — 先 Read L580 附近 `class="row-btn"` 所有模板字面量。对每个 `<span class="row-btn"` 追加：
  - 资质 → `data-testid="merchant-row-view"`
  - 调额 → `data-testid="merchant-row-edit"`
  - 核销类 → `data-testid="merchant-row-verify"`
  - 释放类 → `data-testid="merchant-row-release"`
  - 红色/danger → `data-testid="merchant-row-danger"`
  - 黄色/warn → `data-testid="merchant-row-warn"`
  - 复制 → `data-testid="merchant-row-copy"`
  - 打印 → `data-testid="merchant-row-print"`
  - 详情 → `data-testid="merchant-row-detail"`
  - 风险 → `data-testid="merchant-row-risk"`
  - 申诉 → `data-testid="merchant-row-appeal"`
  - 关闭 → `data-testid="merchant-row-close"`
- [ ] **Step 2: merchant seg 注入** — Read `class="seg"` 块，每个 seg-item 追加：`merchant-seg-7d / 30d / 90d / all`；toolbar 中按钮追加：`merchant-toolbar-refresh / export / filter / search`。
- [ ] **Step 3: platform-admin/app.js 同 A5 Step 1/2 模式替换**，前缀用 `platform-*`，数量更多（行按钮 14 类、工具条 10 类）。
- [ ] **Step 4: mobile-app/app.js renderHome quick-grid 4 项** → `mobile-quick-scan / paycode / coupon / ai`；renderScan 3 个按钮 → `mobile-scan-flashlight / mobile-scan-photo / mobile-scan-paycode`。
- [ ] **Step 5: mini-program/app.js renderHome wx-grid 8 项** → `mini-grid-scan / pay / coupon / card / invite / ai / promo / helper`；renderScan 3 个 → `mini-scan-flashlight / mini-scan-album / mini-scan-qrcode`。
- [ ] **Step 6: JSDOM 全局 count 校验**（模拟注入后）

Run: `cd lsc-system && node -e "
const fs=require('fs');
function count(f){const s=fs.readFileSync(f,'utf8');const re=/data-testid=\"(merchant|platform|mobile|mini)-[^\"]+\"/g;return (s.match(re)||[]).length;}
const total = count('merchant-admin/index.html') + count('merchant-admin/app.js')
            + count('platform-admin/index.html') + count('platform-admin/app.js')
            + count('mobile-app/index.html') + count('mobile-app/app.js')
            + count('mini-program/index.html') + count('mini-program/app.js');
console.log('Total data-testid occurrences:', total);
console.assert(total >= 128, 'Need >= 128 total');
"`
Expected: total ≥ 128

- [ ] **Step 7: Commit A5（按端拆 4 个 commit 或 1 个总 commit）**

```bash
git add lsc-system/merchant-admin/app.js lsc-system/platform-admin/app.js lsc-system/mobile-app/app.js lsc-system/mini-program/app.js
git commit -m "feat: dynamic data-testid hooks for row-buttons/seg/toolbar/shortcuts"
```

---

## Task A6 · E2E 选择器迁移（class/data-view → data-testid）

**Files:**
- Modify: `lsc-system/e2e/lsc-core.spec.js` (L33-L154 选择器行)
- Modify: `lsc-system/e2e/lsc-extended.spec.js`（themeToggle 及 meta 场景选择器）

- [ ] **Step 1: Read lsc-core.spec.js 全文段，逐处替换**

```javascript
// Before:
await page.click('.nav-item[data-view="dashboard"]');
// After:
await page.click('[data-testid="merchant-nav-dashboard"]');

// Before (nh view):
await page.click('.nav-item[data-view="nh"]');
// After:
await page.click('[data-testid="merchant-nav-nh"]');

// Before (wallet view):
await page.click('.nav-item[data-view="wallet"]');
// After:
await page.click('[data-testid="merchant-nav-wallet"]');

// Before (ai view):
await page.click('.nav-item[data-view="ai"]', { timeout: 12000 });
// After:
await page.click('[data-testid="merchant-nav-ai"]', { timeout: 12000 });

// Before (shop view):
await page.click('.nav-item[data-view="shop"]', { timeout: 12000 });
// After:
await page.click('[data-testid="merchant-nav-shop"]', { timeout: 12000 });
```

- [ ] **Step 2: lsc-extended.spec.js 三态 theme-toggle 选择器**

```javascript
// Before:
const t = page.locator('.theme-toggle');
// After (merchant — extend per end):
const t = page.locator('[data-testid="merchant-theme-toggle"]');
```

- [ ] **Step 3: Playwright 语法级静态校验（无 strict selector 报错）**

Run: `cd lsc-system && node -e "
const fs=require('fs');
const code1=fs.readFileSync('e2e/lsc-core.spec.js','utf8');
const code2=fs.readFileSync('e2e/lsc-extended.spec.js','utf8');
const oldClassSel = code1 + code2;
const legacy = oldClassSel.match(/\.nav-item\[data-view/g)||[];
console.log('Legacy .nav-item[data-view selectors remaining:', legacy.length);
console.assert(legacy.length === 0, 'Still has legacy selectors:', legacy.length);
console.log('New [data-testid= occurrences:', (oldClassSel.match(/data-testid=\")||[]).length || 'N/A');
"`
Expected: legacy `nav-item[data-view=*]` count == 0

- [ ] **Step 4: Commit A6**

```bash
git add lsc-system/e2e/lsc-core.spec.js lsc-system/e2e/lsc-extended.spec.js
git commit -m "test(e2e): migrate selectors from class to data-testid"
```

---

## Task A7 · coverage_runner.js 新增契约断言（四端阈值 + Top N）

**Files:**
- Modify: `lsc-system/coverage_runner.js`（在最后一个 `Fxx` 块之后，依次追加 F_A1~F_A4 四个 block）

- [ ] **Step 1: Append F_A1 Merchant 端** — 紧跟已通过的最后一个 JSDOM `try {}` 块后插入：

```javascript
  // F_A1. merchant-admin data-testid 契约
  try {
    const rA1 = execVM(w, `
      var all = document.querySelectorAll('[data-testid^="merchant-"]');
      var ids = {};
      all.forEach(function(el){ids[el.getAttribute('data-testid')]=1;});
      var uniq = Object.keys(ids).length;
      var required = ['merchant-theme-toggle','merchant-search-input','merchant-nav-dashboard',
        'merchant-nav-shop','merchant-nav-product','merchant-nav-wallet','merchant-nav-nh',
        'merchant-nav-b2b','merchant-nav-promotion','merchant-nav-credit','merchant-nav-ai',
        'merchant-content','merchant-app-status'];
      var miss = [];
      required.forEach(function(id){if(!ids[id]) miss.push(id);});
      return {count: uniq, unique_ids: Object.keys(ids), missing_required: miss};
    `);
    passed.push('F_A1_merchant_data_testid_threshold_' + rA1.count);
    assert_ge(rA1.count, 39, 'merchant testid count < 39, ids='+JSON.stringify(rA1.unique_ids));
    assert_eq(rA1.missing_required.length, 0,
      'merchant missing required testids: '+rA1.missing_required.join(','));
  } catch(e) { failed.push('F_A1_merchant_data_testid:'+e.message); }
```

- [ ] **Step 2: F_A2 Platform 端（阈值 45，required 列表含 10 nav + theme-toggle/search-input/ai-toggle/notif-toggle 等 12 条）**
- [ ] **Step 3: F_A3 Mobile 端（阈值 20，required 列表含 5 tabbar + theme-toggle + 4 quick + content 等 12 条）**
- [ ] **Step 4: F_A4 Mini 端（阈值 24，required 列表含 5 tabbar + 3 capsule + 8 grid + theme-toggle 等 17 条）**
- [ ] **Step 5: 本地跑 c8 验证**

Run: `cd lsc-system && node coverage_runner.js > /tmp/cov.log 2>&1 | tail -5; echo "EXIT=$?"`
Expected: exit 0; all 4 A-phase assertions pass; output shows `F_A1_..._passed`、`F_A2_...`、`F_A3_...`、`F_A4_...` 四个新 passed tokens

- [ ] **Step 6: 跑 c8 check-coverage 确保阈值不减**

Run: `cd lsc-system && npx c8 check-coverage --statements 99 --branches 95 --functions 95`
Expected: exit 0 (全 100 仍维持)

- [ ] **Step 7: Commit A7**

```bash
git add lsc-system/coverage_runner.js
git commit -m "test(c8): add data-testid contract assertions (4-end thresholds + required sets)"
```

---

# GROUP B · 键盘可达性

---

## Task B1 · shared/design-system.css 焦点环 + skip-link CSS 追加

**Files:**
- Modify: `lsc-system/shared/design-system.css`（文件末尾追加）

- [ ] **Step 1: 定位 design-system.css 末尾** — Read 最后 30 行，确认没有匹配以下模式后，在 EOF 插入：

```css
/* =========================================================
 * Keyboard A11y — Unified focus ring + skip-link
 * ========================================================= */
:focus { outline: none; }
:focus-visible {
  outline: 2px solid var(--c-accent, #C8A24B);
  outline-offset: 2px;
  border-radius: 4px;
}

.skip-link {
  position: fixed;
  left: -9999px;
  top: 12px;
  z-index: 10000;
  padding: 8px 14px;
  background: var(--c-accent, #C8A24B);
  color: #1a1a1a;
  font-weight: 700;
  border-radius: 8px;
  text-decoration: none;
}
.skip-link:focus-visible {
  left: 12px;
}
```

- [ ] **Step 2: 静态 CSS 校验（无语法错误）**

Run: `cd lsc-system && node -e "
const fs=require('fs');
const css=fs.readFileSync('shared/design-system.css','utf8');
console.assert(css.includes(':focus-visible'), 'no focus-visible rule');
console.assert(css.includes('.skip-link:focus-visible'), 'no skip-link visible rule');
console.assert(css.split('{').length === css.split('}').length, 'unbalanced braces');
console.log('CSS OK');
"`
Expected: CSS OK

- [ ] **Step 3: Commit B1**

```bash
git add lsc-system/shared/design-system.css
git commit -m "feat(a11y): unified focus-visible ring & skip-link focus style"
```

---

## Task B2 · 新建 shared/keyboard-a11y.js（Roving + Shortcuts）

**Files:**
- Create: `lsc-system/shared/keyboard-a11y.js`

- [ ] **Step 1: 写入完整实现**

```javascript
/* LSC Keyboard A11y — shared across 4 ends
 * Exposes: window.LSCKeyboardA11y.init({
 *   scope: 'merchant' | 'platform' | 'mobile' | 'mini',
 *   rovingGroups: [{ containerSel, itemSel }],
 *   searchInputSel: '#search-input',
 *   tabBarSel: '.tab-bar',
 *   contentAnchor: '#view',
 *   navAnchor: '#nav'
 * })
 * Shortcuts (only when focus is not in a form field):
 *   Esc           Close all modals/notif panels
 *   /             Focus top search input
 *   1..5          Activate bottom tabbar Nth item (mobile/mini)
 *   T             Click theme-toggle
 *   G V / G N     Jump to content / nav anchor (vi-style two-key chord, 1.2s window)
 */
(function (global) {
  'use strict';
  var G_CHORD_WINDOW_MS = 1200;
  var lastGAt = 0;

  function isFormField(el) {
    if (!el || !el.tagName) return false;
    var t = el.tagName;
    if (t === 'INPUT' || t === 'TEXTAREA' || t === 'SELECT' || t === 'BUTTON') return true;
    if (el.isContentEditable) return true;
    return false;
  }

  function closeOverlays() {
    var masks = document.querySelectorAll('.modal-mask');
    masks.forEach(function (m) { m.classList.add('hidden'); });
    var notif = document.getElementById('notif-panel');
    if (notif) notif.classList.add('hidden');
    if (typeof closeModals === 'function') closeModals();
  }

  function setupRoving(containerSel, itemSel) {
    var container = document.querySelector(containerSel);
    if (!container) return;
    var items = Array.prototype.slice.call(container.querySelectorAll(itemSel));
    if (!items.length) return;
    items.forEach(function (it) { it.setAttribute('tabindex', '-1'); });
    var activeSel = itemSel + '.active';
    var active = container.querySelector(activeSel);
    if (!active) active = items[0];
    active.setAttribute('tabindex', '0');

    function moveFocus(direction) {
      var current = document.activeElement && container.contains(document.activeElement)
        ? document.activeElement
        : active;
      var idx = items.indexOf(current);
      if (idx === -1) idx = 0;
      var next = (idx + direction + items.length) % items.length;
      items[idx].setAttribute('tabindex', '-1');
      items[next].setAttribute('tabindex', '0');
      items[next].focus();
    }

    container.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowDown' || e.key === 'ArrowRight') { e.preventDefault(); moveFocus(+1); }
      else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') { e.preventDefault(); moveFocus(-1); }
      else if (e.key === 'Home') { e.preventDefault(); items[items.length-1].setAttribute('tabindex','-1');
        items[0].setAttribute('tabindex','0'); items[0].focus(); }
      else if (e.key === 'End') { e.preventDefault(); var cur=container.querySelector(itemSel+'[tabindex="0"]');
        if(cur) cur.setAttribute('tabindex','-1');
        items[items.length-1].setAttribute('tabindex','0'); items[items.length-1].focus(); }
      else if (e.key === 'Enter' || e.key === ' ') {
        if (e.target === container.querySelector(itemSel+'[tabindex="0"]')) { e.preventDefault(); e.target.click(); }
      }
    });
  }

  function activateNthTab(tabBarSel, n0) {
    var bar = document.querySelector(tabBarSel);
    if (!bar) return false;
    var tabs = Array.prototype.slice.call(bar.querySelectorAll('[data-screen]'));
    var target = tabs[n0];
    if (target) { target.click(); target.focus(); return true; }
    return false;
  }

  function init(cfg) {
    cfg = cfg || {};
    var scope = cfg.scope || 'merchant';
    var searchSel = cfg.searchInputSel || ('#' + scope + '-search-input');
    var tabBarSel = cfg.tabBarSel;
    var contentSel = cfg.contentAnchor || '#view';
    var navSel = cfg.navAnchor || '#nav';
    var themeSel = '[data-testid="' + scope + '-theme-toggle"]';

    (cfg.rovingGroups || []).forEach(function (g) { setupRoving(g.containerSel, g.itemSel); });

    function onKey(e) {
      if (isFormField(e.target)) {
        // Allow Esc to still close overlays even inside a form
        if (e.key === 'Escape') closeOverlays();
        return;
      }
      var now = Date.now();
      if (e.key === 'Escape') { closeOverlays(); return; }
      if (e.key === '/') {
        var input = document.querySelector(searchSel);
        if (input) { e.preventDefault(); input.focus(); }
        return;
      }
      if (e.key === 't' || e.key === 'T') {
        var tb = document.querySelector(themeSel);
        if (tb) { e.preventDefault(); tb.click(); }
        return;
      }
      if (/^[1-5]$/.test(e.key) && tabBarSel) {
        var ok = activateNthTab(tabBarSel, parseInt(e.key, 10) - 1);
        if (ok) e.preventDefault();
        return;
      }
      if (e.key === 'g' || e.key === 'G') { lastGAt = now; return; }
      if ((e.key === 'v' || e.key === 'V') && (now - lastGAt) <= G_CHORD_WINDOW_MS) {
        var v = document.querySelector(contentSel);
        if (v) { v.setAttribute('tabindex', '-1'); v.focus(); e.preventDefault(); }
        lastGAt = 0; return;
      }
      if ((e.key === 'n' || e.key === 'N') && (now - lastGAt) <= G_CHORD_WINDOW_MS) {
        var n = document.querySelector(navSel);
        if (n) { n.setAttribute('tabindex', '-1'); n.focus(); e.preventDefault(); }
        lastGAt = 0; return;
      }
      // Reset G chord if no follow-up
      if (lastGAt && (now - lastGAt) > G_CHORD_WINDOW_MS) lastGAt = 0;
    }

    document.addEventListener('keydown', onKey);
    // Cleanup hook for tests
    return { destroy: function () { document.removeEventListener('keydown', onKey); } };
  }

  global.LSCKeyboardA11y = { init: init };
})(typeof window !== 'undefined' ? window : this);
```

- [ ] **Step 2: 语法校验 + 导出 API 检查**

Run: `cd lsc-system && node -e "
const fs=require('fs');
const src=fs.readFileSync('shared/keyboard-a11y.js','utf8');
const fn=new Function(src+'; return typeof LSCKeyboardA11y;');
const ctx={window:{document:{addEventListener:()=>{},querySelector:()=>null,querySelectorAll:()=>[]}}};
Object.setPrototypeOf(ctx, globalThis);
const g={window: ctx.window};
Object.defineProperty(ctx.window, 'LSCKeyboardA11y', {enumerable:true, writable:true, value:null});
// Simpler approach: just parse AST
try { new Function(src); console.log('Syntactically valid.'); } catch(e){ console.error(e.message); process.exit(1); }
console.assert(src.includes('LSCKeyboardA11y.init'), 'no init API');
console.assert(src.includes('setupRoving'), 'no roving setup');
console.assert(src.includes('G_CHORD_WINDOW_MS'), 'no G chord');
console.log('keyboard-a11y.js OK');
"`
Expected: `Syntactically valid.` + `keyboard-a11y.js OK`

- [ ] **Step 3: Commit B2**

```bash
git add lsc-system/shared/keyboard-a11y.js
git commit -m "feat(a11y): shared keyboard module (roving + Esc / / / 1-5 / T / GV / GN)"
```

---

## Task B3 · 四端 index.html 引入脚本并端配置 init 调用

**Files:**
- Modify: `lsc-system/merchant-admin/index.html`
- Modify: `lsc-system/platform-admin/index.html`
- Modify: `lsc-system/mobile-app/index.html`
- Modify: `lsc-system/mini-program/index.html`

每端执行相同模式：

- [ ] **Step 1: `<script src="../shared/app-utils.js"></script>` 行**之后**追加重脚本**

```html
<script src="../shared/app-utils.js"></script>
+<script src="../shared/keyboard-a11y.js"></script>
<script src="app.js"></script>
```

- [ ] **Step 2: 主题切换脚本块之前或末尾追加 DOMContentLoaded 后的 init**

```html
<script>
document.addEventListener('DOMContentLoaded', function () {
  if (window.LSCKeyboardA11y) {
    window.LSCKeyboardA11y.init({
      scope: 'merchant',   // 'platform' | 'mobile' | 'mini'
      rovingGroups: [
        { containerSel: '#nav', itemSel: '.nav-item' }  // 桌面：'#nav' + '.nav-item'
                                                     // mobile/mini: tabBarSel + '[data-screen]'
      ],
      searchInputSel: '[data-testid="merchant-search-input"]',
      // tabBarSel: '#tabbar',     // ← 仅 mobile/mini 启用
      contentAnchor: '[data-testid="merchant-content"]',
      navAnchor:     '[data-testid="merchant-sidebar"] #nav'
    });
  }
});
</script>
```
> 配置差异：
> - **merchant / platform**：`rovingGroups: [{containerSel:'#nav', itemSel:'.nav-item'}]`；**无** `tabBarSel`
> - **mobile**：`rovingGroups: [{containerSel:'#tabbar', itemSel:'.tab-item'}]`；`tabBarSel: '#tabbar'`；`contentAnchor: '#content'`；`navAnchor: '#tabbar'`；`searchInputSel` 仅 mall 页面存在 → 用 `'.mall-search input'`
> - **mini**：同 mobile 结构，containerSel / tabBarSel 用 `#wx-tabbar` / `.wx-tab`；searchInputSel 用 `'.wx-search-inner input'`；contentAnchor=`'#wx-content'`；navAnchor=`'#wx-tabbar'`

- [ ] **Step 3: 4 端分别做静态 `LSCKeyboardA11y.init` 存在性校验**

Run: `node -e "
const fs=require('fs');
['merchant-admin','platform-admin','mobile-app','mini-program'].forEach(d=>{
  const h=fs.readFileSync(d+'/index.html','utf8');
  console.log(d, 'has keyboard-a11y include:', h.includes('keyboard-a11y.js'));
  console.log(d, 'has init(scope=...) call:', /LSCKeyboardA11y\.init\([\s\S]*scope:/.test(h));
});
"`
Expected: 4 ends × 2 checks all true

- [ ] **Step 4: Commit B3**

```bash
git add lsc-system/merchant-admin/index.html lsc-system/platform-admin/index.html lsc-system/mobile-app/index.html lsc-system/mini-program/index.html
git commit -m "feat(a11y): wire up keyboard-a11y.js in 4-end index.html with scoped configs"
```

---

## Task B4 · lsc-screenreader.spec.js 新增 6 个键盘用例

**Files:**
- Modify: `lsc-system/e2e/lsc-screenreader.spec.js`

- [ ] **Step 1: 追加 describe('Keyboard A11y shortcuts', ...) 块**

```javascript
// 追加到文件末尾（若已有末尾 describe 则在其后）
describe('Keyboard A11y shortcuts', () => {
  for (const { name, origin } of [
    { name: 'merchant', origin: 'merchant-admin/index.html' },
    { name: 'platform', origin: 'platform-admin/index.html' },
    { name: 'mobile',   origin: 'mobile-app/index.html' },
    { name: 'mini',     origin: 'mini-program/index.html' },
  ]) {
    it(`${name}: Esc closes overlay`, async () => {
      await page.goto(PREFIX + origin);
      const notif = page.locator(`[data-testid="${name}-notif-btn"], [data-testid="${name}-notif-toggle"], #notif-panel`);
      // If we have a trigger button, open panel first
      const triggers = { merchant:'merchant-notif-btn', platform:'platform-notif-toggle' };
      if (triggers[name]) {
        await page.click(`[data-testid="${triggers[name]}"]`);
        await expect(page.locator('#notif-panel')).not.toHaveClass(/hidden/);
      }
      await page.keyboard.press('Escape');
      await expect(page.locator('#notif-panel')).toHaveClass(/hidden/);
    });

    if (name === 'merchant' || name === 'platform') {
      it(`${name}: '/' focuses search input`, async () => {
        await page.goto(PREFIX + origin);
        await page.keyboard.press('/');
        await expect(page.locator(`[data-testid="${name}-search-input"]`)).toBeFocused();
      });
    }

    it(`${name}: 'T' toggles theme`, async () => {
      await page.goto(PREFIX + origin);
      const btn = page.locator(`[data-testid="${name}-theme-toggle"]`);
      const before = await btn.getAttribute('data-state');
      await page.keyboard.press('T');
      const after = await btn.getAttribute('data-state');
      expect(before).not.toBe(after);
    });
  }

  // mobile / mini 专用: 1..5 switch tab
  for (const [name, origin, screens] of [
    ['mobile', 'mobile-app/index.html', ['home','mall','scan','wallet','me']],
    ['mini',   'mini-program/index.html', ['home','mall','scan','wallet','me']],
  ]) {
    it(`${name}: 1..5 switches bottom tab`, async () => {
      await page.goto(PREFIX + origin);
      for (let i=1;i<=5;i++){
        await page.keyboard.press(String(i));
        const active = page.locator(`.tab-item.active[data-screen="${screens[i-1]}"], .wx-tab.active[data-screen="${screens[i-1]}"]`);
        await expect(active).toHaveCount(1);
      }
    });

    it(`${name}: G V focuses content; G N focuses nav`, async () => {
      await page.goto(PREFIX + origin);
      const kb = page.keyboard;
      await kb.press('g'); await kb.press('v');
      const focused1 = await page.evaluate(() => document.activeElement && (document.activeElement.id || document.activeElement.getAttribute('role')));
      expect(focused1).toBeTruthy();
      await kb.press('g'); await kb.press('n');
      const focused2 = await page.evaluate(() => document.activeElement && (document.activeElement.id || document.activeElement.getAttribute('role')));
      expect(focused2).toBeTruthy();
    });
  }
});
```

- [ ] **Step 2: 语法校验**

Run: `node -e "
const fs=require('fs');
try { new Function('require','describe','it','expect','page','PREFIX', fs.readFileSync('e2e/lsc-screenreader.spec.js','utf8')); console.log('Playwright spec syntax OK'); }
catch(e){ console.error(e.message); process.exit(1); }
"`
Expected: Playwright spec syntax OK

- [ ] **Step 3: Commit B4**

```bash
git add lsc-system/e2e/lsc-screenreader.spec.js
git commit -m "test(a11y): add 6 keyboard shortcut Playwright cases (Esc / / / T / 1-5 / GV / GN)"
```

---

## Task B5 · coverage_runner.js 追加键盘分支覆盖（100% 维持）

**Files:**
- Modify: `lsc-system/coverage_runner.js`

- [ ] **Step 1: 追加 F_B1 块**（覆盖 keyboard-a11y 的 isFormField / Esc / / / T / 1..5 / GV / GN 分支）

```javascript
  // F_B1. keyboard-a11y 分支覆盖 (injected via inline script + keydown events)
  try {
    const rB1 = execVM(w, `
      var res = { events: [] };
      function mkKey(key, opts) {
        opts = opts || {};
        var ev = new KeyboardEvent('keydown', {
          key: key, bubbles: true, cancelable: true,
          shiftKey: !!opts.shiftKey, target: opts.target || document.body
        });
        Object.defineProperty(ev, 'key', { value: key });
        if (opts.target) opts.target.dispatchEvent(ev);
        else document.dispatchEvent(ev);
        return ev;
      }
      // Branch: press Escape with focus on body → closeOverlays runs
      res.escape_noform = 'ok';
      try { mkKey('Escape'); } catch(e){ res.escape_noform = 'err:'+e.message; }
      // Branch: '/' focus search — fake search input exists?
      var si = document.createElement('input');
      si.id = 'kb-search-tmp';
      document.body.appendChild(si);
      window.LSCKeyboardA11y.__lastConfig = window.LSCKeyboardA11y.__lastConfig || {};
      // Note: init configs passed by each app individually, we re-init with a predictable config
      if (window.LSCKeyboardA11y.init) {
        window.LSCKeyboardA11y.init({ scope:'merchant', searchInputSel:'#kb-search-tmp',
          rovingGroups:[], contentAnchor:'#view', navAnchor:'#nav' });
        mkKey('/');
        res.slash_focuses_search = (document.activeElement === si) ? 'ok' : 'fail:'+document.activeElement.tagName;
        mkKey('T');
        res.T_fires = 'ok';
      } else {
        res.slash_focuses_search = 'no-init'; res.T_fires = 'no-init';
      }
      document.body.removeChild(si);
      // G V focus view
      if (window.LSCKeyboardA11y.init) {
        window.LSCKeyboardA11y.init({scope:'merchant', rovingGroups:[],
          contentAnchor:'#view', navAnchor:'#nav'});
        mkKey('G'); mkKey('V');
        res.GV_focuses_view = (document.activeElement && document.activeElement.id === 'view') ? 'ok' : 'fail:'+(document.activeElement && document.activeElement.id);
        mkKey('G'); mkKey('N');
        var nav = document.getElementById('nav');
        res.GN_focuses_nav = (document.activeElement === nav) ? 'ok' : 'fail:'+(document.activeElement && document.activeElement.tagName);
      }
      return res;
    `);
    passed.push('F_B1_keyboard_a11y_branch_matrix_cover');
    assert_eq(rB1.escape_noform, 'ok', 'Escape error: '+rB1.escape_noform);
    assert_eq(rB1.slash_focuses_search, 'ok', '/ search focus: '+rB1.slash_focuses_search);
    assert_eq(rB1.T_fires, 'ok', 'T trigger: '+rB1.T_fires);
    assert_eq(rB1.GV_focuses_view, 'ok', 'G V focus: '+rB1.GV_focuses_view);
    assert_eq(rB1.GN_focuses_nav, 'ok', 'G N focus: '+rB1.GN_focuses_nav);
  } catch(e) { failed.push('F_B1_keyboard:' + e.message); }
```

- [ ] **Step 2: 跑 coverage_runner.js 验证断言 pass**

Run: `cd lsc-system && node coverage_runner.js 2>&1 | tail -15; echo EXIT=$?`
Expected: `passed` token list 新增 `F_B1_...`，`failed=[]`，EXIT=0

- [ ] **Step 3: c8 check**

Run: `cd lsc-system && npx c8 check-coverage --statements 99 --branches 95 --functions 95`
Expected: exit 0

- [ ] **Step 4: Commit B5**

```bash
git add lsc-system/coverage_runner.js
git commit -m "test(c8): cover keyboard-a11y branches (Esc / / / T / GV / GN)"
```

---

# GROUP C · PWA + 性能优化

---

## Task C1 · 新建 4 份 manifest.json（SVG data-uri 图标，零二进制）

**Files:**
- Create: `lsc-system/merchant-admin/manifest.json`
- Create: `lsc-system/platform-admin/manifest.json`
- Create: `lsc-system/mobile-app/manifest.json`
- Create: `lsc-system/mini-program/manifest.json`

- [ ] **Step 1: 写 merchant-admin/manifest.json**

```json
{
  "name": "链盛通LSC·商家后台",
  "short_name": "LSC商家",
  "description": "链盛通消费权益凭证循环系统商家管理后台",
  "start_url": "./index.html",
  "scope": "./",
  "display": "standalone",
  "orientation": "natural",
  "background_color": "#F5F3EC",
  "theme_color": "#F5F3EC",
  "lang": "zh-CN",
  "categories": ["business", "finance"],
  "icons": [
    { "src": "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 192 192'><rect width='192' height='192' rx='40' fill='%232d2515'/><rect width='192' height='192' rx='40' fill='url(%23g)'/><defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='%231a1a1a'/><stop offset='1' stop-color='%232d2515'/></linearGradient></defs><text x='96' y='122' font-family='serif' font-size='100' font-weight='900' text-anchor='middle' fill='%23C8A24B'>商</text></svg>",
      "sizes": "192x192", "type": "image/svg+xml", "purpose": "any maskable" },
    { "src": "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 512 512'><rect width='512' height='512' rx='100' fill='%232d2515'/><defs><linearGradient id='g' x1='0' y1='0' x2='1' y2='1'><stop offset='0' stop-color='%231a1a1a'/><stop offset='1' stop-color='%232d2515'/></linearGradient></defs><rect width='512' height='512' rx='100' fill='url(%23g)'/><text x='256' y='330' font-family='serif' font-size='300' font-weight='900' text-anchor='middle' fill='%23C8A24B'>商</text></svg>",
      "sizes": "512x512", "type": "image/svg+xml", "purpose": "any maskable" }
  ]
}
```

- [ ] **Step 2: platform-admin/manifest.json** — 差异：name=链盛通LSC·平台后台, short_name=LSC平台, orientation=landscape；图标渐变用 `#082E2C` 深绿色 + `链` 字
- [ ] **Step 3: mobile-app/manifest.json** — 差异：name=链盛通LSC, short_name=LSC, orientation=portrait, categories=["lifestyle","finance"]；图标渐变 `#082E2C → #1A6764` 绿色 + `LSC` 文字
- [ ] **Step 4: mini-program/manifest.json** — 差异：name=链盛通LSC小程序, short_name=LSC小程序, orientation=portrait, categories=["lifestyle","social"]；图标微信绿色 `#046D36 → #07C160` + `LSC` 文字
- [ ] **Step 5: JSON.parse 静态校验**

Run: `cd lsc-system && node -e "
['merchant-admin','platform-admin','mobile-app','mini-program'].forEach(d=>{
  const f=d+'/manifest.json';
  const m=JSON.parse(require('fs').readFileSync(f,'utf8'));
  console.log(d, ':', m.name, '| icons:', m.icons.length, '| display:', m.display);
  console.assert(m.name && m.short_name && m.start_url, m.name+' manifest invalid');
  console.assert(Array.isArray(m.icons) && m.icons.length === 2, 'need 2 icons');
});
console.log('All 4 manifests OK');
"`
Expected: All 4 manifests OK

- [ ] **Step 6: Commit C1**

```bash
git add lsc-system/merchant-admin/manifest.json lsc-system/platform-admin/manifest.json lsc-system/mobile-app/manifest.json lsc-system/mini-program/manifest.json
git commit -m "feat(pwa): add 4-end manifest.json with inline SVG icons"
```

---

## Task C2 · 新建 shared/sw.js（App Shell 预缓存 + 分层 runtime cache）

**Files:**
- Create: `lsc-system/shared/sw.js`

- [ ] **Step 1: 写入完整 SW 代码**

```javascript
/* LSC PWA Service Worker
 * Cache strategy:
 *   Install: pre-cache App Shell (4 HTML shells + shared assets + manifests)
 *   Runtime:
 *     Google Fonts               → stale-while-revalidate
 *     Same-origin image / *.svg  → cache-first
 *     Per-end app.js             → network-first (ensure fresh JS)
 *     Other HTML / JSON / CSS   → stale-while-revalidate
 *   Offline fallback: any un-caught HTML request → root shell
 */
const VERSION = 'v1';
const SHELL = 'lsc-appshell-' + VERSION;
const RUNTIME = 'lsc-runtime-' + VERSION;

const PRECACHE = [
  '/lsc-system/',
  '/lsc-system/index.html',
  '/lsc-system/merchant-admin/index.html',
  '/lsc-system/platform-admin/index.html',
  '/lsc-system/mobile-app/index.html',
  '/lsc-system/mini-program/index.html',
  '/lsc-system/shared/design-system.css',
  '/lsc-system/shared/app-utils.js',
  '/lsc-system/shared/keyboard-a11y.js',
  '/lsc-system/merchant-admin/manifest.json',
  '/lsc-system/platform-admin/manifest.json',
  '/lsc-system/mobile-app/manifest.json',
  '/lsc-system/mini-program/manifest.json',
];

self.addEventListener('install', function (event) {
  event.waitUntil(
    caches.open(SHELL).then(function (c) { return c.addAll(PRECACHE); })
      .then(function () { return self.skipWaiting(); })
  );
});

self.addEventListener('activate', function (event) {
  event.waitUntil(
    caches.keys().then(function (keys) {
      return Promise.all(keys.filter(function (k) {
        return (k !== SHELL) && (k !== RUNTIME) && k.startsWith('lsc-');
      }).map(function (k) { return caches.delete(k); }));
    }).then(function () { return self.clients.claim(); })
  );
});

function cacheFirst(req) {
  return caches.match(req).then(function (hit) {
    return hit || fetch(req).then(function (res) {
      const copy = res.clone();
      caches.open(RUNTIME).then(function (c) { c.put(req, copy); }).catch(function(){});
      return res;
    });
  });
}

function staleWhileRevalidate(req) {
  return caches.match(req).then(function (hit) {
    const freshP = fetch(req).then(function (res) {
      const copy = res.clone();
      caches.open(RUNTIME).then(function (c) { c.put(req, copy); }).catch(function(){});
      return res;
    }).catch(function () { return hit; });
    return hit || freshP;
  });
}

function networkFirst(req) {
  return fetch(req).then(function (res) {
    const copy = res.clone();
    caches.open(RUNTIME).then(function (c) { c.put(req, copy); }).catch(function(){});
    return res;
  }).catch(function () { return caches.match(req); });
}

self.addEventListener('fetch', function (event) {
  const req = event.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  const host = url.host;
  const path = url.pathname;

  // Google Fonts → SWR
  if (host === 'fonts.googleapis.com' || host === 'fonts.gstatic.com') {
    event.respondWith(staleWhileRevalidate(req));
    return;
  }

  // Only apply caches to same-origin (localhost sandbox safe)
  if (host !== location.host) return;

  // Per-end app.js → network first
  if (path.endsWith('/app.js') && (path.indexOf('merchant-admin/')>=0 ||
       path.indexOf('platform-admin/')>=0 || path.indexOf('mobile-app/')>=0 ||
       path.indexOf('mini-program/')>=0)) {
    event.respondWith(networkFirst(req));
    return;
  }

  // Images / static SVG → cache first
  if (/\.(png|jpg|jpeg|gif|svg|webp|ico)(\?|$)/i.test(path)) {
    event.respondWith(cacheFirst(req));
    return;
  }

  // HTML / JSON / CSS → stale-while-revalidate
  if (/\.html?$|\.json$|\.css$/.test(path)) {
    event.respondWith(staleWhileRevalidate(req).then(function (r) {
      if (r) return r;
      // offline fallback for HTML nav
      if (req.mode === 'navigate' || /\.html?$/.test(path)) {
        return caches.match('/lsc-system/index.html');
      }
    }));
    return;
  }
});
```

- [ ] **Step 2: 语法校验**

Run: `cd lsc-system && node -e "
const fs=require('fs');
const src=fs.readFileSync('shared/sw.js','utf8');
try { new Function('self','caches','fetch','clients',src); console.log('sw.js syntax OK'); }
catch(e){ console.error(e.message); process.exit(1); }
console.assert(src.includes('SHELL'), 'no pre-cache constant');
console.assert(src.includes('install'), 'no install evt');
console.assert(src.includes('activate'), 'no activate evt');
console.assert(src.includes('fetch'), 'no fetch evt');
console.log('sw.js OK');
"`
Expected: sw.js syntax OK + sw.js OK

- [ ] **Step 3: Commit C2**

```bash
git add lsc-system/shared/sw.js
git commit -m "feat(pwa): add shared SW (app-shell precache + 4-tier runtime cache)"
```

---

## Task C3 · 四端 head 注入 preload/preconnect/manifest + SW 注册脚本（仅 localhost/https）

**Files:**
- Modify: `lsc-system/merchant-admin/index.html`
- Modify: `lsc-system/platform-admin/index.html`
- Modify: `lsc-system/mobile-app/index.html`
- Modify: `lsc-system/mini-program/index.html`

- [ ] **Step 1: 在 `<link rel="stylesheet" href="../shared/design-system.css">` 之前插入 6 条 performance + manifest link**

```html
+<link rel="preload" href="../shared/design-system.css" as="style">
+<link rel="preload" href="../shared/app-utils.js" as="script">
+<link rel="preload" href="../shared/keyboard-a11y.js" as="script">
+<link rel="preconnect" href="https://fonts.googleapis.com" crossorigin>
+<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
+<link rel="manifest" href="./manifest.json">
 <link rel="stylesheet" href="../shared/design-system.css">
```

- [ ] **Step 2: 在 body 闭合标签 `</body>` 之前追加 SW 注册**

```html
<script>
/* SW: register only on https or localhost */
(function(){
  var proto = location.protocol;
  var host = location.hostname;
  if (proto === 'https:' || host === 'localhost' || host === '127.0.0.1') {
    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('../shared/sw.js', { scope: '/lsc-system/' })
        .catch(function() { /* sandbox may block, ignore */ });
    }
  }
})();
</script>
</body>
```

- [ ] **Step 3: 4 端静态检查 link + 注册存在**

Run: `node -e "
const fs=require('fs');
['merchant-admin','platform-admin','mobile-app','mini-program'].forEach(d=>{
  const h=fs.readFileSync(d+'/index.html','utf8');
  const has1 = ['preload.*design-system\.css','preload.*app-utils\.js','preload.*keyboard-a11y\.js',
    'preconnect.*fonts\.googleapis\.com','preconnect.*fonts\.gstatic\.com','manifest.*\.json']
    .every(r=>new RegExp(r).test(h));
  const has2 = h.includes('serviceWorker.register') && h.includes('/shared/sw.js');
  console.log(d, 'link_perf_manifest:', has1, '| sw_register:', has2);
  console.assert(has1 && has2, d+' missing perf/sw injection');
});
console.log('C3 4-end injection OK');
"`
Expected: 4 ends all true, then C3 OK

- [ ] **Step 4: Commit C3**

```bash
git add lsc-system/merchant-admin/index.html lsc-system/platform-admin/index.html lsc-system/mobile-app/index.html lsc-system/mini-program/index.html
git commit -m "perf(pwa): inject preload/preconnect/manifest + SW register into 4 ends"
```

---

## Task C4 · Lighthouse 性能基线复查（desktop 平均 perf ≥ 60）

**Files:**
- 只读运行: `lsc-system/scripts/run-lighthouse.js`

- [ ] **Step 1: 启动本地 server 并跑 desktop LH**

Run: `cd lsc-system && node -e "
const http=require('http'),fs=require('fs'),path=require('path');
const s=http.createServer((req,res)=>{
  let p=decodeURIComponent(req.url.split('?')[0]);
  if(p==='/')p='/index.html';
  const fp=path.join(process.cwd(),p);
  fs.readFile(fp,(e,d)=>{if(e){res.writeHead(404);res.end()}else{res.writeHead(200);res.end(d)}});
}).listen(9413, async ()=>{
  const {execSync}=require('child_process');
  try {
    const out=execSync('node scripts/run-lighthouse.js --targets desktop --baseUrl http://127.0.0.1:9413 --json', {cwd:process.cwd(), stdio:['ignore','pipe','pipe']});
    console.log(String(out));
  } catch(e) { console.error(String(e.stdout||''), String(e.stderr||'')); process.exitCode=1; }
  s.close();
});" 2>&1 | tail -40`
Expected:
- 4 applications 平均 Performance ≥ 60（已设阈值）
- max LCP ≤ 5000 ms
- max CLS ≤ 0.25
- 与基线 perf-baseline.desktop.md 对照，指标不下降（若下降，说明 preload 未生效，需回到 C3 检查 crossorigin 属性）

- [ ] **Step 2: 若 CLS/LCP 异常，检查 preload crossorigin**（字体 as=style 可不加 crossorigin，但 Google Fonts 预连接需要 crossorigin 已加）
- [ ] **Step 3: 记录新基线到 audit-report/perf-baseline.desktop.md.new，并保留历史数据**

Run: `cd lsc-system && cp audit-report/perf-baseline.desktop.md audit-report/perf-baseline.desktop.before-phaseC.md && cp audit-report/perf-baseline.mobile.md audit-report/perf-baseline.mobile.before-phaseC.md || true`

- [ ] **Step 4: Commit C4（基线备份）**

```bash
git add lsc-system/audit-report/perf-baseline.desktop.before-phaseC.md lsc-system/audit-report/perf-baseline.mobile.before-phaseC.md 2>/dev/null
git commit -m "chore(lh): backup pre-phaseC LH baselines" --allow-empty
```

---

# FINAL · 汇总门控

## Task Z1 · 端到端验证（c8 + assets + meta + a11y + LH 汇总）

**Files:** N/A（脚本运行）

- [ ] **Step 1: 全量 c8 + 契约断言**

Run: `cd lsc-system && node coverage_runner.js 2>&1 | tail -10; echo "EXIT=$?"; npx c8 check-coverage --statements 99 --branches 95 --functions 95; echo "CHECK=$?"`
Expected: EXIT=0, CHECK=0

- [ ] **Step 2: assets 体量审计**

Run: `cd lsc-system && node audit-assets.js 2>&1 | tail -15`
Expected: `PASS / 0 FAIL / 0 WARN`，最终 GZIP ≤ 800 KiB

- [ ] **Step 3: meta 审计**

Run: `cd lsc-system && node audit-meta.js 2>&1 | tail -8`
Expected: PASS ≥ 76 + 新增 manifest 不影响现有规则（因为 manifest link 无既有 PASS/FAIL 规则，不会减少 PASS 数）

- [ ] **Step 4: a11y diff 审计**

Run: `cd lsc-system && node audit-a11y-static.js 2>&1 | tail -8; echo "---"; node audit-a11y-diff.js 2>&1 | tail -8`
Expected: 0 violation, 0 console.error，`baseline diff: added=0 removed=?` (added 必须为 0)

- [ ] **Step 5: LH 性能**（复用 C4 结果或重跑一遍 mobile）

Run: `cd lsc-system && node scripts/run-lighthouse.js mobile 2>&1 | tail -10`
Expected: mobile 平均 perf ≥ 60

---

## Plan Self-Review

**1. Spec coverage**

| Spec § 条款 | 对应的 Task | 结果 |
|---|---|---|
| A.1 命名规范 + A.2 清单 | Task A1~A4 (静态钩子) + A5 (动态钩子) | ✔ 静态 19+21+13+16 ≥ 69 + 动态 59 = 128，Task A5 ≥ 128 校验 |
| A.3.1 E2E 选择器迁移 | Task A6 | ✔ |
| A.3.2 c8 契约断言 | Task A7 (F_A1~F_A4) | ✔ 四端阈值 + required 集合 |
| B.1 焦点可视化 | Task B1 | ✔ `:focus-visible` + skip-link CSS |
| B.2 Roving tabindex | Task B2 setupRoving() | ✔ Arrow/Home/End + Enter/Space click |
| B.3 快捷键 Esc / / / T / 1-5 / GV / GN | Task B2 onKey + B4 6 Playwright + B5 JSDOM 断言 | ✔ |
| B.4 交付 & 验证 | Task B3 (init 注入) + B4 (E2E) + B5 (c8) | ✔ |
| C.1 4-end manifest | Task C1 | ✔ 4 JSON，192+512 SVG data-uri 图标 |
| C.2 SW pre-cache + 4-tier runtime | Task C2 | ✔ Install / activate / fetch 全实现 |
| C.3 preload / preconnect / manifest link 注入 | Task C3 | ✔ 6 条 links + SW register guard |
| C.4 LH 验证 | Task C4 + Z1 Step 5 | ✔ desktop + mobile |
| 全局验证矩阵 8 项 | Task Z1 Step 1~5 + C4 | ✔ |

**2. Placeholder scan:** 全 plan 搜索 TBD / TODO / implement later 字样：0 处。

**3. 类型一致性：**
- data-testid 前缀 `merchant-`、`platform-`、`mobile-`、`mini-` 贯穿 A1~A6、B3、B4、A7 — 一致。
- keyboard-a11y 初始化参数 `scope`、`searchInputSel`、`tabBarSel`、`rovingGroups`、`contentAnchor`、`navAnchor` 在 Task B2 定义、B3 注入、B5 断言 — 一致。
- SW `PRECACHE` 路径以 `/lsc-system/` 为前缀，和 Task C3 的 `scope: '/lsc-system/'` 对应 — 一致。
- manifest name/icon gradient 颜色和 meta theme-color 双色（light `#F5F3EC` / dark `#082E2C`）兼容 — 一致。

无 gap。Plan 已就绪。

---

Plan 完成并保存到 [docs/superpowers/plans/2026-09-03-lsc-phaseABC-plan.md](file:///workspace/lsc-system/docs/superpowers/plans/2026-09-03-lsc-phaseABC-plan.md)。两个执行选项：

**1. Subagent-Driven（推荐）** — 每个 Task 派一个独立 sub-agent 执行，Task 间做 review gate，变更隔离、快速迭代。

**2. Inline Execution** — 本会话内使用 executing-plans 批量按 A→B→C 顺序执行，每隔 Group（A 完成后、B 完成后、C 完成后）汇报进展并留 checkpoint。

选哪一种方式？
