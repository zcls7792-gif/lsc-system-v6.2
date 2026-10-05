<template>
  <view class="page" v-if="order">
    <view class="card">
      <view class="row">
        <text class="label">订单号</text>
        <text>{{ order.orderId }}</text>
      </view>
      <view class="row">
        <text class="label">状态</text>
        <text :class="'status-' + order.status">{{ statusText(order.status) }}</text>
      </view>
      <view class="row">
        <text class="label">买家类型</text>
        <text>{{ order.buyerType }}</text>
      </view>
      <view class="row">
        <text class="label">优惠方式</text>
        <text>{{ order.discountMode }}</text>
      </view>
    </view>

    <view class="card">
      <view class="row"><text class="label">商品金额</text><text>¥{{ formatCent(order.goodsCent) }}</text></view>
      <view class="row"><text class="label">优惠券</text><text>-¥{{ formatCent(order.couponCent) }}</text></view>
      <view class="row"><text class="label">LSC抵扣</text><text>-{{ formatUnit(order.lscUnit) }}</text></view>
      <view class="row total"><text class="label">实付金额</text><text class="amount">¥{{ formatCent(order.rmbCent) }}</text></view>
    </view>

    <view class="card">
      <view class="row"><text class="label">创建时间</text><text>{{ order.createdAt }}</text></view>
    </view>

    <view class="footer-bar" v-if="order.status === 'PENDING'">
      <button class="btn-cancel" @click="handleCancel">取消订单</button>
      <button class="btn-pay">去支付</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getOrder, cancelOrder, type Order } from '@/api/order'

const order = ref<Order | null>(null)

onMounted(async () => {
  const pages = getCurrentPages()
  const q = (pages[pages.length - 1] as any).options || {}
  if (q.id) order.value = await getOrder(Number(q.id))
})

async function handleCancel() {
  if (!order.value) return
  await cancelOrder(order.value.orderId)
  uni.showToast({ title: '已取消', icon: 'success' })
  order.value.status = 'CANCELED'
}

function formatCent(c: number) { return (c / 100).toFixed(2) }
function formatUnit(u: number) { return (u / 10000).toFixed(4) + ' LSC' }
function statusText(s: string) {
  return ({ PENDING: '待支付', PAID: '已支付', COMPLETED: '已完成', CANCELED: '已取消', REFUNDED: '已退款' } as any)[s] || s
}
</script>

<style scoped>
.page { padding: 24rpx; padding-bottom: 140rpx; }
.card { background: #fff; border-radius: 12rpx; padding: 24rpx; margin-bottom: 20rpx; }
.row { display: flex; justify-content: space-between; padding: 12rpx 0; font-size: 26rpx; }
.label { color: #909399; }
.row.total { border-top: 1rpx solid #ebeef5; margin-top: 12rpx; padding-top: 20rpx; }
.amount { color: #f56c6c; font-size: 32rpx; font-weight: bold; }
.status-PENDING { color: #e6a23c; }
.status-PAID { color: #1f6feb; }
.status-COMPLETED { color: #67c23a; }
.status-CANCELED, .status-REFUNDED { color: #909399; }
.footer-bar { position: fixed; bottom: 0; left: 0; right: 0; background: #fff; padding: 20rpx 24rpx; display: flex; gap: 20rpx; }
.btn-cancel { flex: 1; background: #f4f4f5; color: #606266; border-radius: 40rpx; }
.btn-pay { flex: 1; background: #1f6feb; color: #fff; border-radius: 40rpx; }
</style>
