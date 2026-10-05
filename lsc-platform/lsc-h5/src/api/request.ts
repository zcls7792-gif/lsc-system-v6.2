import axios, { type AxiosRequestConfig } from 'axios'
import { showToast } from 'vant'

const BASE_URL = '/v1'

const service = axios.create({
  baseURL: BASE_URL,
  timeout: 15000
})

// 请求拦截：注入 token
service.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers = config.headers || {}
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// 响应拦截：统一解包 ApiResponse { code, message, data }
service.interceptors.response.use(
  (response) => {
    const body = response.data
    // 一次性响应适配：兼容统一返回体
    if (body && typeof body === 'object' && 'code' in body) {
      if (body.code === 0) {
        return body.data
      }
      if (body.code === 401) {
        localStorage.removeItem('token')
        window.location.href = '/mine'
        return Promise.reject(new Error('未登录'))
      }
      showToast({ message: body.message || '请求失败', type: 'fail' })
      return Promise.reject(new Error(body.message))
    }
    return body
  },
  (error) => {
    showToast({ message: '网络错误', type: 'fail' })
    return Promise.reject(error)
  }
)

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export function get<T = any>(url: string, params?: any, config?: AxiosRequestConfig): Promise<T> {
  return service.get(url, { params, ...config }) as unknown as Promise<T>
}

export function post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
  return service.post(url, data, config) as unknown as Promise<T>
}
