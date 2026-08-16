import { request } from '@/utils/request'

export interface OrderListParams {
  page?: number
  size?: number
  orderNo?: string
  /** 订单类型 0线上 1线下 */
  orderType?: number | string
  /** 订单状态 0待支付 1已支付 2已完成 3已取消 4已退款 5部分退款 */
  status?: number | string
  /** 消费者/商家ID */
  userId?: number
  startDate?: string
  endDate?: string
}

/** 订单列表 */
export function getOrderList(params: OrderListParams) {
  return request({
    url: '/order/list',
    method: 'get',
    params
  })
}

/** 订单详情 */
export function getOrderDetail(orderNo: string) {
  return request({
    url: `/order/${orderNo}`,
    method: 'get'
  })
}

/** 导出订单(返回 JSON, 前端转 CSV/Excel) */
export function exportOrders(params: OrderListParams) {
  return request({
    url: '/order/export',
    method: 'get',
    params
  })
}
