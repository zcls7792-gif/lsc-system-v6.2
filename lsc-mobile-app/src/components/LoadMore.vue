<template>
  <view class="load-more" @click="onLoadMore">
    <view v-if="status === 'loading'" class="load-more__loading">
      <view class="load-more__spinner"></view>
      <text class="load-more__text">{{ loadingText }}</text>
    </view>
    <text v-else-if="status === 'noMore'" class="load-more__text">{{ noMoreText }}</text>
    <text v-else-if="status === 'error'" class="load-more__text load-more__text--error">{{ errorText }}（点击重试）</text>
    <text v-else class="load-more__text">{{ loadText }}</text>
  </view>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    /** loadmore | loading | noMore | error */
    status?: 'loadmore' | 'loading' | 'noMore' | 'error'
    loadText?: string
    loadingText?: string
    noMoreText?: string
    errorText?: string
  }>(),
  {
    status: 'loadmore',
    loadText: '上拉加载更多',
    loadingText: '加载中...',
    noMoreText: '— 没有更多了 —',
    errorText: '加载失败',
  },
)

const emit = defineEmits<{ (e: 'loadmore'): void }>()

function onLoadMore() {
  emit('loadmore')
}
</script>

<style lang="scss" scoped>
.load-more {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-base 0;
  gap: $spacing-sm;

  &__loading {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__spinner {
    width: 28rpx;
    height: 28rpx;
    border: 3rpx solid $border-color;
    border-top-color: $primary;
    border-radius: 50%;
    animation: lsc-spin 0.8s linear infinite;
  }

  &__text {
    font-size: $font-sm;
    color: $text-secondary;

    &--error {
      color: $danger;
    }
  }
}

@keyframes lsc-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
