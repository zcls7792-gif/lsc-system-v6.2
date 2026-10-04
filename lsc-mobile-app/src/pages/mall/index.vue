<template>
  <view class="mall">
    <!-- 搜索栏 -->
    <view class="mall__search">
      <view class="mall__search-box">
        <text class="mall__search-icon">🔍</text>
        <input
          class="mall__search-input"
          v-model="keyword"
          :focus="focusSearch"
          placeholder="搜索商品"
          confirm-type="search"
          @confirm="onSearch"
        />
        <text v-if="keyword" class="mall__search-clear" @click="keyword = ''">✕</text>
      </view>
    </view>

    <view class="mall__body">
      <!-- 商品列表 -->
      <scroll-view
        scroll-y
        class="mall__list"
        :refresher-enabled="true"
        :refresher-triggered="refreshing"
        @refresherrefresh="onRefresh"
        @scrolltolower="loadMore"
      >
        <view class="mall__list-header">
          <text class="fw-bold">{{ currentCatName }}</text>
        </view>

        <view class="mall__products">
          <view
            v-for="p in products"
            :key="p.productId"
            class="mall__product"
          >
            <ProductCard :product="p" mode="horizontal" :show-cart="true" />
          </view>
        </view>

        <LoadMore v-if="products.length" :status="loadStatus" />
        <EmptyState
          v-else-if="!loading"
          text="暂无商品"
          action-text="去首页看看"
          @action="goHome"
        />
      </scroll-view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getProductList, type Product } from '@/api/product'
import ProductCard from '@/components/ProductCard.vue'
import LoadMore from '@/components/LoadMore.vue'
import EmptyState from '@/components/EmptyState.vue'

const keyword = ref('')
const focusSearch = ref(false)
const products = ref<Product[]>([])
const page = ref(1)
const size = 10
const loading = ref(false)
const loadStatus = ref<'loadmore' | 'loading' | 'noMore' | 'error'>('loadmore')
const refreshing = ref(false)

const currentCatName = computed(() => '全部商品')

async function loadProducts(reset = false) {
  if (loading.value) return
  if (reset) {
    page.value = 1
    products.value = []
    loadStatus.value = 'loadmore'
  }
  loading.value = true
  loadStatus.value = 'loading'
  try {
    const res = await getProductList({
      page: page.value,
      size,
      buyerType: 'C',
    })
    const list = res.records || []
    if (reset) {
      products.value = list
    } else {
      products.value.push(...list)
    }
    loadStatus.value = list.length < size ? 'noMore' : 'loadmore'
  } catch (e) {
    loadStatus.value = 'error'
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function onSearch() {
  loadProducts(true)
}

function loadMore() {
  if (loadStatus.value !== 'loadmore') return
  page.value++
  loadProducts(false)
}

async function onRefresh() {
  refreshing.value = true
  await loadProducts(true)
}

function goHome() {
  uni.switchTab({ url: '/src/pages/home/index' })
}

onMounted(async () => {
  await loadProducts(true)
})

onLoad((options) => {
  if (options?.focus) focusSearch.value = true
})
</script>

<style lang="scss" scoped>
.mall {
  display: flex;
  flex-direction: column;
  height: 100vh;

  &__search {
    padding: $spacing-sm $spacing-base;
    background: #fff;
    flex-shrink: 0;
  }

  &__search-box {
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

  &__search-input {
    flex: 1;
    font-size: $font-sm;
  }

  &__search-clear {
    color: $text-placeholder;
    font-size: $font-sm;
  }

  &__body {
    flex: 1;
    display: flex;
    overflow: hidden;
  }

  &__sidebar {
    width: 180rpx;
    background: $bg-gray;
    height: 100%;
  }

  &__cat-item {
    padding: $spacing-base $spacing-sm;
    text-align: center;
    font-size: $font-sm;
    color: $text-regular;
    position: relative;

    &--active {
      background: #fff;
      color: $primary;
      font-weight: 600;

      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 50%;
        transform: translateY(-50%);
        width: 6rpx;
        height: 32rpx;
        background: $primary;
        border-radius: 3rpx;
      }
    }
  }

  &__list {
    flex: 1;
    height: 100%;
    padding: $spacing-sm $spacing-base;
    box-sizing: border-box;
  }

  &__list-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $spacing-sm 0;
  }

  &__sort {
    display: flex;
    gap: $spacing-base;
  }

  &__sort-item {
    font-size: $font-xs;
    color: $text-secondary;

    &--active {
      color: $primary;
      font-weight: 600;
    }
  }

  &__products {
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }
}
</style>
