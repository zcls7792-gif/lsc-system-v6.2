<script setup lang="ts">
// 退款管理 — 退款申请列表 + 同意/拒绝
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Check, Close } from '@element-plus/icons-vue'
import { getRefundList, agreeRefund, rejectRefund } from '@/api/order'
import type { RefundRequest } from '@/api/types'
import type { PageResult } from '@/utils/request'
import dayjs from 'dayjs'

const loading = ref(false)
const list = ref<RefundRequest[]>([])
const total = ref(0)
const rejectVisible = ref(false)
const rejectForm = reactive({ orderNo: '', reason: '' })

const query = reactive({
  page: 1,
  size: 10,
  status: undefined as number | undefined
})

const REFUND_STATUS_MAP: Record<number, { label: string; type: 'info' | 'success' | 'danger' }> = {
  0: { label: '待处理', type: 'info' },
  1: { label: '已同意', type: 'success' },
  2: { label: '已拒绝', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const res: PageResult<RefundRequest> = await getRefundList({
      page: query.page,
      size: query.size,
      status: query.status
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
  query.page = 1
  load()
}

async function agree(row: RefundRequest) {
  ElMessageBox.confirm(
    `确认同意退款？将退回 ${row.refundLscAmount} LSC / ¥${row.refundRmbAmount.toFixed(2)}`,
    '同意退款',
    { type: 'warning' }
  )
    .then(async () => {
      await agreeRefund(row.orderNo)
      ElMessage.success('退款已同意')
      load()
    })
    .catch(() => {})
}

function openReject(row: RefundRequest) {
  rejectForm.orderNo = row.orderNo
  rejectForm.reason = ''
  rejectVisible.value = true
}

async function confirmReject() {
  if (!rejectForm.reason.trim()) {
    ElMessage.warning('请输入拒绝原因')
    return
  }
  await rejectRefund(rejectForm.orderNo, rejectForm.reason)
  ElMessage.success('退款已拒绝')
  rejectVisible.value = false
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
        <h1 class="lsc-page-title">退款管理</h1>
        <p class="lsc-page-subtitle">处理消费者发起的退款申请</p>
      </div>
    </div>

    <div class="lsc-card">
      <div class="lsc-card__pad filter">
        <el-select v-model="query.status" placeholder="退款状态" clearable class="filter__select">
          <el-option label="待处理" :value="0" />
          <el-option label="已同意" :value="1" />
          <el-option label="已拒绝" :value="2" />
        </el-select>
        <div class="filter__btns">
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </div>
      </div>
    </div>

    <div class="lsc-card" style="margin-top: 16px">
      <el-table v-loading="loading" :data="list" row-key="id" stripe>
        <el-table-column label="订单号" min-width="200">
          <template #default="{ row }">
            <div class="lsc-num">{{ row.orderNo }}</div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="160">
          <template #default="{ row }">
            <span>{{ row.productName || '线下消费' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="退款金额" width="160" align="right">
          <template #default="{ row }">
            <div class="price">¥<span class="lsc-num">{{ fmtMoney(row.refundRmbAmount) }}</span></div>
            <div class="price-sub lsc-gold-text lsc-num">{{ row.refundLscAmount }} LSC</div>
          </template>
        </el-table-column>
        <el-table-column label="退款原因" min-width="200">
          <template #default="{ row }">
            <div class="reason">{{ row.reason }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="REFUND_STATUS_MAP[row.status]?.type" effect="light" size="small">
              {{ REFUND_STATUS_MAP[row.status]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="160">
          <template #default="{ row }">
            <span class="lsc-num muted">{{ fmtDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 0">
              <el-button link type="success" :icon="Check" @click="agree(row as RefundRequest)">同意</el-button>
              <el-button link type="danger" :icon="Close" @click="openReject(row as RefundRequest)">拒绝</el-button>
            </template>
            <span v-else class="muted">{{ fmtDate(row.handledAt) }}</span>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无退款申请" />
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

    <el-dialog v-model="rejectVisible" title="拒绝退款" width="440px">
      <el-form label-position="top">
        <el-form-item label="拒绝原因" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" placeholder="请输入拒绝退款的原因" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认拒绝</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.filter { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.filter__select { width: 160px; }
.filter__btns { margin-left: auto; display: flex; gap: 8px; }
.price { font-weight: 700; color: var(--lsc-text); }
.price-sub { font-size: 11.5px; }
.reason { font-size: 13px; color: var(--lsc-text-regular); line-height: 1.5; }
.muted { color: var(--lsc-text-secondary); }
.pager { display: flex; justify-content: flex-end; padding: 14px 16px 16px; }
</style>
