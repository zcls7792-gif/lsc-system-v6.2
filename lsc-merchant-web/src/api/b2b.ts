import { get, post } from '@/utils/request'
import { useMerchantStore } from '@/stores/merchant'
import type { PageResult } from '@/utils/request'
import type { B2BOrder } from './types'

/** 获取当前商家 userId */
function merchantUserId(): number | undefined {
  return useMerchantStore().profile?.userId
}

/** B2B 订单列表查询 */
export interface B2BListQuery {
  page?: number
  size?: number
  /** 角色: initiator 发起的 / counterparty 接收的 / all 全部 */
  role?: 'initiator' | 'counterparty' | 'all'
  /** 0待确认 1已确认 2已流转 3已完成 4已取消 5已作废 */
  status?: number
  /** 订单号 */
  orderNo?: string
  /** 起始日期 */
  startDate?: string
  /** 结束日期 */
  endDate?: string
}

/** 发起 B2B 交易入参 */
export interface CreateB2BParams {
  /** 对手方用户ID / 手机号 */
  counterparty: string
  tradeDescription: string
  /** 交易总金额(元) — 自动 1:1 换算为 LSC 数量 */
  totalAmountRmb: number
  /** 合同编号 */
  contractNo?: string
  /** 贸易凭证图片 URL 数组 */
  tradeEvidenceUrls: string[]
}

/** B2B 订单分页列表 */
export function getB2BList(params: B2BListQuery) {
  return get<PageResult<B2BOrder>>('/b2b/list', {
    userId: merchantUserId(),
    ...params
  })
}

/** B2B 订单详情 */
export function getB2BDetail(orderNo: string) {
  return get<B2BOrder>(`/b2b/${orderNo}`)
}

/** 发起 B2B 交易 */
export function createB2B(data: CreateB2BParams) {
  const userId = merchantUserId()
  return post<B2BOrder>('/b2b/create', {
    initiatorId: userId,
    counterpartyId: data.counterparty,
    tradeDescription: data.tradeDescription,
    totalAmountRmb: data.totalAmountRmb,
    lscAmount: data.totalAmountRmb,
    contractNo: data.contractNo,
    tradeEvidenceUrls: JSON.stringify(data.tradeEvidenceUrls || [])
  })
}

/** 接收方确认 B2B 交易 */
export function confirmB2B(orderNo: string) {
  return post<void>('/b2b/confirm', {
    orderNo,
    confirmerId: merchantUserId()
  })
}

/** 发起方取消 B2B 交易 */
export function cancelB2B(orderNo: string, reason?: string) {
  return post<void>('/b2b/cancel', {
    orderNo,
    operatorId: merchantUserId(),
    reason
  })
}

/** 标记 B2B 完成 (对手方已收到货 / 履约完成) */
export function completeB2B(orderNo: string) {
  return post<void>('/b2b/complete', {
    orderNo,
    operatorId: merchantUserId()
  })
}
