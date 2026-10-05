import { get } from '@/api/request'

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
  userId: number
  eventType: string
  businessKey: string
  businessDate: string
  occurredAt: string
}

export function getAccount(userId: number) {
  return get<Account>(`/admin/accounts/${userId}`)
}

export function listAccounts(params?: { limit?: number; offset?: number }) {
  return get<Account[]>('/admin/accounts', params)
}

export function listEvents(userId: number, params?: { limit?: number }) {
  return get<LedgerEvent[]>(`/admin/accounts/${userId}/events`, params)
}
