import { http } from '@/utils/request'

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system'
  content: string
  createdAt?: string
}

/** AI 快捷问题（V7.7.2 AI默认关闭，返回占位） */
export function getAiQuickQuestions(): Promise<string[]> {
  return Promise.resolve([
    'LSC 权益如何使用？',
    '每日释放规则是什么？',
    '退款后权益如何处理？',
    '如何推荐好友获得优惠券？',
  ])
}

/** AI 对话（V7.7.2 AI默认关闭，返回占位回复） */
export function chatWithAi(_message: string, _history: ChatMessage[] = []): Promise<ChatMessage> {
  return Promise.resolve({
    role: 'assistant',
    content: '您好，我是链盛通 AI 助手。关于 LSC 权益、订单、退款等问题，我可以为您解答。当前为演示模式。',
    createdAt: new Date().toISOString(),
  })
}
