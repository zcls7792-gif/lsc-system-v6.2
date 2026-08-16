<script setup lang="ts">
// 店铺信息 — 门店名称、联系方式、营业时间编辑
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Check, Picture } from '@element-plus/icons-vue'
import { getStoreInfo, updateStoreInfo, type StoreInfoParams } from '@/api/store'
import type { MerchantExtension } from '@/api/types'
import { AUDIT_STATUS_MAP } from '@/utils/maps'
import ImageUpload from '@/components/ImageUpload.vue'

const loading = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const info = ref<MerchantExtension | null>(null)
const businessLicenseImg = ref<string[]>([])

const form = reactive<StoreInfoParams>({
  storeName: '',
  contactPhone: '',
  businessHours: '',
  businessLicense: '',
  businessLicenseImg: ''
})

const rules: FormRules = {
  storeName: [
    { required: true, message: '请输入门店名称', trigger: 'blur' },
    { max: 128, message: '门店名称不超过 128 字', trigger: 'blur' }
  ],
  contactPhone: [
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  businessHours: [{ max: 128, message: '营业时间不超过 128 字', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await getStoreInfo()
    info.value = res
    form.storeName = res.storeName || ''
    form.contactPhone = res.contactPhone || ''
    form.businessHours = res.businessHours || ''
    form.businessLicense = res.businessLicense || ''
    form.businessLicenseImg = res.businessLicenseImg || ''
    businessLicenseImg.value = res.businessLicenseImg ? [res.businessLicenseImg] : []
  } finally {
    loading.value = false
  }
}

function onLicenseChange(urls: string[]) {
  form.businessLicenseImg = urls[0] || ''
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      const res = await updateStoreInfo(form)
      info.value = res
      ElMessage.success('店铺信息已保存')
    } finally {
      submitting.value = false
    }
  })
}

onMounted(load)
</script>

<template>
  <div class="lsc-page" v-loading="loading">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">店铺信息</h1>
        <p class="lsc-page-subtitle">维护门店基础资料 · 营业执照 · 联系方式</p>
      </div>
    </div>

    <div class="info-grid">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="info-main"
      >
        <div class="lsc-card">
          <div class="lsc-card__pad">
            <h3 class="block-title">基础资料</h3>

            <div class="audit-row" v-if="info">
              <span class="audit-label">审核状态：</span>
              <el-tag :type="AUDIT_STATUS_MAP[info.auditStatus]?.type" effect="light">
                {{ AUDIT_STATUS_MAP[info.auditStatus]?.label || '未知' }}
              </el-tag>
              <span class="audit-id muted">商家ID: <span class="lsc-num">{{ info.merchantId }}</span></span>
            </div>

            <el-form-item label="门店名称" prop="storeName">
              <el-input v-model="form.storeName" placeholder="如：链盛通体验店·朝阳店" maxlength="128" show-word-limit />
            </el-form-item>

            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="店长或客服电话" maxlength="20" />
            </el-form-item>

            <el-form-item label="营业时间" prop="businessHours">
              <el-input v-model="form.businessHours" placeholder="如：09:00 - 22:00" maxlength="128" />
            </el-form-item>

            <el-form-item label="营业执照号" prop="businessLicense">
              <el-input v-model="form.businessLicense" placeholder="统一社会信用代码" maxlength="128" />
            </el-form-item>
          </div>
        </div>

        <div class="lsc-card" style="margin-top: 16px">
          <div class="lsc-card__pad">
            <h3 class="block-title">营业执照图片</h3>
            <ImageUpload v-model="businessLicenseImg" :max="1" dir="store" @update:model-value="onLicenseChange" />
            <p class="tip">用于平台审核，需清晰可辨认</p>
          </div>
        </div>
      </el-form>

      <div class="info-side">
        <div class="lsc-card store-card">
          <div class="lsc-card__pad">
            <h3 class="block-title">店铺概览</h3>
            <div v-if="info" class="store-info">
              <div class="store-avatar">
                <el-icon><Picture /></el-icon>
              </div>
              <div class="store-name">{{ form.storeName || '未设置' }}</div>
              <div class="store-phone muted lsc-num">{{ form.contactPhone || '—' }}</div>
              <div class="store-hours">{{ form.businessHours || '营业时间未设置' }}</div>

              <el-divider />

              <div class="kv"><span>监管账户</span><span class="lsc-num">{{ info.regulatoryAccountNo || '—' }}</span></div>
              <div class="kv"><span>主账户</span><span class="lsc-num">{{ info.mainAccountNo || '—' }}</span></div>
              <div class="kv"><span>信用分</span><span class="lsc-num lsc-gold-text">{{ info.creditScore }}</span></div>
              <div class="kv"><span>核销档位</span><span class="lsc-num">{{ info.nhLimitLevel }}</span></div>
              <div class="kv"><span>日核销限额</span><span class="lsc-num">{{ info.dailyNhLimit }} LSC</span></div>
              <div class="kv"><span>月营业额</span><span class="lsc-num">¥ {{ Number(info.monthlyRevenue || 0).toFixed(2) }}</span></div>
            </div>
          </div>
        </div>

        <div class="lsc-card submit-card">
          <div class="lsc-card__pad">
            <el-button type="primary" size="large" :icon="Check" :loading="submitting" class="full-width" @click="submit">
              保存修改
            </el-button>
            <p class="submit-tip">修改后将重新提交平台审核</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.info-grid {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 16px;
  align-items: flex-start;
}

.block-title {
  font-size: 15px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--lsc-border-soft);
}

.audit-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  padding: 10px 12px;
  background: var(--lsc-bg-soft);
  border-radius: 8px;
  font-size: 13px;
}
.audit-label { color: var(--lsc-text-secondary); }
.audit-id { margin-left: auto; font-size: 12px; }

.tip { margin-top: 8px; font-size: 11.5px; color: var(--lsc-text-placeholder); }

.store-avatar {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  background: linear-gradient(135deg, var(--lsc-primary-100), var(--lsc-primary-50));
  color: var(--lsc-primary-700);
  display: grid;
  place-items: center;
  font-size: 28px;
  margin: 0 auto 12px;
}

.store-name { text-align: center; font-size: 17px; font-weight: 700; }
.store-phone { text-align: center; font-size: 13px; margin-top: 4px; }
.store-hours { text-align: center; font-size: 12px; color: var(--lsc-text-secondary); margin-top: 4px; }

.kv {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
}
.kv span:first-child { color: var(--lsc-text-secondary); }
.kv span:last-child { color: var(--lsc-text); font-weight: 500; }
.muted { color: var(--lsc-text-secondary); }

.full-width { width: 100%; }
.submit-card .full-width { height: 44px; font-weight: 700; }
.submit-tip { margin-top: 10px; text-align: center; font-size: 12px; color: var(--lsc-text-placeholder); }

@media (max-width: 1080px) {
  .info-grid { grid-template-columns: 1fr; }
}
</style>
