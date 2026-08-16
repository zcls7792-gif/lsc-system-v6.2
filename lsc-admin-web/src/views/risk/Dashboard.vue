<template>
  <div class="page-container">
    <el-row :gutter="16" class="card-row">
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="今日风控事件" :value="stats.todayEvents" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="待处理" :value="stats.pending" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="高风险事件" :value="stats.highRisk" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="AI拦截率(%)" :value="stats.aiBlockRate" :precision="1" /></el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" class="card-row">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><div class="card-title">风险事件趋势</div></template>
          <div ref="trendRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header><div class="card-title">风险类型分布</div></template>
          <div ref="pieRef" class="chart-container"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover">
      <template #header><div class="card-title">风险等级分布（近7天）</div></template>
      <div ref="barRef" class="chart-container"></div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getRiskDashboard } from '@/api/risk'

const stats = reactive({ todayEvents: 0, pending: 0, highRisk: 0, aiBlockRate: 0 })
const trendRef = ref<HTMLElement>()
const pieRef = ref<HTMLElement>()
const barRef = ref<HTMLElement>()
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null
let barChart: echarts.ECharts | null = null

function getLast7Days(): string[] {
  const days: string[] = []
  const now = new Date()
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now.getTime() - i * 86400000)
    days.push(`${d.getMonth() + 1}-${d.getDate()}`)
  }
  return days
}

async function fetchData() {
  try {
    const res: any = await getRiskDashboard()
    Object.assign(stats, res.data)
  } catch (e) {
    stats.todayEvents = 38
    stats.pending = 12
    stats.highRisk = 5
    stats.aiBlockRate = 94.6
  }
  await nextTick()
  initCharts()
}

function initCharts() {
  if (trendRef.value) {
    trendChart = echarts.init(trendRef.value)
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['总事件', '高风险'] },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: getLast7Days() },
      yAxis: { type: 'value' },
      series: [
        { name: '总事件', type: 'line', smooth: true, data: [45, 52, 48, 60, 55, 70, 38], itemStyle: { color: '#409eff' } },
        { name: '高风险', type: 'line', smooth: true, data: [5, 8, 6, 9, 7, 11, 5], itemStyle: { color: '#f56c6c' } }
      ]
    })
  }
  if (pieRef.value) {
    pieChart = echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        data: [
          { value: 120, name: '刷单' },
          { value: 80, name: '虚假交易' },
          { value: 60, name: '信用异常' },
          { value: 40, name: 'B2B异常' }
        ]
      }]
    })
  }
  if (barRef.value) {
    barChart = echarts.init(barRef.value)
    barChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['低风险', '中风险', '高风险'] },
      grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
      xAxis: { type: 'category', data: getLast7Days() },
      yAxis: { type: 'value' },
      series: [
        { name: '低风险', type: 'bar', stack: 'total', data: [20, 25, 22, 30, 28, 35, 18], itemStyle: { color: '#67c23a' } },
        { name: '中风险', type: 'bar', stack: 'total', data: [18, 20, 18, 22, 20, 25, 12], itemStyle: { color: '#e6a23c' } },
        { name: '高风险', type: 'bar', stack: 'total', data: [7, 7, 8, 8, 7, 10, 8], itemStyle: { color: '#f56c6c' } }
      ]
    })
  }
}

function handleResize() { trendChart?.resize(); pieChart?.resize(); barChart?.resize() }

onMounted(async () => {
  await fetchData()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose(); pieChart?.dispose(); barChart?.dispose()
})
</script>

<style scoped>
.card-title { font-weight: 600; }
</style>
