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
  orderNo?: string
  userId: number
  buyerType: string
  goodsCent: number
  couponCent: number
  lscUnit: number
  rmbCent: number
  discountMode: string
  status: string
  createdAt: string
  // 后端 snake_case 兼容字段
  order_id?: number
  order_no?: string
  goods_cent?: number
  coupon_cent?: number
  lsc_unit?: number
  rmb_cent?: number
  payment_status?: string
  created_at?: string
  items?: OrderItem[]
}

export interface OrderItem {
  itemId?: number
  item_id?: number
  skuId?: number
  sku_id?: number
  qty: number
  unitPriceCent?: number
  unit_price_cent?: number
  lineGoodsCent?: number
  line_goods_cent?: number
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
