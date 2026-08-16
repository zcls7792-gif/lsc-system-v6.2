<template>
  <view class="order-create">
    <!-- 收货地址 -->
    <view class="order-create__address card" @click="chooseAddress">
      <template v-if="address">
        <view class="order-create__addr-icon">📍</view>
        <view class="order-create__addr-info">
          <view class="order-create__addr-user">
            <text class="fw-bold">{{ address.name }}</text>
            <text class="fs-sm text-secondary">{{ address.phone }}</text>
          </view>
          <text class="fs-sm text-ellipsis-2">{{ fullAddress }}</text>
        </view>
      </template>
      <template v-else>
        <view class="order-create__addr-empty">
          <text>请选择收货地址</text>
        </view>
      </template>
      <text class="order-create__addr-arrow">›</text>
    </view>

    <!-- 商品列表 -->
    <view class="order-create__items card">
      <view v-for="(item, idx) in orderItems" :key="idx" class="order-create__item">
        <image class="order-create__item-img" :src="item.productImage || '/static/placeholder/product.png'" mode="aspectFill" />
        <view class="order-create__item-info">
          <text class="text-ellipsis-2">{{ item.productName }}</text>
          <text v-if="item.spec" class="fs-sm text-secondary">{{ item.spec }}</text>
          <view class="order-create__item-bottom">
            <view class="price-row">
              <text class="price-rmb">¥{{ formatPrice(item.price) }}</text>
              <text class="price-lsc fs-sm">{{ Math.floor(item.lscPrice) }} LSC</text>
            </view>
            <text class="fs-sm text-secondary">x{{ item.quantity }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 混合支付 -->
    <HybridPaySlider
      :total-amount="totalAmount"
      :available="availableLsc"
      v-model="useLsc"
      @change="onHybridChange"
    />

    <!-- 金额明细 -->
    <view class="order-create__detail card">
      <view class="order-create__detail-row">
        <text class="text-secondary">商品金额</text>
        <text>¥{{ formatPrice(totalAmount) }}</text>
      </view>
      <view class="order-create__detail-row">
        <text class="text-secondary">运费</text>
        <text class="text-success">免邮</text>
      </view>
      <view class="order-create__detail-row">
        <text class="text-lsc">LSC 抵扣</text>
        <text class="text-lsc">-{{ hybridResult.lscAmount }} LSC</text>
      </view>
      <view class="order-create__detail-row order-create__detail-row--total">
        <text class="fw-bold">实付人民币</text>
        <text class="price-rmb fs-lg fw-bold">¥{{ formatPrice(hybridResult.rmbAmount) }}</text>
      </view>
    </view>

    <!-- 备注 -->
    <view class="order-create__remark card">
      <text class="text-secondary">订单备注</text>
      <input class="order-create__remark-input" v-model="remark" placeholder="选填，给商家留言" maxlength="50" />
    </view>

    <view style="height: 140rpx"></view>

    <!-- 底部提交 -->
    <view class="order-create__footer footer-bar">
      <view class="order-create__footer-amount">
        <text class="fs-sm text-secondary">合计:</text>
        <text class="price-rmb fs-lg fw-bold">¥{{ formatPrice(hybridResult.rmbAmount) }}</text>
        <text v-if="hybridResult.lscAmount > 0" class="fs-sm text-lsc">+ {{ hybridResult.lscAmount }} LSC</text>
      </view>
      <button class="order-create__submit btn-primary" :loading="submitting" @click="onSubmit">提交订单</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { previewOrder, createOrder, type OrderItem } from '@/api/order'
import { getAddressList, type Address } from '@/api/map'
import { useUserStore } from '@/stores/user'
import { calcHybridPay, round2 } from '@/utils/pay'
import HybridPaySlider from '@/components/HybridPaySlider.vue'

const userStore = useUserStore()
const address = ref<Address | null>(null)
const orderItems = ref<OrderItem[]>([])
const useLsc = ref(0)
const remark = ref('')
const submitting = ref(false)
const fromCart = ref(false)
const rawItems = ref<Array<{ productId: number; quantity: number; spec?: string }>>([])

const availableLsc = computed(() => userStore.availableLsc)
const totalAmount = computed(() =>
  round2(orderItems.value.reduce((s, i) => s + i.price * i.quantity, 0)),
)
const hybridResult = computed(() =>
  calcHybridPay(totalAmount.value, useLsc.value, availableLsc.value),
)

const fullAddress = computed(() => {
  if (!address.value) return ''
  const a = address.value
  return `${a.province}${a.city}${a.district}${a.detail}`
})

function formatPrice(n: number): string {
  return round2(n).toFixed(2)
}

function onHybridChange(r: ReturnType<typeof calcHybridPay>) {
  // 父组件已通过 v-model 同步
}

async function loadDefaultAddress() {
  try {
    const list = await getAddressList()
    address.value = list.find((a) => a.isDefault) || list[0] || null
  } catch (e) {
    // ignore
  }
}

async function preview() {
  if (!rawItems.value.length) return
  try {
    const res = await previewOrder({
      addressId: address.value?.id || 0,
      items: rawItems.value,
      lscAmount: useLsc.value,
    })
    orderItems.value = res.items
    if (res.address && !address.value) {
      address.value = {
        id: 0,
        name: res.address.name,
        phone: res.address.phone,
        province: res.address.province,
        city: res.address.city,
        district: res.address.district,
        detail: res.address.detail,
        isDefault: false,
      } as Address
    }
  } catch (e) {
    // 预览失败，使用本地数据（直购场景商品信息从详情页带入不足，需请求）
  }
}

function chooseAddress() {
  uni.navigateTo({
    url: '/src/pages-address/list/index?from=order',
  })
}

async function onSubmit() {
  if (!address.value) {
    uni.showToast({ title: '请选择收货地址', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const res = await createOrder({
      addressId: address.value.id,
      items: rawItems.value,
      lscAmount: hybridResult.value.lscAmount,
      remark: remark.value,
      fromCart: fromCart.value,
    })
    uni.showToast({ title: '订单创建成功', icon: 'success' })
    // 跳转支付
    setTimeout(() => {
      uni.redirectTo({
        url: `/src/pages-order/detail/index?id=${res.orderId}&pay=1`,
      })
    }, 800)
  } catch (e) {
    // ignore
  } finally {
    submitting.value = false
  }
}

onLoad(async (options) => {
  if (options?.items) {
    rawItems.value = JSON.parse(decodeURIComponent(options.items))
  }
  if (options?.fromCart) fromCart.value = true
  if (options?.lsc) useLsc.value = Number(options.lsc) || 0

  await loadDefaultAddress()
  await preview()
})

// 监听地址选择返回（uni eventChannel 简化用全局事件）
uni.$on('address:selected', (a: Address) => {
  address.value = a
  preview()
})

onMounted(() => {
  if (userStore.isLoggedIn && !userStore.lscAccount) {
    userStore.fetchLscAccount().catch(() => {})
  }
})
</script>

<style lang="scss" scoped>
.order-create {
  padding: $spacing-base;
  min-height: 100vh;

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

  &__addr-empty {
    flex: 1;
    color: $text-secondary;
  }

  &__addr-arrow {
    color: $text-placeholder;
    font-size: $font-md;
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

  &__detail {
    margin-top: $spacing-base;
  }

  &__detail-row {
    display: flex;
    justify-content: space-between;
    padding: $spacing-xs 0;

    &--total {
      margin-top: $spacing-sm;
      padding-top: $spacing-sm;
      border-top: 1rpx solid $border-color-light;
    }
  }

  &__remark {
    margin-top: $spacing-base;
    display: flex;
    align-items: center;
    gap: $spacing-base;
  }

  &__remark-input {
    flex: 1;
    font-size: $font-base;
  }

  &__footer {
    gap: $spacing-base;
  }

  &__footer-amount {
    flex: 1;
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
  }

  &__submit {
    flex-shrink: 0;
  }
}
</style>
