import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Product } from '@/api/product'

export interface CartItem {
  product: Product
  quantity: number
  spec?: string
  /** 是否选中 */
  selected: boolean
}

export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])

  const totalCount = computed(() =>
    items.value.reduce((sum, it) => sum + it.quantity, 0),
  )

  const selectedItems = computed(() => items.value.filter((it) => it.selected))

  const selectedCount = computed(() =>
    selectedItems.value.reduce((sum, it) => sum + it.quantity, 0),
  )

  /** 选中商品人民币总额 */
  const selectedTotalPrice = computed(() =>
    round2(selectedItems.value.reduce((sum, it) => sum + it.product.price * it.quantity, 0)),
  )

  /** 选中商品 LSC 总额（1:1） */
  const selectedTotalLsc = computed(() =>
    Math.floor(selectedItems.value.reduce((sum, it) => sum + it.product.lscPrice * it.quantity, 0)),
  )

  const isAllSelected = computed(
    () => items.value.length > 0 && items.value.every((it) => it.selected),
  )

  function round2(n: number) {
    return Math.round((n + Number.EPSILON) * 100) / 100
  }

  function add(product: Product, quantity = 1, spec?: string) {
    const exist = items.value.find(
      (it) => it.product.id === product.id && it.spec === spec,
    )
    if (exist) {
      exist.quantity += quantity
    } else {
      items.value.push({ product, quantity, spec, selected: true })
    }
    persist()
    uni.showToast({ title: '已加入购物车', icon: 'success' })
  }

  function updateQuantity(productId: number, quantity: number, spec?: string) {
    const it = items.value.find((i) => i.product.id === productId && i.spec === spec)
    if (!it) return
    it.quantity = Math.max(1, quantity)
    persist()
  }

  function remove(productId: number, spec?: string) {
    items.value = items.value.filter(
      (it) => !(it.product.id === productId && it.spec === spec),
    )
    persist()
  }

  function toggleSelect(productId: number, spec?: string) {
    const it = items.value.find((i) => i.product.id === productId && i.spec === spec)
    if (it) {
      it.selected = !it.selected
      persist()
    }
  }

  function toggleSelectAll(selected: boolean) {
    items.value.forEach((it) => (it.selected = selected))
    persist()
  }

  function clearSelected() {
    items.value = items.value.filter((it) => !it.selected)
    persist()
  }

  function clear() {
    items.value = []
    persist()
  }

  function persist() {
    uni.setStorageSync('LSC_CART', JSON.stringify(items.value))
  }

  function restore() {
    const cached = uni.getStorageSync('LSC_CART')
    if (cached) {
      try {
        items.value = JSON.parse(cached)
      } catch (e) {
        items.value = []
      }
    }
  }

  return {
    items,
    totalCount,
    selectedItems,
    selectedCount,
    selectedTotalPrice,
    selectedTotalLsc,
    isAllSelected,
    add,
    updateQuantity,
    remove,
    toggleSelect,
    toggleSelectAll,
    clearSelected,
    clear,
    restore,
  }
})
