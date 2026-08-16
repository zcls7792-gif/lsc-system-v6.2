import { http } from '@/utils/request'

/** LSC 账户 */
export interface LscAccount {
  /** 总余额 */
  total: number
  /** 可用余额 */
  available: number
  /** 锁定余额 */
  locked: number
  /** 已释放总量 */
  released: number
  /** 待释放总量 */
  pendingRelease: number
  /** 释放进度 0-100 */
  releaseProgress: number
  /** 今日释放量 */
  todayRelease: number
}

export interface LscTransaction {
  id: number
  /** 流水号 */
  txNo: string
  /** 类型 1-9 见 LscTransactionTypeEnum */
  type: number
  typeDesc: string
  /** 变动数量（正为收入，负为支出） */
  amount: number
  /** 变动后余额 */
  balance: number
  /** 备注 */
  remark?: string
  /** 关联订单号 */
  orderNo?: string
  createTime: string
}

export interface LscTxListParams {
  page?: number
  size?: number
  /** 类型筛选，-1 全部 */
  type?: number
  /** 起始时间 yyyy-MM-dd */
  startDate?: string
  endDate?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
}

/** LSC 账户余额 */
export function getLscAccount() {
  return http.get<LscAccount>('/api/ledger/account')
}

/** LSC 流水列表 */
export function getLscTransactions(params: LscTxListParams) {
  return http.get<PageResult<LscTransaction>>('/api/ledger/transactions', params)
}

/** LSC 流水类型枚举 */
export function getLscTxTypes() {
  return http.get<Array<{ code: number; desc: string }>>('/api/ledger/transaction-types')
}

/** 推广奖励概览 */
export function getPromotionSummary() {
  return http.get<{
    totalReward: number
    invitedCount: number
    activeCount: number
    referrerPhone?: string
    rules?: string
  }>('/api/ledger/promotion/summary')
}
