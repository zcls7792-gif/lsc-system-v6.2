import { get } from '@/utils/request'
import type { PageResult } from '@/utils/request'
import type { AvailableLscDetail, LscAccount, LscTransaction } from './types'
import { useMerchantStore } from '@/stores/merchant'

/** 获取当前商家 userId (从登录态读取) */
function merchantUserId(): number | undefined {
  return useMerchantStore().profile?.userId
}

/** LSC 账户余额（V7.7.2 五桶模型） */
export function getLscAccount() {
  return get<LscAccount>('/v1/lsc/account')
}

/** LSC 权益事件明细（V7.7.2） */
export function getLscTransactions(params: {
  page?: number
  size?: number
}) {
  return get<PageResult<LscTransaction>>('/v1/lsc/events', {
    ...params
  })
}

/** GrantLot 列表（V7.7.2） */
export function getAvailableDetails(params: {
  page?: number
  size?: number
}) {
  return get<PageResult<AvailableLscDetail>>('/v1/lsc/grant-lots', {
    ...params
  })
}

/** 近7天交易趋势（V7.7.2 暂无独立接口，返回空数组占位） */
export interface TrendPoint {
  date: string
  /** 当日订单数 */
  orderCount: number
  /** 当日收入(元) */
  revenue: number
  /** 当日 LSC 收入 */
  lscIn: number
}

export function getRecentTrend(_days = 7): Promise<TrendPoint[]> {
  return Promise.resolve([])
}

/** 商家 LSC 概览（V7.7.2 从五桶账户派生） */
export interface LscOverview {
  totalLocked: number
  totalAvailable: number
  totalUsed: number
  totalWrittenOff: number
  monthlyRevenue: number
}

export async function getLscOverview(): Promise<LscOverview> {
  const acc = await getLscAccount()
  const unitToLsc = (s?: string) => Number(s || '0') / 10000
  return {
    totalLocked: unitToLsc(acc.lockedUnit),
    totalAvailable: unitToLsc(acc.availableUnit),
    totalUsed: 0,
    totalWrittenOff: 0,
    monthlyRevenue: 0,
  }
}
