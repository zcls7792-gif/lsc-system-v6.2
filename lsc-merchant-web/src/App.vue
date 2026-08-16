<script setup lang="ts">
// 链盛通 LSC 商家管理后台 · 根组件
import { onBeforeMount } from 'vue'
import { useMerchantStore } from '@/stores/merchant'

const merchant = useMerchantStore()

onBeforeMount(() => {
  // 启动时从本地恢复 token 与商家信息
  merchant.restore()
})
</script>

<template>
  <router-view v-slot="{ Component }">
    <transition name="view-fade" mode="out-in">
      <component :is="Component" />
    </transition>
  </router-view>
</template>
