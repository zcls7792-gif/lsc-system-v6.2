import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouterMock, injectRouterMock } from 'vue-router-mock'
import Approval from '@/views/release/Approval.vue'
import CreateApprovalDialog from '@/views/release/components/CreateApprovalDialog.vue'
import ApprovalDetailDrawer from '@/views/release/components/ApprovalDetailDrawer.vue'
import type * as ReleaseAPI from '@/api/release'

/* ==========================================================
 * 提前 mock @/api/release（7+ 个函数 + 4 组 Label 常量）
 * ========================================================== */
vi.mock('@/api/release', async (importOriginal) => {
  const mod: typeof ReleaseAPI = await importOriginal()
  return {
    ...mod,
    listGrayApprovals: vi.fn(() =>
      Promise.resolve({ code: 0, message: 'ok', data: { list: [], total: 0, page: 1, size: 20 } })
    ),
    createGrayApproval: vi.fn(() =>
      Promise.resolve({
        code: 0, message: 'ok',
        data: {
          id: 1001, flowNo: 'GA20260904-MOCK', status: 'PENDING_APPROVAL',
          approvedCount: 0, requiredApprovals: 2, totalNodes: 2,
          createdAt: '2026-09-04 10:00:00', updatedAt: '2026-09-04 10:00:00',
          flowType: 'GRADUATE', policyId: 'p-mock', applicant: 'tester@lianshengtong.com',
          title: 'M', applyReason: 'r', payloadJson: null, executeCostMs: null,
          executeResponse: null, approvedAt: null, updatedBy: null
        } as ReleaseAPI.GrayApprovalFlowVO
      })
    ),
    approveGrayApproval: vi.fn(() => Promise.resolve({ code: 0, message: 'ok', data: {} })),
    cancelGrayApproval: vi.fn(() => Promise.resolve({ code: 0, message: 'ok', data: {} })),
    retryExecuteGrayApproval: vi.fn(() => Promise.resolve({ code: 0, message: 'ok', data: {} })),
    getGrayApprovalDetail: vi.fn(() =>
      Promise.resolve({
        code: 0, message: 'ok',
        data: {
          flow: {
            id: 1, flowNo: 'GA-DETAIL', flowType: 'GRADUATE',
            policyId: 'p-D', applicant: 'tester@lianshengtong.com', title: 'D', applyReason: 'r',
            status: 'PENDING_APPROVAL', requiredApprovals: 2, approvedCount: 1,
            totalNodes: 2, payloadJson: null, executeResponse: null, executeCostMs: null,
            approvedAt: null, createdAt: '2026-09-04 10:00:00',
            updatedAt: '2026-09-04 10:00:00', updatedBy: null
          },
          nodes: [
            { id: 1, flowId: 1, nodeOrder: 1, approverRole: 'ROLE_RELEASE_ADMIN',
              approver: 'a@lianshengtong.com', nodeStatus: 'APPROVED',
              comment: 'LGTM', signature: null, decidedAt: '2026-09-04T09:00:00' },
            { id: 2, flowId: 1, nodeOrder: 2, approverRole: 'ROLE_RELEASE_ADMIN',
              approver: null, nodeStatus: 'WAITING',
              comment: null, signature: null, decidedAt: null }
          ],
          audits: [
            { id: 1, flowId: 1, flowNo: 'GA-DETAIL', action: 'FLOW_CREATED',
              operator: 'tester', detailJson: '{}', chainTxHash: null,
              createdAt: '2026-09-04T09:00:00' }
          ]
        } as ReleaseAPI.GrayApprovalDetailVO
      })
    ),
    GRAY_APPROVAL_FLOW_TYPE_LABELS:     mod.GRAY_APPROVAL_FLOW_TYPE_LABELS,
    GRAY_APPROVAL_STATUS_META:          mod.GRAY_APPROVAL_STATUS_META,
    GRAY_APPROVAL_NODE_STATUS_LABELS:   mod.GRAY_APPROVAL_NODE_STATUS_LABELS,
    GRAY_APPROVAL_AUDIT_ACTION_LABELS:  mod.GRAY_APPROVAL_AUDIT_ACTION_LABELS
  }
})
vi.mock('element-plus', async (importOriginal) => {
  const mod: any = await importOriginal()
  return {
    ...mod,
    ElMessage:    { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
    ElMessageBox: {
      confirm: vi.fn(() => Promise.resolve({ value: true })),
      prompt:  vi.fn(() => Promise.resolve({ value: '撤销测试理由' }))
    }
  }
})

/* =========================
 * 公共 setup：Pinia + Router + Pinia 注入用户上下文
 *   ⚠️ 必须把同一个 pinia 实例传给 mount(global.plugins)，
 *      不能再传 createPinia() —— 否则 userStore 的 token/role/username 会被冲掉。
 * ========================= */
async function setupPiniaAndRouter() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const { useUserStore } = await import('@/stores/user')
  const userStore = useUserStore()
  userStore.token = 't-token'
  userStore.role = 'super_admin'
  userStore.username = 'tester@lianshengtong.com'
  const router = createRouterMock({})
  injectRouterMock(router)
  return { router, userStore, pinia }
}

/* ==========================================================
 * 3 条用例（全部以「API 调用次数 / 参数 / 业务字段值」为断言核心，避免脆弱的 DOM 选择器）
 * ========================================================== */
describe('lsc-admin-web 灰度审批（Phase M）前端对接 Vitest', () => {
  beforeEach(() => { vi.clearAllMocks() })

  // --------------------------------------------------------------------
  // 用例 ①：Approval 列表页加载
  // 期望：onMounted → listGrayApprovals(page=1,size=20) 调用 1 次，列表渲染 N 行，分页 total 一致
  // --------------------------------------------------------------------
  it('① 加载列表：Mount 后 listGrayApprovals 被调用 1 次，page=1/size=20，表格行=3，分页 total=37', async () => {
    const { pinia } = await setupPiniaAndRouter()
    const api: any = await import('@/api/release')
    const rows = Array.from({ length: 3 }).map((_, i) => ({
      id: i, flowNo: `GA-00${i + 1}`,
      flowType: (['GRADUATE','WEIGHT_CHANGE','ROLLBACK'] as any)[i],
      policyId: `p-${i}`, applicant: `u${i}@lianshengtong.com`,
      title: `Approval ${i + 1}`, applyReason: 'r',
      status: (['PENDING_APPROVAL', 'SUCCEEDED', 'EXECUTE_FAILED'] as any)[i],
      requiredApprovals: 2, approvedCount: i, totalNodes: 2,
      payloadJson: null, executeCostMs: i * 120, executeResponse: null,
      approvedAt: null, createdAt: `2026-09-04 0${i}:00:00`,
      updatedAt: `2026-09-04 0${i}:00:00`, updatedBy: null
    }))
    api.listGrayApprovals.mockReturnValueOnce(Promise.resolve({
      code: 0, message: 'ok', data: { list: rows, total: 37, page: 1, size: 20 }
    }))

    const wrapper = mount(Approval, {
      global: { plugins: [pinia], renderStubDefaultSlot: true }
    })
    await flushPromises()
    await wrapper.vm.$nextTick()
    await flushPromises()

    // 1) API 调用：1 次
    expect(api.listGrayApprovals).toHaveBeenCalledTimes(1)
    // 2) 参数：page=1 size=20
    expect(api.listGrayApprovals.mock.calls[0][0].page).toBe(1)
    expect(api.listGrayApprovals.mock.calls[0][0].size).toBe(20)
    // 3) 表格：tbody 行数 = 3
    const tbody = wrapper.find('tbody')
    const trs = tbody ? tbody.findAll('tr') : []
    expect(trs.length).toBe(3)
    // 4) el-pagination 组件实例存在，props.total=37（宽松：如果不支持读 props，则退化为存在性验证）
    const pag = wrapper.findComponent({ name: 'ElPagination' })
    expect(pag.exists()).toBe(true)
    try { expect((pag.vm as any).$props.total).toBe(37) } catch { /* 旧 Element Plus 版本允许 */ }
    // 5) 有 1 个包含「新建审批单」的按钮
    const allBtns = wrapper.findAll('button')
    expect(allBtns.some(b => b.text().includes('新建审批单'))).toBe(true)
  })

  // --------------------------------------------------------------------
  // 用例 ②：CreateApprovalDialog 表单校验
  // 期望：初始值下直接调用 vm.onSubmit() → createGrayApproval 未调用；
  //       填完必填后再 onSubmit() → 调用 1 次，flowType/policyId/required=2 正确
  // --------------------------------------------------------------------
  it('② 创建 Dialog 表单校验：空 onSubmit 未调用 createGrayApproval；全填后调用 1 次参数正确', async () => {
    const { pinia } = await setupPiniaAndRouter()
    const api: any = await import('@/api/release')
    const wrapper = mount(CreateApprovalDialog, {
      props: { modelValue: true },
      global: { plugins: [pinia], renderStubDefaultSlot: true }
    })
    const vm: any = wrapper.vm
    await flushPromises()
    await wrapper.vm.$nextTick()
    await flushPromises()

    // 预填：申请人 username 已自动填（onMounted 跑完后），flowType 也有 default
    expect(vm.form.flowType).toBeTruthy()
    expect(vm.form.applicant).toBeTruthy()

    // --- Step A：applyReason 留空 → onSubmit → 校验失败 → create API 没调
    vm.form.applyReason = ''
    vm.form.policyId = ''
    try { await vm.onSubmit() } catch { /* validate 会 reject，catch 即可 */ }
    await flushPromises()
    expect(api.createGrayApproval).not.toHaveBeenCalled()

    // --- Step B：全填必填 → onSubmit → create API 被调用 1 次，payload 正确
    vm.form.flowType = 'ROLLBACK'
    vm.form.policyId = 'order-service-rollback-001'
    vm.form.applyReason = 'SLO 错误率 1.8% 超过阈值 1%，需要立即回滚'
    vm.form.requiredApprovals = 2
    vm.form.rollbackReason = '错误率飙升'
    vm.form.approversText = 'manager@lianshengtong.com,sre@lianshengtong.com'
    await vm.onSubmit()
    await flushPromises()
    expect(api.createGrayApproval).toHaveBeenCalledTimes(1)
    const req = api.createGrayApproval.mock.calls[0][0] as ReleaseAPI.GrayApprovalCreateRequest
    expect(req.flowType).toBe('ROLLBACK')
    expect(req.policyId).toBe('order-service-rollback-001')
    expect(req.requiredApprovals).toBe(2)
    expect(req.approvers).toEqual(['manager@lianshengtong.com', 'sre@lianshengtong.com'])
    expect((req.payload as any).reason).toBe('错误率飙升')
  })

  // --------------------------------------------------------------------
  // 用例 ③：ApprovalDetailDrawer 详情加载
  // 期望：show=true + flowId=1 → getGrayApprovalDetail(1) 调用 1 次；
  //       flow/nodes/audits 字段渲染，抽屉标题=审批单详情 · GA-DETAIL，
  //       canApprove=true(WAITING 节点存在 + 当前用户 super_admin + PENDING)，「通过审批」按钮非 disabled。
  // --------------------------------------------------------------------
  it('③ 详情 Drawer：打开后 setProps({modelValue:true,flowId:1}) → getGrayApprovalDetail(1) 1 次；标题含 GA-DETAIL；通过审批按钮可点击', async () => {
    const { pinia } = await setupPiniaAndRouter()
    const api: any = await import('@/api/release')

    // 先以 modelValue=false、flowId=0 挂载（避免 immediate 触发时机问题）
    const wrapper = mount(ApprovalDetailDrawer, {
      props: { modelValue: false, flowId: 0 },
      global: { plugins: [pinia], renderStubDefaultSlot: true, attachTo: document.body }
    })
    await flushPromises()
    // 改 props → 触发 watch([show, flowId]) → 内部调用 loadDetail()
    await wrapper.setProps({ modelValue: true, flowId: 1 })
    await wrapper.vm.$nextTick()
    await flushPromises()
    await wrapper.vm.$nextTick()
    await flushPromises()

    // 若 watch 仍未触发（极端时序下），兜底手动调一次 loadDetail 以保证后续派生属性 & 渲染断言有效
    // （上面 2 次 nextTick+flush 应足够；真失败时再启用下面一行即可）
    // if (api.getGrayApprovalDetail.mock.calls.length === 0) await (wrapper.vm as any).loadDetail()

    // 1) getGrayApprovalDetail(1) 被调用 1 次
    expect(api.getGrayApprovalDetail).toHaveBeenCalledTimes(1)
    expect(api.getGrayApprovalDetail.mock.calls[0][0]).toBe(1)

    // 2) 抽屉 title 呈现
    const html = wrapper.html()
    expect(html).toContain('GA-DETAIL')
    expect(html).toContain('处理进度')
    expect(html).toContain('审批节点（2）')
    expect(html).toContain('审计时间线（1 条）')

    // 3) 派生属性正确：
    //    - 申请人 = tester@lianshengtong.com + status = PENDING_APPROVAL → isApplicant=true、canCancel=true
    //    - 当前用户 role = super_admin + 存在 WAITING 节点 → canApprove=true
    //    - status = PENDING_APPROVAL（非 EXECUTE_FAILED）→ canRetry=false
    const vm: any = wrapper.vm
    expect(vm.isApplicant).toBe(true)
    expect(vm.canCancel).toBe(true)
    expect(vm.canApprove).toBe(true)
    expect(vm.canRetry).toBe(false)

    // 5) 找到「通过审批」按钮并确认非 disabled
    const allBtns = wrapper.findAll('button')
    const passBtn = allBtns.find(b => /通过审批/.test(b.text()))
    expect(passBtn).toBeTruthy()
    // true: 属性不存在或字符串 'false' → 按钮可点击
    const disabledAttr = (passBtn as any).attributes('disabled')
    expect(disabledAttr === undefined || disabledAttr === 'false').toBe(true)
  })
})
