<template>
  <div class="dashboard" data-testid="platform-dashboard-page">
    <el-row :gutter="16" class="card-row">
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="总用户数" :value="stats.totalUsers">
            <template #prefix>
              <el-icon style="color: #409eff"><User /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" data-testid="platform-dashboard-merchant-card">
          <el-statistic title="总商家数" :value="stats.totalMerchants">
            <template #prefix>
              <el-icon style="color: #67c23a"><Shop /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card" data-testid="platform-dashboard-amount-card">
          <el-statistic title="今日交易额(元)" :value="stats.todayAmount" :precision="2">
            <template #prefix>
              <el-icon style="color: #e6a23c"><Money /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover" class="stat-card">
          <el-statistic title="全网LSC总量" :value="stats.totalLsc" :precision="2">
            <template #prefix>
              <el-icon style="color: #f56c6c"><TrendCharts /></el-icon>
            </template>
          </el-statistic>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-title">释放趋势</div>
          </template>
          <div ref="releaseChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-title">核销率走势</div>
          </template>
          <div ref="writeoffChartRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'

const stats = reactive({
  totalUsers: 0,
  totalMerchants: 0,
  todayAmount: 0,
  totalLsc: 0
})

const releaseChartRef = ref<HTMLElement>()
const writeoffChartRef = ref<HTMLElement>()
let releaseChart: echarts.ECharts | null = null
let writeoffChart: echarts.ECharts | null = null

function initReleaseChart() {
  if (!releaseChartRef.value) return
  releaseChart = echarts.init(releaseChartRef.value)
  releaseChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['日释放量', '累计释放量'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: getLast7Days() },
    yAxis: [
      { type: 'value', name: '日释放' },
      { type: 'value', name: '累计' }
    ],
    series: [
      {
        name: '日释放量',
        type: 'bar',
        data: [1200, 1500, 1800, 1400, 1900, 2100, 2300],
        itemStyle: { color: '#409eff' }
      },
      {
        name: '累计释放量',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: [1200, 2700, 4500, 5900, 7800, 9900, 12200],
        itemStyle: { color: '#67c23a' }
      }
    ]
  })
}

function initWriteoffChart() {
  if (!writeoffChartRef.value) return
  writeoffChart = echarts.init(writeoffChartRef.value)
  writeoffChart.setOption({
    tooltip: { trigger: 'axis', formatter: '{b}<br/>{a}: {c}%' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: getLast7Days() },
    yAxis: { type: 'value', name: '核销率(%)', max: 100 },
    series: [
      {
        name: '核销率',
        type: 'line',
        smooth: true,
        data: [62, 65, 70, 68, 75, 78, 82],
        areaStyle: { color: 'rgba(64,158,255,0.2)' },
        itemStyle: { color: '#409eff' }
      }
    ]
  })
}

function getLast7Days(): string[] {
  const days: string[] = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 24 * 3600 * 1000)
    days.push(`${d.getMonth() + 1}-${d.getDate()}`)
  }
  return days
}

function handleResize() {
  releaseChart?.resize()
  writeoffChart?.resize()
}

onMounted(async () => {
  stats.totalUsers = 12580
  stats.totalMerchants = 368
  stats.todayAmount = 285600.5
  stats.totalLsc = 9865230.42
  await nextTick()
  initReleaseChart()
  initWriteoffChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  releaseChart?.dispose()
  writeoffChart?.dispose()
})
</script>

<style scoped>
.card-title {
  font-weight: 600;
}
</style>
