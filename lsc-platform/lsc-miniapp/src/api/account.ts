import { get } from './request'

export interface Account {
  userId: number
  lockedUnit: number
  availableUnit: number
  reservedUnit: number
  frozenLockedUnit: number
  frozenAvailableUnit: number
  lastEventSeq: number
}

export interface LedgerEvent {
  eventId: number
  eventType: string
  businessKey: string
  businessDate: string
  occurredAt: string
}

export function getAccount() {
  return get<Account>('/user/account')
}

export function listEvents(limit = 20) {
  return get<LedgerEvent[]>('/user/account/events', { limit })
}
