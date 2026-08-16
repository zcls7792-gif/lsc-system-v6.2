<template>
  <view class="lsc-bar" :class="{ 'lsc-bar--clickable': clickable }" @click="onClick">
    <view class="lsc-bar__icon">
      <text class="lsc-bar__icon-text">LSC</text>
    </view>
    <view class="lsc-bar__info">
      <view class="lsc-bar__row">
        <text class="lsc-bar__label">可用</text>
        <text class="lsc-bar__value">{{ formatNum(available) }}</text>
      </view>
      <view class="lsc-bar__row">
        <text class="lsc-bar__label">锁定</text>
        <text class="lsc-bar__value lsc-bar__value--locked">{{ formatNum(locked) }}</text>
      </view>
    </view>
    <view v-if="clickable" class="lsc-bar__arrow">
      <text class="lsc-bar__arrow-text">明细 ›</text>
    </view>
  </view>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    available?: number
    locked?: number
    clickable?: boolean
  }>(),
  {
    available: 0,
    locked: 0,
    clickable: false,
  },
)

const emit = defineEmits<{ (e: 'click'): void }>()

function formatNum(n: number): string {
  return (Number(n) || 0).toLocaleString('zh-CN')
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
  background: linear-gradient(135deg, $lsc-color 0%, $lsc-color-light 100%);
  border-radius: $radius-lg;
  padding: $spacing-base $spacing-lg;
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
    margin-right: $spacing-base;
    flex-shrink: 0;
  }

  &__icon-text {
    color: #fff;
    font-weight: 700;
    font-size: $font-sm;
  }

  &__info {
    flex: 1;
    display: flex;
    gap: $spacing-lg;
  }

  &__row {
    display: flex;
    flex-direction: column;
  }

  &__label {
    font-size: $font-xs;
    opacity: 0.85;
  }

  &__value {
    font-size: $font-lg;
    font-weight: 700;
    line-height: 1.2;
    margin-top: 4rpx;

    &--locked {
      font-size: $font-md;
      opacity: 0.85;
    }
  }

  &__arrow {
    &-text {
      font-size: $font-sm;
      opacity: 0.9;
    }
  }
}
</style>
