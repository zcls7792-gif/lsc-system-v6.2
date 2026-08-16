package com.lianshengtong.b2b.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * AI 网关 Feign 客户端
 * <p>调用 lsc-ai-gateway 对 B2B 贸易真实性进行核验。</p>
 */
@FeignClient(name = "lsc-ai-gateway", contextId = "b2bAiGatewayClient")
public interface AiGatewayFeignClient {

    /**
     * B2B 贸易真实性核验
     * <p>AI 根据交易描述、凭证等判定贸易真实性，返回核验结果与评分。</p>
     *
     * @param orderNo            订单号
     * @param tradeDescription   交易描述
     * @param tradeEvidenceUrls  贸易凭证图片(JSON数组)
     * @return 核验结果: result(0未核验 1AI真实 2AI可疑 3人工真实 4人工虚假), score(0-100), riskTags
     */
    @PostMapping("/api/ai/b2b-verify")
    R<Map<String, Object>> b2bVerify(@RequestParam("orderNo") String orderNo,
                                     @RequestParam("tradeDescription") String tradeDescription,
                                     @RequestParam(value = "tradeEvidenceUrls", required = false) String tradeEvidenceUrls);
}
