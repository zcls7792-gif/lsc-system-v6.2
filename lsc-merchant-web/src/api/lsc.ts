import { get } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { AvailableLscDetail, LscAccount, LscTransaction } from './types'
import { useMerchantStore } from '@/stores/merchant'

/** 获取当前商家 userId (从登录态读取) */
function merchantUserId(): number | undefined {
  return useMerchantStore().profile?.userId
}

/** LSC 账户余额 */
export function getLscAccount() {
  const userId = merchantUserId()
  return get<LscAccount>('/ledger/account/' + userId)
}

/** LSC 流水列表 */
export function getLscTransactions(params: {
  page?: number
  size?: number
  /** 流水类型 1-9 */
  type?: number
  startDate?: string
  endDate?: string
  orderNo?: string
}) {
  return get<PageResult<LscTransaction>>('/ledger/transactions', {
    userId: merchantUserId(),
    ...params
  })
}

/** 可用 LSC 明细列表 (按过期日) */
export function getAvailableDetails(params: {
  page?: number
  size?: number
  status?: number
}) {
  return get<PageResult<AvailableLscDetail>>('/ledger/available-details', {
    userId: merchantUserId(),
    ...params
  })
}

/** 近7天交易趋势 */
export interface TrendPoint {
  date: string
  /** 当日订单数 */
  orderCount: number
  /** 当日收入(元) */
  revenue: number
  /** 当日 LSC 收入 */
  lscIn: number
}

export function getRecentTrend(days = 7) {
  return get<TrendPoint[]>('/ledger/recent-trend', {
    userId: merchantUserId(),
    days
  })
}

/** 商家 LSC 概览 (锁定/可用/已核销/月收入等) */
export interface LscOverview {
  totalLocked: number
  totalAvailable: number
  totalUsed: number
  totalWrittenOff: number
  monthlyRevenue: number
}

export function getLscOverview() {
  const userId = merchantUserId()
  return get<LscOverview>('/ledger/overview/' + userId)
}
