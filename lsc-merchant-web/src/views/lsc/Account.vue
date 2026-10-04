<script setup lang="ts">
// LSC账户 — V7.7.2 五桶模型：锁定/可用/支付占用/风险冻结/待追偿
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowRight, DataLine } from '@element-plus/icons-vue'
import LscBalanceCard from '@/components/LscBalanceCard.vue'
import { getLscAccount, getLscTransactions } from '@/api/lsc'
import type { LscAccount, LscTransaction } from '@/api/types'
import type { PageResult } from '@/utils/request'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const account = ref<LscAccount | null>(null)
const transactions = ref<LscTransaction[]>([])

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
  RECOVERY_SATISFIED: '追偿冲抵',
}

/** unit 字符串转 LSC 数值（1 LSC = 10000 unit） */
function unitToLsc(unitStr?: string): number {
  return Number(unitStr || '0') / 10000
}

function fmt(n: number) {
  return Number(n || 0).toLocaleString('en-US', { maximumFractionDigits: 4 })
}

function fmtMoney(n: number) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtDate(d: string) {
  return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '-'
}

async function load() {
  loading.value = true
  try {
    const [a, t] = await Promise.all([
      getLscAccount(),
      getLscTransactions({ page: 1, size: 10 }).catch(() => ({ records: [], total: 0 } as PageResult<LscTransaction>))
    ])
    account.value = a
    transactions.value = (t as PageResult<LscTransaction>).records || []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="lsc-page" v-loading="loading">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">LSC 账户</h1>
        <p class="lsc-page-subtitle">V7.7.2 五桶模型：锁定 / 可用 / 支付占用 / 风险冻结 / 待追偿</p>
      </div>
      <el-button type="primary" :icon="DataLine" @click="router.push('/lsc/transactions')">查看流水</el-button>
    </div>

    <div class="account-grid">
      <div class="account-left">
        <LscBalanceCard
          v-if="account"
          :total-locked="unitToLsc(account.lockedUnit)"
          :total-available="unitToLsc(account.availableUnit)"
        />

        <div class="lsc-card bucket-grid">
          <div class="lsc-card__pad">
            <div class="bucket-item">
              <div class="bucket-item__label">总权益</div>
              <div class="bucket-item__value lsc-num">{{ fmt(unitToLsc(account?.totalUnit)) }}</div>
            </div>
            <div class="bucket-divider" />
            <div class="bucket-item">
              <div class="bucket-item__label">支付占用</div>
              <div class="bucket-item__value lsc-num" style="color:#f59e0b">{{ fmt(unitToLsc(account?.reservedUnit)) }}</div>
            </div>
            <div class="bucket-divider" />
            <div class="bucket-item">
              <div class="bucket-item__label">风险冻结</div>
              <div class="bucket-item__value lsc-num" style="color:#ef4444">{{ fmt(unitToLsc(account?.frozenTotalUnit)) }}</div>
            </div>
            <div class="bucket-divider" />
            <div class="bucket-item">
              <div class="bucket-item__label">待追偿</div>
              <div class="bucket-item__value lsc-num" style="color:#6366f1">{{ fmt(unitToLsc(account?.pendingRecoveryUnit)) }}</div>
            </div>
          </div>
        </div>

        <div class="lsc-card rule-card">
          <div class="lsc-card__pad">
            <h3 class="rule-title">权益规则</h3>
            <ul class="rule-list">
              <li>消费赠送权益按日释放，释放率 0.05% ~ 0.10%</li>
              <li>权益有效期 365 天，到期自动作废</li>
              <li>100 unit = 1 分，抵扣需为 100 的整数倍</li>
              <li>单订单最多抵扣 50%，不可与优惠券叠加</li>
              <li>退款返还：原批次未到期恢复原到期日，已过期给 30 天宽限</li>
            </ul>
          </div>
        </div>
      </div>

      <div class="account-right">
        <div class="lsc-card detail-card">
          <div class="lsc-card__pad">
            <div class="detail-head">
              <h3>最近权益流水</h3>
              <span class="detail-sub">按事件序号倒序</span>
            </div>

            <el-table :data="transactions" row-key="eventId" size="small">
              <el-table-column label="类型" width="120">
                <template #default="{ row }">
                  <el-tag size="small">{{ EVENT_TYPE_MAP[row.eventType] || row.eventType }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="业务键" min-width="180">
                <template #default="{ row }">
                  <span class="lsc-num" style="font-size:12px">{{ row.businessKey }}</span>
                </template>
              </el-table-column>
              <el-table-column label="业务日" width="110">
                <template #default="{ row }">
                  <span>{{ row.businessDate }}</span>
                </template>
              </el-table-column>
              <el-table-column label="发生时间" width="150">
                <template #default="{ row }">
                  <span>{{ fmtDate(row.occurredAt) }}</span>
                </template>
              </el-table-column>

              <template #empty>
                <el-empty description="暂无流水记录" :image-size="80" />
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

.bucket-grid .lsc-card__pad {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 22px 16px;
}

.bucket-item {
  flex: 1;
  text-align: center;
}

.bucket-item__label {
  font-size: 12px;
  color: var(--lsc-text-secondary);
  margin-bottom: 6px;
}

.bucket-item__value {
  font-size: 20px;
  font-weight: 700;
  color: var(--lsc-text);
}

.bucket-divider {
  width: 1px;
  height: 36px;
  background: var(--lsc-border-soft);
}

.rule-card .lsc-card__pad {
  padding: 18px 20px;
}

.rule-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 10px;
}

.rule-list {
  margin: 0;
  padding-left: 18px;
  font-size: 12.5px;
  color: var(--lsc-text-secondary);
  line-height: 1.9;
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
