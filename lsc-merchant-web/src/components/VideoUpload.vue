<script setup lang="ts">
// 视频上传组件 — 进度条、转码状态、调用 /api/media/upload-video
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, VideoPlay, Delete, RefreshRight } from '@element-plus/icons-vue'
import type { UploadProps } from 'element-plus'
import { uploadVideo, getVideoStatus } from '@/api/media'
import type { UploadResult } from '@/api/types'

interface Props {
  modelValue?: string
  coverUrl?: string
  duration?: number
  maxSize?: number
  disabled?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  coverUrl: '',
  duration: 0,
  maxSize: 100,
  disabled: false
})

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'update:coverUrl', v: string): void
  (e: 'update:duration', v: number): void
  (e: 'success', res: UploadResult): void
}>()

const progress = ref(0)
const status = ref<'idle' | 'uploading' | 'transcoding' | 'ready' | 'failed'>('idle')
const fileName = ref('')
const videoUrl = ref('')
let pollTimer: number | null = null

watch(
  () => props.modelValue,
  (v) => {
    videoUrl.value = v || ''
    if (v) status.value = 'ready'
  },
  { immediate: true }
)

const beforeUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const isVideo = rawFile.type.startsWith('video/')
  if (!isVideo) {
    ElMessage.error('只能上传视频文件')
    return false
  }
  const overSize = rawFile.size / 1024 / 1024 > props.maxSize
  if (overSize) {
    ElMessage.error(`视频大小不能超过 ${props.maxSize}MB`)
    return false
  }
  return true
}

const customRequest: UploadProps['httpRequest'] = async (option) => {
  const file = option.file as File
  fileName.value = file.name
  progress.value = 0
  status.value = 'uploading'
  try {
    const result = await uploadVideo(file, (p) => {
      progress.value = p
    })
    videoUrl.value = result.url
    emit('update:modelValue', result.url)
    if (result.coverUrl) emit('update:coverUrl', result.coverUrl)
    if (result.duration) emit('update:duration', result.duration)
    emit('success', result)

    // 后端返回转码中状态则轮询
    if (result.status === 'transcoding' || !result.coverUrl) {
      status.value = 'transcoding'
      pollVideoStatus(result.url)
    } else {
      status.value = 'ready'
      ElMessage.success('视频上传成功')
    }
  } catch (e: any) {
    status.value = 'failed'
    ElMessage.error(e?.message || '视频上传失败')
  }
}

function pollVideoStatus(url: string) {
  if (pollTimer) window.clearInterval(pollTimer)
  let times = 0
  pollTimer = window.setInterval(async () => {
    times++
    try {
      const res = await getVideoStatus(url)
      if (res.status === 'ready') {
        status.value = 'ready'
        if (res.coverUrl) emit('update:coverUrl', res.coverUrl)
        if (res.duration) emit('update:duration', res.duration)
        ElMessage.success('视频转码完成')
        stopPoll()
      } else if (res.status === 'failed' || times > 60) {
        status.value = 'failed'
        ElMessage.error('视频转码失败')
        stopPoll()
      }
    } catch {
      stopPoll()
    }
  }, 3000)
}

function stopPoll() {
  if (pollTimer) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
}

function clearVideo() {
  stopPoll()
  videoUrl.value = ''
  status.value = 'idle'
  progress.value = 0
  fileName.value = ''
  emit('update:modelValue', '')
  emit('update:coverUrl', '')
  emit('update:duration', 0)
}

function fmtDuration(s: number) {
  if (!s) return '00:00'
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}
</script>

<template>
  <div class="video-upload">
    <!-- 上传前 -->
    <el-upload
      v-if="!videoUrl && status !== 'uploading'"
      drag
      :show-file-list="false"
      :before-upload="beforeUpload"
      :http-request="customRequest"
      :disabled="disabled"
      accept="video/*"
      class="video-upload__zone"
    >
      <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
      <div class="el-upload__text">将视频拖到此处，或<em>点击上传</em></div>
      <template #tip>
        <div class="video-upload__tip">MP4 / MOV，单文件 ≤ {{ maxSize }}MB</div>
      </template>
    </el-upload>

    <!-- 上传中 -->
    <div v-if="status === 'uploading'" class="video-upload__progress">
      <div class="video-upload__progress-head">
        <el-icon class="is-loading"><UploadFilled /></el-icon>
        <span class="video-upload__name">{{ fileName }}</span>
        <span class="video-upload__pct lsc-num">{{ progress }}%</span>
      </div>
      <el-progress :percentage="progress" :show-text="false" :stroke-width="6" color="#0d9488" />
      <div class="video-upload__stage">正在上传…</div>
    </div>

    <!-- 转码中 -->
    <div v-else-if="status === 'transcoding'" class="video-upload__progress">
      <div class="video-upload__progress-head">
        <el-icon class="is-loading"><RefreshRight /></el-icon>
        <span class="video-upload__name">{{ fileName }}</span>
      </div>
      <el-progress :percentage="100" :show-text="false" :stroke-width="6" status="success" :indeterminate="true" />
      <div class="video-upload__stage">视频转码中，请稍候…</div>
    </div>

    <!-- 上传完成 -->
    <div v-else-if="videoUrl && status === 'ready'" class="video-upload__preview">
      <div class="video-upload__cover">
        <video :src="videoUrl" :poster="coverUrl" controls></video>
        <div class="video-upload__badge">已转码</div>
      </div>
      <div class="video-upload__meta">
        <div class="video-upload__name video-upload__name--done">{{ fileName || '视频' }}</div>
        <div class="video-upload__sub">
          <el-icon><VideoPlay /></el-icon>
          <span class="lsc-num">{{ fmtDuration(duration) }}</span>
        </div>
        <div class="video-upload__actions">
          <el-button v-if="!disabled" type="danger" plain size="small" :icon="Delete" @click="clearVideo">
            重新上传
          </el-button>
        </div>
      </div>
    </div>

    <!-- 失败 -->
    <div v-else-if="status === 'failed'" class="video-upload__failed">
      <el-icon class="is-loading"><RefreshRight /></el-icon>
      <span>视频处理失败，请重新上传</span>
    </div>
  </div>
</template>

<style scoped>
.video-upload__zone {
  width: 100%;
}

:deep(.video-upload__zone .el-upload-dragger) {
  width: 100%;
  padding: 28px 16px;
  border-radius: 12px;
  background: var(--lsc-bg-soft);
  border: 1.5px dashed var(--lsc-border);
}

:deep(.video-upload__zone .el-upload-dragger:hover) {
  border-color: var(--lsc-primary-400);
}

.video-upload__tip {
  margin-top: 6px;
  color: var(--lsc-text-placeholder);
  font-size: 11.5px;
}

.video-upload__progress,
.video-upload__preview,
.video-upload__failed {
  border: 1px solid var(--lsc-border);
  border-radius: 12px;
  padding: 16px;
  background: var(--lsc-surface);
}

.video-upload__progress-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--lsc-text-regular);
  font-size: 13px;
}

.video-upload__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.video-upload__pct {
  font-weight: 700;
  color: var(--lsc-primary-700);
}

.video-upload__stage {
  margin-top: 8px;
  font-size: 12px;
  color: var(--lsc-text-secondary);
}

.video-upload__preview {
  display: flex;
  gap: 14px;
}

.video-upload__cover {
  position: relative;
  width: 200px;
  flex-shrink: 0;
}

.video-upload__cover video {
  width: 100%;
  border-radius: 8px;
  background: #000;
  aspect-ratio: 9 / 16;
  object-fit: cover;
}

.video-upload__badge {
  position: absolute;
  top: 6px;
  left: 6px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: rgba(22, 163, 74, 0.9);
  border-radius: 4px;
}

.video-upload__meta {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.video-upload__name--done {
  font-weight: 600;
  color: var(--lsc-text);
}

.video-upload__sub {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--lsc-text-secondary);
}

.video-upload__actions {
  margin-top: auto;
}

.video-upload__failed {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--lsc-danger);
  font-size: 13px;
}
</style>
