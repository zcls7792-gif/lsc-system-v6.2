<script setup lang="ts">
// 地图选点组件 — 集成高德地图 JS SDK
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, LocationFilled } from '@element-plus/icons-vue'
import { loadAmapJs, searchPois, regeocode } from '@/api/map'
import type { AmapPoi } from '@/api/types'

interface Props {
  modelValue?: { longitude: number; latitude: number; address?: string }
  /** 默认城市 */
  city?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({ longitude: 116.397428, latitude: 39.90923 }),
  city: '北京'
})

const emit = defineEmits<{
  (e: 'update:modelValue', v: { longitude: number; latitude: number; address?: string }): void
  (e: 'select', v: AmapPoi): void
}>()

const mapContainer = ref<HTMLDivElement | null>(null)
const keyword = ref('')
const poiList = ref<AmapPoi[]>([])
const loadingPois = ref(false)
const currentLng = ref(props.modelValue.longitude)
const currentLat = ref(props.modelValue.latitude)
const currentAddress = ref(props.modelValue.address || '')
const center = ref<{ lng: number; lat: number }>({ lng: currentLng.value, lat: currentLat.value })

let AMap: any = null
let map: any = null
let marker: any = null
let placeSearch: any = null
let geocoder: any = null

onMounted(async () => {
  try {
    AMap = await loadAmapJs()
    initMap()
  } catch (e: any) {
    ElMessage.warning('地图加载失败，可手动输入经纬度')
  }
})

onBeforeUnmount(() => {
  if (map) {
    map.destroy()
    map = null
  }
})

function initMap() {
  if (!mapContainer.value) return
  map = new AMap.Map(mapContainer.value, {
    zoom: 14,
    center: [center.value.lng, center.value.lat],
    viewMode: '2D'
  })

  marker = new AMap.Marker({
    position: [currentLng.value, currentLat.value],
    draggable: true
  })
  map.add(marker)

  // 拖拽 marker
  marker.on('dragend', (e: any) => {
    const lng = e.lnglat.getLng()
    const lat = e.lnglat.getLat()
    setPosition(lng, lat, true)
  })

  // 点击地图选址
  map.on('click', (e: any) => {
    const lng = e.lnglat.getLng()
    const lat = e.lnglat.getLat()
    marker.setPosition([lng, lat])
    setPosition(lng, lat, true)
  })

  placeSearch = new AMap.PlaceSearch({ pageSize: 12, pageIndex: 1, city: props.city })
  geocoder = new AMap.Geocoder()
}

async function setPosition(lng: number, lat: number, reverseGeocode = false) {
  currentLng.value = lng
  currentLat.value = lat
  if (reverseGeocode) {
    try {
      // 优先走后端逆地理
      const res = await regeocode(lng, lat)
      currentAddress.value = res.formattedAddress || res.address
    } catch {
      // 退回 SDK
      if (geocoder) {
        geocoder.getAddress([lng, lat], (status: string, result: any) => {
          if (status === 'complete' && result.regeocode) {
            currentAddress.value = result.regeocode.formattedAddress
          }
        })
      }
    }
  }
  emit('update:modelValue', {
    longitude: lng,
    latitude: lat,
    address: currentAddress.value
  })
}

async function handleSearch() {
  if (!keyword.value.trim()) {
    poiList.value = []
    return
  }
  loadingPois.value = true
  try {
    const list = await searchPois(keyword.value, props.city)
    poiList.value = list || []
    if (list.length === 0) {
      ElMessage.info('未找到相关地点')
    }
  } catch {
    // 退回 SDK PlaceSearch
    if (placeSearch) {
      placeSearch.search(keyword.value, (status: string, result: any) => {
        if (status === 'complete' && result.poiList) {
          poiList.value = (result.poiList.pois || []).map((p: any) => ({
            name: p.name,
            address: p.address || p.name,
            longitude: p.location.getLng(),
            latitude: p.location.getLat(),
            pname: p.pname,
            cityname: p.cityname,
            adname: p.adname
          }))
        } else {
          poiList.value = []
        }
      })
    }
  } finally {
    loadingPois.value = false
  }
}

function selectPoi(poi: AmapPoi) {
  currentLng.value = poi.longitude
  currentLat.value = poi.latitude
  currentAddress.value = [poi.pname, poi.cityname, poi.adname, poi.address].filter(Boolean).join('')
  center.value = { lng: poi.longitude, lat: poi.latitude }
  if (map && marker) {
    map.setCenter([poi.longitude, poi.latitude])
    marker.setPosition([poi.longitude, poi.latitude])
  }
  emit('update:modelValue', {
    longitude: poi.longitude,
    latitude: poi.latitude,
    address: currentAddress.value
  })
  emit('select', poi)
}

watch(
  () => props.modelValue,
  (v) => {
    if (v && (v.longitude !== currentLng.value || v.latitude !== currentLat.value)) {
      currentLng.value = v.longitude
      currentLat.value = v.latitude
      if (map && marker) {
        map.setCenter([v.longitude, v.latitude])
        marker.setPosition([v.longitude, v.latitude])
      }
    }
  }
)
</script>

<template>
  <div class="map-picker">
    <div class="map-picker__search">
      <el-input
        v-model="keyword"
        placeholder="搜索地点 / 商圈 / 门牌号"
        clearable
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #append>
          <el-button :loading="loadingPois" @click="handleSearch">搜索</el-button>
        </template>
      </el-input>
    </div>

    <div class="map-picker__body">
      <div ref="mapContainer" class="map-picker__map"></div>
      <div class="map-picker__panel">
        <div class="map-picker__panel-head">搜索结果</div>
        <div v-if="poiList.length === 0" class="map-picker__empty">输入关键词后点搜索</div>
        <div v-for="poi in poiList" :key="poi.name + poi.longitude" class="map-picker__poi" @click="selectPoi(poi)">
          <el-icon class="map-picker__poi-icon"><LocationFilled /></el-icon>
          <div class="map-picker__poi-body">
            <div class="map-picker__poi-name">{{ poi.name }}</div>
            <div class="map-picker__poi-addr">{{ [poi.pname, poi.cityname, poi.adname, poi.address].filter(Boolean).join('') }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="map-picker__result">
      <div class="map-picker__result-row">
        <span class="map-picker__label">经度</span>
        <span class="lsc-num">{{ currentLng.toFixed(6) }}</span>
      </div>
      <div class="map-picker__result-row">
        <span class="map-picker__label">纬度</span>
        <span class="lsc-num">{{ currentLat.toFixed(6) }}</span>
      </div>
      <div class="map-picker__result-row map-picker__result-row--wide">
        <span class="map-picker__label">地址</span>
        <span class="map-picker__addr">{{ currentAddress || '点击地图或选择地点' }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.map-picker {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.map-picker__body {
  display: flex;
  gap: 12px;
  height: 320px;
}

.map-picker__map {
  flex: 1;
  border-radius: 12px;
  border: 1px solid var(--lsc-border);
  overflow: hidden;
  background: var(--lsc-bg-soft);
}

.map-picker__panel {
  width: 240px;
  border: 1px solid var(--lsc-border);
  border-radius: 12px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.map-picker__panel-head {
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--lsc-text-secondary);
  background: var(--lsc-bg-soft);
  border-bottom: 1px solid var(--lsc-border-soft);
}

.map-picker__empty {
  padding: 24px 12px;
  font-size: 12px;
  color: var(--lsc-text-placeholder);
  text-align: center;
}

.map-picker__poi {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  border-bottom: 1px solid var(--lsc-border-soft);
  transition: background 0.16s ease;
}

.map-picker__poi:hover {
  background: var(--lsc-primary-50);
}

.map-picker__poi-icon {
  color: var(--lsc-primary-600);
  margin-top: 2px;
}

.map-picker__poi-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--lsc-text);
}

.map-picker__poi-addr {
  font-size: 11.5px;
  color: var(--lsc-text-secondary);
  margin-top: 2px;
  line-height: 1.4;
}

.map-picker__result {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  padding: 12px 14px;
  background: var(--lsc-bg-soft);
  border-radius: 10px;
  font-size: 13px;
}

.map-picker__result-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.map-picker__result-row--wide {
  grid-column: 1 / -1;
}

.map-picker__label {
  color: var(--lsc-text-secondary);
  min-width: 28px;
}

.map-picker__addr {
  color: var(--lsc-text-regular);
}
</style>
