<template>
  <div class="lsc-events-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>权益流水查询</span>
          <el-input v-model="userId" placeholder="用户ID" style="width: 160px" clearable />
          <el-button type="primary" @click="query">查询</el-button>
        </div>
      </template>

      <el-table :data="events" v-loading="loading" border stripe>
        <el-table-column prop="userEventSeq" label="序号" width="80" />
        <el-table-column prop="eventType" label="事件类型" width="160">
          <template #default="{ row }">
            <el-tag :type="eventTypeColor(row.eventType)">{{ eventTypeDesc(row.eventType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="businessKey" label="业务键" min-width="200" show-overflow-tooltip />
        <el-table-column prop="orderId" label="关联订单" width="120" />
        <el-table-column prop="refundId" label="关联退款" width="120" />
        <el-table-column prop="caseId" label="风控案件" width="120" />
        <el-table-column prop="businessDate" label="业务日期" width="120" />
        <el-table-column prop="occurredAt" label="发生时间" width="180" />
        <el-table-column prop="ruleVersion" label="规则版本" width="100" />
      </el-table>

      <el-pagination
        v-if="total > 0"
        style="margin-top: 16px; justify-content: flex-end"
        :current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="onPageChange"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { listEvents, type LscEvent } from '@/api/lsc'

const userId = ref('')
const events = ref<LscEvent[]>([])
const loading = ref(false)
const page = ref(1)
const size = 20
const total = ref(0)

const EVENT_TYPE_MAP: Record<string, { desc: string; color: string }> = {
  GRANT: { desc: '消费赠送', color: 'success' },
  DAILY_RELEASE: { desc: '每日释放', color: 'primary' },
  PAY_RESERVE: { desc: '支付占用', color: 'warning' },
  PAY_CAPTURE: { desc: '支付核销', color: 'danger' },
  PAY_RELEASE: { desc: '解占用', color: 'info' },
  REFUND_RESTORE: { desc: '退款返还', color: 'success' },
  GRANT_CLAWBACK: { desc: '赠送撤回', color: 'danger' },
  FREEZE: { desc: '风险冻结', color: 'danger' },
  UNFREEZE: { desc: '解除冻结', color: 'success' },
  EXPIRE: { desc: '过期作废', color: 'info' },
  RECOVERY_OFFSET: { desc: '追偿冲抵', color: 'warning' },
}

function eventTypeDesc(type: string): string {
  return EVENT_TYPE_MAP[type]?.desc || type
}

function eventTypeColor(type: string): string {
  return EVENT_TYPE_MAP[type]?.color || 'info'
}

async function query() {
  if (!userId.value) return
  loading.value = true
  try {
    const res = await listEvents(Number(userId.value), page.value, size)
    events.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(p: number) {
  page.value = p
  query()
}
</script>

<style scoped>
.lsc-events-page {
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
