<template>
  <view class="store-map">
    <!-- 地图 -->
    <view class="store-map__map">
      <map
        class="store-map__map-el"
        :latitude="latitude"
        :longitude="longitude"
        :markers="markers"
        :scale="16"
        show-location
        @markertap="onMarkerTap"
      ></map>
    </view>

    <!-- 门店信息卡片 -->
    <view class="store-map__card">
      <view class="store-map__card-header">
        <view class="store-map__store-icon">🏪</view>
        <view class="store-map__store-info">
          <text class="fw-bold fs-md">{{ store.name || '商家门店' }}</text>
          <text class="fs-sm text-secondary text-ellipsis">{{ store.address || '暂无地址' }}</text>
        </view>
      </view>

      <view v-if="store.phone" class="store-map__card-row">
        <text class="fs-sm text-secondary">📞 电话</text>
        <text class="fs-sm text-primary" @click="onCall">{{ store.phone }} 拨打</text>
      </view>

      <view class="store-map__card-actions">
        <button class="store-map__btn store-map__btn--call" @click="onCall">
          <text class="store-map__btn-icon">📞</text>
          <text>电话</text>
        </button>
        <button class="store-map__btn store-map__btn--nav" @click="onNavigate">
          <text class="store-map__btn-icon">🧭</text>
          <text>导航到店</text>
        </button>
      </view>
    </view>

    <!-- 附近门店列表（可选） -->
    <view v-if="nearby.length" class="store-map__nearby">
      <text class="fw-bold">附近门店</text>
      <scroll-view scroll-x :show-scrollbar="false" class="store-map__nearby-scroll">
        <view class="store-map__nearby-list">
          <view
            v-for="(s, idx) in nearby"
            :key="idx"
            class="store-map__nearby-item"
            @click="selectStore(s)"
          >
            <text class="fw-bold fs-sm text-ellipsis">{{ s.name }}</text>
            <text class="fs-sm text-secondary text-ellipsis">{{ s.address }}</text>
            <text v-if="s.distance" class="fs-sm text-primary">{{ s.distance }}km</text>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { openNavigation, getCurrentLocation, type LocationPoint } from '@/api/map'
import { getNearbyStores, type StoreInfo } from '@/api/product'

const store = ref<Partial<LocationPoint>>({
  name: '商家门店',
  address: '',
  latitude: 39.908823,
  longitude: 116.397470,
})

const latitude = ref(39.908823)
const longitude = ref(116.397470)
const nearby = ref<StoreInfo[]>([])

const markers = computed(() => [
  {
    id: 1,
    latitude: store.value.latitude,
    longitude: store.value.longitude,
    title: store.value.name,
    width: 32,
    height: 32,
  },
])

async function loadNearby() {
  try {
    const loc = await getCurrentLocation()
    nearby.value = await getNearbyStores(loc.latitude, loc.longitude)
  } catch (e) {
    // 定位失败忽略
  }
}

function selectStore(s: StoreInfo) {
  store.value = {
    name: s.name,
    address: s.address,
    latitude: s.latitude,
    longitude: s.longitude,
  }
  latitude.value = s.latitude
  longitude.value = s.longitude
}

function onMarkerTap() {
  // 可选：展示 marker 详情
}

function onCall() {
  if (!store.value.phone) {
    uni.showToast({ title: '暂无商家电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({ phoneNumber: store.value.phone })
}

function onNavigate() {
  openNavigation({
    name: store.value.name || '商家门店',
    address: store.value.address || '',
    latitude: store.value.latitude!,
    longitude: store.value.longitude!,
  })
}

onLoad((options) => {
  if (options?.lat) latitude.value = Number(options.lat)
  if (options?.lng) longitude.value = Number(options.lng)
  if (options?.name) store.value.name = decodeURIComponent(options.name)
  if (options?.addr) store.value.address = decodeURIComponent(options.addr)
  if (options?.lat) store.value.latitude = Number(options.lat)
  if (options?.lng) store.value.longitude = Number(options.lng)
  loadNearby()
})
</script>

<style lang="scss" scoped>
.store-map {
  display: flex;
  flex-direction: column;
  height: 100vh;

  &__map {
    flex: 1;
    position: relative;
  }

  &__map-el {
    width: 100%;
    height: 100%;
  }

  &__card {
    background: #fff;
    border-radius: $radius-xl $radius-xl 0 0;
    padding: $spacing-base;
    margin-top: -32rpx;
    position: relative;
    z-index: 2;
    box-shadow: 0 -4rpx 16rpx rgba(0, 0, 0, 0.06);
  }

  &__card-header {
    display: flex;
    gap: $spacing-base;
    align-items: center;
    padding-bottom: $spacing-base;
    border-bottom: 1rpx solid $border-color-light;
  }

  &__store-icon {
    font-size: 48rpx;
  }

  &__store-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    min-width: 0;
  }

  &__card-row {
    display: flex;
    justify-content: space-between;
    padding: $spacing-sm 0;
  }

  &__card-actions {
    display: flex;
    gap: $spacing-base;
    padding-top: $spacing-base;
  }

  &__btn {
    flex: 1;
    border: none;
    border-radius: $radius-base;
    height: 80rpx;
    display: flex;
    align-items: center;
    justify-content: center;
    gap: $spacing-sm;
    font-size: $font-base;
    margin: 0;

    &--call {
      background: $bg-gray;
      color: $text-regular;
    }

    &--nav {
      background: linear-gradient(135deg, $primary, $primary-light);
      color: #fff;
    }
  }

  &__btn-icon {
    font-size: 32rpx;
  }

  &__nearby {
    background: #fff;
    padding: $spacing-base;
    border-top: 1rpx solid $border-color-light;
  }

  &__nearby-scroll {
    white-space: nowrap;
    margin-top: $spacing-sm;
  }

  &__nearby-list {
    display: inline-flex;
    gap: $spacing-base;
  }

  &__nearby-item {
    display: inline-flex;
    flex-direction: column;
    gap: 4rpx;
    width: 280rpx;
    padding: $spacing-sm;
    background: $bg-gray;
    border-radius: $radius-base;
    vertical-align: top;
  }
}
</style>
