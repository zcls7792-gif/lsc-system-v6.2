<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="对账日期">
        <el-date-picker
          v-model="query.date"
          type="date"
          placeholder="选择日期"
          value-format="YYYY-MM-DD"
          style="width: 180px"
        />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option label="已平账" value="balanced" />
          <el-option label="存在差异" value="diff" />
          <el-option label="待对账" value="pending" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="RefreshRight" @click="handleTriggerReconcile">触发对账</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="date" label="对账日期" width="130" />
      <el-table-column prop="ledgerAmount" label="账面金额" width="140" align="right">
        <template #default="{ row }">¥{{ Number(row.ledgerAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="actualAmount" label="实际金额" width="140" align="right">
        <template #default="{ row }">¥{{ Number(row.actualAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="diffAmount" label="差异金额" width="140" align="right">
        <template #default="{ row }">
          <span :style="{ color: row.diffAmount === 0 ? '#67c23a' : '#f56c6c' }">
            {{ row.diffAmount >= 0 ? '+' : '' }}¥{{ Number(row.diffAmount).toFixed(2) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="releaseLsc" label="释放LSC" width="120" align="right" />
      <el-table-column prop="writeoffLsc" label="核销LSC" width="120" align="right" />
      <el-table-column prop="status" label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="reconciledAt" label="对账时间" width="170" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">详情</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, RefreshRight } from '@element-plus/icons-vue'
import { getReconcileReport, triggerReconcile } from '@/api/reconcile'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 10, date: '', status: '' })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getReconcileReport(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function statusText(s: string) { return ({ balanced: '已平账', diff: '存在差异', pending: '待对账' } as any)[s] }
function statusTagType(s: string) { return ({ balanced: 'success', diff: 'danger', pending: 'warning' } as any)[s] }

function handleSearch() { query.page = 1; fetchData() }
function handleReset() { query.date = ''; query.status = ''; handleSearch() }
function handleView(row: any) { ElMessage.info(`查看 ${row.date} 对账详情`) }

async function handleTriggerReconcile() {
  const date = query.date || new Date().toISOString().slice(0, 10)
  await ElMessageBox.confirm(`确定要对 ${date} 触发对账吗？`, '提示', { type: 'warning' })
  try { await triggerReconcile({ date }) } catch (e) { ElMessage.error('操作失败') }
  ElMessage.success('对账任务已触发')
  fetchData()
}

onMounted(fetchData)
</script>
