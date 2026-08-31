# 链盛通 LSC 前端资产体量审计报告

- 生成时间: 2026-08-31T11:37:11.630Z
- 严格模式: 开启
- 结果: FAIL=0 WARN=0  Score=100/100
- 合计: RAW=343.50 KiB  GZIP=90.16 KiB  压缩率=26.2%

## 阈值

| 项 | 阈值 | 实际 | 状态 |
|---|---|---|---|
| 单应用 JS gzip 总和 | ≤200.00 KiB | - | - |
| 单应用 CSS gzip 总和 | ≤50.00 KiB | - | - |
| 单文件 gzip 绝对上限 | ≤180.00 KiB | - | - |
| 全部应用+共享 gzip 总和 | ≤800.00 KiB | 90.16 KiB | PASS |

## 平台管理后台 (platform-admin)

- 资源文件数: 1
- RAW  total: 120.06 KiB  (JS 120.06 KiB / CSS 0 B)
- GZIP total: 30.12 KiB (JS 30.12 KiB / CSS 0 B)
- 压缩率: 25.1%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `platform-admin/app.js` | JS | 120.06 KiB | 30.12 KiB | 25.08% |

## 商家管理后台 (merchant-admin)

- 资源文件数: 1
- RAW  total: 82.63 KiB  (JS 82.63 KiB / CSS 0 B)
- GZIP total: 21.56 KiB (JS 21.56 KiB / CSS 0 B)
- 压缩率: 26.1%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `merchant-admin/app.js` | JS | 82.63 KiB | 21.56 KiB | 26.09% |

## 消费者移动端APP (mobile-app)

- 资源文件数: 1
- RAW  total: 48.66 KiB  (JS 48.66 KiB / CSS 0 B)
- GZIP total: 13.08 KiB (JS 13.08 KiB / CSS 0 B)
- 压缩率: 26.9%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `mobile-app/app.js` | JS | 48.66 KiB | 13.08 KiB | 26.87% |

## 微信小程序端 (mini-program)

- 资源文件数: 1
- RAW  total: 34.56 KiB  (JS 34.56 KiB / CSS 0 B)
- GZIP total: 8.57 KiB (JS 8.57 KiB / CSS 0 B)
- 压缩率: 24.8%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `mini-program/app.js` | JS | 34.56 KiB | 8.57 KiB | 24.8% |

## 共享资源 (design-system.css/app-utils.js) (shared)

- 资源文件数: 2
- RAW  total: 57.57 KiB  (JS 24.58 KiB / CSS 32.99 KiB)
- GZIP total: 16.84 KiB (JS 7.89 KiB / CSS 8.95 KiB)
- 压缩率: 29.2%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `shared/design-system.css` | CSS | 32.99 KiB | 8.95 KiB | 27.11% |
| `shared/app-utils.js` | JS | 24.58 KiB | 7.89 KiB | 32.1% |

