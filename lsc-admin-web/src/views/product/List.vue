<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="商品名称" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="在售" value="on_sale" />
          <el-option label="下架" value="off_shelf" />
          <el-option label="待审核" value="pending" />
        </el-select>
      </el-form-item>
      <el-form-item label="商家ID">
        <el-input v-model="query.merchantId" placeholder="商家ID" clearable style="width: 120px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="主图" width="90" align="center">
        <template #default="{ row }">
          <el-image
            style="width: 50px; height: 50px"
            :src="row.imageUrl"
            :preview-src-list="row.imageUrl ? [row.imageUrl] : []"
            fit="cover"
            preview-teleported
          >
            <template #error><div class="img-mini">无图</div></template>
          </el-image>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="商品名称" min-width="180" show-overflow-tooltip />
      <el-table-column prop="price" label="价格(元)" width="110" align="right">
        <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="90" align="center" />
      <el-table-column prop="merchantName" label="所属商家" width="140" show-overflow-tooltip />
      <el-table-column prop="aiLabel" label="AI审核标签" width="140" align="center">
        <template #default="{ row }">
          <el-tag class="ai-tag" :type="aiTagType(row.aiLabel)" effect="plain">
            <el-icon><MagicStick /></el-icon>{{ aiLabelText(row.aiLabel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handlePreview(row)">预览</el-button>
          <el-button link type="warning" @click="toggleStatus(row)">{{ row.status === 'on_sale' ? '下架' : '上架' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, MagicStick } from '@element-plus/icons-vue'
import { getProductList, toggleProductStatus } from '@/api/product'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const query = reactive({ page: 1, size: 10, keyword: '', status: '', merchantId: '' })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getProductList(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function aiLabelText(label: string) {
  return ({ pass: 'AI通过', review: 'AI待复核', reject: 'AI驳回' } as any)[label]
}
function aiTagType(label: string) {
  return ({ pass: 'success', review: 'warning', reject: 'danger' } as any)[label]
}
function statusText(s: string) {
  return ({ on_sale: '在售', off_shelf: '下架', pending: '待审核' } as any)[s]
}
function statusTagType(s: string) {
  return ({ on_sale: 'success', off_shelf: 'info', pending: 'warning' } as any)[s]
}

function handleSearch() { query.page = 1; fetchData() }
function handleReset() { query.keyword = ''; query.status = ''; query.merchantId = ''; handleSearch() }
function handlePreview(row: any) { ElMessage.info(`预览商品 ${row.name}`) }

async function toggleStatus(row: any) {
  const action = row.status === 'on_sale' ? '下架' : '上架'
  await ElMessageBox.confirm(`确定${action}该商品吗？`, '提示', { type: 'warning' })
  try { await toggleProductStatus(row.id, row.status === 'on_sale' ? 'off_shelf' : 'on_sale') }
  catch (e) { ElMessage.error('操作失败') }
  row.status = row.status === 'on_sale' ? 'off_shelf' : 'on_sale'
  ElMessage.success(`${action}成功`)
}

onMounted(fetchData)
</script>

<style scoped>
.img-mini {
  width: 50px;
  height: 50px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  color: #c0c4cc;
  font-size: 12px;
  border-radius: 4px;
}
</style>
