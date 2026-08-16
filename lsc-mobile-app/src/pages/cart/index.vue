<template>
  <view class="cart">
    <scroll-view
      v-if="cartStore.items.length"
      scroll-y
      class="cart__list"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <view
        v-for="(item, idx) in cartStore.items"
        :key="idx"
        class="cart__item card"
      >
        <!-- 选择框 -->
        <view
          class="cart__check"
          :class="{ 'cart__check--active': item.selected }"
          @click="cartStore.toggleSelect(item.product.id, item.spec)"
        >
          <text v-if="item.selected" class="cart__check-icon">✓</text>
        </view>

        <image
          class="cart__img"
          :src="item.product.cover || '/static/placeholder/product.png'"
          mode="aspectFill"
          @click="goDetail(item.product)"
        />

        <view class="cart__info">
          <text class="cart__name text-ellipsis-2" @click="goDetail(item.product)">{{ item.product.name }}</text>
          <text v-if="item.spec" class="cart__spec">{{ item.spec }}</text>
          <view class="cart__price-row">
            <text class="price-rmb">¥{{ formatPrice(item.product.price) }}</text>
            <text class="price-lsc fs-sm">{{ Math.floor(item.product.lscPrice) }} LSC</text>
          </view>
          <view class="cart__qty">
            <text class="cart__qty-btn" @click="dec(item)">－</text>
            <text class="cart__qty-num">{{ item.quantity }}</text>
            <text class="cart__qty-btn" @click="inc(item)">＋</text>
          </view>
        </view>

        <text class="cart__del" @click="onRemove(item)">删除</text>
      </view>

      <view style="height: 140rpx"></view>
    </scroll-view>

    <!-- 空状态 -->
    <EmptyState
      v-else
      text="购物车还是空的"
      icon-text="🛒"
      action-text="去逛逛"
      @action="goMall"
    />

    <!-- 底部混合支付计算 -->
    <view v-if="cartStore.items.length" class="cart__footer footer-bar">
      <view class="cart__check-all" @click="onToggleAll">
        <view class="cart__check" :class="{ 'cart__check--active': cartStore.isAllSelected }">
          <text v-if="cartStore.isAllSelected" class="cart__check-icon">✓</text>
        </view>
        <text class="fs-sm">全选</text>
      </view>

      <view class="cart__summary">
        <view class="cart__summary-row">
          <text class="fs-sm text-secondary">合计 ({{ cartStore.selectedCount }}件):</text>
          <text class="price-rmb fs-md fw-bold">¥{{ formatPrice(cartStore.selectedTotalPrice) }}</text>
        </view>
        <view class="cart__hybrid">
          <text class="fs-sm text-secondary">使用 LSC:</text>
          <input
            class="cart__lsc-input"
            type="number"
            v-model="useLsc"
            placeholder="0"
          />
          <text class="fs-sm text-lsc">LSC</text>
          <text class="fs-sm text-secondary">| 人民币补足</text>
          <text class="price-rmb fs-sm">¥{{ formatPrice(rmbSupplement) }}</text>
        </view>
      </view>

      <button class="cart__btn btn-primary" :disabled="!cartStore.selectedCount" @click="onCheckout">
        结算
      </button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useCartStore, type CartItem } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { calcHybridPay, round2 } from '@/utils/pay'
import EmptyState from '@/components/EmptyState.vue'

const cartStore = useCartStore()
const userStore = useUserStore()
const refreshing = ref(false)
const useLsc = ref(0)

const hybridResult = computed(() =>
  calcHybridPay(
    cartStore.selectedTotalPrice,
    Number(useLsc.value) || 0,
    userStore.availableLsc,
  ),
)

const rmbSupplement = computed(() => hybridResult.value.rmbAmount)

// 选中项变化时校验 LSC 上限
watch(
  () => cartStore.selectedTotalPrice,
  () => {
    if (Number(useLsc.value) > hybridResult.value.maxLsc) {
      useLsc.value = hybridResult.value.maxLsc
    }
  },
)

function formatPrice(n: number): string {
  return round2(n).toFixed(2)
}

function inc(item: CartItem) {
  cartStore.updateQuantity(item.product.id, item.quantity + 1, item.spec)
}

function dec(item: CartItem) {
  if (item.quantity <= 1) {
    cartStore.remove(item.product.id, item.spec)
  } else {
    cartStore.updateQuantity(item.product.id, item.quantity - 1, item.spec)
  }
}

function onRemove(item: CartItem) {
  uni.showModal({
    title: '提示',
    content: `从购物车移除「${item.product.name}」？`,
    success: (res) => {
      if (res.confirm) cartStore.remove(item.product.id, item.spec)
    },
  })
}

function onToggleAll() {
  cartStore.toggleSelectAll(!cartStore.isAllSelected)
}

function onRefresh() {
  refreshing.value = true
  cartStore.restore()
  setTimeout(() => (refreshing.value = false), 500)
}

function goMall() {
  uni.switchTab({ url: '/src/pages/mall/index' })
}

function goDetail(product: any) {
  uni.navigateTo({ url: `/src/pages-product/detail/index?id=${product.id}` })
}

function onCheckout() {
  if (!userStore.isLoggedIn) {
    uni.navigateTo({ url: '/src/pages-account/login/index' })
    return
  }
  if (!cartStore.selectedCount) {
    uni.showToast({ title: '请选择商品', icon: 'none' })
    return
  }
  const items = cartStore.selectedItems.map((it) => ({
    productId: it.product.id,
    quantity: it.quantity,
    spec: it.spec,
  }))
  const lscAmount = hybridResult.value.lscAmount
  uni.navigateTo({
    url: `/src/pages-order/create/index?fromCart=1&lsc=${lscAmount}&items=${encodeURIComponent(JSON.stringify(items))}`,
  })
}

onMounted(() => cartStore.restore())

onShow(() => {
  cartStore.restore()
})
</script>

<style lang="scss" scoped>
.cart {
  min-height: 100vh;
  display: flex;
  flex-direction: column;

  &__list {
    flex: 1;
    padding: $spacing-base;
    box-sizing: border-box;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    margin-bottom: $spacing-base;
    position: relative;
  }

  &__check {
    width: 40rpx;
    height: 40rpx;
    border: 2rpx solid $border-color;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;

    &--active {
      background: $primary;
      border-color: $primary;
    }
  }

  &__check-icon {
    color: #fff;
    font-size: 24rpx;
  }

  &__img {
    width: 160rpx;
    height: 160rpx;
    border-radius: $radius-base;
    background: $bg-gray;
    flex-shrink: 0;
  }

  &__info {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 8rpx;
  }

  &__name {
    font-size: $font-base;
    color: $text-primary;
    line-height: 1.4;
  }

  &__spec {
    font-size: $font-xs;
    color: $text-secondary;
    background: $bg-gray;
    padding: 2rpx 10rpx;
    border-radius: $radius-sm;
    align-self: flex-start;
  }

  &__price-row {
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
  }

  &__qty {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    align-self: flex-end;
  }

  &__qty-btn {
    width: 48rpx;
    height: 48rpx;
    line-height: 44rpx;
    text-align: center;
    border: 1rpx solid $border-color;
    border-radius: $radius-sm;
    font-size: $font-md;
    color: $text-regular;
  }

  &__qty-num {
    min-width: 48rpx;
    text-align: center;
    font-size: $font-base;
  }

  &__del {
    position: absolute;
    right: $spacing-base;
    bottom: $spacing-base;
    font-size: $font-xs;
    color: $danger;
  }

  &__footer {
    flex-wrap: wrap;
    gap: $spacing-sm;
  }

  &__check-all {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__summary {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 6rpx;
    min-width: 0;
  }

  &__summary-row {
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
  }

  &__hybrid {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    flex-wrap: wrap;
  }

  &__lsc-input {
    width: 80rpx;
    border: 1rpx solid $lsc-color;
    border-radius: $radius-sm;
    text-align: center;
    font-size: $font-sm;
    color: $lsc-color;
    height: 44rpx;
  }

  &__btn {
    width: 200rpx;
    flex-shrink: 0;
  }
}
</style>
