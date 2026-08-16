import { request } from '@/utils/request'

export interface ProductListParams {
  page?: number
  size?: number
  keyword?: string
  status?: string
  merchantId?: string | number
}

/** 商品列表 */
export function getProductList(params: ProductListParams) {
  return request({
    url: '/product/list',
    method: 'get',
    params
  })
}

/** 商品详情 */
export function getProductDetail(id: number) {
  return request({
    url: `/product/${id}`,
    method: 'get'
  })
}

/** 待审核商品列表 */
export function getProductAuditList(params: Record<string, any>) {
  return request({
    url: '/product/audit/list',
    method: 'get',
    params
  })
}

/** AI审核结果 */
export function getAiReviewResult(id: number) {
  return request({
    url: `/product/${id}/ai-review`,
    method: 'get'
  })
}

/** 人工复核商品 */
export function reviewProduct(id: number, data: { status: string; reason?: string }) {
  return request({
    url: `/product/audit/${id}`,
    method: 'post',
    data
  })
}

/** 上架/下架 */
export function toggleProductStatus(id: number, status: string) {
  return request({
    url: `/product/${id}/status`,
    method: 'post',
    data: { status }
  })
}
