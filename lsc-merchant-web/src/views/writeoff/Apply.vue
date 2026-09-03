<script setup lang="ts">
// 申请核销 — 输入核销数量、自动计算可得现金(100:87)、显示当日限额
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Money, Tickets } from '@element-plus/icons-vue'
import { getWriteOffQuota, applyWriteOff, calcCash, WRITEOFF_RATE, type WriteOffQuota } from '@/api/writeoff'
import { getLscAccount } from '@/api/lsc'
import type { LscAccount } from '@/api/types'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const quota = ref<WriteOffQuota | null>(null)
const account = ref<LscAccount | null>(null)
const lscAmount = ref<number>(0)

const cashAmount = computed(() => calcCash(lscAmount.value))

const canApply = computed(() => {
  if (!quota.value) return false
  if (lscAmount.value <= 0) return false
  if (lscAmount.value > quota.value.todayRemaining) return false
  if (account.value && lscAmount.value > account.value.totalAvailable) return false
  return true
})

const errorMsg = computed(() => {
  if (!quota.value) return ''
  if (lscAmount.value <= 0) return ''
  if (account.value && lscAmount.value > account.value.totalAvailable) {
    return '核销数量不能超过可用 LSC 余额'
  }
  if (lscAmount.value > quota.value.todayRemaining) {
    return `超过今日剩余可核销限额 ${quota.value.todayRemaining} LSC`
  }
  return ''
})

async function load() {
  loading.value = true
  try {
    const [q, a] = await Promise.all([
      getWriteOffQuota(),
      getLscAccount().catch(() => null)
    ])
    quota.value = q
    account.value = a
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (!canApply.value) return
  ElMessageBox.confirm(
    `确认核销 ${lscAmount.value} LSC，预计可得现金 ¥${cashAmount.value.toFixed(2)}？`,
    '核销确认',
    { type: 'warning' }
  )
    .then(async () => {
      submitting.value = true
      try {
        const res = await applyWriteOff({
          lscAmount: lscAmount.value,
          idempotentKey: `writeoff_${Date.now()}_${Math.floor(Math.random() * 10000)}`
        })
        ElMessage.success('核销申请已提交，处理中')
        lscAmount.value = 0
        await load()
        router.push({ path: '/writeoff/records', query: { orderNo: res.orderNo } })
      } finally {
        submitting.value = false
      }
    })
    .catch(() => {})
}

function fmt(n: number | undefined | null) {
  return Number(n || 0).toLocaleString('en-US')
}

function fmtMoney(n: number | undefined | null) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const levelLabel = computed(() => {
  const lv = quota.value?.nhLimitLevel || 0
  if (lv === 0) return '初始档位'
  return `档位 ${lv}`
})

onMounted(load)
</script>

<template>
  <div class="lsc-page" v-loading="loading" data-testid="merchant-writeoff-apply-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">申请核销</h1>
        <p class="lsc-page-subtitle">将可用 LSC 按 100:87 比例核销为现金，划拨至监管账户</p>
      </div>
      <el-button :icon="ArrowLeft" @click="router.push('/writeoff/records')">核销记录</el-button>
    </div>

    <div class="apply-grid">
      <div class="apply-main">
        <div class="lsc-card">
          <div class="lsc-card__pad">
            <h3 class="block-title">核销信息</h3>

            <div class="apply-input-row">
              <label class="apply-label">核销 LSC 数量</label>
              <el-input-number
                v-model="lscAmount"
                :min="0"
                :max="Math.min(quota?.todayRemaining || 0, account?.totalAvailable || 0)"
                :step="10"
                controls-position="right"
                class="apply-input"
                size="large"
              />
              <div class="apply-hint">
                可用余额 <strong class="lsc-num lsc-gold-text">{{ fmt(account?.totalAvailable) }}</strong> LSC
                · 今日剩余可核销 <strong class="lsc-num">{{ fmt(quota?.todayRemaining) }}</strong> LSC
              </div>
            </div>

            <div class="calc-box">
              <div class="calc-row">
                <span class="calc-label">核销 LSC</span>
                <span class="lsc-num calc-value">{{ fmt(lscAmount) }} LSC</span>
              </div>
              <div class="calc-arrow">÷ 100 × 87</div>
              <div class="calc-row">
                <span class="calc-label">可得现金</span>
                <strong class="lsc-num lsc-gold-text calc-value calc-value--big">¥ {{ fmtMoney(cashAmount) }}</strong>
              </div>
              <div class="calc-rate">当前核销比例：100 LSC = {{ (WRITEOFF_RATE * 100).toFixed(0) }} 元</div>
            </div>

            <el-alert
              v-if="errorMsg"
              :title="errorMsg"
              type="error"
              show-icon
              :closable="false"
            />

            <div class="apply-actions">
              <el-button
                type="primary"
                size="large"
                :icon="Money"
                data-testid="merchant-writeoff-submit-btn"
                :loading="submitting"
                :disabled="!canApply"
                @click="submit"
              >
                确认核销
              </el-button>
              <el-button :icon="Tickets" @click="router.push('/writeoff/records')">查看记录</el-button>
            </div>
          </div>
        </div>
      </div>

      <div class="apply-side">
        <div class="lsc-card">
          <div class="lsc-card__pad">
            <h3 class="block-title">当日限额</h3>
            <div class="quota-row">
              <span>核销档位</span>
              <el-tag type="warning" effect="light">{{ levelLabel }}</el-tag>
            </div>
            <div class="quota-row">
              <span>每日核销限额</span>
              <strong class="lsc-num">{{ fmt(quota?.dailyLimit) }} LSC</strong>
            </div>
            <div class="quota-row">
              <span>今日已核销</span>
              <strong class="lsc-num">{{ fmt(quota?.todayUsed) }} LSC</strong>
            </div>
            <div class="quota-row">
              <span>今日剩余</span>
              <strong class="lsc-num lsc-gold-text">{{ fmt(quota?.todayRemaining) }} LSC</strong>
            </div>

            <el-divider />

            <div class="quota-row">
              <span>监管账户余额</span>
              <strong class="lsc-num">¥ {{ fmtMoney(quota?.regulatoryBalance) }}</strong>
            </div>
            <div class="quota-row">
              <span>最近核销</span>
              <span class="lsc-num">{{ quota?.lastNhDate || '—' }}</span>
            </div>
          </div>
        </div>

        <div class="lsc-card notice-card">
          <div class="lsc-card__pad">
            <h4 class="notice-title">核销说明</h4>
            <ul class="notice-list">
              <li>LSC 核销按 <strong>100:87</strong> 比例兑换为现金</li>
              <li>核销现金将划拨至商家监管账户</li>
              <li>核销档位由信用分动态调整，分越高档位越高</li>
              <li>每日核销受当日限额约束，超额次日可继续</li>
              <li>处罚状态下核销可能受限</li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.apply-grid {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 16px;
  align-items: flex-start;
}

.block-title {
  font-size: 15px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--lsc-border-soft);
}

.apply-input-row {
  margin-bottom: 20px;
}

.apply-label {
  display: block;
  font-size: 13px;
  color: var(--lsc-text-secondary);
  margin-bottom: 8px;
  font-weight: 600;
}

.apply-input {
  width: 100%;
}

.apply-hint {
  margin-top: 10px;
  font-size: 12.5px;
  color: var(--lsc-text-secondary);
}

.calc-box {
  margin: 18px 0;
  padding: 20px 22px;
  background: linear-gradient(135deg, var(--lsc-gold-50), #fff7ed);
  border: 1px solid var(--lsc-gold-100);
  border-radius: var(--lsc-radius-md);
}

.calc-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
}

.calc-label { font-size: 13px; color: var(--lsc-text-secondary); }
.calc-value { font-size: 18px; font-weight: 700; color: var(--lsc-text); }
.calc-value--big { font-size: 26px; }

.calc-arrow {
  text-align: center;
  margin: 8px 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--lsc-gold-700);
}

.calc-rate {
  text-align: center;
  margin-top: 10px;
  font-size: 11.5px;
  color: var(--lsc-text-placeholder);
}

.apply-actions {
  margin-top: 18px;
  display: flex;
  gap: 10px;
}

.quota-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 13px;
}
.quota-row span:first-child { color: var(--lsc-text-secondary); }
.quota-row strong { color: var(--lsc-text); }

.notice-title { font-size: 14px; margin-bottom: 10px; }
.notice-list { margin: 0; padding-left: 18px; font-size: 12.5px; color: var(--lsc-text-secondary); line-height: 1.8; }
.notice-list li strong { color: var(--lsc-gold-700); }

@media (max-width: 1080px) {
  .apply-grid { grid-template-columns: 1fr; }
}
</style>
