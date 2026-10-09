<template>
  <div class="page">
    <div class="balance-card">
      <div class="balance-title">我的 LSC 权益</div>
      <div class="balance-total" v-if="account">{{ formatUnit(account.availableUnit) }}</div>
      <div class="balance-sub" v-if="account">可用余额（LSC）</div>
    </div>

    <div class="buckets" v-if="account">
      <div v-for="b in buckets" :key="b.key" class="bucket">
        <div class="bucket-label">{{ b.label }}</div>
        <div class="bucket-value">{{ formatUnit((account as any)[b.key]) }}</div>
      </div>
    </div>

    <div class="section-title">最近流水</div>
    <van-cell-group inset>
      <van-cell
        v-for="e in events" :key="e.eventId"
        :title="e.eventType"
        :label="e.businessKey"
        :value="e.occurredAt"
      />
      <van-empty v-if="events.length === 0" description="暂无流水" />
    </van-cell-group>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
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

onMounted(async () => {
  account.value = await getAccount()
  events.value = await listEvents(20)
})

function formatUnit(u: number) {
  return (u / 10000).toFixed(4)
}
</script>

<style scoped>
.page { padding: 16px; padding-bottom: 50px; }
.balance-card {
  background: linear-gradient(135deg, #1989fa, #4fc08d);
  border-radius: 12px; padding: 32px; color: #fff; text-align: center;
}
.balance-title { font-size: 14px; opacity: 0.85; }
.balance-total { font-size: 36px; font-weight: bold; margin-top: 12px; }
.balance-sub { font-size: 12px; opacity: 0.7; margin-top: 6px; }

.buckets {
  display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px;
  margin-top: 16px;
}
.bucket {
  background: #fff; border-radius: 10px; padding: 16px; text-align: center;
}
.bucket-label { font-size: 11px; color: #969799; }
.bucket-value { font-size: 15px; font-weight: bold; color: #1989fa; margin-top: 6px; }

.section-title { font-size: 16px; font-weight: bold; margin: 24px 0 12px; }
</style>
