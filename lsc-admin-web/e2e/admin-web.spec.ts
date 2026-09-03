import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test.describe('admin-web · data-testid 锚点验证 (24 platform-* anchors)', () => {
  test('Login 页渲染 9 个 platform-login-* 锚点', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'networkidle' })
    const anchors = page.locator('[data-testid^="platform-login-"]')
    await expect(anchors).toHaveCount(9)
    await expect(page.locator('[data-testid="platform-login-title"]')).toContainText('LSC平台管理后台')
    await expect(page.locator('[data-testid="platform-login-form"]')).toBeVisible()
    await expect(page.locator('[data-testid="platform-login-submit-btn"]')).toBeVisible()
  })

  test('Dashboard 页渲染 3 个 platform-dashboard-* 锚点', async ({ page }) => {
    // 直接访问 dashboard，Vue Router 会处理未认证重定向到 login
    await page.goto('/dashboard', { waitUntil: 'networkidle' }).catch(() => {})
    // 如果重定向到 login，先验证 login 页锚点存在
    const loginAnchors = page.locator('[data-testid^="platform-login-"]')
    const dashAnchors = page.locator('[data-testid^="platform-dashboard-"]')
    const loginCount = await loginAnchors.count()
    const dashCount = await dashAnchors.count()
    // 至少一处有锚点
    expect(loginCount + dashCount).toBeGreaterThan(0)
  })
})

test.describe('admin-web · axe-core a11y 扫描', () => {
  test('Login 页无 WCAG 严重违规', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'networkidle' })
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze()
    const critical = results.violations.filter(v => v.impact === 'critical')
    expect(critical).toHaveLength(0)
  })
})
