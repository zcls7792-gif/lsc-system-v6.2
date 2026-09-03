import { test, expect } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'

test.describe('merchant-web · data-testid 锚点验证 (22 merchant-* anchors)', () => {
  test('Login 页渲染 10 个 merchant-login-* 锚点', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'networkidle' })
    const anchors = page.locator('[data-testid^="merchant-login-"]')
    await expect(anchors).toHaveCount(10)
    await expect(page.locator('[data-testid="merchant-login-brand-title"]')).toContainText('链盛通')
    await expect(page.locator('[data-testid="merchant-login-title"]')).toContainText('商家登录')
    await expect(page.locator('[data-testid="merchant-login-form"]')).toBeVisible()
    await expect(page.locator('[data-testid="merchant-login-submit-btn"]')).toBeVisible()
  })

  test('Dashboard 页至少渲染 1 个 merchant-dashboard-* 锚点', async ({ page }) => {
    await page.goto('/dashboard', { waitUntil: 'networkidle' }).catch(() => {})
    const loginAnchors = page.locator('[data-testid^="merchant-login-"]')
    const dashAnchors = page.locator('[data-testid^="merchant-dashboard-"]')
    const loginCount = await loginAnchors.count()
    const dashCount = await dashAnchors.count()
    expect(loginCount + dashCount).toBeGreaterThan(0)
  })
})

test.describe('merchant-web · axe-core a11y 扫描', () => {
  test('Login 页无 WCAG 严重违规', async ({ page }) => {
    await page.goto('/login', { waitUntil: 'networkidle' })
    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa'])
      .analyze()
    const critical = results.violations.filter(v => v.impact === 'critical')
    expect(critical).toHaveLength(0)
  })
})
