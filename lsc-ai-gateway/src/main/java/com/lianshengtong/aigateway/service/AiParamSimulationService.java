package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiParamSimulationDTO;

/**
 * 参数调整仿真推演服务
 * <p>
 * 在正式变更释放参数前，对参数变更进行模拟推演，输出模拟天数内的核销率/释放量预测。
 * 调用外部AI模型API，超时10秒自动降级。
 * </p>
 */
public interface AiParamSimulationService {

    /**
     * 参数仿真推演
     *
     * @param request 推演请求(参数变更、模拟天数)
     * @return 推演响应(每日释放量序列、累计释放量、风险提示)
     */
    AiParamSimulationDTO.Response simulate(AiParamSimulationDTO.Request request);
}
