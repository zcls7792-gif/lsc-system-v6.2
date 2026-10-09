import { get, post } from '@/api/request'

export interface ConfigChange {
  changeId: number
  configGroup: string
  beforeVersion: number
  proposedJson: string
  requesterId: number
  approverId: number
  reason: string
  effectiveDate: string
  status: string
}

export interface AuditLog {
  logId: number
  actorId: number
  action: string
  resourceType: string
  resourceId: number
  result: string
  reason: string
  occurredAt: string
}

export function submitConfigChange(data: { configGroup: string; proposedJson: string; requesterId: number; reason?: string; effectiveDate: string }) {
  return post<number>('/admin/config-changes', data.proposedJson, { params: { ...data, proposedJson: undefined } })
}

export function approveConfigChange(changeId: number, approverId: number) {
  return post(`/admin/config-changes/${changeId}/approve`, null, { params: { approverId } })
}

export function rejectConfigChange(changeId: number, approverId: number, reason?: string) {
  return post(`/admin/config-changes/${changeId}/reject`, null, { params: { approverId, reason } })
}

export function applyConfigChange(changeId: number) {
  return post<number>(`/admin/config-changes/${changeId}/apply`)
}

export function getActiveConfig(configGroup: string) {
  return get<any>(`/admin/config/active`, { configGroup })
}

export function queryAuditLog(params: { actorId?: number; resourceType?: string; resourceId?: number; limit?: number }) {
  return get<AuditLog[]>('/admin/audit-logs', params)
}

export function requestGate(data: { gateCode: string; scope: string; evidenceRef?: string; ownerId: number }) {
  return post<string>('/admin/compliance-gates', null, { params: data })
}

export function approveGate(gateCode: string, approverId: number, ttlHours: number) {
  return post(`/admin/compliance-gates/${gateCode}/approve`, null, { params: { approverId, ttlHours } })
}

export function verifyGate(gateCode: string) {
  return get<boolean>(`/admin/compliance-gates/${gateCode}/verify`)
}
