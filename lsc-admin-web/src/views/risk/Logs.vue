<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="风险等级">
        <el-select v-model="query.level" placeholder="全部" clearable style="width: 130px">
          <el-option label="低风险" value="low" />
          <el-option label="中风险" value="mid" />
          <el-option label="高风险" value="high" />
        </el-select>
      </el-form-item>
      <el-form-item label="风险类型">
        <el-select v-model="query.type" placeholder="全部" clearable style="width: 150px">
          <el-option label="刷单" value="fake_order" />
          <el-option label="虚假交易" value="fake_trade" />
          <el-option label="信用异常" value="credit_abnormal" />
          <el-option label="B2B异常" value="b2b_abnormal" />
        </el-select>
      </el-form-item>
      <el-form-item label="处理状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 130px">
          <el-option label="待处理" value="pending" />
          <el-option label="已处理" value="handled" />
          <el-option label="已忽略" value="ignored" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间">
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
      <el-table-column prop="id" label="事件ID" width="100" />
      <el-table-column prop="type" label="风险类型" width="120" align="center">
        <template #default="{ row }">{{ typeText(row.type) }}</template>
      </el-table-column>
      <el-table-column prop="target" label="关联对象" min-width="160" show-overflow-tooltip />
      <el-table-column prop="level" label="等级" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="levelTagType(row.level)" effect="dark">{{ levelText(row.level) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="aiScore" label="AI风险分" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.aiScore >= 80 ? 'danger' : 'warning'"><el-icon><MagicStick /></el-icon>{{ row.aiScore }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="风险描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="发生时间" width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">详情</el-button>
          <el-button v-if="row.status === 'pending'" link type="warning" @click="handleEvent(row)">处理</el-button>
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
import { Search, Refresh, MagicStick } from '@element-plus/icons-vue'
import { getRiskLogs, handleRiskEvent } from '@/api/risk'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1, size: 10, level: '', type: '', status: '', startDate: '', endDate: ''
})

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getRiskLogs(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function typeText(t: string) {
  return ({ fake_order: '刷单', fake_trade: '虚假交易', credit_abnormal: '信用异常', b2b_abnormal: 'B2B异常' } as any)[t]
}
function levelText(l: string) { return ({ low: '低风险', mid: '中风险', high: '高风险' } as any)[l] }
function levelTagType(l: string) { return ({ low: 'success', mid: 'warning', high: 'danger' } as any)[l] }
function statusText(s: string) { return ({ pending: '待处理', handled: '已处理', ignored: '已忽略' } as any)[s] }
function statusTagType(s: string) { return ({ pending: 'warning', handled: 'success', ignored: 'info' } as any)[s] }

function handleDateChange(val: [string, string] | null) {
  if (val) { query.startDate = val[0]; query.endDate = val[1] }
  else { query.startDate = ''; query.endDate = '' }
}
function handleSearch() { query.page = 1; fetchData() }
function handleReset() {
  query.level = ''; query.type = ''; query.status = ''
  dateRange.value = null; query.startDate = ''; query.endDate = ''
  handleSearch()
}
function handleView(row: any) { ElMessage.info(`查看事件 ${row.id}`) }

async function handleEvent(row: any) {
  const { value } = await ElMessageBox.prompt('请输入处理意见', `处理风控事件 ${row.id}`, {
    inputType: 'textarea',
    inputPlaceholder: '处理意见',
    distinguishCancelAndClose: true
  }).catch(() => ({ value: '' }))
  if (value === '') return
  try { await handleRiskEvent(row.id, { action: 'handle', remark: value }) } catch (e) { ElMessage.error('操作失败') }
  ElMessage.success('已处理')
  fetchData()
}

onMounted(fetchData)
</script>
