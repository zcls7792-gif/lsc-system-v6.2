import { http } from '@/utils/request'

/** 用户信息（V7.7.2） */
export interface UserProfile {
  userId: number
  nickname: string
  userType: 'UNVERIFIED' | 'C' | 'B'
  accountStatus: string
  referrerUserId?: number
}

export interface LoginResult {
  token: string
  userId: number
  nickname: string
  userType: string
}

/** 手机号登录（V7.7.2 简化） */
export function login(mobile: string) {
  return http.post<LoginResult>('/v1/users/login', { mobile }, { skipAuth: true })
}

/** 发送短信验证码（V7.7.2 暂无独立接口，占位） */
export function sendSmsCode(mobile: string): Promise<{ success: boolean }> {
  return Promise.resolve({ success: true })
}

/** 获取当前用户信息 */
export function getProfile() {
  return http.get<UserProfile>('/v1/users/me')
}

/** 绑定推荐人 */
export function bindReferral(userId: number, referrerUserId: number) {
  return http.post<{ referralId: number }>(`/v1/users/${userId}/referral`, undefined, { params: { referrerUserId } })
}

/** B端资质申请 */
export function applyBusiness(userId: number, profile: Record<string, any>) {
  return http.post<{ status: string }>(`/v1/users/${userId}/business`, profile)
}

/** 查询B端资质状态 */
export function getBusinessStatus(userId: number) {
  return http.get(`/v1/users/${userId}/business`)
}
