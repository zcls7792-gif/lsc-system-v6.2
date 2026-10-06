<template>
  <div class="page" v-if="order">
    <van-cell-group inset>
      <van-cell title="订单号" :value="orderNoText" />
      <van-cell title="状态">
        <template #value>
          <span :class="'status-' + payStatus">{{ statusText(payStatus) }}</span>
        </template>
      </van-cell>
      <van-cell title="买家类型" :value="buyerType" />
      <van-cell title="优惠方式" :value="discountMode" />
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="商品金额" :value="'¥' + formatCent(goodsCent)" />
      <van-cell title="优惠券" :value="'-¥' + formatCent(couponCent)" />
      <van-cell title="LSC抵扣" :value="'-' + formatUnit(lscUnit)" />
      <van-cell title="实付金额">
        <template #value>
          <span class="amount">¥{{ formatCent(rmbCent) }}</span>
        </template>
      </van-cell>
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px" v-if="items.length">
      <van-cell title="商品明细" />
      <div class="order-item" v-for="it in items" :key="it.itemId || it.item_id">
        <div class="item-row">
          <span class="item-sku">SKU #{{ it.skuId || it.sku_id }}</span>
          <span class="item-qty">×{{ it.qty }}</span>
        </div>
        <div class="item-row">
          <span class="item-price">单价 ¥{{ formatCent(it.unitPriceCent ?? it.unit_price_cent) }}</span>
          <span class="item-line">小计 ¥{{ formatCent(it.lineGoodsCent ?? it.line_goods_cent) }}</span>
        </div>
      </div>
    </van-cell-group>

    <van-cell-group inset style="margin-top: 12px">
      <van-cell title="创建时间" :value="order.createdAt || order.created_at" />
    </van-cell-group>

    <div class="footer-bar" v-if="payStatus === 'PENDING' || payStatus === 'UNPAID'">
      <van-button block @click="handleCancel">取消订单</van-button>
      <van-button type="primary" block>去支付</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
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
  ;(order.value as any).status = 'CANCELED'
  ;(order.value as any).payment_status = 'CANCELED'
}

const payStatus = computed(() => {
  const o = order.value as any
  return o?.status || o?.paymentStatus || o?.payment_status || 'PENDING'
})
const goodsCent = computed(() => (order.value as any)?.goodsCent ?? (order.value as any)?.goods_cent ?? 0)
const couponCent = computed(() => (order.value as any)?.couponCent ?? (order.value as any)?.coupon_cent ?? 0)
const lscUnit = computed(() => (order.value as any)?.lscUnit ?? (order.value as any)?.lsc_unit ?? 0)
const rmbCent = computed(() => (order.value as any)?.rmbCent ?? (order.value as any)?.rmb_cent ?? 0)
const buyerType = computed(() => (order.value as any)?.buyerType || (order.value as any)?.buyer_type_snapshot || (order.value as any)?.buyer_type || '-')
const discountMode = computed(() => (order.value as any)?.discountMode || (order.value as any)?.discount_mode || '-')
const items = computed(() => (order.value as any)?.items || [])
const orderNoText = computed(() => {
  const o = order.value as any
  return o?.orderNo || o?.order_no || String(o?.orderId ?? o?.order_id ?? '-')
})

function formatCent(c: number) { return ((c ?? 0) / 100).toFixed(2) }
function formatUnit(u: number) { return ((u ?? 0) / 10000).toFixed(4) + ' LSC' }
function statusText(s: string) {
  return ({ PENDING: '待支付', PAID: '已支付', UNPAID: '待支付', COMPLETED: '已完成', CREATED: '待支付', CANCELED: '已取消', REFUNDED: '已退款' } as any)[s] || s
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
.order-item { padding: 10px 16px; border-top: 1px solid #f2f3f5; }
.item-row { display: flex; justify-content: space-between; font-size: 13px; color: #646566; margin-top: 4px; }
.item-sku, .item-price { color: #323233; }
.item-line { color: #ee0a24; font-weight: bold; }
</style>
