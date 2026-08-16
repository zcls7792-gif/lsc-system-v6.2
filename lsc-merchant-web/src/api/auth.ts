import { get, post } from '@/utils/request'
import { useMerchantStore } from '@/stores/merchant'
import type { MerchantExtension, MerchantViolation } from './types'

/** 商家登录入参 */
export interface LoginParams {
  /** 手机号 */
  mobile: string
  /** 密码 (明文，由后端哈希校验) */
  password: string
}

/** 商家登录返回 (后端 /user/login 仅返回 token, 商家信息需登录后调 /merchant/info) */
export interface LoginResult {
  token: string
  merchant: {
    userId: number
    mobile: string
    storeName?: string
    nickname?: string
    avatarUrl?: string
    auditStatus?: number
    isSignedSupervision?: number
  }
}

/** 获取当前商家 userId (从登录态读取) */
function merchantUserId(): number | undefined {
  return useMerchantStore().profile?.userId
}

/**
 * 商家登录
 * <p>后端 /user/login 仅返回 token 字符串, 登录成功后需调 /merchant/info 获取商家信息。</p>
 */
export async function login(data: LoginParams): Promise<LoginResult> {
  const token = await post<string>('/user/login', {
    account: data.mobile,
    password: data.password
  })
  // 将 token 暂存, 以便后续请求携带
  useMerchantStore().setAuth(token, { userId: 0, mobile: data.mobile })
  // 获取商家信息
  let merchant: LoginResult['merchant'] = { userId: 0, mobile: data.mobile }
  try {
    // 从 token 解析 userId (JWT payload 第二段)
    const payload = JSON.parse(atob(token.split('.')[1]))
    const userId = Number(payload.userId || payload.sub || 0)
    if (userId > 0) {
      const ext = await get<MerchantExtension>('/merchant/info', { merchantId: userId })
      merchant = {
        userId,
        mobile: data.mobile,
        storeName: ext.storeName,
        auditStatus: ext.auditStatus,
        isSignedSupervision: ext.isSignedSupervision
      }
    }
  } catch (e) {
    // token 解析失败, 仅返回基础信息
    console.warn('登录后获取商家信息失败', e)
  }
  // 写入完整登录态
  useMerchantStore().setAuth(token, merchant)
  return { token, merchant }
}

/** 退出登录 (后端记录审计/黑名单, 前端清理本地态) */
export function logout() {
  return post<void>('/user/logout').catch(() => {
    // 后端 logout 失败不阻塞前端退出
  })
}

/** 获取当前商家信息 (扩展表) */
export function getMerchantProfile() {
  const userId = merchantUserId()
  return get<MerchantExtension>('/merchant/info', { merchantId: userId })
}

/** 修改登录密码 */
export function changePassword(data: { oldPassword: string; newPassword: string }) {
  return post<void>('/user/change-password', data)
}

/** 商家违规记录列表 (信用管理相关) */
export function getMerchantViolations(params?: { page?: number; size?: number }) {
  return get<MerchantViolation[]>('/merchant/violation/logs', {
    merchantId: merchantUserId(),
    ...params
  })
}
