<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="商家名称/账号" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="审核状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="待审核" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已驳回" value="rejected" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="name" label="商家名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="legalPerson" label="法人" width="100" />
      <el-table-column prop="phone" label="联系电话" width="130" />
      <el-table-column prop="aiRiskScore" label="AI风险评分" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="riskTagType(row.aiRiskScore)" effect="dark">
            {{ row.aiRiskScore }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="applyTime" label="申请时间" width="170" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openAudit(row)">审核</el-button>
        </template>
      </el-table-column>
    </el-table>

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

    <el-dialog v-model="dialogVisible" title="商家审核详情" width="720px">
      <el-descriptions :column="2" border v-if="current">
        <el-descriptions-item label="商家名称">{{ current.name }}</el-descriptions-item>
        <el-descriptions-item label="法人代表">{{ current.legalPerson }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ current.phone }}</el-descriptions-item>
        <el-descriptions-item label="统一社会信用代码">{{ current.creditCode }}</el-descriptions-item>
        <el-descriptions-item label="经营地址" :span="2">{{ current.address }}</el-descriptions-item>
        <el-descriptions-item label="营业执照" :span="2">
          <el-image
            style="width: 200px; height: 120px"
            :src="current.licenseUrl"
            :preview-src-list="[current.licenseUrl]"
            fit="cover"
          >
            <template #error>
              <div class="img-placeholder">暂无图片</div>
            </template>
          </el-image>
        </el-descriptions-item>
        <el-descriptions-item label="AI风险评分">
          <el-tag :type="riskTagType(current.aiRiskScore)" effect="dark">{{ current.aiRiskScore }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AI风险提示">{{ current.aiRiskTip || '无明显风险' }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="auditForm" label-width="90px" style="margin-top: 20px">
        <el-form-item label="审核结果">
          <el-radio-group v-model="auditForm.status">
            <el-radio value="approved">通过</el-radio>
            <el-radio value="rejected">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="auditForm.reason" type="textarea" :rows="3" placeholder="请输入审核意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAudit">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getAuditList, auditMerchant } from '@/api/merchant'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const current = ref<any>(null)

const query = reactive({ page: 1, size: 10, keyword: '', status: '' })
const auditForm = reactive({ status: 'approved', reason: '' })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getAuditList(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function riskTagType(score: number) {
  if (score >= 70) return 'danger'
  if (score >= 40) return 'warning'
  return 'success'
}
function statusText(status: string) {
  return { pending: '待审核', approved: '已通过', rejected: '已驳回' }[status] || status
}
function statusTagType(status: string) {
  return ({ pending: 'warning', approved: 'success', rejected: 'danger' } as any)[status]
}

function handleSearch() { query.page = 1; fetchData() }
function handleReset() { query.keyword = ''; query.status = ''; handleSearch() }

function openAudit(row: any) {
  current.value = row
  auditForm.status = 'approved'
  auditForm.reason = ''
  dialogVisible.value = true
}

async function submitAudit() {
  if (!current.value) return
  submitting.value = true
  try {
    await auditMerchant(current.value.id, auditForm)
    current.value.status = auditForm.status
    ElMessage.success('审核完成')
    dialogVisible.value = false
    fetchData()
  } catch (e) {
    ElMessage.error('审核操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped>
.img-placeholder {
  width: 200px;
  height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  color: #909399;
  font-size: 12px;
}
</style>
