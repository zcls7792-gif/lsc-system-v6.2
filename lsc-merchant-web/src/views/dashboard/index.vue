<script setup lang="ts">
// 工作台 — 统计卡片(今日订单/今日收入/LSC余额/信用分) + 近7天交易趋势图
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { Goods, List, Money, Medal, ArrowRight, TrendCharts } from '@element-plus/icons-vue'
import { getOrderStats, type OrderStats } from '@/api/order'
import { getLscOverview, getRecentTrend, type LscOverview, type TrendPoint } from '@/api/lsc'
import { getMerchantProfile } from '@/api/auth'
import type { MerchantExtension } from '@/api/types'

echarts.use([LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const router = useRouter()

const stats = ref<OrderStats | null>(null)
const overview = ref<LscOverview | null>(null)
const merchant = ref<MerchantExtension | null>(null)
const trend = ref<TrendPoint[]>([])
const loading = ref(false)

const trendChartRef = ref<HTMLDivElement | null>(null)
let trendChart: echarts.ECharts | null = null

const creditScore = computed(() => merchant.value?.creditScore ?? 100)

async function load() {
  loading.value = true
  try {
    const [s, o, m, t] = await Promise.all([
      getOrderStats().catch(() => null),
      getLscOverview().catch(() => null),
      getMerchantProfile().catch(() => null),
      getRecentTrend(7).catch(() => [])
    ])
    stats.value = s
    overview.value = o
    merchant.value = m
    trend.value = t || []
    renderTrend()
  } finally {
    loading.value = false
  }
}

function fmt(n: number | undefined) {
  return Number(n || 0).toLocaleString('en-US')
}

function fmtMoney(n: number | undefined) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function renderTrend() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const dates = trend.value.map((p) => p.date.slice(5))
  const revenues = trend.value.map((p) => Number(p.revenue || 0))
  const orders = trend.value.map((p) => Number(p.orderCount || 0))
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: 'rgba(15,23,42,0.92)',
      borderWidth: 0,
      textStyle: { color: '#fff' }
    },
    legend: {
      data: ['收入(元)', '订单数'],
      top: 0,
      right: 0,
      icon: 'roundRect',
      itemWidth: 12,
      itemHeight: 6,
      textStyle: { color: '#64748b', fontSize: 12 }
    },
    grid: { top: 36, left: 8, right: 16, bottom: 0, containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      axisLine: { lineStyle: { color: '#e2e8f0' } },
      axisTick: { show: false },
      axisLabel: { color: '#94a3b8', fontSize: 12 }
    },
    yAxis: [
      {
        type: 'value',
        name: '收入',
        nameTextStyle: { color: '#94a3b8', fontSize: 11 },
        axisLabel: { color: '#94a3b8', fontSize: 11 },
        splitLine: { lineStyle: { color: '#eef2f5', type: 'dashed' } }
      },
      {
        type: 'value',
        name: '订单',
        nameTextStyle: { color: '#94a3b8', fontSize: 11 },
        axisLabel: { color: '#94a3b8', fontSize: 11 },
        splitLine: { show: false }
      }
    ],
    series: [
      {
        name: '收入(元)',
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 8,
        data: revenues,
        lineStyle: { width: 3, color: '#0d9488' },
        itemStyle: { color: '#0d9488', borderColor: '#fff', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(13,148,136,0.28)' },
            { offset: 1, color: 'rgba(13,148,136,0.02)' }
          ])
        }
      },
      {
        name: '订单数',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        data: orders,
        lineStyle: { width: 2, color: '#d97706', type: 'dashed' },
        itemStyle: { color: '#d97706', borderColor: '#fff', borderWidth: 2 }
      }
    ]
  })
}

const quickActions = [
  { label: '发布商品', icon: Goods, path: '/product/publish' },
  { label: '订单管理', icon: List, path: '/order/list' },
  { label: '发起B2B', icon: Money, path: '/b2b/create' },
  { label: '申请核销', icon: Medal, path: '/writeoff/apply' }
]

function go(path: string) {
  router.push(path)
}

onMounted(async () => {
  await load()
  window.addEventListener('resize', resize)
})

function resize() {
  trendChart?.resize()
}
</script>

<template>
  <div class="lsc-page" v-loading="loading">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">工作台</h1>
        <p class="lsc-page-subtitle">概览今日经营 · LSC 资产 · 近7天趋势</p>
      </div>
      <div class="dash-date lsc-num">{{ new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', weekday: 'long' }) }}</div>
    </div>

    <!-- 统计卡片 -->
    <div class="dash-stats">
      <div class="stat-card stat-card--teal">
        <div class="stat-card__head">
          <span class="stat-card__label">今日订单</span>
          <el-icon class="stat-card__icon"><List /></el-icon>
        </div>
        <div class="stat-card__value lsc-num">{{ fmt(stats?.todayOrderCount) }}</div>
        <div class="stat-card__foot">
          <span>待发货 <em class="lsc-num">{{ fmt(stats?.pendingShipCount) }}</em></span>
          <span>待退款 <em class="lsc-num">{{ fmt(stats?.pendingRefundCount) }}</em></span>
        </div>
      </div>

      <div class="stat-card stat-card--gold">
        <div class="stat-card__head">
          <span class="stat-card__label">今日收入</span>
          <el-icon class="stat-card__icon"><Money /></el-icon>
        </div>
        <div class="stat-card__value lsc-num">¥{{ fmtMoney(stats?.todayRevenue) }}</div>
        <div class="stat-card__foot">
          <span>月营业额 <em class="lsc-num">¥{{ fmtMoney(merchant?.monthlyRevenue) }}</em></span>
        </div>
      </div>

      <div class="stat-card stat-card--deep">
        <div class="stat-card__head">
          <span class="stat-card__label">LSC 余额</span>
          <el-icon class="stat-card__icon"><TrendCharts /></el-icon>
        </div>
        <div class="stat-card__value lsc-num lsc-gold-text">{{ fmt(overview?.totalAvailable) }}</div>
        <div class="stat-card__foot">
          <span>锁定 <em class="lsc-num">{{ fmt(overview?.totalLocked) }}</em></span>
          <span>已核销 <em class="lsc-num">{{ fmt(overview?.totalWrittenOff) }}</em></span>
        </div>
      </div>

      <div class="stat-card stat-card--credit">
        <div class="stat-card__head">
          <span class="stat-card__label">信用分</span>
          <el-icon class="stat-card__icon"><Medal /></el-icon>
        </div>
        <div class="stat-card__value lsc-num">{{ creditScore }}</div>
        <div class="stat-card__foot">
          <span>核销档位 <em class="lsc-num">{{ merchant?.nhLimitLevel || 0 }}</em></span>
          <span>日核销限额 <em class="lsc-num">{{ merchant?.dailyNhLimit || 0 }}</em></span>
        </div>
      </div>
    </div>

    <!-- 趋势 + 快捷操作 -->
    <div class="dash-grid">
      <div class="lsc-card dash-trend">
        <div class="lsc-card__pad">
          <div class="dash-trend__head">
            <div>
              <h3 class="dash-trend__title">近 7 天交易趋势</h3>
              <p class="dash-trend__sub">收入(元) 与 订单数 走势</p>
            </div>
            <el-tag type="success" effect="light" round>7 天</el-tag>
          </div>
          <div ref="trendChartRef" class="dash-trend__chart"></div>
        </div>
      </div>

      <div class="lsc-card dash-actions">
        <div class="lsc-card__pad">
          <h3 class="dash-actions__title">快捷操作</h3>
          <div class="dash-actions__grid">
            <div
              v-for="a in quickActions"
              :key="a.path"
              class="dash-actions__item"
              @click="go(a.path)"
            >
              <div class="dash-actions__icon">
                <el-icon><component :is="a.icon" /></el-icon>
              </div>
              <span class="dash-actions__label">{{ a.label }}</span>
              <el-icon class="dash-actions__arrow"><ArrowRight /></el-icon>
            </div>
          </div>

          <div class="dash-notice" v-if="merchant && merchant.auditStatus !== 1">
            <el-alert
              :title="merchant.auditStatus === 0 ? '商家资料待平台审核' : '商家资料审核未通过，请补充资料'"
              :type="merchant.auditStatus === 0 ? 'warning' : 'error'"
              show-icon
              :closable="false"
            >
              <template #default>
                <el-button type="primary" link @click="go('/store/info')">前往处理</el-button>
              </template>
            </el-alert>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dash-date {
  font-size: 13px;
  color: var(--lsc-text-secondary);
}

.dash-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 18px;
}

.stat-card {
  position: relative;
  border-radius: var(--lsc-radius-md);
  padding: 20px 22px;
  background: var(--lsc-surface);
  border: 1px solid var(--lsc-border-soft);
  box-shadow: var(--lsc-shadow-sm);
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto auto;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  transform: translate(40%, -40%);
  opacity: 0.1;
}

.stat-card--teal::before {
  background: var(--lsc-primary-500);
}

.stat-card--gold::before {
  background: var(--lsc-gold-500);
}

.stat-card--deep::before {
  background: var(--lsc-primary-800);
  opacity: 0.18;
}

.stat-card--credit::before {
  background: var(--lsc-gold-600);
  opacity: 0.16;
}

.stat-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.stat-card__label {
  font-size: 13px;
  color: var(--lsc-text-secondary);
  font-weight: 600;
}

.stat-card__icon {
  font-size: 20px;
  color: var(--lsc-text-disabled);
}

.stat-card--teal .stat-card__icon {
  color: var(--lsc-primary-600);
}

.stat-card--gold .stat-card__icon {
  color: var(--lsc-gold-600);
}

.stat-card--deep .stat-card__icon {
  color: var(--lsc-primary-800);
}

.stat-card--credit .stat-card__icon {
  color: var(--lsc-gold-600);
}

.stat-card__value {
  font-size: 30px;
  font-weight: 700;
  color: var(--lsc-text);
  letter-spacing: -0.02em;
  line-height: 1.1;
}

.stat-card__foot {
  display: flex;
  gap: 18px;
  margin-top: 14px;
  font-size: 12px;
  color: var(--lsc-text-secondary);
}

.stat-card__foot em {
  font-style: normal;
  font-weight: 700;
  color: var(--lsc-text-regular);
  margin-left: 2px;
}

.dash-grid {
  display: grid;
  grid-template-columns: 1.7fr 1fr;
  gap: 16px;
}

.dash-trend__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}

.dash-trend__title {
  font-size: 16px;
}

.dash-trend__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--lsc-text-secondary);
}

.dash-trend__chart {
  height: 300px;
}

.dash-actions__title {
  font-size: 16px;
  margin-bottom: 16px;
}

.dash-actions__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.dash-actions__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 12px;
  border-radius: var(--lsc-radius);
  border: 1px solid var(--lsc-border-soft);
  background: var(--lsc-surface-2);
  cursor: pointer;
  transition: all 0.18s ease;
}

.dash-actions__item:hover {
  border-color: var(--lsc-primary-300);
  background: var(--lsc-primary-50);
  transform: translateY(-1px);
  box-shadow: var(--lsc-shadow-sm);
}

.dash-actions__icon {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  background: linear-gradient(135deg, var(--lsc-primary-100), var(--lsc-primary-50));
  color: var(--lsc-primary-700);
  display: grid;
  place-items: center;
  font-size: 18px;
  flex-shrink: 0;
}

.dash-actions__label {
  flex: 1;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--lsc-text);
}

.dash-actions__arrow {
  color: var(--lsc-text-placeholder);
  font-size: 13px;
}

.dash-actions__item:hover .dash-actions__arrow {
  color: var(--lsc-primary-600);
}

.dash-notice {
  margin-top: 18px;
}

@media (max-width: 1180px) {
  .dash-stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .dash-grid {
    grid-template-columns: 1fr;
  }
}
</style>
