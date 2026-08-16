<template>
  <view class="detail" v-if="product">
    <!-- 商品图片轮播 + 视频 -->
    <swiper
      class="detail__swiper"
      :indicator-dots="mediaList.length > 1"
      :autoplay="false"
      circular
      indicator-active-color="#FF6B00"
      @change="onSwiperChange"
    >
      <swiper-item v-if="product.video">
        <video
          class="detail__video"
          :src="product.video"
          controls
          object-fit="contain"
        />
      </swiper-item>
      <swiper-item v-for="(img, idx) in product.images" :key="idx">
        <image class="detail__media" :src="img" mode="aspectFill" @click="previewImage(idx)" />
      </swiper-item>
      <swiper-item v-if="!product.video && !product.images?.length">
        <image class="detail__media" :src="product.cover || '/static/placeholder/product.png'" mode="aspectFill" />
      </swiper-item>
    </swiper>

    <view v-if="mediaList.length > 1" class="detail__media-count">
      {{ currentMedia + 1 }} / {{ mediaList.length }}
    </view>

    <!-- 商品名称、价格 -->
    <view class="detail__info card">
      <view class="detail__price-row">
        <view class="detail__price-main">
          <text class="detail__price-rmb">¥{{ formatPrice(product.price) }}</text>
          <text v-if="product.originPrice && product.originPrice > product.price" class="detail__price-origin">¥{{ formatPrice(product.originPrice) }}</text>
        </view>
        <view class="detail__price-lsc">
          <text class="detail__price-lsc-num">{{ Math.floor(product.lscPrice) }}</text>
          <text class="detail__price-lsc-unit">LSC</text>
        </view>
      </view>
      <text class="detail__name">{{ product.name }}</text>
      <view class="detail__meta">
        <text v-if="product.sales" class="fs-sm text-secondary">已售 {{ product.sales }}</text>
        <text v-if="product.stock" class="fs-sm text-secondary">库存 {{ product.stock }}</text>
      </view>
    </view>

    <!-- 商家门店信息卡片 -->
    <view v-if="product.store" class="detail__store card" @click="goStoreMap">
      <view class="detail__store-icon">🏪</view>
      <view class="detail__store-info">
        <text class="fw-bold">{{ product.store.name }}</text>
        <text class="fs-sm text-secondary text-ellipsis">{{ product.store.address }}</text>
        <text v-if="product.store.distance" class="fs-sm text-primary">距您 {{ product.store.distance }}km</text>
      </view>
      <view class="detail__store-nav" @click.stop="navigateToStore">
        <text class="detail__store-nav-icon">🧭</text>
        <text class="fs-sm text-primary">导航到店</text>
      </view>
    </view>

    <!-- 商品描述 -->
    <view class="detail__desc card">
      <text class="fw-bold">商品详情</text>
      <rich-text v-if="product.description" class="detail__desc-content" :nodes="product.description"></rich-text>
      <text v-else class="fs-sm text-secondary">暂无详细描述</text>
    </view>

    <!-- 规格选择 -->
    <view v-if="product.specs && product.specs.length" class="detail__spec card">
      <text class="fw-bold">规格选择</text>
      <view v-for="spec in product.specs" :key="spec.id" class="detail__spec-group">
        <text class="fs-sm text-secondary">{{ spec.name }}</text>
        <view class="detail__spec-values">
          <text
            v-for="v in spec.values"
            :key="v"
            class="detail__spec-value"
            :class="{ 'detail__spec-value--active': selectedSpec[spec.id] === v }"
            @click="selectSpec(spec.id, v)"
          >{{ v }}</text>
        </view>
      </view>
    </view>

    <view style="height: 140rpx"></view>

    <!-- 底部操作栏 -->
    <view class="detail__footer footer-bar">
      <view class="detail__footer-icon" @click="goHome">
        <text class="detail__footer-icon-emoji">🏠</text>
        <text class="fs-sm">首页</text>
      </view>
      <view class="detail__footer-icon" @click="goCart">
        <text class="detail__footer-icon-emoji">🛒</text>
        <text class="fs-sm">购物车</text>
      </view>
      <view class="detail__footer-icon" @click="goService">
        <text class="detail__footer-icon-emoji">🤖</text>
        <text class="fs-sm">客服</text>
      </view>
      <button class="detail__btn detail__btn--cart" @click="onAddCart">加入购物车</button>
      <button class="detail__btn detail__btn--buy" @click="onBuyNow">立即购买</button>
    </view>
  </view>

  <view v-else class="detail__loading">
    <text>加载中...</text>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProductDetail, type Product } from '@/api/product'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { openNavigation } from '@/api/map'

const product = ref<Product | null>(null)
const currentMedia = ref(0)
const quantity = ref(1)
const selectedSpec = reactive<Record<number, string>>({})

const cartStore = useCartStore()
const userStore = useUserStore()

const mediaList = computed(() => {
  const list: string[] = []
  if (product.value?.video) list.push(product.value.video)
  if (product.value?.images?.length) list.push(...product.value.images)
  else if (product.value?.cover) list.push(product.value.cover)
  return list
})

async function loadDetail(id: string) {
  try {
    product.value = await getProductDetail(id)
    // 默认选中第一个规格
    product.value?.specs?.forEach((spec) => {
      if (spec.values.length) selectedSpec[spec.id] = spec.values[0]
    })
  } catch (e) {
    // ignore
  }
}

function formatPrice(n: number): string {
  return (Number(n) || 0).toFixed(2)
}

function onSwiperChange(e: any) {
  currentMedia.value = e.detail.current
}

function previewImage(idx: number) {
  const images = product.value?.images || []
  if (!images.length) return
  uni.previewImage({ urls: images, current: images[idx] })
}

function selectSpec(specId: number, value: string) {
  selectedSpec[specId] = value
}

function getSpecText(): string {
  return Object.values(selectedSpec).join(' / ')
}

function onAddCart() {
  if (!product.value) return
  cartStore.add(product.value, quantity.value, getSpecText())
}

function onBuyNow() {
  if (!userStore.isLoggedIn) {
    uni.navigateTo({ url: '/src/pages-account/login/index' })
    return
  }
  if (!product.value) return
  const items = [{ productId: product.value.id, quantity: quantity.value, spec: getSpecText() }]
  uni.navigateTo({
    url: `/src/pages-order/create/index?items=${encodeURIComponent(JSON.stringify(items))}`,
  })
}

function navigateToStore() {
  if (!product.value?.store) return
  openNavigation({
    name: product.value.store.name,
    address: product.value.store.address,
    latitude: product.value.store.latitude,
    longitude: product.value.store.longitude,
  })
}

function goStoreMap() {
  if (!product.value?.store) return
  uni.navigateTo({
    url: `/src/pages-store/map/index?lat=${product.value.store.latitude}&lng=${product.value.store.longitude}&name=${encodeURIComponent(product.value.store.name)}&addr=${encodeURIComponent(product.value.store.address)}`,
  })
}

function goHome() {
  uni.switchTab({ url: '/src/pages/home/index' })
}

function goCart() {
  uni.switchTab({ url: '/src/pages/cart/index' })
}

function goService() {
  uni.navigateTo({ url: '/src/pages-ai/assistant/index' })
}

onLoad((options) => {
  if (options?.id) loadDetail(options.id)
})
</script>

<style lang="scss" scoped>
.detail {
  padding-bottom: 40rpx;

  &__swiper {
    width: 100%;
    height: 750rpx;
    background: $bg-gray;
  }

  &__video,
  &__media {
    width: 100%;
    height: 750rpx;
  }

  &__media-count {
    position: absolute;
    right: $spacing-base;
    top: 700rpx;
    background: rgba(0, 0, 0, 0.5);
    color: #fff;
    font-size: $font-xs;
    padding: 4rpx 16rpx;
    border-radius: 999rpx;
  }

  &__info {
    margin: $spacing-base;
  }

  &__price-row {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    margin-bottom: $spacing-sm;
  }

  &__price-main {
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
  }

  &__price-rmb {
    font-size: 48rpx;
    color: $primary;
    font-weight: 700;
  }

  &__price-origin {
    font-size: $font-sm;
    color: $text-placeholder;
    text-decoration: line-through;
  }

  &__price-lsc {
    background: $lsc-color-bg;
    border-radius: $radius-base;
    padding: $spacing-xs $spacing-base;
    display: flex;
    align-items: baseline;
    gap: 4rpx;
  }

  &__price-lsc-num {
    color: $lsc-color;
    font-size: $font-md;
    font-weight: 700;
  }

  &__price-lsc-unit {
    color: $lsc-color;
    font-size: $font-xs;
  }

  &__name {
    font-size: $font-md;
    color: $text-primary;
    line-height: 1.5;
    font-weight: 600;
  }

  &__meta {
    display: flex;
    gap: $spacing-lg;
    margin-top: $spacing-sm;
  }

  &__store {
    margin: $spacing-base;
    display: flex;
    align-items: center;
    gap: $spacing-base;
    border-left: 8rpx solid $primary;
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

  &__store-nav {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4rpx;
    padding: $spacing-sm;
    border-left: 1rpx solid $border-color-light;
  }

  &__store-nav-icon {
    font-size: 36rpx;
  }

  &__desc {
    margin: $spacing-base;
  }

  &__desc-content {
    margin-top: $spacing-sm;
    display: block;
  }

  &__spec {
    margin: $spacing-base;
  }

  &__spec-group {
    margin-top: $spacing-base;
  }

  &__spec-values {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-sm;
    margin-top: $spacing-xs;
  }

  &__spec-value {
    padding: $spacing-xs $spacing-base;
    background: $bg-gray;
    border-radius: $radius-sm;
    font-size: $font-sm;
    color: $text-regular;
    border: 1rpx solid transparent;

    &--active {
      background: $primary-bg;
      color: $primary;
      border-color: $primary;
      font-weight: 600;
    }
  }

  &__footer {
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
  }

  &__footer-icon {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2rpx;
    padding: 0 $spacing-xs;
  }

  &__footer-icon-emoji {
    font-size: 36rpx;
  }

  &__btn {
    color: #fff;
    border: none;
    border-radius: 999rpx;
    font-size: $font-sm;
    height: 72rpx;
    line-height: 72rpx;
    padding: 0 $spacing-lg;
    margin: 0;

    &--cart {
      background: linear-gradient(135deg, $lsc-color, $lsc-color-light);
    }

    &--buy {
      background: linear-gradient(135deg, $primary, $primary-light);
    }
  }

  &__loading {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100vh;
    color: $text-secondary;
  }
}
</style>
