import request from '@/utils/request'

/** LSC 账户（5桶） */
export interface LscAccount {
  lockedUnit: string
  availableUnit: string
  reservedUnit: string
  frozenLockedUnit: string
  frozenAvailableUnit: string
  pendingRecoveryUnit: string
  totalUnit: string
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

/** GrantLot */
export interface GrantLot {
  grantLotId: number
  userId: number
  sourceItemId: number
  originalGrantUnit: number
  remainingLockedUnit: number
  frozenLockedUnit: number
  releasedTotalUnit: number
  revokedLockedUnit: number
  remainderNanoUnit: number
  grantBusinessDate: string
  firstReleaseDate: string
  lastProcessedDate: string
  state: string
  refundHold: boolean
  ruleVersion: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/** 查询用户账户 */
export function getAccount(userId: number) {
  return request<LscAccount>({ url: `/v1/lsc/admin/account/${userId}`, method: 'GET' })
}

/** 查询用户事件明细 */
export function listEvents(userId: number, page = 1, size = 20) {
  return request<PageResult<LscEvent>>({ url: `/v1/lsc/events`, method: 'GET', params: { userId, page, size } })
}

/** 查询用户 GrantLot 列表 */
export function listGrantLots(userId: number, page = 1, size = 20) {
  return request<PageResult<GrantLot>>({ url: `/v1/lsc/grant-lots`, method: 'GET', params: { userId, page, size } })
}

/** 手动触发释放 */
export function manualRelease(userId: number, bizDate?: string, ratePpb = 750000) {
  return request({ url: `/v1/lsc/admin/release/${userId}`, method: 'POST', params: { bizDate, ratePpb } })
}

/** 手动触发过期 */
export function manualExpire(userId: number) {
  return request({ url: `/v1/lsc/admin/expire/${userId}`, method: 'POST' })
}
