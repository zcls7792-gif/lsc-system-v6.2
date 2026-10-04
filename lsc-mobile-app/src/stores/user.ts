import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  login as apiLogin,
  getProfile,
  type UserProfile,
} from '@/api/user'
import { getToken, setToken, clearToken } from '@/utils/auth'
import { AppConfig } from '@/config'
import { getLscAccount, type LscAccount } from '@/api/ledger'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const profile = ref<UserProfile | null>(null)
  const lscAccount = ref<LscAccount | null>(null)

  /** 是否已登录 */
  const isLoggedIn = computed(() => !!token.value)
  /** 是否商家 */
  const isMerchant = computed(() => profile.value?.userType === 'B')
  /** 可用 LSC（unit 字符串转 number） */
  const availableLsc = computed(() => Number(lscAccount.value?.availableUnit || '0'))

  /** 从本地存储恢复 token */
  function restore() {
    token.value = getToken()
    const cached = uni.getStorageSync(AppConfig.profileKey)
    if (cached) {
      try {
        profile.value = typeof cached === 'string' ? JSON.parse(cached) : cached
      } catch (e) {
        profile.value = null
      }
    }
  }

  function persistProfile(p: UserProfile | null) {
    profile.value = p
    if (p) {
      uni.setStorageSync(AppConfig.profileKey, JSON.stringify(p))
    } else {
      uni.removeStorageSync(AppConfig.profileKey)
    }
  }

  /** 手机号登录（V7.7.2 简化） */
  async function login(mobile: string) {
    const res = await apiLogin(mobile)
    token.value = res.token
    setToken(res.token)
    persistProfile({
      userId: res.userId,
      nickname: res.nickname,
      userType: res.userType as any,
      accountStatus: 'ACTIVE',
    })
    return res
  }

  /** 拉取用户信息 */
  async function fetchProfile() {
    const p = await getProfile()
    persistProfile(p)
    return p
  }

  /** 拉取 LSC 账户 */
  async function fetchLscAccount() {
    lscAccount.value = await getLscAccount()
    return lscAccount.value
  }

  /** 退出登录 */
  async function logout() {
    resetLocal()
  }

  function resetLocal() {
    token.value = ''
    profile.value = null
    lscAccount.value = null
    clearToken()
    uni.removeStorageSync(AppConfig.profileKey)
  }

  return {
    token,
    profile,
    lscAccount,
    isLoggedIn,
    isMerchant,
    availableLsc,
    restore,
    login,
    fetchProfile,
    fetchLscAccount,
    logout,
  }
})
