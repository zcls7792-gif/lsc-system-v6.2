<template>
  <view class="lsc-tx">
    <!-- 类型筛选 -->
    <view class="lsc-tx__filter">
      <scroll-view scroll-x :show-scrollbar="false">
        <view class="lsc-tx__filter-list">
          <text
            v-for="t in txTypes"
            :key="t.code"
            class="lsc-tx__filter-item"
            :class="{ 'lsc-tx__filter-item--active': activeType === t.code }"
            @click="changeType(t.code)"
          >{{ t.desc }}</text>
        </view>
      </scroll-view>
    </view>

    <scroll-view
      scroll-y
      class="lsc-tx__scroll"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
      @scrolltolower="loadMore"
    >
      <view class="lsc-tx__list">
        <view v-for="ev in list" :key="ev.eventId" class="lsc-tx__item card">
          <view class="lsc-tx__icon" :class="isInflow(ev.eventType) ? 'lsc-tx__icon--in' : 'lsc-tx__icon--out'">
            <text>{{ isInflow(ev.eventType) ? '↓' : '↑' }}</text>
          </view>
          <view class="lsc-tx__info">
            <view class="lsc-tx__info-top">
              <text class="fw-bold">{{ eventTypeDesc(ev.eventType) }}</text>
              <text class="lsc-tx__seq">#{{ ev.userEventSeq }}</text>
            </view>
            <view class="lsc-tx__info-bottom">
              <text class="fs-sm text-secondary">{{ ev.occurredAt }}</text>
              <text class="fs-sm text-secondary">业务日 {{ ev.businessDate }}</text>
            </view>
            <text class="fs-sm text-secondary text-ellipsis">业务键：{{ ev.businessKey }}</text>
          </view>
        </view>
      </view>

      <LoadMore v-if="list.length" :status="loadStatus" />
      <EmptyState v-else-if="!loading" text="暂无流水记录" icon-text="📊" />
    </scroll-view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getLscEvents, type LscEvent } from '@/api/ledger'
import LoadMore from '@/components/LoadMore.vue'
import EmptyState from '@/components/EmptyState.vue'

const EVENT_TYPES = [
  { code: '', desc: '全部' },
  { code: 'GRANT', desc: '消费赠送' },
  { code: 'DAILY_RELEASE', desc: '每日释放' },
  { code: 'PAY_RESERVE', desc: '支付占用' },
  { code: 'PAY_CAPTURE', desc: '支付核销' },
  { code: 'PAY_RELEASE', desc: '解占用' },
  { code: 'REFUND_RESTORE', desc: '退款返还' },
  { code: 'GRANT_CLAWBACK', desc: '赠送撤回' },
  { code: 'FREEZE', desc: '风险冻结' },
  { code: 'UNFREEZE', desc: '解除冻结' },
  { code: 'EXPIRE', desc: '过期作废' },
  { code: 'RECOVERY_SATISFIED', desc: '追偿冲抵' },
]

const txTypes = ref(EVENT_TYPES)
const activeType = ref('')
const list = ref<LscEvent[]>([])
const page = ref(1)
const size = 20
const loading = ref(false)
const loadStatus = ref<'loadmore' | 'loading' | 'noMore' | 'error'>('loadmore')
const refreshing = ref(false)

const INFLOW_TYPES = new Set(['GRANT', 'DAILY_RELEASE', 'PAY_RELEASE', 'REFUND_RESTORE', 'UNFREEZE'])

function isInflow(type: string): boolean {
  return INFLOW_TYPES.has(type)
}

function eventTypeDesc(type: string): string {
  return EVENT_TYPES.find(t => t.code === type)?.desc || type
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
    const res = await getLscEvents({ page: page.value, size })
    let l = res.records || []
    if (activeType.value) {
      l = l.filter((e: LscEvent) => e.eventType === activeType.value)
    }
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

function changeType(code: string) {
  if (activeType.value === code) return
  activeType.value = code
  loadList(true)
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
.lsc-tx {
  display: flex;
  flex-direction: column;
  height: 100vh;

  &__filter {
    background: #fff;
    padding: $spacing-sm $spacing-base;
    border-bottom: 1rpx solid $border-color-light;
    flex-shrink: 0;
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

  &__scroll {
    flex: 1;
    height: 0;
  }

  &__list {
    padding: $spacing-base;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__item {
    display: flex;
    gap: $spacing-base;
    align-items: flex-start;
  }

  &__icon {
    width: 64rpx;
    height: 64rpx;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 32rpx;
    font-weight: 700;
    flex-shrink: 0;

    &--in {
      background: rgba(7, 193, 96, 0.1);
      color: $success;
    }

    &--out {
      background: rgba(250, 81, 80, 0.1);
      color: $danger;
    }
  }

  &__info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    min-width: 0;
  }

  &__info-top {
    display: flex;
    justify-content: space-between;
    gap: $spacing-sm;
  }

  &__seq {
    font-weight: 600;
    font-size: $font-sm;
    color: $text-secondary;
  }

  &__info-bottom {
    display: flex;
    justify-content: space-between;
    gap: $spacing-sm;
  }
}
</style>
