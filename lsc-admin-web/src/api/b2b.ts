import { request } from '@/utils/request'

export interface B2BListParams {
  page?: number
  size?: number
  orderNo?: string
  /** 订单状态 0待确认 1已确认 2已流转 3已完成 4已取消 5已作废 */
  status?: number | string
  /** 发起方/接收方ID */
  userId?: number
  startDate?: string
  endDate?: string
}

/** B2B订单列表 */
export function getB2BList(params: B2BListParams) {
  return request({
    url: '/b2b/list',
    method: 'get',
    params
  })
}

/** B2B订单详情 */
export function getB2BDetail(orderNo: string) {
  return request({
    url: `/b2b/${orderNo}`,
    method: 'get'
  })
}

/** AI核验结果 */
export function getB2BVerifyResult(orderNo: string) {
  return request({
    url: `/b2b/${orderNo}/verify-result`,
    method: 'get'
  })
}

/** 查看贸易凭证 */
export function getTradeDocuments(orderNo: string) {
  return request({
    url: `/b2b/${orderNo}/documents`,
    method: 'get'
  })
}

/** 人工确认核验 */
export function confirmB2BVerify(orderNo: string, data: { result: boolean | string; remark?: string }) {
  return request({
    url: `/b2b/${orderNo}/verify-confirm`,
    method: 'post',
    data
  })
}
