<template>
  <view class="refund">
    <view class="refund__tips card">
      <text class="fw-bold">申请退款</text>
      <text class="fs-sm text-secondary">退款金额将原路返回，LSC 部分立即退回账户可用余额。</text>
    </view>

    <view class="refund__form card">
      <view class="refund__row">
        <text class="refund__label">退款原因</text>
        <picker :range="reasons" @change="onReasonChange">
          <view class="refund__picker">
            <text :class="{ 'refund__picker--placeholder': !reason }">{{ reason || '请选择退款原因' }}</text>
            <text class="refund__picker-arrow">›</text>
          </view>
        </picker>
      </view>

      <view class="refund__row">
        <text class="refund__label">退款金额</text>
        <view class="refund__amount">
          <text class="price-rmb fs-md">¥{{ formatPrice(maxAmount) }}</text>
          <text v-if="orderLsc > 0" class="fs-sm text-lsc">含 LSC {{ orderLsc }}</text>
        </view>
      </view>

      <view class="refund__row refund__row--desc">
        <text class="refund__label">问题描述</text>
        <textarea
          class="refund__textarea"
          v-model="description"
          placeholder="补充说明（选填）"
          maxlength="200"
          :auto-height="false"
        />
      </view>
    </view>

    <view v-if="order" class="refund__order card">
      <view v-for="(item, idx) in order.items" :key="idx" class="refund__item">
        <image class="refund__item-img" :src="item.productImage || '/static/placeholder/product.png'" mode="aspectFill" />
        <view class="refund__item-info">
          <text class="text-ellipsis">{{ item.productName }}</text>
          <view class="price-row">
            <text class="price-rmb fs-sm">¥{{ formatPrice(item.price) }}</text>
            <text class="fs-sm text-secondary">x{{ item.quantity }}</text>
          </view>
        </view>
      </view>
    </view>

    <view style="height: 140rpx"></view>

    <view class="refund__footer footer-bar">
      <button class="refund__btn btn-primary" :loading="submitting" @click="onSubmit">提交申请</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getOrderDetail, applyRefund, type Order } from '@/api/order'
import { round2 } from '@/utils/pay'

const order = ref<Order | null>(null)
const reason = ref('')
const description = ref('')
const submitting = ref(false)
const orderId = ref(0)

const reasons = [
  '商品质量问题',
  '商品与描述不符',
  '不想要了/拍错',
  '卖家缺货',
  '物流问题',
  '其他原因',
]

const maxAmount = computed(() => order.value?.totalAmount || 0)
const orderLsc = computed(() => order.value?.lscAmount || 0)

function formatPrice(n: number): string {
  return round2(n).toFixed(2)
}

function onReasonChange(e: any) {
  reason.value = reasons[e.detail.value]
}

async function loadOrder(id: string) {
  try {
    order.value = await getOrderDetail(id)
  } catch (e) {
    // ignore
  }
}

async function onSubmit() {
  if (!reason.value) {
    uni.showToast({ title: '请选择退款原因', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await applyRefund({
      orderId: orderId.value,
      reason: reason.value + (description.value ? `：${description.value}` : ''),
    })
    uni.showToast({ title: '退款申请已提交', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch (e) {
    // ignore
  } finally {
    submitting.value = false
  }
}

onLoad((options) => {
  if (options?.id) {
    orderId.value = Number(options.id)
    loadOrder(options.id)
  }
})
</script>

<style lang="scss" scoped>
.refund {
  padding: $spacing-base;
  min-height: 100vh;

  &__tips {
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;
    margin-bottom: $spacing-base;
  }

  &__form {
    margin-bottom: $spacing-base;
  }

  &__row {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    padding: $spacing-base 0;
    border-bottom: 1rpx solid $border-color-light;

    &--desc {
      align-items: flex-start;
      flex-direction: column;
      gap: $spacing-sm;
    }

    &:last-child {
      border-bottom: none;
    }
  }

  &__label {
    width: 160rpx;
    font-size: $font-base;
    color: $text-regular;
    flex-shrink: 0;
  }

  &__picker {
    flex: 1;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: $font-base;

    &--placeholder {
      color: $text-placeholder;
    }
  }

  &__picker-arrow {
    color: $text-placeholder;
  }

  &__amount {
    flex: 1;
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
    justify-content: flex-end;
  }

  &__textarea {
    width: 100%;
    height: 160rpx;
    background: $bg-gray;
    border-radius: $radius-base;
    padding: $spacing-sm;
    font-size: $font-sm;
    box-sizing: border-box;
  }

  &__order {
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
    width: 100rpx;
    height: 100rpx;
    border-radius: $radius-base;
    background: $bg-gray;
    flex-shrink: 0;
  }

  &__item-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 4rpx;
    min-width: 0;
  }

  &__btn {
    width: 100%;
  }
}
</style>
