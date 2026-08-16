import { AppConfig } from '@/config'

/**
 * Token 与用户本地存储管理
 * 兼容小程序(uni.setStorageSync)与H5
 */
export function getToken(): string {
  try {
    return uni.getStorageSync(AppConfig.tokenKey) || ''
  } catch (e) {
    return ''
  }
}

export function setToken(token: string): void {
  if (!token) {
    clearToken()
    return
  }
  uni.setStorageSync(AppConfig.tokenKey, token)
}

export function clearToken(): void {
  try {
    uni.removeStorageSync(AppConfig.tokenKey)
  } catch (e) {
    // ignore
  }
}

/** 判断是否已登录 */
export function isLoggedIn(): boolean {
  return !!getToken()
}

/**
 * 未登录拦截：跳转登录页
 * @param redirect 登录后回跳地址
 */
export function ensureLogin(redirect?: string): boolean {
  if (isLoggedIn()) return true
  const url = redirect ? `/src/pages-account/login/index?redirect=${encodeURIComponent(redirect)}` : '/src/pages-account/login/index'
  uni.navigateTo({ url })
  return false
}
