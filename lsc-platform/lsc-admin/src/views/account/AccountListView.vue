<template>
  <div class="page-container">
    <div class="toolbar">
      <el-input v-model="userId" placeholder="输入用户ID查询" style="width: 240px" />
      <el-button type="primary" :icon="Search" @click="loadAccount">查询</el-button>
    </div>
    <el-card v-if="account">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="用户ID">{{ account.userId }}</el-descriptions-item>
        <el-descriptions-item label="锁定(LOCKED)">{{ account.lockedUnit }}</el-descriptions-item>
        <el-descriptions-item label="可用(AVAILABLE)">{{ account.availableUnit }}</el-descriptions-item>
        <el-descriptions-item label="预占(RESERVED)">{{ account.reservedUnit }}</el-descriptions-item>
        <el-descriptions-item label="冻结锁定">{{ account.frozenLockedUnit }}</el-descriptions-item>
        <el-descriptions-item label="冻结可用">{{ account.frozenAvailableUnit }}</el-descriptions-item>
        <el-descriptions-item label="事件序号">{{ account.lastEventSeq }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
    <el-empty v-else-if="!loading" description="请输入用户ID查询账户" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { getAccount, type Account } from '@/api/modules/account'

const userId = ref('')
const account = ref<Account | null>(null)
const loading = ref(false)

async function loadAccount() {
  if (!userId.value) return
  loading.value = true
  try {
    account.value = await getAccount(Number(userId.value))
  } finally {
    loading.value = false
  }
}
</script>
