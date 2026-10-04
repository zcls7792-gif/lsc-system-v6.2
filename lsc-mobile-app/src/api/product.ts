import { http } from '@/utils/request'

export interface ProductSku {
  skuId: number
  skuCode: string
  specJson: string
  retailPriceCent: number
  bPriceCent?: number
  grantCoefficientPpm: number
}

export interface Product {
  productId: number
  name: string
  categoryId: number
  status: string
}

export interface ProductDetail {
  product: Product
  skus: ProductSku[]
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/** 商品列表（V7.7.2） */
export function getProductList(params: { page?: number; size?: number; buyerType?: string }) {
  return http.get<PageResult<Product>>('/v1/products', params)
}

/** 商品详情 + SKU + 价格版本（V7.7.2） */
export function getProductDetail(id: number | string, buyerType: 'C' | 'B' = 'C') {
  return http.get<ProductDetail>(`/v1/products/${id}`, { buyerType })
}

/** 轮播图（V7.7.2 暂无独立接口，返回占位数据） */
export function getBanners(): Promise<any[]> {
  return Promise.resolve([])
}

/** 商品分类（V7.7.2 暂无独立接口，返回占位数据） */
export function getCategories(): Promise<any[]> {
  return Promise.resolve([])
}

/** 推荐商品（复用商品列表接口） */
export function getRecommendProducts(size = 8) {
  return getProductList({ page: 1, size })
}

/** 热门商品（复用商品列表接口） */
export function getHotProducts(params: { page?: number; size?: number }) {
  return getProductList(params)
}

export interface Category {
  id: number
  name: string
  icon?: string
}

export interface StoreInfo {
  id: number
  name: string
  address: string
  longitude: number
  latitude: number
  phone?: string
  businessHours?: string
}

/** 附近门店（V7.7.2 暂无独立接口，返回占位数据） */
export function getNearbyStores(_params?: Record<string, any>): Promise<StoreInfo[]> {
  return Promise.resolve([])
}
