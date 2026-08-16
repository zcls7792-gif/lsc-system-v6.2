import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  login as apiLogin,
  loginBySms as apiLoginBySms,
  register as apiRegister,
  getProfile,
  logout as apiLogout,
  submitVerify,
  type LoginParams,
  type RegisterParams,
  type VerifyParams,
  type UserProfile,
} from '@/api/user'
import { getToken, setToken, clearToken } from '@/utils/auth'
import { AppConfig } from '@/config'
import { getLscAccount, type LscAccount } from '@/api/ledger'

/** 实名状态枚举（与后端 verifyStatus 对齐） */
export const VerifyStatus = {
  UNVERIFIED: 0,
  VERIFIED: 1,
  AUDITING: 2,
  REJECTED: 3,
} as const

export const useUserStore = defineStore('user', () => {
  const token = ref<string>('')
  const profile = ref<UserProfile | null>(null)
  const lscAccount = ref<LscAccount | null>(null)

  /** 是否已登录 */
  const isLoggedIn = computed(() => !!token.value)
  /** 是否已实名 */
  const isVerified = computed(() => profile.value?.verifyStatus === VerifyStatus.VERIFIED)
  /** 是否商家 */
  const isMerchant = computed(() => profile.value?.userType === 2)
  /** 可用 LSC */
  const availableLsc = computed(() => lscAccount.value?.available ?? 0)

  /** 从本地存储恢复 token（App onLaunch 调用） */
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

  /** 密码登录 */
  async function login(params: LoginParams) {
    const res = await apiLogin(params)
    token.value = res.token
    setToken(res.token)
    persistProfile(res.userInfo)
    return res
  }

  /** 短信登录 */
  async function loginSms(phone: string, code: string) {
    const res = await apiLoginBySms(phone, code)
    token.value = res.token
    setToken(res.token)
    persistProfile(res.userInfo)
    return res
  }

  /** 注册 */
  async function register(params: RegisterParams) {
    const res = await apiRegister(params)
    token.value = res.token
    setToken(res.token)
    persistProfile(res.userInfo)
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

  /** 提交实名认证 */
  async function doVerify(params: VerifyParams) {
    const p = await submitVerify(params)
    persistProfile(p)
    return p
  }

  /** 退出登录（调接口） */
  async function logout() {
    try {
      if (token.value) await apiLogout()
    } catch (e) {
      // 忽略退出接口失败
    } finally {
      resetLocal()
    }
  }

  /** 静默退出（token 失效时） */
  function logoutSilent() {
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
    isVerified,
    isMerchant,
    availableLsc,
    restore,
    login,
    loginSms,
    register,
    fetchProfile,
    fetchLscAccount,
    doVerify,
    logout,
    logoutSilent,
  }
})
