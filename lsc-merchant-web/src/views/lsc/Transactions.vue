<script setup lang="ts">
// LSC流水 — 类型筛选 + 时间筛选 + 表格
import { onMounted, reactive, ref } from 'vue'
import { Search, Refresh, Download } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getLscTransactions } from '@/api/lsc'
import type { LscTransaction } from '@/api/types'
import type { PageResult } from '@/utils/request'
import { LSC_TX_TYPE_MAP } from '@/utils/maps'
import dayjs from 'dayjs'

const loading = ref(false)
const list = ref<LscTransaction[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1,
  size: 10,
  type: undefined as number | undefined,
  startDate: '',
  endDate: '',
  orderNo: ''
})

const typeOptions = Object.entries(LSC_TX_TYPE_MAP).map(([k, v]) => ({ label: v, value: Number(k) }))

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
    const res: PageResult<LscTransaction> = await getLscTransactions({
      page: query.page,
      size: query.size,
      type: query.type,
      startDate: query.startDate,
      endDate: query.endDate,
      orderNo: query.orderNo
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
  query.type = undefined
  query.orderNo = ''
  dateRange.value = null
  query.page = 1
  load()
}

function exportCsv() {
  ElMessage.info('导出功能：请前往监管账户系统下载')
}

function fmt(n: number) {
  return Number(n || 0).toLocaleString('en-US')
}

function fmtDate(d: string) {
  return dayjs(d).format('YYYY-MM-DD HH:mm:ss')
}

/** 收入为正数显示绿色，支出为负数显示红色 */
function isIncome(t: { type?: number }) {
  // 流入类型对商家而言：每日释放、推广奖励、过期转回、退款退回、商家核销(减少可用但增加现金)
  return [2, 3, 6, 9].includes(Number(t?.type))
}

onMounted(load)
</script>

<template>
  <div class="lsc-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">LSC 流水</h1>
        <p class="lsc-page-subtitle">查询 LSC 账户的资金变动明细</p>
      </div>
      <el-button :icon="Download" plain @click="exportCsv">导出</el-button>
    </div>

    <div class="lsc-card">
      <div class="lsc-card__pad filter">
        <el-select v-model="query.type" placeholder="流水类型" clearable filterable class="filter__select">
          <el-option v-for="o in typeOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
        <el-input v-model="query.orderNo" placeholder="关联订单号" clearable :prefix-icon="Search" class="filter__input" @keyup.enter="onSearch" />
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
      <el-table v-loading="loading" :data="list" row-key="id" stripe>
        <el-table-column label="流水ID" width="120">
          <template #default="{ row }">
            <span class="lsc-num muted">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="130">
          <template #default="{ row }">
            <el-tag effect="light" size="small">{{ LSC_TX_TYPE_MAP[row.type] || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="变动数量" width="140" align="right">
          <template #default="{ row }">
            <span class="amount" :class="{ 'amount--in': isIncome(row), 'amount--out': !isIncome(row) }">
              {{ isIncome(row) ? '+' : '-' }}{{ fmt(row.amount) }} LSC
            </span>
          </template>
        </el-table-column>
        <el-table-column label="变动前可用" width="130" align="right">
          <template #default="{ row }">
            <span class="lsc-num muted">{{ row.beforeAvailable }}</span>
          </template>
        </el-table-column>
        <el-table-column label="变动后可用" width="130" align="right">
          <template #default="{ row }">
            <span class="lsc-num">{{ row.afterAvailable }}</span>
          </template>
        </el-table-column>
        <el-table-column label="关联订单号" min-width="180">
          <template #default="{ row }">
            <span class="lsc-num">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="对手方" width="120">
          <template #default="{ row }">
            <span class="lsc-num muted">{{ row.counterpartyId || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }">
            <span class="muted">{{ row.remark || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">
            <span class="lsc-num muted">{{ fmtDate(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无流水记录" />
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
.filter__select { width: 160px; }
.filter__input { width: 220px; }
.filter__date { width: 240px; }
.filter__btns { margin-left: auto; display: flex; gap: 8px; }

.amount { font-weight: 700; font-size: 13.5px; }
.amount--in { color: var(--lsc-success); }
.amount--out { color: var(--lsc-danger); }
.muted { color: var(--lsc-text-secondary); }
.pager { display: flex; justify-content: flex-end; padding: 14px 16px 16px; }
</style>
