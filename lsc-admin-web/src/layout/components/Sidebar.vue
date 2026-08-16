<template>
  <div class="sidebar-container">
    <div class="logo">
      <span v-if="!collapsed" class="logo-text">LSC管理后台</span>
      <span v-else class="logo-mini">LSC</span>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="collapsed"
      background-color="#001529"
      text-color="#bfcbd9"
      active-text-color="#409eff"
      :unique-opened="true"
      router
    >
      <el-menu-item index="/dashboard">
        <el-icon><Odometer /></el-icon>
        <template #title>首页</template>
      </el-menu-item>

      <el-sub-menu v-for="group in filteredGroups" :key="group.name" :index="group.name">
        <template #title>
          <el-icon><component :is="group.icon" /></el-icon>
          <span>{{ group.name }}</span>
        </template>
        <el-menu-item
          v-for="item in group.children"
          :key="item.path"
          :index="item.path"
        >
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-sub-menu>
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores/app'
import { useUserStore } from '@/stores/user'
import { routes } from '@/router'

const route = useRoute()
const appStore = useAppStore()
const { sidebarCollapsed: collapsed } = storeToRefs(appStore)
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

interface MenuItem {
  path: string
  title: string
}

interface MenuGroup {
  name: string
  icon: string
  children: MenuItem[]
}

const groupIconMap: Record<string, string> = {
  商家管理: 'Shop',
  商品管理: 'Goods',
  订单管理: 'List',
  B2B交易: 'Connection',
  核销管理: 'CircleCheck',
  释放管理: 'TrendCharts',
  风控管理: 'Warning',
  存证管理: 'Document',
  对账管理: 'Money',
  系统管理: 'Setting'
}

const groupOrder = [
  '商家管理',
  '商品管理',
  '订单管理',
  'B2B交易',
  '核销管理',
  '释放管理',
  '风控管理',
  '存证管理',
  '对账管理',
  '系统管理'
]

function hasPermission(metaRoles?: string[]) {
  if (!metaRoles || metaRoles.length === 0) return true
  return metaRoles.includes(userStore.role)
}

const filteredGroups = computed<MenuGroup[]>(() => {
  const groupMap = new Map<string, MenuItem[]>()
  for (const r of routes) {
    if (!r.children || r.meta?.hidden) continue
    if (r.meta?.roles && !hasPermission(r.meta.roles as string[])) continue
    for (const child of r.children) {
      const group = (child.meta?.group as string) || (r.meta?.title as string)
      if (!group) continue
      if (!hasPermission(child.meta?.roles as string[])) continue
      if (!groupMap.has(group)) groupMap.set(group, [])
      groupMap.get(group)!.push({
        path: `${r.path === '/' ? '' : r.path}/${child.path}`.replace('//', '/'),
        title: child.meta?.title as string
      })
    }
  }
  return groupOrder
    .filter((name) => groupMap.has(name))
    .map((name) => ({
      name,
      icon: groupIconMap[name] || 'Menu',
      children: groupMap.get(name)!
    }))
})
</script>

<style scoped>
.sidebar-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background-color: #002140;
  overflow: hidden;
  white-space: nowrap;
}
.logo-text {
  font-size: 18px;
  font-weight: 600;
}
.logo-mini {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
}
.el-menu {
  border-right: none;
  flex: 1;
}
:deep(.el-menu--collapse) {
  width: 64px;
}
</style>
