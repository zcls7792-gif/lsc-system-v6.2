import { get, post } from './request'

// ===== 报价 =====

export interface QuoteItem {
  skuId: number
  qty: number
}

export interface QuoteItemResult {
  skuId: number
  qty: number
  unitPriceCent: number
  lineCent: number
}

export interface QuoteResult {
  quoteId: number
  goodsCent: number
  couponCent: number
  lscUnit: number
  rmbCent: number
  expireAt: string
  items: QuoteItemResult[]
}

/**
 * 创建报价
 * buyerType: C 端零售 / B 端批发
 */
export function createQuote(
  buyerType: string,
  items: QuoteItem[],
  opts?: { lscUnit?: number; couponId?: number }
) {
  const params: any = { buyerType }
  if (opts?.lscUnit) params.lscUnit = opts.lscUnit
  if (opts?.couponId) params.couponId = opts.couponId
  return post<QuoteResult>('/checkout/quotes', items, { params })
}

// ===== 订单 =====

export interface Order {
  orderId: number
  userId: number
  buyerType: string
  goodsCent: number
  couponCent: number
  lscUnit: number
  rmbCent: number
  discountMode: string
  status: string
  createdAt: string
}

export function listOrders(status?: string) {
  return get<Order[]>('/orders', { status })
}

export function getOrder(orderId: number) {
  return get<Order>(`/orders/${orderId}`)
}

export function cancelOrder(orderId: number) {
  return post(`/orders/${orderId}/cancel`)
}

/**
 * 从报价创建订单
 */
export function createOrder(quoteId: number) {
  return post<string>('/orders', undefined, {
    params: { quoteId },
    headers: { 'Idempotency-Key': `order-${Date.now()}` }
  })
}
