<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="商家">
        <el-input v-model="query.keyword" placeholder="商家名称/ID" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="处罚状态">
        <el-select v-model="query.status" placeholder="全部" clearable style="width: 140px">
          <el-option label="正常" value="normal" />
          <el-option label="警告" value="warning" />
          <el-option label="限制" value="restricted" />
          <el-option label="封禁" value="banned" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="name" label="商家名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="creditScore" label="信用分" width="100" align="center">
        <template #default="{ row }">
          <el-progress :percentage="row.creditScore" :color="creditColor(row.creditScore)" :stroke-width="14" :text-inside="true" />
        </template>
      </el-table-column>
      <el-table-column prop="violationCount" label="违规次数" width="100" align="center" />
      <el-table-column prop="penaltyStatus" label="处罚状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="penaltyTagType(row.penaltyStatus)">{{ penaltyText(row.penaltyStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastViolation" label="最近违规时间" width="170" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="viewDetail(row)">明细</el-button>
          <el-button link type="warning" @click="openPenalty(row)">处罚</el-button>
          <el-button link type="success" @click="openAdjust(row)">调整</el-button>
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

    <!-- 信用明细 -->
    <el-dialog v-model="detailVisible" title="信用分明细" width="640px">
      <el-timeline v-if="current">
        <el-timeline-item
          v-for="(item, idx) in current.records"
          :key="idx"
          :timestamp="item.time"
          :type="item.delta > 0 ? 'success' : 'danger'"
        >
          <el-card>
            <p><b>{{ item.reason }}</b></p>
            <p>信用分变化：<el-tag :type="item.delta > 0 ? 'success' : 'danger'">{{ item.delta > 0 ? '+' : '' }}{{ item.delta }}</el-tag></p>
            <p>当前信用分：{{ item.afterScore }}</p>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>

    <!-- 处罚操作 -->
    <el-dialog v-model="penaltyVisible" title="商家处罚" width="480px">
      <el-form :model="penaltyForm" label-width="90px">
        <el-form-item label="处罚类型">
          <el-select v-model="penaltyForm.type" placeholder="请选择" style="width: 100%">
            <el-option label="警告" value="warning" />
            <el-option label="限制交易" value="restricted" />
            <el-option label="封禁" value="banned" />
          </el-select>
        </el-form-item>
        <el-form-item label="封禁天数">
          <el-input-number v-model="penaltyForm.days" :min="1" :max="365" />
        </el-form-item>
        <el-form-item label="处罚原因">
          <el-input v-model="penaltyForm.reason" type="textarea" :rows="3" placeholder="请输入处罚原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="penaltyVisible = false">取消</el-button>
        <el-button type="danger" @click="submitPenalty">确认处罚</el-button>
      </template>
    </el-dialog>

    <!-- 信用调整 -->
    <el-dialog v-model="adjustVisible" title="信用分调整" width="480px">
      <el-form :model="adjustForm" label-width="90px">
        <el-form-item label="调整分值">
          <el-input-number v-model="adjustForm.delta" :min="-50" :max="50" />
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="adjustForm.reason" type="textarea" :rows="3" placeholder="请输入调整原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdjust">确认调整</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Refresh } from '@element-plus/icons-vue'
import { penalizeMerchant, adjustCredit, getViolationLogs } from '@/api/merchant'

const loading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const detailVisible = ref(false)
const penaltyVisible = ref(false)
const adjustVisible = ref(false)
const current = ref<any>(null)

const query = reactive({ page: 1, size: 10, keyword: '', status: '' })
const penaltyForm = reactive({ type: 'warning', reason: '', days: 7 })
const adjustForm = reactive({ delta: 0, reason: '' })

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getViolationLogs(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function creditColor(score: number) {
  if (score >= 80) return '#67c23a'
  if (score >= 60) return '#e6a23c'
  return '#f56c6c'
}
function penaltyText(s: string) {
  return ({ normal: '正常', warning: '警告', restricted: '限制', banned: '封禁' } as any)[s]
}
function penaltyTagType(s: string) {
  return ({ normal: 'success', warning: 'warning', restricted: 'warning', banned: 'danger' } as any)[s]
}

function handleSearch() { query.page = 1; fetchData() }
function handleReset() { query.keyword = ''; query.status = ''; handleSearch() }

function viewDetail(row: any) { current.value = row; detailVisible.value = true }
function openPenalty(row: any) { current.value = row; penaltyForm.type = 'warning'; penaltyForm.reason = ''; penaltyForm.days = 7; penaltyVisible.value = true }
function openAdjust(row: any) { current.value = row; adjustForm.delta = 0; adjustForm.reason = ''; adjustVisible.value = true }

async function submitPenalty() {
  if (!current.value) return
  try {
    await penalizeMerchant(current.value.id, penaltyForm)
  } catch (e) { ElMessage.error('操作失败') }
  ElMessage.success('处罚已执行')
  penaltyVisible.value = false
  fetchData()
}
async function submitAdjust() {
  if (!current.value) return
  try {
    await adjustCredit(current.value.id, adjustForm)
  } catch (e) { ElMessage.error('操作失败') }
  ElMessage.success('信用分已调整')
  adjustVisible.value = false
  fetchData()
}

onMounted(fetchData)
</script>
