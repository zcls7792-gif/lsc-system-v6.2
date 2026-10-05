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

export interface PriceVersion {
  priceVersion: number
  skuId: number
  retailPriceCent: number
  bPriceCent: number
  costKeyVersion: string
  grantCoefPpm: number
  grantCUnit: number
  grantBUnit: number
  effectiveAt: string
  approvedBy: number
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

export function updateProduct(id: number, data: { name?: string; descriptionRef?: string }) {
  return post(`/admin/products/${id}`, null, { params: data })
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

export function updateSku(id: number, data: any) {
  return post(`/admin/skus/${id}`, null, { params: data })
}

export function updateSkuStatus(id: number, status: string) {
  return post(`/admin/skus/${id}/status`, null, { params: { status } })
}

export function getActivePriceVersion(skuId: number) {
  return get<PriceVersion>(`/admin/skus/${skuId}/price-versions/active`)
}

export function listPriceVersions(skuId: number) {
  return get<PriceVersion[]>(`/admin/skus/${skuId}/price-versions`)
}

export function createPriceVersion(data: any) {
  return post<number>('/admin/price-versions', null, { params: data })
}
