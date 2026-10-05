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

export function getAccount(userId: number) {
  return get<Account>(`/admin/accounts/${userId}`)
}

export function listAccounts() {
  return get<Account[]>('/admin/accounts')
}
