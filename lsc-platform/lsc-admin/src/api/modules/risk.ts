import { get, post } from '@/api/request'

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

export function createRiskCase(data: { userId: number; ruleId?: string; aiFlag?: boolean; evidenceRef?: string; proposedAction: string }) {
  return post<number>('/admin/risk/cases', null, { params: data })
}

export function reviewCase(caseId: number, reviewerId: number, decision: string, reason?: string) {
  return post(`/admin/risk/cases/${caseId}/review`, null, { params: { reviewerId, decision, reason } })
}

export function findOverdueCases() {
  return get<number[]>('/admin/risk/cases/overdue')
}

export function findBlockingCases(userId: number) {
  return get<number[]>(`/admin/risk/cases/blocking`, { userId })
}

export function submitAppeal(caseId: number, userId: number) {
  return post<number>('/user/appeals', null, { params: { caseId, userId } })
}

export function replyAppeal(appealId: number, reviewerId: number, decision: string, decisionRef?: string) {
  return post(`/admin/appeals/${appealId}/reply`, null, { params: { reviewerId, decision, decisionRef } })
}

export function findOverdueAppeals() {
  return get<number[]>('/admin/appeals/overdue')
}
