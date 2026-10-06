import { get, post } from './request'

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
