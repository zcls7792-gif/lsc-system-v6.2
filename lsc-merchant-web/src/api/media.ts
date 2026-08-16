import { upload, post } from '@/utils/request'
import type { UploadResult } from './types'

/**
 * 图片上传 (多图)
 * 后端: /api/media/upload-image
 */
export function uploadImage(file: File, dir: string = 'product') {
  const form = new FormData()
  form.append('file', file)
  form.append('dir', dir)
  return upload<UploadResult>('/media/upload-image', form)
}

/**
 * 视频上传 (含进度)
 * 后端: /api/media/upload-video
 */
export function uploadVideo(
  file: File,
  onProgress?: (percent: number) => void
) {
  const form = new FormData()
  form.append('file', file)
  form.append('dir', 'video')
  return upload<UploadResult>('/media/upload-video', form, {
    onUploadProgress: (e) => {
      if (onProgress && e.total) {
        const percent = Math.round((e.loaded * 100) / e.total)
        onProgress(percent)
      }
    }
  })
}

/** 查询视频转码状态 */
export function getVideoStatus(url: string) {
  return post<UploadResult>('/media/video-status', { url })
}
