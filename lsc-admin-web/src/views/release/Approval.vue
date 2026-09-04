<script setup lang="ts">
/**
 * 灰度审批列表页（Phase M 前端入口）
 * 路由：/release/approval
 * 功能：
 *   1) 顶部筛选：关键字 / flowType / status / 日期范围
 *   2) 工具条：刷新 + 新建审批单
 *   3) 主表格：14 列（flowNo / 动作 / 策略 / 标题 / 申请人 / 进度 / 状态 / 创建 / 操作）
 *   4) 操作列：查看详情 / 我要审批 / 撤销 / 重试执行
 *   5) 底部分页：el-pagination（10 / 20 / 50）
 *   6) 全局 v-loading；无数据空状态提示
 */
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, DatePickerType } from 'element-plus'
import { useUserStore } from '@/stores/user'
import {
  listGrayApprovals,
  approveGrayApproval,
  cancelGrayApproval,
  retryExecuteGrayApproval,
  GRAY_APPROVAL_FLOW_TYPE_LABELS,
  GRAY_APPROVAL_STATUS_META,
  type GrayApprovalFlowType,
  type GrayApprovalFlowStatus,
  type GrayApprovalFlowVO,
  type GrayApprovalQueryParams
} from '@/api/release'
import CreateApprovalDialog from './components/CreateApprovalDialog.vue'
import ApprovalDetailDrawer from './components/ApprovalDetailDrawer.vue'

const userStore = useUserStore()

/* -------- 查询表单 -------- */
const queryForm = reactive<GrayApprovalQueryParams & { dateRange?: Date[] }>({
  page: 1,
  size: 20,
  keyword: '',
  flowType: '',
  status: '',
  applicant: '',
  dateRange: [] as Date[]
})
const queryFormRef = ref<FormInstance>()
const queryRules: FormRules = {
  keyword: [{ min: 0, max: 64, message: '关键字 ≤64 字符', trigger: 'blur' }],
  applicant: [{ min: 0, max: 64, message: '申请人 ≤64 字符', trigger: 'blur' }]
}

/* -------- 表格数据 -------- */
const loading = ref(false)
const total = ref(0)
const tableData = ref<GrayApprovalFlowVO[]>([])

async function fetchData() {
  loading.value = true
  try {
    const params: GrayApprovalQueryParams = {
      page: queryForm.page,
      size: queryForm.size,
      keyword: queryForm.keyword?.trim() || undefined,
      flowType: queryForm.flowType || undefined,
      status: queryForm.status || undefined,
      applicant: queryForm.applicant?.trim() || undefined,
      startDate: queryForm.dateRange?.length === 2 ? fmtDate(queryForm.dateRange[0]) : undefined,
      endDate: queryForm.dateRange?.length === 2 ? fmtDate(queryForm.dateRange[1]) : undefined
    }
    const res = await listGrayApprovals(params)
    if (res.code === 0) {
      tableData.value = res.data.list ?? []
      total.value = res.data.total ?? 0
    }
  } finally {
    loading.value = false
  }
}

function fmtDate(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

onMounted(fetchData)

/* -------- 筛选操作 -------- */
function onQuery() { queryForm.page = 1; fetchData() }
function onReset() {
  queryForm.keyword = ''
  queryForm.flowType = ''
  queryForm.status = ''
  queryForm.applicant = ''
  queryForm.dateRange = []
  onQuery()
}
function onPageChange(p: number) { queryForm.page = p; fetchData() }
function onSizeChange(s: number) { queryForm.size = s; queryForm.page = 1; fetchData() }

/* -------- 创建审批单 Dialog -------- */
const showCreate = ref(false)
function onCreated(_flowId: number) { fetchData() }

/* -------- 详情 Drawer -------- */
const showDetail = ref(false)
const detailFlowId = ref<number | null>(null)
function openDetail(row: GrayApprovalFlowVO) {
  detailFlowId.value = row.id
  showDetail.value = true
}
function onDetailChanged() { fetchData() }

/* -------- 列内快捷操作（权限判断 = 后端会再次校验，前端仅做按钮显隐） -------- */
function canCancel(row: GrayApprovalFlowVO) {
  const applicantOk = !!(row.applicant === userStore.username)
  return applicantOk && (row.status === 'DRAFT' || row.status === 'PENDING_APPROVAL')
}
function canApprove(row: GrayApprovalFlowVO) {
  return row.status === 'PENDING_APPROVAL'
}
function canRetry(row: GrayApprovalFlowVO) {
  return row.status === 'EXECUTE_FAILED' || row.status === 'APPROVED'
}

async function quickApprove(row: GrayApprovalFlowVO, approved: boolean) {
  const verb = approved ? '通过' : '拒绝'
  if (approved) {
    try {
      await ElMessageBox.confirm(
        `将快捷审批「通过」 ${row.flowNo}：跳过审批意见直接确认？\n（若要写理由请点「详情」）`,
        '确认通过', { type: 'warning', confirmButtonText: '确认通过', cancelButtonText: '去详情写理由' }
      )
    } catch { return openDetail(row) }
  } else {
    try {
      await ElMessageBox.confirm(`确认快捷审批「拒绝」该审批单 ${row.flowNo}？（拒绝后无法恢复）`,
        '确认拒绝', { type: 'error', confirmButtonText: '确认拒绝', cancelButtonText: '取消' })
    } catch { return }
  }
  const res = await approveGrayApproval({
    flowId: row.id,
    approver: userStore.username || '',
    approved,
    comment: approved ? '（快捷审批：同意）' : '（快捷审批：拒绝）'
  })
  if (res.code === 0) {
    ElMessage.success(`已${verb}：${row.flowNo}`)
    fetchData()
  }
}
async function onCancel(row: GrayApprovalFlowVO) {
  try {
    await ElMessageBox.prompt('请输入撤销理由（≥4 字）', '撤销审批单', {
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消',
      inputPattern: /^.{4,}$/,
      inputErrorMessage: '撤销理由 4~200 字'
    }).then(async ({ value }) => {
      const res = await cancelGrayApproval({
        flowId: row.id,
        operator: userStore.username || '',
        reason: value
      })
      if (res.code === 0) {
        ElMessage.success('已撤销：' + row.flowNo)
        fetchData()
      }
    }).catch(() => {})
  } catch { /* cancel */ }
}
async function onRetry(row: GrayApprovalFlowVO) {
  try {
    await ElMessageBox.confirm(
      `将重新调用 lsc-gateway 执行端处理 ${row.flowNo}；\n若仍失败会再次进入 EXECUTE_FAILED。是否继续？`,
      '重试执行', { type: 'warning', confirmButtonText: '确认重试', cancelButtonText: '取消' }
    )
  } catch { return }
  const res = await retryExecuteGrayApproval({ flowId: row.id, operator: userStore.username || '' })
  if (res.code === 0) {
    ElMessage.success(res.data.status === 'SUCCEEDED' ? '执行成功 ✅' : `重试提交：${res.data.status}`)
    fetchData()
  }
}

/* -------- 下拉选项：动态下拉 flowType / status -------- */
const FLOW_TYPE_OPTIONS = computed(() =>
  (Object.keys(GRAY_APPROVAL_FLOW_TYPE_LABELS) as GrayApprovalFlowType[])
    .map(v => ({ value: v, label: GRAY_APPROVAL_FLOW_TYPE_LABELS[v] })))
const STATUS_OPTIONS = computed(() =>
  (Object.keys(GRAY_APPROVAL_STATUS_META) as GrayApprovalFlowStatus[])
    .map(v => ({ value: v, label: GRAY_APPROVAL_STATUS_META[v].label })))
</script>

<template>
  <div class="page-container gray-approval-page">
    <!-- ============ 筛选区 ============ -->
    <div class="filter-bar card-shadow">
      <el-form
        ref="queryFormRef"
        :model="queryForm"
        :rules="queryRules"
        label-width="80px"
        inline
        class="filter-form"
      >
        <el-form-item label="关键字" prop="keyword">
          <el-input
            v-model="queryForm.keyword"
            placeholder="搜索 审批编号 / 策略ID / 标题"
            clearable
            style="width: 260px"
            @keyup.enter="onQuery"
          />
        </el-form-item>
        <el-form-item label="动作类型">
          <el-select
            v-model="queryForm.flowType"
            placeholder="全部"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="o in FLOW_TYPE_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部"
            clearable
            style="width: 140px"
          >
            <el-option
              v-for="o in STATUS_OPTIONS"
              :key="o.value"
              :label="o.label"
              :value="o.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="申请人" prop="applicant">
          <el-input
            v-model="queryForm.applicant"
            placeholder="用户邮箱/用户名"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="创建日期">
          <el-date-picker
            v-model="queryForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="x"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="onQuery">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- ============ 工具条 ============ -->
    <div class="table-toolbar">
      <div class="card-title">灰度审批列表</div>
      <div>
        <el-tag type="info" effect="plain" class="mr8">
          共 <b>{{ total }}</b> 条
        </el-tag>
        <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="showCreate = true">
          新建审批单
        </el-button>
      </div>
    </div>

    <!-- ============ 主表格 ============ -->
    <div class="card-shadow">
      <el-table
        v-loading="loading"
        :data="tableData"
        border
        stripe
        style="width: 100%"
      >
        <el-table-column type="index" label="#" width="56" align="center" />
        <el-table-column label="审批编号" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" @click="openDetail(row)">
              <b>{{ row.flowNo }}</b>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="动作" width="110" align="center">
          <template #default="{ row }">
            <el-tag
              :type="({GRADUATE:'primary',WEIGHT_CHANGE:'warning',ROLLBACK:'danger',LAUNCH:'info'} as Record<string,any>)[row.flowType]"
              effect="plain"
            >
              {{ GRAY_APPROVAL_FLOW_TYPE_LABELS[row.flowType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="灰度策略ID" min-width="200" show-overflow-tooltip prop="policyId" />
        <el-table-column label="标题" min-width="200" show-overflow-tooltip prop="title" />
        <el-table-column label="申请人" width="160" prop="applicant" />
        <el-table-column label="审批进度" width="180" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="row.requiredApprovals ? Math.round(100 * row.approvedCount / row.requiredApprovals) : 0"
              :status="row.approvedCount >= row.requiredApprovals ? 'success' : undefined"
              :stroke-width="10"
            />
            <div style="color:#606266;font-size:12px;margin-top:2px">
              {{ row.approvedCount }} / {{ row.requiredApprovals }} 人通过
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="GRAY_APPROVAL_STATUS_META[row.status].type">
              {{ GRAY_APPROVAL_STATUS_META[row.status].label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="执行耗时" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.executeCostMs != null" style="color:#606266">
              {{ row.executeCostMs }} ms
            </span>
            <span v-else style="color:#C0C4CC">-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170" prop="createdAt" />
        <el-table-column label="操作" width="310" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="canApprove(row)"
              link
              type="success"
              @click="quickApprove(row, true)"
            >通过</el-button>
            <el-button
              v-if="canApprove(row)"
              link
              type="danger"
              @click="quickApprove(row, false)"
            >拒绝</el-button>
            <el-button
              v-if="canCancel(row)"
              link
              type="warning"
              @click="onCancel(row)"
            >撤销</el-button>
            <el-button
              v-if="canRetry(row)"
              link
              type="primary"
              @click="onRetry(row)"
            >重试执行</el-button>
          </template>
        </el-table-column>

        <!-- 空状态 -->
        <template #empty>
          <el-empty description="暂无审批单">
            <el-button type="primary" @click="showCreate = true">新建审批单</el-button>
          </el-empty>
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryForm.page"
          v-model:page-size="queryForm.size"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="onSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <!-- ============ 子组件 ============ -->
    <CreateApprovalDialog
      v-model="showCreate"
      @created="onCreated"
      @refresh="fetchData"
    />
    <ApprovalDetailDrawer
      v-model="showDetail"
      :flow-id="detailFlowId"
      @changed="onDetailChanged"
    />
  </div>
</template>

<style scoped>
.gray-approval-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.filter-bar {
  padding: 14px 18px 4px;
  background: white;
  border-radius: 6px;
}
.filter-form {
  display: flex;
  flex-wrap: wrap;
}
.mr8 { margin-right: 8px; }
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 12px 6px 16px;
}
</style>
