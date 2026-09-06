package com.lianshengtong.lsc.controller;

import com.lianshengtong.lsc.common.R;
import com.lianshengtong.lsc.service.AiReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * AI网关审核控制器（方案文档第十二章）
 * 提供商品图文审核、商品视频审核、B2B贸易背景核验接口
 *
 * 路径前缀: /api/v1/ai
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiReviewService aiReviewService;

    /**
     * AI审核商品图文信息
     * POST /api/v1/ai/review/product
     * 请求体: {"productId": 1}
     */
    @PostMapping("/review/product")
    public R<Map<String, Object>> reviewProduct(@RequestBody Map<String, Object> body) {
        Long productId = Long.valueOf(body.get("productId").toString());
        log.info("[AI审核] 商品图文审核请求, productId={}", productId);
        return R.ok(aiReviewService.reviewProduct(productId));
    }

    /**
     * AI审核商品视频
     * POST /api/v1/ai/review/video
     * 请求体: {"productId": 1}
     */
    @PostMapping("/review/video")
    public R<Map<String, Object>> reviewVideo(@RequestBody Map<String, Object> body) {
        Long productId = Long.valueOf(body.get("productId").toString());
        log.info("[AI审核] 商品视频审核请求, productId={}", productId);
        return R.ok(aiReviewService.reviewVideo(productId));
    }

    /**
     * AI核验B2B贸易背景真实性
     * POST /api/v1/ai/review/b2b
     * 请求体: {"orderNo": "B2B1234567890"}
     */
    @PostMapping("/review/b2b")
    public R<Map<String, Object>> reviewB2B(@RequestBody Map<String, Object> body) {
        String orderNo = body.get("orderNo").toString();
        log.info("[AI审核] B2B贸易背景核验请求, orderNo={}", orderNo);
        return R.ok(aiReviewService.reviewB2B(orderNo));
    }
}
