<template>
  <div class="breadcrumb-wrapper">
    <el-breadcrumb :separator-icon="ArrowRight">
      <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item v-for="item in breadcrumbList" :key="item.path">
        {{ item.title }}
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowRight } from '@element-plus/icons-vue'

const route = useRoute()

interface BreadcrumbItem {
  path: string
  title: string
}

const breadcrumbList = computed<BreadcrumbItem[]>(() => {
  const matched = route.matched.filter((item) => item.meta && item.meta.title)
  const list: BreadcrumbItem[] = []
  matched.forEach((item) => {
    if (item.path !== '/dashboard' && item.meta?.title) {
      list.push({ path: item.path, title: item.meta.title as string })
    }
  })
  return list
})
</script>

<style scoped>
.breadcrumb-wrapper {
  background-color: #fff;
  padding: 10px 16px;
  border-bottom: 1px solid #f0f0f0;
}
</style>
