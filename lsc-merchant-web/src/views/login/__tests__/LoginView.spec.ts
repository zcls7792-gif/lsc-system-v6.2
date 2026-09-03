import { describe, it, expect, beforeEach, vi } from 'vitest'
import { shallowMount, config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouterMock, injectRouterMock } from 'vue-router-mock'
import LoginView from '@/views/login/index.vue'

config.global.renderStubDefaultSlot = true

vi.mock('@/api/auth', () => ({
  login: vi.fn(() => Promise.resolve({
    token: 'TK-8888',
    merchant: { userId: 1, mobile: '13800138000', storeName: '测试门店', nickname: '测试商家', avatarUrl: '', auditStatus: 1, isSignedSupervision: true }
  }))
}))
vi.mock('element-plus', async (importOriginal) => {
  const mod: any = await importOriginal()
  return {
    ...mod,
    ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
  }
})

function factory() {
  setActivePinia(createPinia())
  const router = createRouterMock({})
  injectRouterMock(router)
  return shallowMount(LoginView, {
    global: { plugins: [createPinia()], renderStubDefaultSlot: true, directives: { focus() {} } }
  })
}

describe('merchant-web Login View spec', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders login page + brand + title anchors', () => {
    const w = factory()
    expect(w.find('[data-testid="merchant-login-page"]').exists()).toBe(true)
    expect(w.find('[data-testid="merchant-login-brand-title"]').text()).toContain('链盛通')
    expect(w.find('[data-testid="merchant-login-title"]').text()).toContain('商家登录')
  })

  it('has 10 merchant- prefixed data-testid anchors on Login page', () => {
    const w = factory()
    const anchors = w.findAll('[data-testid^="merchant-"]')
    expect(anchors.length).toBeGreaterThanOrEqual(10)
  })
})
