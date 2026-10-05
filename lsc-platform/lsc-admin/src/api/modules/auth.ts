import { post } from '@/api/request'

export interface LoginResult {
  token: string
  userInfo: {
    adminId: number
    name: string
    roles: string[]
  }
}

export function login(username: string, password: string): Promise<LoginResult> {
  return post<LoginResult>('/admin/auth/login', { username, password })
}

export function logout(): Promise<void> {
  return post('/admin/auth/logout')
}
