<template>
  <div class="page-container">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="配置变更" name="config">
        <div class="toolbar">
          <el-button type="primary" :icon="Plus">发起变更</el-button>
        </div>
        <el-table :data="changes" border stripe>
          <el-table-column prop="changeId" label="工单ID" width="100" />
          <el-table-column prop="configGroup" label="配置分组" width="160" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="effectiveDate" label="生效日期" width="140" />
          <el-table-column label="操作" width="200">
            <template #default>
              <el-button size="small">审批</el-button>
              <el-button size="small" type="success">生效</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="审计日志" name="audit">
        <el-table :data="audits" border stripe>
          <el-table-column prop="logId" label="日志ID" width="100" />
          <el-table-column prop="actorId" label="操作人" width="100" />
          <el-table-column prop="action" label="操作" width="160" />
          <el-table-column prop="resourceType" label="资源类型" width="120" />
          <el-table-column prop="result" label="结果" width="80" />
          <el-table-column prop="occurredAt" label="时间" width="180" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="合规门禁" name="gate">
        <el-table :data="gates" border stripe>
          <el-table-column prop="gateCode" label="门禁编码" width="180" />
          <el-table-column prop="scope" label="用途" width="160" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="expiryAt" label="过期时间" width="180" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Plus } from '@element-plus/icons-vue'

const activeTab = ref('config')
const changes = ref<any[]>([])
const audits = ref<any[]>([])
const gates = ref<any[]>([])
</script>
