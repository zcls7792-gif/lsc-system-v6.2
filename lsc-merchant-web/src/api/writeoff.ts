import { get, post } from '@/utils/request'
import { useMerchantStore } from '@/stores/merchant'
import type { PageResult } from '@/utils/request'
import type { MerchantNhRecord } from './types'

/** 获取当前商家 userId */
function merchantUserId(): number | undefined {
  return useMerchantStore().profile?.userId
}

/**
 * 核销档位信息
 * 1 LSC = 0.87 元 (100:87)
 */
export const WRITEOFF_RATE = 0.87

/** 申请核销入参 */
export interface ApplyWriteOffParams {
  /** 核销 LSC 数量 (正整数) */
  lscAmount: number
  /** 幂等键 */
  idempotentKey?: string
  remark?: string
}

/** 核销限额预览 */
export interface WriteOffQuota {
  /** 当日核销限额 (LSC) */
  dailyLimit: number
  /** 今日已核销 LSC */
  todayUsed: number
  /** 今日剩余可核销 LSC */
  todayRemaining: number
  /** 核销档位 1-16 (0 初始) */
  nhLimitLevel: number
  /** 1 LSC 兑换现金比例 */
  cashRate: number
  /** 监管账户余额 */
  regulatoryBalance: number
  /** 最近核销日期 */
  lastNhDate?: string
}

/** 核销限额预览 */
export function getWriteOffQuota() {
  return get<WriteOffQuota>('/writeoff/quota', {
    merchantId: merchantUserId()
  })
}

/** 申请核销 */
export function applyWriteOff(data: ApplyWriteOffParams) {
  return post<MerchantNhRecord>('/writeoff/apply', {
    merchantId: merchantUserId(),
    lscAmount: data.lscAmount
  })
}

/** 核销记录列表 */
export function getWriteOffRecords(params: {
  page?: number
  size?: number
  status?: number
  startDate?: string
  endDate?: string
}) {
  return get<PageResult<MerchantNhRecord>>('/writeoff/list', {
    merchantId: merchantUserId(),
    ...params
  })
}

/** 核销记录详情 */
export function getWriteOffRecordDetail(orderNo: string) {
  return get<MerchantNhRecord>(`/writeoff/${orderNo}`)
}

/**
 * 计算 LSC -> 现金 (100:87 向下取两位小数)
 */
export function calcCash(lscAmount: number): number {
  if (!lscAmount || lscAmount <= 0) return 0
  const cash = lscAmount * WRITEOFF_RATE
  return Math.floor(cash * 100) / 100
}
