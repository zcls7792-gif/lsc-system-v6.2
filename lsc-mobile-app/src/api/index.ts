/**
 * API 统一导出
 * 用法：import { login, getProfile } from '@/api'
 */
export * from './user'
export * from './product'
export * from './order'
export * from './ledger'
export * from './map'

/** AI 客服消息 */
import { http } from '@/utils/request'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
  createTime?: string
}

export interface AiChatParams {
  message: string
  /** 上下文消息（不含本次） */
  history?: ChatMessage[]
}

/** AI 客服对话 */
export function chatWithAi(data: AiChatParams) {
  return http.post<{ reply: string; createTime: string }>('/api/ai/customer-service', data)
}

/** AI 客服快捷问题 */
export function getAiQuickQuestions() {
  return http.get<string[]>('/api/ai/quick-questions')
}
