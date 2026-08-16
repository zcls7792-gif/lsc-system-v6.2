import { http } from '@/utils/request'

/** 地图位置点 */
export interface LocationPoint {
  name: string
  address: string
  latitude: number
  longitude: number
}

/**
 * 唤起原生地图导航（使用 uni.openLocation）
 * 适用于：商家门店导航、收货地址选点查看
 */
export function openNavigation(location: LocationPoint): Promise<void> {
  return new Promise((resolve, reject) => {
    uni.openLocation({
      latitude: location.latitude,
      longitude: location.longitude,
      name: location.name,
      address: location.address,
      scale: 18,
      success: () => resolve(),
      fail: (err) => {
        uni.showToast({ title: '打开地图失败', icon: 'none' })
        reject(err)
      },
    })
  })
}

/** 获取当前位置 */
export function getCurrentLocation(): Promise<{ latitude: number; longitude: number }> {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      success: (res) => resolve({ latitude: res.latitude, longitude: res.longitude }),
      fail: (err) => {
        uni.showToast({ title: '获取定位失败', icon: 'none' })
        reject(err)
      },
    })
  })
}

/** 选择位置（地址选点） */
export function chooseLocation(): Promise<LocationPoint> {
  return new Promise((resolve, reject) => {
    uni.chooseLocation({
      success: (res) => {
        resolve({
          name: res.name,
          address: res.address,
          latitude: res.latitude,
          longitude: res.longitude,
        })
      },
      fail: (err) => reject(err),
    })
  })
}

/** 地址管理接口 */
export interface Address {
  id: number
  name: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  /** 是否默认 */
  isDefault: boolean
  latitude?: number
  longitude?: number
}

export function getAddressList() {
  return http.get<Address[]>('/api/user/address/list')
}

export function getAddressDetail(id: number | string) {
  return http.get<Address>('/api/user/address/detail', { id })
}

export function saveAddress(data: Partial<Address>) {
  return http.post<Address>('/api/user/address/save', data)
}

export function deleteAddress(id: number | string) {
  return http.delete<void>('/api/user/address/delete', { id })
}

export function setDefaultAddress(id: number | string) {
  return http.post<void>('/api/user/address/default', { id })
}
