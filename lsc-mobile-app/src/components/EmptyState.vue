<template>
  <view class="empty-state">
    <image
      v-if="image"
      class="empty-state__image"
      :src="image"
      mode="aspectFit"
    />
    <view v-else class="empty-state__icon">
      <text class="empty-state__icon-text">{{ iconText }}</text>
    </view>
    <text class="empty-state__text">{{ text }}</text>
    <button
      v-if="actionText"
      class="empty-state__btn"
      @click="$emit('action')"
    >{{ actionText }}</button>
  </view>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    text?: string
    image?: string
    actionText?: string
    /** 当无 image 时显示的图标文字（emoji 或单字） */
    iconText?: string
  }>(),
  {
    text: '暂无数据',
    image: '',
    actionText: '',
    iconText: '📭',
  },
)

defineEmits<{ (e: 'action'): void }>()
</script>

<style lang="scss" scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl $spacing-base;
}

.empty-state__image {
  width: 240rpx;
  height: 240rpx;
}

.empty-state__icon {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: $bg-gray;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: $spacing-base;
}

.empty-state__icon-text {
  font-size: 80rpx;
}

.empty-state__text {
  color: $text-secondary;
  font-size: $font-base;
  margin-top: $spacing-sm;
}

.empty-state__btn {
  margin-top: $spacing-lg;
  background: linear-gradient(135deg, $primary, $primary-light);
  color: #fff;
  border: none;
  border-radius: 999rpx;
  font-size: $font-sm;
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 $spacing-xl;
}
</style>
