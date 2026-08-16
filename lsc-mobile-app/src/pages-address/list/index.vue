<template>
  <view class="addr-list">
    <scroll-view
      scroll-y
      class="addr-list__scroll"
      :refresher-enabled="true"
      :refresher-triggered="refreshing"
      @refresherrefresh="onRefresh"
    >
      <view class="addr-list__content">
        <view v-for="addr in list" :key="addr.id" class="addr-list__item card" @click="onSelect(addr)">
          <view class="addr-list__info">
            <view class="addr-list__user">
              <text class="fw-bold">{{ addr.name }}</text>
              <text class="fs-sm text-secondary">{{ addr.phone }}</text>
              <text v-if="addr.isDefault" class="tag tag-primary">默认</text>
            </view>
            <text class="fs-sm text-ellipsis-2">{{ fullAddress(addr) }}</text>
          </view>
          <view class="addr-list__ops">
            <text class="addr-list__op" @click.stop="onEdit(addr)">编辑</text>
            <text class="addr-list__op addr-list__op--del" @click.stop="onDel(addr)">删除</text>
          </view>
        </view>

        <EmptyState v-if="!loading && !list.length" text="还没有收货地址" icon-text="📍" action-text="新增地址" @action="onAdd" />
      </view>
      <view style="height: 160rpx"></view>
    </scroll-view>

    <view class="addr-list__footer footer-bar">
      <button class="addr-list__btn btn-primary" @click="onAdd">+ 新增收货地址</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { getAddressList, deleteAddress, type Address } from '@/api/map'
import EmptyState from '@/components/EmptyState.vue'

const list = ref<Address[]>([])
const loading = ref(false)
const refreshing = ref(false)
const fromOrder = ref(false)

function fullAddress(a: Address): string {
  return `${a.province}${a.city}${a.district}${a.detail}`
}

async function load() {
  loading.value = true
  try {
    list.value = await getAddressList()
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

function onAdd() {
  uni.navigateTo({ url: '/src/pages-address/edit/index' })
}

function onEdit(addr: Address) {
  uni.navigateTo({ url: `/src/pages-address/edit/index?id=${addr.id}` })
}

function onDel(addr: Address) {
  uni.showModal({
    title: '提示',
    content: '确认删除该地址？',
    success: async (res) => {
      if (res.confirm) {
        await deleteAddress(addr.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        load()
      }
    },
  })
}

function onSelect(addr: Address) {
  if (fromOrder.value) {
    uni.$emit('address:selected', addr)
    uni.navigateBack()
  } else {
    onEdit(addr)
  }
}

async function onRefresh() {
  refreshing.value = true
  await load()
}

onLoad((options) => {
  if (options?.from === 'order') fromOrder.value = true
})

onShow(load)
</script>

<style lang="scss" scoped>
.addr-list {
  display: flex;
  flex-direction: column;
  height: 100vh;

  &__scroll {
    flex: 1;
    height: 0;
  }

  &__content {
    padding: $spacing-base;
    display: flex;
    flex-direction: column;
    gap: $spacing-base;
  }

  &__item {
    display: flex;
    justify-content: space-between;
    gap: $spacing-base;
  }

  &__info {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: $spacing-xs;
    min-width: 0;
  }

  &__user {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  &__ops {
    display: flex;
    flex-direction: column;
    gap: $spacing-sm;
    justify-content: center;
  }

  &__op {
    font-size: $font-sm;
    color: $primary;
    padding: $spacing-xs $spacing-sm;

    &--del {
      color: $danger;
    }
  }

  &__btn {
    width: 100%;
  }
}
</style>
