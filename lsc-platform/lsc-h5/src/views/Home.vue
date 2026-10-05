<template>
  <div class="home">
    <div class="banner">
      <div class="banner-title">链盛通 LSC</div>
      <div class="banner-sub">消费回馈 · 权益共享</div>
    </div>

    <van-grid :column-num="4" class="entries">
      <van-grid-item icon="balance-o" text="权益中心" to="/account" />
      <van-grid-item icon="orders-o" text="我的订单" to="/orders" />
      <van-grid-item icon="shop-o" text="商品列表" to="/products" />
      <van-grid-item icon="user-o" text="个人中心" to="/mine" />
    </van-grid>

    <div class="section-title">热门推荐</div>
    <van-list v-model:loading="loading" :finished="finished" finished-text="没有更多了" @load="onLoad">
      <div v-for="item in products" :key="item.productId" class="product-card" @click="goDetail(item.productId)">
        <div class="product-name">{{ item.name }}</div>
        <div class="product-desc">{{ item.descriptionRef || '优质好物' }}</div>
        <van-tag type="success" plain>在售</van-tag>
      </div>
    </van-list>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { listProducts, type Product } from '@/api/product'

const products = ref<Product[]>([])
const loading = ref(false)
const finished = ref(false)
const router = useRouter()

async function onLoad() {
  try {
    const list = await listProducts('ON_SALE', 20)
    products.value.push(...list)
  } finally {
    loading.value = false
    finished.value = true
  }
}

function goDetail(id: number) {
  router.push(`/product/${id}`)
}
</script>

<style scoped>
.home { padding-bottom: 50px; }
.banner {
  background: linear-gradient(135deg, #1989fa, #4fc08d);
  padding: 40px 20px;
  color: #fff;
}
.banner-title { font-size: 24px; font-weight: bold; }
.banner-sub { font-size: 13px; opacity: 0.85; margin-top: 4px; }
.entries { background: #fff; margin: -20px 12px 0; border-radius: 12px; box-shadow: 0 2px 12px rgba(0,0,0,0.06); }
.section-title { font-size: 16px; font-weight: bold; padding: 20px 16px 12px; }
.product-card {
  background: #fff; margin: 0 12px 12px; padding: 16px;
  border-radius: 10px;
}
.product-name { font-size: 15px; font-weight: bold; }
.product-desc { font-size: 12px; color: #969799; margin: 6px 0 10px; }
</style>
