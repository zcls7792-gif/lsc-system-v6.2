# 屏幕阅读器兼容性审计报告

> 生成时间: 2026-08-30T16:07:08.160Z
> 审计工具: audit-screenreader.js (JSDOM + VM)
> 标准: WAI-ARIA 1.2 + WCAG 2.1 AA (屏幕阅读器兼容性)

## 汇总

| 指标 | 数值 |
|------|------|
| 应用数 | 4 |
| 检查项/应用 | 22 |
| 总检查数 | 88 |
| ✅ PASS | 83 |
| ❌ FAIL (必须) | 0 |
| ⚠ WARN (建议) | 5 |

## ⚠ 平台管理后台 [platform-admin]

| 检查ID | 级别 | 检查项 | 结果 | 详情 |
|--------|------|--------|------|------|
| doc-lang | R | <html lang> 属性存在且有效 | ✅ PASS | lang="zh-CN" |
| doc-title | R | <title> 存在且长度合规 (8~60字符) | ✅ PASS | "链盛通LSC系统 · 平台管理后台 V6.2" (22字符) |
| skip-links | R | 跳过链接 (skip-link) 存在且目标有效 | ✅ PASS | ✓ "跳到主内容区" → #view; ✓ "跳到主导航" → #nav |
| landmarks | R | 关键地标 (navigation + main) 齐全 | ✅ PASS | navigation + main 齐全 |
| landmarks-banner | · | banner 地标 (header/role=banner) 存在 | ✅ PASS | banner 存在 |
| heading-order | R | 标题层级无跳级 (h1→h2, 禁 h1→h3) | ✅ PASS | ✓ h1: "链盛通LSC平台管理后台" |
| button-names | R | 所有 <button> 有无障碍名称 | ✅ PASS | 6 个按钮全部有名称 |
| link-names | R | 所有 <a href> 有无障碍名称 | ✅ PASS | 3 个链接全部有名称 |
| input-labels | R | 所有 <input>/<select>/<textarea> 有关联标签 | ✅ PASS | 2 个表单元素全部有标签 |
| img-alt | R | 所有 <img> 有 alt 属性 | ✅ PASS | 无 img 元素 |
| svg-hidden | · | 装饰性 <svg> 标记 aria-hidden="true" | ⚠ WARN | 32/37 个装饰性 svg 缺失 aria-hidden: (无类名), (无类名), (无类名), (无类名), (无类名) |
| dialog-aria | R | 弹窗有 role="dialog" + aria-modal="true" + 可访问名称 | ✅ PASS | ✓ #ai-mask "AI助手对话窗" |
| aria-hidden-focus | R | aria-hidden="true" 元素不含可聚焦子元素 (inert 豁免) | ✅ PASS | 23 个 aria-hidden 元素无焦点泄漏 |
| aria-current | R | 当前激活导航项标记 aria-current | ✅ PASS | aria-current="page" |
| tabindex-positive | R | 无 tabindex 正数 (避免破坏 Tab 顺序) | ✅ PASS | 无正数 tabindex |
| live-regions-dom | R | 动态状态文本元素有 aria-live 或 role=status/alert | ✅ PASS | ✓ #notif-panel (polite); ○ #notif-list (继承祖先 live region) |
| live-regions-js | R | JS 动态生成状态文本含 aria-live 或 role=status | ✅ PASS | JS 中无动态文本更新元素 |
| dialog-aria-js | R | JS 动态生成弹窗含 role=dialog + aria-modal + 可访问名称 | ✅ PASS | ✓ 动态 dialog aria 完整 |
| theme-toggle-a11y | · | 主题切换按钮有 aria-label + data-state | ✅ PASS | ✓ aria-label="切换主题，当前：跟随系统" |
| nav-keyboard | R | 导航项支持键盘 (tabindex/role=button) | ✅ PASS | 10 个导航项均可键盘操作 |
| sr-only-text | · | 使用 .sr-only 为视觉隐藏文本提供屏幕阅读器内容 | ✅ PASS | 4 处 .sr-only: ，12 条待处理; ，5 条待审核; 链盛通LSC平台管理后台; 搜索商家/订单/用户ID |
| color-scheme | · | <meta name="color-scheme"> 支持浅/深色 | ✅ PASS | content="light dark" |

## ⚠ 商家管理后台 [merchant-admin]

| 检查ID | 级别 | 检查项 | 结果 | 详情 |
|--------|------|--------|------|------|
| doc-lang | R | <html lang> 属性存在且有效 | ✅ PASS | lang="zh-CN" |
| doc-title | R | <title> 存在且长度合规 (8~60字符) | ✅ PASS | "链盛通LSC · 商家管理后台 V6.2" (20字符) |
| skip-links | R | 跳过链接 (skip-link) 存在且目标有效 | ✅ PASS | ✓ "跳到主内容区" → #view; ✓ "跳到主导航" → #nav |
| landmarks | R | 关键地标 (navigation + main) 齐全 | ✅ PASS | navigation + main 齐全 |
| landmarks-banner | · | banner 地标 (header/role=banner) 存在 | ✅ PASS | banner 存在 |
| heading-order | R | 标题层级无跳级 (h1→h2, 禁 h1→h3) | ✅ PASS | ✓ h1: "链盛通LSC商家管理后台" |
| button-names | R | 所有 <button> 有无障碍名称 | ✅ PASS | 5 个按钮全部有名称 |
| link-names | R | 所有 <a href> 有无障碍名称 | ✅ PASS | 2 个链接全部有名称 |
| input-labels | R | 所有 <input>/<select>/<textarea> 有关联标签 | ✅ PASS | 1 个表单元素全部有标签 |
| img-alt | R | 所有 <img> 有 alt 属性 | ✅ PASS | 无 img 元素 |
| svg-hidden | · | 装饰性 <svg> 标记 aria-hidden="true" | ⚠ WARN | 15/19 个装饰性 svg 缺失 aria-hidden: (无类名), (无类名), (无类名), (无类名), (无类名) |
| dialog-aria | R | 弹窗有 role="dialog" + aria-modal="true" + 可访问名称 | ✅ PASS | 无静态 dialog (动态生成见 JS 扫描) |
| aria-hidden-focus | R | aria-hidden="true" 元素不含可聚焦子元素 (inert 豁免) | ✅ PASS | 21 个 aria-hidden 元素无焦点泄漏 |
| aria-current | R | 当前激活导航项标记 aria-current | ✅ PASS | aria-current="page" |
| tabindex-positive | R | 无 tabindex 正数 (避免破坏 Tab 顺序) | ✅ PASS | 无正数 tabindex |
| live-regions-dom | R | 动态状态文本元素有 aria-live 或 role=status/alert | ✅ PASS | 无动态状态元素 (DOM 级) |
| live-regions-js | R | JS 动态生成状态文本含 aria-live 或 role=status | ✅ PASS | JS 中无动态文本更新元素 |
| dialog-aria-js | R | JS 动态生成弹窗含 role=dialog + aria-modal + 可访问名称 | ✅ PASS | ✓ 动态 dialog aria 完整 |
| theme-toggle-a11y | · | 主题切换按钮有 aria-label + data-state | ✅ PASS | ✓ aria-label="切换主题，当前：跟随系统" |
| nav-keyboard | R | 导航项支持键盘 (tabindex/role=button) | ✅ PASS | 9 个导航项均可键盘操作 |
| sr-only-text | · | 使用 .sr-only 为视觉隐藏文本提供屏幕阅读器内容 | ✅ PASS | 4 处 .sr-only: ，3 项待处理; ，2 条待处理; 链盛通LSC商家管理后台; 搜索商品、订单或流水 |
| color-scheme | · | <meta name="color-scheme"> 支持浅/深色 | ✅ PASS | content="light dark" |

## ⚠ 消费者移动端APP [mobile-app]

| 检查ID | 级别 | 检查项 | 结果 | 详情 |
|--------|------|--------|------|------|
| doc-lang | R | <html lang> 属性存在且有效 | ✅ PASS | lang="zh-CN" |
| doc-title | R | <title> 存在且长度合规 (8~60字符) | ✅ PASS | "链盛通LSC · 消费者APP V6.2" (20字符) |
| skip-links | R | 跳过链接 (skip-link) 存在且目标有效 | ✅ PASS | ✓ "跳到主内容区" → #content; ✓ "跳到主导航" → #tabbar |
| landmarks | R | 关键地标 (navigation + main) 齐全 | ✅ PASS | navigation + main 齐全 |
| landmarks-banner | · | banner 地标 (header/role=banner) 存在 | ⚠ WARN | 缺失 banner/header (移动端可接受) |
| heading-order | R | 标题层级无跳级 (h1→h2, 禁 h1→h3) | ✅ PASS | ✓ h1: "链盛通LSC消费者APP"; ✓ h2: "邀请好友 · 赚10%奖励" |
| button-names | R | 所有 <button> 有无障碍名称 | ✅ PASS | 15 个按钮全部有名称 |
| link-names | R | 所有 <a href> 有无障碍名称 | ✅ PASS | 2 个链接全部有名称 |
| input-labels | R | 所有 <input>/<select>/<textarea> 有关联标签 | ✅ PASS | 2 个表单元素全部有标签 |
| img-alt | R | 所有 <img> 有 alt 属性 | ✅ PASS | 无 img 元素 |
| svg-hidden | · | 装饰性 <svg> 标记 aria-hidden="true" | ⚠ WARN | 71/77 个装饰性 svg 缺失 aria-hidden: (无类名), (无类名), (无类名), (无类名), (无类名) |
| dialog-aria | R | 弹窗有 role="dialog" + aria-modal="true" + 可访问名称 | ✅ PASS | 无静态 dialog (动态生成见 JS 扫描) |
| aria-hidden-focus | R | aria-hidden="true" 元素不含可聚焦子元素 (inert 豁免) | ✅ PASS | 22 个 aria-hidden 元素无焦点泄漏 |
| aria-current | R | 当前激活导航项标记 aria-current | ✅ PASS | aria-current="page" |
| tabindex-positive | R | 无 tabindex 正数 (避免破坏 Tab 顺序) | ✅ PASS | 无正数 tabindex |
| live-regions-dom | R | 动态状态文本元素有 aria-live 或 role=status/alert | ✅ PASS | 无动态状态元素 (DOM 级) |
| live-regions-js | R | JS 动态生成状态文本含 aria-live 或 role=status | ✅ PASS | JS 中无动态状态文本元素 |
| dialog-aria-js | R | JS 动态生成弹窗含 role=dialog + aria-modal + 可访问名称 | ✅ PASS | JS 中无动态 dialog |
| theme-toggle-a11y | · | 主题切换按钮有 aria-label + data-state | ✅ PASS | ✓ aria-label="切换主题，当前：跟随系统" |
| nav-keyboard | R | 导航项支持键盘 (tabindex/role=button) | ✅ PASS | 5 个导航项均可键盘操作 |
| sr-only-text | · | 使用 .sr-only 为视觉隐藏文本提供屏幕阅读器内容 | ✅ PASS | 1 处 .sr-only: 链盛通LSC消费者APP |
| color-scheme | · | <meta name="color-scheme"> 支持浅/深色 | ✅ PASS | content="light dark" |

## ⚠ 微信小程序端 [mini-program]

| 检查ID | 级别 | 检查项 | 结果 | 详情 |
|--------|------|--------|------|------|
| doc-lang | R | <html lang> 属性存在且有效 | ✅ PASS | lang="zh-CN" |
| doc-title | R | <title> 存在且长度合规 (8~60字符) | ✅ PASS | "链盛通LSC · 微信小程序 V6.2" (19字符) |
| skip-links | R | 跳过链接 (skip-link) 存在且目标有效 | ✅ PASS | ✓ "跳到主内容区" → #wx-content; ✓ "跳到主导航" → #wx-tabbar |
| landmarks | R | 关键地标 (navigation + main) 齐全 | ✅ PASS | navigation + main 齐全 |
| landmarks-banner | · | banner 地标 (header/role=banner) 存在 | ✅ PASS | banner 存在 |
| heading-order | R | 标题层级无跳级 (h1→h2, 禁 h1→h3) | ✅ PASS | ✓ h1: "链盛通LSC微信小程序"; ✓ h2: "权益商城推荐"; ✓ h2: "附近商家"; ✓ h2: "推广战绩"; ✓ h2: "商品详情" |
| button-names | R | 所有 <button> 有无障碍名称 | ✅ PASS | 12 个按钮全部有名称 |
| link-names | R | 所有 <a href> 有无障碍名称 | ✅ PASS | 2 个链接全部有名称 |
| input-labels | R | 所有 <input>/<select>/<textarea> 有关联标签 | ✅ PASS | 1 个表单元素全部有标签 |
| img-alt | R | 所有 <img> 有 alt 属性 | ✅ PASS | 无 img 元素 |
| svg-hidden | · | 装饰性 <svg> 标记 aria-hidden="true" | ⚠ WARN | 63/69 个装饰性 svg 缺失 aria-hidden: (无类名), (无类名), (无类名), (无类名), (无类名) |
| dialog-aria | R | 弹窗有 role="dialog" + aria-modal="true" + 可访问名称 | ✅ PASS | 无静态 dialog (动态生成见 JS 扫描) |
| aria-hidden-focus | R | aria-hidden="true" 元素不含可聚焦子元素 (inert 豁免) | ✅ PASS | 21 个 aria-hidden 元素无焦点泄漏 |
| aria-current | R | 当前激活导航项标记 aria-current | ✅ PASS | aria-current="page" |
| tabindex-positive | R | 无 tabindex 正数 (避免破坏 Tab 顺序) | ✅ PASS | 无正数 tabindex |
| live-regions-dom | R | 动态状态文本元素有 aria-live 或 role=status/alert | ✅ PASS | 无动态状态元素 (DOM 级) |
| live-regions-js | R | JS 动态生成状态文本含 aria-live 或 role=status | ✅ PASS | JS 中无动态状态文本元素 |
| dialog-aria-js | R | JS 动态生成弹窗含 role=dialog + aria-modal + 可访问名称 | ✅ PASS | JS 中无动态 dialog |
| theme-toggle-a11y | · | 主题切换按钮有 aria-label + data-state | ✅ PASS | ✓ aria-label="切换主题，当前：跟随系统" |
| nav-keyboard | R | 导航项支持键盘 (tabindex/role=button) | ✅ PASS | 5 个导航项均可键盘操作 |
| sr-only-text | · | 使用 .sr-only 为视觉隐藏文本提供屏幕阅读器内容 | ✅ PASS | 1 处 .sr-only: 链盛通LSC微信小程序 |
| color-scheme | · | <meta name="color-scheme"> 支持浅/深色 | ✅ PASS | content="light dark" |

## 结论

✅ 无必须级违规。⚠ 5 项建议级改进，不影响屏幕阅读器基本可用性。

> 人工测试清单见: `audit-report/screenreader-checklist.md`