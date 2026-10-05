<template>
  <div class="page-container">
    <div class="toolbar">
      <div class="search-bar">
        <el-input v-model="keyword" placeholder="订单号/用户ID" style="width: 240px" clearable />
        <el-select v-model="status" placeholder="订单状态" style="width: 160px">
          <el-option label="全部" value="" />
          <el-option label="待支付" value="PENDING" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELED" />
        </el-select>
        <el-button type="primary" :icon="Search">查询</el-button>
      </div>
    </div>
    <el-table :data="orders" v-loading="loading" border stripe>
      <el-table-column prop="orderId" label="订单号" width="160" />
      <el-table-column prop="userId" label="用户ID" width="100" />
      <el-table-column prop="totalCent" label="金额(分)" width="120" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && orders.length === 0" description="暂无订单数据" />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'

const orders = ref<any[]>([])
const loading = ref(false)
const status = ref('')
const keyword = ref('')
</script>
