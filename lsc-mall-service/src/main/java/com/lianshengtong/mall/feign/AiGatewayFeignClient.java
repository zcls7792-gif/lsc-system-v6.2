package com.lianshengtong.mall.feign;

import com.lianshengtong.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * AI网关 Feign 客户端 - 商品AI审核
 * <p>商品发布后异步提交AI审核，审核结果通过回调更新商品 ai_review 字段。</p>
 */
@FeignClient(name = "lsc-ai-gateway", contextId = "mallAiGatewayClient")
public interface AiGatewayFeignClient {

    /**
     * 提交商品AI审核任务
     *
     * @param productId 商品ID
     * @param name      商品名称
     * @param desc      商品描述
     * @param imageUrl  主图URL
     * @return 任务ID
     */
    @PostMapping("/api/ai/product/review")
    R<String> submitProductReview(@RequestParam("productId") Long productId,
                                  @RequestParam("name") String name,
                                  @RequestParam(value = "desc", required = false) String desc,
                                  @RequestParam(value = "imageUrl", required = false) String imageUrl);
}
