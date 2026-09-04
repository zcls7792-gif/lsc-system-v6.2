<script setup lang="ts">
/**
 * 审批单详情 Drawer（Phase M）
 * 内容：
 *   ① 头部：flowNo / status progress（8 状态步骤条）
 *   ② flowType 摘要卡片：policyId / 申请人 / 审批进度（approvedCount / required）
 *   ③ payload 区：targetWeight / reason 显示
 *   ④ 2~N 审批节点：环形节点图（WAITING/APPROVED/REJECTED）
 *   ⑤ 操作按钮区（按状态条件显示）：
 *        PENDING → 我要审批（通过/拒绝）；申请人 → 撤销
 *        EXECUTE_FAILED → 重试执行
 *   ⑥ Audit 时间线：FLOW_CREATED → NODE_APPROVED×N → FLOW_SUCCEEDED / …
 *   ⑦ executeResponse JSON 折叠显示
 */
import { computed, ref, watch } from 'vue'
import {
  ElMessage, ElMessageBox, type FormInstance, type FormRules, type DrawerInstance
} from 'element-plus'
import type { FormRules as _FR } from 'element-plus'
import {
  getGrayApprovalDetail,
  approveGrayApproval,
  cancelGrayApproval,
  retryExecuteGrayApproval,
  GRAY_APPROVAL_FLOW_TYPE_LABELS,
  GRAY_APPROVAL_STATUS_META,
  GRAY_APPROVAL_NODE_STATUS_LABELS,
  GRAY_APPROVAL_AUDIT_ACTION_LABELS,
  type GrayApprovalDetailVO,
  type GrayApprovalFlowStatus,
  type GrayApprovalNodeVO
} from '@/api/release'
import { useUserStore } from '@/stores/user'

interface Props {
  modelValue: boolean
  flowId: number | null
}
const props = withDefaults(defineProps<Props>(), { modelValue: false, flowId: null })
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'changed'): void // 父列表刷新
}>()

const userStore = useUserStore()
const drawer = ref<DrawerInstance>()
const loading = ref(false)
const submitting = ref(false)
const detail = ref<GrayApprovalDetailVO | null>(null)

/* ---- 操作：审批 Dialog ---- */
const approveDialog = ref(false)
const approveForm = ref({
  approved: true as boolean,
  comment: '',
  signature: ''
})
const approveFormRef = ref<FormInstance>()
const approveRules: _FR = {
  comment: [{ required: false, min: 2, max: 200, message: '建议写 2~200 字审批意见', trigger: 'blur' }]
}

/* ---- 操作：撤销 Dialog ---- */
const cancelDialog = ref(false)
const cancelForm = ref({ reason: '' })
const cancelFormRef = ref<FormInstance>()
const cancelRules: FormRules = {
  reason: [{ required: true, min: 4, max: 200, message: '请填写撤销原因（4~200 字）', trigger: 'blur' }]
}

const show = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

async function loadDetail() {
  if (!props.flowId) return
  loading.value = true
  try {
    const res = await getGrayApprovalDetail(props.flowId)
    if (res.code === 0) detail.value = res.data
  } finally {
    loading.value = false
  }
}

watch([show, () => props.flowId], ([v, id]) => {
  if (v && id) loadDetail()
  else detail.value = null
})

/* -------- 派生显示字段 -------- */
const flow = computed(() => detail.value?.flow ?? null)
const nodes = computed<GrayApprovalNodeVO[]>(() =>
  [...(detail.value?.nodes ?? [])].sort((a, b) => a.nodeOrder - b.nodeOrder)
)
const audits = computed(() => [...(detail.value?.audits ?? [])].sort((a, b) => a.id - b.id))
const flowStatusMeta = computed(() =>
  flow.value ? GRAY_APPROVAL_STATUS_META[flow.value.status as GrayApprovalFlowStatus] : null
)
const flowTypeLabel = computed(() =>
  flow.value ? GRAY_APPROVAL_FLOW_TYPE_LABELS[flow.value.flowType] : '-'
)
const payloadObj = computed<Record<string, unknown> | null>(() => {
  if (!flow.value?.payloadJson) return null
  try { return JSON.parse(flow.value.payloadJson) } catch { return null }
})
const executeResponseObj = computed(() => {
  if (!flow.value?.executeResponse) return null
  try { return JSON.parse(flow.value.executeResponse) } catch { return flow.value.executeResponse }
})

/* -------- 8 状态步骤条：仅展示线性可达成子集（可视化进度感） -------- */
const statusSteps: { value: GrayApprovalFlowStatus; label: string; key: string }[] = [
  { value: 'DRAFT',            label: '草稿',   key: 'draft' },
  { value: 'PENDING_APPROVAL', label: '待审批', key: 'pending' },
  { value: 'APPROVED',         label: '已通过', key: 'approved' },
  { value: 'EXECUTING',        label: '执行中', key: 'executing' },
  { value: 'SUCCEEDED',        label: '成功',   key: 'success' }
]
// 步骤条激活的 index：SUCCEEDED→5 全绿；REJECTED/CANCELLED/FAILED 停留到上一步
const stepActive = computed(() => {
  if (!flow.value) return 0
  const idx = statusSteps.findIndex(s => s.value === flow.value!.status)
  if (idx >= 0) return idx + 1
  // REJECTED / CANCELLED / EXECUTE_FAILED → 停在 PENDING_APPROVAL（因为分支离开）
  // EXECUTE_FAILED → 停在 EXECUTING（达到过，但失败）
  if (flow.value.status === 'EXECUTE_FAILED') return 4
  return 2
})
const stepStatus = computed<'success' | 'warning' | 'error' | 'primary' | ''>(() => {
  if (!flow.value) return ''
  switch (flow.value.status) {
    case 'REJECTED':
    case 'CANCELLED':
    case 'EXECUTE_FAILED':
      return flow.value.status === 'EXECUTE_FAILED' ? 'error' : 'warning'
    case 'SUCCEEDED':
      return 'success'
    default: return 'primary'
  }
})

/* -------- 按钮权限判断 -------- */
const isApplicant = computed(() => userStore.username && flow.value?.applicant === userStore.username)
const isApproverForWaitingNode = computed(() => {
  if (!userStore.username) return false
  const waiting = nodes.value.find(n => n.nodeStatus === 'WAITING')
  if (!waiting) return false
  // 指定审批人精确匹配 or 未指定则角色池（前端简化：只要角色 == release_admin/tech_admin/super 即可）
  if (waiting.approver) return waiting.approver === userStore.username
  return ['super_admin', 'tech_admin', 'release_admin'].includes(userStore.role || '')
})
const canCancel = computed(() =>
  isApplicant.value &&
    (flow.value?.status === 'DRAFT' || flow.value?.status === 'PENDING_APPROVAL')
)
const canApprove = computed(() =>
  flow.value?.status === 'PENDING_APPROVAL' && isApproverForWaitingNode.value
)
const canRetry = computed(() =>
  flow.value?.status === 'EXECUTE_FAILED' || flow.value?.status === 'APPROVED'
)

/* -------- Actions -------- */
async function openApproveDialog(approved: boolean) {
  approveForm.value = { approved, comment: '', signature: '' }
  approveDialog.value = true
}
async function submitApprove() {
  if (!approveFormRef.value || !flow.value) return
  try { await approveFormRef.value.validate() }
  catch { return ElMessage.warning('请先校验表单') }
  submitting.value = true
  try {
    const res = await approveGrayApproval({
      flowId: flow.value.id,
      approver: userStore.username || '',
      approved: approveForm.value.approved,
      comment: approveForm.value.comment || undefined,
      signature: approveForm.value.signature || undefined
    })
    if (res.code === 0) {
      ElMessage.success(approveForm.value.approved ? '已通过节点审批' : '已拒绝该审批单')
      approveDialog.value = false
      emit('changed')
      await loadDetail()
    }
  } finally { submitting.value = false }
}
async function submitCancel() {
  if (!cancelFormRef.value || !flow.value) return
  try { await cancelFormRef.value.validate() }
  catch { return ElMessage.warning('请先填写撤销原因') }
  submitting.value = true
  try {
    const res = await cancelGrayApproval({
      flowId: flow.value.id,
      operator: userStore.username || '',
      reason: cancelForm.value.reason
    })
    if (res.code === 0) {
      ElMessage.success('撤销成功：审批单已进入 CANCELLED 终态')
      cancelDialog.value = false
      emit('changed')
      await loadDetail()
    }
  } finally { submitting.value = false }
}
async function onRetryExecute() {
  if (!flow.value) return
  try {
    await ElMessageBox.confirm(
      '将重新调用 lsc-gateway 执行端处理该审批动作；若仍失败会再次进入 EXECUTE_FAILED。确认执行？',
      '重试执行',
      { type: 'warning', confirmButtonText: '确认重试', cancelButtonText: '取消' }
    )
  } catch { return }
  submitting.value = true
  try {
    const res = await retryExecuteGrayApproval({
      flowId: flow.value.id,
      operator: userStore.username || ''
    })
    if (res.code === 0) {
      ElMessage.success(res.data.status === 'SUCCEEDED' ? '执行成功 ✅' : '已提交重试，结果：' + res.data.status)
      emit('changed')
      await loadDetail()
    }
  } finally { submitting.value = false }
}

function auditLabel(a: string) {
  return GRAY_APPROVAL_AUDIT_ACTION_LABELS[a] ?? a
}
</script>

<template>
  <el-drawer
    ref="drawer"
    v-model="show"
    :title="detail?.flow.flowNo ? `审批单详情 · ${detail.flow.flowNo}` : '审批单详情'"
    size="720px"
    :with-header="true"
  >
    <div v-loading="loading" class="approval-detail">

      <!-- ① 状态步骤条 -->
      <section class="section" v-if="flow">
        <div class="section-head">处理进度</div>
        <div class="status-wrap">
          <el-steps
            :active="stepActive"
            :status="stepStatus"
            finish-status="success"
            align-center
            style="max-width:760px"
          >
            <el-step
              v-for="s in statusSteps"
              :key="s.key"
              :title="s.label"
              :description="s.value === flow.status ? `● 当前状态` : ''"
            />
          </el-steps>
          <el-tag :type="flowStatusMeta?.type" effect="light" style="margin-top:8px">
            {{ flowStatusMeta?.label }}
          </el-tag>
          <span v-if="flow.executeCostMs != null" style="margin-left:12px;color:#909399;font-size:12px">
            执行耗时 {{ flow.executeCostMs }}ms
          </span>
        </div>
      </section>

      <!-- ② 基本信息 -->
      <section class="section" v-if="flow">
        <div class="section-head">基本信息</div>
        <el-descriptions :column="2" border size="default">
          <el-descriptions-item label="审批编号" :span="1">{{ flow.flowNo }}</el-descriptions-item>
          <el-descriptions-item label="动作类型">{{ flowTypeLabel }}（{{ flow.flowType }}）</el-descriptions-item>
          <el-descriptions-item label="灰度策略ID">{{ flow.policyId }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="1">{{ flow.title || '-' }}</el-descriptions-item>
          <el-descriptions-item label="申请人">{{ flow.applicant }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{ flow.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="审批进度" :span="1">
            <el-progress
              type="line"
              :percentage="flow.requiredApprovals ? Math.round(100 * flow.approvedCount / flow.requiredApprovals) : 0"
              :status="flow.approvedCount >= flow.requiredApprovals ? 'success' : undefined"
            />
            <div style="color:#606266;font-size:12px;margin-top:4px">
              已通过 {{ flow.approvedCount }} / {{ flow.requiredApprovals }} 人，共 {{ flow.totalNodes }} 节点
            </div>
          </el-descriptions-item>
          <el-descriptions-item label="申请原因" :span="2">{{ flow.applyReason || '-' }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <!-- ③ payload 差异化显示 -->
      <section class="section" v-if="flow && payloadObj && Object.keys(payloadObj).length">
        <div class="section-head">执行参数（Payload）</div>
        <el-descriptions :column="2" border size="default">
          <template v-if="'targetWeight' in payloadObj">
            <el-descriptions-item label="灰度目标权重">
              <el-tag type="warning">{{ payloadObj.targetWeight }}%</el-tag>
            </el-descriptions-item>
          </template>
          <template v-if="'reason' in payloadObj">
            <el-descriptions-item label="回滚/执行原因" :span="2">{{ payloadObj.reason }}</el-descriptions-item>
          </template>
          <template v-for="(v, k) in payloadObj" :key="k">
            <el-descriptions-item v-if="k !== 'targetWeight' && k !== 'reason'" :label="String(k)">
              {{ typeof v === 'object' ? JSON.stringify(v) : v }}
            </el-descriptions-item>
          </template>
        </el-descriptions>
      </section>

      <!-- ④ 审批节点 -->
      <section class="section" v-if="nodes.length">
        <div class="section-head">审批节点（{{ nodes.length }}）</div>
        <el-steps :space="Math.max(180, 540 / nodes.length)" direction="horizontal" finish-status="success" align-center>
          <el-step
            v-for="n in nodes"
            :key="n.id"
            :status="({
              WAITING: '', APPROVED: 'success', REJECTED: 'error', SKIPPED: 'wait'
            } as Record<string, any>)[n.nodeStatus]"
            :title="`节点 ${n.nodeOrder}`"
            :description="`${GRAY_APPROVAL_NODE_STATUS_LABELS[n.nodeStatus]}`"
          >
            <template #icon>
              <div style="text-align:center">
                <div style="font-weight:600;margin-bottom:4px">
                  {{ n.approver ? n.approver.split('@')[0] : `角色池·${n.approverRole.replace('ROLE_','')}` }}
                </div>
                <div style="color:#909399;font-size:11px">
                  {{ n.decidedAt || '待处理' }}
                </div>
                <div v-if="n.comment" style="margin-top:4px;color:#606266;font-size:11px;max-width:160px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">
                  “{{ n.comment }}”
                </div>
              </div>
            </template>
          </el-step>
        </el-steps>
      </section>

      <!-- ⑤ 操作按钮 -->
      <section class="section actions" v-if="flow">
        <el-button
          type="success"
          plain
          :disabled="!canApprove || submitting"
          @click="openApproveDialog(true)"
        >
          ✓ 通过审批
        </el-button>
        <el-button
          type="danger"
          plain
          :disabled="!canApprove || submitting"
          @click="openApproveDialog(false)"
        >
          ✗ 拒绝审批
        </el-button>
        <el-button
          type="warning"
          plain
          :disabled="!canCancel || submitting"
          @click="cancelDialog = true"
        >
          ↶ 撤销审批单
        </el-button>
        <el-button
          type="primary"
          :disabled="!canRetry || submitting"
          :loading="submitting"
          @click="onRetryExecute"
        >
          ⟳ 重试执行
        </el-button>
        <span v-if="!canApprove && !canCancel && !canRetry" style="color:#909399;font-size:12px;margin-left:8px">
          该审批单当前状态下无可用操作；请刷新列表查看最新进度。
        </span>
      </section>

      <!-- ⑥ 审计时间线 -->
      <section class="section" v-if="audits.length">
        <div class="section-head">审计时间线（{{ audits.length }} 条）</div>
        <el-timeline>
          <el-timeline-item
            v-for="a in audits"
            :key="a.id"
            :timestamp="a.createdAt"
            :type="({
              FLOW_CREATED: 'primary', FLOW_SUBMITTED: 'primary',
              NODE_APPROVED: 'success', FLOW_APPROVED: 'success',
              FLOW_SUCCEEDED: 'success', FLOW_REJECTED: 'danger',
              FLOW_CANCELLED: 'warning', FLOW_EXECUTING: 'primary',
              FLOW_EXECUTE_FAILED: 'danger'
            } as Record<string, any>)[a.action] ?? ''"
          >
            <div class="audit-head">
              <b>{{ auditLabel(a.action) }}</b>
              <span class="audit-op">操作者：{{ a.operator }}</span>
              <el-tag v-if="a.chainTxHash" type="info" size="small" style="margin-left:8px">
                链上存证 {{ a.chainTxHash.slice(0, 10) }}…
              </el-tag>
            </div>
            <pre v-if="a.detailJson && a.detailJson !== '{}'" class="audit-detail">{{
              (() => { try {
                return JSON.stringify(JSON.parse(a.detailJson), null, 2)
              } catch { return a.detailJson } })()
            }}</pre>
          </el-timeline-item>
        </el-timeline>
      </section>

      <!-- ⑦ executeResponse -->
      <section class="section" v-if="executeResponseObj">
        <div class="section-head">执行端响应（lsc-gateway）</div>
        <el-collapse>
          <el-collapse-item title="点击查看执行端的返回结果（含错误堆栈/降级 fallback 信息）" name="er">
            <pre class="audit-detail">{{ typeof executeResponseObj === 'string' ? executeResponseObj : JSON.stringify(executeResponseObj, null, 2) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </section>
    </div>

    <!-- ==== 审批 Dialog ==== -->
    <el-dialog v-model="approveDialog" :title="approveForm.approved ? '审批通过' : '审批拒绝'" width="520px">
      <el-form
        ref="approveFormRef"
        :model="approveForm"
        :rules="approveRules"
        label-width="90px"
      >
        <el-form-item label="审批结论">
          <el-radio-group v-model="approveForm.approved" :disabled="submitting">
            <el-radio-button :value="true">通过</el-radio-button>
            <el-radio-button :value="false">拒绝</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见" prop="comment">
          <el-input v-model="approveForm.comment" type="textarea" :rows="3" maxlength="200" show-word-limit
            :placeholder="approveForm.approved ? '建议说明通过依据（如错误率/SLA/观察期）' : '请说明拒绝原因，方便申请人调整'" />
        </el-form-item>
        <el-form-item label="合规签名">
          <el-input v-model="approveForm.signature" maxlength="256" show-password placeholder="可选：密码+时间戳签名（合规场景）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="approveDialog = false">取消</el-button>
        <el-button
          :type="approveForm.approved ? 'success' : 'danger'"
          :loading="submitting"
          @click="submitApprove"
        >
          {{ approveForm.approved ? '确认通过' : '确认拒绝' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- ==== 撤销 Dialog ==== -->
    <el-dialog v-model="cancelDialog" title="撤销审批单" width="480px">
      <el-form
        ref="cancelFormRef"
        :model="cancelForm"
        :rules="cancelRules"
        label-width="90px"
      >
        <el-form-item label="撤销原因" prop="reason">
          <el-input v-model="cancelForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit
            placeholder="例如：发现新 Bug，暂缓上线；SLO 指标需要重新复核；等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="cancelDialog = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="submitCancel">确认撤销</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.approval-detail { padding: 8px 4px 24px; }
.section { margin-bottom: 22px; }
.section-head {
  font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 10px;
  padding-left: 10px; border-left: 3px solid #409EFF;
}
.status-wrap { display:flex; flex-direction:column; align-items:center; padding: 8px 0; }
.actions button { margin-right: 8px; }
.audit-head {
  display:flex; align-items:center; font-size:13px; margin-bottom:6px;
}
.audit-op { color:#909399; font-size:12px; margin-left:auto; }
.audit-detail {
  background: #f5f7fa; border-radius: 4px; padding: 8px 12px;
  font-size: 12px; color: #606266; overflow-x:auto;
  margin-top:4px; max-height: 260px;
}
</style>
