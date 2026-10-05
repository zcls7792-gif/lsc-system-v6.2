<template>
  <div class="page" v-if="order">
    <van-cell-group inset>
      <van-cell title="订单号" :value="String(order.orderId)" />
      <van-cell title="状态">
        <template #value>
          <span :class="'status-' + order.status">{{ statusText(order.status) }}</span>
        </template>
      </van-cell>
      <van-cell title="买家类型" :value="order.buyerType" />
      <van-cell title="优惠方式" :value="order.discountMode" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="商品金额" :value="'¥' + formatCent(order.goodsCent)" />
      <van-cell title="优惠券" :value="'-¥' + formatCent(order.couponCent)" />
      <van-cell title="LSC抵扣" :value="'-' + formatUnit(order.lscUnit)" />
      <van-cell title="实付金额">
        <template #value>
          <span class="amount">¥{{ formatCent(order.rmbCent) }}</span>
        </template>
      </van-cell>
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="创建时间" :value="order.createdAt" />
    </van-cell-group>

    <div class="footer-bar" v-if="order.status === 'PENDING'">
      <van-button block @click="handleCancel">取消订单</van-button>
      <van-button type="primary" block>去支付</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { showToast } from 'vant'
import { getOrder, cancelOrder, type Order } from '@/api/order'

const route = useRoute()
const order = ref<Order | null>(null)

onMounted(async () => {
  order.value = await getOrder(Number(route.params.id))
})

async function handleCancel() {
  if (!order.value) return
  await cancelOrder(order.value.orderId)
  showToast('已取消')
  order.value.status = 'CANCELED'
}

function formatCent(c: number) { return (c / 100).toFixed(2) }
function formatUnit(u: number) { return (u / 10000).toFixed(4) + ' LSC' }
function statusText(s: string) {
  return ({ PENDING: '待支付', PAID: '已支付', COMPLETED: '已完成', CANCELED: '已取消', REFUNDED: '已退款' } as any)[s] || s
}
</script>

<style scoped>
.page { padding-bottom: 70px; }
.amount { color: #ee0a24; font-size: 17px; font-weight: bold; }
.status-PENDING { color: #ff976a; }
.status-PAID { color: #1989fa; }
.status-COMPLETED { color: #07c160; }
.status-CANCELED, .status-REFUNDED { color: #969799; }
.footer-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: #fff; padding: 12px 16px;
  display: flex; gap: 12px;
  box-shadow: 0 -1px 6px rgba(0,0,0,0.06);
}
.footer-bar .van-button { flex: 1; }
</style>
