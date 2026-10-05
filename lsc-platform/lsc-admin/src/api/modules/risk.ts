import { get } from '@/api/request'

export interface RiskCase {
  caseId: number
  userId: number
  ruleId: string
  aiFlag: number
  evidenceRef: string
  proposedAction: string
  reviewStatus: string
  reviewDeadline: string
  reviewerId: number
  decision: string
  decidedAt: string
}

export function findOverdueCases() {
  return get<number[]>('/admin/risk/cases/overdue')
}

export function findBlockingCases(userId: number) {
  return get<number[]>(`/admin/risk/cases/blocking?userId=${userId}`)
}
