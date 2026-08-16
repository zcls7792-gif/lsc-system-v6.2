package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiMerchantProfileDTO;

/**
 * 商家画像构建服务
 * <p>
 * 基于商家交易、核销、B2B流转、客诉等数据构建商家画像，用于资格审核与风控。
 * 调用外部AI模型API，超时10秒自动降级为人工审核模式。
 * </p>
 */
public interface AiMerchantProfileService {

    /**
     * 构建商家画像
     *
     * @param request 画像请求(商家交易汇总、客诉标签)
     * @return 画像响应(信用评分、画像标签、健康度)
     */
    AiMerchantProfileDTO.Response buildProfile(AiMerchantProfileDTO.Request request);
}
