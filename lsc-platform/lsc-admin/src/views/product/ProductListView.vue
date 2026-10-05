<template>
  <div class="page-container">
    <div class="toolbar">
      <div class="search-bar">
        <el-select v-model="status" placeholder="商品状态" style="width: 160px" @change="loadData">
          <el-option label="全部" value="" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="审核中" value="REVIEWING" />
          <el-option label="在售" value="ON_SALE" />
          <el-option label="下架" value="OFF_SALE" />
          <el-option label="售罄" value="SOLD_OUT" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索商品名称" style="width: 240px" clearable />
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      </div>
      <el-button type="primary" :icon="Plus" @click="showCreate = true">新增商品</el-button>
    </div>

    <el-table :data="products" v-loading="loading" border stripe>
      <el-table-column prop="productId" label="商品ID" width="100" />
      <el-table-column prop="name" label="商品名称" min-width="180" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditVersion" label="审核版本" width="100" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="viewSku(row)">SKU</el-button>
          <el-button v-if="row.status === 'DRAFT'" size="small" type="success" @click="handleSubmitReview(row)">提交审核</el-button>
          <el-button v-if="row.status === 'ON_SALE'" size="small" type="warning" @click="handleOffSale(row)">下架</el-button>
          <el-button v-if="row.status === 'OFF_SALE'" size="small" type="success" @click="handleOnSale(row)">上架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showCreate" title="新增商品" width="500px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="商品名称">
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item label="类目ID">
          <el-input-number v-model="createForm.categoryId" :min="1" />
        </el-form-item>
        <el-form-item label="退货政策版本">
          <el-input v-model="createForm.returnPolicyVersion" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { listProducts, createProduct, submitReview, offSale, onSale, type Product } from '@/api/modules/product'

const products = ref<Product[]>([])
const loading = ref(false)
const status = ref('')
const keyword = ref('')
const showCreate = ref(false)
const creating = ref(false)
const createForm = ref({ name: '', categoryId: 1, returnPolicyVersion: 'v1' })

async function loadData() {
  if (!status.value) return
  loading.value = true
  try {
    products.value = await listProducts(status.value)
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  creating.value = true
  try {
    await createProduct({ sellerEntityId: 1, ...createForm.value })
    ElMessage.success('创建成功')
    showCreate.value = false
    loadData()
  } finally {
    creating.value = false
  }
}

async function handleSubmitReview(row: Product) {
  try {
    await ElMessageBox.confirm(`确认提交「${row.name}」审核？`, '提示', { type: 'warning' })
    await submitReview(row.productId, 1)
    ElMessage.success('已提交审核')
    loadData()
  } catch {}
}

async function handleOffSale(row: Product) {
  await offSale(row.productId)
  ElMessage.success('已下架')
  loadData()
}

async function handleOnSale(row: Product) {
  await onSale(row.productId)
  ElMessage.success('已上架')
  loadData()
}

function viewSku(row: Product) {
  ElMessage.info(`查看商品 ${row.productId} 的 SKU`)
}

function statusText(s: string) {
  return ({ DRAFT: '草稿', REVIEWING: '审核中', ON_SALE: '在售', OFF_SALE: '下架', SOLD_OUT: '售罄' } as any)[s] || s
}

function statusTagType(s: string) {
  return ({ DRAFT: 'info', REVIEWING: 'warning', ON_SALE: 'success', OFF_SALE: 'info', SOLD_OUT: 'danger' } as any)[s] || ''
}

onMounted(() => {
  status.value = 'ON_SALE'
  loadData()
})
</script>
