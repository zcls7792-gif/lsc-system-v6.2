import { get, post, put, del } from '@/utils/request'
import { useMerchantStore } from '@/stores/merchant'
import type { MerchantExtension, StoreAddress } from './types'

/** 获取当前商家 userId */
function merchantUserId(): number | undefined {
  return useMerchantStore().profile?.userId
}

/** 获取商家店铺信息 (扩展表) */
export function getStoreInfo() {
  return get<MerchantExtension>('/merchant/info', { merchantId: merchantUserId() })
}

/** 更新店铺基本信息 */
export interface StoreInfoParams {
  storeName?: string
  contactPhone?: string
  businessHours?: string
  businessLicense?: string
  businessLicenseImg?: string
}

export function updateStoreInfo(data: StoreInfoParams) {
  return post<MerchantExtension>('/merchant/store/info', {
    merchantId: merchantUserId(),
    ...data
  })
}

/** 线下地址列表 */
export function getStoreAddresses() {
  return get<StoreAddress[]>('/merchant/store/addresses', {
    merchantId: merchantUserId()
  })
}

/** 添加 / 编辑线下地址 */
export interface StoreAddressParams {
  id?: number
  label?: string
  province: string
  city: string
  district: string
  addressDetail: string
  longitude: number
  latitude: number
  contactPhone?: string
  isPrimary?: number
}

export function saveStoreAddress(data: StoreAddressParams) {
  const body = {
    merchantId: merchantUserId(),
    ...data
  }
  if (data.id) {
    return put<StoreAddress>(`/merchant/store/addresses/${data.id}`, body)
  }
  return post<StoreAddress>('/merchant/store/addresses', body)
}

/** 删除地址 */
export function deleteStoreAddress(id: number) {
  return del<void>(`/merchant/store/addresses/${id}`, {
    merchantId: merchantUserId()
  })
}

/** 设置主地址 */
export function setPrimaryAddress(id: number) {
  return post<void>(`/merchant/store/addresses/${id}/primary`, {
    merchantId: merchantUserId()
  })
}

/** 当日地址修改次数提示 */
export interface AddressUpdateState {
  todayUpdatedCount: number
  /** 当日上限 */
  dailyLimit: number
  remaining: number
}

export function getAddressUpdateState() {
  return get<AddressUpdateState>('/merchant/store/addresses/update-state', {
    merchantId: merchantUserId()
  })
}
