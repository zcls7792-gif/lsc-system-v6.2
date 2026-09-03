<script setup lang="ts">
// 订单管理 — 表格 + 订单状态筛选 + 详情弹窗
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, View, Van } from '@element-plus/icons-vue'
import { getOrders, shipOrder, type OrderListQuery } from '@/api/order'
import type { Order } from '@/api/types'
import type { PageResult } from '@/utils/request'
import { ORDER_STATUS_MAP, ORDER_TYPE_MAP } from '@/utils/maps'
import dayjs from 'dayjs'

const loading = ref(false)
const list = ref<Order[]>([])
const total = ref(0)
const detailVisible = ref(false)
const current = ref<Order | null>(null)
const shipping = ref(false)

const query = reactive<OrderListQuery>({
  page: 1,
  size: 10,
  orderNo: '',
  status: undefined,
  orderType: undefined,
  startDate: '',
  endDate: ''
})
const dateRange = ref<[string, string] | null>(null)

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
    const res: PageResult<Order> = await getOrders({
      page: query.page,
      size: query.size,
      orderNo: query.orderNo,
      status: query.status,
      orderType: query.orderType,
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
  query.orderNo = ''
  query.status = undefined
  query.orderType = undefined
  dateRange.value = null
  query.page = 1
  load()
}

function showDetail(row: Order) {
  current.value = row
  detailVisible.value = true
}

async function ship(row: Order) {
  shipping.value = true
  try {
    await shipOrder(row.orderNo)
    ElMessage.success('已确认履约 / 发货')
    load()
    detailVisible.value = false
  } finally {
    shipping.value = false
  }
}

function fmtMoney(n: number) {
  return Number(n || 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
function fmtDate(d?: string) {
  return d ? dayjs(d).format('YYYY-MM-DD HH:mm:ss') : '-'
}

onMounted(load)
</script>

<template>
  <div class="lsc-page" data-testid="merchant-order-list-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">订单管理</h1>
        <p class="lsc-page-subtitle">管理线上商城与线下消费订单</p>
      </div>
    </div>

    <div class="lsc-card">
      <div class="lsc-card__pad filter">
        <el-input
          v-model="query.orderNo"
          placeholder="订单号"
          clearable
          :prefix-icon="Search"
          class="filter__input"
          @keyup.enter="onSearch"
        />
        <el-select v-model="query.status" placeholder="订单状态" clearable class="filter__select">
          <el-option v-for="(v, k) in ORDER_STATUS_MAP" :key="k" :label="v.label" :value="Number(k)" />
        </el-select>
        <el-select v-model="query.orderType" placeholder="订单类型" clearable class="filter__select">
          <el-option label="线上商城" :value="0" />
          <el-option label="线下消费" :value="1" />
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
      <el-table v-loading="loading" :data="list" row-key="id" stripe data-testid="merchant-order-list-table">
        <el-table-column label="订单号" min-width="200">
          <template #default="{ row }">
            <div class="ord-no lsc-num">{{ row.orderNo }}</div>
            <div class="ord-type">{{ ORDER_TYPE_MAP[row.orderType] }}</div>
          </template>
        </el-table-column>
        <el-table-column label="商品" min-width="200">
          <template #default="{ row }">
            <div class="ord-product">{{ row.productName || (row.orderType === 1 ? '线下消费' : '-') }}</div>
            <div class="ord-qty lsc-num muted">x{{ row.quantity }}</div>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="150" align="right">
          <template #default="{ row }">
            <div class="price">¥<span class="lsc-num">{{ fmtMoney(row.totalPrice) }}</span></div>
            <div class="price-sub lsc-gold-text lsc-num">{{ row.lscAmount }} LSC</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="ORDER_STATUS_MAP[row.status]?.type" effect="light" size="small">
              {{ ORDER_STATUS_MAP[row.status]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="lsc-num muted">{{ fmtDate(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="View" @click="showDetail(row as Order)">详情</el-button>
            <el-button
              v-if="row.status === 1"
              link
              type="success"
              :icon="Van"
              :loading="shipping"
              @click="ship(row as Order)"
            >履约/发货</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无订单" />
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

    <!-- 订单详情弹窗 -->
    <el-drawer v-model="detailVisible" title="订单详情" direction="rtl" size="480px">
      <template v-if="current">
        <div class="detail">
          <div class="detail__status">
            <el-tag :type="ORDER_STATUS_MAP[current.status]?.type" effect="dark" size="large">
              {{ ORDER_STATUS_MAP[current.status]?.label || '未知' }}
            </el-tag>
          </div>

          <div class="detail__section">
            <div class="detail__section-title">订单信息</div>
            <div class="detail__row"><span>订单号</span><span class="lsc-num">{{ current.orderNo }}</span></div>
            <div class="detail__row"><span>类型</span><span>{{ ORDER_TYPE_MAP[current.orderType] }}</span></div>
            <div class="detail__row"><span>商品</span><span>{{ current.productName || '-' }}</span></div>
            <div class="detail__row"><span>数量</span><span class="lsc-num">{{ current.quantity }}</span></div>
            <div class="detail__row"><span>创建时间</span><span class="lsc-num">{{ fmtDate(current.createdAt) }}</span></div>
            <div class="detail__row"><span>支付时间</span><span class="lsc-num">{{ fmtDate(current.payTime) }}</span></div>
            <div class="detail__row"><span>完成时间</span><span class="lsc-num">{{ fmtDate(current.completedAt) }}</span></div>
          </div>

          <div class="detail__section">
            <div class="detail__section-title">金额</div>
            <div class="detail__row"><span>订单总价</span><span class="lsc-num">¥{{ fmtMoney(current.totalPrice) }}</span></div>
            <div class="detail__row"><span>人民币支付</span><span class="lsc-num">¥{{ fmtMoney(current.rmbAmount) }}</span></div>
            <div class="detail__row"><span>LSC 支付</span><span class="lsc-num lsc-gold-text">{{ current.lscAmount }} LSC</span></div>
            <div class="detail__row" v-if="current.refundLscAmount > 0">
              <span>已退 LSC</span><span class="lsc-num">{{ current.refundLscAmount }}</span>
            </div>
            <div class="detail__row" v-if="current.refundRmbAmount > 0">
              <span>已退人民币</span><span class="lsc-num">¥{{ fmtMoney(current.refundRmbAmount) }}</span>
            </div>
          </div>

          <div class="detail__section">
            <div class="detail__section-title">关联方</div>
            <div class="detail__row"><span>消费者ID</span><span class="lsc-num">{{ current.consumerId }}</span></div>
            <div class="detail__row"><span>商家ID</span><span class="lsc-num">{{ current.merchantId }}</span></div>
            <div class="detail__row"><span>商品ID</span><span class="lsc-num">{{ current.productId }}</span></div>
          </div>

          <div v-if="current.status === 1" class="detail__action">
            <el-button type="primary" :icon="Van" :loading="shipping" @click="ship(current)">
              确认履约 / 发货
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.filter {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.filter__input { width: 220px; }
.filter__select { width: 140px; }
.filter__date { width: 240px; }
.filter__btns { margin-left: auto; display: flex; gap: 8px; }

.ord-no { font-size: 13px; color: var(--lsc-text); }
.ord-type { font-size: 11px; color: var(--lsc-text-placeholder); margin-top: 2px; }
.ord-product { font-weight: 600; color: var(--lsc-text); }
.ord-qty { font-size: 11.5px; margin-top: 2px; }
.price { font-weight: 700; color: var(--lsc-text); }
.price-sub { font-size: 11.5px; }
.muted { color: var(--lsc-text-secondary); }
.pager { display: flex; justify-content: flex-end; padding: 14px 16px 16px; }

.detail { padding: 0 20px 24px; }
.detail__status { margin-bottom: 18px; }
.detail__section { margin-bottom: 20px; }
.detail__section-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--lsc-text-secondary);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--lsc-border-soft);
  margin-bottom: 8px;
}
.detail__row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
}
.detail__row span:first-child { color: var(--lsc-text-secondary); }
.detail__row span:last-child { color: var(--lsc-text); font-weight: 500; text-align: right; }
.detail__action { padding-top: 8px; text-align: center; }
</style>
