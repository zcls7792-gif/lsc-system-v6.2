<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="商品名称" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="AI结果">
        <el-select v-model="query.aiResult" placeholder="全部" clearable style="width: 140px">
          <el-option label="AI通过" value="pass" />
          <el-option label="待复核" value="review" />
          <el-option label="AI驳回" value="reject" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="merchantName" label="所属商家" width="140" show-overflow-tooltip />
      <el-table-column prop="submitTime" label="提交时间" width="170" />
      <el-table-column label="AI审核结果" width="160" align="center">
        <template #default="{ row }">
          <el-tag :type="aiTagType(row.aiResult)" effect="dark" class="ai-tag">
            <el-icon><MagicStick /></el-icon>{{ aiLabelText(row.aiResult) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="aiConfidence" label="置信度" width="100" align="center">
        <template #default="{ row }">{{ (row.aiConfidence * 100).toFixed(1) }}%</template>
      </el-table-column>
      <el-table-column prop="aiIssues" label="AI发现问题" min-width="180" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openReview(row)">人工复核</el-button>
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

    <el-dialog v-model="dialogVisible" title="人工复核" width="640px">
      <el-descriptions :column="2" border v-if="current">
        <el-descriptions-item label="商品名称" :span="2">{{ current.name }}</el-descriptions-item>
        <el-descriptions-item label="所属商家">{{ current.merchantName }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ current.submitTime }}</el-descriptions-item>
        <el-descriptions-item label="AI审核结果">
          <el-tag :type="aiTagType(current.aiResult)" effect="dark">{{ aiLabelText(current.aiResult) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AI置信度">{{ (current.aiConfidence * 100).toFixed(1) }}%</el-descriptions-item>
        <el-descriptions-item label="AI发现问题" :span="2">{{ current.aiIssues }}</el-descriptions-item>
      </el-descriptions>
      <el-form :model="reviewForm" label-width="90px" style="margin-top: 20px">
        <el-form-item label="复核结果">
          <el-radio-group v-model="reviewForm.status">
            <el-radio value="approved">通过</el-radio>
            <el-radio value="rejected">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复核意见">
          <el-input v-model="reviewForm.reason" type="textarea" :rows="3" placeholder="请输入复核意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReview">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, MagicStick } from '@element-plus/icons-vue'
import { getProductAuditList, reviewProduct } from '@/api/product'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const current = ref<any>(null)

const query = reactive({ page: 1, size: 10, keyword: '', aiResult: '' })
const reviewForm = reactive({ status: 'approved', reason: '' })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getProductAuditList(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function aiLabelText(r: string) { return ({ pass: 'AI通过', review: '待复核', reject: 'AI驳回' } as any)[r] }
function aiTagType(r: string) { return ({ pass: 'success', review: 'warning', reject: 'danger' } as any)[r] }

function handleSearch() { query.page = 1; fetchData() }
function handleReset() { query.keyword = ''; query.aiResult = ''; handleSearch() }

function openReview(row: any) {
  current.value = row
  reviewForm.status = 'approved'
  reviewForm.reason = ''
  dialogVisible.value = true
}

async function submitReview() {
  if (!current.value) return
  submitting.value = true
  try {
    await reviewProduct(current.value.id, reviewForm)
  } catch (e) { ElMessage.error('操作失败') }
  submitting.value = false
  ElMessage.success('复核完成')
  dialogVisible.value = false
  fetchData()
}

onMounted(fetchData)
</script>
