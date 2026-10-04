<template>
  <div class="lsc-release-page">
    <el-card>
      <template #header>
        <span>释放参数配置</span>
      </template>

      <el-descriptions :column="2" border>
        <el-descriptions-item label="周转率下限 w_min">
          {{ config.wMinPpm }} ppm ({{ (config.wMinPpm / 10000).toFixed(2) }}%)
        </el-descriptions-item>
        <el-descriptions-item label="周转率上限 w_max">
          {{ config.wMaxPpm }} ppm ({{ (config.wMaxPpm / 10000).toFixed(2) }}%)
        </el-descriptions-item>
        <el-descriptions-item label="最低释放率 r_min">
          {{ config.rMinPpb }} ppb ({{ (config.rMinPpb / 10000000).toFixed(4) }}%)
        </el-descriptions-item>
        <el-descriptions-item label="最高释放率 r_max">
          {{ config.rMaxPpb }} ppb ({{ (config.rMaxPpb / 10000000).toFixed(4) }}%)
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        style="margin-top: 16px"
        type="info"
        :closable="false"
        title="释放率插值公式"
        description="当 w ≤ w_min 时 r = r_min；当 w ≥ w_max 时 r = r_max；中间线性插值。默认 w=1.5% 对应 r=0.075%。"
      />
    </el-card>

    <el-card style="margin-top: 20px">
      <template #header>
        <span>手动释放操作</span>
      </template>

      <el-form :inline="true">
        <el-form-item label="用户ID">
          <el-input v-model="userId" placeholder="输入用户ID" />
        </el-form-item>
        <el-form-item label="业务日期">
          <el-date-picker v-model="bizDate" type="date" value-format="YYYY-MM-DD" placeholder="可选，默认今天" />
        </el-form-item>
        <el-form-item label="释放率(ppb)">
          <el-input-number v-model="ratePpb" :min="500000" :max="1000000" :step="10000" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="doRelease">触发释放</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        style="margin-top: 12px"
        type="warning"
        :closable="false"
        title="注意"
        description="手动释放仅用于运维补偿。正常释放由每日定时任务执行。"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getReleaseConfig } from '@/api/config'
import { manualRelease } from '@/api/lsc'

const config = ref({ wMinPpm: 10000, wMaxPpm: 20000, rMinPpb: 500000, rMaxPpb: 1000000 })
const userId = ref('')
const bizDate = ref('')
const ratePpb = ref(750000)

onMounted(async () => {
  try {
    const res = await getReleaseConfig()
    config.value = res.data
  } catch (e) {
    // ignore
  }
})

async function doRelease() {
  if (!userId.value) {
    ElMessage.warning('请输入用户ID')
    return
  }
  try {
    await manualRelease(Number(userId.value), bizDate.value || undefined, ratePpb.value)
    ElMessage.success('释放任务已触发')
  } catch (e) {
    // error handled by interceptor
  }
}
</script>

<style scoped>
.lsc-release-page {
  padding: 20px;
}
</style>
