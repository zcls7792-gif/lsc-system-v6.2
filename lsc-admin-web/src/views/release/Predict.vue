<template>
  <div class="page-container">
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header>
        <div class="card-title">
          AI趋势预测
          <el-radio-group v-model="days" size="small" style="margin-left: 16px" @change="fetchData">
            <el-radio-button :value="7">未来7天</el-radio-button>
            <el-radio-button :value="14">未来14天</el-radio-button>
            <el-radio-button :value="30">未来30天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="chartRef" style="width: 100%; height: 400px"></div>
    </el-card>

    <el-row :gutter="16" class="card-row">
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="预测期释放总量" :value="predictData.totalPredict" :precision="2" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="预测日均释放" :value="predictData.avgDaily" :precision="2" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="预测峰值" :value="predictData.peak" :precision="2" /></el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover"><el-statistic title="模型置信度(%)" :value="predictData.confidence" :precision="1" /></el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover">
      <template #header><div class="card-title">AI预测分析说明</div></template>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
        <template #title>预测模型基于历史释放数据、核销率、市场活跃度等多维特征，采用时序预测算法生成。</template>
      </el-alert>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="预测区间">{{ predictData.startDate }} 至 {{ predictData.endDate }}</el-descriptions-item>
        <el-descriptions-item label="模型版本">LSC-Predict-v2.3</el-descriptions-item>
        <el-descriptions-item label="趋势判断">
          <el-tag :type="predictData.trend === 'up' ? 'success' : predictData.trend === 'down' ? 'danger' : 'warning'">
            {{ predictData.trendText }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="主要影响因素">{{ predictData.factors }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { getReleasePredict } from '@/api/release'

const days = ref(7)
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null

const predictData = reactive({
  totalPredict: 0,
  avgDaily: 0,
  peak: 0,
  confidence: 0,
  startDate: '',
  endDate: '',
  trend: 'up',
  trendText: '上升趋势',
  factors: '历史释放节奏、核销率回升、节假日效应'
})

async function fetchData() {
  try {
    const res: any = await getReleasePredict({ days: days.value })
    const data = res.data || {}
    // 填充预测概要数据
    if (data.totalPredict != null) predictData.totalPredict = data.totalPredict
    if (data.avgDaily != null) predictData.avgDaily = data.avgDaily
    if (data.peak != null) predictData.peak = data.peak
    if (data.confidence != null) predictData.confidence = data.confidence
    if (data.startDate != null) predictData.startDate = data.startDate
    if (data.endDate != null) predictData.endDate = data.endDate
    if (data.trend != null) predictData.trend = data.trend
    if (data.trendText != null) predictData.trendText = data.trendText
    if (data.factors != null) predictData.factors = data.factors
    renderChart(data)
  } catch (e) {
    return
  }
}

function renderChart(data: any) {
  if (!chartRef.value) return
  chart = chart || echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['历史实际', 'AI预测', '预测上界', '预测下界'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: data.dates },
    yAxis: { type: 'value', name: '释放量' },
    series: [
      { name: '历史实际', type: 'line', data: data.actual, itemStyle: { color: '#409eff' }, smooth: true },
      { name: 'AI预测', type: 'line', data: data.predicted, itemStyle: { color: '#f56c6c' }, lineStyle: { type: 'dashed' }, smooth: true },
      { name: '预测上界', type: 'line', data: data.upper, itemStyle: { color: '#e6a23c' }, lineStyle: { type: 'dotted', opacity: 0.5 } },
      { name: '预测下界', type: 'line', data: data.lower, itemStyle: { color: '#67c23a' }, lineStyle: { type: 'dotted', opacity: 0.5 }, areaStyle: { color: 'rgba(245,108,108,0.08)' } }
    ]
  }, true)
}

function handleResize() { chart?.resize() }

onMounted(async () => {
  await nextTick()
  await fetchData()
  window.addEventListener('resize', handleResize)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
})
</script>

<style scoped>
.card-title { font-weight: 600; display: flex; align-items: center; }
</style>
