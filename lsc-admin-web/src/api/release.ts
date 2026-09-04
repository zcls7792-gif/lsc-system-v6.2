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

/* ======================================================================
 * 灰度发布审批工作流 API（Phase M 后端 lsc-release-service GrayApprovalController）
 * 单一真源：后端 @RequestMapping("${api.prefix:}/release/gray")
 * 前端 axios baseURL = /api → 完整路径 /api/release/gray/**
 * ====================================================================== */

/** flowType: 审批单覆盖的 4 类灰度动作 */
export type GrayApprovalFlowType = 'GRADUATE' | 'WEIGHT_CHANGE' | 'ROLLBACK' | 'LAUNCH'

/** 主流程 8 状态（与后端 GrayApprovalFlow.Status 严格一致） */
export type GrayApprovalFlowStatus =
  | 'DRAFT' | 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED'
  | 'CANCELLED' | 'EXECUTING' | 'SUCCEEDED' | 'EXECUTE_FAILED'

/** 审批节点 4 状态 */
export type GrayApprovalNodeStatus = 'WAITING' | 'APPROVED' | 'REJECTED' | 'SKIPPED'

/** 审计流水 action（后端 10+ 动作） */
export type GrayApprovalAuditAction =
  | 'FLOW_CREATED' | 'FLOW_SUBMITTED' | 'NODE_APPROVED' | 'FLOW_APPROVED'
  | 'FLOW_REJECTED' | 'FLOW_CANCELLED' | 'FLOW_EXECUTING' | 'FLOW_SUCCEEDED'
  | 'FLOW_EXECUTE_FAILED' | string

/** 后端 R<T> 通用响应（request.ts 返回原始 {code, message, data}） */
export interface R<T = unknown> {
  code: number
  message: string
  data: T
}

/** flow 基础信息（list/detail 通用） */
export interface GrayApprovalFlowVO {
  id: number
  flowNo: string
  flowType: GrayApprovalFlowType
  policyId: string
  applicant: string
  title: string
  applyReason: string | null
  status: GrayApprovalFlowStatus
  requiredApprovals: number
  approvedCount: number
  totalNodes: number
  payloadJson: string | null // JSON 字符串：前端解析显示
  executeResponse: string | null
  executeCostMs: number | null
  approvedAt: string | null
  createdAt: string
  updatedAt: string
  updatedBy: string | null
}

/** 审批节点信息 */
export interface GrayApprovalNodeVO {
  id: number
  flowId: number
  nodeOrder: number
  approverRole: string
  approver: string | null
  nodeStatus: GrayApprovalNodeStatus
  comment: string | null
  signature: string | null
  decidedAt: string | null
}

/** 审计流水信息 */
export interface GrayApprovalAuditVO {
  id: number
  flowId: number
  flowNo: string
  action: GrayApprovalAuditAction
  operator: string
  detailJson: string | null
  chainTxHash: string | null
  createdAt: string
}

/** 详情页完整 VO（flow + nodes + audits） */
export interface GrayApprovalDetailVO {
  flow: GrayApprovalFlowVO
  nodes: GrayApprovalNodeVO[]
  audits: GrayApprovalAuditVO[]
}

/* ---------- Query / Pagination ---------- */

export interface GrayApprovalQueryParams {
  page?: number
  size?: number
  /** 按 flowNo/policyId/title 模糊搜索 */
  keyword?: string
  flowType?: GrayApprovalFlowType | ''
  status?: GrayApprovalFlowStatus | ''
  applicant?: string
  startDate?: string
  endDate?: string
}

export interface PageVO<T> {
  list: T[]
  total: number
  page: number
  size: number
}

/* ---------- Request payloads ---------- */

export interface GrayApprovalCreateRequest {
  flowType: GrayApprovalFlowType
  policyId: string
  applicant: string // 后端会校验等于当前登录用户名
  title?: string
  applyReason?: string
  requiredApprovals?: number // 1..5
  approvers?: string[] // 指定审批人；留空走 ROLE_RELEASE_ADMIN 角色池
  payload?: Record<string, unknown> | null
  // WEIGHT_CHANGE 需要: { targetWeight: number }
  // ROLLBACK 需要:       { reason: string }
  // GRADUATE / LAUNCH 可空
}

export interface GrayApprovalApproveRequest {
  flowId: number
  approver: string
  approved: boolean
  comment?: string
  signature?: string // 合规场景可选
}

export interface GrayApprovalCancelRequest {
  flowId: number
  operator: string
  reason?: string
}

export interface GrayApprovalRetryExecuteRequest {
  flowId: number
  operator: string
}

/* ---------- 7 API 封装（与后端 Design Doc §5.1 一一对应）---------- */

const API_PREFIX = '/release/gray'

/** #1 POST /approvals — 创建并提交审批单 */
export function createGrayApproval(data: GrayApprovalCreateRequest): Promise<R<GrayApprovalFlowVO>> {
  return request({ url: `${API_PREFIX}/approvals`, method: 'post', data })
}

/** #2 PUT /approvals/action/approve — 审批通过/拒绝 */
export function approveGrayApproval(data: GrayApprovalApproveRequest): Promise<R<GrayApprovalFlowVO>> {
  return request({ url: `${API_PREFIX}/approvals/action/approve`, method: 'put', data })
}

/** #3 PUT /approvals/action/cancel — 撤销（仅 DRAFT/PENDING） */
export function cancelGrayApproval(data: GrayApprovalCancelRequest): Promise<R<GrayApprovalFlowVO>> {
  return request({ url: `${API_PREFIX}/approvals/action/cancel`, method: 'put', data })
}

/** #4 PUT /approvals/action/retry-execute — 重试网关执行 */
export function retryExecuteGrayApproval(
  data: GrayApprovalRetryExecuteRequest
): Promise<R<GrayApprovalFlowVO>> {
  return request({ url: `${API_PREFIX}/approvals/action/retry-execute`, method: 'put', data })
}

/** #5 GET /approvals — 分页条件查询 */
export function listGrayApprovals(
  params: GrayApprovalQueryParams
): Promise<R<PageVO<GrayApprovalFlowVO>>> {
  return request({ url: `${API_PREFIX}/approvals`, method: 'get', params })
}

/** #6 GET /approvals/{flowId} — 详情（flow + nodes + audits） */
export function getGrayApprovalDetail(flowId: number): Promise<R<GrayApprovalDetailVO>> {
  return request({ url: `${API_PREFIX}/approvals/${flowId}`, method: 'get' })
}

/**
 * flowType 中文映射（表格 / 筛选下拉显示）
 * 注意：此处必须与后端枚举顺序同步，新增 flowType 请同时追加
 */
export const GRAY_APPROVAL_FLOW_TYPE_LABELS: Record<GrayApprovalFlowType, string> = {
  GRADUATE: '灰度毕业',
  WEIGHT_CHANGE: '灰度放量',
  ROLLBACK: '灰度回滚',
  LAUNCH: '首次灰度'
}

/** 状态 → 中文标签 + Element Plus tag type */
export const GRAY_APPROVAL_STATUS_META: Record<
  GrayApprovalFlowStatus,
  { label: string; type: '' | 'success' | 'warning' | 'info' | 'primary' | 'danger' }
> = {
  DRAFT:            { label: '草稿',     type: 'info' },
  PENDING_APPROVAL: { label: '待审批',   type: 'warning' },
  APPROVED:         { label: '已通过',   type: 'primary' },
  REJECTED:         { label: '已拒绝',   type: 'danger' },
  CANCELLED:        { label: '已撤销',   type: 'info' },
  EXECUTING:        { label: '执行中',   type: 'primary' },
  SUCCEEDED:        { label: '执行成功', type: 'success' },
  EXECUTE_FAILED:   { label: '执行失败', type: 'danger' }
}

/** 节点状态 → 中文标签 */
export const GRAY_APPROVAL_NODE_STATUS_LABELS: Record<GrayApprovalNodeStatus, string> = {
  WAITING:  '待审批',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  SKIPPED:  '已跳过'
}

/** Audit action 中文标签（便于时间线展示） */
export const GRAY_APPROVAL_AUDIT_ACTION_LABELS: Partial<Record<GrayApprovalAuditAction, string>> = {
  FLOW_CREATED:         '审批单已创建',
  FLOW_SUBMITTED:       '已提交待审批',
  NODE_APPROVED:        '审批节点通过',
  FLOW_APPROVED:        '全票通过：自动进入执行阶段',
  FLOW_REJECTED:        '审批被拒绝：终止',
  FLOW_CANCELLED:       '申请人撤销审批单',
  FLOW_EXECUTING:       '调用执行端（lsc-gateway）处理中',
  FLOW_SUCCEEDED:       '执行端处理成功 ✅',
  FLOW_EXECUTE_FAILED:  '执行端处理失败：请重试或联系运维'
}
