# 链盛通 LSC 前端资产体量审计报告

- 生成时间: 2026-08-29T03:03:13.149Z
- 严格模式: 开启
- 结果: FAIL=0 WARN=0  Score=100/100
- 合计: RAW=313.24 KiB  GZIP=80.06 KiB  压缩率=25.6%

## 阈值

| 项 | 阈值 | 实际 | 状态 |
|---|---|---|---|
| 单应用 JS gzip 总和 | ≤200.00 KiB | - | - |
| 单应用 CSS gzip 总和 | ≤50.00 KiB | - | - |
| 单文件 gzip 绝对上限 | ≤180.00 KiB | - | - |
| 全部应用+共享 gzip 总和 | ≤800.00 KiB | 80.06 KiB | PASS |

## 平台管理后台 (platform-admin)

- 资源文件数: 1
- RAW  total: 114.95 KiB  (JS 114.95 KiB / CSS 0 B)
- GZIP total: 28.30 KiB (JS 28.30 KiB / CSS 0 B)
- 压缩率: 24.6%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `platform-admin/app.js` | JS | 114.95 KiB | 28.30 KiB | 24.62% |

## 商家管理后台 (merchant-admin)

- 资源文件数: 1
- RAW  total: 75.84 KiB  (JS 75.84 KiB / CSS 0 B)
- GZIP total: 19.41 KiB (JS 19.41 KiB / CSS 0 B)
- 压缩率: 25.6%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `merchant-admin/app.js` | JS | 75.84 KiB | 19.41 KiB | 25.6% |

## 消费者移动端APP (mobile-app)

- 资源文件数: 1
- RAW  total: 42.52 KiB  (JS 42.52 KiB / CSS 0 B)
- GZIP total: 10.70 KiB (JS 10.70 KiB / CSS 0 B)
- 压缩率: 25.2%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `mobile-app/app.js` | JS | 42.52 KiB | 10.70 KiB | 25.17% |

## 微信小程序端 (mini-program)

- 资源文件数: 1
- RAW  total: 34.49 KiB  (JS 34.49 KiB / CSS 0 B)
- GZIP total: 8.55 KiB (JS 8.55 KiB / CSS 0 B)
- 压缩率: 24.8%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `mini-program/app.js` | JS | 34.49 KiB | 8.55 KiB | 24.8% |

## 共享资源 (design-system.css/app-utils.js) (shared)

- 资源文件数: 2
- RAW  total: 45.45 KiB  (JS 19.03 KiB / CSS 26.42 KiB)
- GZIP total: 13.09 KiB (JS 5.92 KiB / CSS 7.16 KiB)
- 压缩率: 28.8%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `shared/design-system.css` | CSS | 26.42 KiB | 7.16 KiB | 27.11% |
| `shared/app-utils.js` | JS | 19.03 KiB | 5.92 KiB | 31.12% |

