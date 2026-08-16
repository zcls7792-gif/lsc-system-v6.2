<template>
  <view class="mine">
    <!-- 用户信息卡片 -->
    <view class="mine__header">
      <view class="mine__user" @click="onUserClick">
        <image
          class="mine__avatar"
          :src="profile?.avatar || '/static/placeholder/avatar.png'"
          mode="aspectFill"
        />
        <view class="mine__user-info">
          <template v-if="isLoggedIn">
            <text class="mine__nickname">{{ profile?.nickname || '链生通用户' }}</text>
            <text class="mine__phone">{{ maskedPhone || '未绑定手机号' }}</text>
          </template>
          <template v-else>
            <text class="mine__nickname">点击登录 / 注册</text>
            <text class="mine__phone">登录后享受更多权益</text>
          </template>
        </view>
        <view v-if="isLoggedIn" class="mine__verify-tag" :class="verifyTagClass" @click.stop="goVerify">
          {{ verifyTagText }}
        </view>
      </view>

      <!-- LSC 余额展示 -->
      <LscBalanceBar
        v-if="isLoggedIn"
        :available="userStore.availableLsc"
        :locked="lscLocked"
        clickable
        @click="goLscAccount"
      />

      <!-- 未实名认证引导 -->
      <view v-if="isLoggedIn && !userStore.isVerified" class="mine__verify-banner" @click="goVerify">
        <view class="mine__verify-left">
          <text class="mine__verify-icon">🛡️</text>
          <view>
            <text class="fw-bold fs-sm">完成实名认证</text>
            <text class="fs-sm text-secondary">认证后可使用 LSC 支付、提现等全部功能</text>
          </view>
        </view>
        <text class="mine__verify-go">去认证 ›</text>
      </view>
    </view>

    <!-- 我的订单 -->
    <view class="mine__section card">
      <view class="mine__section-header" @click="goOrderList(-1)">
        <text class="fw-bold">我的订单</text>
        <text class="mine__more">全部订单 ›</text>
      </view>
      <view class="mine__order-grid">
        <view class="mine__order-entry" @click="goOrderList(0)">
          <text class="mine__order-icon">💰</text>
          <text class="mine__order-text">待支付</text>
        </view>
        <view class="mine__order-entry" @click="goOrderList(1)">
          <text class="mine__order-icon">📦</text>
          <text class="mine__order-text">已支付</text>
        </view>
        <view class="mine__order-entry" @click="goOrderList(2)">
          <text class="mine__order-icon">✅</text>
          <text class="mine__order-text">已完成</text>
        </view>
        <view class="mine__order-entry" @click="goOrderList(4)">
          <text class="mine__order-icon">↩️</text>
          <text class="mine__order-text">退款</text>
        </view>
      </view>
    </view>

    <!-- 功能入口 -->
    <view class="mine__section card">
      <view class="mine__menu">
        <view class="mine__menu-item" @click="goLscTransactions">
          <text class="mine__menu-icon">📋</text>
          <text class="mine__menu-name flex-1">LSC 流水</text>
          <text class="mine__menu-arrow">›</text>
        </view>
        <view class="mine__menu-item" @click="goAddress">
          <text class="mine__menu-icon">📍</text>
          <text class="mine__menu-name flex-1">地址管理</text>
          <text class="mine__menu-arrow">›</text>
        </view>
        <view class="mine__menu-item" @click="goPromotion">
          <text class="mine__menu-icon">🎁</text>
          <text class="mine__menu-name flex-1">推广奖励</text>
          <text class="mine__menu-arrow">›</text>
        </view>
        <view class="mine__menu-item" @click="goStoreMap">
          <text class="mine__menu-icon">🗺️</text>
          <text class="mine__menu-name flex-1">商家门店</text>
          <text class="mine__menu-arrow">›</text>
        </view>
        <view class="mine__menu-item" @click="goAiAssistant">
          <text class="mine__menu-icon">🤖</text>
          <text class="mine__menu-name flex-1">AI 客服助手</text>
          <text class="mine__menu-arrow">›</text>
        </view>
        <view class="mine__menu-item" @click="contactService">
          <text class="mine__menu-icon">🎧</text>
          <text class="mine__menu-name flex-1">联系客服</text>
          <text class="mine__menu-arrow">›</text>
        </view>
        <view class="mine__menu-item" @click="goSettings">
          <text class="mine__menu-icon">⚙️</text>
          <text class="mine__menu-name flex-1">设置</text>
          <text class="mine__menu-arrow">›</text>
        </view>
      </view>
    </view>

    <view style="height: 40rpx"></view>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { useUserStore } from '@/stores/user'
import LscBalanceBar from '@/components/LscBalanceBar.vue'
import { AppConfig } from '@/config'

const userStore = useUserStore()

const isLoggedIn = computed(() => userStore.isLoggedIn)
const profile = computed(() => userStore.profile)
const lscLocked = computed(() => userStore.lscAccount?.locked ?? 0)

const maskedPhone = computed(() => {
  const p = profile.value?.phone
  if (!p) return ''
  return p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
})

const verifyTagText = computed(() => {
  switch (profile.value?.verifyStatus) {
    case 1:
      return '已实名'
    case 2:
      return '认证中'
    case 3:
      return '认证驳回'
    default:
      return '未认证'
  }
})

const verifyTagClass = computed(() => {
  switch (profile.value?.verifyStatus) {
    case 1:
      return 'mine__verify-tag--ok'
    case 2:
      return 'mine__verify-tag--pending'
    case 3:
      return 'mine__verify-tag--reject'
    default:
      return 'mine__verify-tag--warn'
  }
})

function onUserClick() {
  if (!isLoggedIn.value) {
    uni.navigateTo({ url: '/src/pages-account/login/index' })
  }
}

function goVerify() {
  if (!isLoggedIn.value) {
    uni.navigateTo({ url: '/src/pages-account/login/index' })
    return
  }
  uni.navigateTo({ url: '/src/pages-account/verify/index' })
}

function goLscAccount() {
  uni.navigateTo({ url: '/src/pages-lsc/account/index' })
}

function goLscTransactions() {
  ensureLogin('/src/pages-lsc/transactions/index')
}

function goAddress() {
  ensureLogin('/src/pages-address/list/index')
}

function goPromotion() {
  ensureLogin('/src/pages-account/promotion/index')
}

function goStoreMap() {
  uni.navigateTo({ url: '/src/pages-store/map/index' })
}

function goAiAssistant() {
  uni.navigateTo({ url: '/src/pages-ai/assistant/index' })
}

function goSettings() {
  ensureLogin('/src/pages-account/settings/index')
}

function goOrderList(status: number) {
  if (!isLoggedIn.value) {
    uni.navigateTo({ url: '/src/pages-account/login/index' })
    return
  }
  uni.navigateTo({ url: `/src/pages-order/list/index?status=${status}` })
}

function contactService() {
  uni.showActionSheet({
    itemList: [`复制客服微信: ${AppConfig.serviceWechat}`, '拨打客服电话'],
    success: (res) => {
      if (res.tapIndex === 0) {
        uni.setClipboardData({ data: AppConfig.serviceWechat })
      } else if (res.tapIndex === 1) {
        uni.makePhoneCall({ phoneNumber: '400-000-0000' })
      }
    },
  })
}

function ensureLogin(redirect: string) {
  if (!isLoggedIn.value) {
    uni.navigateTo({ url: `/src/pages-account/login/index?redirect=${encodeURIComponent(redirect)}` })
    return
  }
  uni.navigateTo({ url: redirect })
}

async function refresh() {
  if (!isLoggedIn.value) return
  try {
    await Promise.all([userStore.fetchProfile(), userStore.fetchLscAccount()])
  } catch (e) {
    // ignore
  }
}

onMounted(refresh)
onShow(refresh)
onPullDownRefresh(async () => {
  await refresh()
  uni.stopPullDownRefresh()
})
</script>

<style lang="scss" scoped>
.mine {
  padding-bottom: 40rpx;

  &__header {
    background: linear-gradient(135deg, $primary 0%, $primary-light 100%);
    padding: $spacing-lg $spacing-base $spacing-xl;
    color: #fff;
  }

  &__user {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    padding: $spacing-base 0;
  }

  &__avatar {
    width: 120rpx;
    height: 120rpx;
    border-radius: 50%;
    border: 4rpx solid rgba(255, 255, 255, 0.4);
    background: rgba(255, 255, 255, 0.2);
  }

  &__user-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 8rpx;
  }

  &__nickname {
    font-size: $font-lg;
    font-weight: 600;
    color: #fff;
  }

  &__phone {
    font-size: $font-sm;
    color: rgba(255, 255, 255, 0.85);
  }

  &__verify-tag {
    font-size: $font-xs;
    padding: 4rpx 14rpx;
    border-radius: 999rpx;
    background: rgba(255, 255, 255, 0.25);
    color: #fff;

    &--ok { background: rgba(7, 193, 96, 0.9); }
    &--pending { background: rgba(255, 176, 32, 0.9); }
    &--reject { background: rgba(250, 81, 80, 0.9); }
    &--warn { background: rgba(255, 255, 255, 0.3); }
  }

  &__verify-banner {
    margin-top: $spacing-base;
    background: rgba(255, 255, 255, 0.95);
    border-radius: $radius-base;
    padding: $spacing-base;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  &__verify-left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__verify-icon {
    font-size: 44rpx;
  }

  &__verify-go {
    color: $primary;
    font-size: $font-sm;
    font-weight: 600;
  }

  &__section {
    margin: $spacing-base;
  }

  &__section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: $spacing-base;
    border-bottom: 1rpx solid $border-color-light;
  }

  &__more {
    font-size: $font-sm;
    color: $text-secondary;
  }

  &__order-grid {
    display: flex;
    padding: $spacing-base 0;
  }

  &__order-entry {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-xs;
  }

  &__order-icon {
    font-size: 48rpx;
  }

  &__order-text {
    font-size: $font-xs;
    color: $text-regular;
  }

  &__menu-item {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    padding: $spacing-base 0;
    border-bottom: 1rpx solid $border-color-light;

    &:last-child {
      border-bottom: none;
    }
  }

  &__menu-icon {
    font-size: 40rpx;
    width: 56rpx;
    text-align: center;
  }

  &__menu-name {
    font-size: $font-base;
    color: $text-primary;
  }

  &__menu-arrow {
    color: $text-placeholder;
    font-size: $font-md;
  }
}
</style>
