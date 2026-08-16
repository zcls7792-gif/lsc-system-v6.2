package com.lianshengtong.aigateway.controller;

import com.lianshengtong.aigateway.dto.AiAddressVerifyDTO;
import com.lianshengtong.aigateway.dto.AiB2bVerifyDTO;
import com.lianshengtong.aigateway.dto.AiCustomerServiceDTO;
import com.lianshengtong.aigateway.dto.AiMerchantProfileDTO;
import com.lianshengtong.aigateway.dto.AiParamSimulationDTO;
import com.lianshengtong.aigateway.dto.AiProductReviewDTO;
import com.lianshengtong.aigateway.dto.AiRecommendDTO;
import com.lianshengtong.aigateway.dto.AiReleasePredictDTO;
import com.lianshengtong.aigateway.dto.AiRiskControlDTO;
import com.lianshengtong.aigateway.service.AiAddressVerifyService;
import com.lianshengtong.aigateway.service.AiB2bVerifyService;
import com.lianshengtong.aigateway.service.AiCircuitBreakerManager;
import com.lianshengtong.aigateway.service.AiCustomerServiceService;
import com.lianshengtong.aigateway.service.AiMerchantProfileService;
import com.lianshengtong.aigateway.service.AiParamSimulationService;
import com.lianshengtong.aigateway.service.AiProductReviewService;
import com.lianshengtong.aigateway.service.AiRecommendService;
import com.lianshengtong.aigateway.service.AiReleasePredictService;
import com.lianshengtong.aigateway.service.AiRiskControlService;
import com.lianshengtong.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI网关统一入口控制器
 * <p>
 * 所有上游业务服务通过本控制器调用AI能力，由网关统一处理超时、降级、缓存与熔断。
 * </p>
 */
@Tag(name = "AI网关", description = "AI模型服务统一入口")
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiGatewayController {

    private final AiProductReviewService productReviewService;
    private final AiB2bVerifyService b2bVerifyService;
    private final AiAddressVerifyService addressVerifyService;
    private final AiRiskControlService riskControlService;
    private final AiReleasePredictService releasePredictService;
    private final AiParamSimulationService paramSimulationService;
    private final AiMerchantProfileService merchantProfileService;
    private final AiRecommendService recommendService;
    private final AiCustomerServiceService customerServiceService;
    private final AiCircuitBreakerManager circuitBreakerManager;

    @Operation(summary = "商品AI审核(图片违规+视频多模态+文案敏感词)")
    @PostMapping("/product-review")
    public R<AiProductReviewDTO.Response> productReview(@Valid @RequestBody AiProductReviewDTO.Request request) {
        return R.ok(productReviewService.review(request));
    }

    @Operation(summary = "B2B贸易背景核验(OCR+合同匹配+画像异常)")
    @PostMapping("/b2b-verify")
    public R<AiB2bVerifyDTO.Response> b2bVerify(@Valid @RequestBody AiB2bVerifyDTO.Request request) {
        return R.ok(b2bVerifyService.verify(request));
    }

    @Operation(summary = "地址真实性核验(实景图+工商地址比对)")
    @PostMapping("/address-verify")
    public R<AiAddressVerifyDTO.Response> addressVerify(@Valid @RequestBody AiAddressVerifyDTO.Request request) {
        return R.ok(addressVerifyService.verify(request));
    }

    @Operation(summary = "动态风控评分(用户行为特征)")
    @PostMapping("/risk-control")
    public R<AiRiskControlDTO.Response> riskControl(@Valid @RequestBody AiRiskControlDTO.Request request) {
        return R.ok(riskControlService.score(request));
    }

    @Operation(summary = "LSC释放趋势预测(7-30天核销率预测)")
    @PostMapping("/release-predict")
    public R<AiReleasePredictDTO.Response> releasePredict(@Valid @RequestBody AiReleasePredictDTO.Request request) {
        return R.ok(releasePredictService.predict(request));
    }

    @Operation(summary = "参数调整仿真推演")
    @PostMapping("/param-simulation")
    public R<AiParamSimulationDTO.Response> paramSimulation(@Valid @RequestBody AiParamSimulationDTO.Request request) {
        return R.ok(paramSimulationService.simulate(request));
    }

    @Operation(summary = "商家画像构建")
    @PostMapping("/merchant-profile")
    public R<AiMerchantProfileDTO.Response> merchantProfile(@Valid @RequestBody AiMerchantProfileDTO.Request request) {
        return R.ok(merchantProfileService.buildProfile(request));
    }

    @Operation(summary = "商品个性化推荐")
    @PostMapping("/recommend")
    public R<AiRecommendDTO.Response> recommend(@Valid @RequestBody AiRecommendDTO.Request request) {
        return R.ok(recommendService.recommend(request));
    }

    @Operation(summary = "AI客服问答")
    @PostMapping("/customer-service")
    public R<AiCustomerServiceDTO.Response> customerService(@Valid @RequestBody AiCustomerServiceDTO.Request request) {
        return R.ok(customerServiceService.chat(request));
    }

    @Operation(summary = "管理员操作异常检测(由 admin-service 调用)")
    @PostMapping("/admin/monitor")
    public R<Integer> monitorAdminAction(@RequestParam("adminId") Long adminId,
                                          @RequestParam("module") String module,
                                          @RequestParam("action") String action,
                                          @RequestParam("detail") String detail) {
        // 复用风控评分能力：将管理员操作映射为行为特征送入风控模型
        Map<String, BigDecimal> features = new LinkedHashMap<>();
        features.put("module_" + module, BigDecimal.ONE);
        features.put("action_" + action, BigDecimal.ONE);
        AiRiskControlDTO.Request req = AiRiskControlDTO.Request.builder()
                .userId(adminId)
                .userType(3) // 3=管理员
                .behaviorFeatures(features)
                .anomalyTags(Collections.emptyList())
                .deviceFingerprint(null)
                .build();
        AiRiskControlDTO.Response resp;
        try {
            resp = riskControlService.score(req);
        } catch (RuntimeException e) {
            // 降级：异常时返回 0(最低风险=放行)，与"高分=危险"契约一致。
            // 调用方按风险阈值判定，0 不会被判定为高风险，避免误拦截管理员正常操作。
            log.warn("[monitorAdminAction] AI 风控评分异常 adminId={} module={} action={}",
                    adminId, module, action, e);
            return R.ok(0);
        }
        if (resp == null || resp.getRiskScore() == null) {
            // 风控无评分返回时同样降级为 0(放行)
            return R.ok(0);
        }
        return R.ok(resp.getRiskScore().intValue());
    }

    @Operation(summary = "客服快捷问题列表(预置问题)")
    @GetMapping("/quick-questions")
    public R<List<String>> quickQuestions() {
        // 预置常见快捷问题，前端展示为可点击入口
        List<String> questions = List.of(
                "什么是 LSC 消费权益凭证？",
                "如何获得 LSC？",
                "LSC 怎么消费使用？",
                "商家如何核销 LSC？",
                "LSC 释放规则是什么？",
                "如何联系商家客服？",
                "退款后 LSC 如何返还？",
                "如何成为推荐人赚取奖励？"
        );
        return R.ok(questions);
    }

    @Operation(summary = "健康检查(含各AI能力熔断状态)")
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", "UP");
        Map<String, String> breakers = new LinkedHashMap<>();
        breakers.put("product-review", circuitBreakerManager.getState("product-review"));
        breakers.put("b2b-verify", circuitBreakerManager.getState("b2b-verify"));
        breakers.put("address-verify", circuitBreakerManager.getState("address-verify"));
        breakers.put("risk-control", circuitBreakerManager.getState("risk-control"));
        breakers.put("release-predict", circuitBreakerManager.getState("release-predict"));
        breakers.put("param-simulation", circuitBreakerManager.getState("param-simulation"));
        breakers.put("merchant-profile", circuitBreakerManager.getState("merchant-profile"));
        breakers.put("recommend", circuitBreakerManager.getState("recommend"));
        breakers.put("customer-service", circuitBreakerManager.getState("customer-service"));
        status.put("circuitBreakers", breakers);
        return R.ok(status);
    }

    @Operation(summary = "AI能力监控指标(调用次数/成功/失败/降级/平均延迟/熔断状态)")
    @GetMapping("/metrics")
    public R<Map<String, Object>> metrics() {
        return R.ok(circuitBreakerManager.metrics());
    }

    @Operation(summary = "指定AI能力监控指标")
    @GetMapping("/metrics/{capability}")
    public R<Map<String, Object>> metricsByCapability(@PathVariable("capability") String capability) {
        Map<String, Object> all = circuitBreakerManager.metrics();
        Object m = all.get(capability);
        if (m == null) {
            return R.fail("capability not found: " + capability);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) m;
        return R.ok(result);
    }

    @Operation(summary = "重置指定AI能力熔断状态(人工运维介入，HALF_OPEN/OPEN -> CLOSED)")
    @DeleteMapping("/reset/{capability}")
    public R<Map<String, Object>> resetBreaker(@PathVariable("capability") String capability) {
        boolean ok = circuitBreakerManager.reset(capability);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("capability", capability);
        data.put("reset", ok);
        data.put("state", circuitBreakerManager.getState(capability));
        return ok ? R.ok(data) : R.fail("capability not found: " + capability);
    }

    @Operation(summary = "AI能力清单(供前端/运维枚举)")
    @GetMapping("/capabilities")
    public R<List<String>> capabilities() {
        // 与 circuitBreakerManager.metrics() 的 key 保持一致
        return R.ok(List.of(
                "product-review",
                "b2b-verify",
                "address-verify",
                "risk-control",
                "release-predict",
                "param-simulation",
                "merchant-profile",
                "recommend",
                "customer-service"
        ));
    }
}
