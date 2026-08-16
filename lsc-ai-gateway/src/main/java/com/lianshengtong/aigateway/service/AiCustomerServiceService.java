package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiCustomerServiceDTO;

/**
 * AI客服问答服务
 * <p>
 * 智能客服问答，支持多轮对话与知识库检索。
 * 调用外部AI模型API，超时10秒自动降级建议转人工。
 * </p>
 */
public interface AiCustomerServiceService {

    /**
     * 客服问答
     *
     * @param request 问答请求(用户问题、历史上下文)
     * @return 问答响应(AI回答、命中知识库、是否转人工)
     */
    AiCustomerServiceDTO.Response chat(AiCustomerServiceDTO.Request request);
}
