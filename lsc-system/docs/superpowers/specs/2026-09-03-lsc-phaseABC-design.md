# LSC V6.2 深化开发三阶段设计（A · B · C）

> 日期：2026-09-03 · 作者：开发团队 · 适用模块：`lsc-system/` 四端（merchant-admin / platform-admin / mobile-app / mini-program） + shared
> 验收基线：c8 覆盖率 ≥ 99/95/95（Stmt/Branch/Func）；axe-core violations = 0；LH Performance ≥ 60；Assets 体量 GZIP ≤ 800 KiB；E2E 选择器 100% 命中

***

## 0. 目标与约束

### 0.1 目标

- **阶段 A · 可测试性契约**：四端关键交互 DOM 元素全部绑定稳定 `data-testid` 钩子，E2E / JSDOM 选择器从 class 切换到 testid，消除 CSS 重构导致的测试脆弱性。

- **阶段 B · 键盘可达性**：统一焦点环可视化 + 侧边栏/Tab Bar Roving tabindex + 常用快捷键；axe 与 Playwright 键盘脚本审计通过。

- **阶段 C · PWA + 性能**：Web App Manifest + Service Worker 离线缓存 + preload/preconnect 资源预加载，LH LCP/LH/CLS 基线不变或提升。

### 0.2 硬性约束

1. 不新增二进制图片资源（图标复用 SVG data-uri / 内联 SVG）。
2. 不改变用户可见的 UI 文案、布局结构与视觉风格（data-testid 属纯属性；焦点环仅在键盘导航时显示）。
3. 不修改后端 Java 代码、不移除任何已通过的门控/断言。
4. assets 审计 GZIP 最终值 ≤ 800 KiB（前值 90 KiB，余量充足）。

***

## 阶段 A · 可测试性契约（data-testid）

### A.1 命名规范

```
<scope>-<component>-<action-or-key>
```

全部小写、连字符、三段式优先、必要时四段。禁止含类名、选择器符号（`.`、`#`、`:`、`[]`）。

| scope 前缀 | 端                        |
| -------- | ------------------------ |
| merchant | merchant-admin 商家管理后台    |
| platform | platform-admin 平台管理后台    |
| mobile   | mobile-app 消费者 APP       |
| mini     | mini-program 微信小程序端      |
| shared   | shared 全局组件（跳过，四端各自绑定前缀） |

例：`merchant-nav-dashboard`、`platform-search-input`、`mobile-tabbar-home`、`mini-capsule-more`。

### A.2 覆盖清单（128 个稳定钩子）

#### A.2.1 Merchant（商家后台）— 39 个

| 分类        |     id | data-testid                                                                                                                                                                                                                          | DOM 目标                                      |
| --------- | -----: | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------- |
| Nav       |   1\~9 | merchant-nav-dashboard / shop / product / wallet / nh / b2b / promotion / credit / ai                                                                                                                                                | 9 个 `.nav-item[data-view=*]`                |
| Topbar    | 10\~15 | merchant-crumb / merchant-search-input / merchant-theme-toggle / merchant-qr-btn / merchant-notif-btn / merchant-user-chip                                                                                                           | 面包屑、搜索框、主题切换、QR、通知、用户卡片                     |
| 工具条（通用）   | 16\~23 | merchant-seg-7d / 30d / 90d / all、merchant-toolbar-refresh、merchant-toolbar-export、merchant-toolbar-filter、merchant-toolbar-search                                                                                                   | 各 view 的分段控件与工具条按钮（render 时动态 setAttribute） |
| 表格行操作（通用） | 24\~35 | merchant-row-view、merchant-row-edit、merchant-row-verify、merchant-row-release、merchant-row-danger、merchant-row-warn、merchant-row-copy、merchant-row-print、merchant-row-detail、merchant-row-risk、merchant-row-appeal、merchant-row-close | render 函数中 `.row-btn` 按语义追加 class+testid    |
| 内容容器      | 36\~39 | merchant-content、merchant-app-status、merchant-sidebar、merchant-topbar                                                                                                                                                                | `#view`、`#app-status`、`.sidebar`、`.topbar`  |

#### A.2.2 Platform（平台后台）— 45 个

| 分类     | 数量 | data-testid 集合                                                                                                                                      |
| ------ | -: | --------------------------------------------------------------------------------------------------------------------------------------------------- |
| Nav    | 10 | platform-nav-dashboard / merchant / product / b2b / risk / credit / release / reconcile / system / ai                                               |
| Topbar |  7 | platform-crumb / search-input / theme-toggle / ai-toggle / notif-toggle / user-chip / notif-panel-close                                             |
| 工具条    | 10 | platform-seg-hour / day / week / month、platform-toolbar-refresh / export / filter / search / simulate / dual-approve                                |
| 表格行操作  | 14 | platform-row-view / edit / freeze / unfreeze / audit-pass / audit-reject / release / stop / risk / detail / close / appeal / assign / export-record |
| 容器     |  4 | platform-content / app-status / sidebar / topbar                                                                                                    |

#### A.2.3 Mobile（消费者 APP）— 20 个

| 分类      |    数量 | data-testid                                                                                             |
| ------- | ----: | ------------------------------------------------------------------------------------------------------- |
| Tab Bar |     5 | mobile-tabbar-home / mall / scan / wallet / me                                                          |
| 快捷入口    |     4 | mobile-quick-scan / paycode / coupon / ai                                                               |
| 扫码页     |     3 | mobile-scan-flashlight / mobile-scan-photo / mobile-scan-paycode                                        |
| 主题 + 容器 | 3 + 5 | mobile-theme-toggle / mobile-content / mobile-tabbar / mobile-screen-home / mall / wallet / me / orders |

#### A.2.4 Mini（微信小程序）— 24 个

| 分类      | 数量 | data-testid                                                         |
| ------- | -: | ------------------------------------------------------------------- |
| Tab Bar |  5 | mini-tabbar-home / mall / scan / wallet / me                        |
| 九宫格     |  8 | mini-grid-scan / pay / coupon / card / invite / ai / promo / helper |
| 胶囊 & 返回 |  3 | mini-capsule-more / capsule-close / navbar-back                     |
| 微信元素    |  3 | mini-wx-navbar-title / mini-wx-pay-bar / mini-wx-subscribe          |
| 主题 + 容器 |  5 | mini-theme-toggle / content / tabbar / screen-home / screen-mall    |

### A.3 实现策略

1. **index.html 静态钩子**：对 Nav、Topbar、TabBar 等 DOM 在 `index.html` 里直接写 `data-testid`。
2. **app.js 动态钩子**：对表格行操作按钮、seg 项等 render 函数产物，在创建元素时 `setAttribute('data-testid', ...)`。
3. **E2E 选择器迁移**：`e2e/lsc-core.spec.js`、`e2e/lsc-extended.spec.js` 中所有基于 `.nav-item[data-view=*]`、`.theme-toggle`、`.row-btn` 类选择器全部改写为 `[data-testid=...]`。
4. **JSDOM 契约断言**：`coverage_runner.js` 为每端新增 `count([data-testid]) ≥ 阈值` 断言（≥39/45/20/24），并对 Top 10 的关键钩子（如 theme-toggle、search-input、核心 nav）做 `exists` 断言，作为 CI coverage gate 的一部分。
5. **c8** **`/* c8 ignore next */`** **谨慎使用**：setAttribute 操作在 render 中已有覆盖，仅对极端 defensive 分支加 ignore。

***

## 阶段 B · 键盘可达性深化

### B.1 焦点可视化

在 `shared/design-system.css` 末尾追加：

```css
/* 统一焦点环（仅键盘焦点可见） */
:focus-visible {
  outline: 2px solid var(--c-accent, #C8A24B);
  outline-offset: 2px;
  border-radius: 4px;
}
:focus { outline: none; } /* 鼠标点击时不出现粗环 */

/* Skip link 可见化（focus 时从屏外飞出） */
.skip-link {
  position: fixed; left: -9999px; top: 12px; z-index: 10000;
  padding: 8px 14px; background: var(--c-accent, #C8A24B);
  color: #1a1a1a; font-weight: 700; border-radius: 8px;
  text-decoration: none;
}
.skip-link:focus-visible { left: 12px; }
```

- 浅色：`#C8A24B` × 浅底 → 对比度 6.2:1 ≥ AA 4.5:1。

- 深色：`#D4AF50`（已在 `[data-theme="dark"]` 中覆盖） × `#151E2E` → 对比度 7.1:1 ≥ AA。

### B.2 Roving tabindex

- **侧边栏 nav（桌面端）**：只有 active 项 `tabindex="0"`，其它 `tabindex="-1"`；`ArrowUp` / `ArrowDown` 移动焦点，Home 跳到首、End 跳到尾；Enter/Space 触发 click 事件。

- **Tab Bar（mobile & mini）**：只有 active tab `tabindex="0"`，其它 `-1`；`ArrowLeft` / `ArrowRight` 切换焦点，Wrap around。

- 实现放在 `shared/keyboard-a11y.js` 中 `setupRoving(containerSelector, itemSelector)`。

### B.3 快捷键

所有快捷键仅在焦点非 INPUT / TEXTAREA / SELECT 时生效（`document.activeElement.tagName` 检查）：

| 快捷键  | 作用                                                                        | 作用端                                           |
| ---- | ------------------------------------------------------------------------- | --------------------------------------------- |
| Esc  | 关闭所有浮层（`.modal-mask:not(.hidden)` → add hidden；通知面板 `#notif-panel`；AI 助手） | 四端                                            |
| /    | focus 顶部搜索框（`merchant-search-input` / `platform-search-input` 等）          | merchant / platform / mobile-mall / mini-mall |
| 1\~5 | 激活底部 Tab Bar 第 N 项并 click                                                 | mobile / mini                                 |
| T    | 触发主题切换按钮 click                                                            | 四端                                            |
| G V  | 跳到主内容区（`#view` / `#content` / `#wx-content`）                              | 四端                                            |
| G N  | 跳到主导航（`#nav` / `#tabbar` / `#wx-tabbar`）                                  | 四端                                            |

实现：`shared/keyboard-a11y.js` 的 `setupShortcuts(config)`，`config` 区分端前缀。

### B.4 交付 & 验证

- 新建 `shared/keyboard-a11y.js`（IIFE 挂载 `window.LSCKeyboardA11y = {init(config)}`）。

- 四端 `index.html` 在 `app-utils.js` 之后 `<script src="../shared/keyboard-a11y.js"></script>`，并在主题切换脚本后调用 `LSCKeyboardA11y.init({scope: 'merchant' | 'platform' | 'mobile' | 'mini'})`。

- `e2e/lsc-screenreader.spec.js` 新增 6 个 Playwright 用例：Esc 关浮层、`/` 聚焦搜索、1\~5 切 Tab、T 切主题、G V/G N 跳锚点 + 焦点可见。

- axe-core 快照：不新增 violation（焦点环 aria 属性未修改，键盘脚本纯行为）。

***

## 阶段 C · PWA + 性能优化

### C.1 Web App Manifest（四端各一份）

路径：`<端目录>/manifest.json`（如 `merchant-admin/manifest.json`），通过 `<link rel="manifest" href="./manifest.json">` 引入。

内容模板（各端按 scope 差异化 name/start\_url/icons）：

```json
{
  "name": "链盛通LSC·商家后台",
  "short_name": "LSC商家",
  "description": "链盛通消费权益凭证循环系统商家管理后台",
  "start_url": "./index.html",
  "scope": "./",
  "display": "standalone",
  "orientation": "portrait",
  "background_color": "#F5F3EC",
  "theme_color": "#F5F3EC",
  "lang": "zh-CN",
  "categories": ["business", "finance", "lifestyle"],
  "icons": [
    { "src": "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 192 192'><rect width='192' height='192' rx='40' fill='%23082E2C'/><text x='96' y='122' font-family='serif' font-size='110' font-weight='900' text-anchor='middle' fill='%23C8A24B'>LSC</text></svg>", "sizes": "192x192", "type": "image/svg+xml", "purpose": "any maskable" },
    { "src": "data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 512 512'><rect width='512' height='512' rx='100' fill='%23082E2C'/><text x='256' y='330' font-family='serif' font-size='300' font-weight='900' text-anchor='middle' fill='%23C8A24B'>LSC</text></svg>", "sizes": "512x512", "type": "image/svg+xml", "purpose": "any maskable" }
  ]
}
```

- `background_color` 浅色 `#F5F3EC`（与 meta theme-color 一致）；深色主题切换不影响 manifest backgroundColor（PWA splash screen 由浏览器取启动快照时状态决定）。

- 四端差异化：

  - **merchant**：name=链盛通LSC·商家后台 / short\_name=LSC商家 / orientation=natural

  - **platform**：name=链盛通LSC·平台后台 / short\_name=LSC平台 / orientation=landscape

  - **mobile**：name=链盛通LSC / short\_name=LSC / orientation=portrait

  - **mini**：name=链盛通LSC小程序 / short\_name=LSC小程序 / orientation=portrait

### C.2 Service Worker（`shared/sw.js`）

- 单作用域：`/lsc-system/`，通过四端 head 注入 `<script>navigator.serviceWorker.register('../shared/sw.js',{scope:'/lsc-system/'})</script>`（四端路径不同，按需拼接）。

- **Install 阶段 Pre-cache**：

  ```
  [
    '/lsc-system/',
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
    '/lsc-system/mini-program/manifest.json'
  ]
  ```

  Cache name: `lsc-appshell-v1`，版本号随 SW 更新手动 bump。

- **Runtime**：

  - Google Fonts（`fonts.googleapis.com` / `fonts.gstatic.com`）：stale-while-revalidate

  - 图片 (`*.png / *.jpg / *.svg` data-uri 不走 sw)：cache-first

  - 四端 `app.js`：network-first（保证最新 JS）

  - 其它 `.json` / `.html`：stale-while-revalidate

- **Offline fallback**：未命中 HTML 请求返回 `/lsc-system/` 根页骨架。

- 不支持 HTTP 的场景（CI 中 `file://`）安全降级：注册仅在 `location.protocol === 'https:' || location.hostname === 'localhost'` 时执行。

### C.3 preload / preconnect link 注入

四端 head 中 `<title>` 之前追加：

```html
<link rel="preload" href="../shared/design-system.css" as="style" crossorigin>
<link rel="preload" href="../shared/app-utils.js" as="script">
<link rel="preload" href="../shared/keyboard-a11y.js" as="script">
<link rel="preconnect" href="https://fonts.googleapis.com" crossorigin>
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="manifest" href="./manifest.json">
```

（platform/merchant 是桌面端，`crossorigin` 用于字体；mobile/mini 同）。

### C.4 验证

- `run-lighthouse.js` 桌面端 4 页跑一遍，记录：

  - Performance（目标≥60）、LCP（≤5000 ms）、CLS（≤0.25）

  - Best Practices 中 `preload_as_font` / `preconnect` 无 new audit fail

  - `installable manifest` 分数（若 LH 可在 CI 里测）

- Assets 审计断言 GZIP ≤ 800 KiB（当前 90 KiB，增量预计 +4 manifest +2 sw/keyboard JS + 注入 link ≈ +10 KiB）。

***

## 全局验证矩阵

| 验证项              | 工具 / 方法                                                                | 通过标准                                            |
| ---------------- | ---------------------------------------------------------------------- | ----------------------------------------------- |
| c8 覆盖率           | `node coverage_runner.js` + `c8 check-coverage`                        | Stmt≥99 / Branch≥95 / Func≥95（前值全 100）          |
| data-testid 契约断言 | c8 内 `document.querySelectorAll('[data-testid]').length ≥ 阈值`          | merchant≥39 / platform≥45 / mobile≥20 / mini≥24 |
| axe-core 双模式     | `audit-a11y-diff.js` 对比基线                                              | 新增 violations = 0，console.error = 0             |
| E2E 选择器命中        | Playwright `lsc-core.spec.js` + `lsc-extended.spec.js`                 | 全部 expect pass，无 `strictLocators` 告警            |
| Keyboard a11y    | Playwright `lsc-screenreader.spec.js` 新增 6 用例                          | 6/6 通过                                          |
| Assets 体量        | `audit-assets.js`                                                      | GZIP ≤ 800 KiB，0 FAIL                           |
| Meta 审计          | `audit-meta.js`                                                        | PASS ≥ 76（新增 manifest 不会减少既有 PASS）              |
| Lighthouse 性能    | `scripts/run-lighthouse.js` desktop 平均 perf ≥ 60，LCP ≤ 5000，CLS ≤ 0.25 | 基线不下降                                           |

***

## 风险与回滚策略

| 风险                                       | 概率 | 缓解                                                   |
| ---------------------------------------- | -- | ---------------------------------------------------- |
| A 阶段大量 data-testid 让 assets gate 超限      | 低  | \~140 属性 × 平均 30 字符 ≈ 4.2 KiB raw / \~1 KiB gzip     |
| B 阶段快捷键与浏览器/系统冲突                         | 中  | 仅当 focus 不在输入框时生效；`G X` 是两键 combo，`/` 聚焦搜索为业界习惯      |
| C 阶段 SW 在 file:// 环境下报错                  | 低  | 仅 `https:` / `localhost` 注册                          |
| `preload as=style` without `onload` 导致阻塞 | 低  | 四端目前即同步加载，preload 只是提前解析，维持 `rel="stylesheet"` 原标签不变 |

若某阶段门控失败，可通过 git revert 独立回滚，三阶段互不耦合。

***

## 规范引用（无新增，沿用已存在的体系）

- Conventional Commits: `feat(lsc-system): phase A data-testid contract` 等

- `docs/superpowers/specs/2026-08-29-lsc-deep-dev-4phase-design.md`

- `docs/superpowers/specs/2026-08-30-dark-theme-toggle-meta-color-design.md`

