import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export interface AdminInfo {
  id: number
  username: string
  realName: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem('lsc_admin_token') || '')
  const role = ref<string>(localStorage.getItem('lsc_admin_role') || '')
  const userInfo = ref<AdminInfo | null>(
    JSON.parse(localStorage.getItem('lsc_admin_user') || 'null')
  )

  const roles = computed(() => (userInfo.value ? [userInfo.value.role] : []))
  const isSuperAdmin = computed(() => role.value === 'super_admin')

  function setToken(t: string) {
    token.value = t
    localStorage.setItem('lsc_admin_token', t)
  }

  function setRole(r: string) {
    role.value = r
    localStorage.setItem('lsc_admin_role', r)
  }

  function setUserInfo(info: AdminInfo) {
    userInfo.value = info
    localStorage.setItem('lsc_admin_user', JSON.stringify(info))
  }

  function logout() {
    token.value = ''
    role.value = ''
    userInfo.value = null
    localStorage.removeItem('lsc_admin_token')
    localStorage.removeItem('lsc_admin_role')
    localStorage.removeItem('lsc_admin_user')
  }

  return {
    token,
    role,
    userInfo,
    roles,
    isSuperAdmin,
    setToken,
    setRole,
    setUserInfo,
    logout
  }
})
