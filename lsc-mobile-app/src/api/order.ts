import { http } from '@/utils/request'
import type { Product } from './product'

/** 订单状态 0-待支付 1-已支付 2-已完成 3-已取消 4-已退款 5-部分退款 */
export type OrderStatus = 0 | 1 | 2 | 3 | 4 | 5

export interface OrderItem {
  productId: number
  productName: string
  productImage: string
  price: number
  lscPrice: number
  quantity: number
  spec?: string
}

export interface Order {
  id: number
  orderNo: string
  status: OrderStatus
  /** 订单总金额（元） */
  totalAmount: number
  /** 使用 LSC 数量 */
  lscAmount: number
  /** 人民币补足金额 */
  rmbAmount: number
  items: OrderItem[]
  /** 收货地址快照 */
  address?: AddressSnapshot
  /** 商家门店 */
  store?: { id: number; name: string; address: string; latitude: number; longitude: number; phone?: string }
  /** 创建时间 */
  createTime: string
  /** 支付时间 */
  payTime?: string
  /** 支付超时时间戳(ms) */
  expireTime?: number
  /** 退款金额 */
  refundAmount?: number
  /** 退款原因 */
  refundReason?: string
}

export interface AddressSnapshot {
  name: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
}

export interface CreateOrderParams {
  /** 收货地址 id */
  addressId: number
  /** 商品项 */
  items: Array<{ productId: number; quantity: number; spec?: string }>
  /** 使用的 LSC 数量 */
  lscAmount: number
  /** 备注 */
  remark?: string
  /** 来源 cart:从购物车结算 */
  fromCart?: boolean
}

export interface CreateOrderResult {
  orderId: number
  orderNo: string
  /** 待支付金额（人民币补足） */
  payAmount: number
  lscAmount: number
  /** 支付超时时间 */
  expireTime: number
}

export interface PayOrderParams {
  orderId: number
  /** 使用的 LSC 数量 */
  lscAmount: number
  /** 支付方式: wechat | alipay | balance */
  payMethod: 'wechat' | 'alipay' | 'balance'
}

export interface PayResult {
  orderId: number
  status: number
  /** 微信支付参数 */
  wxPayParams?: {
    timeStamp: string
    nonceStr: string
    package: string
    signType: string
    paySign: string
  }
}

export interface OrderListParams {
  page?: number
  size?: number
  /** -1 全部 / 0 待支付 / 1 已支付 / 2 已完成 / 4 退款 */
  status?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

/** 创建订单 */
export function createOrder(data: CreateOrderParams) {
  return http.post<CreateOrderResult>('/api/order/create', data)
}

/** 支付订单 */
export function payOrder(data: PayOrderParams) {
  return http.post<PayResult>('/api/order/pay', data)
}

/** 订单列表 */
export function getOrderList(params: OrderListParams) {
  return http.get<PageResult<Order>>('/api/order/list', params)
}

/** 订单详情 */
export function getOrderDetail(id: number | string) {
  return http.get<Order>('/api/order/detail', { id })
}

/** 取消订单 */
export function cancelOrder(id: number | string) {
  return http.post<void>('/api/order/cancel', { id })
}

/** 确认收货 */
export function confirmReceive(id: number | string) {
  return http.post<void>('/api/order/confirm', { id })
}

/** 申请退款 */
export function applyRefund(data: { orderId: number; reason: string; amount?: number }) {
  return http.post<void>('/api/order/refund/apply', data)
}

/** 订单预览（确认订单页用，返回金额试算） */
export function previewOrder(data: Omit<CreateOrderParams, 'lscAmount'> & { lscAmount: number }) {
  return http.post<{
    totalAmount: number
    lscAmount: number
    rmbAmount: number
    items: OrderItem[]
    address: AddressSnapshot
  }>('/api/order/preview', data)
}

export type { Product }
