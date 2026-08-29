# 链盛通 LSC 前端资产体量审计报告

- 生成时间: 2026-08-29T11:21:02.028Z
- 严格模式: 开启
- 结果: FAIL=0 WARN=0  Score=100/100
- 合计: RAW=318.50 KiB  GZIP=82.36 KiB  压缩率=25.9%

## 阈值

| 项 | 阈值 | 实际 | 状态 |
|---|---|---|---|
| 单应用 JS gzip 总和 | ≤200.00 KiB | - | - |
| 单应用 CSS gzip 总和 | ≤50.00 KiB | - | - |
| 单文件 gzip 绝对上限 | ≤180.00 KiB | - | - |
| 全部应用+共享 gzip 总和 | ≤800.00 KiB | 82.36 KiB | PASS |

## 平台管理后台 (platform-admin)

- 资源文件数: 1
- RAW  total: 116.91 KiB  (JS 116.91 KiB / CSS 0 B)
- GZIP total: 29.25 KiB (JS 29.25 KiB / CSS 0 B)
- 压缩率: 25.0%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `platform-admin/app.js` | JS | 116.91 KiB | 29.25 KiB | 25.02% |

## 商家管理后台 (merchant-admin)

- 资源文件数: 1
- RAW  total: 76.01 KiB  (JS 76.01 KiB / CSS 0 B)
- GZIP total: 19.53 KiB (JS 19.53 KiB / CSS 0 B)
- 压缩率: 25.7%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `merchant-admin/app.js` | JS | 76.01 KiB | 19.53 KiB | 25.69% |

## 消费者移动端APP (mobile-app)

- 资源文件数: 1
- RAW  total: 45.42 KiB  (JS 45.42 KiB / CSS 0 B)
- GZIP total: 11.80 KiB (JS 11.80 KiB / CSS 0 B)
- 压缩率: 26.0%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `mobile-app/app.js` | JS | 45.42 KiB | 11.80 KiB | 25.98% |

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
- RAW  total: 45.67 KiB  (JS 19.25 KiB / CSS 26.42 KiB)
- GZIP total: 13.22 KiB (JS 6.06 KiB / CSS 7.16 KiB)
- 压缩率: 29.0%

| 文件 | 类型 | RAW | GZIP | 压缩率 |
|---|---|---:|---:|---:|
| `shared/design-system.css` | CSS | 26.42 KiB | 7.16 KiB | 27.11% |
| `shared/app-utils.js` | JS | 19.25 KiB | 6.06 KiB | 31.48% |

