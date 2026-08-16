<template>
  <div class="page-container">
    <div class="table-toolbar">
      <div class="card-title">参数审批</div>
      <el-tag type="warning" effect="plain">关键参数修改需双人审批</el-tag>
    </div>

    <el-tabs v-model="activeTab" @tab-change="fetchData">
      <el-tab-pane label="待审批" name="pending">
        <el-table v-loading="loading" :data="tableData" border stripe>
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="module" label="所属模块" width="130" align="center" />
          <el-table-column prop="paramKey" label="参数键" width="180" show-overflow-tooltip />
          <el-table-column prop="paramName" label="参数名称" width="160" />
          <el-table-column prop="oldValue" label="原值" width="120" />
          <el-table-column prop="newValue" label="新值" width="120">
            <template #default="{ row }">
              <span style="color: #f56c6c; font-weight: 600">{{ row.newValue }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="applicant" label="申请人" width="110" />
          <el-table-column prop="reason" label="申请原因" min-width="180" show-overflow-tooltip />
          <el-table-column prop="createdAt" label="申请时间" width="170" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button link type="success" @click="openApprove(row, 'approved')">同意</el-button>
              <el-button link type="danger" @click="openApprove(row, 'rejected')">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="已审批" name="history">
        <el-table v-loading="loading" :data="tableData" border stripe>
          <el-table-column type="index" label="序号" width="60" align="center" />
          <el-table-column prop="module" label="所属模块" width="130" align="center" />
          <el-table-column prop="paramKey" label="参数键" width="180" show-overflow-tooltip />
          <el-table-column prop="paramName" label="参数名称" width="160" />
          <el-table-column prop="oldValue" label="原值" width="120" />
          <el-table-column prop="newValue" label="新值" width="120" />
          <el-table-column prop="applicant" label="申请人" width="110" />
          <el-table-column prop="approver" label="审批人" width="110" />
          <el-table-column prop="result" label="审批结果" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="row.result === 'approved' ? 'success' : 'danger'">
                {{ row.result === 'approved' ? '已通过' : '已驳回' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approvalRemark" label="审批意见" min-width="160" show-overflow-tooltip />
          <el-table-column prop="approvedAt" label="审批时间" width="170" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="approveForm.result === 'approved' ? '同意参数修改' : '驳回参数修改'" width="460px">
      <el-descriptions :column="1" border v-if="current">
        <el-descriptions-item label="参数名称">{{ current.paramName }}</el-descriptions-item>
        <el-descriptions-item label="原值">{{ current.oldValue }}</el-descriptions-item>
        <el-descriptions-item label="新值">{{ current.newValue }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ current.applicant }}</el-descriptions-item>
        <el-descriptions-item label="申请原因">{{ current.reason }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="approveForm" label-width="100px" style="margin-top: 16px">
        <el-form-item label="审批人账号">
          <el-input v-model="approveForm.approver" placeholder="请输入审批人账号" />
        </el-form-item>
        <el-form-item label="审批人密码">
          <el-input v-model="approveForm.password" type="password" show-password placeholder="请输入审批人密码" />
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="approveForm.remark" type="textarea" :rows="2" placeholder="请输入审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button :type="approveForm.result === 'approved' ? 'success' : 'danger'" :loading="submitting" @click="submitApproval">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getReleaseConfigList, approveParamChange } from '@/api/release'

const loading = ref(false)
const submitting = ref(false)
const activeTab = ref('pending')
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const current = ref<any>(null)

const query = reactive({ page: 1, size: 10, status: 'pending' })
const approveForm = reactive({ id: 0, result: 'approved', approver: '', password: '', remark: '' })

async function fetchData() {
  query.status = activeTab.value === 'pending' ? 'pending' : 'history'
  loading.value = true
  try {
    const res: any = await getReleaseConfigList()
    tableData.value = res.data?.records || res.data || []
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function openApprove(row: any, result: string) {
  current.value = row
  approveForm.id = row.id
  approveForm.result = result
  approveForm.approver = ''
  approveForm.password = ''
  approveForm.remark = ''
  dialogVisible.value = true
}

async function submitApproval() {
  if (!approveForm.approver || !approveForm.password) {
    ElMessage.warning('请填写审批人账号与密码')
    return
  }
  submitting.value = true
  try {
    await approveParamChange({
      approvalId: approveForm.id,
      approver: approveForm.approver,
      approverSignatures: approveForm.password,
      approveComment: approveForm.remark,
      approved: approveForm.result === 'approved'
    })
  } catch (e) {
    submitting.value = false
    ElMessage.error('操作失败')
    return
  }
  submitting.value = false
  ElMessage.success(approveForm.result === 'approved' ? '已同意参数修改' : '已驳回参数修改')
  dialogVisible.value = false
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.card-title { font-weight: 600; font-size: 16px; }
.table-toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
</style>
