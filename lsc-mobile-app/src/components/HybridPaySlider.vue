<template>
  <view class="hybrid-pay">
    <!-- 标题 -->
    <view class="hybrid-pay__title">
      <text class="fw-bold">混合支付</text>
      <text class="fs-sm text-secondary">1 LSC = ¥1.00</text>
    </view>

    <!-- 余额提示 -->
    <view class="hybrid-pay__balance">
      <text class="fs-sm text-secondary">可用 LSC: {{ available }}</text>
      <text v-if="available <= 0" class="fs-sm text-danger">（余额不足）</text>
    </view>

    <!-- 滑块 -->
    <view class="hybrid-pay__slider-wrap">
      <slider
        class="hybrid-pay__slider"
        :min="0"
        :max="result.maxLsc"
        :step="1"
        :value="result.lscAmount"
        activeColor="#6C5CE7"
        backgroundColor="#EDEDF7"
        block-color="#6C5CE7"
        :block-size="24"
        :disabled="result.maxLsc <= 0"
        @change="onSlide"
        @changing="onSlide"
      />
      <view class="hybrid-pay__slider-scale">
        <text>0</text>
        <text>最多 {{ result.maxLsc }} LSC</text>
      </view>
    </view>

    <!-- 数值输入 -->
    <view class="hybrid-pay__input-row">
      <text class="hybrid-pay__input-label">使用 LSC</text>
      <view class="hybrid-pay__input-box">
        <input
          class="hybrid-pay__input"
          type="number"
          :value="String(result.lscAmount)"
          placeholder="0"
          @input="onInput"
        />
        <text class="hybrid-pay__input-suffix">LSC</text>
      </view>
      <view class="hybrid-pay__quick">
        <text class="hybrid-pay__quick-btn" @click="setMax">最大</text>
        <text class="hybrid-pay__quick-btn" @click="setHalf">一半</text>
        <text class="hybrid-pay__quick-btn" @click="setZero">清零</text>
      </view>
    </view>

    <!-- 金额明细 -->
    <view class="hybrid-pay__detail">
      <view class="hybrid-pay__detail-row">
        <text class="text-secondary fs-sm">订单总额</text>
        <text class="fw-bold">¥{{ format2(result.totalAmount) }}</text>
      </view>
      <view class="hybrid-pay__detail-row">
        <text class="text-lsc fs-sm">LSC 抵扣</text>
        <text class="text-lsc fw-bold">-{{ result.lscAmount }} LSC</text>
      </view>
      <view class="hybrid-pay__detail-row hybrid-pay__detail-row--highlight">
        <text class="fs-sm">人民币补足</text>
        <text class="price-rmb fs-md fw-bold">¥{{ format2(result.rmbAmount) }}</text>
      </view>
    </view>

    <!-- 快捷提示 -->
    <view v-if="result.rmbAmount <= 0 && result.lscAmount > 0" class="hybrid-pay__tip">
      ✓ LSC 已全额抵扣，无需人民币支付
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { calcHybridPay, round2 } from '@/utils/pay'

const props = withDefaults(
  defineProps<{
    /** 订单总金额（元） */
    totalAmount: number
    /** 可用 LSC 余额 */
    available: number
    /** 双向绑定的 LSC 使用量 */
    modelValue?: number
  }>(),
  {
    modelValue: 0,
  },
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: number): void
  (e: 'change', result: ReturnType<typeof calcHybridPay>): void
}>()

const useLsc = ref<number>(props.modelValue || 0)

const result = computed(() =>
  calcHybridPay(props.totalAmount, useLsc.value, props.available),
)

// 外部 modelValue 变化时同步
watch(
  () => props.modelValue,
  (v) => {
    if (v !== useLsc.value) useLsc.value = v
  },
)

// totalAmount / available 变化时校验上限
watch(
  () => [props.totalAmount, props.available],
  () => {
    const r = calcHybridPay(props.totalAmount, useLsc.value, props.available)
    if (r.lscAmount !== useLsc.value) {
      useLsc.value = r.lscAmount
      emit('update:modelValue', r.lscAmount)
    }
  },
)

function sync() {
  const r = result.value
  useLsc.value = r.lscAmount
  emit('update:modelValue', r.lscAmount)
  emit('change', r)
}

function onSlide(e: any) {
  useLsc.value = Math.floor(Number(e.detail.value) || 0)
  sync()
}

function onInput(e: any) {
  useLsc.value = Math.floor(Number(e.detail.value) || 0)
  sync()
}

function setMax() {
  useLsc.value = result.value.maxLsc
  sync()
}

function setHalf() {
  useLsc.value = Math.floor(result.value.maxLsc / 2)
  sync()
}

function setZero() {
  useLsc.value = 0
  sync()
}

function format2(n: number): string {
  return round2(n).toFixed(2)
}
</script>

<style lang="scss" scoped>
.hybrid-pay {
  background: #fff;
  border-radius: $radius-lg;
  padding: $spacing-base;

  &__title {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: $spacing-sm;
  }

  &__balance {
    margin-bottom: $spacing-sm;
  }

  &__slider-wrap {
    padding: $spacing-sm 0;
  }

  &__slider {
    margin: 0;
  }

  &__slider-scale {
    display: flex;
    justify-content: space-between;
    font-size: $font-xs;
    color: $text-placeholder;
    margin-top: $spacing-xs;
  }

  &__input-row {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    padding: $spacing-base 0;
    border-top: 1rpx solid $border-color-light;
    border-bottom: 1rpx solid $border-color-light;
  }

  &__input-label {
    font-size: $font-base;
    color: $text-regular;
    flex-shrink: 0;
  }

  &__input-box {
    flex: 1;
    display: flex;
    align-items: center;
    background: $lsc-color-bg;
    border-radius: $radius-base;
    padding: 0 $spacing-base;
    height: 64rpx;
  }

  &__input {
    flex: 1;
    font-size: $font-md;
    color: $lsc-color;
    font-weight: 600;
  }

  &__input-suffix {
    color: $lsc-color;
    font-size: $font-sm;
  }

  &__quick {
    display: flex;
    gap: $spacing-sm;
    flex-shrink: 0;
  }

  &__quick-btn {
    font-size: $font-xs;
    color: $lsc-color;
    background: $lsc-color-bg;
    padding: 6rpx 14rpx;
    border-radius: $radius-sm;
  }

  &__detail {
    padding-top: $spacing-base;
  }

  &__detail-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: $spacing-xs 0;

    &--highlight {
      margin-top: $spacing-xs;
      padding-top: $spacing-sm;
      border-top: 1rpx dashed $border-color;
    }
  }

  &__tip {
    margin-top: $spacing-sm;
    font-size: $font-sm;
    color: $success;
    background: rgba(7, 193, 96, 0.08);
    padding: $spacing-xs $spacing-sm;
    border-radius: $radius-sm;
    text-align: center;
  }
}
</style>
