import { get, post } from '@/utils/request'
import { useMerchantStore } from '@/stores/merchant'
import type { PageResult } from '@/utils/request'
import type { Order, RefundRequest } from './types'

/** 获取当前商家 userId */
function merchantUserId(): number | undefined {
  return useMerchantStore().profile?.userId
}

/** 订单列表查询 */
export interface OrderListQuery {
  page?: number
  size?: number
  /** 订单号 */
  orderNo?: string
  /** 0待支付 1已支付 2已完成 3已取消 4已退款 5部分退款 */
  status?: number
  /** 0线上商城 1线下消费 */
  orderType?: number
  /** 起始日期 YYYY-MM-DD */
  startDate?: string
  /** 结束日期 YYYY-MM-DD */
  endDate?: string
}

/** 订单分页列表 */
export function getOrders(params: OrderListQuery) {
  return get<PageResult<Order>>('/order/list', {
    userId: merchantUserId(),
    ...params
  })
}

/** 订单详情 */
export function getOrderDetail(orderNo: string) {
  return get<Order>(`/order/${orderNo}`)
}

/** 商家发货/确认履约 */
export function shipOrder(orderNo: string) {
  return post<void>('/order/ship', {
    orderNo,
    operatorId: merchantUserId()
  })
}

/** 商家主动同意退款 */
export function agreeRefund(orderNo: string) {
  return post<void>('/order/refund/agree', {
    orderNo,
    operatorId: merchantUserId()
  })
}

/** 商家拒绝退款 */
export function rejectRefund(orderNo: string, reason: string) {
  return post<void>('/order/refund/reject', {
    orderNo,
    reason,
    operatorId: merchantUserId()
  })
}

/** 退款申请列表 */
export function getRefundList(params: {
  page?: number
  size?: number
  status?: number
}) {
  return get<PageResult<RefundRequest>>('/order/refund/list', {
    merchantId: merchantUserId(),
    ...params
  })
}

/** 商家今日订单/收入统计 */
export interface OrderStats {
  todayOrderCount: number
  todayRevenue: number
  pendingShipCount: number
  pendingRefundCount: number
}

export function getOrderStats() {
  return get<OrderStats>('/order/stats-today', {
    merchantId: merchantUserId()
  })
}
