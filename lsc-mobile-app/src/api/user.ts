import { http } from '@/utils/request'

/** 用户信息 */
export interface UserProfile {
  id: number
  phone: string
  nickname: string
  avatar?: string
  /** 0-未实名 1-已实名 2-审核中 3-驳回 */
  verifyStatus: number
  realName?: string
  idCard?: string
  /** 推荐人手机号 */
  referrerPhone?: string
  /** 用户类型 1-普通用户 2-商家 */
  userType: number
  createTime?: string
}

export interface LoginResult {
  token: string
  userInfo: UserProfile
}

export interface LoginParams {
  account: string
  password: string
  /** 验证码（短信登录时使用） */
  code?: string
  /** 登录方式: password | sms */
  loginType?: 'password' | 'sms'
}

export interface RegisterParams {
  phone: string
  password: string
  /** 推荐人手机号（可选） */
  referrerPhone?: string
  /** 短信验证码 */
  code: string
}

export interface VerifyParams {
  realName: string
  idCard: string
  /** 人脸识别凭证（由 uniapp 人脸采集 SDK 返回） */
  faceToken?: string
}

/** 手机号密码登录 */
export function login(data: LoginParams) {
  return http.post<LoginResult>('/api/user/login', data, { skipAuth: true })
}

/** 短信验证码登录 */
export function loginBySms(phone: string, code: string) {
  return http.post<LoginResult>(
    '/api/user/login/sms',
    { phone, code, loginType: 'sms' },
    { skipAuth: true },
  )
}

/** 发送短信验证码 */
export function sendSmsCode(phone: string, scene: 'login' | 'register' | 'reset' = 'login') {
  return http.post<void>('/api/user/sms/send', { phone, scene }, { skipAuth: true })
}

/** 注册 */
export function register(data: RegisterParams) {
  return http.post<LoginResult>('/api/user/register', data, { skipAuth: true })
}

/** 退出登录 */
export function logout() {
  return http.post<void>('/api/user/logout')
}

/** 获取当前用户信息 */
export function getProfile() {
  return http.get<UserProfile>('/api/user/profile')
}

/** 实名认证 */
export function submitVerify(data: VerifyParams) {
  return http.post<UserProfile>('/api/user/verify', data)
}

/** 修改昵称/头像 */
export function updateProfile(data: Partial<Pick<UserProfile, 'nickname' | 'avatar'>>) {
  return http.put<UserProfile>('/api/user/profile', data)
}

/** 修改密码 */
export function changePassword(oldPassword: string, newPassword: string) {
  return http.post<void>('/api/user/password/change', { oldPassword, newPassword })
}

/** 重置密码（短信验证码） */
export function resetPassword(phone: string, code: string, newPassword: string) {
  return http.post<void>('/api/user/password/reset', { phone, code, newPassword }, { skipAuth: true })
}
