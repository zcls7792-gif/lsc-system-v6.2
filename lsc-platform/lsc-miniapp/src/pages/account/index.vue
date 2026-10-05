<template>
  <view class="page">
    <view class="balance-card">
      <view class="balance-title">我的 LSC 权益</view>
      <view class="balance-total" v-if="account">{{ formatUnit(account.availableUnit) }}</view>
      <view class="balance-sub" v-if="account">可用余额</view>
    </view>

    <view class="buckets" v-if="account">
      <view class="bucket" v-for="b in buckets" :key="b.key">
        <view class="bucket-label">{{ b.label }}</view>
        <view class="bucket-value">{{ formatUnit((account as any)[b.key]) }}</view>
      </view>
    </view>

    <view class="section-title">最近流水</view>
    <view class="event-list">
      <view v-for="e in events" :key="e.eventId" class="event-item">
        <view class="event-type">{{ e.eventType }}</view>
        <view class="event-key">{{ e.businessKey }}</view>
        <view class="event-time">{{ e.occurredAt }}</view>
      </view>
      <view v-if="events.length === 0" class="empty">暂无流水</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { getAccount, listEvents, type Account, type LedgerEvent } from '@/api/account'

const account = ref<Account | null>(null)
const events = ref<LedgerEvent[]>([])

const buckets = [
  { key: 'lockedUnit', label: '锁定' },
  { key: 'availableUnit', label: '可用' },
  { key: 'reservedUnit', label: '预占' },
  { key: 'frozenLockedUnit', label: '冻结锁定' },
  { key: 'frozenAvailableUnit', label: '冻结可用' }
]

onMounted(() => load())
onShow(() => load())

async function load() {
  try {
    account.value = await getAccount()
    events.value = await listEvents(20)
  } catch {}
}

function formatUnit(u: number) {
  return (u / 10000).toFixed(4)
}
</script>

<style scoped>
.page { padding: 24rpx; }
.balance-card {
  background: linear-gradient(135deg, #1f6feb, #388bfd);
  border-radius: 16rpx; padding: 40rpx; color: #fff;
  text-align: center;
}
.balance-title { font-size: 26rpx; opacity: 0.85; }
.balance-total { font-size: 56rpx; font-weight: bold; margin-top: 16rpx; }
.balance-sub { font-size: 24rpx; opacity: 0.7; margin-top: 8rpx; }

.buckets {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 16rpx;
  margin-top: 24rpx;
}
.bucket {
  background: #fff; border-radius: 12rpx; padding: 24rpx; text-align: center;
}
.bucket-label { font-size: 22rpx; color: #909399; }
.bucket-value { font-size: 28rpx; font-weight: bold; color: #1f6feb; margin-top: 8rpx; }

.section-title { font-size: 30rpx; font-weight: bold; margin: 30rpx 0 16rpx; }
.event-list { display: flex; flex-direction: column; gap: 16rpx; }
.event-item { background: #fff; border-radius: 12rpx; padding: 20rpx; }
.event-type { font-weight: bold; font-size: 26rpx; }
.event-key { font-size: 22rpx; color: #606266; margin-top: 6rpx; }
.event-time { font-size: 22rpx; color: #c0c4cc; margin-top: 6rpx; }
.empty { text-align: center; color: #c0c4cc; padding: 60rpx 0; }
</style>
