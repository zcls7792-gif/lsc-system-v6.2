<template>
  <view class="order-card" @click="onClick">
    <!-- 订单头 -->
    <view class="order-card__header">
      <text class="order-card__no">订单号: {{ order.orderNo }}</text>
      <text class="order-card__status" :class="statusClass">{{ statusText }}</text>
    </view>

    <!-- 商品列表 -->
    <view class="order-card__items">
      <view
        v-for="(item, idx) in order.items"
        :key="idx"
        class="order-card__item"
      >
        <image class="order-card__item-img" :src="item.productImage || placeholder" mode="aspectFill" />
        <view class="order-card__item-info">
          <text class="order-card__item-name text-ellipsis">{{ item.productName }}</text>
          <text v-if="item.spec" class="order-card__item-spec">{{ item.spec }}</text>
          <view class="order-card__item-bottom">
            <view class="price-row">
              <text class="price-rmb">¥{{ formatPrice(item.price) }}</text>
              <text class="price-lsc fs-sm">{{ Math.floor(item.lscPrice) }} LSC</text>
            </view>
            <text class="order-card__item-qty">x{{ item.quantity }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 金额 -->
    <view class="order-card__footer">
      <view class="order-card__amount">
        <text class="text-secondary fs-sm">共 {{ totalQty }} 件 合计:</text>
        <text class="price-rmb fs-md">¥{{ formatPrice(order.totalAmount) }}</text>
        <text class="order-card__lsc-tag">含 {{ order.lscAmount }} LSC</text>
      </view>
      <view class="order-card__actions" @click.stop>
        <button
          v-if="order.status === 0"
          class="order-card__btn order-card__btn--primary"
          @click="onAction('pay')"
        >立即付款</button>
        <button
          v-if="order.status === 0"
          class="order-card__btn"
          @click="onAction('cancel')"
        >取消</button>
        <button
          v-if="order.status === 1"
          class="order-card__btn"
          @click="onAction('remind')"
        >催发货</button>
        <button
          v-if="order.status === 1"
          class="order-card__btn order-card__btn--primary"
          @click="onAction('confirm')"
        >确认收货</button>
        <button
          v-if="order.status === 1 || order.status === 2"
          class="order-card__btn"
          @click="onAction('refund')"
        >申请退款</button>
        <button
          v-if="order.status === 2 || order.status === 3 || order.status === 4"
          class="order-card__btn"
          @click="onAction('review')"
        >查看详情</button>
      </view>
    </view>

    <!-- 支付倒计时 -->
    <view v-if="order.status === 0 && order.expireTime" class="order-card__countdown">
      <CountDown
        :end-time="order.expireTime"
        @finish="onAction('expire')"
      >
        <template #default="{ text }">
          <text class="fs-sm text-danger">剩余 {{ text }} 自动关闭</text>
        </template>
      </CountDown>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Order, OrderStatus } from '@/api/order'
import CountDown from './CountDown.vue'

const props = defineProps<{ order: Order }>()
const emit = defineEmits<{
  (e: 'click', order: Order): void
  (e: 'action', type: string, order: Order): void
}>()

const placeholder = '/static/placeholder/product.png'

const STATUS_MAP: Record<OrderStatus, { text: string; class: string }> = {
  0: { text: '待支付', class: 'is-warning' },
  1: { text: '已支付', class: 'is-primary' },
  2: { text: '已完成', class: 'is-success' },
  3: { text: '已取消', class: 'is-info' },
  4: { text: '已退款', class: 'is-info' },
  5: { text: '部分退款', class: 'is-info' },
}

const statusText = computed(() => STATUS_MAP[props.order.status]?.text || '')
const statusClass = computed(() => STATUS_MAP[props.order.status]?.class || '')
const totalQty = computed(() => props.order.items.reduce((s, i) => s + i.quantity, 0))

function formatPrice(n: number): string {
  return (Number(n) || 0).toFixed(2)
}

function onClick() {
  emit('click', props.order)
  uni.navigateTo({ url: `/src/pages-order/detail/index?id=${props.order.id}` })
}

function onAction(type: string) {
  emit('action', type, props.order)
}
</script>

<style lang="scss" scoped>
.order-card {
  background: #fff;
  border-radius: $radius-lg;
  padding: $spacing-base;
  margin-bottom: $spacing-base;
  box-shadow: $shadow-base;

  &__header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: $spacing-sm;
    border-bottom: 1rpx solid $border-color-light;
  }

  &__no {
    font-size: $font-sm;
    color: $text-secondary;
  }

  &__status {
    font-size: $font-sm;
    font-weight: 600;
    &.is-warning { color: $warning; }
    &.is-primary { color: $primary; }
    &.is-success { color: $success; }
    &.is-info { color: $info; }
  }

  &__items {
    padding: $spacing-base 0;
  }

  &__item {
    display: flex;
    gap: $spacing-base;
    margin-bottom: $spacing-sm;
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

  &__item-name {
    font-size: $font-base;
    color: $text-primary;
  }

  &__item-spec {
    font-size: $font-sm;
    color: $text-secondary;
  }

  &__item-bottom {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
  }

  &__item-qty {
    font-size: $font-sm;
    color: $text-secondary;
  }

  &__footer {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
    padding-top: $spacing-sm;
    border-top: 1rpx solid $border-color-light;
  }

  &__amount {
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
    justify-content: flex-end;
  }

  &__lsc-tag {
    font-size: $font-xs;
    color: $lsc-color;
    background: $lsc-color-bg;
    padding: 2rpx 10rpx;
    border-radius: $radius-sm;
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: $spacing-sm;
    flex-wrap: wrap;
  }

  &__btn {
    font-size: $font-sm;
    height: 56rpx;
    line-height: 56rpx;
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

  &__countdown {
    margin-top: $spacing-xs;
    text-align: right;
  }
}
</style>
