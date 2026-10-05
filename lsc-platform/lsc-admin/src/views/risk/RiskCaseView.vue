<template>
  <div class="page-container">
    <div class="toolbar">
      <el-button type="warning" :icon="Warning" @click="loadOverdue">查询超期案件</el-button>
      <el-tag type="danger">超期未复核: {{ overdueCount }}</el-tag>
    </div>
    <el-table :data="cases" v-loading="loading" border stripe>
      <el-table-column prop="caseId" label="案件ID" width="100" />
      <el-table-column prop="userId" label="用户ID" width="100" />
      <el-table-column prop="proposedAction" label="建议处置" width="140" />
      <el-table-column prop="reviewStatus" label="复核状态" width="100" />
      <el-table-column prop="reviewDeadline" label="复核截止" width="180" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" type="primary">复核</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Warning } from '@element-plus/icons-vue'
import { findOverdueCases } from '@/api/modules/risk'

const cases = ref<any[]>([])
const loading = ref(false)
const overdueCount = ref(0)

async function loadOverdue() {
  loading.value = true
  try {
    const ids = await findOverdueCases()
    overdueCount.value = ids.length
  } finally {
    loading.value = false
  }
}
</script>
