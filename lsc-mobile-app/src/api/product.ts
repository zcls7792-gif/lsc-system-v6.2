import { http } from '@/utils/request'

export interface Product {
  id: number
  name: string
  /** 主图 */
  cover: string
  /** 轮播图 */
  images?: string[]
  /** 视频 */
  video?: string
  /** 人民币价格（元） */
  price: number
  /** LSC 价格（与人民币 1:1） */
  lscPrice: number
  /** 原价 */
  originPrice?: number
  /** 销量 */
  sales?: number
  /** 库存 */
  stock?: number
  /** 分类ID */
  categoryId?: number
  /** 商品描述（富文本/html） */
  description?: string
  /** 规格 */
  specs?: ProductSpec[]
  /** 商家门店信息 */
  store?: StoreInfo
  status?: number
}

export interface ProductSpec {
  id: number
  name: string
  values: string[]
}

export interface StoreInfo {
  id: number
  name: string
  address: string
  phone?: string
  latitude: number
  longitude: number
  distance?: number
}

export interface Category {
  id: number
  name: string
  icon?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  size: number
}

export interface ProductListParams {
  page?: number
  size?: number
  categoryId?: number
  keyword?: string
  /** 排序: sales | price_asc | price_desc */
  sort?: string
}

/** 首页轮播 */
export function getBanners() {
  return http.get<Array<{ id: number; image: string; link?: string; type?: string }>>('/api/product/banners')
}

/** 首页分类 */
export function getCategories() {
  return http.get<Category[]>('/api/product/categories')
}

/** 商品列表（分页） */
export function getProductList(params: ProductListParams) {
  return http.get<PageResult<Product>>('/api/product/list', params)
}

/** AI 推荐商品 */
export function getRecommendProducts(limit = 10) {
  return http.get<Product[]>('/api/product/recommend', { limit })
}

/** 热门商品 */
export function getHotProducts(limit = 10) {
  return http.get<Product[]>('/api/product/hot', { limit })
}

/** 商品详情 */
export function getProductDetail(id: number | string) {
  return http.get<Product>(`/api/product/detail`, { id })
}

/** 搜索商品 */
export function searchProducts(keyword: string, page = 1, size = 10) {
  return http.get<PageResult<Product>>('/api/product/search', { keyword, page, size })
}

/** 附近商家门店列表 */
export function getNearbyStores(latitude: number, longitude: number) {
  return http.get<StoreInfo[]>('/api/product/stores/nearby', { latitude, longitude })
}
