# 链盛通 LSC V6.2-AI · 可访问性 & 响应式基线审计 (快照)

> 生成于 **2026-08-28T06:00:30.999Z** · axe-core wcag2a+wcag2aa+best-practice · 4 应用 × 2 视口共 8 项快照

## 汇总表

| # | 应用 | 视口 | 加载 | 规则违规(V) | 待核查(Inc) | 通过规则 | console.error | console.warn | 4xx/5xx | 无 alt 图 | 正文长度 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 平台管理后台 | 768×1024 | ✅ | 5 | 2 | 20 | 0 | 0 | 0 | 0 | 1713 |
| 2 | 平台管理后台 | 1440×900 | ✅ | 4 | 2 | 20 | 0 | 0 | 0 | 0 | 1828 |
| 3 | 商家管理后台 | 768×1024 | ✅ | 4 | 1 | 22 | 0 | 0 | 0 | 0 | 916 |
| 4 | 商家管理后台 | 1440×900 | ✅ | 4 | 1 | 22 | 0 | 0 | 0 | 0 | 1052 |
| 5 | 移动端 APP | 360×740 | ✅ | 5 | 1 | 8 | 0 | 0 | 0 | 0 | 340 |
| 6 | 移动端 APP | 768×1024 | ✅ | 4 | 1 | 8 | 0 | 0 | 0 | 0 | 340 |
| 7 | 微信小程序 | 360×740 | ✅ | 5 | 1 | 10 | 0 | 0 | 0 | 0 | 397 |
| 8 | 微信小程序 | 768×1024 | ✅ | 4 | 1 | 10 | 0 | 0 | 0 | 0 | 397 |
| — | **合计 8** | — | — | **35** | — | — | **0** | **0** | **0** | **0** | — |

## 逐项违规详情

### 平台管理后台 · 平板 768×1024 (iPad mini) (768×1024)  
- 加载: ✅    截图: ![platform-md](audit-report/platform__md__768x1024.png)  
- URL: `http://127.0.0.1:8765/platform-admin/index.html`
- 违规: **5 项** (前 10):
  1. **color-contrast** (impact=serious) Ensures the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds  — 影响节点 16  
     - 例: `.breadcrumb > span:nth-child(1)`
  1. **landmark-one-main** (impact=moderate) Ensures the document has a main landmark  — 影响节点 1  
     - 例: `html`
  1. **page-has-heading-one** (impact=moderate) Ensure that the page, or at least one of its frames contains a level-one heading  — 影响节点 1  
     - 例: `html`
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 33  
     - 例: `.page-head > div:nth-child(1)`
  1. **scrollable-region-focusable** (impact=serious) Ensure elements that have scrollable content are accessible by keyboard  — 影响节点 1  
     - 例: `.mb-5.card.chart-card > div:nth-child(2)`

### 平台管理后台 · 桌面 1440×900 (1440×900)  
- 加载: ✅    截图: ![platform-lg](audit-report/platform__lg__1440x900.png)  
- URL: `http://127.0.0.1:8765/platform-admin/index.html`
- 违规: **4 项** (前 10):
  1. **color-contrast** (impact=serious) Ensures the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds  — 影响节点 19  
     - 例: `.breadcrumb > span:nth-child(1)`
  1. **landmark-one-main** (impact=moderate) Ensures the document has a main landmark  — 影响节点 1  
     - 例: `html`
  1. **page-has-heading-one** (impact=moderate) Ensure that the page, or at least one of its frames contains a level-one heading  — 影响节点 1  
     - 例: `html`
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 33  
     - 例: `.page-head > div:nth-child(1)`

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024)  
- 加载: ✅    截图: ![merchant-md](audit-report/merchant__md__768x1024.png)  
- URL: `http://127.0.0.1:8765/merchant-admin/index.html`
- 违规: **4 项** (前 10):
  1. **color-contrast** (impact=serious) Ensures the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds  — 影响节点 15  
     - 例: `.breadcrumb > span:nth-child(1)`
  1. **landmark-one-main** (impact=moderate) Ensures the document has a main landmark  — 影响节点 1  
     - 例: `html`
  1. **page-has-heading-one** (impact=moderate) Ensure that the page, or at least one of its frames contains a level-one heading  — 影响节点 1  
     - 例: `html`
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 9  
     - 例: `.items-center > div:nth-child(1)`

### 商家管理后台 · 桌面 1440×900 (1440×900)  
- 加载: ✅    截图: ![merchant-lg](audit-report/merchant__lg__1440x900.png)  
- URL: `http://127.0.0.1:8765/merchant-admin/index.html`
- 违规: **4 项** (前 10):
  1. **color-contrast** (impact=serious) Ensures the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds  — 影响节点 16  
     - 例: `.breadcrumb > span:nth-child(1)`
  1. **landmark-one-main** (impact=moderate) Ensures the document has a main landmark  — 影响节点 1  
     - 例: `html`
  1. **page-has-heading-one** (impact=moderate) Ensure that the page, or at least one of its frames contains a level-one heading  — 影响节点 1  
     - 例: `html`
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 9  
     - 例: `.items-center > div:nth-child(1)`

### 移动端 APP · 移动端 360×740 (iPhone SE) (360×740)  
- 加载: ✅    截图: ![mobile-sm](audit-report/mobile__sm__360x740.png)  
- URL: `http://127.0.0.1:8765/mobile-app/index.html`
- 违规: **5 项** (前 10):
  1. **color-contrast** (impact=serious) Ensures the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds  — 影响节点 11  
     - 例: `#statusbar > span`
  1. **landmark-one-main** (impact=moderate) Ensures the document has a main landmark  — 影响节点 1  
     - 例: `html`
  1. **page-has-heading-one** (impact=moderate) Ensure that the page, or at least one of its frames contains a level-one heading  — 影响节点 1  
     - 例: `html`
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 22  
     - 例: `#statusbar > span`
  1. **scrollable-region-focusable** (impact=serious) Ensure elements that have scrollable content are accessible by keyboard  — 影响节点 2  
     - 例: `#content`

### 移动端 APP · 平板 768×1024 (iPad mini) (768×1024)  
- 加载: ✅    截图: ![mobile-md](audit-report/mobile__md__768x1024.png)  
- URL: `http://127.0.0.1:8765/mobile-app/index.html`
- 违规: **4 项** (前 10):
  1. **landmark-one-main** (impact=moderate) Ensures the document has a main landmark  — 影响节点 1  
     - 例: `html`
  1. **page-has-heading-one** (impact=moderate) Ensure that the page, or at least one of its frames contains a level-one heading  — 影响节点 1  
     - 例: `html`
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 22  
     - 例: `#statusbar > span`
  1. **scrollable-region-focusable** (impact=serious) Ensure elements that have scrollable content are accessible by keyboard  — 影响节点 2  
     - 例: `#content`

### 微信小程序 · 移动端 360×740 (iPhone SE) (360×740)  
- 加载: ✅    截图: ![mini-sm](audit-report/mini__sm__360x740.png)  
- URL: `http://127.0.0.1:8765/mini-program/index.html`
- 违规: **5 项** (前 10):
  1. **color-contrast** (impact=serious) Ensures the contrast between foreground and background colors meets WCAG 2 AA minimum contrast ratio thresholds  — 影响节点 9  
     - 例: `#wx-statusbar > span`
  1. **landmark-one-main** (impact=moderate) Ensures the document has a main landmark  — 影响节点 1  
     - 例: `html`
  1. **page-has-heading-one** (impact=moderate) Ensure that the page, or at least one of its frames contains a level-one heading  — 影响节点 1  
     - 例: `html`
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 28  
     - 例: `#wx-statusbar > span`
  1. **scrollable-region-focusable** (impact=serious) Ensure elements that have scrollable content are accessible by keyboard  — 影响节点 2  
     - 例: `#wx-content`

### 微信小程序 · 平板 768×1024 (iPad mini) (768×1024)  
- 加载: ✅    截图: ![mini-md](audit-report/mini__md__768x1024.png)  
- URL: `http://127.0.0.1:8765/mini-program/index.html`
- 违规: **4 项** (前 10):
  1. **landmark-one-main** (impact=moderate) Ensures the document has a main landmark  — 影响节点 1  
     - 例: `html`
  1. **page-has-heading-one** (impact=moderate) Ensure that the page, or at least one of its frames contains a level-one heading  — 影响节点 1  
     - 例: `html`
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 28  
     - 例: `#wx-statusbar > span`
  1. **scrollable-region-focusable** (impact=serious) Ensure elements that have scrollable content are accessible by keyboard  — 影响节点 2  
     - 例: `#wx-content`

## 结论 (基线)

- **可访问性规则违规合计: 35 条** (axe-core, 含 4 应用)
- **JS 控制台错误合计: 0 条**  警告合计: 0 条
- **资源加载失败(4xx/5xx): 0 次**  缺 alt 图像: 0 张
- ⚠️ 存在基线问题，建议优先修复 console.error / 4xx/5xx，其次针对 axe violation 分类处理。

> 详细 JSON: `audit-report/a11y-baseline.json`