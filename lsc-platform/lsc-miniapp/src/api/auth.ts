/**
 * 鉴权 API：登录 / 当前用户
 */
import { request } from './request'

export interface LoginResult {
  token: string
  userId: number
  expiresIn: number
}

export interface UserInfo {
  userId: number
  phone?: string
  userType?: string
  status?: string
}

/** 短信验证码登录（开发期验证码固定 1234） */
export function loginBySms(phone: string, code: string) {
  // 后端用 @RequestParam，需走 query 参数
  return request<LoginResult>({
    url: `/auth/login/sms?phone=${encodeURIComponent(phone)}&code=${encodeURIComponent(code)}`,
    method: 'POST'
  })
}

/** 微信小程序登录（code 换 token） */
export function loginByWechat(code: string) {
  return request<LoginResult>({
    url: `/auth/login/wechat?code=${encodeURIComponent(code)}`,
    method: 'POST'
  })
}

/** 获取当前登录用户 */
export function getMe() {
  return request<UserInfo>({ url: '/auth/me' })
}
