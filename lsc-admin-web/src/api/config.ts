import request from '@/utils/request'

/** 配置变更请求 */
export interface ConfigChangeRequest {
  changeId: number
  configGroup: string
  beforeVersion: number
  proposedJson: string
  requesterId: number
  approverId?: number
  reason: string
  effectiveDate: string
  status: string
  approvalAt?: string
}

/** 配置版本 */
export interface ConfigVersion {
  configGroup: string
  version: number
  payloadJson: string
  schemaVersion: number
  checksum: string
  effectiveAt: string
  changeId: number
  status: string
}

/** 释放参数 */
export interface ReleaseConfig {
  wMinPpm: number
  wMaxPpm: number
  rMinPpb: number
  rMaxPpb: number
}

/** 获取当前释放参数 */
export function getReleaseConfig() {
  return request<ReleaseConfig>({ url: '/v1/admin/config/release', method: 'GET' })
}

/** 发起配置变更 */
export function createConfigChange(data: {
  configGroup: string
  proposedJson: string
  reason: string
  effectiveDate: string
}) {
  return request({ url: '/v1/admin/config-changes', method: 'POST', data })
}

/** 审批配置变更 */
export function approveConfigChange(changeId: number) {
  return request({ url: `/v1/admin/config-changes/${changeId}/approve`, method: 'POST' })
}

/** 查询配置变更列表 */
export function listConfigChanges(configGroup?: string) {
  return request({ url: '/v1/admin/config-changes', method: 'GET', params: { configGroup } })
}
