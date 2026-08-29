# 链盛通 LSC V6.2-AI 深度开发四阶段设计

- 生成时间: 2026-08-29
- 策略: 顺序推进（覆盖率 → UI 切换 → CI 门控 → E2E 扩展）
- 当前基线: all:hard exit=0，语句覆盖率 98.44%、分支 90.04%、函数 93.23%，A11y 16/16 violations=0，E2E 8/8 PASS，Meta/Assets 审计 100 分

---

## 总体架构

```
阶段 1  覆盖率追击      coverage_runner.js 补 10 个分支 → c8 开启 checkCoverage 门控
阶段 2  深色模式 UI     4 应用追加 themeToggle 按钮 + audit-a11y-baseline.js 强制 emulator colorScheme 再 axe-core
阶段 3  CI 门控强化     a11y-audit.yml / .gitlab-ci.yml 追加 coverage / 16 快照 / assets / meta 严格阈值
阶段 4  E2E 扩展到 15   lsc-extended.spec.js 新增 7 场景 (I-O)，覆盖核销/扫码/购物车/分享/主题切换
```

---

## 阶段 1：覆盖率追击（语句≥99%、分支≥95%、函数≥95%）

### 目标
精准覆盖 coverage-gap.md Top15 中 2-7 行级别的剩余分支，全部通过在 `coverage_runner.js` 新增断言实现，**不修改任何生产代码**。

### 待覆盖热点清单与补测方案

| # | 应用 | 函数/分支 | 行 | 未覆盖原因 | 补测方案 |
|---|---|---|---|---|---|
| 1 | platform-admin | `navTo` keydown 回调 | L23-28 | addEventListener('keydown') 从未触发 | 定位 `.nav-item[data-view=merchant]` → dispatch KeyboardEvent('keydown', { key:'Enter', bubbles:true }) + Space 键各 1 次 |
| 2 | merchant-admin | `navTo` keydown 回调 | L24-28 | 同上 | 定位 `#nav .nav-item[data-view=product]` → dispatch Enter + Space keydown |
| 3 | mobile-app | `showScreen` keydown 回调 | L40-44 | tabbar keydown 监听回调未触发 | 定位 `.tab-item[data-screen=wallet]` → dispatch Enter keydown |
| 4 | merchant-admin | `apply` | L449-464 | 核销申请 modal 的 onConfirm 回调 | 直接 `w.apply()`，检查 resultModal 渲染（#global-modal 存在 + 成功文案） |
| 5 | merchant-admin | `window.calcNH` | L718-726 | 金额/比例联动计算器未调用 | 直接 `w.calcNH(1000, 0.0072)`，验证规则：返回值结构含 `{ releaseLSC, validDays }`，`releaseLSC = rmb * k`（与 merchant-admin/app.js L718 实际公式一致，若返回 number 则直接按源码 return 校验） |
| 6 | merchant-admin | `showB2BDetail` verify timer ≥100% 分支 | L1017-1022 | setInterval 回调跑不到 p≥100 | 打开 B2B20260827009 (verify=0) → 若 setInterval callback 不可直接取则用替代策略：临时 mock `w.setInterval` 在 showB2BDetail 调用前捕获 fn 引用，然后手动调用 15 次（每次 +8%，15 次=120%）→ 断言 `p>=100` 时 `_verifyTimer` 已 clearInterval + #global-modal 存在 resultModal 文案 |
| 7 | platform-admin | `onClose` | L1443-1453 | dualApproval X 按钮回调 | showCircuitBreaker → 点击 `#gm-close` 触发 mask click → onClose 清理签名 |
| 8 | platform-admin | `setView` curView===nextView 短路 + notifCount | L1326-1352 | 未走短路分支 | 先 `navTo('dashboard')` 再 `navTo('dashboard')` → 断言不重复渲染 + `#notif-count` 正确显示 |
| 9 | platform-admin | `window.updateSig` 双签名 enable + 同签名校验末段 | L1423-1442 | 同签名校验 disabled 逻辑 | `updateSig('sig1','A'); updateSig('sig2','A')` → 断言 `#dual-confirm` disabled=true |
| 10 | platform-admin | `renderNotifList` 空列表 | L1353-1375 | notifications=[] 分支 | 临时 `MOCK.notifications = []` → `renderNotifList()` → 断言空状态 DOM 存在 → 恢复原 MOCK |

### c8 门控配置
修改 `package.json` 的 c8 块：
```json
"c8": {
  "checkCoverage": true,
  "lines": 99,
  "branches": 95,
  "functions": 95,
  "statements": 99,
  "reportDir": "coverage",
  "extension": [".js"],
  "sourceMap": true,
  "instrument": true
}
```
`npm run coverage` 未达标 → c8 exit=1，阻断后续步骤。

---

## 阶段 2：深色模式切换 UI 按钮 + prefers-color-scheme 强制深色 axe-core 重跑

### 2.1 4 应用 themeToggle 按钮

**共同模式**：
- id 统一：`themeToggle`
- `data-state` 属性：`auto` → `light` → `dark` 循环
- `localStorage.KEY` 按应用区分：`lsc-platform-theme` / `lsc-merchant-theme` / `lsc-mobile-theme` / `lsc-mini-theme`
- 图标：auto=auto 图标 / light=sun / dark=moon（复用现有 ICONS）
- 每个 app.js 追加 `setupThemeToggle()` 函数（15-25 行），在启动时调用一次

**各应用按钮落点**：

| 应用 | 容器 / 位置 | 样式 |
|---|---|---|
| platform-admin | 顶部栏 `#topbar-right`（通知图标左侧），若不存在则在 `#topbar` 末尾追加 | `btn btn-outline btn-sm`，margin-left: auto 推到右侧 |
| merchant-admin | 面包屑右侧 `#topbar-extra`（和 platform 对齐） | `btn btn-outline btn-sm` |
| mobile-app | `position:fixed;top:16px;right:16px;z-index:40`，低于 tabbar(z-index:50) | 圆形按钮 `width:36px;height:36px;border-radius:50%`，半透明背景 + 图标 |
| mini-program | navbar 内，返回按钮 (.wx-back) 右侧，`#wx-navbar` 末尾追加 | 小圆形 `width:28px;height:28px;border-radius:50%`，navbar inline 布局 |

**setupThemeToggle() 逻辑（伪代码）**：
```
btn = document.getElementById('themeToggle') || create()
state = localStorage[KEY] || 'auto'
apply(state)  // 设置 data-theme
btn.onclick = () => {
  state = state==='auto'?'light':state==='light'?'dark':'auto'
  localStorage[KEY] = state
  apply(state)
  updateBtnIcon(state)
}
```
注：`data-theme='dark'` 覆盖 CSS 变量，`data-theme='auto'` 依赖 `prefers-color-scheme`。

### 2.2 prefers-color-scheme:dark 强制渲染 + axe-core 重跑

修改 `audit-a11y-baseline.js` 中 16 快照循环：
- **对 [light] 快照**：`await page.emulateMedia({ colorScheme: 'light' })` + `await page.reload()` → 跑 axe-core
- **对 [dark] 快照**：`await page.emulateMedia({ colorScheme: 'dark' })` + `await page.reload()` → 跑 axe-core

确保 `prefers-color-scheme` 媒体查询驱动的 CSS token 也在实际渲染视口下被校验。完成后再次运行 `audit:a11y:ci`，仍需 16/16 violations=0，否则回滚并修复深色模式 token 对比度问题。

---

## 阶段 3：CI 门控强化

### 3.1 GitHub Actions (`.github/workflows/a11y-audit.yml`)

在现有 workflow 基础上**追加 job steps**（保持同一个 job，顺序执行）：

| 顺序 | Step | 命令 | 失败条件 |
|---|---|---|---|
| 1 | coverage + c8 阈值 | `npx c8 --check-coverage --lines 99 --branches 95 --functions 95 --statements 99 node coverage_runner.js` | c8 未达标 exit=1 |
| 2 | 16 快照严格模式 + baseline diff | `node audit-a11y-baseline.js --strict --diff audit-report/a11y-baseline.baseline.json` | 新 violation 或 baseline 差异 exit=1 |
| 3 | assets 严格阈值 | `node audit-assets.js --strict --json audit-report/assets-audit.json --md audit-report/assets-audit.md` | 超阈值 exit=1 |
| 4 | meta 严格审计 | `node audit-meta.js --strict --json audit-report/meta-audit.json --md audit-report/meta-audit.md` | 缺必填项 exit=1 |

### 3.2 GitLab CI (`.gitlab-ci.yml`)

在现有 pipeline 后**追加 3 个独立 job**（stage: quality），`needs` 依赖 build，且全部需要 `exit=0` 才能进入后续 deploy stage：

- `coverage_gate`: `npm run coverage`（c8 checkCoverage 已在 package.json 开）
- `a11y_gate`: `npm run audit:a11y:ci`（baseline save 已在上游跑，这里严格比对）
- `assets_meta_gate`: `npm run audit:assets:ci && npm run audit:meta:ci`

---

## 阶段 4：E2E 扩展到 15 场景

在 `e2e/lsc-extended.spec.js` 中新增 7 个场景，复用现有 `describe / test` 模式和 `baseURL`, `viewport`, `storageState` 配置。

### 新增场景详情

| 场景 | 页面 | 视口 | 步骤 | 核心断言 |
|---|---|---|---|---|
| **I: 平台 themeToggle 三态循环** | platform-admin/index.html | 1440x900 | 1) 初始 localStorage.KEY 读取 → 2) 点击 1 次 → 3) 点击 2 次 → 4) 点击 3 次回到 auto | 每次点击 data-state 正确循环; data-theme 属性同步切换; localStorage 值写回正确 |
| **J: 商家核销 apply + calcNH** | merchant-admin/index.html | 1440x900 | 1) 进入 NH 视图 → 2) 填核销金额 500 → 3) 调 apply() → 4) 查看 resultModal | calcNH 返回值与公式一致; #global-modal 存在且含"核销已提交"文案 |
| **K: 平台 B2B verify timer 自动完成** | platform-admin/index.html | 1440x900 | 1) 进入 B2B 视图 → 2) 打开 verify=0 的订单 → 3) 等待 verify-bar 到 100% → 4) resultModal 出现 | #verify-bar 宽度从 0% 开始增长; 30 秒内 resultModal 渲染"AI核验完成" |
| **L: 移动端混合扫码支付 → paySuccess → wallet** | mobile-app/index.html | 360x740 | 1) simulateScan() 打开 modal → 2) 拖拽 hybrid 滑块到 50% → 3) 点击确认支付 → 4) paySuccess 替换内容 → 5) 点击"查看钱包"按钮 | hybrid-rmb 随拖拽变化 (¥100 → ¥50); #pay-final 更新; paySuccess 后 screen-wallet active |
| **M: 移动端商品 → addToCart** | mobile-app/index.html | 360x740 | 1) openProduct(0) → 2) 点击加入购物车 | screen-product active; #app-tip toast 出现且含"已加入购物车" |
| **N: 小程序 wxScanPay → wxPaySuccess → wallet** | mini-program/index.html | 360x740 | 1) wxScanPay() → 2) 点击确认支付 → 3) wxPaySuccess 成功页 → 4) 点击"查看钱包" | modal 渲染 #wx-pay-amt; wxPaySuccess 后 screen-wallet active |
| **O: 小程序 wxShare → 分享项 toast** | mini-program/index.html | 360x740 | 1) wxShare() → 2) 依次点击微信好友/朋友圈/收藏 | share modal 创建; 每次点击后 .wx-subscribe-tip toast 出现 1 次 |

### 复用现有基础设施
- 静态服务启动方式与 lsc-core.spec.js 一致
- 所有用例使用 `beforeEach` 导航到对应 URL
- 用 `await expect(page.locator(...)).toBeVisible()` 替代脆弱的 innerHTML 断言
- 拖拽操作使用 Playwright 原生 `dragTo()` / `mouse.move()` 而非 DOM 事件

---

## 成功标准（4 阶段全部达成后）

1. `npm run coverage` → c8 checkCoverage 通过: statements≥99%, branches≥95%, functions≥95%
2. `npm run audit:a11y:ci` → 16/16 violations=0 (含 prefers-color-scheme 双模式)
3. 4 应用页面: themeToggle 按钮均可见可点击,三态循环正确
4. GitHub PR: 任一 PR 未达标 coverage/16 快照/assets/meta 任一门控 → Actions 红叉阻断合并
5. GitLab CI: 任一 job 失败即阻断 deploy stage
6. `npm run e2e` → 15/15 Playwright 场景全部通过 (原 8 + 新增 7)
7. `npm run all:hard` → 全流水线 exit=0（仍是最终验收标准）
