<template>
  <view class="page">
    <view class="tabs">
      <view v-for="tab in tabs" :key="tab.value"
            :class="['tab', activeTab === tab.value && 'active']"
            @click="switchTab(tab.value)">
        {{ tab.label }}
      </view>
    </view>
    <view class="order-list">
      <view v-for="o in orders" :key="o.orderId" class="order-card" @click="goDetail(o.orderId)">
        <view class="order-header">
          <text class="order-no">订单 #{{ o.orderId }}</text>
          <text :class="'status-' + o.status">{{ statusText(o.status) }}</text>
        </view>
        <view class="order-amount">
          实付 <text class="amount">¥{{ formatCent(o.rmbCent) }}</text>
        </view>
        <view class="order-time">{{ o.createdAt }}</view>
      </view>
      <view v-if="orders.length === 0" class="empty">暂无订单</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { listOrders, type Order } from '@/api/order'

const tabs = [
  { label: '全部', value: '' },
  { label: '待支付', value: 'PENDING' },
  { label: '已支付', value: 'PAID' },
  { label: '已完成', value: 'COMPLETED' }
]
const activeTab = ref('')
const orders = ref<Order[]>([])

onMounted(() => load())
onShow(() => load())

async function load() {
  try {
    orders.value = await listOrders(activeTab.value || undefined)
  } catch {}
}

function switchTab(v: string) {
  activeTab.value = v
  load()
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
}

function formatCent(c: number) {
  return (c / 100).toFixed(2)
}
function statusText(s: string) {
  return ({ PENDING: '待支付', PAID: '已支付', COMPLETED: '已完成', CANCELED: '已取消', REFUNDED: '已退款' } as any)[s] || s
}
</script>

<style scoped>
.page { padding: 0 24rpx; }
.tabs { display: flex; background: #fff; border-radius: 12rpx; padding: 8rpx; margin: 20rpx 0; }
.tab { flex: 1; text-align: center; padding: 16rpx 0; font-size: 26rpx; color: #606266; border-radius: 8rpx; }
.tab.active { background: #1f6feb; color: #fff; }
.order-list { display: flex; flex-direction: column; gap: 20rpx; }
.order-card { background: #fff; border-radius: 12rpx; padding: 24rpx; }
.order-header { display: flex; justify-content: space-between; }
.order-no { font-weight: bold; }
.status-PENDING { color: #e6a23c; }
.status-PAID { color: #1f6feb; }
.status-COMPLETED { color: #67c23a; }
.status-CANCELED, .status-REFUNDED { color: #909399; }
.order-amount { margin-top: 16rpx; font-size: 26rpx; color: #606266; }
.amount { color: #f56c6c; font-size: 32rpx; font-weight: bold; margin-left: 8rpx; }
.order-time { font-size: 22rpx; color: #c0c4cc; margin-top: 12rpx; }
.empty { text-align: center; color: #c0c4cc; padding: 80rpx 0; }
</style>
