package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiRiskControlDTO;

/**
 * 动态风控评分服务
 * <p>
 * 基于用户行为特征进行风控评分。
 * 调用外部AI模型API，超时10秒自动降级为人工审核模式。
 * </p>
 */
public interface AiRiskControlService {

    /**
     * 风控评分
     *
     * @param request 评分请求(用户行为特征)
     * @return 评分响应(风控评分、风险等级、命中规则)
     */
    AiRiskControlDTO.Response score(AiRiskControlDTO.Request request);
}
