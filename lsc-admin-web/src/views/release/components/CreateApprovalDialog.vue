<script setup lang="ts">
/**
 * 创建灰度审批单 Dialog（Phase M）
 * 字段：flowType、policyId、title、applyReason、requiredApprovals、approvers、payload（按 flowType 差异化）
 * 触发：Approval.vue 顶部「新建审批单」按钮
 */
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  createGrayApproval,
  GRAY_APPROVAL_FLOW_TYPE_LABELS,
  type GrayApprovalCreateRequest,
  type GrayApprovalFlowType
} from '@/api/release'
import { useUserStore } from '@/stores/user'

interface Props {
  modelValue: boolean
  /** 可选：跳转进来时预填的灰度策略 ID（来自灰度详情页发起审批） */
  defaultPolicyId?: string
  defaultFlowType?: GrayApprovalFlowType
}
const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  defaultPolicyId: '',
  defaultFlowType: 'GRADUATE'
})
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'created', flowId: number): void
  (e: 'refresh'): void
}>()

const userStore = useUserStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

// 表单初始状态（用工厂，关闭 dialog 可彻底 reset）
function blankForm(): GrayApprovalCreateRequest & { approversText: string; targetWeight: number; rollbackReason: string } {
  return {
    flowType: props.defaultFlowType,
    policyId: props.defaultPolicyId,
    applicant: userStore.username || '',
    title: '',
    applyReason: '',
    requiredApprovals: 2,
    approvers: [],
    payload: null,
    approversText: '', // UI: comma-separated
    targetWeight: 50,  // UI: WEIGHT_CHANGE 专用
    rollbackReason: '' // UI: ROLLBACK 专用
  }
}

const form = reactive(blankForm())

const show = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

// 打开 / 关闭 → reset 表单（避免打开时残留上次内容）
watch(show, (v) => {
  if (v) Object.assign(form, blankForm())
})

/* ---------- 根据 flowType 显示/隐藏 payload 字段 ---------- */
const showTargetWeight = computed(() => form.flowType === 'WEIGHT_CHANGE')
const showRollbackReason = computed(() => form.flowType === 'ROLLBACK')
const approvalsRange: [number, number] = [1, 5]

/* ---------- 表单校验规则 ---------- */
const rules: FormRules = {
  flowType:          [{ required: true, message: '请选择动作类型', trigger: 'change' }],
  policyId:          [{ required: true, message: '请输入灰度策略 ID', trigger: 'blur' },
                      { min: 3, max: 80, message: '长度 3-80', trigger: 'blur' }],
  applicant:         [{ required: true, message: '申请人不能为空（已自动填登录用户）', trigger: 'blur' }],
  applyReason:       [{ required: true, message: '请填写申请原因（便于审批人判断）', trigger: 'blur' }],
  requiredApprovals: [{ required: true, type: 'number', min: 1, max: 5, message: '审批人数 1~5 人', trigger: 'blur' }],
  targetWeight: [
    { validator: (_r, v, cb) => {
      if (!showTargetWeight.value) return cb()
      if (v === null || v === undefined || Number.isNaN(v)) return cb(new Error('请输入目标权重'))
      if (v < 0 || v > 100) return cb(new Error('权重范围 0~100'))
      cb()
    }, trigger: 'blur' }
  ],
  rollbackReason: [
    { validator: (_r, v, cb) => {
      if (!showRollbackReason.value) return cb()
      if (!v || (typeof v === 'string' && v.trim().length < 4)) return cb(new Error('请填写回滚原因（≥4字）'))
      cb()
    }, trigger: 'blur' }
  ]
}

/* ---------- 提交 ---------- */
async function onSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (_e) {
    ElMessage.warning('请先填写并校验完整表单')
    return
  }

  // 根据 UI 字段组装 payload
  const approvers = form.approversText
    ? form.approversText.split(',').map(s => s.trim()).filter(Boolean)
    : []
  let payload: Record<string, unknown> | null = null
  if (form.flowType === 'WEIGHT_CHANGE') payload = { targetWeight: Number(form.targetWeight) }
  if (form.flowType === 'ROLLBACK')      payload = { reason: form.rollbackReason }

  submitting.value = true
  try {
    const req: GrayApprovalCreateRequest = {
      flowType: form.flowType,
      policyId: form.policyId.trim(),
      applicant: form.applicant.trim(),
      title: form.title?.trim() || undefined,
      applyReason: form.applyReason?.trim() || undefined,
      requiredApprovals: Number(form.requiredApprovals),
      approvers: approvers.length ? approvers : undefined,
      payload
    }
    const res = await createGrayApproval(req)
    if (res.code === 0) {
      ElMessage.success(`审批单已提交：${res.data.flowNo}`)
      emit('created', res.data.id)
      emit('refresh')
      show.value = false
    }
  } finally {
    submitting.value = false
  }
}

defineExpose({ open: () => (show.value = true) })
</script>

<template>
  <el-dialog
    v-model="show"
    title="新建灰度审批单"
    width="640px"
    :close-on-click-modal="false"
    :before-close="() => !submitting && (show = false)"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="110px"
      label-position="right"
      class="approval-create-form"
    >
      <el-form-item label="动作类型" prop="flowType">
        <el-select v-model="form.flowType" style="width: 100%" placeholder="请选择灰度动作类型">
          <el-option
            v-for="(label, val) in GRAY_APPROVAL_FLOW_TYPE_LABELS"
            :key="val"
            :label="label"
            :value="val"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="灰度策略ID" prop="policyId">
        <el-input
          v-model="form.policyId"
          placeholder="例：order-service-v2.1-20260904"
          clearable
        />
      </el-form-item>

      <el-form-item label="审批标题" prop="title">
        <el-input
          v-model="form.title"
          :placeholder="`默认自动生成：{动作类型}/{策略ID}`"
          maxlength="80"
          show-word-limit
          clearable
        />
      </el-form-item>

      <el-form-item label="申请原因" prop="applyReason">
        <el-input
          v-model="form.applyReason"
          type="textarea"
          :rows="3"
          maxlength="300"
          show-word-limit
          placeholder="错误率/观察期/SLA 达标情况 等审批人判断依据"
        />
      </el-form-item>

      <el-form-item label="申请人" prop="applicant">
        <el-input v-model="form.applicant" disabled />
        <span style="color:#909399;font-size:12px;margin-left:8px">已自动填写登录用户名，后端二次校验</span>
      </el-form-item>

      <el-form-item label="审批人数" prop="requiredApprovals">
        <el-input-number
          v-model="form.requiredApprovals"
          :min="approvalsRange[0]"
          :max="approvalsRange[1]"
          :step="1"
          controls-position="right"
        />
        <span style="color:#909399;font-size:12px;margin-left:8px">范围 1~5，默认 2 人</span>
      </el-form-item>

      <el-form-item label="指定审批人">
        <el-input
          v-model="form.approversText"
          placeholder="留空则走 ROLE_RELEASE_ADMIN 角色池；多人用英文逗号分隔"
          clearable
        />
      </el-form-item>

      <!-- WEIGHT_CHANGE: 目标权重 -->
      <el-form-item
        v-if="showTargetWeight"
        label="目标权重(%)"
        prop="targetWeight"
      >
        <el-slider
          v-model="form.targetWeight"
          :min="0"
          :max="100"
          :step="10"
          show-stops
          show-input
          style="width: 380px"
        />
        <span style="color:#606266;margin-left:12px">
          灰度流量从当前切到 <b>{{ form.targetWeight }}%</b>
        </span>
      </el-form-item>

      <!-- ROLLBACK: 回滚原因（与 applyReason 分开） -->
      <el-form-item
        v-if="showRollbackReason"
        label="回滚原因"
        prop="rollbackReason"
      >
        <el-input
          v-model="form.rollbackReason"
          type="textarea"
          :rows="2"
          maxlength="200"
          show-word-limit
          placeholder="例：错误率 2.3% > 1% SLO 阈值；P0 订单链路报错"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="submitting" @click="show = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">
        提交并进入审批流程
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.approval-create-form {
  padding-top: 8px;
}
</style>
