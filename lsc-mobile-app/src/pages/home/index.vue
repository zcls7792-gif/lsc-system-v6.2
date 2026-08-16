<template>
  <view class="home">
    <!-- 顶部搜索栏 -->
    <view class="home__search">
      <view class="home__search-box" @click="goSearch">
        <text class="home__search-icon">🔍</text>
        <text class="home__search-placeholder">搜索商品 / 商家门店</text>
      </view>
      <view class="home__scan" @click="onScan">
        <text class="home__scan-icon">⌖</text>
      </view>
    </view>

    <!-- 轮播图 -->
    <view class="home__banner">
      <swiper
        class="home__swiper"
        :indicator-dots="true"
        :autoplay="true"
        :interval="4000"
        :duration="500"
        indicator-active-color="#FF6B00"
        indicator-color="rgba(255,255,255,0.5)"
        circular
      >
        <swiper-item v-for="b in banners" :key="b.id" @click="onBannerClick(b)">
          <image class="home__banner-img" :src="b.image" mode="aspectFill" />
        </swiper-item>
        <swiper-item v-if="!banners.length">
          <image class="home__banner-img" src="/static/placeholder/banner.png" mode="aspectFill" />
        </swiper-item>
      </swiper>
    </view>

    <!-- 商品分类入口（宫格） -->
    <view class="home__categories card">
      <view
        v-for="c in categories"
        :key="c.id"
        class="home__category-item"
        @click="goCategory(c)"
      >
        <view class="home__category-icon">
          <image v-if="c.icon" :src="c.icon" mode="aspectFit" class="home__category-img" />
          <text v-else class="home__category-emoji">🛍️</text>
        </view>
        <text class="home__category-name">{{ c.name }}</text>
      </view>
      <view class="home__category-item" @click="goStoreMap">
        <view class="home__category-icon">
          <text class="home__category-emoji">📍</text>
        </view>
        <text class="home__category-name">附近门店</text>
      </view>
    </view>

    <!-- AI 推荐商品横向滚动 -->
    <view class="home__section">
      <view class="home__section-header">
        <view class="home__section-title">
          <text class="home__section-tag">AI</text>
          <text class="fw-bold">为你推荐</text>
        </view>
        <text class="home__section-more" @click="goMall">更多 ›</text>
      </view>
      <scroll-view scroll-x class="home__recommend" :show-scrollbar="false">
        <view class="home__recommend-list">
          <view
            v-for="p in recommend"
            :key="p.id"
            class="home__recommend-item"
          >
            <ProductCard :product="p" mode="card" style="width: 260rpx" />
          </view>
          <EmptyState v-if="!recommend.length" text="暂无推荐" :icon-text="''" />
        </view>
      </scroll-view>
    </view>

    <!-- 热门商品列表 -->
    <view class="home__section">
      <view class="home__section-header">
        <text class="fw-bold">🔥 热门商品</text>
      </view>
      <view class="home__hot-grid">
        <view
          v-for="p in hotProducts"
          :key="p.id"
          class="home__hot-item"
        >
          <ProductCard :product="p" mode="card" />
        </view>
      </view>
      <LoadMore v-if="hotProducts.length" :status="hotStatus" />
    </view>

    <!-- 商家门店地图导航入口 -->
    <view class="home__store-entry card" @click="goStoreMap">
      <view class="home__store-icon">🗺️</view>
      <view class="home__store-info">
        <text class="fw-bold">商家门店地图</text>
        <text class="fs-sm text-secondary">查看附近商家门店，导航到店消费</text>
      </view>
      <text class="home__store-arrow">›</text>
    </view>

    <view style="height: 40rpx"></view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import {
  getBanners,
  getCategories,
  getRecommendProducts,
  getHotProducts,
  type Product,
  type Category,
} from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'
import LoadMore from '@/components/LoadMore.vue'
import EmptyState from '@/components/EmptyState.vue'

const banners = ref<Array<{ id: number; image: string; link?: string; type?: string }>>([])
const categories = ref<Category[]>([])
const recommend = ref<Product[]>([])
const hotProducts = ref<Product[]>([])
const hotPage = ref(1)
const hotStatus = ref<'loadmore' | 'loading' | 'noMore' | 'error'>('loadmore')

async function loadAll() {
  try {
    const [b, c, r, h] = await Promise.all([
      getBanners().catch(() => []),
      getCategories().catch(() => []),
      getRecommendProducts(8).catch(() => []),
      getHotProducts(10).catch(() => []),
    ])
    banners.value = b as any
    categories.value = c as Category[]
    recommend.value = r as Product[]
    hotProducts.value = h as Product[]
    hotStatus.value = (h as Product[]).length < 10 ? 'noMore' : 'loadmore'
  } catch (e) {
    // 已在 request 中提示
  }
}

async function loadMoreHot() {
  if (hotStatus.value === 'loading' || hotStatus.value === 'noMore') return
  hotStatus.value = 'loading'
  try {
    hotPage.value++
    const list = await getHotProducts(10)
    if (!list.length) {
      hotStatus.value = 'noMore'
      hotPage.value--
      return
    }
    hotProducts.value.push(...list)
    hotStatus.value = list.length < 10 ? 'noMore' : 'loadmore'
  } catch (e) {
    hotStatus.value = 'error'
    hotPage.value--
  }
}

function goSearch() {
  uni.navigateTo({ url: '/src/pages/mall/index?focus=1' })
}

function goMall() {
  uni.switchTab({ url: '/src/pages/mall/index' })
}

function goCategory(c: Category) {
  uni.switchTab({ url: '/src/pages/mall/index' })
}

function goStoreMap() {
  uni.navigateTo({ url: '/src/pages-store/map/index' })
}

function onBannerClick(b: any) {
  if (b?.link) {
    uni.navigateTo({ url: b.link })
  }
}

function onScan() {
  uni.scanCode({
    success: (res) => {
      uni.showToast({ title: res.result, icon: 'none' })
    },
    fail: () => {},
  })
}

onMounted(loadAll)

onPullDownRefresh(async () => {
  hotPage.value = 1
  await loadAll()
  uni.stopPullDownRefresh()
})

onReachBottom(loadMoreHot)
</script>

<style lang="scss" scoped>
.home {
  padding-bottom: 40rpx;

  &__search {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm $spacing-base;
    background: #fff;
  }

  &__search-box {
    flex: 1;
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    height: 64rpx;
    background: $bg-gray;
    border-radius: 999rpx;
    padding: 0 $spacing-base;
  }

  &__search-icon {
    font-size: 28rpx;
  }

  &__search-placeholder {
    color: $text-placeholder;
    font-size: $font-sm;
  }

  &__scan {
    width: 64rpx;
    height: 64rpx;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__scan-icon {
    font-size: 40rpx;
    color: $primary;
  }

  &__banner {
    padding: $spacing-sm $spacing-base;
  }

  &__swiper {
    height: 320rpx;
    border-radius: $radius-lg;
    overflow: hidden;
  }

  &__banner-img {
    width: 100%;
    height: 320rpx;
    border-radius: $radius-lg;
    background: $bg-gray;
  }

  &__categories {
    display: flex;
    flex-wrap: wrap;
    margin: 0 $spacing-base;
    padding: $spacing-base $spacing-sm;
  }

  &__category-item {
    width: 20%;
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: $spacing-sm 0;
  }

  &__category-icon {
    width: 88rpx;
    height: 88rpx;
    border-radius: $radius-lg;
    background: $primary-bg;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  &__category-img {
    width: 56rpx;
    height: 56rpx;
  }

  &__category-emoji {
    font-size: 44rpx;
  }

  &__category-name {
    font-size: $font-xs;
    color: $text-regular;
    margin-top: $spacing-xs;
  }

  &__section {
    margin: $spacing-base;
  }

  &__section-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: $spacing-sm;
  }

  &__section-title {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__section-tag {
    background: linear-gradient(135deg, $primary, $primary-light);
    color: #fff;
    font-size: $font-xs;
    padding: 2rpx 10rpx;
    border-radius: $radius-sm;
    font-weight: 700;
  }

  &__section-more {
    font-size: $font-sm;
    color: $text-secondary;
  }

  &__recommend {
    white-space: nowrap;
  }

  &__recommend-list {
    display: inline-flex;
    gap: $spacing-base;
    padding-right: $spacing-base;
  }

  &__recommend-item {
    display: inline-block;
  }

  &__hot-grid {
    display: flex;
    flex-wrap: wrap;
    gap: $spacing-base;
  }

  &__hot-item {
    width: calc((100% - 24rpx) / 2);
  }

  &__store-entry {
    margin: $spacing-base;
    display: flex;
    align-items: center;
    gap: $spacing-base;
  }

  &__store-icon {
    font-size: 56rpx;
  }

  &__store-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
  }

  &__store-arrow {
    font-size: 40rpx;
    color: $text-placeholder;
  }
}
</style>
