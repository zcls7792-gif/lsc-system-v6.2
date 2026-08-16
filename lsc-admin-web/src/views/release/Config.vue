<template>
  <div class="page-container">
    <div class="table-toolbar">
      <div class="card-title">释放配置参数</div>
      <div>
        <el-button type="primary" :icon="Refresh" @click="fetchData">刷新</el-button>
        <el-tag type="info" effect="plain" style="margin-left: 8px">可配置参数修改需双人审批</el-tag>
      </div>
    </div>

    <el-alert
      title="说明：rate_max（最大释放率）与 rate_min（最小释放率）为系统核心阈值，由平台统一管控，不可在后台修改。其余参数可调整，提交后将进入双人审批流程。"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />

    <el-table v-loading="loading" :data="configList" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="key" label="参数键" width="200" />
      <el-table-column prop="name" label="参数名称" width="180" />
      <el-table-column prop="value" label="当前值" width="160">
        <template #default="{ row }">
          <el-input
            v-if="!isLocked(row.key)"
            v-model="row.value"
            size="small"
            :disabled="row.pendingApproval"
          />
          <el-input v-else v-model="row.value" size="small" disabled />
        </template>
      </el-table-column>
      <el-table-column prop="unit" label="单位" width="90" align="center" />
      <el-table-column label="属性" width="130" align="center">
        <template #default="{ row }">
          <el-tag v-if="isLocked(row.key)" type="info">系统锁定</el-tag>
          <el-tag v-else type="success">可配置</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="approvalStatus" label="审批状态" width="130" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.pendingApproval" type="warning">待审批</el-tag>
          <el-tag v-else type="success">已生效</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!isLocked(row.key)"
            link
            type="primary"
            :disabled="row.pendingApproval"
            @click="submitChange(row)"
          >提交审批</el-button>
          <span v-else style="color: #909399">-</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="双人审批确认" width="460px">
      <el-form :model="approvalForm" label-width="100px">
        <el-form-item label="参数名称">{{ approvalForm.name }}</el-form-item>
        <el-form-item label="修改为">{{ approvalForm.value }}</el-form-item>
        <el-form-item label="审批人账号">
          <el-input v-model="approvalForm.approver" placeholder="请输入审批人账号" />
        </el-form-item>
        <el-form-item label="审批人密码">
          <el-input v-model="approvalForm.password" type="password" show-password placeholder="请输入审批人密码" />
        </el-form-item>
        <el-form-item label="修改原因">
          <el-input v-model="approvalForm.reason" type="textarea" :rows="2" placeholder="请输入修改原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="confirmApproval">提交审批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getReleaseConfigList, applyParamChange } from '@/api/release'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const configList = ref<any[]>([])
const approvalForm = reactive({ id: 0, key: '', name: '', value: '', approver: '', password: '', reason: '' })

const lockedKeys = ['rate_max', 'rate_min']
function isLocked(key: string) {
  return lockedKeys.includes(key)
}

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getReleaseConfigList()
    configList.value = res.data || []
  } catch (e) {
    configList.value = []
  } finally {
    loading.value = false
  }
}

function submitChange(row: any) {
  approvalForm.id = row.id
  approvalForm.key = row.key
  approvalForm.name = row.name
  approvalForm.value = row.value
  approvalForm.approver = ''
  approvalForm.password = ''
  approvalForm.reason = ''
  dialogVisible.value = true
}

async function confirmApproval() {
  if (!approvalForm.approver || !approvalForm.password) {
    ElMessage.warning('请填写审批人账号与密码')
    return
  }
  submitting.value = true
  try {
    await applyParamChange({
      configKey: approvalForm.key,
      configValue: String(approvalForm.value),
      operator: approvalForm.approver
    })
  } catch (e) {
    submitting.value = false
    ElMessage.error('操作失败')
    return
  }
  submitting.value = false
  ElMessage.success('已提交双人审批，待审批通过后生效')
  dialogVisible.value = false
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.card-title { font-weight: 600; font-size: 16px; }
</style>
