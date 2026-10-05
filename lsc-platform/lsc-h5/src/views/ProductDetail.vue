<template>
  <div class="page" v-if="product">
    <div class="header">
      <div class="name">{{ product.name }}</div>
      <van-tag :type="product.status === 'ON_SALE' ? 'success' : 'default'">
        {{ statusText }}
      </van-tag>
    </div>
    <div class="desc">{{ product.descriptionRef || '暂无描述' }}</div>

    <div class="section-title">SKU 规格</div>
    <div v-for="sku in skus" :key="sku.skuId"
         :class="['sku-card', selectedSku?.skuId === sku.skuId && 'active']"
         @click="selectSku(sku)">
      <div class="sku-code">{{ sku.skuCode }}</div>
      <div class="sku-spec">{{ sku.specJson }}</div>
      <div class="sku-unit">{{ sku.saleUnit }} · 箱规{{ sku.packQty }}</div>
      <div v-if="price" class="sku-price">¥{{ formatCent(price.retailPriceCent) }}</div>
    </div>
    <van-empty v-if="skus.length === 0" description="暂无 SKU" />

    <div class="footer-bar">
      <van-button type="primary" block @click="buy">立即购买</van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { showToast } from 'vant'
import { getProduct, listSkus, getActivePrice, type Product, type Sku } from '@/api/product'

const route = useRoute()
const product = ref<Product | null>(null)
const skus = ref<Sku[]>([])
const selectedSku = ref<Sku | null>(null)
const price = ref<any>(null)

onMounted(async () => {
  const id = Number(route.params.id)
  product.value = await getProduct(id)
  skus.value = await listSkus(id)
})

async function selectSku(sku: Sku) {
  selectedSku.value = sku
  price.value = await getActivePrice(sku.skuId)
}

function buy() {
  if (!selectedSku.value) {
    showToast('请选择规格')
    return
  }
  showToast('下单功能开发中')
}

const statusText = computed(() => {
  const map: any = { DRAFT: '草稿', REVIEWING: '审核中', ON_SALE: '在售', OFF_SALE: '下架', SOLD_OUT: '售罄' }
  return map[product.value?.status || ''] || product.value?.status
})

function formatCent(c: number) {
  return (c / 100).toFixed(2)
}
</script>

<style scoped>
.page { padding: 16px; padding-bottom: 70px; }
.header { display: flex; justify-content: space-between; align-items: center; }
.name { font-size: 20px; font-weight: bold; }
.desc { font-size: 13px; color: #646566; margin-top: 12px; line-height: 1.6; }
.section-title { font-size: 16px; font-weight: bold; margin: 24px 0 12px; }
.sku-card {
  background: #fff; border-radius: 10px; padding: 16px;
  margin-bottom: 12px; border: 2px solid transparent;
}
.sku-card.active { border-color: #1989fa; }
.sku-code { font-weight: bold; font-size: 14px; }
.sku-spec { font-size: 12px; color: #646566; margin-top: 4px; }
.sku-unit { font-size: 11px; color: #969799; margin-top: 4px; }
.sku-price { font-size: 18px; color: #ee0a24; font-weight: bold; margin-top: 8px; }
.footer-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: #fff; padding: 12px 16px;
  box-shadow: 0 -1px 6px rgba(0,0,0,0.06);
}
</style>
