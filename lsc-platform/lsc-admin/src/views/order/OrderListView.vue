<template>
  <div class="page-container">
    <div class="toolbar">
      <div class="search-bar">
        <el-input v-model="keyword" placeholder="订单号/用户ID" style="width: 220px" clearable />
        <el-select v-model="status" placeholder="订单状态" style="width: 140px" clearable @change="loadData">
          <el-option label="待支付" value="PENDING" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELED" />
          <el-option label="已退款" value="REFUNDED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      </div>
    </div>

    <el-table :data="orders" v-loading="loading" border stripe row-key="orderId">
      <el-table-column prop="orderId" label="订单号" width="160" />
      <el-table-column prop="userId" label="用户ID" width="100" />
      <el-table-column prop="buyerType" label="买家类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.buyerType === 'C' ? 'primary' : 'success'">{{ row.buyerType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="商品金额(分)" width="120">
        <template #default="{ row }">{{ formatCent(row.goodsCent) }}</template>
      </el-table-column>
      <el-table-column label="优惠(分)" width="100">
        <template #default="{ row }">{{ formatCent(row.couponCent + row.lscUnit) }}</template>
      </el-table-column>
      <el-table-column label="实付(分)" width="120">
        <template #default="{ row }">{{ formatCent(row.rmbCent) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openDetail(row)">详情</el-button>
          <el-button v-if="row.status === 'PENDING'" size="small" type="danger" @click="handleCancel(row)">取消</el-button>
          <el-button v-if="row.status === 'PAID' || row.status === 'COMPLETED'" size="small" type="warning" @click="openRefund(row)">退款</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && orders.length === 0" description="暂无订单数据" />

    <!-- 订单详情弹窗 -->
    <el-dialog v-model="showDetail" title="订单详情" width="600px">
      <el-descriptions v-if="current" :column="2" border>
        <el-descriptions-item label="订单号">{{ current.orderId }}</el-descriptions-item>
        <el-descriptions-item label="用户ID">{{ current.userId }}</el-descriptions-item>
        <el-descriptions-item label="买家类型">{{ current.buyerType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusTagType(current.status)">{{ statusText(current.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商品金额">{{ formatCent(current.goodsCent) }}</el-descriptions-item>
        <el-descriptions-item label="优惠券">{{ formatCent(current.couponCent) }}</el-descriptions-item>
        <el-descriptions-item label="LSC抵扣">{{ formatCent(current.lscUnit) }}</el-descriptions-item>
        <el-descriptions-item label="实付金额">{{ formatCent(current.rmbCent) }}</el-descriptions-item>
        <el-descriptions-item label="优惠方式">{{ current.discountMode }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ current.createdAt }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <!-- 退款弹窗 -->
    <el-dialog v-model="showRefund" title="订单退款" width="480px">
      <el-form ref="refundFormRef" :model="refundForm" :rules="refundRules" label-width="100px">
        <el-form-item label="退款原因" prop="reason">
          <el-input v-model="refundForm.reason" type="textarea" :rows="3" placeholder="请输入退款原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRefund = false">取消</el-button>
        <el-button type="danger" :loading="refunding" @click="handleRefund">确认退款</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { listOrders, cancelOrder, refundOrder, type Order } from '@/api/modules/order'

const orders = ref<Order[]>([])
const loading = ref(false)
const status = ref('')
const keyword = ref('')

const showDetail = ref(false)
const current = ref<Order | null>(null)

const showRefund = ref(false)
const refunding = ref(false)
const refundFormRef = ref<FormInstance>()
const refundForm = reactive({ reason: '' })
const refundRules: FormRules = {
  reason: [{ required: true, message: '请输入退款原因', trigger: 'blur' }]
}
let refundTarget: Order | null = null

async function loadData() {
  loading.value = true
  try {
    orders.value = await listOrders({ status: status.value, keyword: keyword.value })
  } finally {
    loading.value = false
  }
}

function openDetail(row: Order) {
  current.value = row
  showDetail.value = true
}

async function handleCancel(row: Order) {
  try {
    await ElMessageBox.confirm(`确认取消订单 ${row.orderId}？`, '提示', { type: 'warning' })
    await cancelOrder(row.orderId)
    ElMessage.success('已取消')
    loadData()
  } catch {}
}

function openRefund(row: Order) {
  refundTarget = row
  refundForm.reason = ''
  showRefund.value = true
}

async function handleRefund() {
  if (!refundFormRef.value || !refundTarget) return
  try { await refundFormRef.value.validate() } catch { return }
  refunding.value = true
  try {
    await refundOrder(refundTarget.orderId, refundForm.reason)
    ElMessage.success('退款申请已提交')
    showRefund.value = false
    loadData()
  } finally {
    refunding.value = false
  }
}

function formatCent(cent: number) {
  return (cent / 100).toFixed(2)
}

function statusText(s: string) {
  return ({ PENDING: '待支付', PAID: '已支付', COMPLETED: '已完成', CANCELED: '已取消', REFUNDED: '已退款' } as any)[s] || s
}
function statusTagType(s: string) {
  return ({ PENDING: 'warning', PAID: 'primary', COMPLETED: 'success', CANCELED: 'info', REFUNDED: 'danger' } as any)[s] || ''
}
</script>
