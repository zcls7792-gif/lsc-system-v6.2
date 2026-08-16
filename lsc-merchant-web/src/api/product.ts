import { get, post, put, del } from '@/utils/request'
import { useMerchantStore } from '@/stores/merchant'
import type { PageResult } from '@/utils/request'
import type { Product, ProductCategory } from './types'

/** 获取当前商家 userId */
function merchantUserId(): number | undefined {
  return useMerchantStore().profile?.userId
}

/** 商品列表查询 */
export interface ProductListQuery {
  page?: number
  size?: number
  /** 商品名称 (模糊) */
  keyword?: string
  /** 0下架 1上架 2审核中 */
  status?: number
  /** 类目ID */
  categoryId?: number
}

/** 发布商品入参 */
export interface PublishProductParams {
  id?: number
  productName: string
  productDesc?: string
  productImages: string[]
  /** 价格 (人民币 = LSC 价格 1:1) */
  price: number
  stock: number
  categoryId: number
  videoUrl?: string
  videoCoverUrl?: string
  videoDuration?: number
}

/** 商品分页列表 */
export function getProducts(params: ProductListQuery) {
  return get<PageResult<Product>>('/product/list', {
    merchantId: merchantUserId(),
    ...params
  })
}

/** 商品详情 */
export function getProductDetail(id: number) {
  return get<Product>(`/product/${id}`)
}

/** 发布/编辑商品 */
export function publishProduct(data: PublishProductParams) {
  const merchantId = merchantUserId()
  const body = {
    merchantId,
    categoryId: data.categoryId,
    name: data.productName,
    description: data.productDesc,
    mainImage: data.productImages?.[0] || '',
    price: data.price,
    stock: data.stock
  }
  if (data.id) {
    return put<Product>('/product/update', body, { params: { id: data.id } })
  }
  return post<Product>('/product/publish', body)
}

/** 删除商品 */
export function deleteProduct(id: number) {
  return del<void>(`/product/${id}`)
}

/** 上架 */
export function shelfOn(id: number) {
  return post<void>('/product/on-shelf', null, { params: { id } })
}

/** 下架 */
export function shelfOff(id: number) {
  return post<void>('/product/off-shelf', null, { params: { id } })
}

/** 类目树 (含父子) */
export function getCategories() {
  return get<ProductCategory[]>('/product/categories')
}

/** 子类目列表 */
export function getSubCategories(parentId: number) {
  return get<ProductCategory[]>(`/product/categories/${parentId}`)
}
