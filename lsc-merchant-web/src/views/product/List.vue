<script setup lang="ts">
// 商品列表 — 表格 + 上下架操作 + AI审核状态标签
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, Refresh, Edit, Delete, Top, Bottom } from '@element-plus/icons-vue'
import { getProducts, shelfOn, shelfOff, deleteProduct, type ProductListQuery } from '@/api/product'
import type { Product } from '@/api/types'
import type { PageResult } from '@/utils/request'
import { PRODUCT_STATUS_MAP, AI_REVIEW_MAP, VIDEO_STATUS_MAP, parseJsonArray } from '@/utils/maps'
import dayjs from 'dayjs'

const router = useRouter()
const loading = ref(false)
const list = ref<Product[]>([])

const query = reactive<ProductListQuery>({
  page: 1,
  size: 10,
  keyword: '',
  status: undefined,
  categoryId: undefined
})

const total = ref(0)

async function load() {
  loading.value = true
  try {
    const res: PageResult<Product> = await getProducts({
      page: query.page,
      size: query.size,
      keyword: query.keyword,
      status: query.status,
      categoryId: query.categoryId
    })
    list.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function onReset() {
  query.keyword = ''
  query.status = undefined
  query.categoryId = undefined
  query.page = 1
  load()
}

function onShelf(p: Product) {
  ElMessageBox.confirm(`确认上架商品「${p.productName}」？`, '上架确认', {
    type: 'warning'
  })
    .then(async () => {
      await shelfOn(p.id)
      ElMessage.success('已提交上架')
      load()
    })
    .catch(() => {})
}

function offShelf(p: Product) {
  ElMessageBox.confirm(`确认下架商品「${p.productName}」？下架后消费者不可见`, '下架确认', {
    type: 'warning'
  })
    .then(async () => {
      await shelfOff(p.id)
      ElMessage.success('已下架')
      load()
    })
    .catch(() => {})
}

function onDelete(p: Product) {
  ElMessageBox.confirm(`确认删除商品「${p.productName}」？该操作不可恢复`, '删除确认', {
    type: 'error',
    confirmButtonText: '删除'
  })
    .then(async () => {
      await deleteProduct(p.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

function goPublish(id?: number) {
  if (id) router.push({ path: '/product/publish', query: { id: String(id) } })
  else router.push('/product/publish')
}

function fmtMoney(n: number) {
  return Number(n || 0).toFixed(2)
}

function fmtDate(d: string) {
  return dayjs(d).format('YYYY-MM-DD HH:mm')
}

onMounted(load)
</script>

<template>
  <div class="lsc-page" data-testid="merchant-product-list-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">商品列表</h1>
        <p class="lsc-page-subtitle">管理已发布商品 · 上下架 · 查看 AI 审核状态</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="goPublish()">发布商品</el-button>
    </div>

    <div class="lsc-card">
      <div class="lsc-card__pad filter">
        <el-input
          v-model="query.keyword"
          placeholder="商品名称 / 关键词"
          clearable
          :prefix-icon="Search"
          class="filter__input"
          @keyup.enter="onSearch"
        />
        <el-select v-model="query.status" placeholder="商品状态" clearable class="filter__select">
          <el-option label="已下架" :value="0" />
          <el-option label="上架中" :value="1" />
          <el-option label="审核中" :value="2" />
        </el-select>
        <div class="filter__btns">
          <el-button type="primary" :icon="Search" @click="onSearch">查询</el-button>
          <el-button :icon="Refresh" @click="onReset">重置</el-button>
        </div>
      </div>
    </div>

    <div class="lsc-card" style="margin-top: 16px">
      <el-table v-loading="loading" :data="list" row-key="id" stripe data-testid="merchant-product-list-table">
        <el-table-column label="商品" min-width="280">
          <template #default="{ row }">
            <div class="prod-cell">
              <el-image
                class="prod-cell__img"
                :src="parseJsonArray(row.productImages)[0]"
                fit="cover"
                :preview-src-list="parseJsonArray(row.productImages)"
                preview-teleported
                hide-on-click-modal
              >
                <template #error>
                  <div class="prod-cell__img-fallback">无图</div>
                </template>
              </el-image>
              <div class="prod-cell__info">
                <div class="prod-cell__name">{{ row.productName }}</div>
                <div class="prod-cell__id">ID: <span class="lsc-num">{{ row.id }}</span></div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="价格" width="130" align="right">
          <template #default="{ row }">
            <div class="price">¥<span class="lsc-num">{{ fmtMoney(row.price) }}</span></div>
            <div class="price-sub lsc-gold-text">{{ row.price }} LSC</div>
          </template>
        </el-table-column>

        <el-table-column label="库存 / 销量" width="120" align="center">
          <template #default="{ row }">
            <div class="lsc-num">库存 {{ row.stock }}</div>
            <div class="lsc-num muted">销量 {{ row.salesCount }}</div>
          </template>
        </el-table-column>

        <el-table-column label="AI审核" width="110">
          <template #default="{ row }">
            <el-tag :type="AI_REVIEW_MAP[row.aiReviewResult]?.type" size="small">
              {{ AI_REVIEW_MAP[row.aiReviewResult]?.label || '未审核' }}
            </el-tag>
            <div v-if="row.videoUrl" class="video-tag">
              视频:
              <el-tag :type="VIDEO_STATUS_MAP[row.videoStatus]?.type" size="small" effect="plain">
                {{ VIDEO_STATUS_MAP[row.videoStatus]?.label || '待审核' }}
              </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="PRODUCT_STATUS_MAP[row.status]?.type" effect="light">
              {{ PRODUCT_STATUS_MAP[row.status]?.label || '未知' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            <span class="lsc-num muted">{{ fmtDate(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Edit" @click="goPublish(row.id)">编辑</el-button>
            <el-button
              v-if="row.status !== 1"
              link
              type="success"
              :icon="Top"
              :disabled="row.status === 2"
              @click="onShelf(row as Product)"
            >上架</el-button>
            <el-button v-else link type="warning" :icon="Bottom" @click="offShelf(row as Product)">下架</el-button>
            <el-button link type="danger" :icon="Delete" @click="onDelete(row as Product)">删除</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无商品，去发布第一件吧" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="load"
          @size-change="load"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.filter__input {
  width: 280px;
}

.filter__select {
  width: 160px;
}

.filter__btns {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.prod-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.prod-cell__img {
  width: 52px;
  height: 52px;
  border-radius: 8px;
  flex-shrink: 0;
  border: 1px solid var(--lsc-border);
}

.prod-cell__img-fallback {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  font-size: 11px;
  color: var(--lsc-text-placeholder);
  background: var(--lsc-bg-soft);
}

.prod-cell__name {
  font-weight: 600;
  color: var(--lsc-text);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.prod-cell__id {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--lsc-text-placeholder);
}

.price {
  color: var(--lsc-text);
  font-weight: 700;
  font-size: 15px;
}

.price-sub {
  font-size: 11.5px;
  margin-top: 2px;
}

.muted {
  color: var(--lsc-text-secondary);
}

.video-tag {
  margin-top: 4px;
  font-size: 11px;
  color: var(--lsc-text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px 16px;
}
</style>
