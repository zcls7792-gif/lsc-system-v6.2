<template>
  <div class="page">
    <van-tabs v-model:active="activeTab" @change="onChange">
      <van-tab v-for="tab in tabs" :key="tab.value" :title="tab.label" :name="tab.value" />
    </van-tabs>

    <van-list v-model:loading="loading" :finished="finished" @load="onLoad">
      <div v-for="o in orders" :key="o.orderId" class="order-card" @click="goDetail(o.orderId)">
        <div class="order-header">
          <span class="order-no">订单 #{{ o.orderNo || o.order_no || o.orderId || o.order_id }}</span>
          <span :class="'status-' + paymentStatus(o)">{{ statusText(paymentStatus(o)) }}</span>
        </div>
        <div class="order-amount">
          实付 <span class="amount">¥{{ formatCent(o.rmbCent ?? o.rmb_cent) }}</span>
        </div>
        <div class="order-time">{{ o.createdAt || o.created_at }}</div>
      </div>
      <van-empty v-if="!loading && orders.length === 0" description="暂无订单" />
    </van-list>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { listOrders, type Order } from '@/api/order'

const tabs = [
  { label: '全部', value: '' },
  { label: '待支付', value: 'PENDING' },
  { label: '已支付', value: 'PAID' },
  { label: '已完成', value: 'COMPLETED' }
]
const activeTab = ref('')
const orders = ref<Order[]>([])
const loading = ref(false)
const finished = ref(false)
const router = useRouter()

async function onLoad() {
  try {
    orders.value = await listOrders(activeTab.value || undefined)
  } finally {
    loading.value = false
    finished.value = true
  }
}

function onChange() {
  loading.value = true
  finished.value = false
  onLoad()
}

function goDetail(id: number) {
  router.push(`/order/${id}`)
}

function formatCent(c: number) { return ((c ?? 0) / 100).toFixed(2) }
function paymentStatus(o: any) { return o.status || o.paymentStatus || o.payment_status || 'PENDING' }
function statusText(s: string) {
  return ({ PENDING: '待支付', PAID: '已支付', UNPAID: '待支付', COMPLETED: '已完成', CREATED: '待支付', CANCELED: '已取消', REFUNDED: '已退款' } as any)[s] || s
}
</script>

<style scoped>
.page { padding-bottom: 50px; }
.order-card { background: #fff; margin: 12px; padding: 16px; border-radius: 10px; }
.order-header { display: flex; justify-content: space-between; }
.order-no { font-weight: bold; }
.status-PENDING { color: #ff976a; }
.status-PAID { color: #1989fa; }
.status-COMPLETED { color: #07c160; }
.status-CANCELED, .status-REFUNDED { color: #969799; }
.order-amount { margin-top: 12px; font-size: 13px; color: #646566; }
.amount { color: #ee0a24; font-size: 17px; font-weight: bold; margin-left: 6px; }
.order-time { font-size: 11px; color: #c8c9cc; margin-top: 10px; }
</style>
