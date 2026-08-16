<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="订单号">
        <el-input v-model="query.orderNo" placeholder="B2B订单号" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 150px">
          <el-option label="待核验" value="pending" />
          <el-option label="核验中" value="verifying" />
          <el-option label="核验通过" value="passed" />
          <el-option label="核验失败" value="failed" />
        </el-select>
      </el-form-item>
      <el-form-item label="下单时间">
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
      <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
      <el-table-column prop="buyerName" label="采购方" min-width="140" show-overflow-tooltip />
      <el-table-column prop="sellerName" label="供应方" min-width="140" show-overflow-tooltip />
      <el-table-column prop="amount" label="交易金额" width="120" align="right">
        <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="数量" width="90" align="center" />
      <el-table-column prop="aiVerifyScore" label="AI核验分" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.aiVerifyScore >= 80 ? 'success' : row.aiVerifyScore >= 60 ? 'warning' : 'danger'">
            {{ row.aiVerifyScore }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="下单时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">详情</el-button>
          <el-button link type="warning" @click="goVerify(row)">核验</el-button>
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
import { getB2BList } from '@/api/b2b'

const router = useRouter()
const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1, size: 10, orderNo: '', status: '', startDate: '', endDate: ''
})

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

function statusText(s: string) {
  return ({ pending: '待核验', verifying: '核验中', passed: '核验通过', failed: '核验失败' } as any)[s]
}
function statusTagType(s: string) {
  return ({ pending: 'warning', verifying: 'primary', passed: 'success', failed: 'danger' } as any)[s]
}

function handleDateChange(val: [string, string] | null) {
  if (val) { query.startDate = val[0]; query.endDate = val[1] }
  else { query.startDate = ''; query.endDate = '' }
}
function handleSearch() { query.page = 1; fetchData() }
function handleReset() {
  query.orderNo = ''; query.status = ''; dateRange.value = null
  query.startDate = ''; query.endDate = ''; handleSearch()
}
function handleView(row: any) { ElMessage.info(`查看订单 ${row.orderNo}`) }
function goVerify(row: any) { router.push('/b2b/verify') }

onMounted(fetchData)
</script>
