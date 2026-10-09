package com.zcls.lsc.api.controller;

import com.zcls.lsc.api.common.ApiResponse;
import com.zcls.lsc.risk.rule.AIService;
import com.zcls.lsc.risk.rule.RuleEngineService;
import com.zcls.lsc.risk.rule.RuleVersionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 第10.8章 风控规则引擎 REST 接口。
 * 规则管理（版本/激活/停用）+ 风险评估（预览/执行立案）。
 */
@RestController
@RequestMapping("/admin/risk-rules")
public class RiskRuleController {

    private final RuleVersionService ruleVersionService;
    private final RuleEngineService ruleEngineService;
    private final AIService aiService;

    public RiskRuleController(RuleVersionService ruleVersionService,
                              RuleEngineService ruleEngineService,
                              AIService aiService) {
        this.ruleVersionService = ruleVersionService;
        this.ruleEngineService = ruleEngineService;
        this.aiService = aiService;
    }

    /** 创建规则新版本。 */
    @PostMapping
    public ApiResponse<Long> createRule(@RequestBody Map<String, Object> body) {
        String ruleCode = (String) body.get("ruleCode");
        String name = (String) body.get("name");
        String description = (String) body.get("description");
        String conditionJson = (String) body.get("conditionJson");
        String matchMode = (String) body.getOrDefault("matchMode", "ALL");
        int score = toInt(body.get("score"), 10);
        int threshold = toInt(body.get("threshold"), 10);
        String action = (String) body.get("action");
        int priority = toInt(body.get("priority"), 100);
        long createdBy = toLong(body.get("createdBy"), 1L);

        long ruleId = ruleVersionService.createRule(
                ruleCode, name, description, conditionJson, matchMode,
                score, threshold, action, priority, createdBy);
        return ApiResponse.ok(ruleId);
    }

    /** 激活规则版本。 */
    @PostMapping("/{ruleId}/activate")
    public ApiResponse<Void> activateRule(@PathVariable long ruleId) {
        ruleVersionService.activateRule(ruleId);
        return ApiResponse.ok(null);
    }

    /** 停用规则。 */
    @PostMapping("/{ruleId}/disable")
    public ApiResponse<Void> disableRule(@PathVariable long ruleId) {
        ruleVersionService.disableRule(ruleId);
        return ApiResponse.ok(null);
    }

    /** 查询所有生效规则。 */
    @GetMapping("/active")
    public ApiResponse<List<Map<String, Object>>> listActiveRules() {
        return ApiResponse.ok(ruleVersionService.listActiveRules());
    }

    /**
     * 风险评估（仅预览，不立案）。
     */
    @PostMapping("/evaluate")
    public ApiResponse<Map<String, Object>> evaluate(@RequestBody Map<String, Object> body) {
        long userId = toLong(body.get("userId"), 0L);
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", Map.of());
        boolean useAI = Boolean.TRUE.equals(body.get("useAI"));

        RuleEngineService.EvaluationResult result = ruleEngineService.evaluateOnly(userId, context, useAI);
        return ApiResponse.ok(Map.of(
                "matchedCount", result.matchedCount(),
                "totalScore", result.totalScore(),
                "triggered", result.triggered()
        ));
    }

    /**
     * 风险评估并执行立案。
     */
    @PostMapping("/enforce")
    public ApiResponse<Map<String, Object>> enforce(@RequestBody Map<String, Object> body) {
        long userId = toLong(body.get("userId"), 0L);
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", Map.of());
        boolean useAI = Boolean.TRUE.equals(body.get("useAI"));

        RuleEngineService.EvaluationResult result = ruleEngineService.evaluateAndEnforce(userId, context, useAI);
        Map<String, Object> resp = new java.util.HashMap<>();
        resp.put("matchedCount", result.matchedCount());
        resp.put("totalScore", result.totalScore());
        resp.put("triggered", result.triggered());
        if (result.caseId() != null) resp.put("caseId", result.caseId());
        return ApiResponse.ok(resp);
    }

    /** AI 模型风险评分预览。 */
    @PostMapping("/ai-score")
    public ApiResponse<Map<String, Object>> aiScore(@RequestBody Map<String, Object> body) {
        long userId = toLong(body.get("userId"), 0L);
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.getOrDefault("context", Map.of());
        AIService.AIResult r = aiService.evaluate(userId, context);
        return ApiResponse.ok(Map.of(
                "aiFlag", r.aiFlag(),
                "score", r.score(),
                "evidenceRef", r.evidenceRef() == null ? "" : r.evidenceRef()
        ));
    }

    private int toInt(Object o, int def) {
        return o instanceof Number ? ((Number) o).intValue() : def;
    }
    private long toLong(Object o, long def) {
        return o instanceof Number ? ((Number) o).longValue() : def;
    }
}
