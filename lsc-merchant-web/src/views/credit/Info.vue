<script setup lang="ts">
// 信用信息 — 信用分、违规记录、处罚状态、核销档位、日核销限额
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Warning, CircleCheck, Medal, Money } from '@element-plus/icons-vue'
import { getMerchantProfile, getMerchantViolations } from '@/api/auth'
import type { MerchantExtension, MerchantViolation } from '@/api/types'
import { PENALTY_STATUS_MAP } from '@/utils/maps'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const info = ref<MerchantExtension | null>(null)
const violations = ref<MerchantViolation[]>([])

const creditScore = computed(() => info.value?.creditScore ?? 100)
const penalty = computed(() => PENALTY_STATUS_MAP[info.value?.penaltyStatus ?? 0])

const creditLevel = computed(() => {
  const s = creditScore.value
  if (s >= 80) return { label: '优秀', color: 'var(--lsc-success)' }
  if (s >= 60) return { label: '良好', color: 'var(--lsc-primary-600)' }
  if (s >= 40) return { label: '一般', color: 'var(--lsc-warning)' }
  if (s >= 20) return { label: '较差', color: 'var(--lsc-danger)' }
  return { label: '极差', color: 'var(--lsc-danger)' }
})

// 信用分进度 (满分 100)
const scorePct = computed(() => Math.max(0, Math.min(100, creditScore.value)))

// 档位说明
const levelRanges = [
  { level: 16, min: 95, label: '16 档' },
  { level: 14, min: 90, label: '14 档' },
  { level: 12, min: 85, label: '12 档' },
  { level: 10, min: 80, label: '10 档' },
  { level: 8, min: 70, label: '8 档' },
  { level: 6, min: 60, label: '6 档' },
  { level: 4, min: 40, label: '4 档' },
  { level: 2, min: 20, label: '2 档' }
]

async function load() {
  loading.value = true
  try {
    const [p, v] = await Promise.all([
      getMerchantProfile(),
      getMerchantViolations({ page: 1, size: 20 }).catch(() => [])
    ])
    info.value = p
    violations.value = v || []
  } finally {
    loading.value = false
  }
}

function fmtDate(d?: string) {
  return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '—'
}

onMounted(load)
</script>

<template>
  <div class="lsc-page" v-loading="loading">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">信用信息</h1>
        <p class="lsc-page-subtitle">信用分决定核销档位与日核销限额 · 违规将影响处罚状态</p>
      </div>
      <el-button type="primary" :icon="Money" @click="router.push('/writeoff/apply')">申请核销</el-button>
    </div>

    <div class="credit-grid">
      <!-- 信用分卡片 -->
      <div class="lsc-card credit-score-card">
        <div class="lsc-card__pad">
          <div class="score-head">
            <el-icon class="score-icon"><Medal /></el-icon>
            <span>信用评分</span>
          </div>

          <div class="score-ring">
            <el-progress
              type="dashboard"
              :percentage="scorePct"
              :width="170"
              :stroke-width="10"
              :color="creditLevel.color"
            >
              <template #default>
                <div class="score-num">
                  <span class="lsc-num" :style="{ color: creditLevel.color }">{{ creditScore }}</span>
                  <span class="score-level">{{ creditLevel.label }}</span>
                </div>
              </template>
            </el-progress>
          </div>

          <div class="penalty-row">
            <span>处罚状态</span>
            <el-tag :type="penalty.type" effect="light">{{ penalty.label }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 核销档位 & 限额 -->
      <div class="lsc-card">
        <div class="lsc-card__pad">
          <h3 class="block-title">核销档位</h3>
          <div class="level-current">
            <div class="level-num lsc-num lsc-gold-text">{{ info?.nhLimitLevel || 0 }}</div>
            <div class="level-label">当前档位</div>
          </div>
          <div class="level-meta">
            <div class="kv"><span>日核销限额</span><span class="lsc-num">{{ info?.dailyNhLimit || 0 }} LSC</span></div>
            <div class="kv"><span>AI 风险评分</span><span class="lsc-num">{{ info?.aiRiskScore ?? '—' }}</span></div>
            <div class="kv"><span>月营业额</span><span class="lsc-num">¥ {{ Number(info?.monthlyRevenue || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span></div>
          </div>

          <el-divider />

          <div class="level-table">
            <div class="level-table__head">信用分档位对照</div>
            <div v-for="l in levelRanges" :key="l.level" class="level-table__row" :class="{ 'is-current': info?.nhLimitLevel === l.level }">
              <span class="lsc-num">{{ l.min }}+ 分</span>
              <span class="lsc-num">{{ l.label }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 违规记录 -->
      <div class="lsc-card violation-card">
        <div class="lsc-card__pad">
          <div class="violation-head">
            <h3 class="block-title no-border">违规记录</h3>
            <el-tag type="info" size="small">{{ violations.length }} 条</el-tag>
          </div>

          <div v-if="violations.length === 0" class="violation-empty">
            <el-icon><CircleCheck /></el-icon>
            <span>暂无违规记录，保持良好经营</span>
          </div>

          <div v-else class="violation-list">
            <div v-for="v in violations" :key="v.id" class="violation-item">
              <div class="violation-item__head">
                <el-icon class="violation-item__icon"><Warning /></el-icon>
                <span class="violation-item__type">{{ v.violationType }}</span>
                <el-tag v-if="v.aiDetected" type="warning" size="small" effect="plain">AI 自动发现</el-tag>
                <span class="violation-item__deduct">-{{ v.creditDeduct }} 分</span>
              </div>
              <div class="violation-item__desc">{{ v.violationDesc }}</div>
              <div class="violation-item__meta">
                <span class="muted">处罚：{{ v.penaltyAction }}</span>
                <span class="muted lsc-num">{{ fmtDate(v.penaltyStart) }} ~ {{ fmtDate(v.penaltyEnd) }}</span>
              </div>
              <div class="violation-item__foot muted">操作人 {{ v.operator }} · {{ fmtDate(v.createdAt) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.credit-grid {
  display: grid;
  grid-template-columns: 360px 1fr 1.2fr;
  gap: 16px;
  align-items: flex-start;
}

.block-title {
  font-size: 15px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--lsc-border-soft);
}
.block-title.no-border { border-bottom: none; padding-bottom: 0; }

/* 信用分卡片 */
.score-head {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--lsc-text-secondary);
  margin-bottom: 18px;
}
.score-icon { color: var(--lsc-gold-500); font-size: 20px; }

.score-ring {
  display: flex;
  justify-content: center;
  margin: 8px 0 16px;
}

.score-num {
  text-align: center;
}
.score-num .lsc-num {
  font-size: 36px;
  font-weight: 700;
  display: block;
}
.score-level {
  font-size: 12px;
  color: var(--lsc-text-secondary);
}

.penalty-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 14px;
  background: var(--lsc-bg-soft);
  border-radius: 8px;
  font-size: 13px;
  color: var(--lsc-text-secondary);
}

/* 档位卡片 */
.level-current {
  text-align: center;
  margin-bottom: 18px;
}
.level-num {
  font-size: 44px;
  font-weight: 800;
  line-height: 1;
}
.level-label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--lsc-text-secondary);
}

.level-meta .kv {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
}
.kv span:first-child { color: var(--lsc-text-secondary); }
.kv span:last-child { color: var(--lsc-text); font-weight: 600; }

.level-table__head {
  font-size: 12px;
  color: var(--lsc-text-placeholder);
  margin-bottom: 8px;
}
.level-table__row {
  display: flex;
  justify-content: space-between;
  padding: 6px 10px;
  font-size: 12.5px;
  color: var(--lsc-text-secondary);
  border-radius: 6px;
}
.level-table__row.is-current {
  background: var(--lsc-gold-50);
  color: var(--lsc-gold-700);
  font-weight: 700;
  border: 1px solid var(--lsc-gold-100);
}

/* 违规 */
.violation-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.violation-empty {
  text-align: center;
  padding: 36px 12px;
  color: var(--lsc-success);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.violation-empty .el-icon { font-size: 36px; }

.violation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 480px;
  overflow-y: auto;
}

.violation-item {
  border: 1px solid var(--lsc-border-soft);
  border-left: 3px solid var(--lsc-warning);
  border-radius: 8px;
  padding: 12px 14px;
  background: var(--lsc-surface-2);
}

.violation-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.violation-item__icon { color: var(--lsc-warning); }
.violation-item__type { font-weight: 700; font-size: 13.5px; }
.violation-item__deduct {
  margin-left: auto;
  color: var(--lsc-danger);
  font-weight: 700;
  font-family: var(--lsc-font-mono);
}

.violation-item__desc {
  font-size: 13px;
  color: var(--lsc-text-regular);
  line-height: 1.6;
}

.violation-item__meta {
  display: flex;
  justify-content: space-between;
  margin-top: 8px;
  font-size: 12px;
}

.violation-item__foot {
  margin-top: 6px;
  font-size: 11.5px;
}
.muted { color: var(--lsc-text-secondary); }

@media (max-width: 1080px) {
  .credit-grid { grid-template-columns: 1fr; }
}
</style>
