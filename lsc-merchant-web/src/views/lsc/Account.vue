<script setup lang="ts">
// LSC账户 — 锁定/可用环形图 + 明细列表
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, DataLine } from '@element-plus/icons-vue'
import LscBalanceCard from '@/components/LscBalanceCard.vue'
import { getLscAccount, getLscOverview, getAvailableDetails, type LscOverview } from '@/api/lsc'
import type { AvailableLscDetail, LscAccount } from '@/api/types'
import type { PageResult } from '@/utils/request'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const account = ref<LscAccount | null>(null)
const overview = ref<LscOverview | null>(null)
const details = ref<AvailableLscDetail[]>([])

const DETAIL_STATUS_MAP: Record<number, { label: string; type: 'info' | 'success' | 'warning' | 'danger' }> = {
  1: { label: '有效', type: 'success' },
  2: { label: '过期转回', type: 'info' },
  3: { label: '已使用', type: 'info' },
  4: { label: '已核销', type: 'warning' },
  5: { label: '退款退回', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const [a, o, d] = await Promise.all([
      getLscAccount(),
      getLscOverview(),
      getAvailableDetails({ page: 1, size: 10 }).catch(() => ({ records: [], total: 0 }))
    ])
    account.value = a
    overview.value = o
    details.value = (d as PageResult<AvailableLscDetail>).records || []
  } finally {
    loading.value = false
  }
}

function fmt(n: number) {
  return Number(n || 0).toLocaleString('en-US')
}

function fmtMoney(n: number) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtDate(d: string) {
  return dayjs(d).format('YYYY-MM-DD')
}

onMounted(load)
</script>

<template>
  <div class="lsc-page" v-loading="loading" data-testid="merchant-lsc-account-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">LSC 账户</h1>
        <p class="lsc-page-subtitle">查看锁定 / 可用 LSC 余额分布与可用明细</p>
      </div>
      <el-button type="primary" :icon="DataLine" @click="router.push('/lsc/transactions')">查看流水</el-button>
    </div>

    <div class="account-grid">
      <div class="account-left">
        <LscBalanceCard
          v-if="account"
          :total-locked="account.totalLocked"
          :total-available="account.totalAvailable"
        />

        <div class="lsc-card stat-grid">
          <div class="lsc-card__pad">
            <div class="stat-item">
              <div class="stat-item__label">月营业额</div>
              <div class="stat-item__value lsc-num">¥ {{ fmtMoney(overview?.monthlyRevenue || 0) }}</div>
            </div>
            <div class="stat-divider" />
            <div class="stat-item">
              <div class="stat-item__label">累计已使用 LSC</div>
              <div class="stat-item__value lsc-num">{{ fmt(overview?.totalUsed || 0) }}</div>
            </div>
            <div class="stat-divider" />
            <div class="stat-item">
              <div class="stat-item__label">累计已核销 LSC</div>
              <div class="stat-item__value lsc-num lsc-gold-text">{{ fmt(overview?.totalWrittenOff || 0) }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="account-right">
        <div class="lsc-card detail-card">
          <div class="lsc-card__pad">
            <div class="detail-head">
              <h3>可用 LSC 明细</h3>
              <span class="detail-sub">按过期日排序 · 最近 10 条</span>
            </div>

            <el-table :data="details" row-key="id" size="small" :show-header="true" data-testid="merchant-lsc-details-table">
              <el-table-column label="数量" width="100">
                <template #default="{ row }">
                  <span class="lsc-num lsc-gold-text">{{ row.amount }}</span>
                </template>
              </el-table-column>
              <el-table-column label="来源" min-width="120">
                <template #default="{ row }">
                  <span>{{ row.sourceType }}</span>
                </template>
              </el-table-column>
              <el-table-column label="过期日期" width="120">
                <template #default="{ row }">
                  <span class="lsc-num">{{ fmtDate(row.expireDate) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="DETAIL_STATUS_MAP[row.status]?.type" effect="light" size="small">
                    {{ DETAIL_STATUS_MAP[row.status]?.label || '未知' }}
                  </el-tag>
                </template>
              </el-table-column>

              <template #empty>
                <el-empty description="暂无可用 LSC 明细" :image-size="80" />
              </template>
            </el-table>

            <div class="detail-foot" @click="router.push('/lsc/transactions')">
              <span>查看完整流水</span>
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.account-grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
  align-items: flex-start;
}

.account-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-grid .lsc-card__pad {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 22px 16px;
}

.stat-item {
  flex: 1;
  text-align: center;
}

.stat-item__label {
  font-size: 12px;
  color: var(--lsc-text-secondary);
  margin-bottom: 6px;
}

.stat-item__value {
  font-size: 22px;
  font-weight: 700;
  color: var(--lsc-text);
}

.stat-divider {
  width: 1px;
  height: 36px;
  background: var(--lsc-border-soft);
}

.detail-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.detail-head h3 { font-size: 15px; }
.detail-sub { font-size: 12px; color: var(--lsc-text-placeholder); }

.detail-foot {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 8px;
  color: var(--lsc-primary-600);
  font-size: 13px;
  cursor: pointer;
  border-top: 1px dashed var(--lsc-border);
}
.detail-foot:hover { color: var(--lsc-primary-700); }

@media (max-width: 1080px) {
  .account-grid { grid-template-columns: 1fr; }
}
</style>
