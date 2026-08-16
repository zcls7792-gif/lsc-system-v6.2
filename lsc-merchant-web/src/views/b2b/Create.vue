<script setup lang="ts">
// 发起B2B交易 — 对手方、交易描述、金额、LSC数量自动1:1、上传贸易凭证
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, Check } from '@element-plus/icons-vue'
import { createB2B } from '@/api/b2b'
import ImageUpload from '@/components/ImageUpload.vue'

const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  counterparty: '',
  tradeDescription: '',
  totalAmountRmb: 0,
  contractNo: '',
  tradeEvidenceUrls: [] as string[]
})

// LSC 数量 = 人民币金额 (1:1)
const lscAmount = computed(() => Math.floor(Number(form.totalAmountRmb) || 0))

const rules: FormRules = {
  counterparty: [
    { required: true, message: '请输入对手方手机号或用户ID', trigger: 'blur' }
  ],
  tradeDescription: [
    { required: true, message: '请输入交易描述', trigger: 'blur' },
    { max: 512, message: '交易描述不超过 512 字', trigger: 'blur' }
  ],
  totalAmountRmb: [
    { required: true, message: '请输入交易金额', trigger: 'blur' },
    {
      validator: (_r, v, cb) => (v <= 0 ? cb(new Error('交易金额必须大于 0')) : cb()),
      trigger: 'blur'
    }
  ]
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (lscAmount.value <= 0) {
      ElMessage.warning('LSC 流转数量必须大于 0')
      return
    }
    submitting.value = true
    try {
      const res = await createB2B({
        counterparty: form.counterparty,
        tradeDescription: form.tradeDescription,
        totalAmountRmb: Number(form.totalAmountRmb),
        contractNo: form.contractNo || undefined,
        tradeEvidenceUrls: form.tradeEvidenceUrls
      })
      ElMessage.success('B2B 交易已发起，等待对手方确认')
      router.push('/b2b/list')
    } finally {
      submitting.value = false
    }
  })
}
</script>

<template>
  <div class="lsc-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">发起 B2B 交易</h1>
        <p class="lsc-page-subtitle">向另一商家发起 LSC 流转交易 · 1 元 = 1 LSC</p>
      </div>
      <el-button :icon="ArrowLeft" @click="router.push('/b2b/list')">返回列表</el-button>
    </div>

    <div class="create-grid">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="create-main"
      >
        <div class="lsc-card">
          <div class="lsc-card__pad">
            <h3 class="block-title">交易信息</h3>

            <el-form-item label="对手方" prop="counterparty">
              <el-input v-model="form.counterparty" placeholder="输入对手方手机号或用户ID" clearable />
              <div class="tip">对方必须是已通过审核的链盛通商家</div>
            </el-form-item>

            <el-form-item label="交易描述" prop="tradeDescription">
              <el-input
                v-model="form.tradeDescription"
                type="textarea"
                :rows="3"
                placeholder="描述本次交易的商品/服务内容"
                maxlength="512"
                show-word-limit
              />
            </el-form-item>

            <el-form-item label="合同编号">
              <el-input v-model="form.contractNo" placeholder="选填，如 HT-2026-0001" clearable />
            </el-form-item>

            <el-form-item label="贸易凭证">
              <ImageUpload v-model="form.tradeEvidenceUrls" :max="9" dir="b2b" />
              <div class="tip">上传合同、发货单等贸易凭证，AI 将自动核验真实性</div>
            </el-form-item>
          </div>
        </div>
      </el-form>

      <div class="create-side">
        <div class="lsc-card">
          <div class="lsc-card__pad">
            <h3 class="block-title">金额</h3>

            <el-form-item label="交易总金额 (元)" prop="totalAmountRmb" label-position="top">
              <el-input-number
                v-model="form.totalAmountRmb"
                :min="0"
                :precision="2"
                :step="100"
                controls-position="right"
                class="full-width"
              />
            </el-form-item>

            <div class="sync-box">
              <div class="sync-row">
                <span class="sync-label">交易金额</span>
                <span class="lsc-num">¥ {{ Number(form.totalAmountRmb || 0).toFixed(2) }}</span>
              </div>
              <el-icon class="sync-arrow"><Check /></el-icon>
              <div class="sync-row">
                <span class="sync-label">LSC 流转数量</span>
                <strong class="lsc-num lsc-gold-text">{{ lscAmount }} LSC</strong>
              </div>
              <div class="sync-tip">按 1:1 比例自动换算，向下取整</div>
            </div>
          </div>
        </div>

        <div class="lsc-card">
          <div class="lsc-card__pad submit-card">
            <el-button type="primary" :icon="Check" :loading="submitting" size="large" class="full-width" @click="submit">
              发起交易
            </el-button>
            <p class="submit-tip">发起后需对手方确认，确认后 LSC 自动流转</p>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.create-grid {
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

.tip {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--lsc-text-placeholder);
}

.sync-box {
  margin-top: 8px;
  padding: 14px 16px;
  border-radius: var(--lsc-radius);
  background: linear-gradient(135deg, var(--lsc-gold-50), #fff7ed);
  border: 1px solid var(--lsc-gold-100);
}

.sync-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13.5px;
  padding: 4px 0;
}

.sync-label { color: var(--lsc-text-secondary); }

.sync-arrow {
  display: block;
  margin: 6px auto;
  color: var(--lsc-gold-600);
  font-size: 18px;
}

.sync-tip {
  margin-top: 6px;
  text-align: center;
  font-size: 11.5px;
  color: var(--lsc-text-placeholder);
}

.full-width { width: 100%; }

.submit-card .full-width { height: 44px; font-weight: 700; }
.submit-tip { margin-top: 10px; text-align: center; font-size: 12px; color: var(--lsc-text-placeholder); }

@media (max-width: 1080px) {
  .create-grid { grid-template-columns: 1fr; }
}
</style>
