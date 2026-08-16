import { AppConfig } from '@/config'
import { getToken, clearToken } from '@/utils/auth'

/** 业务状态码：成功 */
const BIZ_SUCCESS = 200

export interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: Record<string, any> | string
  header?: Record<string, string>
  /** 是否跳过 token 注入（如登录接口） */
  skipAuth?: boolean
  /** 是否在请求体内以 query 形式拼接 */
  params?: Record<string, any>
  /** 自定义超时 */
  timeout?: number
}

export interface ApiResult<T = any> {
  code: number
  msg: string
  message?: string
  data: T
  /** 部分接口的分页 */
  rows?: T[]
  total?: number
}

/** 401 未授权处理，避免重复弹窗 */
let unauthorizedHandling = false

function buildUrl(url: string, params?: Record<string, any>): string {
  if (!params) return url
  const qs = Object.keys(params)
    .filter((k) => params[k] !== undefined && params[k] !== null && params[k] !== '')
    .map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(params[k])}`)
    .join('&')
  if (!qs) return url
  return url.includes('?') ? `${url}&${qs}` : `${url}?${qs}`
}

function handleUnauthorized() {
  if (unauthorizedHandling) return
  unauthorizedHandling = true
  clearToken()
  uni.showToast({ title: '登录已过期，请重新登录', icon: 'none', duration: 1500 })
  setTimeout(() => {
    unauthorizedHandling = false
    uni.reLaunch({ url: '/src/pages-account/login/index' })
  }, 1500)
}

/**
 * 统一请求封装
 * - 自动注入 baseURL
 * - 自动注入 Authorization
 * - 统一错误提示
 * - 401 自动登出
 * - 约定后端返回 { code, msg, data }
 */
export function request<T = any>(options: RequestOptions): Promise<T> {
  const {
    url,
    method = 'GET',
    data,
    header = {},
    skipAuth = false,
    params,
    timeout,
  } = options

  const fullUrl = url.startsWith('http') ? url : `${AppConfig.baseURL}${buildUrl(url, params)}`

  const finalHeader: Record<string, string> = {
    'Content-Type': 'application/json',
    ...header,
  }
  if (!skipAuth) {
    const token = getToken()
    if (token) {
      finalHeader['Authorization'] = `Bearer ${token}`
    }
  }

  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: fullUrl,
      method,
      data,
      header: finalHeader,
      timeout: timeout || AppConfig.timeout,
      success: (res: any) => {
        const status = res.statusCode
        if (status === 401) {
          handleUnauthorized()
          return reject(new Error('未授权'))
        }
        if (status < 200 || status >= 300) {
          const msg = `请求失败(${status})`
          uni.showToast({ title: msg, icon: 'none' })
          return reject(new Error(msg))
        }
        const body = res.data as ApiResult<T>
        // 兼容后端两种返回结构：{ code, data } 或直接数组/对象
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code === BIZ_SUCCESS || body.code === 0) {
            return resolve(body.data as T)
          }
          // 业务码 401
          if (body.code === 401) {
            handleUnauthorized()
            return reject(new Error(body.msg || body.message || '未授权'))
          }
          const errMsg = body.msg || body.message || '请求失败'
          uni.showToast({ title: errMsg, icon: 'none' })
          return reject(new Error(errMsg))
        }
        // 非约定结构，原样返回
        resolve(body as unknown as T)
      },
      fail: (err: any) => {
        const msg = err?.errMsg || '网络异常，请稍后重试'
        uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      },
    })
  })
}

export const http = {
  get: <T = any>(url: string, params?: Record<string, any>, opts?: Partial<RequestOptions>) =>
    request<T>({ url, method: 'GET', params, ...opts }),
  post: <T = any>(url: string, data?: Record<string, any>, opts?: Partial<RequestOptions>) =>
    request<T>({ url, method: 'POST', data, ...opts }),
  put: <T = any>(url: string, data?: Record<string, any>, opts?: Partial<RequestOptions>) =>
    request<T>({ url, method: 'PUT', data, ...opts }),
  delete: <T = any>(url: string, params?: Record<string, any>, opts?: Partial<RequestOptions>) =>
    request<T>({ url, method: 'DELETE', params, ...opts }),
}

export default request
