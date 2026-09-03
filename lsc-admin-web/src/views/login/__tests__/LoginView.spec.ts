import { describe, it, expect, beforeEach, vi } from 'vitest'
import { shallowMount, config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouterMock, injectRouterMock } from 'vue-router-mock'
import LoginView from '@/views/login/index.vue'

config.global.renderStubDefaultSlot = true

vi.mock('@/api/admin', () => ({
  login: vi.fn(() => Promise.resolve({ data: { token: 'T-123456', role: 'super_admin', username: 'admin', realName: '超级管理员', id: 1 } }))
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
    global: {
      plugins: [createPinia()],
      renderStubDefaultSlot: true,
      directives: { focus() {} }
    }
  })
}

describe('admin-web Login View spec', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders login container with title anchor', () => {
    const w = factory()
    expect(w.find('[data-testid="platform-login-container"]').exists()).toBe(true)
    expect(w.find('[data-testid="platform-login-title"]').text()).toContain('LSC平台管理后台')
  })

  it('has 6+ platform- prefixed data-testid anchors on Login page', () => {
    const w = factory()
    const anchors = w.findAll('[data-testid^="platform-"]')
    expect(anchors.length).toBeGreaterThanOrEqual(6)
  })
})
