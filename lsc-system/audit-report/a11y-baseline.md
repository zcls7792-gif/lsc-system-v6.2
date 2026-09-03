# 链盛通 LSC V6.2-AI · 可访问性 & 响应式基线审计 (快照 · Light + Dark)

> 生成于 **2026-09-03T01:17:27.424Z** · axe-core wcag2a+wcag2aa+best-practice · 基础 4 应用 × 2 视口 × 2 色方案=16 快照 + 8 扩展(档位+B2B 门控+处罚弹窗) · 合计 **24** 项快照

## 汇总表

| # | 应用 | 视口 | 配色 | 加载 | 违规(V) | 待核查(Inc) | 通过规则 | console.error | console.warn | 4xx/5xx | 无 alt 图 | 正文长度 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 平台管理后台 | 768×1024 | ☀️ light | ✅ | 2 | 1 | 40 | 3 | 0 | 0 | 0 | 1778 |
| 2 | 平台管理后台 | 768×1024 | 🌙 dark | ✅ | 2 | 1 | 40 | 3 | 0 | 0 | 0 | 1782 |
| 3 | 平台管理后台 | 1440×900 | ☀️ light | ✅ | 2 | 1 | 40 | 3 | 0 | 0 | 0 | 1874 |
| 4 | 平台管理后台 | 1440×900 | 🌙 dark | ✅ | 2 | 1 | 40 | 3 | 0 | 0 | 0 | 1874 |
| 5 | 商家管理后台 | 768×1024 | ☀️ light | ✅ | 2 | 1 | 44 | 1 | 0 | 0 | 0 | 976 |
| 6 | 商家管理后台 | 768×1024 | 🌙 dark | ✅ | 2 | 1 | 44 | 1 | 0 | 0 | 0 | 976 |
| 7 | 商家管理后台 | 1440×900 | ☀️ light | ✅ | 2 | 1 | 44 | 1 | 0 | 0 | 0 | 1127 |
| 8 | 商家管理后台 | 1440×900 | 🌙 dark | ✅ | 2 | 1 | 44 | 1 | 0 | 0 | 0 | 1127 |
| 9 | 移动端 APP | 360×740 | ☀️ light | ✅ | 2 | 1 | 36 | 1 | 0 | 0 | 0 | 554 |
| 10 | 移动端 APP | 360×740 | 🌙 dark | ✅ | 2 | 1 | 36 | 1 | 0 | 0 | 0 | 554 |
| 11 | 移动端 APP | 768×1024 | ☀️ light | ✅ | 2 | 1 | 36 | 1 | 0 | 0 | 0 | 554 |
| 12 | 移动端 APP | 768×1024 | 🌙 dark | ✅ | 2 | 1 | 36 | 1 | 0 | 0 | 0 | 554 |
| 13 | 微信小程序 | 360×740 | ☀️ light | ✅ | 2 | 1 | 37 | 1 | 0 | 0 | 0 | 448 |
| 14 | 微信小程序 | 360×740 | 🌙 dark | ✅ | 2 | 1 | 37 | 1 | 0 | 0 | 0 | 448 |
| 15 | 微信小程序 | 768×1024 | ☀️ light | ✅ | 2 | 1 | 37 | 1 | 0 | 0 | 0 | 448 |
| 16 | 微信小程序 | 768×1024 | 🌙 dark | ✅ | 2 | 1 | 37 | 1 | 0 | 0 | 0 | 448 |
| 17 | 商家管理后台 | 768×1024 | ☀️ light | ✅ | 2 | 1 | 44 | 1 | 0 | 0 | 0 | 748 |
| 18 | 商家管理后台 | 768×1024 | 🌙 dark | ✅ | 2 | 1 | 44 | 1 | 0 | 0 | 0 | 748 |
| 19 | 商家管理后台 | 768×1024 | ☀️ light | ✅ | 2 | 1 | 45 | 1 | 0 | 0 | 0 | 762 |
| 20 | 商家管理后台 | 768×1024 | 🌙 dark | ✅ | 2 | 1 | 45 | 1 | 0 | 0 | 0 | 762 |
| 21 | 商家管理后台 | 768×1024 | ☀️ light | ✅ | 2 | 1 | 45 | 1 | 0 | 0 | 0 | 767 |
| 22 | 商家管理后台 | 768×1024 | 🌙 dark | ✅ | 2 | 1 | 45 | 1 | 0 | 0 | 0 | 767 |
| 23 | 平台管理后台 | 1440×900 | ☀️ light | ✅ | 2 | 1 | 46 | 3 | 0 | 0 | 0 | 1634 |
| 24 | 平台管理后台 | 1440×900 | 🌙 dark | ✅ | 2 | 1 | 46 | 3 | 0 | 0 | 0 | 1634 |
| — | **合计 24** | — | light(24)/dark(24) | — | **48** | — | — | **36** | **0** | **0** | **0** | — |

> 子统计: Light 模式违规=24  通过规则=494   Dark 模式违规=24  通过规则=494

## 逐项违规详情

### 平台管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![platform-md-light](audit-report/platform__md__768x1024.png)  
- URL: `http://127.0.0.1:35143/platform-admin/index.html`   配色: `light`
- console.error (首 3):
  - Access to image at 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=LSC%20blockchain%20consumption%20voucher%20platform%20dashboard%20with%20AI%20panel%20clean%20modern%20screenshot&image_size=landscape_16_9' from origin 'http://127.0.0.1:35143' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.
  - Failed to load resource: net::ERR_FAILED
  - The path of the provided scope ('/platform-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `.skip-links`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 平台管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![platform-md-dark](audit-report/platform__md__768x1024__dark.png)  
- URL: `http://127.0.0.1:35143/platform-admin/index.html`   配色: `dark`
- console.error (首 3):
  - Access to image at 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=LSC%20blockchain%20consumption%20voucher%20platform%20dashboard%20with%20AI%20panel%20clean%20modern%20screenshot&image_size=landscape_16_9' from origin 'http://127.0.0.1:35143' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.
  - Failed to load resource: net::ERR_FAILED
  - The path of the provided scope ('/platform-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `.skip-links`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 平台管理后台 · 桌面 1440×900 (1440×900) ☀️light  
- 加载: ✅    截图: ![platform-lg-light](audit-report/platform__lg__1440x900.png)  
- URL: `http://127.0.0.1:35143/platform-admin/index.html`   配色: `light`
- console.error (首 3):
  - Access to image at 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=LSC%20blockchain%20consumption%20voucher%20platform%20dashboard%20with%20AI%20panel%20clean%20modern%20screenshot&image_size=landscape_16_9' from origin 'http://127.0.0.1:35143' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.
  - Failed to load resource: net::ERR_FAILED
  - The path of the provided scope ('/platform-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `.skip-links`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 平台管理后台 · 桌面 1440×900 (1440×900) 🌙dark  
- 加载: ✅    截图: ![platform-lg-dark](audit-report/platform__lg__1440x900__dark.png)  
- URL: `http://127.0.0.1:35143/platform-admin/index.html`   配色: `dark`
- console.error (首 3):
  - Access to image at 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=LSC%20blockchain%20consumption%20voucher%20platform%20dashboard%20with%20AI%20panel%20clean%20modern%20screenshot&image_size=landscape_16_9' from origin 'http://127.0.0.1:35143' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.
  - Failed to load resource: net::ERR_FAILED
  - The path of the provided scope ('/platform-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `.skip-links`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![merchant-md-light](audit-report/merchant__md__768x1024.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `light`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![merchant-md-dark](audit-report/merchant__md__768x1024__dark.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 桌面 1440×900 (1440×900) ☀️light  
- 加载: ✅    截图: ![merchant-lg-light](audit-report/merchant__lg__1440x900.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `light`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 桌面 1440×900 (1440×900) 🌙dark  
- 加载: ✅    截图: ![merchant-lg-dark](audit-report/merchant__lg__1440x900__dark.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 移动端 APP · 移动端 360×740 (iPhone SE) (360×740) ☀️light  
- 加载: ✅    截图: ![mobile-sm-light](audit-report/mobile__sm__360x740.png)  
- URL: `http://127.0.0.1:35143/mobile-app/index.html`   配色: `light`
- console.error (首 3):
  - The path of the provided scope ('/mobile-app/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 2  
     - 例: `a[href$="#nav"]`

### 移动端 APP · 移动端 360×740 (iPhone SE) (360×740) 🌙dark  
- 加载: ✅    截图: ![mobile-sm-dark](audit-report/mobile__sm__360x740__dark.png)  
- URL: `http://127.0.0.1:35143/mobile-app/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/mobile-app/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 2  
     - 例: `a[href$="#nav"]`

### 移动端 APP · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![mobile-md-light](audit-report/mobile__md__768x1024.png)  
- URL: `http://127.0.0.1:35143/mobile-app/index.html`   配色: `light`
- console.error (首 3):
  - The path of the provided scope ('/mobile-app/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 2  
     - 例: `a[href$="#nav"]`

### 移动端 APP · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![mobile-md-dark](audit-report/mobile__md__768x1024__dark.png)  
- URL: `http://127.0.0.1:35143/mobile-app/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/mobile-app/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 2  
     - 例: `a[href$="#nav"]`

### 微信小程序 · 移动端 360×740 (iPhone SE) (360×740) ☀️light  
- 加载: ✅    截图: ![mini-sm-light](audit-report/mini__sm__360x740.png)  
- URL: `http://127.0.0.1:35143/mini-program/index.html`   配色: `light`
- console.error (首 3):
  - The path of the provided scope ('/mini-program/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 3  
     - 例: `a[href$="#content"]`

### 微信小程序 · 移动端 360×740 (iPhone SE) (360×740) 🌙dark  
- 加载: ✅    截图: ![mini-sm-dark](audit-report/mini__sm__360x740__dark.png)  
- URL: `http://127.0.0.1:35143/mini-program/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/mini-program/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 3  
     - 例: `a[href$="#content"]`

### 微信小程序 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![mini-md-light](audit-report/mini__md__768x1024.png)  
- URL: `http://127.0.0.1:35143/mini-program/index.html`   配色: `light`
- console.error (首 3):
  - The path of the provided scope ('/mini-program/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 3  
     - 例: `a[href$="#content"]`

### 微信小程序 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![mini-md-dark](audit-report/mini__md__768x1024__dark.png)  
- URL: `http://127.0.0.1:35143/mini-program/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/mini-program/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 3  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![merchant-md-light](audit-report/merchant__md__768x1024__nh-suspend-55.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `light`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![merchant-md-dark](audit-report/merchant__md__768x1024__dark__nh-suspend-55.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![merchant-md-light](audit-report/merchant__md__768x1024__b2b-suspend-30.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `light`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![merchant-md-dark](audit-report/merchant__md__768x1024__dark__b2b-suspend-30.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) ☀️light  
- 加载: ✅    截图: ![merchant-md-light](audit-report/merchant__md__768x1024__b2b-close-15.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `light`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 商家管理后台 · 平板 768×1024 (iPad mini) (768×1024) 🌙dark  
- 加载: ✅    截图: ![merchant-md-dark](audit-report/merchant__md__768x1024__dark__b2b-close-15.png)  
- URL: `http://127.0.0.1:35143/merchant-admin/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/merchant-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 平台管理后台 · 桌面 1440×900 (1440×900) ☀️light  
- 加载: ✅    截图: ![platform-lg-light](audit-report/platform__lg__1440x900__platform-penalty-modal.png)  
- URL: `http://127.0.0.1:35143/platform-admin/index.html`   配色: `light`
- console.error (首 3):
  - Access to image at 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=LSC%20blockchain%20consumption%20voucher%20platform%20dashboard%20with%20AI%20panel%20clean%20modern%20screenshot&image_size=landscape_16_9' from origin 'http://127.0.0.1:35143' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.
  - Failed to load resource: net::ERR_FAILED
  - The path of the provided scope ('/platform-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

### 平台管理后台 · 桌面 1440×900 (1440×900) 🌙dark  
- 加载: ✅    截图: ![platform-lg-dark](audit-report/platform__lg__1440x900__dark__platform-penalty-modal.png)  
- URL: `http://127.0.0.1:35143/platform-admin/index.html`   配色: `dark`
- console.error (首 3):
  - The path of the provided scope ('/platform-admin/') is not under the max scope allowed ('/shared/'). Adjust the scope, move the Service Worker script, or use the Service-Worker-Allowed HTTP header to allow the scope.
  - Access to image at 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=LSC%20blockchain%20consumption%20voucher%20platform%20dashboard%20with%20AI%20panel%20clean%20modern%20screenshot&image_size=landscape_16_9' from origin 'http://127.0.0.1:35143' has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present on the requested resource.
  - Failed to load resource: net::ERR_FAILED
- 违规: **2 项** (前 10):
  1. **region** (impact=moderate) Ensures all page content is contained by landmarks  — 影响节点 1  
     - 例: `ul`
  1. **skip-link** (impact=moderate) Ensure all skip links have a focusable target  — 影响节点 1  
     - 例: `a[href$="#content"]`

## 结论 (基线)

- **可访问性规则违规合计: 48 条** (axe-core, 含 4 应用, 双色方案 light + dark)
- Light 模式: 违规 24 / 通过规则 494    Dark 模式: 违规 24 / 通过规则 494
- **JS 控制台错误合计: 36 条**  警告合计: 0 条
- **资源加载失败(4xx/5xx): 0 次**  缺 alt 图像: 0 张
- ⚠️ 存在基线问题，建议优先修复 console.error / 4xx/5xx，其次针对 axe violation 分类处理 (查看具体 colorScheme 分类定位)。

> 详细 JSON: `audit-report/a11y-baseline.json`