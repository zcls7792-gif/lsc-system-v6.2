import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig
} from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useMerchantStore } from '@/stores/merchant'
import router from '@/router'

/**
 * 后端统一响应结构 (com.lianshengtong.common.result.R)
 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T = unknown> {
  /** 当前页 (从1开始) */
  current: number
  /** 每页条数 */
  size: number
  /** 总页数 */
  pages: number
  /** 总条数 */
  total: number
  /** 数据列表 */
  records: T[]
}

const service: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 20000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求拦截：注入商家 token
service.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const merchant = useMerchantStore()
    if (merchant.token) {
      config.headers.set('Authorization', `Bearer ${merchant.token}`)
    }
    return config
  },
  (error) => Promise.reject(error)
)

// 是否已弹出登录失效提示 (避免重复弹窗)
let authExpiredHandling = false

// 响应拦截：统一解包 R<T>
service.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const res = response.data
    // 二进制文件流直接返回
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response as unknown as AxiosResponse
    }

    // code === 0 视为成功
    if (res.code === 0) {
      return res.data as any
    }

    // 业务失败
    ElMessage({
      message: res.message || '请求失败',
      type: 'error',
      duration: 3000
    })
    return Promise.reject(new Error(res.message || 'Error'))
  },
  (error) => {
    const status = error?.response?.status
    const resp = error?.response?.data

    // 401 / token 失效
    if (status === 401 || resp?.code === 401) {
      handleAuthExpired()
      return Promise.reject(error)
    }

    // 403 无权限
    if (status === 403) {
      ElMessage.error('无权限执行该操作')
      return Promise.reject(error)
    }

    const msg =
      (resp && (resp.message || resp.msg)) ||
      error.message ||
      '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

function handleAuthExpired() {
  if (authExpiredHandling) return
  authExpiredHandling = true
  const merchant = useMerchantStore()
  merchant.logout()
  ElMessageBox.alert('登录状态已失效，请重新登录', '提示', {
    confirmButtonText: '重新登录',
    type: 'warning',
    showClose: false
  })
    .then(() => {
      router.replace({
        path: '/login',
        query: { redirect: router.currentRoute.value.fullPath }
      })
    })
    .finally(() => {
      authExpiredHandling = false
    })
}

/** GET */
export function get<T = unknown>(url: string, params?: Record<string, any>, config?: AxiosRequestConfig) {
  return service.get<unknown, T>(url, { params, ...config })
}

/** POST */
export function post<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return service.post<unknown, T>(url, data, config)
}

/** PUT */
export function put<T = unknown>(url: string, data?: unknown, config?: AxiosRequestConfig) {
  return service.put<unknown, T>(url, data, config)
}

/** DELETE */
export function del<T = unknown>(url: string, params?: Record<string, any>, config?: AxiosRequestConfig) {
  return service.delete<unknown, T>(url, { params, ...config })
}

/** 文件上传 (FormData) */
export function upload<T = unknown>(url: string, formData: FormData, config?: AxiosRequestConfig) {
  return service.post<unknown, T>(url, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    ...config
  })
}

export default service
