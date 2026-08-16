<template>
  <el-container class="app-wrapper">
    <el-aside :width="sidebarCollapsed ? '64px' : '220px'" class="app-aside">
      <Sidebar />
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <Header />
      </el-header>
      <Breadcrumb />
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-transform" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { useAppStore } from '@/stores/app'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'
import Breadcrumb from './components/Breadcrumb.vue'

const appStore = useAppStore()
const { sidebarCollapsed } = storeToRefs(appStore)
</script>

<style scoped>
.app-wrapper {
  height: 100vh;
  width: 100%;
}
.app-aside {
  background-color: #001529;
  transition: width 0.28s;
  overflow: hidden;
}
.app-header {
  background-color: #fff;
  border-bottom: 1px solid #f0f0f0;
  padding: 0 16px;
  height: 56px;
  line-height: 56px;
  display: flex;
  align-items: center;
}
.app-main {
  background-color: #f0f2f5;
  padding: 16px;
  overflow-y: auto;
}
.fade-transform-enter-active,
.fade-transform-leave-active {
  transition: all 0.3s;
}
.fade-transform-enter-from {
  opacity: 0;
  transform: translateX(-10px);
}
.fade-transform-leave-to {
  opacity: 0;
  transform: translateX(10px);
}
</style>
