import { request } from '@/utils/request'

export interface WriteoffListParams {
  page?: number
  size?: number
  /** 核销订单号模糊匹配 */
  batchNo?: string
  /** 核销状态 0待处理 1处理中 2成功 3失败 */
  status?: number | string
  merchantId?: number | string
  startDate?: string
  endDate?: string
}

/** 核销记录列表 */
export function getWriteoffList(params: WriteoffListParams) {
  return request({
    url: '/writeoff/list',
    method: 'get',
    params
  })
}

/** 核销记录详情(按核销订单号) */
export function getWriteoffDetail(orderNo: string) {
  return request({
    url: `/writeoff/${orderNo}`,
    method: 'get'
  })
}

/** 核销记录详情(按主键ID, 兼容) */
export function getWriteoffDetailById(id: number) {
  return request({
    url: `/writeoff/by-id/${id}`,
    method: 'get'
  })
}

/** 核销统计 */
export function getWriteoffStats(params: { merchantId?: number; startDate?: string; endDate?: string }) {
  return request({
    url: '/writeoff/stats',
    method: 'get',
    params
  })
}
