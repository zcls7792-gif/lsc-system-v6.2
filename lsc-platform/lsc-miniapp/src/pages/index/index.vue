<template>
  <view class="page">
    <!-- 顶部横幅 -->
    <view class="banner">
      <view class="banner-title">链盛通 LSC</view>
      <view class="banner-sub">消费回馈 · 权益共享</view>
    </view>

    <!-- 快捷入口 -->
    <view class="quick-entries">
      <view class="entry" @click="go('/pages/account/index')">
        <view class="entry-icon">💰</view>
        <view class="entry-text">权益中心</view>
      </view>
      <view class="entry" @click="go('/pages/order/list')">
        <view class="entry-icon">📦</view>
        <view class="entry-text">我的订单</view>
      </view>
      <view class="entry" @click="go('/pages/product/list')">
        <view class="entry-icon">🛒</view>
        <view class="entry-text">商品列表</view>
      </view>
      <view class="entry" @click="go('/pages/mine/index')">
        <view class="entry-icon">👤</view>
        <view class="entry-text">个人中心</view>
      </view>
    </view>

    <!-- 推荐商品 -->
    <view class="section">
      <view class="section-title">热门推荐</view>
      <view class="product-list">
        <view v-for="item in products" :key="item.productId" class="product-card" @click="goDetail(item.productId)">
          <view class="product-name">{{ item.name }}</view>
          <view class="product-desc">{{ item.descriptionRef || '优质好物' }}</view>
          <view class="product-status">
            <text class="tag-on">在售</text>
          </view>
        </view>
        <view v-if="products.length === 0" class="empty">暂无推荐商品</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onPullDownRefresh } from '@dcloudio/uni-app'
import { listProducts, type Product } from '@/api/product'

const products = ref<Product[]>([])

onMounted(() => loadProducts())

onPullDownRefresh(() => {
  loadProducts().finally(() => uni.stopPullDownRefresh())
})

async function loadProducts() {
  try {
    products.value = await listProducts('ON_SALE', 10)
  } catch {}
}

function go(url: string) {
  uni.switchTab({ url })
}

function goDetail(id: number) {
  uni.navigateTo({ url: `/pages/product/detail?id=${id}` })
}
</script>

<style scoped>
.page { padding: 0 0 40rpx; }
.banner {
  background: linear-gradient(135deg, #1f6feb, #388bfd);
  padding: 60rpx 40rpx;
  color: #fff;
}
.banner-title { font-size: 44rpx; font-weight: bold; }
.banner-sub { font-size: 26rpx; opacity: 0.85; margin-top: 8rpx; }

.quick-entries {
  display: flex;
  justify-content: space-around;
  background: #fff;
  margin: -30rpx 24rpx 0;
  border-radius: 16rpx;
  padding: 30rpx 0;
  box-shadow: 0 4rpx 16rpx rgba(0,0,0,0.06);
}
.entry { text-align: center; flex: 1; }
.entry-icon { font-size: 48rpx; }
.entry-text { font-size: 24rpx; color: #606266; margin-top: 8rpx; }

.section { padding: 30rpx 24rpx; }
.section-title { font-size: 32rpx; font-weight: bold; margin-bottom: 20rpx; }
.product-list { display: flex; flex-direction: column; gap: 20rpx; }
.product-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  box-shadow: 0 2rpx 8rpx rgba(0,0,0,0.04);
}
.product-name { font-size: 30rpx; font-weight: bold; }
.product-desc { font-size: 24rpx; color: #909399; margin-top: 8rpx; }
.product-status { margin-top: 12rpx; }
.tag-on { background: #e1f3d8; color: #67c23a; padding: 4rpx 16rpx; border-radius: 8rpx; font-size: 22rpx; }
.empty { text-align: center; color: #c0c4cc; padding: 40rpx 0; }
</style>
