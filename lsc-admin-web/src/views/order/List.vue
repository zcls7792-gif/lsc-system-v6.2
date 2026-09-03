<template>
  <div class="page-container" data-testid="platform-order-list-page">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="订单号">
        <el-input v-model="query.orderNo" placeholder="订单号" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="订单类型">
        <el-select v-model="query.type" placeholder="全部" clearable style="width: 140px">
          <el-option label="普通订单" value="normal" />
          <el-option label="B2B订单" value="b2b" />
          <el-option label="核销订单" value="writeoff" />
        </el-select>
      </el-form-item>
      <el-form-item label="订单状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="待支付" value="pending" />
          <el-option label="已支付" value="paid" />
          <el-option label="已完成" value="completed" />
          <el-option label="已取消" value="cancelled" />
          <el-option label="已退款" value="refunded" />
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
        <el-button type="primary" :icon="Search" @click="handleSearch" data-testid="platform-order-search-btn">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <el-button :icon="Download">导出</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ typeText(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="buyerName" label="买方" width="130" show-overflow-tooltip />
      <el-table-column prop="sellerName" label="卖方" width="130" show-overflow-tooltip />
      <el-table-column prop="amount" label="金额(元)" width="120" align="right">
        <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="lscAmount" label="释放LSC" width="110" align="right" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="下单时间" width="170" />
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
import { Search, Refresh, Download } from '@element-plus/icons-vue'
import { getOrderList } from '@/api/order'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1,
  size: 10,
  orderNo: '',
  type: '',
  status: '',
  startDate: '',
  endDate: ''
})

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getOrderList(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function typeText(t: string) { return ({ normal: '普通订单', b2b: 'B2B订单', writeoff: '核销订单' } as any)[t] }
function statusText(s: string) {
  return ({ pending: '待支付', paid: '已支付', completed: '已完成', cancelled: '已取消', refunded: '已退款' } as any)[s]
}
function statusTagType(s: string) {
  return ({ pending: 'warning', paid: 'primary', completed: 'success', cancelled: 'info', refunded: 'danger' } as any)[s]
}

function handleDateChange(val: [string, string] | null) {
  if (val) {
    query.startDate = val[0]
    query.endDate = val[1]
  } else {
    query.startDate = ''
    query.endDate = ''
  }
}

function handleSearch() { query.page = 1; fetchData() }
function handleReset() {
  query.orderNo = ''; query.type = ''; query.status = ''
  dateRange.value = null; query.startDate = ''; query.endDate = ''
  handleSearch()
}
function handleView(row: any) { ElMessage.info(`查看订单 ${row.orderNo}`) }

onMounted(fetchData)
</script>
