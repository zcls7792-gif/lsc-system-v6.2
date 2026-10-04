import { http } from '@/utils/request'

/** LSC 账户（V7.7.2 五桶模型） */
export interface LscAccount {
  /** 锁定余额（未释放）unit 字符串 */
  lockedUnit: string
  /** 可用余额 unit 字符串 */
  availableUnit: string
  /** 支付占用 unit 字符串 */
  reservedUnit: string
  /** 风险冻结-锁定来源 unit 字符串 */
  frozenLockedUnit: string
  /** 风险冻结-可用来源 unit 字符串 */
  frozenAvailableUnit: string
  /** 待追偿 unit 字符串 */
  pendingRecoveryUnit: string
  /** 总权益 unit 字符串 */
  totalUnit: string
  /** 风险冻结合计 unit 字符串 */
  frozenTotalUnit: string
}

/** LSC 权益事件 */
export interface LscEvent {
  eventId: number
  userId: number
  userEventSeq: number
  eventType: string
  businessKey: string
  orderId?: number
  refundId?: number
  caseId?: number
  businessDate: string
  occurredAt: string
  ruleVersion: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/** LSC 账户余额（5桶） */
export function getLscAccount() {
  return http.get<LscAccount>('/v1/lsc/account')
}

/** LSC 权益明细 */
export function getLscEvents(params: { page?: number; size?: number }) {
  return http.get<PageResult<LscEvent>>('/v1/lsc/events', params)
}

/** GrantLot 列表 */
export function getGrantLots(params: { page?: number; size?: number }) {
  return http.get<PageResult<any>>('/v1/lsc/grant-lots', params)
}

/** 推广汇总（V7.7.2 暂无独立接口，占位） */
export function getPromotionSummary(): Promise<any> {
  return Promise.resolve({
    inviteCount: 0,
    rewardCouponCount: 0,
    totalRewardAmount: 0,
    nextTier: '第2位 30元券',
  })
}
