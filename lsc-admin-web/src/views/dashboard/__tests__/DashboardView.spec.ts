import { describe, it, expect, beforeEach, vi } from 'vitest'
import { shallowMount, config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DashboardView from '@/views/dashboard/index.vue'

config.global.renderStubDefaultSlot = true

vi.mock('echarts', () => ({
  init: vi.fn(() => ({
    setOption: vi.fn(),
    dispose: vi.fn(),
    resize: vi.fn()
  }))
}))

function factory() {
  setActivePinia(createPinia())
  return shallowMount(DashboardView, {
    global: {
      plugins: [createPinia()],
      renderStubDefaultSlot: true,
      stubs: { 'Teleport': true }
    }
  })
}

describe('admin-web Dashboard View spec', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders platform-dashboard-page anchor', () => {
    const w = factory()
    expect(w.find('[data-testid="platform-dashboard-page"]').exists()).toBe(true)
  })

  it('mounts dashboard root with 3 dashboard anchors', () => {
    const w = factory()
    const anchors = w.findAll('[data-testid^="platform-dashboard-"]')
    expect(anchors.length).toBeGreaterThanOrEqual(3)
  })
})
