import { get } from '@/utils/request'
import type { AmapPoi } from './types'

/**
 * 地图定位 — 通过后端代理调用高德 Web 服务 API (避免在前端暴露 key)
 * 后端: /api/map/*
 */

/** 关键字搜索 POI */
export function searchPois(keyword: string, city?: string) {
  return get<AmapPoi[]>('/map/pois', { keyword, city })
}

/** 逆地理编码 (经纬度 -> 行政区+地址) */
export interface RegeocodeResult {
  province: string
  city: string
  district: string
  address: string
  formattedAddress: string
}

export function regeocode(longitude: number, latitude: number) {
  return get<RegeocodeResult>('/map/reverse-geocode', { longitude, latitude })
}

/** IP 定位 (粗略城市定位) */
export function ipLocate() {
  return get<{ city: string; longitude: number; latitude: number }>('/map/ip-locate')
}

/**
 * 高德地图 JS SDK 动态加载 (前端渲染地图实例)
 * 由 .env 注入 key 与安全密钥
 */
let amapLoader: Promise<any> | null = null

export function loadAmapJs(): Promise<any> {
  if (amapLoader) return amapLoader
  amapLoader = new Promise((resolve, reject) => {
    if ((window as any).AMap) {
      resolve((window as any).AMap)
      return
    }
    const key = import.meta.env.VITE_AMAP_KEY || ''
    const securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE || ''
    if (securityCode) {
      ;(window as any)._AMapSecurityConfig = { securityJsCode: securityCode }
    }
    const script = document.createElement('script')
    script.src = `https://webapi.amap.com/maps?v=2.0&key=${key}&plugin=AMap.PlaceSearch,AMap.Geocoder,AMap.AutoComplete,AMap.CitySearch`
    script.async = true
    script.onload = () => resolve((window as any).AMap)
    script.onerror = () => reject(new Error('高德地图 JS SDK 加载失败'))
    document.head.appendChild(script)
  })
  return amapLoader
}
