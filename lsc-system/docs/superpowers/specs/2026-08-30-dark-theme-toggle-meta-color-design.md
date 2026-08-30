# 链盛通 LSC V6.2-AI：深色模式 UI 切换按钮完善 + meta theme-color 实施设计

* 生成时间: 2026-08-30
* 上游请求：用户在 2026-08-30 迭代开始时明确选择「深色模式UI切换按钮完善 + meta theme-color」作为本迭代优先级路线。
* 策略：最小化渐进改造（方案 A），不抽共享 ThemeController，不增 transition 动画/实时 system-follow，仅补缺口。
* 当前基线：语句 100% / 分支 97.22% / 函数 99.31% / Lines 100%；E2E 25/25 PASS；a11y:ci 24 快照 violations=0 + diff PASS。

***

## 一、现状审计（pre-design）

在 `/workspace/lsc-system/{platform-admin,merchant-admin,mobile-app,mini-program}/index.html` 中：

| 应用 | themeToggle 按钮（HTML） | 三态 SVG (sun/moon/auto) | apply/STATES IIFE 逻辑 | localStorage KEY | <meta name="theme-color"> | 定位问题 |
|------|---------|---------|--------|--------|--------|--------|
| platform-admin | ✅ id=themeToggle + class=theme-toggle，topbar-actions 内 | ✅ tt-sun/tt-moon/tt-auto + display 切换 | ✅ STATES=['auto','light','dark']；apply(mode) → data-theme + localStorage.write + btn.aria-label.write | lsc-platform-theme | ❌ 缺 | 无 |
| merchant-admin | ✅ 同上 | ✅ 同上 | ✅ 同上 | lsc-merchant-theme | ❌ 缺 | 无 |
| mobile-app | ✅ 绝对定位 top:58px right:12px z-index:30 圆按钮 | ✅ 同上 | ✅ 同上，apply(mode): dark→set data-theme=dark 其他 removeAttribute | lsc-mobile-theme | ❌ 缺 | ⚠ 绝对定位，切 screen 后可能被 #screen-home / #screen-wallet 的 overflow:hidden 遮挡 |
| mini-program | ✅ CSS 已声明 theme-toggle（左上避开胶囊位置） | ⚠ 未核 HTML/JS | ⚠ 未核 apply IIFE | lsc-mini-theme | ❌ 缺 | ⚠ 需核对 id=themeToggle 是否存在并挂了 click handler |

## 二、功能需求

### F1: 4 应用补 `<meta name="theme-color">` 双色
每个应用在 `<head>`（`<title>` 后）插入：
```html
<meta name="theme-color" media="(prefers-color-scheme: light)" content="#F5F3EC">
<meta name="theme-color" media="(prefers-color-scheme: dark)"  content="#082E2C">
```
语义：
- light 态 = `#F5F3EC` (纸米暖调 · 与 c-bg 品牌浅背景一致)
- dark  态 = `#082E2C` (青绿深调 · 与 `--c-bg-dark` 一致，见 design-system.css)
- 通过媒体查询实现系统偏好跟随，用户手动切换时由 JS 同步覆盖（见 F2）

### F2: apply(mode) 内同步 theme-color meta（含 auto fallback）
在现有 apply 函数最后追加同步逻辑（或抽出 applyMetaColor(mode) IIFE 内闭包函数）：

```
当 mode='dark' → 取所有 [name="theme-color"] meta，media 属性清空/置 all，content=#082E2C 统一覆盖
当 mode='light' → 同上，content=#F5F3EC 统一覆盖
当 mode='auto'  → 恢复默认 media=(prefers-color-scheme: ...) 两张 meta
```

实现策略（不改 DOM 节点数，避免闪烁）：IIFE 初次执行时保存两份 media 初始值 `savedMediaLight / savedMediaDark` 和对应 savedContentLight/Dark；apply 模式切换时：
- light/dark：把两张 meta 都去掉 media（或统一设为 `all`）并设置为同色
- auto：两张 meta 的 media/content 分别写回 savedMedia* + savedContent*

### F3: 移动端 themeToggle 定位 absolute → fixed
mobile-app/index.html 中 `.theme-toggle` 规则：
```css
.theme-toggle {
  position: absolute; top: 58px; right: 12px; z-index: 30;
}
```
改为：
```css
.theme-toggle {
  position: fixed; top: 58px; right: 12px; z-index: 9999;
}
```
保证用户从首页切到 wallet/scan/product 等 screen 后按钮仍在视口固定位置可点击。
配套：深模式适配从 @media 与 [data-theme] 继承即可，无需额外调整。

### F4: 场景 I E2E 扩展到 4 应用真·三态点击循环
当前 `appCases` 仅：
```
platform-admin (key lsc-platform-theme)
merchant-admin (key lsc-merchant-theme)
```
扩展为：
```
platform-admin (lsc-platform-theme)
merchant-admin (lsc-merchant-theme)
mobile-app (lsc-mobile-theme)
mini-program (lsc-mini-theme)
```
每项都断言：
- 按钮 visible 且 data-state ∈ {auto,light,dark}
- 连续 3 次点击，依次 state 合法；localStorage KEY 与 state 一致
- data-state=light 时 root [data-theme]=light；data-state=dark 时 root [data-theme]=dark 或跟随系统 (auto 时不设 data-theme)
- 第 3 次点击后至少遍历过 ≥2 种不同 state（避免卡死在 auto）

### F5: 小程序端 themeToggle JS 核验
若 mini-program/index.html 缺失 apply IIFE，补齐与 mobile-app 等价的：
- STATED = ['auto','light','dark']
- KEY = 'lsc-mini-theme'
- apply(mode): data-theme 写/删 + btn data-state + aria-label
- btn.addEventListener('click', ... )
- DOMContentLoaded 触发 apply(saved)

### F6: JSDOM 覆盖率补测
在 `coverage_runner.js` F-mobile-app / F-mini-program / F-platform-admin 内各加 1 个子用例：
1. `meta.theme-color` 初始两 meta 存在且有 media 查询
2. 点击 themeToggle → light/dark 时 meta 内容正确覆盖；切回 auto → media 属性复原
3. platform/merchant 端点击 3 次 → localStorage KEY 正确
4. 移动端 fixed 定位选择器在 DOM 上有正确 class（仅静态校验）

## 三、非目标

- **不**将 4 份 IIFE 合并成 shared/theme-controller.js（留待后续 B 方案独立迭代）
- **不**增加 `.theme-toggle` 颜色 CSS transition
- **不**增 `window.matchMedia('(prefers-color-scheme)').addEventListener` 实时跟随（auto 模式已由浏览器 prefers-color-scheme media query + meta media query 自然处理）
- **不**改 data-theme 取值方式：仍 `dark` 设置，其余 removeAttribute（与现有 audit-a11y-baseline colorScheme 脚本兼容）
- **不**处理浏览器 < 2020 的老兼容性（如 iOS 14 之前不支持 meta theme-color 双 media）—— 现代浏览器全部支持

## 四、风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| 切换 light/dark 后 theme-color meta 覆盖写法破坏 prefers-color-scheme media 原本逻辑（auto 回不去） | P1 高：auto 态系统色失效 | 在 IIFE 首次进入时存 初始 meta media 属性 + content，apply('auto') 原样写回 |
| mobile fixed 定位后与 status-bar / 小程序 Viewport 重叠 | P1 中：顶栏遮挡按钮 → 不可点击 | E2E 做 `toBeVisible` + `toBeEnabled` 双校验，不通过时调 top:58px → top:70px |
| mini-program 端 apply IIFE 若缺失导致 E2E 扩展失败 | P2 中：场景 I mobile/mini 分支立即报错 | F5 先行补齐 IIFE，F4 后跑 |
| JSDOM meta.getAttribute('media') 返回值大小写不同 | P3 低：断言失败 | 断言用 `.toLowerCase()` 归一化比较 |

## 五、验收标准（可量化）

1. **E2E 场景 I**：25+X → 当前 25 → 本轮目标 **≥26 全 PASS**，且 4 应用 appCases 每个应用至少经历过 light/dark 两种 data-state
2. **Coverage**：passed **≥624 failed=0**，Statements 100% / Branch ≥95% / Funcs ≥95% / Lines 100%（所有四项门控继续保持）
3. **a11y:ci**：violations=0，新增 meta.theme-color 作为 `<head>` 元素不影响 axe-core（非可视元素不会报 violations）
4. **手工 smoke**（由 E2E 间接证明）：
   - 4 应用任意应用 themeToggle 点击三次 → 每次 UI 图标在 tt-sun/tt-moon/tt-auto 之间切换可见
   - 任意应用切 dark → mobile Chrome DevTools 地址栏 `theme-color` 实时为 `#082E2C`
   - 任意应用切 light → Chrome DevTools `theme-color` 实时为 `#F5F3EC`
   - 任意应用切 auto → 两张 meta 的 media 分别为 `(prefers-color-scheme: light)` / `(prefers-color-scheme: dark)`（与初始 DOM 结构一致）

## 六、变更文件清单

| 文件 | 变更内容 |
|------|---------|
| platform-admin/index.html | <head> 加 theme-color 双 meta；apply() 加 meta 同步 |
| merchant-admin/index.html | 同上 |
| mobile-app/index.html | 同上 + `.theme-toggle { position:fixed; z-index:9999 }` |
| mini-program/index.html | 同上 + 若缺 apply IIFE 补齐 |
| e2e/lsc-extended.spec.js | 场景 I appCases 扩展 4 应用（+mobile/mini） |
| coverage_runner.js | F-platform / F-merchant / F-mobile / F-mini 各加 1~2 个 meta 分支断言 |
