import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as loginApi } from '@/api/modules/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('lsc_token') || '')
  const userInfo = ref<{ adminId: number; name: string; roles: string[] } | null>(null)

  const isLoggedIn = computed(() => !!token.value)

  async function login(username: string, password: string) {
    const res = await loginApi(username, password)
    token.value = res.token
    userInfo.value = res.userInfo
    localStorage.setItem('lsc_token', res.token)
    return res
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('lsc_token')
  }

  return { token, userInfo, isLoggedIn, login, logout }
})
