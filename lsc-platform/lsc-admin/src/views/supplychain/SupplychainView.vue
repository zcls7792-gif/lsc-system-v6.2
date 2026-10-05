<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="供应商" name="supplier">
        <el-table :data="suppliers" border stripe>
          <el-table-column prop="supplierId" label="ID" width="100" />
          <el-table-column prop="legalName" label="供应商名称" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="paymentTermDays" label="账期(天)" width="100" />
          <el-table-column label="操作" width="160">
            <template #default>
              <el-button size="small">变更账户</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="采购单" name="purchase">
        <el-table :data="purchases" border stripe>
          <el-table-column prop="poId" label="采购单ID" width="120" />
          <el-table-column prop="poNo" label="采购单号" width="180" />
          <el-table-column prop="supplierId" label="供应商" width="100" />
          <el-table-column prop="totalCent" label="金额(分)" width="120" />
          <el-table-column prop="status" label="状态" width="100" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="库存" name="stock">
        <el-table :data="stocks" border stripe>
          <el-table-column prop="skuId" label="SKU" width="120" />
          <el-table-column prop="warehouseId" label="仓库" width="100" />
          <el-table-column prop="onHandQty" label="实物" width="100" />
          <el-table-column prop="reservedQty" label="预占" width="100" />
          <el-table-column prop="blockedQty" label="冻结" width="100" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const activeTab = ref('supplier')
const suppliers = ref<any[]>([])
const purchases = ref<any[]>([])
const stocks = ref<any[]>([])
</script>
