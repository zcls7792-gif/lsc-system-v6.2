<template>
  <div class="page">
    <van-search v-model="keyword" placeholder="搜索商品" @search="load" shape="round" />
    <van-list v-model:loading="loading" :finished="finished" @load="onLoad">
      <div v-for="item in products" :key="item.productId" class="product-card" @click="goDetail(item.productId)">
        <div class="product-name">{{ item.name }}</div>
        <div class="product-desc">{{ item.descriptionRef || '—' }}</div>
      </div>
      <van-empty v-if="!loading && products.length === 0" description="暂无商品" />
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
const keyword = ref('')
const router = useRouter()

async function onLoad() {
  try {
    products.value = await listProducts('ON_SALE', 50)
  } finally {
    loading.value = false
    finished.value = true
  }
}

async function load() {
  loading.value = true
  finished.value = false
  await onLoad()
}

function goDetail(id: number) {
  router.push(`/product/${id}`)
}
</script>

<style scoped>
.page { padding-bottom: 50px; }
.product-card {
  background: #fff; margin: 12px; padding: 16px;
  border-radius: 10px;
}
.product-name { font-size: 15px; font-weight: bold; }
.product-desc { font-size: 12px; color: #969799; margin-top: 6px; }
</style>
