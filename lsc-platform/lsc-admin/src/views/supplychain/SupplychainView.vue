<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <!-- 供应商 -->
      <el-tab-pane label="供应商" name="supplier">
        <div class="toolbar">
          <el-input v-model="supplierKeyword" placeholder="供应商名称" style="width: 220px" clearable />
          <el-button type="primary" :icon="Plus" @click="openSupplierForm">新增供应商</el-button>
        </div>
        <el-table :data="suppliers" v-loading="supplierLoading" border stripe row-key="supplierId">
          <el-table-column prop="supplierId" label="ID" width="100" />
          <el-table-column prop="legalName" label="供应商名称" min-width="180" />
          <el-table-column prop="licenseNo" label="营业执照号" width="160" />
          <el-table-column prop="paymentTermDays" label="账期(天)" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="handleToggleSupplier(row)">{{ row.status === 'ACTIVE' ? '停用' : '启用' }}</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-dialog v-model="showSupplierForm" title="新增供应商" width="520px">
          <el-form ref="supplierFormRef" :model="supplierForm" :rules="supplierRules" label-width="110px">
            <el-form-item label="供应商名称" prop="legalName">
              <el-input v-model="supplierForm.legalName" />
            </el-form-item>
            <el-form-item label="营业执照号" prop="licenseNo">
              <el-input v-model="supplierForm.licenseNo" />
            </el-form-item>
            <el-form-item label="联系人(加密)" prop="contactEnc">
              <el-input v-model="supplierForm.contactEnc" />
            </el-form-item>
            <el-form-item label="银行账户(加密)" prop="bankAccountEnc">
              <el-input v-model="supplierForm.bankAccountEnc" />
            </el-form-item>
            <el-form-item label="账期(天)">
              <el-input-number v-model="supplierForm.paymentTermDays" :min="0" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showSupplierForm = false">取消</el-button>
            <el-button type="primary" :loading="supplierSubmitting" @click="handleCreateSupplier">确认</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- 采购单 -->
      <el-tab-pane label="采购单" name="purchase">
        <div class="toolbar">
          <el-button type="primary" :icon="Plus" @click="openPurchaseForm">新建采购单</el-button>
        </div>
        <el-table :data="purchases" v-loading="purchaseLoading" border stripe row-key="poId">
          <el-table-column prop="poId" label="ID" width="100" />
          <el-table-column prop="poNo" label="采购单号" width="180" />
          <el-table-column prop="supplierId" label="供应商" width="100" />
          <el-table-column label="金额(分)" width="120">
            <template #default="{ row }">{{ formatCent(row.totalCent) }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="170" />
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'DRAFT'" size="small" type="success" @click="handleApprovePo(row)">审批</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-dialog v-model="showPurchaseForm" title="新建采购单" width="600px">
          <el-form ref="purchaseFormRef" :model="purchaseForm" :rules="purchaseRules" label-width="100px">
            <el-form-item label="供应商ID" prop="supplierId">
              <el-input-number v-model="purchaseForm.supplierId" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="买方主体ID" prop="buyerEntityId">
              <el-input-number v-model="purchaseForm.buyerEntityId" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="SKU ID" prop="skuId">
              <el-input-number v-model="purchaseForm.skuId" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="数量" prop="qty">
              <el-input-number v-model="purchaseForm.qty" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
            <el-form-item label="单价(分)" prop="unitPriceCent">
              <el-input-number v-model="purchaseForm.unitPriceCent" :min="1" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="showPurchaseForm = false">取消</el-button>
            <el-button type="primary" :loading="purchaseSubmitting" @click="handleCreatePo">提交</el-button>
          </template>
        </el-dialog>
      </el-tab-pane>

      <!-- 库存 -->
      <el-tab-pane label="库存" name="stock">
        <div class="toolbar">
          <el-input v-model="stockFilter.warehouseId" placeholder="仓库ID" style="width: 140px" clearable />
          <el-input v-model="stockFilter.skuId" placeholder="SKU ID" style="width: 140px" clearable />
          <el-input v-model="stockFilter.batchNo" placeholder="批次号" style="width: 160px" clearable />
          <el-button type="primary" :icon="Search" @click="loadStock">查询</el-button>
        </div>
        <el-table :data="stocks" v-loading="stockLoading" border stripe row-key="id">
          <el-table-column prop="skuId" label="SKU" width="120" />
          <el-table-column prop="warehouseId" label="仓库" width="100" />
          <el-table-column prop="batchNo" label="批次" width="140" />
          <el-table-column prop="onHandQty" label="实物" width="100" />
          <el-table-column prop="reservedQty" label="预占" width="100" />
          <el-table-column prop="blockedQty" label="冻结" width="100" />
          <el-table-column label="可用" width="100">
            <template #default="{ row }">
              <el-tag type="success" size="small">{{ row.onHandQty - row.reservedQty - row.blockedQty }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import {
  createSupplier, updateSupplierStatus,
  createPurchaseOrder, approvePurchaseOrder,
  getAvailableStock,
  type Supplier, type PurchaseOrder
} from '@/api/modules/supplychain'

const activeTab = ref('supplier')

// 供应商
const suppliers = ref<Supplier[]>([])
const supplierLoading = ref(false)
const supplierKeyword = ref('')
const showSupplierForm = ref(false)
const supplierSubmitting = ref(false)
const supplierFormRef = ref<FormInstance>()
const supplierForm = reactive({ legalName: '', licenseNo: '', contactEnc: '', bankAccountEnc: '', paymentTermDays: 30 })
const supplierRules: FormRules = {
  legalName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  licenseNo: [{ required: true, message: '请输入营业执照号', trigger: 'blur' }]
}

function openSupplierForm() {
  Object.assign(supplierForm, { legalName: '', licenseNo: '', contactEnc: '', bankAccountEnc: '', paymentTermDays: 30 })
  showSupplierForm.value = true
}

async function handleCreateSupplier() {
  if (!supplierFormRef.value) return
  try { await supplierFormRef.value.validate() } catch { return }
  supplierSubmitting.value = true
  try {
    await createSupplier({ ...supplierForm })
    ElMessage.success('供应商创建成功')
    showSupplierForm.value = false
  } finally {
    supplierSubmitting.value = false
  }
}

async function handleToggleSupplier(row: Supplier) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  await updateSupplierStatus(row.supplierId, next)
  row.status = next
  ElMessage.success('状态已更新')
}

// 采购单
const purchases = ref<PurchaseOrder[]>([])
const purchaseLoading = ref(false)
const showPurchaseForm = ref(false)
const purchaseSubmitting = ref(false)
const purchaseFormRef = ref<FormInstance>()
const purchaseForm = reactive({ supplierId: 1, buyerEntityId: 1, skuId: 1, qty: 1, unitPriceCent: 100 })
const purchaseRules: FormRules = {
  supplierId: [{ required: true, message: '请输入供应商ID', trigger: 'change' }],
  buyerEntityId: [{ required: true, message: '请输入买方主体ID', trigger: 'change' }],
  skuId: [{ required: true, message: '请输入SKU ID', trigger: 'change' }],
  qty: [{ required: true, message: '请输入数量', trigger: 'change' }]
}

function openPurchaseForm() {
  Object.assign(purchaseForm, { supplierId: 1, buyerEntityId: 1, skuId: 1, qty: 1, unitPriceCent: 100 })
  showPurchaseForm.value = true
}

async function handleCreatePo() {
  if (!purchaseFormRef.value) return
  try { await purchaseFormRef.value.validate() } catch { return }
  purchaseSubmitting.value = true
  try {
    await createPurchaseOrder({
      supplierId: purchaseForm.supplierId,
      buyerEntityId: purchaseForm.buyerEntityId,
      items: [{ skuId: purchaseForm.skuId, qty: purchaseForm.qty, unitPriceCent: purchaseForm.unitPriceCent }]
    })
    ElMessage.success('采购单创建成功')
    showPurchaseForm.value = false
  } finally {
    purchaseSubmitting.value = false
  }
}

async function handleApprovePo(row: PurchaseOrder) {
  try {
    await ElMessageBox.confirm(`确认审批采购单 ${row.poNo}？`, '提示', { type: 'success' })
    await approvePurchaseOrder(row.poId, 1)
    row.status = 'APPROVED'
    ElMessage.success('已审批')
  } catch {}
}

// 库存
const stocks = ref<any[]>([])
const stockLoading = ref(false)
const stockFilter = reactive({ warehouseId: '', skuId: '', batchNo: '' })

async function loadStock() {
  if (!stockFilter.warehouseId || !stockFilter.skuId || !stockFilter.batchNo) {
    ElMessage.warning('请输入仓库ID、SKU ID、批次号')
    return
  }
  stockLoading.value = true
  try {
    const available = await getAvailableStock(
      Number(stockFilter.warehouseId), Number(stockFilter.skuId), stockFilter.batchNo
    )
    stocks.value = [{
      id: `${stockFilter.skuId}-${stockFilter.warehouseId}-${stockFilter.batchNo}`,
      skuId: Number(stockFilter.skuId),
      warehouseId: Number(stockFilter.warehouseId),
      batchNo: stockFilter.batchNo,
      onHandQty: available, reservedQty: 0, blockedQty: 0
    }]
  } finally {
    stockLoading.value = false
  }
}

function formatCent(cent: number) {
  return (cent / 100).toFixed(2)
}
</script>
