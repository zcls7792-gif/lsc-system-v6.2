<script setup lang="ts">
// 链盛通 LSC 商家管理后台 · 主布局
import { ref } from 'vue'
import Sidebar from './components/Sidebar.vue'
import Header from './components/Header.vue'

const collapsed = ref(false)

function toggleCollapse() {
  collapsed.value = !collapsed.value
}
</script>

<template>
  <div class="layout" :class="{ 'layout--collapsed': collapsed }">
    <Sidebar :collapsed="collapsed" />

    <div class="layout__main">
      <Header :collapsed="collapsed" @toggle="toggleCollapse" />
      <main class="layout__content">
        <router-view v-slot="{ Component }">
          <transition name="view-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  min-height: 100vh;
  background: var(--lsc-bg);
}

.layout__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  margin-left: 248px;
  transition: margin-left 0.28s cubic-bezier(0.4, 0, 0.2, 1);
}

.layout--collapsed .layout__main {
  margin-left: 76px;
}

.layout__content {
  flex: 1;
  min-width: 0;
}
</style>
