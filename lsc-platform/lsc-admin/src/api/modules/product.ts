import { get, post } from '@/api/request'

export interface Product {
  productId: number
  sellerEntityId: number
  name: string
  categoryId: number
  status: string
  auditVersion: number
  descriptionRef: string
  returnPolicyVersion: string
}

export interface Sku {
  skuId: number
  productId: number
  skuCode: string
  specJson: string
  saleUnit: string
  packQty: number
  bMinQty: number
  status: string
}

export function listProducts(status: string, limit = 50, offset = 0) {
  return get<Product[]>('/admin/products', { status, limit, offset })
}

export function getProduct(id: number) {
  return get<Product>(`/admin/products/${id}`)
}

export function createProduct(data: any) {
  return post<number>('/admin/products', null, { params: data })
}

export function submitReview(id: number, applicantId: number) {
  return post<number>(`/admin/products/${id}/submit-review`, null, { params: { applicantId } })
}

export function offSale(id: number) {
  return post(`/admin/products/${id}/off-sale`)
}

export function onSale(id: number) {
  return post(`/admin/products/${id}/on-sale`)
}

export function listSkus(productId: number) {
  return get<Sku[]>(`/admin/products/${productId}/skus`)
}

export function createSku(data: any) {
  return post<number>('/admin/skus', null, { params: data })
}
