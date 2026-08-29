# 链盛通 LSC 系统 HTML/安全元数据审计报告

- 生成时间: 2026-08-29T02:41:09.466Z
- 严格模式: 开启 (WARN 计为 FAIL)
- 结果: PASS=76  WARN=0  FAIL=0

## 平台管理后台

- 文件: `platform-admin/index.html`
- &lt;html lang&gt; : `zh-CN`
- &lt;title&gt; : "链盛通LSC系统 · 平台管理后台 V6.2"
- meta 总数: 22  link 总数: 5  JSON-LD 块: 1

| 规则 | 级别 | 通过 | 说明 | 必需 |
|---|---|---|---|---|
| `charset` | PASS | ✓ | <meta charset="UTF-8"> (必须 HTML5) | Y |
| `viewport` | PASS | ✓ | <meta name="viewport"> 必须含 width=device-width 且 initial-scale | Y |
| `title` | PASS | ✓ | <title> 长度 8~60 字符 (SEO/展示建议) | Y |
| `htmlLang` | PASS | ✓ | <html lang="zh-CN"> 可访问性/本地化 | Y |
| `favicon` | PASS | ✓ | <link rel="icon"> 防止 /favicon.ico 404 | Y |
| `description` | PASS | ✓ | <meta name="description"> 长度 50~160 (SEO/OG fallback) |  |
| `keywords` | PASS | ✓ | <meta name="keywords"> (兼容性) |  |
| `themeColor` | PASS | ✓ | <meta name="theme-color"> (移动端 UI 适配, 需 media=(prefers-color-scheme) 双色) |  |
| `csp` | PASS | ✓ | <meta http-equiv="Content-Security-Policy"> (含 default-src 且 unsafe-inline 非必须但无 *) |  |
| `contentTypeNosniff` | PASS | ✓ | <meta http-equiv="X-Content-Type-Options"> nosniff |  |
| `referrer` | PASS | ✓ | <meta name="referrer"> strict-origin-when-cross-origin (隐私) |  |
| `ogTitle` | PASS | ✓ | <meta property="og:title"> |  |
| `ogDescription` | PASS | ✓ | <meta property="og:description"> |  |
| `ogImage` | PASS | ✓ | <meta property="og:image"> 链接可访问 |  |
| `ogType` | PASS | ✓ | <meta property="og:type"> website/... |  |
| `twitterCard` | PASS | ✓ | <meta name="twitter:card"> summary/summary_large_image |  |
| `appleTouchIcon` | PASS | ✓ | <link rel="apple-touch-icon"> (iOS 主屏) |  |
| `designSystemCSS` | PASS | ✓ | <link rel="stylesheet"> 加载 design-system.css | Y |
| `jsonLd` | PASS | ✓ | 存在 <script type="application/ld+json"> 结构化数据 |  |

## 商家管理后台

- 文件: `merchant-admin/index.html`
- &lt;html lang&gt; : `zh-CN`
- &lt;title&gt; : "链盛通LSC · 商家管理后台 V6.2"
- meta 总数: 22  link 总数: 5  JSON-LD 块: 1

| 规则 | 级别 | 通过 | 说明 | 必需 |
|---|---|---|---|---|
| `charset` | PASS | ✓ | <meta charset="UTF-8"> (必须 HTML5) | Y |
| `viewport` | PASS | ✓ | <meta name="viewport"> 必须含 width=device-width 且 initial-scale | Y |
| `title` | PASS | ✓ | <title> 长度 8~60 字符 (SEO/展示建议) | Y |
| `htmlLang` | PASS | ✓ | <html lang="zh-CN"> 可访问性/本地化 | Y |
| `favicon` | PASS | ✓ | <link rel="icon"> 防止 /favicon.ico 404 | Y |
| `description` | PASS | ✓ | <meta name="description"> 长度 50~160 (SEO/OG fallback) |  |
| `keywords` | PASS | ✓ | <meta name="keywords"> (兼容性) |  |
| `themeColor` | PASS | ✓ | <meta name="theme-color"> (移动端 UI 适配, 需 media=(prefers-color-scheme) 双色) |  |
| `csp` | PASS | ✓ | <meta http-equiv="Content-Security-Policy"> (含 default-src 且 unsafe-inline 非必须但无 *) |  |
| `contentTypeNosniff` | PASS | ✓ | <meta http-equiv="X-Content-Type-Options"> nosniff |  |
| `referrer` | PASS | ✓ | <meta name="referrer"> strict-origin-when-cross-origin (隐私) |  |
| `ogTitle` | PASS | ✓ | <meta property="og:title"> |  |
| `ogDescription` | PASS | ✓ | <meta property="og:description"> |  |
| `ogImage` | PASS | ✓ | <meta property="og:image"> 链接可访问 |  |
| `ogType` | PASS | ✓ | <meta property="og:type"> website/... |  |
| `twitterCard` | PASS | ✓ | <meta name="twitter:card"> summary/summary_large_image |  |
| `appleTouchIcon` | PASS | ✓ | <link rel="apple-touch-icon"> (iOS 主屏) |  |
| `designSystemCSS` | PASS | ✓ | <link rel="stylesheet"> 加载 design-system.css | Y |
| `jsonLd` | PASS | ✓ | 存在 <script type="application/ld+json"> 结构化数据 |  |

## 移动端消费者APP

- 文件: `mobile-app/index.html`
- &lt;html lang&gt; : `zh-CN`
- &lt;title&gt; : "链盛通LSC · 消费者APP V6.2"
- meta 总数: 25  link 总数: 5  JSON-LD 块: 1

| 规则 | 级别 | 通过 | 说明 | 必需 |
|---|---|---|---|---|
| `charset` | PASS | ✓ | <meta charset="UTF-8"> (必须 HTML5) | Y |
| `viewport` | PASS | ✓ | <meta name="viewport"> 必须含 width=device-width 且 initial-scale | Y |
| `title` | PASS | ✓ | <title> 长度 8~60 字符 (SEO/展示建议) | Y |
| `htmlLang` | PASS | ✓ | <html lang="zh-CN"> 可访问性/本地化 | Y |
| `favicon` | PASS | ✓ | <link rel="icon"> 防止 /favicon.ico 404 | Y |
| `description` | PASS | ✓ | <meta name="description"> 长度 50~160 (SEO/OG fallback) |  |
| `keywords` | PASS | ✓ | <meta name="keywords"> (兼容性) |  |
| `themeColor` | PASS | ✓ | <meta name="theme-color"> (移动端 UI 适配, 需 media=(prefers-color-scheme) 双色) |  |
| `csp` | PASS | ✓ | <meta http-equiv="Content-Security-Policy"> (含 default-src 且 unsafe-inline 非必须但无 *) |  |
| `contentTypeNosniff` | PASS | ✓ | <meta http-equiv="X-Content-Type-Options"> nosniff |  |
| `referrer` | PASS | ✓ | <meta name="referrer"> strict-origin-when-cross-origin (隐私) |  |
| `ogTitle` | PASS | ✓ | <meta property="og:title"> |  |
| `ogDescription` | PASS | ✓ | <meta property="og:description"> |  |
| `ogImage` | PASS | ✓ | <meta property="og:image"> 链接可访问 |  |
| `ogType` | PASS | ✓ | <meta property="og:type"> website/... |  |
| `twitterCard` | PASS | ✓ | <meta name="twitter:card"> summary/summary_large_image |  |
| `appleTouchIcon` | PASS | ✓ | <link rel="apple-touch-icon"> (iOS 主屏) |  |
| `designSystemCSS` | PASS | ✓ | <link rel="stylesheet"> 加载 design-system.css | Y |
| `jsonLd` | PASS | ✓ | 存在 <script type="application/ld+json"> 结构化数据 |  |

## 微信小程序端

- 文件: `mini-program/index.html`
- &lt;html lang&gt; : `zh-CN`
- &lt;title&gt; : "链盛通LSC · 微信小程序 V6.2"
- meta 总数: 25  link 总数: 5  JSON-LD 块: 1

| 规则 | 级别 | 通过 | 说明 | 必需 |
|---|---|---|---|---|
| `charset` | PASS | ✓ | <meta charset="UTF-8"> (必须 HTML5) | Y |
| `viewport` | PASS | ✓ | <meta name="viewport"> 必须含 width=device-width 且 initial-scale | Y |
| `title` | PASS | ✓ | <title> 长度 8~60 字符 (SEO/展示建议) | Y |
| `htmlLang` | PASS | ✓ | <html lang="zh-CN"> 可访问性/本地化 | Y |
| `favicon` | PASS | ✓ | <link rel="icon"> 防止 /favicon.ico 404 | Y |
| `description` | PASS | ✓ | <meta name="description"> 长度 50~160 (SEO/OG fallback) |  |
| `keywords` | PASS | ✓ | <meta name="keywords"> (兼容性) |  |
| `themeColor` | PASS | ✓ | <meta name="theme-color"> (移动端 UI 适配, 需 media=(prefers-color-scheme) 双色) |  |
| `csp` | PASS | ✓ | <meta http-equiv="Content-Security-Policy"> (含 default-src 且 unsafe-inline 非必须但无 *) |  |
| `contentTypeNosniff` | PASS | ✓ | <meta http-equiv="X-Content-Type-Options"> nosniff |  |
| `referrer` | PASS | ✓ | <meta name="referrer"> strict-origin-when-cross-origin (隐私) |  |
| `ogTitle` | PASS | ✓ | <meta property="og:title"> |  |
| `ogDescription` | PASS | ✓ | <meta property="og:description"> |  |
| `ogImage` | PASS | ✓ | <meta property="og:image"> 链接可访问 |  |
| `ogType` | PASS | ✓ | <meta property="og:type"> website/... |  |
| `twitterCard` | PASS | ✓ | <meta name="twitter:card"> summary/summary_large_image |  |
| `appleTouchIcon` | PASS | ✓ | <link rel="apple-touch-icon"> (iOS 主屏) |  |
| `designSystemCSS` | PASS | ✓ | <link rel="stylesheet"> 加载 design-system.css | Y |
| `jsonLd` | PASS | ✓ | 存在 <script type="application/ld+json"> 结构化数据 |  |

