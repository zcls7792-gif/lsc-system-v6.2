<template>
  <div class="page-container">
    <div class="toolbar">
      <el-input v-model="userId" placeholder="输入用户ID" style="width: 220px" clearable />
      <el-button type="primary" :icon="Search" @click="loadAccount">查询</el-button>
      <el-button type="success" @click="loadEvents">查看流水</el-button>
    </div>

    <el-card v-if="account" shadow="never">
      <template #header>
        <div class="card-header">
          <span>账户余额 - 用户 {{ account.userId }}</span>
          <el-tag type="info">事件序号: {{ account.lastEventSeq }}</el-tag>
        </div>
      </template>
      <el-row :gutter="16">
        <el-col :span="8" v-for="bucket in buckets" :key="bucket.key">
          <el-card shadow="hover" class="bucket-card">
            <div class="bucket-label">{{ bucket.label }}</div>
            <div class="bucket-value" :style="{ color: bucket.color }">
              {{ formatUnit((account as any)[bucket.key]) }}
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <el-card v-if="events.length > 0" shadow="never" style="margin-top: 16px">
      <template #header>最近流水</template>
      <el-table :data="events" border stripe row-key="eventId" size="small">
        <el-table-column prop="eventId" label="事件ID" width="120" />
        <el-table-column prop="eventType" label="事件类型" width="140" />
        <el-table-column prop="businessKey" label="业务键" min-width="160" />
        <el-table-column prop="businessDate" label="业务日" width="120" />
        <el-table-column prop="occurredAt" label="发生时间" width="170" />
      </el-table>
    </el-card>

    <el-empty v-if="!account && !loading" description="请输入用户ID查询账户" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getAccount, listEvents, type Account, type LedgerEvent } from '@/api/modules/account'

const userId = ref('')
const account = ref<Account | null>(null)
const events = ref<LedgerEvent[]>([])
const loading = ref(false)

const buckets = [
  { key: 'lockedUnit', label: '锁定 LOCKED', color: '#909399' },
  { key: 'availableUnit', label: '可用 AVAILABLE', color: '#67C23A' },
  { key: 'reservedUnit', label: '预占 RESERVED', color: '#E6A23C' },
  { key: 'frozenLockedUnit', label: '冻结锁定', color: '#F56C6C' },
  { key: 'frozenAvailableUnit', label: '冻结可用', color: '#F56C6C' }
]

async function loadAccount() {
  if (!userId.value) return
  loading.value = true
  try {
    account.value = await getAccount(Number(userId.value))
    events.value = []
  } finally {
    loading.value = false
  }
}

async function loadEvents() {
  if (!userId.value) return
  try {
    events.value = await listEvents(Number(userId.value), { limit: 50 })
  } catch {}
}

function formatUnit(unit: number) {
  // 1 LSC = 10000 unit
  return (unit / 10000).toFixed(4) + ' LSC'
}
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.bucket-card {
  text-align: center;
  .bucket-label {
    color: #909399;
    font-size: 13px;
    margin-bottom: 8px;
  }
  .bucket-value {
    font-size: 22px;
    font-weight: bold;
  }
}
</style>
