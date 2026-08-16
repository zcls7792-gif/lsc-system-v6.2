<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="日期范围">
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
        <el-button :icon="Download">导出</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="16" class="card-row">
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="周期内释放总量" :value="summary.totalRelease" :precision="2" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="日均释放" :value="summary.avgDaily" :precision="2" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="累计已释放" :value="summary.cumulative" :precision="2" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="核销率(%)" :value="summary.writeoffRate" :precision="1" /></el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover">
      <template #header><div class="card-title">日释放趋势</div></template>
      <div ref="chartRef" class="chart-container"></div>
    </el-card>

    <el-table v-loading="loading" :data="tableData" border stripe style="margin-top: 16px">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="date" label="日期" width="130" />
      <el-table-column prop="releaseAmount" label="释放量" width="130" align="right" />
      <el-table-column prop="writeoffAmount" label="核销量" width="130" align="right" />
      <el-table-column prop="writeoffRate" label="核销率(%)" width="120" align="center" />
      <el-table-column prop="poolBalance" label="资金池余额" width="140" align="right" />
      <el-table-column prop="taskCount" label="释放任务数" width="120" align="center" />
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'completed' ? 'success' : 'warning'">{{ row.status === 'completed' ? '已完成' : '进行中' }}</el-tag>
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
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Search, Refresh, Download } from '@element-plus/icons-vue'
import { getReleaseSummary } from '@/api/release'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const query = reactive({ page: 1, size: 10, startDate: '', endDate: '' })
const summary = reactive({ totalRelease: 0, avgDaily: 0, cumulative: 0, writeoffRate: 0 })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getReleaseSummary(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
  calcSummary()
  await nextTick()
  initChart()
}

function calcSummary() {
  summary.totalRelease = tableData.value.reduce((s, i) => s + i.releaseAmount, 0)
  summary.avgDaily = tableData.value.length ? summary.totalRelease / tableData.value.length : 0
  summary.cumulative = summary.totalRelease + 8000000
  const rates = tableData.value.map((i) => i.writeoffRate)
  summary.writeoffRate = rates.length ? rates.reduce((s, i) => s + i, 0) / rates.length : 0
}

function initChart() {
  if (!chartRef.value) return
  chart = echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['释放量', '核销量'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: tableData.value.map((i) => i.date).reverse() },
    yAxis: { type: 'value' },
    series: [
      { name: '释放量', type: 'bar', data: tableData.value.map((i) => i.releaseAmount).reverse(), itemStyle: { color: '#409eff' } },
      { name: '核销量', type: 'line', smooth: true, data: tableData.value.map((i) => i.writeoffAmount).reverse(), itemStyle: { color: '#67c23a' } }
    ]
  })
}

function handleDateChange(val: [string, string] | null) {
  if (val) { query.startDate = val[0]; query.endDate = val[1] }
  else { query.startDate = ''; query.endDate = '' }
}
function handleSearch() { query.page = 1; fetchData() }
function handleReset() { dateRange.value = null; query.startDate = ''; query.endDate = ''; handleSearch() }

onMounted(fetchData)
onBeforeUnmount(() => { chart?.dispose() })
</script>

<style scoped>
.card-title { font-weight: 600; }
</style>
