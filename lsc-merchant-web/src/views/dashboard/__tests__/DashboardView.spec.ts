import { describe, it, expect, beforeEach, vi } from 'vitest'
import { shallowMount, config } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import DashboardView from '@/views/dashboard/index.vue'

config.global.renderStubDefaultSlot = true

vi.mock('echarts/core', () => ({
  use: vi.fn(),
  init: vi.fn(() => ({ setOption: vi.fn(), dispose: vi.fn(), resize: vi.fn() })),
  graphic: {
    LinearGradient: class { constructor(..._a: any[]) {} }
  }
}))
vi.mock('echarts/charts', () => ({ LineChart: class L {} }))
vi.mock('echarts/components', () => ({ GridComponent: class G {}, TooltipComponent: class T {}, LegendComponent: class LE {} }))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: class C {} }))
vi.mock('@/api/order', () => ({ getOrderStats: vi.fn(() => Promise.resolve({ todayCount: 0, todayAmount: 0 })) }))
vi.mock('@/api/lsc', () => ({
  getLscOverview: vi.fn(() => Promise.resolve({ available: 0, locked: 0 })),
  getRecentTrend: vi.fn(() => Promise.resolve([]))
}))
vi.mock('@/api/auth', () => ({ getMerchantProfile: vi.fn(() => Promise.resolve({})) }))

function factory() {
  setActivePinia(createPinia())
  return shallowMount(DashboardView, {
    global: { plugins: [createPinia()], renderStubDefaultSlot: true, stubs: { 'Teleport': true } }
  })
}

describe('merchant-web Dashboard View spec', () => {
  beforeEach(() => { vi.clearAllMocks() })

  it('renders dashboard page anchor + has merchant- prefix anchors', () => {
    const w = factory()
    expect(w.find('[data-testid="merchant-dashboard-page"]').exists()).toBe(true)
    const anchors = w.findAll('[data-testid^="merchant-"]')
    expect(anchors.length).toBeGreaterThanOrEqual(1)
  })
})
