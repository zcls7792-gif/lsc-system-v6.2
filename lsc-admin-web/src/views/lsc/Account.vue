<template>
  <div class="lsc-account-page">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>权益账户查询</span>
          <el-input
            v-model="userId"
            placeholder="输入用户ID"
            style="width: 200px"
            clearable
          />
          <el-button type="primary" @click="query">查询</el-button>
        </div>
      </template>

      <div v-if="account" class="account-detail">
        <el-descriptions :column="3" border>
          <el-descriptions-item label="总权益">
            {{ formatLsc(account.totalUnit) }} LSC
          </el-descriptions-item>
          <el-descriptions-item label="可用">
            <el-tag type="success">{{ formatLsc(account.availableUnit) }} LSC</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="锁定">
            <el-tag>{{ formatLsc(account.lockedUnit) }} LSC</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="支付占用">
            <el-tag type="warning">{{ formatLsc(account.reservedUnit) }} LSC</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="风险冻结">
            <el-tag type="danger">{{ formatLsc(account.frozenTotalUnit) }} LSC</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="待追偿">
            <el-tag type="info" v-if="hasPending">{{ formatLsc(account.pendingRecoveryUnit) }} LSC</el-tag>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="actions">
          <el-button type="primary" @click="manualRelease">手动释放</el-button>
          <el-button type="warning" @click="manualExpire">手动过期扫描</el-button>
        </div>
      </div>

      <el-empty v-else-if="queried" description="未找到用户账户" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getAccount, manualRelease, manualExpire, type LscAccount } from '@/api/lsc'

const userId = ref('')
const account = ref<LscAccount | null>(null)
const queried = ref(false)

const hasPending = computed(() => Number(account.value?.pendingRecoveryUnit || '0') > 0)

function formatLsc(unitStr?: string): string {
  return (Number(unitStr || '0') / 10000).toFixed(4)
}

async function query() {
  if (!userId.value) {
    ElMessage.warning('请输入用户ID')
    return
  }
  try {
    const res = await getAccount(Number(userId.value))
    account.value = res.data
    queried.value = true
  } catch (e) {
    account.value = null
    queried.value = true
  }
}

async function manualRelease() {
  if (!userId.value) return
  try {
    await manualRelease(Number(userId.value))
    ElMessage.success('释放任务已触发')
    query()
  } catch (e) {
    // error handled by interceptor
  }
}

async function manualExpire() {
  if (!userId.value) return
  try {
    await manualExpire(Number(userId.value))
    ElMessage.success('过期扫描已触发')
    query()
  } catch (e) {
    // error handled by interceptor
  }
}
</script>

<style scoped>
.lsc-account-page {
  padding: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}
.account-detail {
  margin-top: 20px;
}
.actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}
</style>
