package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiB2bVerifyDTO;

/**
 * B2B贸易背景核验服务
 * <p>
 * OCR提取凭证信息、合同匹配度评分、商家画像异常检测。
 * 调用外部AI模型API，超时10秒自动降级为人工审核模式。
 * </p>
 */
public interface AiB2bVerifyService {

    /**
     * B2B贸易背景核验
     *
     * @param request 核验请求(订单信息、凭证图片列表)
     * @return 核验响应(OCR结果、合同匹配度、画像异常)
     */
    AiB2bVerifyDTO.Response verify(AiB2bVerifyDTO.Request request);
}
