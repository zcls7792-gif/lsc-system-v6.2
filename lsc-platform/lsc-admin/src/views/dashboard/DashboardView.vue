<template>
  <div class="dashboard">
    <el-row :gutter="16" class="stat-cards">
      <el-col :span="6" v-for="card in statCards" :key="card.title">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-card-inner">
            <el-icon :size="40" :color="card.color">
              <component :is="card.icon" />
            </el-icon>
            <div class="stat-info">
              <div class="stat-value">{{ card.value }}</div>
              <div class="stat-label">{{ card.title }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="16">
        <el-card>
          <template #header>权益释放趋势</template>
          <div ref="chartRef" style="height: 320px"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header>待处理事项</template>
          <el-timeline>
            <el-timeline-item
              v-for="item in todos"
              :key="item.id"
              :timestamp="item.time"
              :type="item.type"
            >
              {{ item.content }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'

const chartRef = ref<HTMLElement>()
let chartInstance: echarts.ECharts | null = null

const statCards = [
  { title: '今日订单', value: '1,286', icon: 'List', color: '#409EFF' },
  { title: '今日释放 LSC', value: '52,310', icon: 'Wallet', color: '#67C23A' },
  { title: '待审商品', value: '23', icon: 'Goods', color: '#E6A23C' },
  { title: '风控案件', value: '7', icon: 'Warning', color: '#F56C6C' }
]

const todos = [
  { id: 1, content: '3 个风控案件待复核', time: '10分钟前', type: 'danger' },
  { id: 2, content: '2 个配置变更待审批', time: '1小时前', type: 'warning' },
  { id: 3, content: '5 个商品待审核', time: '2小时前', type: 'primary' },
  { id: 4, content: '供应商结算单待处理', time: '昨天', type: 'success' }
]

function initChart() {
  if (!chartRef.value) return
  chartInstance = echarts.init(chartRef.value)
  chartInstance.setOption({
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '释放量',
        type: 'line',
        smooth: true,
        data: [12000, 19000, 15000, 22000, 28000, 31000, 26000],
        areaStyle: { opacity: 0.1 },
        itemStyle: { color: '#409EFF' }
      }
    ]
  })
}

function handleResize() {
  chartInstance?.resize()
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})
</script>

<style scoped lang="scss">
.stat-cards {
  .stat-card {
    .stat-card-inner {
      display: flex;
      align-items: center;
      gap: 16px;
    }
    .stat-info {
      .stat-value {
        font-size: 28px;
        font-weight: bold;
        color: #303133;
      }
      .stat-label {
        color: #909399;
        font-size: 13px;
        margin-top: 4px;
      }
    }
  }
}
</style>
