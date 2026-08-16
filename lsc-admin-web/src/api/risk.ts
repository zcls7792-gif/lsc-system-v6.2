import { request } from '@/utils/request'

export interface RiskLogParams {
  page?: number
  size?: number
  /** 风险等级 0低 1中 2高 (后端字段 riskLevel, 整数) */
  riskLevel?: number
  /** 处理状态 (后端字段 handleStatus, 整数) */
  handleStatus?: number
  /** 用户ID */
  userId?: number
}

/** 风控日志列表 */
export function getRiskLogs(params: RiskLogParams) {
  return request({
    url: '/risk/logs',
    method: 'get',
    params
  })
}

/** 风控仪表盘统计 */
export function getRiskDashboard() {
  return request({
    url: '/risk/dashboard',
    method: 'get'
  })
}

/** 风控事件详情 */
export function getRiskDetail(id: number) {
  return request({
    url: `/risk/logs/${id}`,
    method: 'get'
  })
}

/** 处理风控事件 */
export function handleRiskEvent(id: number, data: { action: string; remark?: string }) {
  return request({
    url: `/risk/logs/${id}/handle`,
    method: 'post',
    data
  })
}
