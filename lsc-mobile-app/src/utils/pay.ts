import { AppConfig } from '@/config'

/**
 * LSC 混合支付计算工具
 * 规则：1 LSC = 1 元（与人民币 1:1）
 * 滑块选择使用的 LSC 数量，剩余金额由人民币补足。
 */

/** 金额向上保留两位小数（避免浮点误差），返回数字 */
export function round2(n: number | string): number {
  const num = Number(n) || 0
  return Math.round((num + Number.EPSILON) * 100) / 100
}

/** 金额格式化为字符串（2位小数） */
export function formatMoney(n: number | string, withSymbol = true): string {
  const num = round2(n).toFixed(2)
  return withSymbol ? `¥${num}` : num
}

/** LSC 数量格式化（整数） */
export function formatLsc(n: number | string): string {
  return `${Math.floor(Number(n) || 0)} LSC`
}

export interface HybridPayResult {
  /** 使用的 LSC 数量（整数） */
  lscAmount: number
  /** 人民币补足金额 */
  rmbAmount: number
  /** 订单总金额 */
  totalAmount: number
  /** 可用 LSC 上限（不超过订单总额） */
  maxLsc: number
}

/**
 * 计算混合支付
 * @param totalAmount 订单总金额（元）
 * @param useLsc 用户选择使用的 LSC 数量
 * @param availableLsc 用户可用 LSC 余额
 */
export function calcHybridPay(
  totalAmount: number,
  useLsc: number,
  availableLsc: number,
): HybridPayResult {
  const rate = AppConfig.lscToRmbRate
  const total = round2(totalAmount)
  const maxLsc = Math.min(Math.floor(availableLsc), Math.floor(total / rate))
  let lsc = Math.floor(useLsc)
  if (Number.isNaN(lsc)) lsc = 0
  if (lsc < 0) lsc = 0
  if (lsc > maxLsc) lsc = maxLsc
  const rmbAmount = round2(total - lsc * rate)
  return {
    lscAmount: lsc,
    rmbAmount: rmbAmount < 0 ? 0 : rmbAmount,
    totalAmount: total,
    maxLsc,
  }
}

/**
 * LSC 转人民币
 */
export function lscToRmb(lsc: number): number {
  return round2((Number(lsc) || 0) * AppConfig.lscToRmbRate)
}

/**
 * 人民币转 LSC
 */
export function rmbToLsc(rmb: number): number {
  return Math.floor((Number(rmb) || 0) / AppConfig.lscToRmbRate)
}
