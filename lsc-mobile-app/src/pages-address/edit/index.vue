<template>
  <view class="addr-edit">
    <view class="addr-edit__form card">
      <view class="addr-edit__row">
        <text class="addr-edit__label">收货人</text>
        <input class="addr-edit__input" v-model="form.name" placeholder="请输入收货人姓名" />
      </view>
      <view class="addr-edit__row">
        <text class="addr-edit__label">手机号</text>
        <input class="addr-edit__input" v-model="form.phone" type="number" maxlength="11" placeholder="请输入手机号" />
      </view>
      <view class="addr-edit__row" @click="chooseRegion">
        <text class="addr-edit__label">所在地区</text>
        <view class="addr-edit__region">
          <text :class="{ 'addr-edit__region--placeholder': !regionText }">{{ regionText || '请选择省/市/区' }}</text>
          <text class="addr-edit__arrow">›</text>
        </view>
      </view>
      <view class="addr-edit__row addr-edit__row--detail">
        <text class="addr-edit__label">详细地址</text>
        <textarea
          class="addr-edit__textarea"
          v-model="form.detail"
          placeholder="街道、楼牌号等"
          maxlength="100"
        />
      </view>
    </view>

    <!-- 地图选点 -->
    <view class="addr-edit__map card" @click="onChooseLocation">
      <text class="addr-edit__map-icon">🗺️</text>
      <view class="addr-edit__map-info">
        <text class="fw-bold">地图定位</text>
        <text class="fs-sm text-secondary text-ellipsis">{{ form.latitude ? `已定位：${locationName || '已选择位置'}` : '点击在地图上选择准确位置' }}</text>
      </view>
      <text class="addr-edit__arrow">›</text>
    </view>

    <view class="addr-edit__default card">
      <text>设为默认地址</text>
      <switch :checked="form.isDefault" color="#FF6B00" @change="onDefaultChange" />
    </view>

    <view style="height: 140rpx"></view>

    <view class="addr-edit__footer footer-bar">
      <button class="addr-edit__btn btn-primary" :loading="saving" @click="onSave">保存</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { getAddressDetail, saveAddress, type Address } from '@/api/map'
import { chooseLocation } from '@/api/map'

const saving = ref(false)
const editId = ref<number | null>(null)
const locationName = ref('')

const form = reactive<Address>({
  id: 0,
  name: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  isDefault: false,
  latitude: undefined,
  longitude: undefined,
})

const regionText = computed(() => {
  if (form.province || form.city || form.district) {
    return `${form.province} ${form.city} ${form.district}`
  }
  return ''
})

async function loadDetail(id: string) {
  try {
    const a = await getAddressDetail(id)
    Object.assign(form, a)
    editId.value = a.id
  } catch (e) {
    // ignore
  }
}

function chooseRegion() {
  // #ifdef MP-WEIXIN || APP-PLUS
  uni.showActionSheet({
    itemList: ['使用省市区选择器'],
    success: () => {
      // 简化：使用 picker mode region（小程序支持）
      // @ts-ignore
      uniPickerRegion()
    },
  })
  // #endif
  // #ifndef MP-WEIXIN || APP-PLUS
  uniPickerRegion()
  // #endif
}

function uniPickerRegion() {
  // 仅微信小程序支持 region picker，其他平台用文字输入兜底
  // #ifdef MP-WEIXIN
  // @ts-ignore - uni region picker
  uni.showModal({
    title: '所在地区',
    editable: true,
    placeholderText: '如：广东省 深圳市 南山区',
    content: regionText.value,
    success: (res) => {
      if (res.confirm && res.content) {
        const parts = res.content.split(/\s+/)
        form.province = parts[0] || ''
        form.city = parts[1] || ''
        form.district = parts[2] || ''
      }
    },
  })
  // #endif
  // #ifndef MP-WEIXIN
  uni.showModal({
    title: '所在地区',
    editable: true,
    placeholderText: '如：广东省 深圳市 南山区',
    content: regionText.value,
    success: (res) => {
      if (res.confirm && res.content) {
        const parts = res.content.split(/\s+/)
        form.province = parts[0] || ''
        form.city = parts[1] || ''
        form.district = parts[2] || ''
      }
    },
  })
  // #endif
}

function onDefaultChange(e: any) {
  form.isDefault = e.detail.value
}

async function onChooseLocation() {
  try {
    const loc = await chooseLocation()
    locationName.value = loc.name
    form.latitude = loc.latitude
    form.longitude = loc.longitude
    if (loc.address && !form.detail) {
      form.detail = loc.address
    }
  } catch (e) {
    // 用户取消
  }
}

async function onSave() {
  if (!form.name) {
    uni.showToast({ title: '请输入收货人', icon: 'none' })
    return
  }
  if (!/^1\d{10}$/.test(form.phone)) {
    uni.showToast({ title: '请输入正确手机号', icon: 'none' })
    return
  }
  if (!form.province || !form.detail) {
    uni.showToast({ title: '请完善地区与详细地址', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await saveAddress(form)
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  } catch (e) {
    // ignore
  } finally {
    saving.value = false
  }
}

onLoad((options) => {
  if (options?.id) loadDetail(options.id)
})
</script>

<style lang="scss" scoped>
.addr-edit {
  padding: $spacing-base;
  min-height: 100vh;

  &__form {
    margin-bottom: $spacing-base;
  }

  &__row {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    padding: $spacing-base 0;
    border-bottom: 1rpx solid $border-color-light;

    &--detail {
      align-items: flex-start;
      flex-direction: column;
      gap: $spacing-sm;
    }

    &:last-child {
      border-bottom: none;
    }
  }

  &__label {
    width: 160rpx;
    font-size: $font-base;
    color: $text-regular;
    flex-shrink: 0;
  }

  &__input {
    flex: 1;
    font-size: $font-base;
  }

  &__region {
    flex: 1;
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: $font-base;

    &--placeholder {
      color: $text-placeholder;
    }
  }

  &__arrow {
    color: $text-placeholder;
    font-size: $font-md;
  }

  &__textarea {
    width: 100%;
    height: 120rpx;
    background: $bg-gray;
    border-radius: $radius-base;
    padding: $spacing-sm;
    font-size: $font-base;
    box-sizing: border-box;
  }

  &__map {
    display: flex;
    align-items: center;
    gap: $spacing-base;
    margin-bottom: $spacing-base;
  }

  &__map-icon {
    font-size: 44rpx;
  }

  &__map-info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 4rpx;
    min-width: 0;
  }

  &__default {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: $spacing-base;
  }

  &__btn {
    width: 100%;
  }
}
</style>
