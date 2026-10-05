// 请求层：统一适配后端 ApiResponse { code, message, data }
// 业务页面只处理单一 data 形态，降低前后端响应结构差异

const BASE_URL = 'https://api.lsc.example.com/v1'

function getToken(): string {
  return uni.getStorageSync('token') || ''
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  header?: Record<string, string>
  loading?: boolean
}

/**
 * 统一请求封装：
 * 1. 注入 token
 * 2. 解包 ApiResponse，业务层直接拿到 data
 * 3. code !== 0 时抛出 BusinessError
 * 4. 401 跳转登录
 */
export function request<T = any>(opts: RequestOptions): Promise<T> {
  const { url, method = 'GET', data, header = {}, loading = true } = opts

  if (loading) {
    uni.showLoading({ title: '加载中', mask: true })
  }

  const token = getToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...header
  }
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: headers,
      success: (res) => {
        const body = res.data as ApiResult<T>
        // 一次性响应适配：兼容 ApiResponse 包装
        if (body && typeof body === 'object' && 'code' in body) {
          if (body.code === 0) {
            resolve(body.data)
          } else if (body.code === 401) {
            uni.removeStorageSync('token')
            uni.reLaunch({ url: '/pages/mine/index' })
            reject(new Error('未登录'))
          } else {
            uni.showToast({ title: body.message || '请求失败', icon: 'none' })
            reject(new Error(body.message))
          }
        } else {
          // 兼容直接返回数据的接口
          resolve(body as unknown as T)
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络错误', icon: 'none' })
        reject(err)
      },
      complete: () => {
        if (loading) uni.hideLoading()
      }
    })
  })
}

export function get<T = any>(url: string, data?: any) {
  return request<T>({ url, method: 'GET', data })
}

export function post<T = any>(url: string, data?: any) {
  return request<T>({ url, method: 'POST', data })
}
