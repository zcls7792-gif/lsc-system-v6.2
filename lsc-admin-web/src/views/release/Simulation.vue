<template>
  <div class="page-container">
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="hover">
          <template #header><div class="card-title">仿真参数配置</div></template>
          <el-form :model="simForm" label-width="120px">
            <el-form-item label="仿真天数">
              <el-input-number v-model="simForm.days" :min="7" :max="90" />
            </el-form-item>
            <el-form-item label="初始释放率(%)">
              <el-input-number v-model="simForm.releaseRate" :min="0.5" :max="5" :step="0.1" :precision="2" />
            </el-form-item>
            <el-form-item label="预期核销率(%)">
              <el-input-number v-model="simForm.writeoffRate" :min="0" :max="100" :step="1" />
            </el-form-item>
            <el-form-item label="日均交易额">
              <el-input-number v-model="simForm.dailyAmount" :min="1000" :step="1000" />
            </el-form-item>
            <el-form-item label="AI调节系数">
              <el-input-number v-model="simForm.aiFactor" :min="0.5" :max="2" :step="0.1" :precision="2" />
            </el-form-item>
            <el-form-item label="风控阈值">
              <el-input-number v-model="simForm.riskThreshold" :min="0" :max="100" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Cpu" :loading="running" @click="runSim">开始推演</el-button>
              <el-button :icon="Refresh" @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="hover">
          <template #header><div class="card-title">推演结果</div></template>
          <div ref="chartRef" style="width: 100%; height: 320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="hover" style="margin-top: 16px">
      <template #header><div class="card-title">推演报告</div></template>
      <el-descriptions :column="2" border v-if="report">
        <el-descriptions-item label="推演周期">{{ report.days }} 天</el-descriptions-item>
        <el-descriptions-item label="预计释放总量">{{ report.totalRelease }} LSC</el-descriptions-item>
        <el-descriptions-item label="预计核销量">{{ report.totalWriteoff }} LSC</el-descriptions-item>
        <el-descriptions-item label="期末资金池余额">{{ report.poolBalance }} LSC</el-descriptions-item>
        <el-descriptions-item label="预计峰值释放">{{ report.peak }} LSC</el-descriptions-item>
        <el-descriptions-item label="风险事件预计">{{ report.riskEvents }} 次</el-descriptions-item>
        <el-descriptions-item label="AI评估结论" :span="2">
          <el-tag :type="report.riskLevel === 'low' ? 'success' : report.riskLevel === 'mid' ? 'warning' : 'danger'">
            {{ report.riskLevelText }}
          </el-tag>
          {{ report.conclusion }}
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="请配置参数后开始推演" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onBeforeUnmount, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Cpu, Refresh } from '@element-plus/icons-vue'
import { runSimulation } from '@/api/release'

const running = ref(false)
const chartRef = ref<HTMLElement>()
let chart: echarts.ECharts | null = null
const report = ref<any>(null)

const defaultForm = { days: 30, releaseRate: 2.5, writeoffRate: 70, dailyAmount: 50000, aiFactor: 1.2, riskThreshold: 80 }
const simForm = reactive({ ...defaultForm })

async function runSim() {
  running.value = true
  try {
    const res: any = await runSimulation(simForm)
    handleResult(res.data)
  } catch (e) {
    return
  } finally {
    running.value = false
  }
}

function handleResult(data: any) {
  report.value = data
  nextTick(() => initChart(data))
}

function initChart(data: any) {
  if (!chartRef.value) return
  chart = chart || echarts.init(chartRef.value)
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['释放量', '核销量', '资金池余额'] },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', data: data.dates },
    yAxis: [
      { type: 'value', name: '释放/核销' },
      { type: 'value', name: '资金池' }
    ],
    series: [
      { name: '释放量', type: 'bar', data: data.release, itemStyle: { color: '#409eff' } },
      { name: '核销量', type: 'line', data: data.writeoff, itemStyle: { color: '#67c23a' }, smooth: true },
      { name: '资金池余额', type: 'line', yAxisIndex: 1, data: data.pool, itemStyle: { color: '#e6a23c' }, smooth: true }
    ]
  }, true)
}

function resetForm() {
  Object.assign(simForm, defaultForm)
  report.value = null
}

onBeforeUnmount(() => { chart?.dispose() })
</script>

<style scoped>
.card-title { font-weight: 600; }
</style>
