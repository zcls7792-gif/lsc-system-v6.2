<template>
  <div id="app">
    <router-view />
    <TabBar v-if="showTabBar" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TabBar from './components/TabBar.vue'

const route = useRoute()
const showTabBar = computed(() => !(route.meta.hideTabBar as boolean))
</script>
