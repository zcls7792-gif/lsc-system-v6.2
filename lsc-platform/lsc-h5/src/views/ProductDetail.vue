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
      <div class="sku-spec">{{ formatSpec(sku.specJson) }}</div>
      <div class="sku-unit">{{ sku.saleUnit }} · 箱规{{ sku.packQty }} · 起订{{ sku.bMinQty }}</div>
      <div v-if="selectedSku?.skuId === sku.skuId && price" class="sku-price">
        <span class="price-label">零售价</span>
        <span class="price-val">¥{{ formatCent(price.retailPriceCent) }}</span>
      </div>
    </div>
    <van-empty v-if="skus.length === 0" description="暂无 SKU" />

    <!-- 数量选择 -->
    <div v-if="selectedSku" class="qty-section">
      <span class="section-title">购买数量</span>
      <van-stepper v-model="qty" :min="selectedSku.bMinQty" :max="99" />
    </div>

    <!-- 报价预览 -->
    <div v-if="quote" class="quote-card">
      <div class="quote-title">报价确认</div>
      <div class="quote-row">
        <span>商品金额</span>
        <span>¥{{ formatCent(quote.goodsCent) }}</span>
      </div>
      <div class="quote-row" v-if="quote.couponCent > 0">
        <span>优惠券</span>
        <span>-¥{{ formatCent(quote.couponCent) }}</span>
      </div>
      <div class="quote-row" v-if="quote.lscUnit > 0">
        <span>LSC 抵扣</span>
        <span>-¥{{ formatCent(quote.lscUnit / 100) }}</span>
      </div>
      <div class="quote-row total">
        <span>应付金额</span>
        <span class="price-val">¥{{ formatCent(quote.rmbCent) }}</span>
      </div>
      <div class="quote-hint">报价有效期至 {{ formatTime(quote.expireAt) }}</div>
    </div>

    <div class="footer-bar">
      <van-button type="primary" block :loading="submitting" @click="buy">
        {{ quote ? '确认下单' : '立即购买' }}
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast } from 'vant'
import { getProduct, listSkus, getActivePrice, type Product, type Sku } from '@/api/product'
import { createQuote, createOrder, type QuoteResult } from '@/api/order'

const route = useRoute()
const router = useRouter()
const product = ref<Product | null>(null)
const skus = ref<Sku[]>([])
const selectedSku = ref<Sku | null>(null)
const price = ref<any>(null)
const qty = ref(1)
const quote = ref<QuoteResult | null>(null)
const submitting = ref(false)

onMounted(async () => {
  const id = Number(route.params.id)
  product.value = await getProduct(id)
  skus.value = await listSkus(id)
})

async function selectSku(sku: Sku) {
  selectedSku.value = sku
  qty.value = sku.bMinQty ?? sku.b_min_qty ?? 1
  price.value = await getActivePrice(sku.skuId)
  quote.value = null
}

async function buy() {
  if (!selectedSku.value) {
    showToast('请选择规格')
    return
  }
  submitting.value = true
  try {
    if (!quote.value) {
      // 第一步：创建报价
      quote.value = await createQuote('C', [
        { skuId: selectedSku.value.skuId, qty: qty.value }
      ])
      showToast({ message: '报价已生成，请确认', position: 'top' })
    } else {
      // 第二步：确认下单
      const orderNo = await createOrder(quote.value.quoteId)
      showSuccessToast('下单成功')
      // 跳转订单详情
      router.push('/orders')
    }
  } catch (e: any) {
    // 错误已由 request 拦截器 showToast
  } finally {
    submitting.value = false
  }
}

const statusText = computed(() => {
  const map: any = { DRAFT: '草稿', REVIEWING: '审核中', ON_SALE: '在售', OFF_SALE: '下架', SOLD_OUT: '售罄' }
  return map[product.value?.status || ''] || product.value?.status
})

function formatCent(c: number) {
  return (c / 100).toFixed(2)
}

function formatSpec(json: string) {
  try {
    const obj = JSON.parse(json)
    return Object.entries(obj).map(([k, v]) => `${k}:${v}`).join(' ')
  } catch {
    return json
  }
}

function formatTime(iso: string) {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getHours()}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`
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
.price-label { font-size: 12px; color: #969799; font-weight: normal; margin-right: 6px; }
.price-val { color: #ee0a24; }
.qty-section { display: flex; align-items: center; justify-content: space-between; margin-top: 20px; }
.qty-section .section-title { margin: 0; }
.quote-card {
  background: #fff8f8; border-radius: 10px; padding: 16px; margin-top: 20px;
  border: 1px solid #ffb4b4;
}
.quote-title { font-size: 15px; font-weight: bold; margin-bottom: 12px; }
.quote-row { display: flex; justify-content: space-between; font-size: 14px; margin-bottom: 8px; color: #646566; }
.quote-row.total { font-size: 16px; font-weight: bold; color: #323233; margin-top: 8px; padding-top: 8px; border-top: 1px solid #eee; }
.quote-hint { font-size: 11px; color: #969799; margin-top: 8px; }
.footer-bar {
  position: fixed; bottom: 0; left: 0; right: 0;
  background: #fff; padding: 12px 16px;
  box-shadow: 0 -1px 6px rgba(0,0,0,0.06);
}
</style>
