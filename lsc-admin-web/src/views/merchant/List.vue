<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="商家名称/账号" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="正常" value="1" />
          <el-option label="禁用" value="0" />
          <el-option label="待审核" value="2" />
        </el-select>
      </el-form-item>
      <el-form-item label="信用分">
        <el-input v-model="query.creditMin" placeholder="最低" style="width: 90px" />
        <span style="margin: 0 4px">-</span>
        <el-input v-model="query.creditMax" placeholder="最高" style="width: 90px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-toolbar">
      <div>
        <el-button type="primary" :icon="Plus">新增商家</el-button>
        <el-button :icon="Download">导出</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="id" label="商家ID" width="100" />
      <el-table-column prop="name" label="商家名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="contact" label="联系人" width="100" />
      <el-table-column prop="phone" label="联系电话" width="130" />
      <el-table-column prop="creditScore" label="信用分" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="creditTagType(row.creditScore)">{{ row.creditScore }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'warning' : 'danger'">
            {{ statusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="170" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">详情</el-button>
          <el-button link type="primary" @click="handleCredit(row)">信用</el-button>
          <el-button link type="danger" @click="handleDisable(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
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
import { Search, Refresh, Plus, Download } from '@element-plus/icons-vue'
import { getMerchantList } from '@/api/merchant'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: '',
  creditMin: '',
  creditMax: ''
})

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getMerchantList(query)
    tableData.value = res.data?.records || res.data?.list || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function creditTagType(score: number) {
  if (score >= 80) return 'success'
  if (score >= 60) return 'primary'
  return 'danger'
}

function statusText(status: number) {
  return status === 1 ? '正常' : status === 2 ? '待审核' : '禁用'
}

function handleSearch() {
  query.page = 1
  fetchData()
}

function handleReset() {
  query.keyword = ''
  query.status = ''
  query.creditMin = ''
  query.creditMax = ''
  handleSearch()
}

function handleView(row: any) {
  ElMessage.info(`查看商家 ${row.name} 详情`)
}

function handleCredit(row: any) {
  ElMessage.info(`管理商家 ${row.name} 信用`)
}

function handleDisable(row: any) {
  ElMessageBox.confirm(`确定要${row.status === 1 ? '禁用' : '启用'}该商家吗？`, '提示', {
    type: 'warning'
  }).then(() => {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.success('操作成功')
  }).catch(() => {})
}

onMounted(fetchData)
</script>
