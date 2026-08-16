<template>
  <view class="promo">
    <!-- 推广概览卡片 -->
    <view class="promo__hero">
      <text class="promo__hero-title">我的推广奖励</text>
      <text class="promo__hero-amount">{{ summary.totalReward || 0 }} LSC</text>
      <view class="promo__hero-stats">
        <view class="promo__stat">
          <text class="promo__stat-num">{{ summary.invitedCount || 0 }}</text>
          <text class="promo__stat-label">累计邀请</text>
        </view>
        <view class="promo__stat">
          <text class="promo__stat-num">{{ summary.activeCount || 0 }}</text>
          <text class="promo__stat-label">活跃用户</text>
        </view>
      </view>
    </view>

    <!-- 我的推荐人 -->
    <view class="promo__referrer card">
      <text class="fw-bold">我的推荐人</text>
      <view class="promo__referrer-info">
        <text class="fs-base">{{ summary.referrerPhone || '无（您是平台直推用户）' }}</text>
        <text v-if="summary.referrerPhone" class="fs-sm text-secondary">推荐您加入链生通</text>
      </view>
    </view>

    <!-- 推广规则说明 -->
    <view class="promo__rules card">
      <text class="fw-bold">推广规则说明</text>
      <view class="promo__rule-list">
        <view class="promo__rule-item">
          <text class="promo__rule-num">1</text>
          <text class="flex-1">将您的邀请码/邀请链接分享给好友，好友通过您的链接注册即成为您的下级。</text>
        </view>
        <view class="promo__rule-item">
          <text class="promo__rule-num">2</text>
          <text class="flex-1">下级用户消费后，您将获得推广奖励 LSC 释放额度（按平台规则比例释放）。</text>
        </view>
        <view class="promo__rule-item">
          <text class="promo__rule-num">3</text>
          <text class="flex-1">推广奖励进入锁定账户，按每日释放规则逐步转入可用余额。</text>
        </view>
        <view class="promo__rule-item">
          <text class="promo__rule-num">4</text>
          <text class="flex-1">奖励 LSC 可用于权益商城消费，与人民币 1:1 等值。</text>
        </view>
      </view>
      <view v-if="summary.rules" class="promo__rule-extra">
        <text class="fs-sm text-secondary">{{ summary.rules }}</text>
      </view>
    </view>

    <!-- 邀请操作 -->
    <view class="promo__share card">
      <text class="fw-bold">邀请好友</text>
      <view class="promo__invite-code">
        <text class="promo__code-label">我的邀请码</text>
        <text class="promo__code">{{ inviteCode }}</text>
        <button class="promo__copy-btn" size="mini" @click="copyCode">复制</button>
      </view>
      <button class="promo__share-btn btn-primary" @click="onShare">分享邀请链接</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onShareAppMessage } from '@dcloudio/uni-app'
import { getPromotionSummary } from '@/api/ledger'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const summary = ref<any>({})

const inviteCode = computed(() => {
  const phone = userStore.profile?.phone || ''
  return phone ? `LSC${phone.slice(-6)}` : 'LSC000000'
})

async function loadSummary() {
  try {
    summary.value = await getPromotionSummary()
  } catch (e) {
    summary.value = {}
  }
}

function copyCode() {
  uni.setClipboardData({
    data: inviteCode.value,
    success: () => uni.showToast({ title: '已复制邀请码', icon: 'success' }),
  })
}

function onShare() {
  // #ifdef H5
  uni.setClipboardData({
    data: `邀请您加入链生通，邀请码：${inviteCode.value}`,
    success: () => uni.showToast({ title: '邀请文案已复制，去分享吧', icon: 'none' }),
  })
  // #endif
  // #ifdef MP-WEIXIN || APP-PLUS
  uni.showToast({ title: '点击右上角分享给好友', icon: 'none' })
  // #endif
}

onShareAppMessage(() => ({
  title: `我在链生通赚取 LSC，邀请你一起！邀请码 ${inviteCode.value}`,
  path: `/src/pages-account/register/index?referrer=${inviteCode.value}`,
}))

onMounted(loadSummary)
</script>

<style lang="scss" scoped>
.promo {
  padding: $spacing-base;
  min-height: 100vh;

  &__hero {
    background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
    border-radius: $radius-lg;
    padding: $spacing-lg $spacing-base;
    color: #fff;
    display: flex;
    flex-direction: column;
    align-items: center;
    margin-bottom: $spacing-base;
  }

  &__hero-title {
    font-size: $font-sm;
    opacity: 0.9;
  }

  &__hero-amount {
    font-size: 64rpx;
    font-weight: 700;
    margin: $spacing-sm 0;
  }

  &__hero-stats {
    display: flex;
    gap: $spacing-xl;
    background: rgba(255, 255, 255, 0.15);
    border-radius: $radius-base;
    padding: $spacing-sm $spacing-lg;
    width: 100%;
    justify-content: center;
  }

  &__stat {
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  &__stat-num {
    font-size: $font-md;
    font-weight: 700;
  }

  &__stat-label {
    font-size: $font-xs;
    opacity: 0.85;
  }

  &__referrer {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    margin-bottom: $spacing-base;
  }

  &__referrer-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4rpx;
  }

  &__rules {
    margin-bottom: $spacing-base;
  }

  &__rule-list {
    margin-top: $spacing-base;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__rule-item {
    display: flex;
    gap: $spacing-base;
    align-items: flex-start;
  }

  &__rule-num {
    width: 40rpx;
    height: 40rpx;
    border-radius: 50%;
    background: $primary;
    color: #fff;
    font-size: $font-xs;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    font-weight: 700;
  }

  &__rule-extra {
    margin-top: $spacing-base;
    padding: $spacing-sm;
    background: $bg-gray;
    border-radius: $radius-base;
  }

  &__share {
  }

  &__invite-code {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    margin: $spacing-base 0;
    padding: $spacing-base;
    background: $primary-bg;
    border-radius: $radius-base;
  }

  &__code-label {
    font-size: $font-sm;
    color: $text-secondary;
  }

  &__code {
    flex: 1;
    font-size: $font-md;
    color: $primary;
    font-weight: 700;
    letter-spacing: 2rpx;
  }

  &__copy-btn {
    background: $primary;
    color: #fff;
    border-radius: $radius-sm;
    font-size: $font-xs;
    height: 48rpx;
    line-height: 48rpx;
    margin: 0;
  }

  &__share-btn {
    width: 100%;
  }
}
</style>
