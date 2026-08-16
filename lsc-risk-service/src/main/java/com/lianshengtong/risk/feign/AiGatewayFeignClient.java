package com.lianshengtong.risk.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AI网关 Feign 客户端 - 动态风控评分
 */
@FeignClient(name = "lsc-ai-gateway", contextId = "riskAiGatewayClient")
public interface AiGatewayFeignClient {

    /**
     * 动态风控评分
     *
     * @param userId    用户ID
     * @param behavior  行为特征(JSON)
     * @return 风险评分(0~100, 越高越危险)
     */
    @PostMapping("/api/ai/risk/score")
    R<Integer> riskScore(@RequestParam("userId") Long userId,
                         @RequestParam("behavior") String behavior);
}
