import { request } from '@/utils/request'

export interface AdminLoginParams {
  username: string
  password: string
}

export interface AdminListParams {
  page?: number
  size?: number
  keyword?: string
  role?: string
}

/** 管理员登录 */
export function login(data: AdminLoginParams) {
  return request({
    url: '/admin/login',
    method: 'post',
    data
  })
}

/** 获取当前管理员信息 */
export function getAdminInfo() {
  return request({
    url: '/admin/info',
    method: 'get'
  })
}

/** 退出登录 */
export function logout() {
  return request({
    url: '/admin/logout',
    method: 'post'
  })
}

/** 管理员列表 */
export function getAdminList(params: AdminListParams) {
  return request({
    url: '/admin/list',
    method: 'get',
    params
  })
}

/** 新增管理员 */
export function addAdmin(data: Record<string, any>) {
  return request({
    url: '/admin',
    method: 'post',
    data
  })
}

/** 修改管理员 */
export function updateAdmin(id: number, data: Record<string, any>) {
  return request({
    url: `/admin/${id}`,
    method: 'put',
    data
  })
}

/** 删除管理员 */
export function deleteAdmin(id: number) {
  return request({
    url: `/admin/${id}`,
    method: 'delete'
  })
}

/** 操作审计日志 */
export function getAuditLogs(params: Record<string, any>) {
  return request({
    url: '/admin/audit/logs',
    method: 'get',
    params
  })
}
