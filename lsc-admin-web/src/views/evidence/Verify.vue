<template>
  <div class="page-container">
    <el-card shadow="hover" style="margin-bottom: 16px">
      <template #header><div class="card-title">存证校验</div></template>
      <el-form :inline="true" :model="query">
        <el-form-item label="选择日期">
          <el-date-picker
            v-model="query.date"
            type="date"
            placeholder="选择校验日期"
            value-format="YYYY-MM-DD"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Check" :loading="verifying" @click="doVerify">开始校验</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-empty v-if="!report" description="请选择日期后开始存证校验" />

    <template v-else>
      <el-row :gutter="16" class="card-row">
        <el-col :span="6">
          <el-card shadow="hover"><el-statistic title="存证批次" :value="report.totalBatches" /></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><el-statistic title="已校验" :value="report.verifiedCount" /></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><el-statistic title="异常批次" :value="report.abnormalCount" /></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="整体一致性" :value="report.consistency" suffix="%" />
          </el-card>
        </el-col>
      </el-row>

      <el-card shadow="hover" style="margin-bottom: 16px">
        <template #header><div class="card-title">校验报告</div></template>
        <el-result
          :icon="report.abnormalCount === 0 ? 'success' : 'warning'"
          :title="report.abnormalCount === 0 ? '存证校验通过' : '发现异常批次，请核查'"
          :sub-title="`校验日期：${query.date} | 数据条数：${report.totalRecords} | 耗时：${report.duration}ms`"
        />
      </el-card>

      <el-card shadow="hover">
        <template #header><div class="card-title">批次校验明细</div></template>
        <el-table :data="report.details" border stripe>
          <el-table-column prop="batchNo" label="批次号" min-width="200" show-overflow-tooltip />
          <el-table-column prop="hash" label="存证哈希" min-width="240" show-overflow-tooltip />
          <el-table-column prop="recordCount" label="数据条数" width="110" align="center" />
          <el-table-column prop="recomputedHash" label="重算哈希" min-width="240" show-overflow-tooltip />
          <el-table-column prop="matched" label="一致性" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.matched ? 'success' : 'danger'">{{ row.matched ? '一致' : '不一致' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import { verifyEvidence } from '@/api/evidence'

const verifying = ref(false)
const report = ref<any>(null)
const query = reactive({ date: '' })

async function doVerify() {
  if (!query.date) {
    ElMessage.warning('请选择校验日期')
    return
  }
  verifying.value = true
  try {
    const res: any = await verifyEvidence(query)
    report.value = res.data
  } catch (e) {
    report.value = null
  } finally {
    verifying.value = false
  }
}
</script>

<style scoped>
.card-title { font-weight: 600; }
</style>
