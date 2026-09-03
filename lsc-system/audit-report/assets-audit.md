# 链盛通 LSC 前端资产体量审计报告

- 生成时间: 2026-09-03T05:00:17.512Z
- 严格模式: 开启
- 结果: FAIL=0 WARN=0  Score=100/100
- 合计: RAW=410.07 KiB  GZIP=113.57 KiB  压缩率=27.7%

## 阈值

| 项 | 阈值 | 实际 | 状态 |
|---|---|---|---|
| 单应用 JS gzip 总和 | ≤200.00 KiB | - | - |
| 单应用 CSS gzip 总和 | ≤50.00 KiB | - | - |
| 单文件 gzip 绝对上限 | ≤180.00 KiB | - | - |
| 全部应用+共享 gzip 总和 | ≤800.00 KiB | 113.57 KiB | PASS |

## 平台管理后台 (platform-admin)

- 资源文件数: 2
- RAW  total: 128.17 KiB  (JS 128.17 KiB / CSS 0 B)
- GZIP total: 32.96 KiB (JS 32.96 KiB / CSS 0 B)
- 压缩率: 25.7%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `platform-admin/app.js` | JS | 121.55 KiB | 30.37 KiB | 24.99% |
| `platform-admin/sw.js` | JS | 6.62 KiB | 2.59 KiB | 39.08% |

## 商家管理后台 (merchant-admin)

- 资源文件数: 2
- RAW  total: 90.20 KiB  (JS 90.20 KiB / CSS 0 B)
- GZIP total: 24.35 KiB (JS 24.35 KiB / CSS 0 B)
- 压缩率: 27.0%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `merchant-admin/app.js` | JS | 83.58 KiB | 21.76 KiB | 26.04% |
| `merchant-admin/sw.js` | JS | 6.62 KiB | 2.59 KiB | 39.08% |

## 消费者移动端APP (mobile-app)

- 资源文件数: 2
- RAW  total: 56.10 KiB  (JS 56.10 KiB / CSS 0 B)
- GZIP total: 15.84 KiB (JS 15.84 KiB / CSS 0 B)
- 压缩率: 28.2%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `mobile-app/app.js` | JS | 49.48 KiB | 13.26 KiB | 26.79% |
| `mobile-app/sw.js` | JS | 6.62 KiB | 2.59 KiB | 39.08% |

## 微信小程序端 (mini-program)

- 资源文件数: 2
- RAW  total: 41.70 KiB  (JS 41.70 KiB / CSS 0 B)
- GZIP total: 11.28 KiB (JS 11.28 KiB / CSS 0 B)
- 压缩率: 27.1%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `mini-program/app.js` | JS | 35.08 KiB | 8.70 KiB | 24.79% |
| `mini-program/sw.js` | JS | 6.62 KiB | 2.59 KiB | 39.08% |

## 共享资源 (design-system.css/app-utils.js) (shared)

- 资源文件数: 4
- RAW  total: 93.91 KiB  (JS 55.30 KiB / CSS 38.60 KiB)
- GZIP total: 29.14 KiB (JS 18.68 KiB / CSS 10.46 KiB)
- 压缩率: 31.0%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `shared/design-system.css` | CSS | 38.60 KiB | 10.46 KiB | 27.08% |
| `shared/keyboard-a11y.js` | JS | 24.11 KiB | 8.21 KiB | 34.05% |
| `shared/app-utils.js` | JS | 24.58 KiB | 7.89 KiB | 32.1% |
| `shared/sw.js` | JS | 6.62 KiB | 2.58 KiB | 39.06% |

