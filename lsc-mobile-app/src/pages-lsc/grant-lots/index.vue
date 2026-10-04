<template>
  <view class="lsc-lots">
    <view class="lsc-lots__summary card">
      <view class="lsc-lots__summary-item">
        <text class="lsc-lots__summary-label">锁定中</text>
        <text class="lsc-lots__summary-value">{{ formatLsc(lockedTotal) }} LSC</text>
      </view>
      <view class="lsc-lots__summary-divider" />
      <view class="lsc-lots__summary-item">
        <text class="lsc-lots__summary-label">已释放</text>
        <text class="lsc-lots__summary-value text-success">{{ formatLsc(releasedTotal) }} LSC</text>
      </view>
      <view class="lsc-lots__summary-divider" />
      <view class="lsc-lots__summary-item">
        <text class="lsc-lots__summary-label">已撤回</text>
        <text class="lsc-lots__summary-value text-danger">{{ formatLsc(revokedTotal) }} LSC</text>
      </view>
    </view>

    <scroll-view
      scroll-y
      class="lsc-lots__scroll"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view class="lsc-lots__list">
        <view v-for="lot in list" :key="lot.grantLotId" class="lsc-lots__item card">
          <view class="lsc-lots__item-header">
            <text class="fw-bold">批次 #{{ lot.grantLotId }}</text>
            <el-tag :type="stateTagType(lot.state)" size="small">{{ stateDesc(lot.state) }}</el-tag>
          </view>
          <view class="lsc-lots__item-body">
            <view class="lsc-lots__row">
              <text class="lsc-lots__label">原始赠送</text>
              <text class="lsc-lots__value">{{ formatLsc(lot.originalGrantUnit) }} LSC</text>
            </view>
            <view class="lsc-lots__row">
              <text class="lsc-lots__label">剩余锁定</text>
              <text class="lsc-lots__value">{{ formatLsc(lot.remainingLockedUnit) }} LSC</text>
            </view>
            <view class="lsc-lots__row">
              <text class="lsc-lots__label">累计释放</text>
              <text class="lsc-lots__value text-success">{{ formatLsc(lot.releasedTotalUnit) }} LSC</text>
            </view>
            <view class="lsc-lots__row">
              <text class="lsc-lots__label">冻结锁定</text>
              <text class="lsc-lots__value text-danger">{{ formatLsc(lot.frozenLockedUnit) }} LSC</text>
            </view>
            <view class="lsc-lots__row">
              <text class="lsc-lots__label">首次释放日</text>
              <text class="lsc-lots__value">{{ lot.firstReleaseDate }}</text>
            </view>
            <view class="lsc-lots__row">
              <text class="lsc-lots__label">赠送日</text>
              <text class="lsc-lots__value">{{ lot.grantBusinessDate }}</text>
            </view>
            <!-- 释放进度条 -->
            <view class="lsc-lots__progress">
              <view class="lsc-lots__progress-bar">
                <view class="lsc-lots__progress-fill" :style="{ width: progressPct(lot) + '%' }"></view>
              </view>
              <text class="lsc-lots__progress-text">{{ progressPct(lot) }}%</text>
            </view>
          </view>
        </view>
      </view>

      <LoadMore v-if="list.length" :status="loadStatus" />
      <EmptyState v-else-if="!loading" text="暂无释放批次" icon-text="🔓" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getGrantLots } from '@/api/ledger'
import LoadMore from '@/components/LoadMore.vue'
import EmptyState from '@/components/EmptyState.vue'

interface GrantLot {
  grantLotId: number
  originalGrantUnit: number
  remainingLockedUnit: number
  releasedTotalUnit: number
  revokedLockedUnit: number
  frozenLockedUnit: number
  grantBusinessDate: string
  firstReleaseDate: string
  state: string
}

const list = ref<GrantLot[]>([])
const page = ref(1)
const size = 10
const loading = ref(false)
const loadStatus = ref<'loadmore' | 'loading' | 'noMore' | 'error'>('loadmore')
const refreshing = ref(false)

const lockedTotal = computed(() => list.value.reduce((s, l) => s + (l.remainingLockedUnit || 0), 0))
const releasedTotal = computed(() => list.value.reduce((s, l) => s + (l.releasedTotalUnit || 0), 0))
const revokedTotal = computed(() => list.value.reduce((s, l) => s + (l.revokedLockedUnit || 0), 0))

function formatLsc(unit: number): string {
  return (Number(unit || 0) / 10000).toFixed(4)
}

function stateDesc(state: string): string {
  const map: Record<string, string> = {
    ACTIVE: '释放中',
    PAUSED: '已暂停',
    RELEASED: '已释放完',
    REVOKED: '已撤回',
  }
  return map[state] || state
}

function stateTagType(state: string): string {
  const map: Record<string, string> = {
    ACTIVE: 'success',
    PAUSED: 'warning',
    RELEASED: 'info',
    REVOKED: 'danger',
  }
  return map[state] || 'info'
}

function progressPct(lot: GrantLot): number {
  if (!lot.originalGrantUnit) return 0
  return Math.round((lot.releasedTotalUnit / lot.originalGrantUnit) * 100)
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
    const res = await getGrantLots({ page: page.value, size })
    const l = res.records || []
    if (reset) list.value = l
    else list.value.push(...l)
    loadStatus.value = l.length < size ? 'noMore' : 'loadmore'
  } catch (e) {
    loadStatus.value = 'error'
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function loadMore() {
  if (loadStatus.value !== 'loadmore') return
  page.value++
  loadList(false)
}

async function onRefresh() {
  refreshing.value = true
  await loadList(true)
}

loadList(true)

onShow(() => {
  if (list.value.length) loadList(true)
})
</script>

<style lang="scss" scoped>
.lsc-lots {
  display: flex;
  flex-direction: column;
  height: 100vh;

  &__summary {
    display: flex;
    align-items: center;
    justify-content: space-around;
    margin: $spacing-base;
    padding: $spacing-base;
  }

  &__summary-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;
  }

  &__summary-label {
    font-size: $font-xs;
    color: $text-secondary;
  }

  &__summary-value {
    font-size: $font-lg;
    font-weight: 700;
    color: $text-primary;
  }

  &__summary-divider {
    width: 1rpx;
    height: 60rpx;
    background: $border-color-light;
  }

  &__scroll {
    flex: 1;
    height: 0;
  }

  &__list {
    padding: 0 $spacing-base $spacing-base;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__item {
    padding: $spacing-base;
  }

  &__item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: $spacing-sm;
    border-bottom: 1rpx solid $border-color-light;
    margin-bottom: $spacing-sm;
  }

  &__item-body {
    display: flex;
    flex-direction: column;
    gap: 12rpx;
  }

  &__row {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  &__label {
    font-size: $font-sm;
    color: $text-secondary;
  }

  &__value {
    font-size: $font-sm;
    font-weight: 600;
    color: $text-primary;
  }

  &__progress {
    display: flex;
    align-items: center;
    gap: 16rpx;
    margin-top: 8rpx;
  }

  &__progress-bar {
    flex: 1;
    height: 12rpx;
    background: $bg-gray;
    border-radius: 999rpx;
    overflow: hidden;
  }

  &__progress-fill {
    height: 100%;
    background: linear-gradient(90deg, $lsc-color, #a29bfe);
    border-radius: 999rpx;
    transition: width 0.3s;
  }

  &__progress-text {
    font-size: $font-xs;
    color: $text-secondary;
    font-weight: 600;
  }
}
</style>
