<template>
  <view class="page" v-if="product">
    <view class="header">
      <view class="name">{{ product.name }}</view>
      <view class="status">
        <text :class="'tag-' + statusClass">{{ statusText }}</text>
      </view>
    </view>
    <view class="desc">{{ product.descriptionRef || '暂无描述' }}</view>

    <view class="section-title">SKU 规格</view>
    <view v-for="sku in skus" :key="sku.skuId" class="sku-card" @click="selectSku(sku)">
      <view class="sku-code">{{ sku.skuCode }}</view>
      <view class="sku-spec">{{ sku.specJson }}</view>
      <view class="sku-unit">{{ sku.saleUnit }} · 箱规{{ sku.packQty }}</view>
      <view v-if="price" class="sku-price">¥{{ formatCent(price.retailPriceCent) }}</view>
    </view>
    <view v-if="skus.length === 0" class="empty">暂无 SKU</view>

    <view class="footer-bar">
      <button class="btn-buy" @click="buy">立即购买</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getProduct, listSkus, getActivePrice, type Product, type Sku } from '@/api/product'

const product = ref<Product | null>(null)
const skus = ref<Sku[]>([])
const selectedSku = ref<Sku | null>(null)
const price = ref<any>(null)

onMounted(async () => {
  const pages = getCurrentPages()
  const q = (pages[pages.length - 1] as any).options || {}
  const id = Number(q.id)
  if (id) {
    product.value = await getProduct(id)
    skus.value = await listSkus(id)
  }
})

async function selectSku(sku: Sku) {
  selectedSku.value = sku
  price.value = await getActivePrice(sku.skuId)
}

function buy() {
  if (!selectedSku.value) {
    uni.showToast({ title: '请选择规格', icon: 'none' })
    return
  }
  uni.showToast({ title: '下单功能开发中', icon: 'none' })
}

const statusText = computed(() => {
  const map: any = { DRAFT: '草稿', REVIEWING: '审核中', ON_SALE: '在售', OFF_SALE: '下架', SOLD_OUT: '售罄' }
  return map[product.value?.status || ''] || product.value?.status
})
const statusClass = computed(() => product.value?.status === 'ON_SALE' ? 'on' : 'off')

function formatCent(c: number) {
  return (c / 100).toFixed(2)
}
</script>

<style scoped>
.page { padding: 24rpx; padding-bottom: 140rpx; }
.header { display: flex; justify-content: space-between; align-items: center; }
.name { font-size: 36rpx; font-weight: bold; }
.tag-on { background: #e1f3d8; color: #67c23a; padding: 6rpx 20rpx; border-radius: 8rpx; font-size: 24rpx; }
.tag-off { background: #f4f4f5; color: #909399; padding: 6rpx 20rpx; border-radius: 8rpx; font-size: 24rpx; }
.desc { font-size: 26rpx; color: #606266; margin-top: 16rpx; line-height: 1.6; }
.section-title { font-size: 30rpx; font-weight: bold; margin: 30rpx 0 16rpx; }
.sku-card {
  background: #fff;
  border-radius: 12rpx;
  padding: 24rpx;
  margin-bottom: 16rpx;
}
.sku-code { font-weight: bold; font-size: 28rpx; }
.sku-spec { font-size: 24rpx; color: #606266; margin-top: 6rpx; }
.sku-unit { font-size: 22rpx; color: #909399; margin-top: 6rpx; }
.sku-price { font-size: 32rpx; color: #f56c6c; font-weight: bold; margin-top: 8rpx; }
.empty { text-align: center; color: #c0c4cc; padding: 60rpx 0; }
.footer-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: #fff; padding: 20rpx 24rpx;
  box-shadow: 0 -2rpx 12rpx rgba(0,0,0,0.06);
}
.btn-buy {
  background: #1f6feb; color: #fff; border-radius: 40rpx;
  font-size: 30rpx; padding: 20rpx 0;
}
</style>
