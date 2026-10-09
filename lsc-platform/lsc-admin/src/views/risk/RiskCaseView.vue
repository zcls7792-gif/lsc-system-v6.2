<template>
  <div class="page-container">
    <div class="toolbar">
      <div class="search-bar">
        <el-select v-model="reviewStatus" placeholder="复核状态" style="width: 140px" clearable>
          <el-option label="待复核" value="PENDING" />
          <el-option label="已确认" value="CONFIRMED" />
          <el-option label="已撤销" value="REVOKED" />
          <el-option label="已降级" value="DOWNGRADED" />
        </el-select>
        <el-input v-model="userId" placeholder="用户ID" style="width: 160px" clearable />
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      </div>
      <el-button type="danger" :icon="Warning" @click="loadOverdue">查询超期</el-button>
    </div>

    <el-alert v-if="overdueCount > 0" type="error" :closable="false" style="margin-bottom: 12px">
      当前有 {{ overdueCount }} 个案件超期未复核
    </el-alert>

    <el-table :data="cases" v-loading="loading" border stripe row-key="caseId">
      <el-table-column prop="caseId" label="案件ID" width="100" />
      <el-table-column prop="userId" label="用户ID" width="100" />
      <el-table-column prop="ruleId" label="规则" width="120" />
      <el-table-column prop="aiFlag" label="AI标记" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.aiFlag ? 'danger' : 'info'">{{ row.aiFlag ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="proposedAction" label="建议处置" width="140" />
      <el-table-column prop="reviewStatus" label="复核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="reviewTagType(row.reviewStatus)" size="small">{{ reviewText(row.reviewStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reviewDeadline" label="复核截止" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.reviewStatus === 'PENDING'" size="small" type="primary" @click="openReview(row)">复核</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 复核弹窗 -->
    <el-dialog v-model="showReview" title="案件复核" width="520px">
      <el-descriptions v-if="current" :column="1" border size="small" style="margin-bottom: 16px">
        <el-descriptions-item label="案件ID">{{ current.caseId }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ current.userId }}</el-descriptions-item>
        <el-descriptions-item label="建议处置">{{ current.proposedAction }}</el-descriptions-item>
        <el-descriptions-item label="证据引用">{{ current.evidenceRef || '无' }}</el-descriptions-item>
      </el-descriptions>
      <el-form ref="reviewFormRef" :model="reviewForm" :rules="reviewRules" label-width="100px">
        <el-form-item label="复核决定" prop="decision">
          <el-radio-group v-model="reviewForm.decision">
            <el-radio value="CONFIRMED">确认处置</el-radio>
            <el-radio value="REVOKED">撤销</el-radio>
            <el-radio value="DOWNGRADED">降级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核理由">
          <el-input v-model="reviewForm.reason" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showReview = false">取消</el-button>
        <el-button type="primary" :loading="reviewing" @click="handleReview">提交复核</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Search, Warning } from '@element-plus/icons-vue'
import { findOverdueCases, reviewCase, type RiskCase } from '@/api/modules/risk'

const cases = ref<RiskCase[]>([])
const loading = ref(false)
const reviewStatus = ref('')
const userId = ref('')
const overdueCount = ref(0)

const showReview = ref(false)
const reviewing = ref(false)
const reviewFormRef = ref<FormInstance>()
const reviewForm = reactive({ decision: 'CONFIRMED', reason: '' })
const reviewRules: FormRules = {
  decision: [{ required: true, message: '请选择复核决定', trigger: 'change' }]
}
const current = ref<RiskCase | null>(null)

async function loadData() {
  // 实际应调用分页查询接口，此处简化
  loading.value = true
  try {
    cases.value = []
  } finally {
    loading.value = false
  }
}

async function loadOverdue() {
  const ids = await findOverdueCases()
  overdueCount.value = ids.length
  ElMessage.info(`超期案件数: ${ids.length}`)
}

function openReview(row: RiskCase) {
  current.value = row
  reviewForm.decision = 'CONFIRMED'
  reviewForm.reason = ''
  showReview.value = true
}

async function handleReview() {
  if (!reviewFormRef.value || !current.value) return
  try { await reviewFormRef.value.validate() } catch { return }
  reviewing.value = true
  try {
    await reviewCase(current.value.caseId, 1, reviewForm.decision, reviewForm.reason)
    ElMessage.success('复核已提交')
    showReview.value = false
    loadData()
  } finally {
    reviewing.value = false
  }
}

function reviewText(s: string) {
  return ({ PENDING: '待复核', CONFIRMED: '已确认', REVOKED: '已撤销', DOWNGRADED: '已降级' } as any)[s] || s
}
function reviewTagType(s: string) {
  return ({ PENDING: 'warning', CONFIRMED: 'success', REVOKED: 'info', DOWNGRADED: 'primary' } as any)[s] || ''
}
</script>
