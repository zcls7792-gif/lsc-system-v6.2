<script setup lang="ts">
// 核销记录列表
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Refresh, Plus, Money } from '@element-plus/icons-vue'
import { getWriteOffRecords } from '@/api/writeoff'
import type { MerchantNhRecord } from '@/api/types'
import type { PageResult } from '@/utils/request'
import { WRITEOFF_STATUS_MAP } from '@/utils/maps'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const list = ref<MerchantNhRecord[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1,
  size: 10,
  status: undefined as number | undefined,
  startDate: '',
  endDate: ''
})

async function load() {
  loading.value = true
  try {
    if (dateRange.value) {
      query.startDate = dateRange.value[0]
      query.endDate = dateRange.value[1]
    } else {
      query.startDate = ''
      query.endDate = ''
    }
    const res: PageResult<MerchantNhRecord> = await getWriteOffRecords({
      page: query.page,
      size: query.size,
      status: query.status,
      startDate: query.startDate,
      endDate: query.endDate
    })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}
function onReset() {
  query.status = undefined
  dateRange.value = null
  query.page = 1
  load()
}

function fmtMoney(n: number) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtDate(d?: string) {
  return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '-'
}

onMounted(load)
</script>

<template>
  <div class="lsc-page" data-testid="merchant-writeoff-records-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">核销记录</h1>
        <p class="lsc-page-subtitle">查询历史核销申请与划拨结果</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="router.push('/writeoff/apply')">申请核销</el-button>
    </div>

    <div class="lsc-card">
      <div class="lsc-card__pad filter">
        <el-select v-model="query.status" placeholder="核销状态" clearable class="filter__select">
          <el-option v-for="(v, k) in WRITEOFF_STATUS_MAP" :key="k" :label="v.label" :value="Number(k)" />
        </el-select>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="-"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="filter__date"
        />
        <div class="filter__btns">
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </div>
      </div>
    </div>

    <div class="lsc-card" style="margin-top: 16px">
      <el-table v-loading="loading" :data="list" row-key="id" stripe data-testid="merchant-writeoff-records-table">
        <el-table-column label="核销订单号" min-width="200">
          <template #default="{ row }">
            <span class="lsc-num">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="核销 LSC" width="140" align="right">
          <template #default="{ row }">
            <span class="lsc-num lsc-gold-text">{{ row.lscAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column label="可得现金" width="140" align="right">
          <template #default="{ row }">
            <span class="lsc-num">¥ {{ fmtMoney(row.cashAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="核销前/后余额" width="200">
          <template #default="{ row }">
            <div class="lsc-num muted">{{ row.availableBefore }} → {{ row.availableAfter }}</div>
          </template>
        </el-table-column>
        <el-table-column label="监管账户" width="220">
          <template #default="{ row }">
            <div class="lsc-num muted">¥ {{ fmtMoney(row.fundBefore) }} → ¥ {{ fmtMoney(row.fundAfter) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="WRITEOFF_STATUS_MAP[row.status]?.type" effect="light" size="small">
              {{ WRITEOFF_STATUS_MAP[row.status]?.label || '未知' }}
            </el-tag>
            <div v-if="row.failReason" class="fail-reason">{{ row.failReason }}</div>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            <div class="lsc-num muted">{{ fmtDate(row.createdAt) }}</div>
            <div v-if="row.completedAt" class="lsc-num muted">{{ fmtDate(row.completedAt) }}</div>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无核销记录">
            <el-button type="primary" :icon="Money" @click="router.push('/writeoff/apply')">立即核销</el-button>
          </el-empty>
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="load"
          @size-change="load"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.filter__select { width: 140px; }
.filter__date { width: 240px; }
.filter__btns { margin-left: auto; display: flex; gap: 8px; }
.muted { color: var(--lsc-text-secondary); }
.fail-reason { margin-top: 4px; font-size: 11px; color: var(--lsc-danger); line-height: 1.4; max-width: 120px; }
.pager { display: flex; justify-content: flex-end; padding: 14px 16px 16px; }
</style>
