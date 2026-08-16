<template>
  <view class="order-list">
    <!-- tab 切换 -->
    <view class="order-list__tabs">
      <text
        v-for="t in tabs"
        :key="t.value"
        class="order-list__tab"
        :class="{ 'order-list__tab--active': activeStatus === t.value }"
        @click="switchTab(t.value)"
      >{{ t.label }}</text>
    </view>

    <scroll-view
      scroll-y
      class="order-list__scroll"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view class="order-list__content">
        <OrderCard
          v-for="order in orders"
          :key="order.id"
          :order="order"
          @action="onAction"
        />

        <LoadMore v-if="orders.length" :status="loadStatus" />
        <EmptyState
          v-else-if="!loading"
          text="暂无相关订单"
          icon-text="📦"
          action-text="去商城逛逛"
          @action="goMall"
        />
      </view>
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getOrderList, cancelOrder, confirmReceive, type Order } from '@/api/order'
import { useCartStore } from '@/stores/cart'
import OrderCard from '@/components/OrderCard.vue'
import LoadMore from '@/components/LoadMore.vue'
import EmptyState from '@/components/EmptyState.vue'

const tabs = [
  { label: '全部', value: -1 },
  { label: '待支付', value: 0 },
  { label: '已支付', value: 1 },
  { label: '已完成', value: 2 },
  { label: '退款', value: 4 },
]

const activeStatus = ref(-1)
const orders = ref<Order[]>([])
const page = ref(1)
const size = 10
const loading = ref(false)
const loadStatus = ref<'loadmore' | 'loading' | 'noMore' | 'error'>('loadmore')
const refreshing = ref(false)
const cartStore = useCartStore()

async function loadOrders(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    orders.value = []
    loadStatus.value = 'loadmore'
  }
  loading.value = true
  loadStatus.value = 'loading'
  try {
    const res = await getOrderList({
      page: page.value,
      size,
      status: activeStatus.value,
    })
    const list = res.list || []
    if (reset) orders.value = list
    else orders.value.push(...list)
    loadStatus.value = list.length < size ? 'noMore' : 'loadmore'
  } catch (e) {
    loadStatus.value = 'error'
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function switchTab(v: number) {
  if (activeStatus.value === v) return
  activeStatus.value = v
  loadOrders(true)
}

function loadMore() {
  if (loadStatus.value !== 'loadmore') return
  page.value++
  loadOrders(false)
}

async function onRefresh() {
  refreshing.value = true
  await loadOrders(true)
}

function goMall() {
  uni.switchTab({ url: '/src/pages/mall/index' })
}

async function onAction(type: string, order: Order) {
  switch (type) {
    case 'pay':
      uni.navigateTo({ url: `/src/pages-order/detail/index?id=${order.id}&pay=1` })
      break
    case 'cancel':
      uni.showModal({
        title: '提示',
        content: '确认取消该订单？',
        success: async (res) => {
          if (res.confirm) {
            await cancelOrder(order.id)
            uni.showToast({ title: '已取消', icon: 'success' })
            loadOrders(true)
          }
        },
      })
      break
    case 'confirm':
      uni.showModal({
        title: '确认收货',
        content: '确认已收到商品？',
        success: async (res) => {
          if (res.confirm) {
            await confirmReceive(order.id)
            uni.showToast({ title: '已确认收货', icon: 'success' })
            loadOrders(true)
          }
        },
      })
      break
    case 'refund':
      uni.navigateTo({ url: `/src/pages-order/refund/index?id=${order.id}` })
      break
    case 'review':
      uni.navigateTo({ url: `/src/pages-order/detail/index?id=${order.id}` })
      break
    case 'expire':
      loadOrders(true)
      break
  }
}

onLoad((options) => {
  if (options?.status !== undefined) {
    activeStatus.value = Number(options.status)
  }
  loadOrders(true)
})

onShow(() => {
  if (orders.value.length) loadOrders(true)
})
</script>

<style lang="scss" scoped>
.order-list {
  display: flex;
  flex-direction: column;
  height: 100vh;

  &__tabs {
    display: flex;
    background: #fff;
    padding: $spacing-sm $spacing-base;
    gap: $spacing-sm;
    flex-shrink: 0;
  }

  &__tab {
    flex: 1;
    text-align: center;
    padding: $spacing-xs 0;
    font-size: $font-sm;
    color: $text-secondary;
    border-radius: $radius-base;

    &--active {
      color: $primary;
      font-weight: 600;
      background: $primary-bg;
    }
  }

  &__scroll {
    flex: 1;
    height: 0;
  }

  &__content {
    padding: $spacing-base;
  }
}
</style>
