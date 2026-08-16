<script setup lang="ts">
// 线下地址管理 — 地址列表 + 添加/编辑 + 地图选点 + 当日修改次数提示
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Edit, Delete, Star, StarFilled } from '@element-plus/icons-vue'
import {
  getStoreAddresses,
  saveStoreAddress,
  deleteStoreAddress,
  setPrimaryAddress,
  getAddressUpdateState,
  type StoreAddressParams,
  type AddressUpdateState
} from '@/api/store'
import type { StoreAddress } from '@/api/types'
import MapPicker from '@/components/MapPicker.vue'

const loading = ref(false)
const submitting = ref(false)
const list = ref<StoreAddress[]>([])
const updateState = ref<AddressUpdateState | null>(null)
const dialogVisible = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<StoreAddressParams & { lng?: number; lat?: number; address?: string }>({
  id: 0,
  label: '',
  province: '',
  city: '',
  district: '',
  addressDetail: '',
  longitude: 0,
  latitude: 0,
  contactPhone: '',
  isPrimary: 0
})

const rules: FormRules = {
  label: [{ required: true, message: '请输入地址标签', trigger: 'blur' }],
  addressDetail: [{ required: true, message: '请输入详细地址', trigger: 'blur' }],
  longitude: [
    {
      validator: (_r, _v, cb) => (form.longitude ? cb() : cb(new Error('请在地图选点'))),
      trigger: 'change'
    }
  ]
}

async function load() {
  loading.value = true
  try {
    const [addrs, state] = await Promise.all([
      getStoreAddresses(),
      getAddressUpdateState().catch(() => null)
    ])
    list.value = addrs || []
    updateState.value = state
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: 0,
    label: '',
    province: '',
    city: '',
    district: '',
    addressDetail: '',
    longitude: 0,
    latitude: 0,
    contactPhone: '',
    isPrimary: list.value.length === 0 ? 1 : 0,
    lng: undefined,
    lat: undefined,
    address: ''
  })
  dialogVisible.value = true
}

function openEdit(row: StoreAddress) {
  Object.assign(form, {
    id: row.id,
    label: row.label || '',
    province: row.province,
    city: row.city,
    district: row.district,
    addressDetail: row.addressDetail,
    longitude: row.longitude,
    latitude: row.latitude,
    contactPhone: row.contactPhone || '',
    isPrimary: row.isPrimary,
    lng: row.longitude,
    lat: row.latitude,
    address: [row.province, row.city, row.district, row.addressDetail].join('')
  })
  dialogVisible.value = true
}

function onMapPick(v: { longitude: number; latitude: number; address?: string }) {
  form.longitude = v.longitude
  form.latitude = v.latitude
  if (v.address) {
    // 尝试解析省市区 (简化：直接填入详情)
    form.addressDetail = form.addressDetail || v.address
  }
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await saveStoreAddress(form)
      ElMessage.success(form.id ? '地址已更新' : '地址已添加')
      dialogVisible.value = false
      load()
    } finally {
      submitting.value = false
    }
  })
}

async function remove(row: StoreAddress) {
  ElMessageBox.confirm(`确认删除地址「${row.label || row.addressDetail}」？`, '删除确认', {
    type: 'warning'
  })
    .then(async () => {
      await deleteStoreAddress(row.id)
      ElMessage.success('已删除')
      load()
    })
    .catch(() => {})
}

async function setPrimary(row: StoreAddress) {
  await setPrimaryAddress(row.id)
  ElMessage.success('已设为主地址')
  load()
}

function fmt(n?: number) {
  return n ? n.toFixed(6) : '—'
}

onMounted(load)
</script>

<template>
  <div class="lsc-page" v-loading="loading">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">线下地址</h1>
        <p class="lsc-page-subtitle">管理门店线下地址 · 支持地图选点</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">添加地址</el-button>
    </div>

    <!-- 当日修改次数提示 -->
    <el-alert
      v-if="updateState"
      :type="updateState.remaining > 0 ? 'info' : 'warning'"
      :closable="false"
      show-icon
    >
      <template #title>
        当日地址修改：<strong class="lsc-num">{{ updateState.todayUpdatedCount }}</strong>
        / {{ updateState.dailyLimit }} 次，剩余
        <strong class="lsc-num" :class="{ 'lsc-gold-text': updateState.remaining > 0 }">{{ updateState.remaining }}</strong> 次
        <span v-if="updateState.remaining === 0" style="margin-left: 8px; color: var(--lsc-danger);">已达上限，次日 0 点重置</span>
      </template>
    </el-alert>

    <div class="addr-list">
      <div v-for="addr in list" :key="addr.id" class="addr-card">
        <div class="addr-card__head">
          <div class="addr-card__label">
            <el-icon v-if="addr.isPrimary" class="addr-card__star"><StarFilled /></el-icon>
            <span>{{ addr.label || '门店地址' }}</span>
          </div>
          <el-tag v-if="addr.isPrimary" type="warning" size="small" effect="dark">主地址</el-tag>
        </div>
        <div class="addr-card__addr">
          {{ addr.province }}{{ addr.city }}{{ addr.district }}{{ addr.addressDetail }}
        </div>
        <div class="addr-card__meta">
          <span class="muted">经纬度</span>
          <span class="lsc-num">{{ fmt(addr.longitude) }}, {{ fmt(addr.latitude) }}</span>
        </div>
        <div v-if="addr.contactPhone" class="addr-card__meta">
          <span class="muted">联系电话</span>
          <span class="lsc-num">{{ addr.contactPhone }}</span>
        </div>
        <div class="addr-card__actions">
          <el-button v-if="!addr.isPrimary" link type="warning" :icon="Star" @click="setPrimary(addr)">设为主地址</el-button>
          <el-button link type="primary" :icon="Edit" @click="openEdit(addr)">编辑</el-button>
          <el-button link type="danger" :icon="Delete" :disabled="addr.isPrimary === 1 && list.length > 1" @click="remove(addr)">删除</el-button>
        </div>
      </div>

      <div v-if="list.length === 0 && !loading" class="addr-empty" @click="openCreate">
        <el-icon><Plus /></el-icon>
        <span>添加第一个线下地址</span>
      </div>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑地址' : '添加地址'"
      width="780px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="dialog-grid">
          <el-form-item label="地址标签" prop="label">
            <el-input v-model="form.label" placeholder="如：朝阳旗舰店" maxlength="32" />
          </el-form-item>
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" placeholder="门店电话" maxlength="20" />
          </el-form-item>
        </div>

        <el-form-item label="详细地址" prop="addressDetail">
          <el-input v-model="form.addressDetail" placeholder="街道门牌号" maxlength="256" />
        </el-form-item>

        <el-form-item label="地图选点" prop="longitude">
          <MapPicker
            :model-value="{ longitude: form.longitude, latitude: form.latitude, address: form.addressDetail }"
            @update:model-value="onMapPick"
          />
        </el-form-item>

        <el-form-item label="设为主地址">
          <el-switch v-model="form.isPrimary" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.addr-list {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.addr-card {
  background: var(--lsc-surface);
  border: 1px solid var(--lsc-border-soft);
  border-radius: var(--lsc-radius-md);
  padding: 18px;
  box-shadow: var(--lsc-shadow-sm);
  transition: all 0.18s ease;
}

.addr-card:hover {
  border-color: var(--lsc-primary-300);
  box-shadow: var(--lsc-shadow-md);
}

.addr-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.addr-card__label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  font-size: 15px;
  color: var(--lsc-text);
}

.addr-card__star {
  color: var(--lsc-gold-500);
}

.addr-card__addr {
  font-size: 13.5px;
  color: var(--lsc-text-regular);
  line-height: 1.6;
  margin-bottom: 10px;
}

.addr-card__meta {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  font-size: 12.5px;
}
.muted { color: var(--lsc-text-secondary); }

.addr-card__actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  border-top: 1px dashed var(--lsc-border-soft);
  padding-top: 10px;
}

.addr-empty {
  grid-column: 1 / -1;
  padding: 48px;
  border: 1.5px dashed var(--lsc-border);
  border-radius: var(--lsc-radius-md);
  text-align: center;
  color: var(--lsc-text-placeholder);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  transition: all 0.18s ease;
}
.addr-empty:hover {
  border-color: var(--lsc-primary-400);
  color: var(--lsc-primary-600);
}

.dialog-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
</style>
