package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiProductReviewDTO;

/**
 * 商品AI审核服务
 * <p>
 * 图片违规检测、视频多模态识别、文案敏感词检测(如"保本/增值/理财/收益")。
 * 调用外部AI模型API，超时10秒自动降级为人工审核模式。
 * </p>
 */
public interface AiProductReviewService {

    /**
     * 商品审核
     *
     * @param request 审核请求(图片URL列表、视频URL、商品文案)
     * @return 审核响应(违规明细、敏感词、审核结果)
     */
    AiProductReviewDTO.Response review(AiProductReviewDTO.Request request);
}
