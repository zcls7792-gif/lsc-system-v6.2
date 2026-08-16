import { request } from '@/utils/request'

export interface ReconcileParams {
  page?: number
  size?: number
  /** 对账日期 yyyy-MM-dd */
  date?: string
  /** 对账状态 0进行中 1一致 2有差异 3失败 */
  status?: number | string
}

/** 对账报告分页列表 */
export function getReconcileReport(params: ReconcileParams) {
  return request({
    url: '/reconcile/report',
    method: 'get',
    params
  })
}

/** 对账报告详情(单日) */
export function getReconcileDetail(date: string) {
  return request({
    url: `/reconcile/report/${date}`,
    method: 'get'
  })
}

/** 触发对账(JSON body, 兼容 daily) */
export function triggerReconcile(data: { date?: string }) {
  return request({
    url: '/reconcile/trigger',
    method: 'post',
    data
  })
}
