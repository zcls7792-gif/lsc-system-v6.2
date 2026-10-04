<template>
  <view class="lsc-account">
    <!-- 余额卡片 -->
    <view class="lsc-account__hero">
      <view class="lsc-account__hero-main">
        <text class="lsc-account__label">可用权益</text>
        <view class="lsc-account__hero-value">
          <text class="lsc-account__value-num">{{ formatLsc(account?.availableUnit) }}</text>
          <text class="lsc-account__unit">LSC</text>
        </view>
        <text class="lsc-account__sub">≈ ¥{{ formatYuan(account?.availableUnit) }}</text>
      </view>

      <!-- 5桶明细 -->
      <view class="lsc-account__buckets">
        <view class="lsc-account__bucket">
          <text class="lsc-account__bucket-label">锁定</text>
          <text class="lsc-account__bucket-val">{{ formatLsc(account?.lockedUnit) }}</text>
        </view>
        <view class="lsc-account__bucket">
          <text class="lsc-account__bucket-label">支付占用</text>
          <text class="lsc-account__bucket-val">{{ formatLsc(account?.reservedUnit) }}</text>
        </view>
        <view class="lsc-account__bucket">
          <text class="lsc-account__bucket-label">风险冻结</text>
          <text class="lsc-account__bucket-val">{{ formatLsc(account?.frozenTotalUnit) }}</text>
        </view>
      </view>

      <view class="lsc-account__total">
        <text>总权益 {{ formatLsc(account?.totalUnit) }} LSC</text>
        <text v-if="hasPendingRecovery" class="lsc-account__pending">待追偿 {{ formatLsc(account?.pendingRecoveryUnit) }}</text>
      </view>
    </view>

    <!-- 规则说明 -->
    <view class="lsc-account__rules card">
      <text class="lsc-account__rules-title">权益规则</text>
      <text class="lsc-account__rules-text">• 消费赠送权益按日释放，释放率 0.05%~0.10%</text>
      <text class="lsc-account__rules-text">• 权益有效期 365 天，到期自动作废</text>
      <text class="lsc-account__rules-text">• 100 unit = 1分，抵扣需为 100 的整数倍</text>
      <text class="lsc-account__rules-text">• 单订单最多抵扣 50%，不可与优惠券叠加</text>
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
      <view class="lsc-account__action" @click="goGrantLots">
        <text class="lsc-account__action-icon">🔓</text>
        <text class="fs-sm">释放批次</text>
      </view>
      <view class="lsc-account__action" @click="goPromotion">
        <text class="lsc-account__action-icon">🎁</text>
        <text class="fs-sm">推荐奖励</text>
      </view>
    </view>

    <!-- 流水记录 -->
    <view class="lsc-account__section card">
      <view class="lsc-account__section-header">
        <text class="fw-bold">最近流水</text>
        <text class="fs-sm text-primary" @click="goTransactions">全部 ›</text>
      </view>

      <view class="lsc-account__tx-list">
        <view v-for="ev in list" :key="ev.eventId" class="lsc-account__tx">
          <view class="lsc-account__tx-info">
            <text class="fw-bold fs-base">{{ eventTypeDesc(ev.eventType) }}</text>
            <text class="fs-sm text-secondary">{{ ev.occurredAt }}</text>
          </view>
          <view class="lsc-account__tx-amount">
            <text class="fs-sm text-secondary">{{ ev.businessKey }}</text>
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
import { ref, computed, onMounted } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { getLscAccount, getLscEvents, type LscAccount, type LscEvent } from '@/api/ledger'
import { useUserStore } from '@/stores/user'
import LoadMore from '@/components/LoadMore.vue'
import EmptyState from '@/components/EmptyState.vue'

const userStore = useUserStore()
const account = ref<LscAccount | null>(null)
const list = ref<LscEvent[]>([])
const page = ref(1)
const size = 10
const loading = ref(false)
const loadStatus = ref<'loadmore' | 'loading' | 'noMore' | 'error'>('loadmore')

const hasPendingRecovery = computed(() => {
  return Number(account.value?.pendingRecoveryUnit || '0') > 0
})

/** unit 字符串转 LSC 显示（1 LSC = 10000 unit） */
function formatLsc(unitStr?: string): string {
  const unit = Number(unitStr || '0')
  return (unit / 10000).toFixed(4)
}

/** unit 字符串转人民币显示（100 unit = 1分） */
function formatYuan(unitStr?: string): string {
  const unit = Number(unitStr || '0')
  return (unit / 100).toFixed(2)
}

const EVENT_TYPE_MAP: Record<string, string> = {
  GRANT: '消费赠送',
  DAILY_RELEASE: '每日释放',
  PAY_RESERVE: '支付占用',
  PAY_CAPTURE: '支付核销',
  PAY_RELEASE: '解占用',
  REFUND_RESTORE: '退款返还',
  GRANT_CLAWBACK: '赠送撤回',
  FREEZE: '风险冻结',
  UNFREEZE: '解除冻结',
  EXPIRE: '过期作废',
  RECOVERY_OFFSET: '追偿冲抵',
}

function eventTypeDesc(type: string): string {
  return EVENT_TYPE_MAP[type] || type
}

async function loadAccount() {
  try {
    account.value = await getLscAccount()
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
    const res = await getLscEvents({ page: page.value, size })
    const l = res.records || []
    if (reset) list.value = l
    else list.value.push(...l)
    loadStatus.value = l.length < size ? 'noMore' : 'loadmore'
  } catch (e) {
    loadStatus.value = 'error'
  } finally {
    loading.value = false
  }
}

function goMall() {
  uni.switchTab({ url: '/src/pages/mall/index' })
}
function goTransactions() {
  uni.navigateTo({ url: '/src/pages-lsc/transactions/index' })
}
function goGrantLots() {
  uni.navigateTo({ url: '/src/pages-lsc/grant-lots/index' })
}
function goPromotion() {
  uni.navigateTo({ url: '/src/pages-account/promotion/index' })
}

onMounted(async () => {
  await loadAccount()
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
    background: linear-gradient(135deg, #6c5ce7 0%, #a29bfe 100%);
    color: #fff;
    padding: 40rpx 32rpx;
    margin: 24rpx;
    border-radius: 24rpx;
    box-shadow: 0 8rpx 24rpx rgba(108, 92, 231, 0.25);
  }

  &__hero-main {
    text-align: center;
    padding-bottom: 24rpx;
  }

  &__label {
    font-size: 24rpx;
    opacity: 0.85;
  }

  &__hero-value {
    display: flex;
    align-items: baseline;
    justify-content: center;
    gap: 8rpx;
    margin: 8rpx 0;
  }

  &__value-num {
    font-size: 64rpx;
    font-weight: 700;
  }

  &__unit {
    font-size: 24rpx;
    opacity: 0.85;
  }

  &__sub {
    font-size: 22rpx;
    opacity: 0.8;
  }

  &__buckets {
    display: flex;
    justify-content: space-around;
    padding: 24rpx 0;
    border-top: 1rpx solid rgba(255, 255, 255, 0.2);
    border-bottom: 1rpx solid rgba(255, 255, 255, 0.2);
  }

  &__bucket {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4rpx;
  }

  &__bucket-label {
    font-size: 22rpx;
    opacity: 0.85;
  }

  &__bucket-val {
    font-size: 28rpx;
    font-weight: 600;
  }

  &__total {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-top: 20rpx;
    font-size: 24rpx;
    opacity: 0.9;
  }

  &__pending {
    color: #ffe08a;
  }

  &__rules {
    margin: 24rpx;
    padding: 24rpx;
  }

  &__rules-title {
    font-size: 28rpx;
    font-weight: 600;
    display: block;
    margin-bottom: 12rpx;
  }

  &__rules-text {
    font-size: 22rpx;
    color: #666;
    line-height: 1.8;
    display: block;
  }

  &__actions {
    margin: 24rpx;
    display: flex;
  }

  &__action {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8rpx;
  }

  &__action-icon {
    font-size: 44rpx;
  }

  &__section {
    margin: 24rpx;
  }

  &__section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 24rpx;
    border-bottom: 1rpx solid #eee;
  }

  &__tx {
    display: flex;
    justify-content: space-between;
    padding: 24rpx 0;
    border-bottom: 1rpx solid #f0f0f0;

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
