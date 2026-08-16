<script setup lang="ts">
// 图片上传组件 — 多图、预览、调用 /api/media/upload-image
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { UploadProps, UploadUserFile } from 'element-plus'
import { uploadImage } from '@/api/media'

interface Props {
  modelValue: string[]
  max?: number
  /** 上传目录: product/store/b2b 等 */
  dir?: string
  /** 单图大小上限 (MB) */
  maxSize?: number
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  max: 6,
  dir: 'product',
  maxSize: 5,
  disabled: false
})

const emit = defineEmits<{ (e: 'update:modelValue', v: string[]): void }>()

const fileList = ref<UploadUserFile[]>([])
const previewVisible = ref(false)
const previewUrl = ref('')
const uploading = ref(false)

// 同步外部 v-model 到内部 fileList
watch(
  () => props.modelValue,
  (urls) => {
    const current = fileList.value.map((f) => f.url).filter(Boolean)
    if (JSON.stringify(current) !== JSON.stringify(urls)) {
      fileList.value = (urls || []).map((url) => ({ name: url.split('/').pop() || 'image', url }))
    }
  },
  { immediate: true }
)

function emitChange() {
  emit('update:modelValue', urls.value)
}

// 已上传图片 URL 列表 (过滤空值)
const urls = computed(() =>
  fileList.value.map((f) => f.url).filter((u): u is string => !!u)
)

const hidePlus = computed(() => fileList.value.length >= props.max)

const customRequest: UploadProps['httpRequest'] = async (option) => {
  const file = option.file as File
  uploading.value = true
  try {
    const result = await uploadImage(file, props.dir)
    fileList.value.push({
      name: file.name,
      url: result.url,
      status: 'success'
    })
    emitChange()
    ElMessage.success('图片上传成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

const beforeUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const isImage = rawFile.type.startsWith('image/')
  if (!isImage) {
    ElMessage.error('只能上传图片文件')
    return false
  }
  const overSize = rawFile.size / 1024 / 1024 > props.maxSize
  if (overSize) {
    ElMessage.error(`图片大小不能超过 ${props.maxSize}MB`)
    return false
  }
  return true
}

function handleRemove(index: number) {
  fileList.value.splice(index, 1)
  emitChange()
}

function handlePreview(url: string) {
  previewUrl.value = url
  previewVisible.value = true
}
</script>

<template>
  <div class="img-upload">
    <el-upload
      list-type="picture-card"
      :file-list="fileList"
      :show-file-list="false"
      :http-request="customRequest"
      :before-upload="beforeUpload"
      :disabled="disabled || uploading"
      multiple
      accept="image/*"
    >
      <div v-show="!hidePlus" class="img-upload__trigger">
        <el-icon class="img-upload__icon"><Plus /></el-icon>
        <span class="img-upload__text">上传图片</span>
      </div>
    </el-upload>

    <div class="img-upload__list">
      <div v-for="(url, i) in urls" :key="url + i" class="img-upload__item">
        <img :src="url" class="img-upload__img" @click="handlePreview(url)" />
        <div class="img-upload__mask">
          <span class="img-upload__action" @click="handlePreview(url)">预览</span>
          <span v-if="!disabled" class="img-upload__action img-upload__action--danger" @click="handleRemove(i)">删除</span>
        </div>
      </div>
      <div v-if="uploading" class="img-upload__item img-upload__item--loading">
        <el-icon class="is-loading"><Plus /></el-icon>
        <span>上传中…</span>
      </div>
    </div>

    <div class="img-upload__tip">
      最多 {{ max }} 张，单张 ≤ {{ maxSize }}MB，支持 JPG / PNG / WebP
    </div>

    <el-image-viewer v-if="previewVisible" :url-list="[previewUrl]" @close="previewVisible = false" />
  </div>
</template>

<style scoped>
.img-upload__list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.img-upload__item {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--lsc-border);
  background: var(--lsc-bg-soft);
}

.img-upload__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
}

.img-upload__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: rgba(15, 23, 42, 0.5);
  opacity: 0;
  transition: opacity 0.18s ease;
}

.img-upload__item:hover .img-upload__mask {
  opacity: 1;
}

.img-upload__action {
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  font-weight: 600;
}

.img-upload__action--danger {
  color: #fca5a5;
}

.img-upload__item--loading {
  display: grid;
  place-items: center;
  color: var(--lsc-text-secondary);
  font-size: 12px;
}

.img-upload__trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--lsc-text-placeholder);
}

.img-upload__icon {
  font-size: 22px;
}

.img-upload__text {
  font-size: 12px;
}

:deep(.el-upload--picture-card) {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  border: 1.5px dashed var(--lsc-border);
  background: var(--lsc-bg-soft);
  transition: all 0.18s ease;
}

:deep(.el-upload--picture-card:hover) {
  border-color: var(--lsc-primary-400);
  color: var(--lsc-primary-600);
}

.img-upload__tip {
  margin-top: 8px;
  font-size: 11.5px;
  color: var(--lsc-text-placeholder);
}
</style>
