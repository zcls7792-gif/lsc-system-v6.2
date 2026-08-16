<script setup lang="ts">
// 发布商品 — 名称、描述、图片上传、视频上传、价格自动同步LSC价格、库存、类目
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { ArrowLeft, Check } from '@element-plus/icons-vue'
import { getCategories, getProductDetail, publishProduct, type PublishProductParams } from '@/api/product'
import type { ProductCategory } from '@/api/types'
import ImageUpload from '@/components/ImageUpload.vue'
import VideoUpload from '@/components/VideoUpload.vue'
import '@wangeditor/editor/dist/css/style.css'
import { onBeforeUnmount, shallowRef } from 'vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'

const route = useRoute()
const router = useRouter()
const formRef = ref<FormInstance>()
const submitting = ref(false)
const categories = ref<ProductCategory[]>([])
const videoCover = ref('')
const videoDuration = ref(0)

// 富文本编辑器
const editorRef = shallowRef()
const editorMode = 'default'

const form = reactive<PublishProductParams & { categoryIdPath?: number[] }>({
  productName: '',
  productDesc: '',
  productImages: [],
  price: 0,
  stock: 1,
  categoryId: 0,
  videoUrl: '',
  videoCoverUrl: '',
  videoDuration: 0
})

const rules: FormRules = {
  productName: [
    { required: true, message: '请输入商品名称', trigger: 'blur' },
    { max: 256, message: '商品名称不超过 256 字', trigger: 'blur' }
  ],
  productImages: [
    {
      validator: (_r, _v, cb) => {
        if (!form.productImages || form.productImages.length === 0) {
          cb(new Error('请至少上传 1 张商品图片'))
        } else {
          cb()
        }
      },
      trigger: 'change'
    }
  ],
  price: [
    { required: true, message: '请输入商品价格', trigger: 'blur' },
    {
      validator: (_r, v, cb) => (v <= 0 ? cb(new Error('价格必须大于 0')) : cb()),
      trigger: 'blur'
    }
  ],
  stock: [
    { required: true, message: '请输入库存', trigger: 'blur' },
    { type: 'number', min: 0, message: '库存不能小于 0', trigger: 'blur' }
  ],
  categoryId: [
    {
      validator: (_r, v, cb) => (v <= 0 ? cb(new Error('请选择商品类目')) : cb()),
      trigger: 'change'
    }
  ]
}

// 价格 = LSC 数量 (1:1)
const lscPrice = computed(() => form.price)

const cascaderProps = {
  value: 'id',
  label: 'name',
  children: 'children',
  emitPath: false
}

const categoryTree = computed(() => buildTree(categories.value))

function buildTree(list: ProductCategory[]): any[] {
  const map: Record<number, any> = {}
  const roots: any[] = []
  list.forEach((c) => (map[c.id] = { ...c, children: [] }))
  list.forEach((c) => {
    if (c.parentId && map[c.parentId]) {
      map[c.parentId].children.push(map[c.id])
    } else {
      roots.push(map[c.id])
    }
  })
  // 清理空 children
  const clean = (nodes: any[]) => {
    nodes.forEach((n) => {
      if (n.children.length === 0) delete n.children
      else clean(n.children)
    })
  }
  clean(roots)
  return roots
}

function handleCascader(val: any) {
  // el-cascader emitPath=false 时返回选中叶子节点的 value
  form.categoryId = Number(val) || 0
}

function onVideoSuccess(res: any) {
  if (res.coverUrl) videoCover.value = res.coverUrl
  if (res.duration) videoDuration.value = res.duration
  form.videoCoverUrl = res.coverUrl || ''
  form.videoDuration = res.duration || 0
}

// 富文本编辑器配置
const editorConfig = {
  placeholder: '请输入商品描述（支持图文）',
  MENU_CONF: {
    uploadImage: {
      async customUpload(file: File, insertFn: Function) {
        // 走统一上传接口
        const { uploadImage } = await import('@/api/media')
        const fd = new FormData()
        fd.append('file', file)
        fd.append('dir', 'product-desc')
        try {
          const res = await uploadImage(file, 'product-desc')
          insertFn(res.url, file.name, res.url)
        } catch {
          ElMessage.error('图片插入失败')
        }
      }
    }
  }
}

function handleEditorCreated(editor: any) {
  editorRef.value = editor
}

function beforeEditorDestroy() {
  editorRef.value?.destroy()
}

onBeforeUnmount(() => {
  editorRef.value?.destroy()
})

async function loadCategories() {
  categories.value = await getCategories()
}

async function loadDetail() {
  const id = Number(route.query.id)
  if (!id) return
  const p = await getProductDetail(id)
  form.id = p.id
  form.productName = p.productName
  form.productDesc = p.productDesc || ''
  form.productImages = form.productImages && p.productImages ? JSON.parse(p.productImages) : []
  form.price = Number(p.price)
  form.stock = p.stock
  form.categoryId = p.categoryId
  form.videoUrl = p.videoUrl
  form.videoCoverUrl = p.videoCoverUrl || ''
  form.videoDuration = p.videoDuration || 0
  videoCover.value = p.videoCoverUrl || ''
  videoDuration.value = p.videoDuration || 0
  setTimeout(() => editorRef.value?.setHtml(p.productDesc || ''), 100)
}

async function submit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      await publishProduct(form)
      ElMessage.success(form.id ? '商品已更新' : '商品已发布，等待审核')
      router.push('/product/list')
    } finally {
      submitting.value = false
    }
  })
}

onMounted(async () => {
  await loadCategories()
  await loadDetail()
})
</script>

<template>
  <div class="lsc-page">
    <div class="lsc-page-header">
      <div>
        <h1 class="lsc-page-title">{{ form.id ? '编辑商品' : '发布商品' }}</h1>
        <p class="lsc-page-subtitle">价格自动同步 LSC 价格（1 元 = 1 LSC）</p>
      </div>
      <el-button :icon="ArrowLeft" @click="router.push('/product/list')">返回列表</el-button>
    </div>

    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      class="publish-form"
    >
      <div class="publish-grid">
        <div class="publish-main">
          <div class="lsc-card">
            <div class="lsc-card__pad">
              <h3 class="block-title">基础信息</h3>

              <el-form-item label="商品名称" prop="productName">
                <el-input v-model="form.productName" placeholder="请输入商品名称" maxlength="256" show-word-limit />
              </el-form-item>

              <el-form-item label="商品描述">
                <div class="rich-wrap">
                  <Toolbar :editor="editorRef" :defaultConfig="editorConfig" mode="editorMode" class="rich-toolbar" />
                  <Editor
                    v-model="form.productDesc"
                    :defaultConfig="editorConfig"
                    mode="editorMode"
                    class="rich-editor"
                    @onCreated="handleEditorCreated"
                  />
                </div>
              </el-form-item>

              <el-form-item label="商品图片" prop="productImages">
                <ImageUpload v-model="form.productImages" :max="6" dir="product" />
              </el-form-item>

              <el-form-item label="商品视频">
                <VideoUpload
                  v-model="form.videoUrl"
                  :cover-url="videoCover"
                  :duration="videoDuration"
                  @success="onVideoSuccess"
                />
              </el-form-item>
            </div>
          </div>
        </div>

        <div class="publish-side">
          <div class="lsc-card">
            <div class="lsc-card__pad">
              <h3 class="block-title">价格与库存</h3>

              <el-form-item label="商品价格 (元)" prop="price">
                <el-input-number
                  v-model="form.price"
                  :min="0"
                  :precision="2"
                  :step="1"
                  controls-position="right"
                  class="full-width"
                />
                <div class="lsc-sync">
                  <span>同步 LSC 价格：</span>
                  <strong class="lsc-num lsc-gold-text">{{ lscPrice }} LSC</strong>
                  <span class="lsc-sync-tip">(1 元 = 1 LSC)</span>
                </div>
              </el-form-item>

              <el-form-item label="库存数量" prop="stock">
                <el-input-number
                  v-model="form.stock"
                  :min="0"
                  :step="1"
                  controls-position="right"
                  class="full-width"
                />
              </el-form-item>

              <el-form-item label="商品类目" prop="categoryId">
                <el-cascader
                  :model-value="form.categoryId"
                  :options="categoryTree"
                  :props="cascaderProps"
                  placeholder="选择类目"
                  class="full-width"
                  @change="handleCascader"
                />
              </el-form-item>
            </div>
          </div>

          <div class="lsc-card publish-submit">
            <div class="lsc-card__pad">
              <el-button type="primary" :icon="Check" :loading="submitting" class="full-width" size="large" @click="submit">
                {{ form.id ? '保存修改' : '发布商品' }}
              </el-button>
              <p class="publish-tip">发布后将进入 AI 审核，审核通过自动上架</p>
            </div>
          </div>
        </div>
      </div>
    </el-form>
  </div>
</template>

<style scoped>
.publish-grid {
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

.full-width {
  width: 100%;
}

.lsc-sync {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  padding: 8px 12px;
  background: var(--lsc-gold-50);
  border: 1px solid var(--lsc-gold-100);
  border-radius: 8px;
  font-size: 13px;
  color: var(--lsc-text-secondary);
}

.lsc-sync-tip {
  color: var(--lsc-text-placeholder);
  font-size: 11.5px;
}

.publish-submit .full-width {
  height: 44px;
  font-weight: 700;
}

.publish-tip {
  margin-top: 10px;
  text-align: center;
  font-size: 12px;
  color: var(--lsc-text-placeholder);
}

.rich-wrap {
  border: 1px solid var(--lsc-border);
  border-radius: 8px;
  overflow: hidden;
}

.rich-toolbar {
  border-bottom: 1px solid var(--lsc-border);
}

.rich-editor {
  height: 220px;
  overflow-y: auto;
}

@media (max-width: 1080px) {
  .publish-grid {
    grid-template-columns: 1fr;
  }
}
</style>
