import { request } from '@/utils/request'

export interface ReleaseSummaryParams {
  page?: number
  size?: number
  startDate?: string
  endDate?: string
  status?: number
}

/** 释放汇总列表(日期范围分页) */
export function getReleaseSummary(params: ReleaseSummaryParams) {
  return request({
    url: '/release/summary',
    method: 'get',
    params
  })
}

/** 释放配置列表 */
export function getReleaseConfigList() {
  return request({
    url: '/release/config',
    method: 'get'
  })
}

/** 提交参数变更申请(双人审批第一步) */
export function applyParamChange(data: {
  configKey: string
  configValue: string
  operator: string
  evidenceTxHash?: string
}) {
  return request({
    url: '/release/param-approval',
    method: 'post',
    data
  })
}

/** 审批参数变更(双人审批第二步) */
export function approveParamChange(data: {
  approvalId: number
  approver: string
  approverSignatures?: string
  approveComment?: string
  approved: boolean
}) {
  return request({
    url: '/release/param-approve',
    method: 'post',
    data
  })
}

/** 修改释放配置(走双人审批流程) */
export function updateReleaseConfig(id: number, data: { configKey: string; configValue: string; operator: string }) {
  return applyParamChange({
    configKey: data.configKey,
    configValue: data.configValue,
    operator: data.operator
  })
}

/** AI趋势预测 */
export function getReleasePredict(params: { days?: number }) {
  return request({
    url: '/release/predict',
    method: 'get',
    params
  })
}

/** 仿真推演 */
export function runSimulation(data: Record<string, any>) {
  return request({
    url: '/release/simulation',
    method: 'post',
    data
  })
}

/** 释放趋势图表数据 */
export function getReleaseTrend(params: { days?: number }) {
  return request({
    url: '/release/trend',
    method: 'get',
    params
  })
}
