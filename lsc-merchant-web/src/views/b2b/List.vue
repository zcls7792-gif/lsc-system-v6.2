<script setup lang="ts">
// B2B订单列表 — 发起的 + 接收的 + 待确认操作
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Check, Close } from '@element-plus/icons-vue'
import { getB2BList, confirmB2B, cancelB2B, completeB2B, type B2BListQuery } from '@/api/b2b'
import type { B2BOrder } from '@/api/types'
import type { PageResult } from '@/utils/request'
import { B2B_STATUS_MAP, parseJsonArray } from '@/utils/maps'
import { useMerchantStore } from '@/stores/merchant'
import dayjs from 'dayjs'

const router = useRouter()
const merchant = useMerchantStore()
const loading = ref(false)
const list = ref<B2BOrder[]>([])
const total = ref(0)

const query = reactive<B2BListQuery>({
  page: 1,
  size: 10,
  role: 'all',
  status: undefined,
  orderNo: ''
})

const pendingConfirmCount = computed(() => list.value.filter((o) => o.counterpartyId === merchant.profile?.userId && o.status === 0).length)

async function load() {
  loading.value = true
  try {
    const res: PageResult<B2BOrder> = await getB2BList({
      page: query.page,
      size: query.size,
      role: query.role,
      status: query.status,
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
  query.role = 'all'
  query.status = undefined
  query.orderNo = ''
  query.page = 1
  load()
}

function isInitiator(o: { initiatorId?: number }) {
  return o?.initiatorId === merchant.profile?.userId
}

async function confirm(o: B2BOrder) {
  ElMessageBox.confirm(
    `确认接收 B2B 交易「${o.orderNo}」？确认后等待 LSC 流转`,
    '确认交易',
    { type: 'warning' }
  )
    .then(async () => {
      await confirmB2B(o.orderNo)
      ElMessage.success('已确认')
      load()
    })
    .catch(() => {})
}

async function cancel(o: B2BOrder) {
  ElMessageBox.confirm(`确认取消交易「${o.orderNo}」？`, '取消交易', { type: 'warning' })
    .then(async () => {
      await cancelB2B(o.orderNo)
      ElMessage.success('已取消')
      load()
    })
    .catch(() => {})
}

async function complete(o: B2BOrder) {
  await completeB2B(o.orderNo)
  ElMessage.success('已标记完成')
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
  <div class="lsc-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">B2B 订单</h1>
        <p class="lsc-page-subtitle">
          我发起的 / 接收的 B2B 交易
          <span v-if="pendingConfirmCount" class="badge">{{ pendingConfirmCount }} 笔待确认</span>
        </p>
      </div>
      <el-button type="primary" :icon="Plus" @click="router.push('/b2b/create')">发起 B2B 交易</el-button>
    </div>

    <div class="lsc-card">
      <div class="lsc-card__pad filter">
        <el-radio-group v-model="query.role" @change="onSearch">
          <el-radio-button label="all">全部</el-radio-button>
          <el-radio-button label="initiator">我发起的</el-radio-button>
          <el-radio-button label="counterparty">接收的</el-radio-button>
        </el-radio-group>
        <el-input v-model="query.orderNo" placeholder="B2B订单号" clearable :prefix-icon="Search" class="filter__input" @keyup.enter="onSearch" />
        <el-select v-model="query.status" placeholder="状态" clearable class="filter__select" @change="onSearch">
          <el-option v-for="(v, k) in B2B_STATUS_MAP" :key="k" :label="v.label" :value="Number(k)" />
        </el-select>
        <div class="filter__btns">
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </div>
      </div>
    </div>

    <div class="lsc-card" style="margin-top: 16px">
      <el-table v-loading="loading" :data="list" row-key="id" stripe>
        <el-table-column label="B2B订单号" min-width="200">
          <template #default="{ row }">
            <div class="lsc-num">{{ row.orderNo }}</div>
            <div class="role-tag" :class="{ 'role-initiator': isInitiator(row), 'role-counter': !isInitiator(row) }">
              {{ isInitiator(row) ? '我发起' : '接收' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="交易描述" min-width="220">
          <template #default="{ row }">
            <div class="desc">{{ row.tradeDescription }}</div>
            <div v-if="parseJsonArray(row.tradeEvidenceUrls).length" class="evidence">
              {{ parseJsonArray(row.tradeEvidenceUrls).length }} 张凭证
            </div>
          </template>
        </el-table-column>
        <el-table-column label="金额 / LSC" width="170" align="right">
          <template #default="{ row }">
            <div class="price">¥<span class="lsc-num">{{ fmtMoney(row.totalAmountRmb) }}</span></div>
            <div class="price-sub lsc-gold-text lsc-num">{{ row.lscAmount }} LSC</div>
          </template>
        </el-table-column>
        <el-table-column label="对手方" width="160">
          <template #default="{ row }">
            <div class="lsc-num">{{ isInitiator(row) ? row.counterpartyId : row.initiatorId }}</div>
            <div class="muted">{{ isInitiator(row) ? '接收方' : '发起方' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="B2B_STATUS_MAP[row.status]?.type" effect="light" size="small">
              {{ B2B_STATUS_MAP[row.status]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建/过期" width="180">
          <template #default="{ row }">
            <div class="lsc-num muted">{{ fmtDate(row.createdAt) }}</div>
            <div class="lsc-num muted expire">过期 {{ fmtDate(row.expireAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <!-- 接收方 + 待确认 -->
            <el-button v-if="!isInitiator(row) && row.status === 0" link type="success" :icon="Check" @click="confirm(row as B2BOrder)">确认</el-button>
            <!-- 发起方 + 待确认 -->
            <el-button v-if="isInitiator(row) && row.status === 0" link type="danger" :icon="Close" @click="cancel(row as B2BOrder)">取消</el-button>
            <!-- 已流转 -->
            <el-button v-if="row.status === 2" link type="primary" :icon="Check" @click="complete(row as B2BOrder)">完成</el-button>
            <span v-if="[3,4,5].includes(row.status)" class="muted">—</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无 B2B 订单" />
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
.badge {
  display: inline-block;
  margin-left: 8px;
  padding: 1px 8px;
  background: var(--lsc-gold-100);
  color: var(--lsc-gold-700);
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.filter { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.filter__input { width: 220px; }
.filter__select { width: 140px; }
.filter__btns { margin-left: auto; display: flex; gap: 8px; }

.role-tag {
  display: inline-block;
  margin-top: 4px;
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 4px;
}
.role-initiator { background: var(--lsc-primary-100); color: var(--lsc-primary-700); }
.role-counter { background: var(--lsc-gold-100); color: var(--lsc-gold-700); }

.desc {
  font-size: 13px;
  color: var(--lsc-text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.evidence { margin-top: 4px; font-size: 11px; color: var(--lsc-primary-600); }
.price { font-weight: 700; color: var(--lsc-text); }
.price-sub { font-size: 11.5px; }
.muted { color: var(--lsc-text-secondary); }
.expire { font-size: 11px; }
.pager { display: flex; justify-content: flex-end; padding: 14px 16px 16px; }
</style>
