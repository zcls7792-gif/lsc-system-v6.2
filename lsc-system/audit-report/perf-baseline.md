# LSC V6.2-AI · 性能 / 实践质量基线

- 生成时间: 2026-08-29T02:35:07.159Z
- 形态: **移动端 (iPhone 390×844)**
- 核心汇总: 平均 Perf=100 · 平均 A11y=98 · 最大 LCP=0ms · 最大 TBT=0ms · 最大 CLS=0

## 4 应用分数矩阵

| 应用 | Perf | A11y | BP | SEO | FCP | SI | LCP | TBT | CLS | TTI |
|---|---|---|---|---|---|---|---|---|---|---|
| 平台管理后台 | **100** | 100 | - | - | 188ms | -ms | 0ms | 0ms | 0 | 170ms |
| 商家管理后台 | **100** | 100 | - | - | 96ms | -ms | 0ms | 0ms | 0 | 79ms |
| 移动端 APP | **100** | 95 | - | - | 60ms | -ms | 0ms | 0ms | 0 | 82ms |
| 微信小程序 | **100** | 95 | - | - | 52ms | -ms | 0ms | 0ms | 0 | 78ms |

## 核心 Web Vitals 解读 (CWV)

- LCP ≤ **2500ms** = Good  · ≤4000ms Needs Improvement  · >4000ms Poor
- CLS ≤ **0.1** = Good  · ≤0.25 Needs Improvement   · >0.25 Poor
- TBT (FID近似) ≤ **200ms** = Good  · ≤600ms Needs Improvement   · >600ms Poor

## 门控阈值 (用于 CI --thresholds)

- 四应用平均 Perf ≥ 60
- 任一应用 LCP ≤ 5000ms
- 任一应用 CLS ≤ 0.25

原始 JSON: `audit-report/perf-baseline.json`
