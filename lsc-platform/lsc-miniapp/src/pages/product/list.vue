<template>
  <view class="page">
    <view class="search-bar">
      <input class="search-input" v-model="keyword" placeholder="搜索商品" confirm-type="search" @confirm="load" />
    </view>
    <view class="product-list">
      <view v-for="item in products" :key="item.productId" class="product-card" @click="goDetail(item.productId)">
        <view class="product-name">{{ item.name }}</view>
        <view class="product-desc">{{ item.descriptionRef || '—' }}</view>
      </view>
      <view v-if="!loading && products.length === 0" class="empty">暂无商品</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { listProducts, type Product } from '@/api/product'

const products = ref<Product[]>([])
const loading = ref(false)
const keyword = ref('')

onMounted(() => load())

async function load() {
  loading.value = true
  try {
    products.value = await listProducts('ON_SALE', 50)
  } finally {
    loading.value = false
  }
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}
</script>

<style scoped>
.page { padding: 20rpx 24rpx; }
.search-bar { margin-bottom: 20rpx; }
.search-input {
  background: #fff;
  border-radius: 32rpx;
  padding: 16rpx 30rpx;
  font-size: 28rpx;
}
.product-list { display: flex; flex-direction: column; gap: 20rpx; }
.product-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
}
.product-name { font-size: 30rpx; font-weight: bold; }
.product-desc { font-size: 24rpx; color: #909399; margin-top: 8rpx; }
.empty { text-align: center; color: #c0c4cc; padding: 80rpx 0; }
</style>
