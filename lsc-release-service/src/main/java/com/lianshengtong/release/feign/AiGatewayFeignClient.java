package com.lianshengtong.release.feign;

import com.lianshengtong.common.result.R;
import com.lianshengtong.release.dto.AiReleasePredictDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * AI网关 Feign 客户端
 * <p>
 * 路由策略：
 *   <ul>
 *     <li>生产：不设 {@code ai.gateway-url} → 走服务名 lsc-ai-gateway + Nacos LB；</li>
 *     <li>沙箱/联调：设置 {@code ai.gateway-url=http(s)://host:port} → 走直连 URL，不依赖 LB。</li>
 *   </ul>
 * 调用 lsc-ai-gateway 的释放趋势预测能力，获取未来7-30天核销率预测，
 * 供每日释放任务参数调节与告警参考。
 * </p>
 */
@FeignClient(name = "lsc-ai-gateway",
             url  = "${ai.gateway-url:}",
             contextId = "aiGatewayClient")
public interface AiGatewayFeignClient {

    /**
     * 释放趋势预测
     *
     * @param request 预测请求(历史核销率序列)
     * @return 预测响应(7/30天核销率均值、趋势方向)
     */
    @PostMapping("/api/ai/release-predict")
    R<AiReleasePredictDTO.Response> releasePredict(@RequestBody AiReleasePredictDTO.Request request);

    /**
     * 参数仿真推演(透传 AI 网关, 使用 Map 避免跨模块 DTO 依赖)
     *
     * @param request 仿真请求(与 AiParamSimulationDTO.Request 对齐)
     * @return 仿真响应(与 AiParamSimulationDTO.Response 对齐)
     */
    @PostMapping("/api/ai/param-simulation")
    R<Map<String, Object>> paramSimulation(@RequestBody Map<String, Object> request);
}
