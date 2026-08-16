import { request } from '@/utils/request'

export interface EvidenceListParams {
  page?: number
  size?: number
  batchNo?: string
  hash?: string
  txId?: string
  startDate?: string
  endDate?: string
}

/** 存证记录列表 */
export function getEvidenceList(params: EvidenceListParams) {
  return request({
    url: '/evidence/list',
    method: 'get',
    params
  })
}

/** 存证详情 */
export function getEvidenceDetail(id: number) {
  return request({
    url: `/evidence/${id}`,
    method: 'get'
  })
}

/** 存证校验报告 */
export function verifyEvidence(params: { date: string }) {
  return request({
    url: '/evidence/verify',
    method: 'post',
    data: params
  })
}

/** 按日期校验存证 */
export function getVerifyReport(date: string) {
  return request({
    url: '/evidence/verify-report',
    method: 'get',
    params: { date }
  })
}
