<template>
  <view class="settings">
    <view class="settings__group card">
      <view class="settings__item" @click="goProfile">
        <text class="flex-1">个人资料</text>
        <text class="settings__arrow">›</text>
      </view>
      <view class="settings__item" @click="changePassword">
        <text class="flex-1">修改密码</text>
        <text class="settings__arrow">›</text>
      </view>
      <view class="settings__item" @click="goAddress">
        <text class="flex-1">收货地址管理</text>
        <text class="settings__arrow">›</text>
      </view>
      <view class="settings__item" @click="goVerify">
        <text class="flex-1">实名认证</text>
        <text class="settings__arrow">›</text>
      </view>
    </view>

    <view class="settings__group card">
      <view class="settings__item">
        <text class="flex-1">清除缓存</text>
        <text class="settings__cache">{{ cacheSize }}</text>
        <text class="settings__arrow" @click="clearCache">清理</text>
      </view>
      <view class="settings__item">
        <text class="flex-1">消息通知</text>
        <switch :checked="notifyEnabled" color="#FF6B00" @change="onNotifyChange" />
      </view>
    </view>

    <view class="settings__group card">
      <view class="settings__item" @click="goAbout">
        <text class="flex-1">关于我们</text>
        <text class="settings__version">v1.0.0</text>
        <text class="settings__arrow">›</text>
      </view>
      <view class="settings__item" @click="contactService">
        <text class="flex-1">联系客服</text>
        <text class="settings__arrow">›</text>
      </view>
    </view>

    <button class="settings__logout" @click="onLogout">退出登录</button>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()
const cacheSize = ref('0KB')
const notifyEnabled = ref(true)

function goProfile() {
  uni.showToast({ title: '个人资料编辑（待开发）', icon: 'none' })
}

function changePassword() {
  uni.showModal({
    title: '修改密码',
    editable: true,
    placeholderText: '请输入原密码',
    success: () => {
      uni.showToast({ title: '功能开发中', icon: 'none' })
    },
  })
}

function goAddress() {
  uni.navigateTo({ url: '/src/pages-address/list/index' })
}

function goVerify() {
  uni.navigateTo({ url: '/src/pages-account/verify/index' })
}

function calcCache() {
  try {
    const info = uni.getStorageInfoSync()
    const kb = (info.currentSize || 0)
    cacheSize.value = kb < 1024 ? `${kb}KB` : `${(kb / 1024).toFixed(1)}MB`
  } catch (e) {
    cacheSize.value = '0KB'
  }
}

function clearCache() {
  uni.showModal({
    title: '提示',
    content: '确认清理缓存？登录态将保留',
    success: (res) => {
      if (res.confirm) {
        // 保留 token 与用户信息
        const keep = ['LSC_TOKEN', 'LSC_PROFILE']
        uni.getStorageInfoSync().keys.forEach((k) => {
          if (!keep.includes(k)) uni.removeStorageSync(k)
        })
        calcCache()
        uni.showToast({ title: '清理完成', icon: 'success' })
      }
    },
  })
}

function onNotifyChange(e: any) {
  notifyEnabled.value = e.detail.value
}

function goAbout() {
  uni.showModal({
    title: '链生通 LSC',
    content: '版本 v1.0.0\n权益商城 · LSC账户 · 混合支付',
    showCancel: false,
  })
}

function contactService() {
  uni.makePhoneCall({ phoneNumber: '400-000-0000' })
}

function onLogout() {
  uni.showModal({
    title: '提示',
    content: '确定退出登录？',
    success: async (res) => {
      if (res.confirm) {
        await userStore.logout()
        uni.showToast({ title: '已退出', icon: 'success' })
        setTimeout(() => uni.reLaunch({ url: '/src/pages-account/login/index' }), 600)
      }
    },
  })
}

onMounted(calcCache)
</script>

<style lang="scss" scoped>
.settings {
  padding: $spacing-base;
  min-height: 100vh;

  &__group {
    margin-bottom: $spacing-base;
  }

  &__item {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    padding: $spacing-base 0;
    font-size: $font-base;
    border-bottom: 1rpx solid $border-color-light;

    &:last-child {
      border-bottom: none;
    }
  }

  &__arrow {
    color: $text-placeholder;
    font-size: $font-md;
  }

  &__cache {
    color: $text-secondary;
    font-size: $font-sm;
  }

  &__version {
    color: $text-secondary;
    font-size: $font-sm;
  }

  &__logout {
    width: 100%;
    background: #fff;
    color: $danger;
    border: 1rpx solid $danger;
    border-radius: $radius-base;
    height: 88rpx;
    line-height: 88rpx;
    font-size: $font-base;
    margin-top: $spacing-lg;
  }
}
</style>
