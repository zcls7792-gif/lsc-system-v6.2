<template>
  <view class="lsc-bar" :class="{ 'lsc-bar--clickable': clickable }" @click="onClick">
    <view class="lsc-bar__icon">
      <text class="lsc-bar__icon-text">LSC</text>
    </view>
    <view class="lsc-bar__info">
      <view class="lsc-bar__row">
        <text class="lsc-bar__label">可用</text>
        <text class="lsc-bar__value">{{ formatLsc(account?.availableUnit) }}</text>
      </view>
      <view class="lsc-bar__row">
        <text class="lsc-bar__label">锁定</text>
        <text class="lsc-bar__value lsc-bar__value--locked">{{ formatLsc(account?.lockedUnit) }}</text>
      </view>
    </view>
    <view v-if="clickable" class="lsc-bar__arrow">
      <text class="lsc-bar__arrow-text">明细 ›</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { LscAccount } from '@/api/ledger'

const props = withDefaults(
  defineProps<{
    account?: LscAccount | null
    clickable?: boolean
  }>(),
  {
    account: null,
    clickable: false,
  },
)

const emit = defineEmits<{ (e: 'click'): void }>()

/** unit 字符串转 LSC 显示（1 LSC = 10000 unit） */
function formatLsc(unitStr?: string): string {
  const unit = Number(unitStr || '0')
  return (unit / 10000).toFixed(2)
}

function onClick() {
  if (!props.clickable) return
  emit('click')
  uni.navigateTo({ url: '/src/pages-lsc/account/index' })
}
</script>

<style lang="scss" scoped>
.lsc-bar {
  display: flex;
  align-items: center;
  background: linear-gradient(135deg, #6c5ce7 0%, #a29bfe 100%);
  border-radius: 16rpx;
  padding: 24rpx 32rpx;
  color: #fff;
  box-shadow: 0 8rpx 24rpx rgba(108, 92, 231, 0.25);

  &--clickable {
    cursor: pointer;
  }

  &__icon {
    width: 80rpx;
    height: 80rpx;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.2);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-right: 24rpx;
    flex-shrink: 0;
  }

  &__icon-text {
    color: #fff;
    font-weight: 700;
    font-size: 24rpx;
  }

  &__info {
    flex: 1;
    display: flex;
    gap: 48rpx;
  }

  &__row {
    display: flex;
    flex-direction: column;
  }

  &__label {
    font-size: 22rpx;
    opacity: 0.85;
  }

  &__value {
    font-size: 32rpx;
    font-weight: 700;
    line-height: 1.2;
    margin-top: 4rpx;

    &--locked {
      font-size: 28rpx;
      opacity: 0.85;
    }
  }

  &__arrow {
    &-text {
      font-size: 24rpx;
      opacity: 0.9;
    }
  }
}
</style>
