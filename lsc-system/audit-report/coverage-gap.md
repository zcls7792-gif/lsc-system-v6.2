# 链盛通 LSC 覆盖率深度缺口扫描报告

- 生成时间: 2026-08-29T03:01:04.720Z

## 各应用覆盖率总览

| 应用 | 文件 | 覆盖率 (LH/LF) | 未覆盖函数 | 部分覆盖函数 |
|---|---|---|---|---|
| platform-admin | platform-admin/app.js | 98.15% (1754/1787) | 6 | 4 |
| merchant-admin | merchant-admin/app.js | 98.05% (1056/1077) | 3 | 2 |
| mobile-app | mobile-app/app.js | 99.1% (662/668) | 0 | 1 |
| mini-program | mini-program/app.js | 98.78% (484/490) | 0 | 1 |
| shared | shared/app-utils.js | 100% (218/218) | 0 | 0 |

## Top15 跨应用未覆盖热点

| 排名 | 应用 | 函数名 | 起止行 | 未覆盖行数 |
|---|---|---|---|---|
| 1 | platform-admin | `onApprove` | L1695-1709 | 7 |
| 2 | platform-admin | `navTo` | L23-44 | 6 |
| 3 | merchant-admin | `navTo` | L10-30 | 6 |
| 4 | merchant-admin | `apply` | L449-464 | 6 |
| 5 | merchant-admin | `showB2BDetail` | L970-1025 | 6 |
| 6 | mobile-app | `showScreen` | L14-47 | 6 |
| 7 | mini-program | `back.onclick` | L46-67 | 6 |
| 8 | platform-admin | `onClose` | L1443-1453 | 4 |
| 9 | platform-admin | `setView` | L1326-1352 | 3 |
| 10 | platform-admin | `onApprove` | L1537-1544 | 3 |
| 11 | merchant-admin | `window.calcNH` | L718-726 | 3 |
| 12 | platform-admin | `renderNotifList` | L1353-1375 | 2 |
| 13 | platform-admin | `window.updateSig` | L1423-1442 | 2 |
| 14 | platform-admin | `onApprove` | L1570-1576 | 2 |
| 15 | platform-admin | `onApprove` | L1753-1759 | 2 |

## platform-admin 详细未覆盖函数清单

| 函数名 | 起止行 | 总行数 | 未覆盖 | 覆盖率 | 状态 |
|---|---|---|---|---|---|
| `onApprove` | L1695-1709 | 15 | 7 | 53.3% | NOCALL |
| `navTo` | L23-44 | 22 | 6 | 72.7% | PART |
| `onClose` | L1443-1453 | 11 | 4 | 63.6% | NOCALL |
| `setView` | L1326-1352 | 27 | 3 | 88.9% | PART |
| `onApprove` | L1537-1544 | 8 | 3 | 62.5% | NOCALL |
| `renderNotifList` | L1353-1375 | 23 | 2 | 91.3% | PART |
| `window.updateSig` | L1423-1442 | 20 | 2 | 90% | PART |
| `onApprove` | L1570-1576 | 7 | 2 | 71.4% | NOCALL |
| `onApprove` | L1753-1759 | 7 | 2 | 71.4% | NOCALL |
| `onApprove` | L1769-? | 98231 | 2 | 100% | NOCALL |

## merchant-admin 详细未覆盖函数清单

| 函数名 | 起止行 | 总行数 | 未覆盖 | 覆盖率 | 状态 |
|---|---|---|---|---|---|
| `navTo` | L10-30 | 21 | 6 | 71.4% | PART |
| `apply` | L449-464 | 16 | 6 | 62.5% | NOCALL |
| `showB2BDetail` | L970-1025 | 56 | 6 | 89.3% | PART |
| `window.calcNH` | L718-726 | 9 | 3 | 66.7% | NOCALL |
| `T` | L68-95 | 28 | 0 | 100% | NOCALL |

## mobile-app 详细未覆盖函数清单

| 函数名 | 起止行 | 总行数 | 未覆盖 | 覆盖率 | 状态 |
|---|---|---|---|---|---|
| `showScreen` | L14-47 | 34 | 6 | 82.4% | PART |

## mini-program 详细未覆盖函数清单

| 函数名 | 起止行 | 总行数 | 未覆盖 | 覆盖率 | 状态 |
|---|---|---|---|---|---|
| `back.onclick` | L46-67 | 22 | 6 | 72.7% | PART |

## shared 详细未覆盖函数清单

| 函数名 | 起止行 | 总行数 | 未覆盖 | 覆盖率 | 状态 |
|---|---|---|---|---|---|
