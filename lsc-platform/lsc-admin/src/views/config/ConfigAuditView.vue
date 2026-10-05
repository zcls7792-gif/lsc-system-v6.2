<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <!-- 配置变更 -->
      <el-tab-pane label="配置变更" name="config">
        <div class="toolbar">
          <el-input v-model="configGroup" placeholder="配置分组" style="width: 200px" clearable />
          <el-button type="primary" :icon="Plus" @click="openChangeForm">发起变更</el-button>
        </div>
        <el-table :data="changes" v-loading="loading" border stripe row-key="changeId">
          <el-table-column prop="changeId" label="工单ID" width="100" />
          <el-table-column prop="configGroup" label="配置分组" width="160" />
          <el-table-column prop="beforeVersion" label="当前版本" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="changeTagType(row.status)" size="small">{{ changeText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="effectiveDate" label="生效日期" width="130" />
          <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
          <el-table-column label="操作" width="240" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'PENDING_APPROVAL'" size="small" type="success" @click="handleApprove(row)">审批</el-button>
              <el-button v-if="row.status === 'PENDING_APPROVAL'" size="small" type="danger" @click="handleReject(row)">驳回</el-button>
              <el-button v-if="row.status === 'APPROVED'" size="small" type="primary" @click="handleApply(row)">生效</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-dialog v-model="showChangeForm" title="发起配置变更" width="560px">
          <el-form ref="changeFormRef" :model="changeForm" :rules="changeRules" label-width="110px">
            <el-form-item label="配置分组" prop="configGroup">
              <el-input v-model="changeForm.configGroup" placeholder="如: release.params" />
            </el-form-item>
            <el-form-item label="变更内容(JSON)" prop="proposedJson">
              <el-input v-model="changeForm.proposedJson" type="textarea" :rows="6" placeholder='{"key": "value"}' />
            </el-form-item>
            <el-form-item label="生效日期" prop="effectiveDate">
              <el-date-picker v-model="changeForm.effectiveDate" type="date" style="width: 100%" />
            </el-form-item>
            <el-form-item label="变更原因">
              <el-input v-model="changeForm.reason" type="textarea" :rows="2" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showChangeForm = false">取消</el-button>
            <el-button type="primary" :loading="submitting" @click="handleSubmitChange">提交</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- 审计日志 -->
      <el-tab-pane label="审计日志" name="audit">
        <div class="toolbar">
          <el-input v-model="auditFilter.resourceType" placeholder="资源类型" style="width: 160px" clearable />
          <el-input-number v-model="auditFilter.actorId" placeholder="操作人ID" :min="1" controls-position="right" style="width: 160px" />
          <el-button type="primary" :icon="Search" @click="loadAudits">查询</el-button>
        </div>
        <el-table :data="audits" v-loading="auditLoading" border stripe row-key="logId">
          <el-table-column prop="logId" label="日志ID" width="100" />
          <el-table-column prop="actorId" label="操作人" width="100" />
          <el-table-column prop="action" label="操作" width="160" />
          <el-table-column prop="resourceType" label="资源类型" width="120" />
          <el-table-column prop="resourceId" label="资源ID" width="100" />
          <el-table-column prop="result" label="结果" width="80">
            <template #default="{ row }">
              <el-tag :type="row.result === 'SUCCESS' ? 'success' : 'danger'" size="small">{{ row.result }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="140" show-overflow-tooltip />
          <el-table-column prop="occurredAt" label="时间" width="170" />
        </el-table>
      </el-tab-pane>

      <!-- 合规门禁 -->
      <el-tab-pane label="合规门禁" name="gate">
        <div class="toolbar">
          <el-input v-model="gateCode" placeholder="门禁编码" style="width: 200px" clearable />
          <el-button type="primary" :icon="Search" @click="verifyGate">校验门禁</el-button>
        </div>
        <el-result v-if="gateResult !== null" :icon="gateResult ? 'success' : 'error'" :title="gateResult ? '门禁通过' : '门禁未通过'" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  submitConfigChange, approveConfigChange, rejectConfigChange, applyConfigChange,
  queryAuditLog, verifyGate as verifyGateApi,
  type ConfigChange, type AuditLog
} from '@/api/modules/config'

const activeTab = ref('config')
const configGroup = ref('')

// 配置变更
const changes = ref<ConfigChange[]>([])
const loading = ref(false)
const showChangeForm = ref(false)
const submitting = ref(false)
const changeFormRef = ref<FormInstance>()
const changeForm = reactive({
  configGroup: '', proposedJson: '', effectiveDate: '', reason: ''
})
const changeRules: FormRules = {
  configGroup: [{ required: true, message: '请输入配置分组', trigger: 'blur' }],
  proposedJson: [{ required: true, message: '请输入变更内容', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }]
}

function openChangeForm() {
  Object.assign(changeForm, { configGroup: '', proposedJson: '', effectiveDate: '', reason: '' })
  showChangeForm.value = true
}

async function handleSubmitChange() {
  if (!changeFormRef.value) return
  try { await changeFormRef.value.validate() } catch { return }
  submitting.value = true
  try {
    await submitConfigChange({ ...changeForm, requesterId: 1 })
    ElMessage.success('变更已提交，等待审批')
    showChangeForm.value = false
  } finally {
    submitting.value = false
  }
}

async function handleApprove(row: ConfigChange) {
  try {
    await ElMessageBox.confirm('确认审批通过？', '提示', { type: 'success' })
    await approveConfigChange(row.changeId, 1)
    row.status = 'APPROVED'
    ElMessage.success('已审批')
  } catch {}
}

async function handleReject(row: ConfigChange) {
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回理由', '驳回', {
      confirmButtonText: '确认', cancelButtonText: '取消', inputType: 'textarea'
    })
    await rejectConfigChange(row.changeId, 1, value)
    row.status = 'REJECTED'
    ElMessage.success('已驳回')
  } catch {}
}

async function handleApply(row: ConfigChange) {
  await applyConfigChange(row.changeId)
  row.status = 'EFFECTIVE'
  ElMessage.success('配置已生效')
}

// 审计日志
const audits = ref<AuditLog[]>([])
const auditLoading = ref(false)
const auditFilter = reactive({ resourceType: '', actorId: null as number | null })

async function loadAudits() {
  auditLoading.value = true
  try {
    audits.value = await queryAuditLog({ resourceType: auditFilter.resourceType || undefined, actorId: auditFilter.actorId || undefined })
  } finally {
    auditLoading.value = false
  }
}

// 门禁
const gateCode = ref('')
const gateResult = ref<boolean | null>(null)

async function verifyGate() {
  if (!gateCode.value) return
  gateResult.value = await verifyGateApi(gateCode.value)
}

function changeText(s: string) {
  return ({ PENDING_APPROVAL: '待审批', APPROVED: '已审批', REJECTED: '已驳回', EFFECTIVE: '已生效', EXPIRED: '已失效' } as any)[s] || s
}
function changeTagType(s: string) {
  return ({ PENDING_APPROVAL: 'warning', APPROVED: 'primary', REJECTED: 'danger', EFFECTIVE: 'success', EXPIRED: 'info' } as any)[s] || ''
}
</script>
