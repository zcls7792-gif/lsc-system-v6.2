/**
 * 鉴权 API：登录 / 当前用户
 */
import { get, post } from './request'

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
  return post<LoginResult>(`/auth/login/sms?phone=${encodeURIComponent(phone)}&code=${encodeURIComponent(code)}`)
}

/** 微信小程序登录（code 换 token） */
export function loginByWechat(code: string) {
  return post<LoginResult>(`/auth/login/wechat?code=${encodeURIComponent(code)}`)
}

/** 微信 H5 网页授权登录（OAuth2 code 换 token） */
export function loginByWechatH5(code: string) {
  return post<LoginResult>(`/auth/login/wechat/h5?code=${encodeURIComponent(code)}`)
}

/** 获取微信 H5 授权跳转 URL */
export function getWechatH5AuthUrl(redirectUri: string, state?: string) {
  return get<{ url: string }>(`/auth/wechat/h5/auth-url?redirectUri=${encodeURIComponent(redirectUri)}${state ? `&state=${state}` : ''}`)
}

/** 获取当前登录用户 */
export function getMe() {
  return get<UserInfo>('/auth/me')
}
