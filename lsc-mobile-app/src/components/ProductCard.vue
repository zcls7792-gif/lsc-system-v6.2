<template>
  <view class="product-card" :class="{ 'product-card--horizontal': mode === 'horizontal' }" @click="onClick">
    <view class="product-card__image-wrap">
      <image
        class="product-card__image"
        :src="product.cover || placeholder"
        mode="aspectFill"
        lazy-load
      />
      <view v-if="product.originPrice && product.originPrice > product.price" class="product-card__discount">
        特惠
      </view>
    </view>

    <view class="product-card__body">
      <view class="product-card__name text-ellipsis-2">{{ product.name }}</view>

      <view v-if="product.specs && product.specs.length" class="product-card__spec text-ellipsis">
        {{ product.specs[0].values.join(' / ') }}
      </view>

      <view class="product-card__price-row">
        <text class="product-card__price-rmb">¥{{ formatPrice(product.price) }}</text>
        <text class="product-card__price-lsc">{{ Math.floor(product.lscPrice) }} LSC</text>
      </view>

      <view v-if="product.sales" class="product-card__sales">已售 {{ product.sales }}</view>

      <view v-if="showCart" class="product-card__cart" @click.stop="onAddCart">
        <text class="product-card__cart-icon">＋</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import type { Product } from '@/api/product'
import { useCartStore } from '@/stores/cart'

const props = withDefaults(
  defineProps<{
    product: Product
    /** card: 卡片(2列网格) / horizontal: 横向 */
    mode?: 'card' | 'horizontal'
    /** 是否显示加入购物车按钮 */
    showCart?: boolean
  }>(),
  {
    mode: 'card',
    showCart: false,
  },
)

const emit = defineEmits<{
  (e: 'click', product: Product): void
  (e: 'add-cart', product: Product): void
}>()

const placeholder = '/static/placeholder/product.png'
const cartStore = useCartStore()

function formatPrice(n: number): string {
  return (Number(n) || 0).toFixed(2)
}

function onClick() {
  emit('click', props.product)
  uni.navigateTo({ url: `/src/pages-product/detail/index?id=${props.product.id}` })
}

function onAddCart() {
  cartStore.add(props.product, 1)
  emit('add-cart', props.product)
}
</script>

<style lang="scss" scoped>
.product-card {
  background: #fff;
  border-radius: $radius-lg;
  overflow: hidden;
  box-shadow: $shadow-base;
  position: relative;

  &--horizontal {
    display: flex;
    .product-card__image-wrap {
      width: 200rpx;
      height: 200rpx;
      flex-shrink: 0;
    }
    .product-card__image {
      width: 200rpx;
      height: 200rpx;
    }
    .product-card__body {
      flex: 1;
      padding: $spacing-base;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }
  }

  &__image-wrap {
    position: relative;
    width: 100%;
    aspect-ratio: 1;
  }

  &__image {
    width: 100%;
    height: 100%;
    background: $bg-gray;
  }

  &__discount {
    position: absolute;
    top: $spacing-sm;
    left: $spacing-sm;
    background: linear-gradient(135deg, $primary, $primary-light);
    color: #fff;
    font-size: $font-xs;
    padding: 2rpx 12rpx;
    border-radius: $radius-sm;
  }

  &__body {
    padding: $spacing-sm $spacing-base $spacing-base;
    position: relative;
  }

  &__name {
    font-size: $font-base;
    color: $text-primary;
    line-height: 1.4;
    height: 78rpx;
  }

  &__spec {
    font-size: $font-sm;
    color: $text-secondary;
    margin-top: $spacing-xs;
  }

  &__price-row {
    display: flex;
    align-items: baseline;
    gap: $spacing-sm;
    margin-top: $spacing-sm;
    flex-wrap: wrap;
  }

  &__price-rmb {
    color: $primary;
    font-weight: 600;
    font-size: $font-md;
  }

  &__price-lsc {
    color: $lsc-color;
    font-size: $font-sm;
    background: $lsc-color-bg;
    padding: 2rpx 10rpx;
    border-radius: $radius-sm;
  }

  &__sales {
    font-size: $font-xs;
    color: $text-placeholder;
    margin-top: $spacing-xs;
  }

  &__cart {
    position: absolute;
    right: $spacing-base;
    bottom: $spacing-base;
    width: 48rpx;
    height: 48rpx;
    border-radius: 50%;
    background: linear-gradient(135deg, $primary, $primary-light);
    color: #fff;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 36rpx;
    line-height: 1;
  }
}
</style>
