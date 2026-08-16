<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="操作人">
        <el-input v-model="query.operator" placeholder="用户名" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="模块">
        <el-select v-model="query.module" placeholder="全部" clearable style="width: 150px">
          <el-option label="商家管理" value="merchant" />
          <el-option label="商品管理" value="product" />
          <el-option label="订单管理" value="order" />
          <el-option label="释放管理" value="release" />
          <el-option label="系统管理" value="admin" />
        </el-select>
      </el-form-item>
      <el-form-item label="操作类型">
        <el-select v-model="query.action" placeholder="全部" clearable style="width: 130px">
          <el-option label="查询" value="query" />
          <el-option label="新增" value="create" />
          <el-option label="修改" value="update" />
          <el-option label="删除" value="delete" />
          <el-option label="审核" value="audit" />
        </el-select>
      </el-form-item>
      <el-form-item label="AI异常">
        <el-switch v-model="query.abnormalOnly" active-text="仅看异常" />
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始"
          end-placeholder="结束"
          value-format="YYYY-MM-DD"
          @change="handleDateChange"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="filteredData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="role" label="角色" width="120" align="center">
        <template #default="{ row }">{{ roleText(row.role) }}</template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="120" align="center" />
      <el-table-column prop="action" label="操作类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="actionTagType(row.action)">{{ actionText(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="操作内容" min-width="220" show-overflow-tooltip />
      <el-table-column prop="ip" label="IP地址" width="140" />
      <el-table-column prop="aiAbnormal" label="AI异常标记" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.aiAbnormal" type="danger" effect="dark">
            <el-icon><Warning /></el-icon>异常
          </el-tag>
          <el-tag v-else type="success">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="aiTip" label="AI分析" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="操作时间" width="170" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleView(row)">详情</el-button>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh, Warning } from '@element-plus/icons-vue'
import { getAuditLogs } from '@/api/admin'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const query = reactive({
  page: 1, size: 10, operator: '', module: '', action: '', abnormalOnly: false,
  startDate: '', endDate: ''
})

const filteredData = computed(() => {
  if (query.abnormalOnly) return tableData.value.filter((r) => r.aiAbnormal)
  return tableData.value
})

const roleText = (r: string) => ({ super_admin: '超级管理员', ops_admin: '运营管理员', tech_admin: '技术管理员', finance_admin: '财务管理员' } as any)[r]
const actionText = (a: string) => ({ query: '查询', create: '新增', update: '修改', delete: '删除', audit: '审核' } as any)[a]
const actionTagType = (a: string) => ({ query: 'info', create: 'success', update: 'primary', delete: 'danger', audit: 'warning' } as any)[a]

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getAuditLogs(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleDateChange(val: [string, string] | null) {
  if (val) { query.startDate = val[0]; query.endDate = val[1] }
  else { query.startDate = ''; query.endDate = '' }
}
function handleSearch() { query.page = 1; fetchData() }
function handleReset() {
  query.operator = ''; query.module = ''; query.action = ''
  query.abnormalOnly = false; dateRange.value = null
  query.startDate = ''; query.endDate = ''
  handleSearch()
}
function handleView(row: any) { ElMessage.info(`查看审计记录 ${row.id}`) }

onMounted(fetchData)
</script>
