<template>
  <view class="order-detail" v-if="order">
    <!-- 订单状态 -->
    <view class="order-detail__status" :class="statusClass">
      <text class="order-detail__status-text">{{ statusText }}</text>
      <view v-if="order.status === 0 && order.expireTime" class="order-detail__countdown">
        <CountDown :end-time="order.expireTime" @finish="onExpire">
          <template #default="{ text }">
            <text>剩余 {{ text }} 自动关闭</text>
          </template>
        </CountDown>
      </view>
    </view>

    <!-- 收货地址 -->
    <view v-if="order.address" class="order-detail__address card">
      <view class="order-detail__addr-icon">📍</view>
      <view class="order-detail__addr-info">
        <view class="order-detail__addr-user">
          <text class="fw-bold">{{ order.address.name }}</text>
          <text class="fs-sm text-secondary">{{ order.address.phone }}</text>
        </view>
        <text class="fs-sm">{{ fullAddress }}</text>
      </view>
    </view>

    <!-- 商品信息 -->
    <view class="order-detail__items card">
      <view v-for="(item, idx) in order.items" :key="idx" class="order-detail__item">
        <image class="order-detail__item-img" :src="item.productImage || '/static/placeholder/product.png'" mode="aspectFill" />
        <view class="order-detail__item-info">
          <text class="text-ellipsis-2">{{ item.productName }}</text>
          <text v-if="item.spec" class="fs-sm text-secondary">{{ item.spec }}</text>
          <view class="order-detail__item-bottom">
            <view class="price-row">
              <text class="price-rmb">¥{{ formatPrice(item.price) }}</text>
              <text class="price-lsc fs-sm">{{ Math.floor(item.lscPrice) }} LSC</text>
            </view>
            <text class="fs-sm text-secondary">x{{ item.quantity }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 支付信息 -->
    <view class="order-detail__pay card">
      <text class="fw-bold">支付信息</text>
      <view class="order-detail__pay-row">
        <text class="text-secondary">订单总额</text>
        <text>¥{{ formatPrice(order.totalAmount) }}</text>
      </view>
      <view class="order-detail__pay-row">
        <text class="text-lsc">LSC 支付</text>
        <text class="text-lsc">{{ order.lscAmount }} LSC</text>
      </view>
      <view class="order-detail__pay-row">
        <text class="text-secondary">人民币支付</text>
        <text class="price-rmb">¥{{ formatPrice(order.rmbAmount) }}</text>
      </view>
      <view v-if="order.refundAmount" class="order-detail__pay-row">
        <text class="text-danger">退款金额</text>
        <text class="text-danger">¥{{ formatPrice(order.refundAmount) }}</text>
      </view>
    </view>

    <!-- 商家信息 + 导航 -->
    <view v-if="order.store" class="order-detail__store card" @click="goStoreMap">
      <view class="order-detail__store-icon">🏪</view>
      <view class="order-detail__store-info">
        <text class="fw-bold">{{ order.store.name }}</text>
        <text class="fs-sm text-secondary text-ellipsis">{{ order.store.address }}</text>
      </view>
      <view class="order-detail__store-nav" @click.stop="navigateToStore">
        <text class="order-detail__store-nav-icon">🧭</text>
        <text class="fs-sm text-primary">导航</text>
      </view>
    </view>

    <!-- 订单信息 -->
    <view class="order-detail__info card">
      <view class="order-detail__info-row">
        <text class="text-secondary">订单编号</text>
        <text class="order-detail__info-value" @click="copyOrderNo">{{ order.orderNo }} 复制</text>
      </view>
      <view class="order-detail__info-row">
        <text class="text-secondary">下单时间</text>
        <text>{{ order.createTime }}</text>
      </view>
      <view v-if="order.payTime" class="order-detail__info-row">
        <text class="text-secondary">支付时间</text>
        <text>{{ order.payTime }}</text>
      </view>
      <view v-if="order.refundReason" class="order-detail__info-row">
        <text class="text-secondary">退款原因</text>
        <text class="text-danger">{{ order.refundReason }}</text>
      </view>
    </view>

    <view style="height: 140rpx"></view>

    <!-- 底部操作 -->
    <view class="order-detail__footer footer-bar">
      <button v-if="order.status === 0" class="order-detail__btn" @click="onCancel">取消订单</button>
      <button v-if="order.status === 0" class="order-detail__btn order-detail__btn--primary" @click="onPay">立即付款</button>
      <button v-if="order.status === 1" class="order-detail__btn order-detail__btn--primary" @click="onConfirm">确认收货</button>
      <button v-if="order.status === 1 || order.status === 2" class="order-detail__btn" @click="onRefund">申请退款</button>
    </view>

    <!-- 支付弹窗 -->
    <view v-if="showPaySheet" class="order-detail__mask" @click="showPaySheet = false">
      <view class="order-detail__sheet" @click.stop>
        <view class="order-detail__sheet-title">混合支付</view>
        <HybridPaySlider
          :total-amount="order.totalAmount"
          :available="availableLsc"
          v-model="payLsc"
        />
        <view class="order-detail__pay-methods">
          <text class="fs-sm text-secondary">支付方式</text>
          <view
            v-for="m in payMethods"
            :key="m.value"
            class="order-detail__pay-method"
            @click="payMethod = m.value"
          >
            <text>{{ m.icon }}</text>
            <text class="flex-1">{{ m.label }}</text>
            <view class="order-detail__radio" :class="{ 'order-detail__radio--active': payMethod === m.value }">
              <text v-if="payMethod === m.value" class="order-detail__radio-icon">✓</text>
            </view>
          </view>
        </view>
        <button class="order-detail__pay-btn btn-primary" :loading="paying" @click="doPay">
          确认支付 ¥{{ formatPrice(payRmb) }}
        </button>
      </view>
    </view>
  </view>

  <view v-else class="order-detail__loading">
    <text>加载中...</text>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getOrderDetail, cancelOrder, confirmReceive, payOrder, type Order } from '@/api/order'
import { openNavigation } from '@/api/map'
import { useUserStore } from '@/stores/user'
import { calcHybridPay, round2 } from '@/utils/pay'
import HybridPaySlider from '@/components/HybridPaySlider.vue'
import CountDown from '@/components/CountDown.vue'

const userStore = useUserStore()
const order = ref<Order | null>(null)
const showPaySheet = ref(false)
const paying = ref(false)
const payLsc = ref(0)
const payMethod = ref<'wechat' | 'alipay' | 'balance'>('wechat')

const payMethods = [
  { value: 'wechat' as const, label: '微信支付', icon: '💚' },
  { value: 'alipay' as const, label: '支付宝', icon: '💙' },
  { value: 'balance' as const, label: '余额支付', icon: '💰' },
]

const availableLsc = computed(() => userStore.availableLsc)

const hybridResult = computed(() =>
  order.value ? calcHybridPay(order.value.totalAmount, payLsc.value, availableLsc.value) : calcHybridPay(0, 0, 0),
)
const payRmb = computed(() => hybridResult.value.rmbAmount)

const statusText = computed(() => {
  const map: Record<number, string> = {
    0: '待支付',
    1: '已支付，待收货',
    2: '已完成',
    3: '已取消',
    4: '已退款',
    5: '部分退款',
  }
  return map[order.value?.status ?? -1] || ''
})

const statusClass = computed(() => {
  const s = order.value?.status
  if (s === 0) return 'order-detail__status--warning'
  if (s === 1) return 'order-detail__status--primary'
  if (s === 2) return 'order-detail__status--success'
  return 'order-detail__status--info'
})

const fullAddress = computed(() => {
  if (!order.value?.address) return ''
  const a = order.value.address
  return `${a.province}${a.city}${a.district}${a.detail}`
})

function formatPrice(n: number): string {
  return round2(n).toFixed(2)
}

async function loadDetail(id: string, autoPay?: boolean) {
  try {
    order.value = await getOrderDetail(id)
    if (autoPay && order.value?.status === 0) {
      showPaySheet.value = true
    }
  } catch (e) {
    // ignore
  }
}

function copyOrderNo() {
  uni.setClipboardData({ data: order.value?.orderNo || '' })
}

function onCancel() {
  uni.showModal({
    title: '提示',
    content: '确认取消该订单？',
    success: async (res) => {
      if (res.confirm) {
        await cancelOrder(order.value!.id)
        uni.showToast({ title: '已取消', icon: 'success' })
        loadDetail(String(order.value!.id))
      }
    },
  })
}

function onPay() {
  if (!userStore.lscAccount) userStore.fetchLscAccount().catch(() => {})
  showPaySheet.value = true
}

async function doPay() {
  if (!order.value) return
  paying.value = true
  try {
    const res = await payOrder({
      orderId: order.value.id,
      lscAmount: hybridResult.value.lscAmount,
      payMethod: payMethod.value,
    })
    // 微信支付需调起支付
    if (res.wxPayParams && payMethod.value === 'wechat') {
      await new Promise<void>((resolve, reject) => {
        uni.requestPayment({
          provider: 'wxpay',
          timeStamp: res.wxPayParams!.timeStamp,
          nonceStr: res.wxPayParams!.nonceStr,
          package: res.wxPayParams!.package,
          signType: res.wxPayParams!.signType as any,
          paySign: res.wxPayParams!.paySign,
          success: () => resolve(),
          fail: (err) => reject(err),
        })
      })
    }
    showPaySheet.value = false
    uni.showToast({ title: '支付成功', icon: 'success' })
    setTimeout(() => loadDetail(String(order.value!.id)), 800)
  } catch (e) {
    // ignore
  } finally {
    paying.value = false
  }
}

function onConfirm() {
  uni.showModal({
    title: '确认收货',
    content: '确认已收到商品？',
    success: async (res) => {
      if (res.confirm) {
        await confirmReceive(order.value!.id)
        uni.showToast({ title: '已确认收货', icon: 'success' })
        loadDetail(String(order.value!.id))
      }
    },
  })
}

function onRefund() {
  uni.navigateTo({ url: `/src/pages-order/refund/index?id=${order.value!.id}` })
}

function navigateToStore() {
  if (!order.value?.store) return
  openNavigation({
    name: order.value.store.name,
    address: order.value.store.address,
    latitude: order.value.store.latitude,
    longitude: order.value.store.longitude,
  })
}

function goStoreMap() {
  if (!order.value?.store) return
  uni.navigateTo({
    url: `/src/pages-store/map/index?lat=${order.value.store.latitude}&lng=${order.value.store.longitude}&name=${encodeURIComponent(order.value.store.name)}&addr=${encodeURIComponent(order.value.store.address)}`,
  })
}

function onExpire() {
  loadDetail(String(order.value!.id))
}

onLoad((options) => {
  if (options?.id) loadDetail(options.id, options?.pay === '1')
})

onShow(() => {
  if (order.value?.id) loadDetail(String(order.value.id))
})
</script>

<style lang="scss" scoped>
.order-detail {
  padding: $spacing-base;
  min-height: 100vh;

  &__status {
    background: $primary;
    color: #fff;
    border-radius: $radius-lg;
    padding: $spacing-lg $spacing-base;
    margin-bottom: $spacing-base;
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;

    &--warning { background: linear-gradient(135deg, $warning, #FF8C33); }
    &--primary { background: linear-gradient(135deg, $primary, $primary-light); }
    &--success { background: linear-gradient(135deg, $success, #2DCE89); }
    &--info { background: linear-gradient(135deg, $info, #B0B3BB); }
  }

  &__status-text {
    font-size: $font-lg;
    font-weight: 700;
  }

  &__countdown {
    font-size: $font-sm;
    opacity: 0.95;
  }

  &__address {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    margin-bottom: $spacing-base;
    border-left: 8rpx solid $primary;
  }

  &__addr-icon {
    font-size: 40rpx;
  }

  &__addr-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    min-width: 0;
  }

  &__addr-user {
    display: flex;
    gap: $spacing-base;
    align-items: baseline;
  }

  &__items {
    margin-bottom: $spacing-base;
  }

  &__item {
    display: flex;
    gap: $spacing-base;
    padding: $spacing-sm 0;
    border-bottom: 1rpx solid $border-color-light;

    &:last-child {
      border-bottom: none;
    }
  }

  &__item-img {
    width: 140rpx;
    height: 140rpx;
    border-radius: $radius-base;
    background: $bg-gray;
    flex-shrink: 0;
  }

  &__item-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
    min-width: 0;
  }

  &__item-bottom {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
  }

  &__pay,
  &__info,
  &__store {
    margin-bottom: $spacing-base;
  }

  &__pay-row,
  &__info-row {
    display: flex;
    justify-content: space-between;
    padding: $spacing-xs 0;
    font-size: $font-base;
  }

  &__info-value {
    color: $primary;
    font-size: $font-sm;
  }

  &__store {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    border-left: 8rpx solid $lsc-color;
  }

  &__store-icon {
    font-size: 44rpx;
  }

  &__store-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    min-width: 0;
  }

  &__store-nav {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: $spacing-sm;
    border-left: 1rpx solid $border-color-light;
  }

  &__store-nav-icon {
    font-size: 36rpx;
  }

  &__footer {
    gap: $spacing-base;
    justify-content: flex-end;
  }

  &__btn {
    font-size: $font-sm;
    height: 64rpx;
    line-height: 64rpx;
    padding: 0 $spacing-lg;
    border-radius: 999rpx;
    background: #fff;
    border: 1rpx solid $border-color;
    color: $text-regular;
    margin: 0;

    &--primary {
      background: linear-gradient(135deg, $primary, $primary-light);
      color: #fff;
      border: none;
    }
  }

  &__mask {
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 999;
    display: flex;
    align-items: flex-end;
  }

  &__sheet {
    width: 100%;
    background: #fff;
    border-radius: $radius-xl $radius-xl 0 0;
    padding: $spacing-base;
    padding-bottom: calc(#{$spacing-base} + env(safe-area-inset-bottom));
  }

  &__sheet-title {
    text-align: center;
    font-weight: 700;
    font-size: $font-md;
    margin-bottom: $spacing-base;
  }

  &__pay-methods {
    margin-top: $spacing-base;
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
  }

  &__pay-method {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    padding: $spacing-sm 0;
    font-size: $font-base;
  }

  &__radio {
    width: 36rpx;
    height: 36rpx;
    border: 2rpx solid $border-color;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;

    &--active {
      background: $primary;
      border-color: $primary;
    }
  }

  &__radio-icon {
    color: #fff;
    font-size: 22rpx;
  }

  &__pay-btn {
    width: 100%;
    margin-top: $spacing-base;
  }

  &__loading {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    color: $text-secondary;
  }
}
</style>
