<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="订单号">
        <el-input v-model="query.orderNo" placeholder="B2B订单号" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="核验状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
          <el-option label="待复核" value="pending" />
          <el-option label="已确认" value="confirmed" />
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
      <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
      <el-table-column prop="buyerName" label="采购方" min-width="130" show-overflow-tooltip />
      <el-table-column prop="sellerName" label="供应方" min-width="130" show-overflow-tooltip />
      <el-table-column prop="amount" label="金额" width="120" align="right">
        <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="AI核验结果" width="180" align="center">
        <template #default="{ row }">
          <el-tag :type="row.aiScore >= 80 ? 'success' : 'warning'" effect="dark">
            <el-icon><MagicStick /></el-icon>AI分: {{ row.aiScore }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openVerify(row)">复核</el-button>
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

    <el-dialog v-model="dialogVisible" title="B2B核验复核" width="760px">
      <template v-if="current">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ current.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="交易金额">¥{{ Number(current.amount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="采购方">{{ current.buyerName }}</el-descriptions-item>
          <el-descriptions-item label="供应方">{{ current.sellerName }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">AI核验结果</el-divider>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="AI核验分">
            <el-tag :type="current.aiScore >= 80 ? 'success' : 'warning'" effect="dark">{{ current.aiScore }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="AI结论">{{ current.aiConclusion }}</el-descriptions-item>
          <el-descriptions-item label="风险点" :span="2">{{ current.aiRiskPoints }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">贸易凭证</el-divider>
        <div class="docs">
          <el-card v-for="(doc, idx) in current.documents" :key="idx" class="doc-card" shadow="hover" @click="previewDoc(doc)">
            <el-icon><Document /></el-icon>
            <span>{{ doc.name }}</span>
          </el-card>
          <el-empty v-if="!current.documents?.length" description="无凭证" :image-size="60" />
        </div>

        <el-divider content-position="left">人工确认</el-divider>
        <el-form :model="verifyForm" label-width="90px">
          <el-form-item label="复核结论">
            <el-radio-group v-model="verifyForm.result">
              <el-radio value="confirmed">确认通过</el-radio>
              <el-radio value="rejected">驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="verifyForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitVerify">提交确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, MagicStick, Document } from '@element-plus/icons-vue'
import { getB2BList, confirmB2BVerify } from '@/api/b2b'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const current = ref<any>(null)

const query = reactive({ page: 1, size: 10, orderNo: '', status: '' })
const verifyForm = reactive({ result: 'confirmed', remark: '' })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getB2BList(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function statusText(s: string) { return ({ pending: '待复核', confirmed: '已确认', rejected: '已驳回' } as any)[s] }
function statusTagType(s: string) { return ({ pending: 'warning', confirmed: 'success', rejected: 'danger' } as any)[s] }

function handleSearch() { query.page = 1; fetchData() }
function handleReset() { query.orderNo = ''; query.status = ''; handleSearch() }

function openVerify(row: any) {
  current.value = row
  verifyForm.result = 'confirmed'
  verifyForm.remark = ''
  dialogVisible.value = true
}

function previewDoc(doc: any) { ElMessage.info(`预览凭证：${doc.name}`) }

async function submitVerify() {
  if (!current.value) return
  submitting.value = true
  try {
    await confirmB2BVerify(current.value.orderNo, verifyForm)
  } catch (e) { ElMessage.error('操作失败') }
  submitting.value = false
  ElMessage.success('已提交确认')
  dialogVisible.value = false
  fetchData()
}

onMounted(fetchData)
</script>

<style scoped>
.docs {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.doc-card {
  cursor: pointer;
  width: 160px;
}
.doc-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  font-size: 13px;
}
</style>
