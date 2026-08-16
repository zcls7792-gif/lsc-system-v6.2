<script setup lang="ts">
// 商品类目 — 类目选择 / 浏览
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { EditPen } from '@element-plus/icons-vue'
import { getCategories } from '@/api/product'
import type { ProductCategory } from '@/api/types'

const router = useRouter()
const categories = ref<ProductCategory[]>([])
const loading = ref(false)
const selectedId = ref<number>(0)

const tree = computed(() => buildTree(categories.value))

function buildTree(list: ProductCategory[]): any[] {
  const map: Record<number, any> = {}
  const roots: any[] = []
  list.forEach((c) => (map[c.id] = { ...c, children: [] }))
  list.forEach((c) => {
    if (c.parentId && map[c.parentId]) map[c.parentId].children.push(map[c.id])
    else roots.push(map[c.id])
  })
  const sortRec = (nodes: any[]) => {
    nodes.sort((a, b) => a.sortOrder - b.sortOrder)
    nodes.forEach((n) => sortRec(n.children))
  }
  sortRec(roots)
  return roots
}

async function load() {
  loading.value = true
  try {
    categories.value = await getCategories()
  } finally {
    loading.value = false
  }
}

function onPick(id: number) {
  selectedId.value = id
}

function goPublish() {
  if (!selectedId.value) return
  router.push({ path: '/product/publish', query: { categoryId: String(selectedId.value) } })
}

onMounted(load)
</script>

<template>
  <div class="lsc-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">商品类目</h1>
        <p class="lsc-page-subtitle">选择合适类目发布商品，提升搜索与审核效率</p>
      </div>
      <el-button type="primary" :icon="EditPen" :disabled="!selectedId" @click="goPublish">
        去发布商品
      </el-button>
    </div>

    <div class="lsc-card" v-loading="loading">
      <div class="lsc-card__pad">
        <div class="cat-grid">
          <div
            v-for="root in tree"
            :key="root.id"
            class="cat-col"
          >
            <div class="cat-col__head">
              <span class="cat-col__name">{{ root.name }}</span>
              <span class="cat-col__count">{{ root.children?.length || 0 }}</span>
            </div>
            <div class="cat-col__items">
              <div
                v-for="child in root.children || []"
                :key="child.id"
                class="cat-item"
                :class="{ 'is-active': selectedId === child.id }"
                @click="onPick(child.id)"
              >
                <span>{{ child.name }}</span>
                <el-icon v-if="selectedId === child.id"><EditPen /></el-icon>
              </div>
              <div v-if="!root.children || root.children.length === 0" class="cat-empty">暂无子类目</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.cat-col {
  border: 1px solid var(--lsc-border-soft);
  border-radius: var(--lsc-radius);
  overflow: hidden;
  background: var(--lsc-surface-2);
}

.cat-col__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 14px;
  background: linear-gradient(135deg, var(--lsc-primary-50), #fff);
  border-bottom: 1px solid var(--lsc-border-soft);
}

.cat-col__name {
  font-weight: 600;
  font-family: var(--lsc-font-display);
  color: var(--lsc-primary-800);
}

.cat-col__count {
  font-size: 11px;
  padding: 1px 8px;
  background: var(--lsc-primary-100);
  color: var(--lsc-primary-800);
  border-radius: 999px;
}

.cat-col__items {
  padding: 6px;
}

.cat-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--lsc-text-regular);
  cursor: pointer;
  transition: all 0.16s ease;
}

.cat-item:hover {
  background: var(--lsc-bg-soft);
}

.cat-item.is-active {
  background: var(--lsc-primary-600);
  color: #fff;
  font-weight: 600;
}

.cat-empty {
  padding: 16px;
  text-align: center;
  font-size: 12px;
  color: var(--lsc-text-placeholder);
}
</style>
