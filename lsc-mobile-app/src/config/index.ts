/**
 * 应用运行时配置
 * baseURL 优先从 import.meta.env 读取（H5/构建期注入），
 * 小程序/APP 无 Vite env 时回退到本地常量。
 */

function readEnv(): string {
  try {
    // @ts-ignore - H5/Vite 环境下存在
    if (typeof import.meta !== 'undefined' && import.meta.env && import.meta.env.VITE_API_BASE_URL) {
      // @ts-ignore
      return import.meta.env.VITE_API_BASE_URL as string
    }
  } catch (e) {
    // ignore
  }
  return 'https://api.lianshengtong.com'
}

export const AppConfig = {
  /** 接口基础地址 */
  baseURL: readEnv(),
  /** 应用名称 */
  appName: '链生通',
  /** 请求超时(ms) */
  timeout: 15000,
  /** Token 在 Storage 中的 key */
  tokenKey: 'LSC_TOKEN',
  /** 用户信息在 Storage 中的 key */
  profileKey: 'LSC_PROFILE',
  /** LSC 与人民币兑换比 1:1 */
  lscToRmbRate: 1,
  /** 客服微信 */
  serviceWechat: 'lsc_kefu_001',
}

export type AppConfigType = typeof AppConfig
