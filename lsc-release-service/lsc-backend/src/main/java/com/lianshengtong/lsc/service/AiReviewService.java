package com.lianshengtong.lsc.service;

import com.lianshengtong.lsc.common.BusinessException;
import com.lianshengtong.lsc.common.ErrorCode;
import com.lianshengtong.lsc.entity.B2bOrder;
import com.lianshengtong.lsc.entity.Product;
import com.lianshengtong.lsc.mapper.B2bOrderMapper;
import com.lianshengtong.lsc.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * AI网关审核服务（方案文档第十二章）
 * 提供商品图文审核、商品视频审核、B2B贸易背景核验三类AI能力
 *
 * 审核结果枚举：
 *   商品AI审核(ai_review_result): 0=AI通过, 1=AI可疑, 2=人工通过, 3=人工拒绝
 *   视频审核(video_status): 0=待审核, 1=审核通过, 2=审核拒绝
 *   B2B核验(ai_verification_result): 0=AI判定真实, 1=AI判定可疑, 2=人工确认真实, 3=人工确认虚假
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiReviewService {

    private final ProductMapper productMapper;
    private final B2bOrderMapper b2bOrderMapper;

    /** AI网关是否启用（默认启用模拟模式，接入真实API后置为true并配置endpoint） */
    @Value("${ai.gateway.enabled:false}")
    private boolean aiEnabled;

    /** AI网关API地址 */
    @Value("${ai.gateway.endpoint:}")
    private String aiEndpoint;

    /** 敏感词库（可扩展） */
    private static final Set<String> SENSITIVE_WORDS = Set.of(
            "违禁", "假货", "高仿", "走私", "赌博", "色情", "毒品", "枪支", "弹药",
            "fake", "counterfeit", "smuggle", "gambling", "porn", "drug", "gun"
    );

    // ===================== 商品图文审核 =====================

    /**
     * AI审核商品图文信息
     * @param productId 商品ID
     * @return 审核结果
     */
    public Map<String, Object> reviewProduct(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 拼接待审核文本
        String text = (product.getProductName() == null ? "" : product.getProductName())
                + " " + (product.getProductDesc() == null ? "" : product.getProductDesc());
        String images = product.getProductImages();

        // 调用AI审核
        AiReviewResult result = callAiTextReview(text, images);

        // 更新商品审核结果
        product.setAiReviewResult(result.passed ? 0 : 1);
        product.setAiReviewTags(result.tags);
        productMapper.updateById(product);

        log.info("[AI商品审核] productId={}, passed={}, score={}, tags={}",
                productId, result.passed, result.score, result.tags);

        return buildProductResult(productId, result);
    }

    // ===================== 商品视频审核 =====================

    /**
     * AI审核商品视频
     * @param productId 商品ID
     * @return 审核结果
     */
    public Map<String, Object> reviewVideo(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (product.getVideoUrl() == null || product.getVideoUrl().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST.getCode(), "商品无视频内容");
        }

        // 调用AI视频审核
        AiReviewResult result = callAiVideoReview(product.getVideoUrl(), product.getVideoCoverUrl());

        // 更新视频审核状态
        product.setVideoStatus(result.passed ? 1 : 2);
        if (!result.passed) {
            product.setVideoRejectReason(result.reason);
        }
        productMapper.updateById(product);

        log.info("[AI视频审核] productId={}, passed={}, score={}, reason={}",
                productId, result.passed, result.score, result.reason);

        return buildVideoResult(productId, result);
    }

    // ===================== B2B贸易背景核验 =====================

    /**
     * AI核验B2B贸易背景真实性
     * @param orderNo B2B订单号
     * @return 核验结果
     */
    public Map<String, Object> reviewB2B(String orderNo) {
        B2bOrder order = b2bOrderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<B2bOrder>()
                        .eq(B2bOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 拼接贸易背景信息
        String tradeContext = (order.getTradeDescription() == null ? "" : order.getTradeDescription())
                + " 合同号:" + (order.getContractNo() == null ? "" : order.getContractNo())
                + " 交易金额:" + (order.getTotalAmountRmb() == null ? "" : order.getTotalAmountRmb())
                + " LSC金额:" + (order.getLscAmount() == null ? "" : order.getLscAmount());

        // 调用AI贸易背景核验
        AiReviewResult result = callAiB2BReview(tradeContext, order.getTradeEvidenceUrls());

        // 更新B2B核验结果
        order.setAiVerificationResult(result.passed ? 0 : 1);
        order.setAiVerificationScore(result.score);
        b2bOrderMapper.updateById(order);

        log.info("[AI B2B核验] orderNo={}, passed={}, score={}", orderNo, result.passed, result.score);

        return buildB2BResult(orderNo, result);
    }

    // ===================== AI调用层（可替换为真实API） =====================

    /**
     * AI文本+图片审核
     * 当前为模拟实现，生产环境接入真实AI网关后替换
     */
    private AiReviewResult callAiTextReview(String text, String images) {
        if (aiEnabled && aiEndpoint != null && !aiEndpoint.isEmpty()) {
            return callRemoteAi(text, images, "text_image");
        }
        // 模拟审核：检测敏感词
        String lowerText = text.toLowerCase();
        List<String> hitWords = new ArrayList<>();
        for (String word : SENSITIVE_WORDS) {
            if (lowerText.contains(word.toLowerCase())) {
                hitWords.add(word);
            }
        }
        boolean passed = hitWords.isEmpty();
        BigDecimal score = passed
                ? BigDecimal.valueOf(90 + new Random().nextInt(10)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(20 + new Random().nextInt(30)).setScale(2, RoundingMode.HALF_UP);
        return new AiReviewResult(passed, score,
                passed ? "内容合规" : "检测到敏感词: " + String.join(",", hitWords),
                passed ? "合规" : "敏感词命中", null);
    }

    /**
     * AI视频审核
     */
    private AiReviewResult callAiVideoReview(String videoUrl, String coverUrl) {
        if (aiEnabled && aiEndpoint != null && !aiEndpoint.isEmpty()) {
            return callRemoteAi(videoUrl, coverUrl, "video");
        }
        // 模拟视频审核：90%通过率
        boolean passed = new Random().nextInt(100) < 90;
        BigDecimal score = passed
                ? BigDecimal.valueOf(85 + new Random().nextInt(15)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(15 + new Random().nextInt(30)).setScale(2, RoundingMode.HALF_UP);
        return new AiReviewResult(passed, score,
                passed ? "视频内容合规" : "视频包含违规内容",
                passed ? "合规" : "违规内容", null);
    }

    /**
     * AI B2B贸易背景核验
     */
    private AiReviewResult callAiB2BReview(String tradeContext, String evidenceUrls) {
        if (aiEnabled && aiEndpoint != null && !aiEndpoint.isEmpty()) {
            return callRemoteAi(tradeContext, evidenceUrls, "b2b_trade");
        }
        // 模拟B2B核验：根据贸易描述长度和证据材料判断
        boolean hasEvidence = evidenceUrls != null && !evidenceUrls.isEmpty();
        boolean hasContext = tradeContext.length() > 20;
        boolean passed = hasEvidence && hasContext;
        BigDecimal score;
        if (passed) {
            score = BigDecimal.valueOf(80 + new Random().nextInt(20)).setScale(2, RoundingMode.HALF_UP);
        } else if (hasEvidence || hasContext) {
            score = BigDecimal.valueOf(50 + new Random().nextInt(20)).setScale(2, RoundingMode.HALF_UP);
            passed = false;
        } else {
            score = BigDecimal.valueOf(10 + new Random().nextInt(20)).setScale(2, RoundingMode.HALF_UP);
            passed = false;
        }
        return new AiReviewResult(passed, score,
                passed ? "贸易背景真实" : "贸易背景存疑，需人工复核",
                passed ? "真实" : "可疑", null);
    }

    /**
     * 调用远程AI网关（生产环境实现）
     */
    private AiReviewResult callRemoteAi(String content, String media, String type) {
        // TODO: 接入真实AI网关API
        // RestTemplate/WebClient调用 aiEndpoint，传入content/media/type
        // 解析返回的审核结果
        log.warn("[AI网关] 远程调用未实现，使用模拟结果. endpoint={}", aiEndpoint);
        // 降级为模拟
        return simulateFallback(type);
    }

    private AiReviewResult simulateFallback(String type) {
        boolean passed = new Random().nextInt(100) < 85;
        BigDecimal score = BigDecimal.valueOf(passed ? 85 : 25).setScale(2, RoundingMode.HALF_UP);
        return new AiReviewResult(passed, score, "模拟审核", passed ? "合规" : "可疑", null);
    }

    // ===================== 结果构建 =====================

    private Map<String, Object> buildProductResult(Long productId, AiReviewResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", productId);
        m.put("reviewType", "PRODUCT_TEXT_IMAGE");
        m.put("passed", r.passed);
        m.put("aiReviewResult", r.passed ? 0 : 1);
        m.put("score", r.score);
        m.put("tags", r.tags);
        m.put("reason", r.reason);
        return m;
    }

    private Map<String, Object> buildVideoResult(Long productId, AiReviewResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", productId);
        m.put("reviewType", "PRODUCT_VIDEO");
        m.put("passed", r.passed);
        m.put("videoStatus", r.passed ? 1 : 2);
        m.put("score", r.score);
        m.put("reason", r.reason);
        return m;
    }

    private Map<String, Object> buildB2BResult(String orderNo, AiReviewResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("orderNo", orderNo);
        m.put("reviewType", "B2B_TRADE_BACKGROUND");
        m.put("passed", r.passed);
        m.put("aiVerificationResult", r.passed ? 0 : 1);
        m.put("score", r.score);
        m.put("reason", r.reason);
        return m;
    }

    // ===================== 内部结果类 =====================

    private static class AiReviewResult {
        final boolean passed;
        final BigDecimal score;
        final String reason;
        final String tags;
        final Map<String, Object> extra;

        AiReviewResult(boolean passed, BigDecimal score, String reason, String tags, Map<String, Object> extra) {
            this.passed = passed;
            this.score = score;
            this.reason = reason;
            this.tags = tags;
            this.extra = extra;
        }
    }
}
