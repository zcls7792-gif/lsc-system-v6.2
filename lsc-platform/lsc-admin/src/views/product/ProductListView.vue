<template>
  <div class="page-container">
    <div class="toolbar">
      <div class="search-bar">
        <el-select v-model="status" placeholder="商品状态" style="width: 140px" @change="loadData">
          <el-option label="全部" value="" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="审核中" value="REVIEWING" />
          <el-option label="在售" value="ON_SALE" />
          <el-option label="下架" value="OFF_SALE" />
          <el-option label="售罄" value="SOLD_OUT" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索商品名称" style="width: 220px" clearable />
        <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增商品</el-button>
    </div>

    <el-table :data="products" v-loading="loading" border stripe row-key="productId">
      <el-table-column prop="productId" label="商品ID" width="100" />
      <el-table-column prop="name" label="商品名称" min-width="180" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="auditVersion" label="审核版本" width="100" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openSku(row)">SKU</el-button>
          <el-button v-if="row.status === 'DRAFT'" size="small" type="success" @click="handleSubmitReview(row)">提交审核</el-button>
          <el-button v-if="row.status === 'ON_SALE'" size="small" type="warning" @click="handleOffSale(row)">下架</el-button>
          <el-button v-if="row.status === 'OFF_SALE'" size="small" type="success" @click="handleOnSale(row)">上架</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑商品弹窗 -->
    <el-dialog v-model="showForm" :title="formMode === 'create' ? '新增商品' : '编辑商品'" width="520px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="110px">
        <el-form-item label="商品名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="类目ID" prop="categoryId">
          <el-input-number v-model="form.categoryId" :min="1" controls-position="right" />
        </el-form-item>
        <el-form-item label="退货政策版本" prop="returnPolicyVersion">
          <el-input v-model="form.returnPolicyVersion" />
        </el-form-item>
        <el-form-item label="描述引用">
          <el-input v-model="form.descriptionRef" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showForm = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确认</el-button>
      </template>
    </el-dialog>

    <!-- SKU 管理弹窗 -->
    <el-dialog v-model="showSku" :title="`SKU 管理 - ${currentProduct?.name || ''}`" width="800px" top="5vh">
      <div style="margin-bottom: 12px">
        <el-button type="primary" size="small" :icon="Plus" @click="openSkuCreate">新增 SKU</el-button>
      </div>
      <el-table :data="skus" v-loading="skuLoading" border stripe row-key="skuId" max-height="360">
        <el-table-column prop="skuCode" label="SKU编码" width="140" />
        <el-table-column prop="specJson" label="规格" min-width="140" show-overflow-tooltip />
        <el-table-column prop="saleUnit" label="单位" width="80" />
        <el-table-column prop="packQty" label="箱规" width="80" />
        <el-table-column prop="bMinQty" label="B端起订" width="90" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openPriceVersion(row)">价格</el-button>
            <el-button size="small" :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleSkuStatus(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 新增 SKU 内嵌表单 -->
      <el-form v-if="showSkuForm" ref="skuFormRef" :model="skuForm" :rules="skuRules" label-width="90px" inline style="margin-top: 16px; padding: 12px; background: #f5f7fa">
        <el-form-item label="SKU编码" prop="skuCode">
          <el-input v-model="skuForm.skuCode" style="width: 160px" />
        </el-form-item>
        <el-form-item label="规格JSON" prop="specJson">
          <el-input v-model="skuForm.specJson" placeholder='{"color":"red"}' style="width: 180px" />
        </el-form-item>
        <el-form-item label="单位">
          <el-select v-model="skuForm.saleUnit" style="width: 110px">
            <el-option v-for="u in saleUnits" :key="u" :label="u" :value="u" />
          </el-select>
        </el-form-item>
        <el-form-item label="箱规" prop="packQty">
          <el-input-number v-model="skuForm.packQty" :min="1" controls-position="right" style="width: 120px" />
        </el-form-item>
        <el-form-item label="B端起订" prop="bMinQty">
          <el-input-number v-model="skuForm.bMinQty" :min="1" controls-position="right" style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" :loading="skuSubmitting" @click="handleCreateSku">保存</el-button>
          <el-button size="small" @click="showSkuForm = false">取消</el-button>
        </el-form-item>
      </el-form>

      <!-- 价格版本管理 -->
      <template v-if="priceTarget">
        <el-divider>价格版本 - {{ priceTarget.skuCode }}</el-divider>
        <el-button size="small" type="primary" :icon="Plus" @click="showPriceForm = true">新增价格版本</el-button>
        <el-table :data="priceVersions" v-loading="priceLoading" border size="small" style="margin-top: 8px">
          <el-table-column prop="priceVersion" label="版本ID" width="120" />
          <el-table-column prop="retailPriceCent" label="C端价(分)" width="110" />
          <el-table-column prop="bPriceCent" label="B端价(分)" width="110" />
          <el-table-column prop="grantCoefPpm" label="赠送系数ppm" width="120" />
          <el-table-column prop="effectiveAt" label="生效时间" width="170" />
        </el-table>

        <el-dialog v-model="showPriceForm" title="新增价格版本" width="500px" append-to-body>
          <el-form ref="priceFormRef" :model="priceForm" :rules="priceRules" label-width="130px">
            <el-form-item label="C端零售价(分)" prop="retailPriceCent">
              <el-input-number v-model="priceForm.retailPriceCent" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="B端采购价(分)" prop="bPriceCent">
              <el-input-number v-model="priceForm.bPriceCent" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="成本价(分)" prop="costPriceCent">
              <el-input-number v-model="priceForm.costPriceCent" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="赠送系数ppm" prop="grantCoefPpm">
              <el-input-number v-model="priceForm.grantCoefPpm" :min="0" :max="1000000" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="生效时间" prop="effectiveAt">
              <el-date-picker v-model="priceForm.effectiveAt" type="datetime" style="width: 100%" />
            </el-form-item>
            <el-form-item label="审批人ID" prop="approvedBy">
              <el-input-number v-model="priceForm.approvedBy" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showPriceForm = false">取消</el-button>
            <el-button type="primary" :loading="priceSubmitting" @click="handleCreatePrice">确认</el-button>
          </template>
        </el-dialog>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import {
  listProducts, createProduct, submitReview, offSale, onSale,
  listSkus, createSku, updateSkuStatus,
  listPriceVersions, createPriceVersion,
  type Product, type Sku, type PriceVersion
} from '@/api/modules/product'

const products = ref<Product[]>([])
const loading = ref(false)
const status = ref('ON_SALE')
const keyword = ref('')

const showForm = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({
  name: '', categoryId: 1, returnPolicyVersion: 'v1', descriptionRef: ''
})
const formRules: FormRules = {
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请输入类目ID', trigger: 'change' }],
  returnPolicyVersion: [{ required: true, message: '请输入退货政策版本', trigger: 'blur' }]
}

const showSku = ref(false)
const skuLoading = ref(false)
const skus = ref<Sku[]>([])
const currentProduct = ref<Product | null>(null)
const showSkuForm = ref(false)
const skuSubmitting = ref(false)
const skuFormRef = ref<FormInstance>()
const skuForm = reactive({ skuCode: '', specJson: '', saleUnit: 'PIECE', packQty: 1, bMinQty: 1 })
const skuRules: FormRules = {
  skuCode: [{ required: true, message: '请输入SKU编码', trigger: 'blur' }],
  packQty: [{ required: true, message: '箱规必须>0', trigger: 'change' }],
  bMinQty: [{ required: true, message: '起订量必须>0', trigger: 'change' }]
}
const saleUnits = ['PIECE', 'BOX', 'BOTTLE', 'BAG', 'CARTON', 'KG', 'G']

const priceTarget = ref<Sku | null>(null)
const priceLoading = ref(false)
const priceVersions = ref<PriceVersion[]>([])
const showPriceForm = ref(false)
const priceSubmitting = ref(false)
const priceFormRef = ref<FormInstance>()
const priceForm = reactive({
  retailPriceCent: 0, bPriceCent: 0, costPriceCent: 0,
  grantCoefPpm: 0, effectiveAt: '', approvedBy: 1
})
const priceRules: FormRules = {
  retailPriceCent: [{ required: true, message: '请输入C端价', trigger: 'change' }],
  bPriceCent: [{ required: true, message: '请输入B端价', trigger: 'change' }],
  grantCoefPpm: [{ required: true, message: '请输入赠送系数', trigger: 'change' }],
  effectiveAt: [{ required: true, message: '请选择生效时间', trigger: 'change' }],
  approvedBy: [{ required: true, message: '请输入审批人ID', trigger: 'change' }]
}

async function loadData() {
  if (!status.value) {
    products.value = []
    return
  }
  loading.value = true
  try {
    products.value = await listProducts(status.value)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  formMode.value = 'create'
  Object.assign(form, { name: '', categoryId: 1, returnPolicyVersion: 'v1', descriptionRef: '' })
  showForm.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  try { await formRef.value.validate() } catch { return }
  submitting.value = true
  try {
    if (formMode.value === 'create') {
      await createProduct({ sellerEntityId: 1, ...form })
      ElMessage.success('创建成功')
    }
    showForm.value = false
    loadData()
  } finally {
    submitting.value = false
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

async function openSku(row: Product) {
  currentProduct.value = row
  priceTarget.value = null
  showSku.value = true
  skuLoading.value = true
  try {
    skus.value = await listSkus(row.productId)
  } finally {
    skuLoading.value = false
  }
}

function openSkuCreate() {
  Object.assign(skuForm, { skuCode: '', specJson: '', saleUnit: 'PIECE', packQty: 1, bMinQty: 1 })
  showSkuForm.value = true
}

async function handleCreateSku() {
  if (!skuFormRef.value || !currentProduct.value) return
  try { await skuFormRef.value.validate() } catch { return }
  skuSubmitting.value = true
  try {
    await createSku({ productId: currentProduct.value.productId, ...skuForm })
    ElMessage.success('SKU 创建成功')
    showSkuForm.value = false
    skus.value = await listSkus(currentProduct.value.productId)
  } finally {
    skuSubmitting.value = false
  }
}

async function toggleSkuStatus(row: Sku) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  await updateSkuStatus(row.skuId, next)
  row.status = next
  ElMessage.success('状态已更新')
}

async function openPriceVersion(row: Sku) {
  priceTarget.value = row
  priceLoading.value = true
  try {
    priceVersions.value = await listPriceVersions(row.skuId)
  } finally {
    priceLoading.value = false
  }
}

async function handleCreatePrice() {
  if (!priceFormRef.value || !priceTarget.value) return
  try { await priceFormRef.value.validate() } catch { return }
  priceSubmitting.value = true
  try {
    await createPriceVersion({
      skuId: priceTarget.value.skuId,
      ...priceForm,
      requesterId: 1,
      grantCUnit: 0, grantBUnit: 0
    })
    ElMessage.success('价格版本创建成功')
    showPriceForm.value = false
    priceVersions.value = await listPriceVersions(priceTarget.value.skuId)
  } finally {
    priceSubmitting.value = false
  }
}

function statusText(s: string) {
  return ({ DRAFT: '草稿', REVIEWING: '审核中', ON_SALE: '在售', OFF_SALE: '下架', SOLD_OUT: '售罄' } as any)[s] || s
}
function statusTagType(s: string) {
  return ({ DRAFT: 'info', REVIEWING: 'warning', ON_SALE: 'success', OFF_SALE: 'info', SOLD_OUT: 'danger' } as any)[s] || ''
}
</script>
