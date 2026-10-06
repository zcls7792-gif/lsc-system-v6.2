import { get } from './request'

export interface Product {
  productId: number
  name: string
  status: string
  descriptionRef: string
}

export interface Sku {
  skuId: number
  skuCode: string
  specJson: string
  saleUnit: string
  packQty: number
  bMinQty?: number
  bMinPackQty?: number
  // snake_case 兼容
  sku_id?: number
  sku_code?: string
  spec_json?: string
  sale_unit?: string
  pack_qty?: number
  b_min_qty?: number
}

export function listProducts(status = 'ON_SALE', limit = 20, offset = 0) {
  return get<Product[]>('/products', { status, limit, offset })
}

export function getProduct(id: number) {
  return get<Product>(`/products/${id}`)
}

export function listSkus(productId: number) {
  return get<Sku[]>(`/products/${productId}/skus`)
}

export function getActivePrice(skuId: number) {
  return get<any>(`/skus/${skuId}/price`)
}
