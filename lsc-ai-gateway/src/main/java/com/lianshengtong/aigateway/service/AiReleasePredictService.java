package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiReleasePredictDTO;

/**
 * LSC释放趋势预测服务
 * <p>
 * 基于历史核销率序列，预测未来7-30天核销率走势，供释放服务参数调节参考。
 * 调用外部AI模型API，超时10秒自动降级。
 * </p>
 */
public interface AiReleasePredictService {

    /**
     * 释放趋势预测
     *
     * @param request 预测请求(历史核销率序列)
     * @return 预测响应(未来核销率序列、7/30天均值、趋势方向)
     */
    AiReleasePredictDTO.Response predict(AiReleasePredictDTO.Request request);
}
