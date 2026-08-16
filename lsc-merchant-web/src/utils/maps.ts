/**
 * 商家端状态映射工具
 * 与后端 lsc_common.enums 对齐
 */

/** Element Plus el-tag 支持的类型 */
export type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

export const PRODUCT_STATUS_MAP: Record<number, { label: string; type: TagType }> = {
  0: { label: '已下架', type: 'info' },
  1: { label: '上架中', type: 'success' },
  2: { label: '审核中', type: 'warning' }
}

export const AI_REVIEW_MAP: Record<number, { label: string; type: TagType }> = {
  0: { label: '未审核', type: 'info' },
  1: { label: 'AI 通过', type: 'success' },
  2: { label: 'AI 可疑', type: 'warning' },
  3: { label: '人工通过', type: 'success' },
  4: { label: '人工拒绝', type: 'danger' }
}

export const VIDEO_STATUS_MAP: Record<number, { label: string; type: TagType }> = {
  0: { label: '待审核', type: 'info' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已拒绝', type: 'danger' }
}

export const ORDER_STATUS_MAP: Record<number, { label: string; type: TagType }> = {
  0: { label: '待支付', type: 'warning' },
  1: { label: '已支付', type: 'primary' },
  2: { label: '已完成', type: 'success' },
  3: { label: '已取消', type: 'info' },
  4: { label: '已退款', type: 'danger' },
  5: { label: '部分退款', type: 'warning' }
}

export const ORDER_TYPE_MAP: Record<number, string> = {
  0: '线上商城',
  1: '线下消费'
}

export const B2B_STATUS_MAP: Record<number, { label: string; type: TagType }> = {
  0: { label: '待确认', type: 'warning' },
  1: { label: '已确认', type: 'primary' },
  2: { label: '已流转', type: 'success' },
  3: { label: '已完成', type: 'success' },
  4: { label: '已取消', type: 'info' },
  5: { label: '已作废', type: 'danger' }
}

export const WRITEOFF_STATUS_MAP: Record<number, { label: string; type: TagType }> = {
  0: { label: '待处理', type: 'warning' },
  1: { label: '处理中', type: 'primary' },
  2: { label: '成功', type: 'success' },
  3: { label: '失败', type: 'danger' }
}

export const LSC_TX_TYPE_MAP: Record<number, string> = {
  1: '消费发行',
  2: '每日释放',
  3: '推广奖励',
  4: '商城消费',
  5: '线下消费',
  6: '过期转回',
  7: '商家核销',
  8: 'B2B流转',
  9: '退款退回'
}

export const PENALTY_STATUS_MAP: Record<number, { label: string; type: TagType }> = {
  0: { label: '正常', type: 'success' },
  1: { label: '一级处罚', type: 'warning' },
  2: { label: '二级处罚', type: 'warning' },
  3: { label: '三级处罚', type: 'danger' },
  4: { label: '清退', type: 'danger' }
}

export const AUDIT_STATUS_MAP: Record<number, { label: string; type: TagType }> = {
  0: { label: '待审核', type: 'warning' },
  1: { label: '已通过', type: 'success' },
  2: { label: '已拒绝', type: 'danger' }
}

/** 解析后端 JSON 数组字符串为 string[] */
export function parseJsonArray<T = string>(raw: string | undefined | null): T[] {
  if (!raw) return []
  try {
    const v = JSON.parse(raw)
    return Array.isArray(v) ? v : []
  } catch {
    return []
  }
}
