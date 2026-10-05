<template>
  <view class="page">
    <view class="profile-card">
      <view class="avatar">👤</view>
      <view class="username">{{ username }}</view>
      <view class="user-sub">链盛通会员</view>
    </view>

    <view class="menu">
      <view class="menu-item" @click="go('/pages/account/index')">
        <text class="menu-icon">💰</text>
        <text class="menu-text">权益中心</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item" @click="go('/pages/order/list')">
        <text class="menu-icon">📦</text>
        <text class="menu-text">我的订单</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item">
        <text class="menu-icon">🎟️</text>
        <text class="menu-text">优惠券</text>
        <text class="menu-arrow">›</text>
      </view>
      <view class="menu-item">
        <text class="menu-icon">⚙️</text>
        <text class="menu-text">设置</text>
        <text class="menu-arrow">›</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const username = ref('LSC 用户')

function go(url: string) {
  uni.navigateTo({ url })
}
</script>

<style scoped>
.page { padding: 0 24rpx 40rpx; }
.profile-card {
  background: linear-gradient(135deg, #1f6feb, #388bfd);
  border-radius: 16rpx; padding: 60rpx 40rpx;
  color: #fff; text-align: center; margin-top: 20rpx;
}
.avatar { font-size: 80rpx; }
.username { font-size: 36rpx; font-weight: bold; margin-top: 16rpx; }
.user-sub { font-size: 24rpx; opacity: 0.8; margin-top: 8rpx; }

.menu {
  background: #fff; border-radius: 16rpx; margin-top: 24rpx;
  overflow: hidden;
}
.menu-item {
  display: flex; align-items: center; padding: 30rpx;
  border-bottom: 1rpx solid #f4f4f5;
}
.menu-item:last-child { border-bottom: none; }
.menu-icon { font-size: 36rpx; margin-right: 20rpx; }
.menu-text { flex: 1; font-size: 28rpx; }
.menu-arrow { color: #c0c4cc; font-size: 36rpx; }
</style>
