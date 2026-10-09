import { get, post } from '@/api/request'

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

export function listOrders(params: { status?: string; keyword?: string; limit?: number; offset?: number }) {
  return get<Order[]>('/admin/orders', params)
}

export function getOrder(orderId: number) {
  return get<Order>(`/admin/orders/${orderId}`)
}

export function cancelOrder(orderId: number) {
  return post(`/admin/orders/${orderId}/cancel`)
}

export function refundOrder(orderId: number, reason: string) {
  return post(`/admin/orders/${orderId}/refund`, null, { params: { reason } })
}
