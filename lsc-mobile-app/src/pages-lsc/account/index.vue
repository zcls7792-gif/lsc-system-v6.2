<template>
  <view class="lsc-account">
    <!-- 余额卡片 -->
    <view class="lsc-account__hero">
      <view class="lsc-account__hero-row">
        <view class="lsc-account__hero-col">
          <text class="lsc-account__label">可用余额</text>
          <text class="lsc-account__value">{{ formatNum(account?.available) }}</text>
          <text class="lsc-account__unit">LSC</text>
        </view>
        <view class="lsc-account__hero-col lsc-account__hero-col--right">
          <text class="lsc-account__label">锁定余额</text>
          <text class="lsc-account__value lsc-account__value--locked">{{ formatNum(account?.locked) }}</text>
          <text class="lsc-account__unit">LSC</text>
        </view>
      </view>

      <view class="lsc-account__total">
        <text class="fs-sm" style="opacity: 0.85">总资产 {{ formatNum(account?.total) }} LSC ≈ ¥{{ formatNum(account?.total) }}</text>
      </view>

      <!-- 释放进度条 -->
      <view class="lsc-account__progress">
        <view class="lsc-account__progress-header">
          <text class="fs-sm" style="opacity: 0.9">释放进度</text>
          <text class="fs-sm fw-bold">{{ account?.releaseProgress || 0 }}%</text>
        </view>
        <view class="lsc-account__progress-bar">
          <view class="lsc-account__progress-inner" :style="{ width: (account?.releaseProgress || 0) + '%' }"></view>
        </view>
        <view class="lsc-account__progress-meta">
          <text class="fs-sm" style="opacity: 0.85">已释放 {{ formatNum(account?.released) }}</text>
          <text class="fs-sm" style="opacity: 0.85">待释放 {{ formatNum(account?.pendingRelease) }}</text>
        </view>
      </view>

      <view class="lsc-account__today">
        <text class="fs-sm" style="opacity: 0.9">今日释放</text>
        <text class="fw-bold">+{{ formatNum(account?.todayRelease) }} LSC</text>
      </view>
    </view>

    <!-- 快捷操作 -->
    <view class="lsc-account__actions card">
      <view class="lsc-account__action" @click="goMall">
        <text class="lsc-account__action-icon">🛍️</text>
        <text class="fs-sm">去消费</text>
      </view>
      <view class="lsc-account__action" @click="goTransactions">
        <text class="lsc-account__action-icon">📋</text>
        <text class="fs-sm">流水明细</text>
      </view>
      <view class="lsc-account__action" @click="goPromotion">
        <text class="lsc-account__action-icon">🎁</text>
        <text class="fs-sm">推广奖励</text>
      </view>
      <view class="lsc-account__action" @click="goAi">
        <text class="lsc-account__action-icon">🤖</text>
        <text class="fs-sm">咨询客服</text>
      </view>
    </view>

    <!-- 流水记录列表（类型筛选） -->
    <view class="lsc-account__section card">
      <view class="lsc-account__section-header">
        <text class="fw-bold">最近流水</text>
        <text class="fs-sm text-primary" @click="goTransactions">全部 ›</text>
      </view>

      <scroll-view scroll-x class="lsc-account__filter" :show-scrollbar="false">
        <view class="lsc-account__filter-list">
          <text
            v-for="t in txTypes"
            :key="t.code"
            class="lsc-account__filter-item"
            :class="{ 'lsc-account__filter-item--active': activeType === t.code }"
            @click="changeType(t.code)"
          >{{ t.desc }}</text>
        </view>
      </scroll-view>

      <view class="lsc-account__tx-list">
        <view v-for="tx in list" :key="tx.id" class="lsc-account__tx">
          <view class="lsc-account__tx-info">
            <text class="fw-bold fs-base">{{ tx.typeDesc }}</text>
            <text class="fs-sm text-secondary">{{ tx.createTime }}</text>
            <text v-if="tx.remark" class="fs-sm text-secondary text-ellipsis">{{ tx.remark }}</text>
          </view>
          <view class="lsc-account__tx-amount">
            <text :class="tx.amount >= 0 ? 'text-success' : 'text-danger'" class="fw-bold">
              {{ tx.amount >= 0 ? '+' : '' }}{{ tx.amount }}
            </text>
            <text class="fs-sm text-secondary">余额 {{ tx.balance }}</text>
          </view>
        </view>
      </view>

      <LoadMore v-if="list.length" :status="loadStatus" />
      <EmptyState v-else-if="!loading" text="暂无流水记录" icon-text="📊" />
    </view>

    <view style="height: 40rpx"></view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { getLscAccount, getLscTransactions, getLscTxTypes, type LscAccount, type LscTransaction } from '@/api/ledger'
import { useUserStore } from '@/stores/user'
import LoadMore from '@/components/LoadMore.vue'
import EmptyState from '@/components/EmptyState.vue'

const userStore = useUserStore()
const account = ref<LscAccount | null>(null)
const list = ref<LscTransaction[]>([])
const txTypes = ref<Array<{ code: number; desc: string }>>([{ code: -1, desc: '全部' }])
const activeType = ref(-1)
const page = ref(1)
const size = 10
const loading = ref(false)
const loadStatus = ref<'loadmore' | 'loading' | 'noMore' | 'error'>('loadmore')

function formatNum(n?: number): string {
  return (Number(n) || 0).toLocaleString('zh-CN')
}

async function loadAccount() {
  try {
    account.value = await userStore.fetchLscAccount()
  } catch (e) {
    // ignore
  }
}

async function loadTypes() {
  try {
    const t = await getLscTxTypes()
    txTypes.value = [{ code: -1, desc: '全部' }, ...t]
  } catch (e) {
    // ignore
  }
}

async function loadList(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    list.value = []
    loadStatus.value = 'loadmore'
  }
  loading.value = true
  loadStatus.value = 'loading'
  try {
    const res = await getLscTransactions({
      page: page.value,
      size,
      type: activeType.value,
    })
    const l = res.list || []
    if (reset) list.value = l
    else list.value.push(...l)
    loadStatus.value = l.length < size ? 'noMore' : 'loadmore'
  } catch (e) {
    loadStatus.value = 'error'
  } finally {
    loading.value = false
  }
}

function changeType(code: number) {
  if (activeType.value === code) return
  activeType.value = code
  loadList(true)
}

function goMall() {
  uni.switchTab({ url: '/src/pages/mall/index' })
}
function goTransactions() {
  uni.navigateTo({ url: '/src/pages-lsc/transactions/index' })
}
function goPromotion() {
  uni.navigateTo({ url: '/src/pages-account/promotion/index' })
}
function goAi() {
  uni.navigateTo({ url: '/src/pages-ai/assistant/index' })
}

onMounted(async () => {
  await Promise.all([loadAccount(), loadTypes()])
  await loadList(true)
})

onPullDownRefresh(async () => {
  await Promise.all([loadAccount(), loadList(true)])
  uni.stopPullDownRefresh()
})

onReachBottom(() => {
  if (loadStatus.value !== 'loadmore') return
  page.value++
  loadList(false)
})
</script>

<style lang="scss" scoped>
.lsc-account {
  min-height: 100vh;
  padding-bottom: 40rpx;

  &__hero {
    background: linear-gradient(135deg, $lsc-color 0%, $lsc-color-light 100%);
    color: #fff;
    padding: $spacing-lg $spacing-base;
    margin: $spacing-base;
    border-radius: $radius-lg;
    box-shadow: 0 8rpx 24rpx rgba(108, 92, 231, 0.25);
  }

  &__hero-row {
    display: flex;
    justify-content: space-between;
  }

  &__hero-col {
    display: flex;
    flex-direction: column;
    gap: 4rpx;

    &--right {
      align-items: flex-end;
    }
  }

  &__label {
    font-size: $font-sm;
    opacity: 0.85;
  }

  &__value {
    font-size: 56rpx;
    font-weight: 700;

    &--locked {
      font-size: $font-lg;
      opacity: 0.85;
    }
  }

  &__unit {
    font-size: $font-xs;
    opacity: 0.85;
  }

  &__total {
    margin-top: $spacing-base;
    padding-top: $spacing-base;
    border-top: 1rpx solid rgba(255, 255, 255, 0.2);
  }

  &__progress {
    margin-top: $spacing-base;
  }

  &__progress-header {
    display: flex;
    justify-content: space-between;
    margin-bottom: $spacing-xs;
  }

  &__progress-bar {
    height: 16rpx;
    background: rgba(255, 255, 255, 0.25);
    border-radius: 999rpx;
    overflow: hidden;
  }

  &__progress-inner {
    height: 100%;
    background: linear-gradient(90deg, #FFF, #FFE08A);
    border-radius: 999rpx;
    transition: width 0.3s;
  }

  &__progress-meta {
    display: flex;
    justify-content: space-between;
    margin-top: $spacing-xs;
  }

  &__today {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: $spacing-base;
    padding-top: $spacing-base;
    border-top: 1rpx solid rgba(255, 255, 255, 0.2);
  }

  &__actions {
    margin: $spacing-base;
    display: flex;
  }

  &__action {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-xs;
  }

  &__action-icon {
    font-size: 44rpx;
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

  &__filter {
    white-space: nowrap;
    padding: $spacing-base 0;
  }

  &__filter-list {
    display: inline-flex;
    gap: $spacing-sm;
  }

  &__filter-item {
    font-size: $font-xs;
    padding: 6rpx 16rpx;
    background: $bg-gray;
    color: $text-regular;
    border-radius: 999rpx;
    flex-shrink: 0;

    &--active {
      background: $lsc-color;
      color: #fff;
    }
  }

  &__tx {
    display: flex;
    justify-content: space-between;
    padding: $spacing-base 0;
    border-bottom: 1rpx solid $border-color-light;

    &:last-child {
      border-bottom: none;
    }
  }

  &__tx-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    min-width: 0;
  }

  &__tx-amount {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4rpx;
  }
}
</style>
