<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="批次号">
        <el-input v-model="query.batchNo" placeholder="批次号" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="哈希">
        <el-input v-model="query.hash" placeholder="存证哈希" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="交易ID">
        <el-input v-model="query.txId" placeholder="链上交易ID" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="存证时间">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
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
      <el-table-column prop="hash" label="存证哈希" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="hash-text">{{ row.hash }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="txId" label="链上交易ID" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="hash-text">{{ row.txId }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="dataType" label="数据类型" width="120" align="center">
        <template #default="{ row }">{{ row.dataType }}</template>
      </el-table-column>
      <el-table-column prop="dataCount" label="数据条数" width="100" align="center" />
      <el-table-column prop="blockHeight" label="区块高度" width="120" align="center" />
      <el-table-column prop="verified" label="校验状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.verified ? 'success' : 'danger'">{{ row.verified ? '已校验' : '未校验' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="存证时间" width="170" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">详情</el-button>
          <el-button link type="success" @click="goVerify">校验</el-button>
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
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { getEvidenceList } from '@/api/evidence'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1, size: 10, batchNo: '', hash: '', txId: '', startDate: '', endDate: ''
})

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getEvidenceList(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleDateChange(val: [string, string] | null) {
  if (val) { query.startDate = val[0]; query.endDate = val[1] }
  else { query.startDate = ''; query.endDate = '' }
}
function handleSearch() { query.page = 1; fetchData() }
function handleReset() {
  query.batchNo = ''; query.hash = ''; query.txId = ''
  dateRange.value = null; query.startDate = ''; query.endDate = ''
  handleSearch()
}
function handleView(row: any) { ElMessage.info(`查看存证 ${row.batchNo}`) }
function goVerify() { router.push('/evidence/verify') }

onMounted(fetchData)
</script>

<style scoped>
.hash-text {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #606266;
}
</style>
