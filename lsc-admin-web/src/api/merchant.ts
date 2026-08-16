import { request } from '@/utils/request'

export interface MerchantListParams {
  page?: number
  size?: number
  keyword?: string
  status?: string
  creditMin?: string | number
  creditMax?: string | number
}

/** 商家列表 */
export function getMerchantList(params: MerchantListParams) {
  return request({
    url: '/merchant/list',
    method: 'get',
    params
  })
}

/** 商家详情 */
export function getMerchantDetail(id: number) {
  return request({
    url: `/merchant/${id}`,
    method: 'get'
  })
}

/** 待审核商家列表 */
export function getAuditList(params: Record<string, any>) {
  return request({
    url: '/merchant/audit/list',
    method: 'get',
    params
  })
}

/** 商家审核 */
export function auditMerchant(id: number, data: { status: string; reason?: string }) {
  return request({
    url: `/merchant/audit/${id}`,
    method: 'post',
    data
  })
}

/** 信用分明细 */
export function getCreditDetail(id: number) {
  return request({
    url: `/merchant/${id}/credit`,
    method: 'get'
  })
}

/** 违规记录 */
export function getViolationLogs(params: Record<string, any>) {
  return request({
    url: '/merchant/violation/logs',
    method: 'get',
    params
  })
}

/** 商家处罚 */
export function penalizeMerchant(id: number, data: { type: string; reason: string; days?: number }) {
  return request({
    url: `/merchant/${id}/penalty`,
    method: 'post',
    data
  })
}

/** 调整信用分 */
export function adjustCredit(id: number, data: { delta: number; reason: string }) {
  return request({
    url: `/merchant/${id}/credit/adjust`,
    method: 'post',
    data
  })
}
