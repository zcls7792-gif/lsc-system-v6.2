<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="批次号">
        <el-input v-model="query.batchNo" placeholder="核销批次号" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="待核销" value="pending" />
          <el-option label="已核销" value="success" />
          <el-option label="核销失败" value="failed" />
        </el-select>
      </el-form-item>
      <el-form-item label="商家ID">
        <el-input v-model="query.merchantId" placeholder="商家ID" clearable style="width: 120px" />
      </el-form-item>
      <el-form-item label="核销时间">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          @change="handleDateChange"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="batchNo" label="批次号" width="200" show-overflow-tooltip />
      <el-table-column prop="merchantName" label="商家" min-width="140" show-overflow-tooltip />
      <el-table-column prop="orderNo" label="关联订单" width="200" show-overflow-tooltip />
      <el-table-column prop="lscAmount" label="核销LSC数量" width="130" align="right" />
      <el-table-column prop="operator" label="核销人" width="110" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="writeoffTime" label="核销时间" width="170" />
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
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getWriteoffList } from '@/api/writeoff'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1, size: 10, batchNo: '', status: '', merchantId: '', startDate: '', endDate: ''
})

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getWriteoffList(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function statusText(s: string) { return ({ pending: '待核销', success: '已核销', failed: '核销失败' } as any)[s] }
function statusTagType(s: string) { return ({ pending: 'warning', success: 'success', failed: 'danger' } as any)[s] }

function handleDateChange(val: [string, string] | null) {
  if (val) { query.startDate = val[0]; query.endDate = val[1] }
  else { query.startDate = ''; query.endDate = '' }
}
function handleSearch() { query.page = 1; fetchData() }
function handleReset() {
  query.batchNo = ''; query.status = ''; query.merchantId = ''
  dateRange.value = null; query.startDate = ''; query.endDate = ''
  handleSearch()
}
function handleView(row: any) { ElMessage.info(`查看批次 ${row.batchNo}`) }

onMounted(fetchData)
</script>
