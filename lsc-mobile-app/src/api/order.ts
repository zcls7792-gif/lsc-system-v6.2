import { http } from '@/utils/request'

/** 订单状态 */
export type PaymentStatus = 'UNPAID' | 'PAYING' | 'PAID' | 'REFUNDING' | 'PART_REFUNDED' | 'REFUNDED' | 'EXCEPTION'
export type FulfillmentStatus = 'CREATED' | 'CONFIRMED' | 'SHIPPED' | 'COMPLETED' | 'CANCELED' | 'CLOSED'

export interface OrderItem {
  itemId: number
  orderId: number
  skuId: number
  qty: number
  unitPriceCent: number
  lineGoodsCent: number
  couponShareCent: number
  lscShareUnit: number
  rmbShareCent: number
  grantedUnit: number
}

export interface Order {
  orderId: number
  orderNo: string
  userId: number
  buyerTypeSnapshot: string
  discountMode: 'NONE' | 'LSC' | 'COUPON'
  goodsCent: number
  shippingCent: number
  couponCent: number
  lscUnit: number
  rmbCent: number
  paymentStatus: PaymentStatus
  fulfillmentStatus: FulfillmentStatus
  refundStatus: string
  expiresAt: string
  completedAt?: string
  createdAt?: string
}

export interface QuoteRequest {
  buyerType: 'C' | 'B'
  skuItems: Array<{ skuId: number; qty: number }>
  shippingCent?: number
  deductionPpm?: number
}

export interface QuoteResult {
  quoteId: string
  goodsCent: number
  shippingCent: number
  maxDeductionUnit: number
  maxDeductionCent: number
  items: Array<{
    skuId: number
    qty: number
    unitPriceCent: number
    lineGoodsCent: number
    grantUnitPerPiece: number
    grantCoefficientPpm: number
  }>
  expiresAt: string
}

export interface CreateOrderRequest {
  userId: number
  buyerType: 'C' | 'B'
  sellerEntityId: number
  skuItems: Array<{ skuId: number; qty: number }>
  shippingCent?: number
  lscUnit?: number
  couponId?: number
  couponCent?: number
}

export interface RefundRequest {
  orderId: number
  rmbCent: number
  reason: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/** 创建报价 */
export function createQuote(data: QuoteRequest) {
  return http.post<QuoteResult>('/v1/checkout/quotes', data)
}

/** 预览订单（复用报价接口） */
export function previewOrder(data: QuoteRequest) {
  return createQuote(data)
}

/** 创建订单 */
export function createOrder(data: CreateOrderRequest) {
  return http.post<Order>('/v1/orders', data, { header: { 'Idempotency-Key': Date.now().toString() } })
}

/** 订单列表 */
export function getOrderList(params: { page?: number; size?: number; paymentStatus?: string; fulfillmentStatus?: string }) {
  return http.get<PageResult<Order>>('/v1/orders', params)
}

/** 订单详情 */
export function getOrderDetail(id: number | string) {
  return http.get<{ order: Order; items: OrderItem[] }>(`/v1/orders/${id}`)
}

/** 取消订单 */
export function cancelOrder(id: number | string) {
  return http.post<void>(`/v1/orders/${id}/cancel`)
}

/** 确认收货（完成订单） */
export function confirmReceive(id: number | string) {
  return http.post<void>(`/v1/orders/${id}/complete`)
}

/** 申请退款 */
export function applyRefund(data: RefundRequest) {
  return http.post<void>('/v1/refunds', data)
}

/** 支付成功（测试用） */
export function paySuccess(orderId: number) {
  return http.post<void>(`/v1/internal/payments/${orderId}/success`)
}

/** 发起支付（简化：直接调用支付成功回调） */
export function payOrder(orderId: number) {
  return paySuccess(orderId)
}
