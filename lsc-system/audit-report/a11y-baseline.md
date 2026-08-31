# 链盛通 LSC V6.2-AI · 可访问性 & 响应式基线审计 (快照 · Light + Dark)

> 生成于 **2026-08-31T03:23:45.929Z** · axe-core wcag2a+wcag2aa+best-practice · 基础 4 应用 × 2 视口 × 2 色方案=16 快照 + 8 扩展(档位+B2B 门控+处罚弹窗) · 合计 **24** 项快照

## 汇总表

| # | 应用 | 视口 | 配色 | 加载 | 违规(V) | 待核查(Inc) | 通过规则 | console.error | console.warn | 4xx/5xx | 无 alt 图 | 正文长度 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 平台管理后台 | 768×1024 | ☀️ light | ✅ | 0 | 1 | 40 | 0 | 0 | 0 | 0 | 1749 |
| 2 | 平台管理后台 | 768×1024 | 🌙 dark | ✅ | 0 | 1 | 40 | 0 | 0 | 0 | 0 | 1757 |
| 3 | 平台管理后台 | 1440×900 | ☀️ light | ✅ | 0 | 1 | 40 | 0 | 0 | 0 | 0 | 1856 |
| 4 | 平台管理后台 | 1440×900 | 🌙 dark | ✅ | 0 | 1 | 40 | 0 | 0 | 0 | 0 | 1883 |
| 5 | 商家管理后台 | 768×1024 | ☀️ light | ✅ | 0 | 1 | 42 | 0 | 0 | 0 | 0 | 950 |
| 6 | 商家管理后台 | 768×1024 | 🌙 dark | ✅ | 0 | 1 | 42 | 0 | 0 | 0 | 0 | 950 |
| 7 | 商家管理后台 | 1440×900 | ☀️ light | ✅ | 0 | 1 | 42 | 0 | 0 | 0 | 0 | 1101 |
| 8 | 商家管理后台 | 1440×900 | 🌙 dark | ✅ | 0 | 1 | 42 | 0 | 0 | 0 | 0 | 1102 |
| 9 | 移动端 APP | 360×740 | ☀️ light | ✅ | 0 | 1 | 34 | 0 | 0 | 0 | 0 | 528 |
| 10 | 移动端 APP | 360×740 | 🌙 dark | ✅ | 0 | 1 | 34 | 0 | 0 | 0 | 0 | 528 |
| 11 | 移动端 APP | 768×1024 | ☀️ light | ✅ | 0 | 1 | 33 | 0 | 0 | 0 | 0 | 528 |
| 12 | 移动端 APP | 768×1024 | 🌙 dark | ✅ | 0 | 1 | 33 | 0 | 0 | 0 | 0 | 528 |
| 13 | 微信小程序 | 360×740 | ☀️ light | ✅ | 0 | 1 | 34 | 0 | 0 | 0 | 0 | 422 |
| 14 | 微信小程序 | 360×740 | 🌙 dark | ✅ | 0 | 1 | 34 | 0 | 0 | 0 | 0 | 422 |
| 15 | 微信小程序 | 768×1024 | ☀️ light | ✅ | 0 | 1 | 33 | 0 | 0 | 0 | 0 | 422 |
| 16 | 微信小程序 | 768×1024 | 🌙 dark | ✅ | 0 | 1 | 33 | 0 | 0 | 0 | 0 | 422 |
| 17 | 商家管理后台 | 768×1024 | ☀️ light | ✅ | 0 | 1 | 42 | 0 | 0 | 0 | 0 | 722 |
| 18 | 商家管理后台 | 768×1024 | 🌙 dark | ✅ | 0 | 1 | 42 | 0 | 0 | 0 | 0 | 722 |
| 19 | 商家管理后台 | 768×1024 | ☀️ light | ✅ | 0 | 1 | 43 | 0 | 0 | 0 | 0 | 736 |
| 20 | 商家管理后台 | 768×1024 | 🌙 dark | ✅ | 0 | 1 | 43 | 0 | 0 | 0 | 0 | 736 |
| 21 | 商家管理后台 | 768×1024 | ☀️ light | ✅ | 0 | 1 | 43 | 0 | 0 | 0 | 0 | 741 |
| 22 | 商家管理后台 | 768×1024 | 🌙 dark | ✅ | 0 | 1 | 43 | 0 | 0 | 0 | 0 | 741 |
| 23 | 平台管理后台 | 1440×900 | ☀️ light | ✅ | 0 | 1 | 44 | 0 | 0 | 0 | 0 | 1608 |
| 24 | 平台管理后台 | 1440×900 | 🌙 dark | ✅ | 0 | 1 | 44 | 0 | 0 | 0 | 0 | 1608 |
| — | **合计 24** | — | light(0)/dark(0) | — | **0** | — | — | **0** | **0** | **0** | **0** | — |

> 子统计: Light 模式违规=0  通过规则=470   Dark 模式违规=0  通过规则=470

## 逐项违规详情

### 平台管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![platform-md-light](audit-report/platform__md__768x1024.png)  
- URL: `http://127.0.0.1:43123/platform-admin/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 平台管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![platform-md-dark](audit-report/platform__md__768x1024__dark.png)  
- URL: `http://127.0.0.1:43123/platform-admin/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 平台管理后台 · 桌面 1440×900 (1440×900) ☀️light  
- 加载: ✅    截图: ![platform-lg-light](audit-report/platform__lg__1440x900.png)  
- URL: `http://127.0.0.1:43123/platform-admin/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 平台管理后台 · 桌面 1440×900 (1440×900) 🌙dark  
- 加载: ✅    截图: ![platform-lg-dark](audit-report/platform__lg__1440x900__dark.png)  
- URL: `http://127.0.0.1:43123/platform-admin/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![merchant-md-light](audit-report/merchant__md__768x1024.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![merchant-md-dark](audit-report/merchant__md__768x1024__dark.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 桌面 1440×900 (1440×900) ☀️light  
- 加载: ✅    截图: ![merchant-lg-light](audit-report/merchant__lg__1440x900.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 桌面 1440×900 (1440×900) 🌙dark  
- 加载: ✅    截图: ![merchant-lg-dark](audit-report/merchant__lg__1440x900__dark.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 移动端 APP · 移动端 360×740 (iPhone SE) (360×740) ☀️light  
- 加载: ✅    截图: ![mobile-sm-light](audit-report/mobile__sm__360x740.png)  
- URL: `http://127.0.0.1:43123/mobile-app/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 移动端 APP · 移动端 360×740 (iPhone SE) (360×740) 🌙dark  
- 加载: ✅    截图: ![mobile-sm-dark](audit-report/mobile__sm__360x740__dark.png)  
- URL: `http://127.0.0.1:43123/mobile-app/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 移动端 APP · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![mobile-md-light](audit-report/mobile__md__768x1024.png)  
- URL: `http://127.0.0.1:43123/mobile-app/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 移动端 APP · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![mobile-md-dark](audit-report/mobile__md__768x1024__dark.png)  
- URL: `http://127.0.0.1:43123/mobile-app/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 微信小程序 · 移动端 360×740 (iPhone SE) (360×740) ☀️light  
- 加载: ✅    截图: ![mini-sm-light](audit-report/mini__sm__360x740.png)  
- URL: `http://127.0.0.1:43123/mini-program/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 微信小程序 · 移动端 360×740 (iPhone SE) (360×740) 🌙dark  
- 加载: ✅    截图: ![mini-sm-dark](audit-report/mini__sm__360x740__dark.png)  
- URL: `http://127.0.0.1:43123/mini-program/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 微信小程序 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![mini-md-light](audit-report/mini__md__768x1024.png)  
- URL: `http://127.0.0.1:43123/mini-program/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 微信小程序 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![mini-md-dark](audit-report/mini__md__768x1024__dark.png)  
- URL: `http://127.0.0.1:43123/mini-program/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![merchant-md-light](audit-report/merchant__md__768x1024__nh-suspend-55.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![merchant-md-dark](audit-report/merchant__md__768x1024__dark__nh-suspend-55.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![merchant-md-light](audit-report/merchant__md__768x1024__b2b-suspend-30.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![merchant-md-dark](audit-report/merchant__md__768x1024__dark__b2b-suspend-30.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![merchant-md-light](audit-report/merchant__md__768x1024__b2b-close-15.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![merchant-md-dark](audit-report/merchant__md__768x1024__dark__b2b-close-15.png)  
- URL: `http://127.0.0.1:43123/merchant-admin/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 平台管理后台 · 桌面 1440×900 (1440×900) ☀️light  
- 加载: ✅    截图: ![platform-lg-light](audit-report/platform__lg__1440x900__platform-penalty-modal.png)  
- URL: `http://127.0.0.1:43123/platform-admin/index.html`   配色: `light`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

### 平台管理后台 · 桌面 1440×900 (1440×900) 🌙dark  
- 加载: ✅    截图: ![platform-lg-dark](audit-report/platform__lg__1440x900__dark__platform-penalty-modal.png)  
- URL: `http://127.0.0.1:43123/platform-admin/index.html`   配色: `dark`
- 违规: ✅ 0 项 (axe wcag2a / 2aa / best-practice)

## 结论 (基线)

- **可访问性规则违规合计: 0 条** (axe-core, 含 4 应用, 双色方案 light + dark)
- Light 模式: 违规 0 / 通过规则 470    Dark 模式: 违规 0 / 通过规则 470
- **JS 控制台错误合计: 0 条**  警告合计: 0 条
- **资源加载失败(4xx/5xx): 0 次**  缺 alt 图像: 0 张
- ✅ **Light + Dark 双色方案 A11y/加载基线全绿，可作为后续 MR 的"无回归"阈值基准。**

> 详细 JSON: `audit-report/a11y-baseline.json`