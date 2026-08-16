import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 商家登录信息
 */
export interface MerchantProfile {
  /** 商家用户ID (雪花算法) */
  userId: number
  /** 手机号 */
  mobile: string
  /** 门店名称 */
  storeName?: string
  /** 昵称 */
  nickname?: string
  /** 头像URL */
  avatarUrl?: string
  /** 商家审核状态 0待审核 1通过 2拒绝 */
  auditStatus?: number
  /** 是否签署监管协议 0否 1是 */
  isSignedSupervision?: number
}

const TOKEN_KEY = 'lsc_merchant_token'
const PROFILE_KEY = 'lsc_merchant_profile'

export const useMerchantStore = defineStore('merchant', () => {
  const token = ref<string>('')
  const profile = ref<MerchantProfile | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const storeName = computed(() => profile.value?.storeName || profile.value?.nickname || '商家')
  const avatar = computed(() => profile.value?.avatarUrl || '')

  /** 写入登录态 */
  function setAuth(t: string, p: MerchantProfile) {
    token.value = t
    profile.value = p
    localStorage.setItem(TOKEN_KEY, t)
    localStorage.setItem(PROFILE_KEY, JSON.stringify(p))
  }

  /** 更新商家资料 (不覆盖 token) */
  function updateProfile(p: Partial<MerchantProfile>) {
    if (!profile.value) return
    profile.value = { ...profile.value, ...p }
    localStorage.setItem(PROFILE_KEY, JSON.stringify(profile.value))
  }

  /** 从本地存储恢复登录态 */
  function restore() {
    if (token.value) return
    const t = localStorage.getItem(TOKEN_KEY)
    const p = localStorage.getItem(PROFILE_KEY)
    if (t) token.value = t
    if (p) {
      try {
        profile.value = JSON.parse(p) as MerchantProfile
      } catch {
        profile.value = null
      }
    }
  }

  /** 退出登录 */
  function logout() {
    token.value = ''
    profile.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(PROFILE_KEY)
  }

  return {
    token,
    profile,
    isLoggedIn,
    storeName,
    avatar,
    setAuth,
    updateProfile,
    restore,
    logout
  }
})
