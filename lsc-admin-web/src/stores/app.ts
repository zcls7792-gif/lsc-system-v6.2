import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref<boolean>(localStorage.getItem('lsc_sidebar') === '1')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('lsc_sidebar', sidebarCollapsed.value ? '1' : '0')
  }

  function setSidebar(collapsed: boolean) {
    sidebarCollapsed.value = collapsed
    localStorage.setItem('lsc_sidebar', collapsed ? '1' : '0')
  }

  return {
    sidebarCollapsed,
    toggleSidebar,
    setSidebar
  }
})
