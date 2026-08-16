<script setup lang="ts">
// LSC 余额卡片 — 锁定/可用环形图 + 明细摘要
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([PieChart, TooltipComponent, LegendComponent, CanvasRenderer])

interface Props {
  totalLocked: number
  totalAvailable: number
  /** 标题 */
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  title: 'LSC 余额分布'
})

const chartRef = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

const total = computed(() => props.totalLocked + props.totalAvailable)
const availablePct = computed(() => {
  if (total.value === 0) return 0
  return Math.round((props.totalAvailable / total.value) * 100)
})
const lockedPct = computed(() => 100 - availablePct.value)

function fmt(n: number) {
  return Number(n || 0).toLocaleString('en-US')
}

function renderChart() {
  if (!chartRef.value) return
  if (!chart) {
    chart = echarts.init(chartRef.value)
  }
  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: (p: any) => `${p.name}<br/>${fmt(p.value)} LSC (${p.percent}%)`
    },
    legend: { show: false },
    series: [
      {
        name: 'LSC',
        type: 'pie',
        radius: ['62%', '88%'],
        avoidLabelOverlap: false,
        label: { show: false },
        labelLine: { show: false },
        itemStyle: {
          borderRadius: 8,
          borderColor: '#fff',
          borderWidth: 2
        },
        data: [
          { value: props.totalAvailable, name: '可用 LSC', itemStyle: { color: '#0d9488' } },
          { value: props.totalLocked, name: '锁定 LSC', itemStyle: { color: '#fbbf24' } }
        ]
      }
    ]
  })
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  renderChart()
  window.addEventListener('resize', handleResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})

watch(
  () => [props.totalLocked, props.totalAvailable],
  () => renderChart()
)
</script>

<template>
  <div class="lsc-card">
    <div class="lsc-card__head">
      <span class="lsc-card__title">{{ title }}</span>
      <span class="lsc-card__total">
        合计 <strong class="lsc-num lsc-gold-text">{{ fmt(total) }}</strong> LSC
      </span>
    </div>

    <div class="lsc-card__body">
      <div ref="chartRef" class="lsc-card__chart"></div>
      <div class="lsc-card__center">
        <span class="lsc-card__center-num lsc-num">{{ availablePct }}%</span>
        <span class="lsc-card__center-label">可用占比</span>
      </div>

      <div class="lsc-card__legend">
        <div class="lsc-card__legend-item">
          <span class="lsc-card__dot lsc-card__dot--available"></span>
          <div>
            <div class="lsc-card__legend-label">可用 LSC</div>
            <div class="lsc-card__legend-value lsc-num">{{ fmt(totalAvailable) }}</div>
          </div>
          <span class="lsc-card__legend-pct lsc-num">{{ availablePct }}%</span>
        </div>
        <div class="lsc-card__legend-item">
          <span class="lsc-card__dot lsc-card__dot--locked"></span>
          <div>
            <div class="lsc-card__legend-label">锁定 LSC</div>
            <div class="lsc-card__legend-value lsc-num">{{ fmt(totalLocked) }}</div>
          </div>
          <span class="lsc-card__legend-pct lsc-num">{{ lockedPct }}%</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lsc-card {
  background: var(--lsc-surface);
  border: 1px solid var(--lsc-border-soft);
  border-radius: var(--lsc-radius-md);
  box-shadow: var(--lsc-shadow-sm);
  overflow: hidden;
}

.lsc-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--lsc-border-soft);
}

.lsc-card__title {
  font-family: var(--lsc-font-display);
  font-weight: 600;
  font-size: 15px;
  color: var(--lsc-text);
}

.lsc-card__total {
  font-size: 12.5px;
  color: var(--lsc-text-secondary);
}

.lsc-card__body {
  position: relative;
  padding: 18px 20px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.lsc-card__chart {
  width: 150px;
  height: 150px;
  flex-shrink: 0;
}

.lsc-card__center {
  position: absolute;
  top: 50%;
  left: 20px;
  transform: translate(calc(75px - 50%), -50%);
  text-align: center;
  pointer-events: none;
  width: 90px;
}

.lsc-card__center-num {
  font-family: var(--lsc-font-mono);
  font-weight: 700;
  font-size: 22px;
  color: var(--lsc-primary-700);
  display: block;
}

.lsc-card__center-label {
  font-size: 11px;
  color: var(--lsc-text-secondary);
  margin-top: 2px;
  display: block;
}

.lsc-card__legend {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.lsc-card__legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.lsc-card__dot {
  width: 10px;
  height: 10px;
  border-radius: 3px;
  flex-shrink: 0;
}

.lsc-card__dot--available {
  background: var(--lsc-primary-600);
}

.lsc-card__dot--locked {
  background: var(--lsc-gold-400);
}

.lsc-card__legend-label {
  font-size: 12px;
  color: var(--lsc-text-secondary);
}

.lsc-card__legend-value {
  font-size: 17px;
  font-weight: 700;
  color: var(--lsc-text);
  margin-top: 2px;
}

.lsc-card__legend-pct {
  margin-left: auto;
  font-size: 13px;
  font-weight: 600;
  color: var(--lsc-text-regular);
}
</style>
