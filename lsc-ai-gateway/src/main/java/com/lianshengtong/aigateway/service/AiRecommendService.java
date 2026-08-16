package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiRecommendDTO;

/**
 * 商品个性化推荐服务
 * <p>
 * 基于用户画像与行为进行商品个性化推荐。
 * 调用外部AI模型API，超时10秒自动降级为热门兜底。
 * </p>
 */
public interface AiRecommendService {

    /**
     * 商品推荐
     *
     * @param request 推荐请求(用户信息、候选商品、场景)
     * @return 推荐响应(排序商品列表、推荐理由)
     */
    AiRecommendDTO.Response recommend(AiRecommendDTO.Request request);
}
