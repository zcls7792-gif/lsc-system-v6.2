package com.lianshengtong.aigateway.service;

import com.lianshengtong.aigateway.dto.AiRiskControlDTO;
import com.lianshengtong.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 本地规则引擎
 * <p>
 * 当 AI 模型不可用时（熔断/超时），使用本地规则引擎快速返回保守评分。
 * 规则按优先级匹配，命中规则即返回对应评分和等级。
 * 目标 P99 < 100ms。
 * </p>
 */
@Slf4j
@Component
public class LocalRuleEngine {

    private final List<Rule> rules = new ArrayList<>();
    private final Map<String, AtomicInteger> ruleHitCount = new ConcurrentHashMap<>();

    public LocalRuleEngine() {
        initDefaultRules();
    }

    private void initDefaultRules() {
        // Rule 1: 高频小额下单（正常）
        rules.add(new Rule("FREQ_LOW", 0, 20, "LOW", BigDecimal.valueOf(85),
                r -> {
                    BigDecimal orderFreq = r.getBehaviorFeatures() != null ? r.getBehaviorFeatures().get("orderFrequency") : null;
                    return orderFreq != null && orderFreq.compareTo(new BigDecimal("10")) < 0;
                }));

        // Rule 2: 中频下单（中等风险）
        rules.add(new Rule("FREQ_MID", 20, 50, "MEDIUM", BigDecimal.valueOf(65),
                r -> {
                    BigDecimal orderFreq = r.getBehaviorFeatures() != null ? r.getBehaviorFeatures().get("orderFrequency") : null;
                    return orderFreq != null
                            && orderFreq.compareTo(new BigDecimal("10")) >= 0
                            && orderFreq.compareTo(new BigDecimal("50")) < 0;
                }));

        // Rule 3: 高频下单（高风险）
        rules.add(new Rule("FREQ_HIGH", 50, 100, "HIGH", BigDecimal.valueOf(35),
                r -> {
                    BigDecimal orderFreq = r.getBehaviorFeatures() != null ? r.getBehaviorFeatures().get("orderFrequency") : null;
                    return orderFreq != null && orderFreq.compareTo(new BigDecimal("50")) >= 0;
                }));

        // Rule 4: 异常标签命中（直接高风险）
        rules.add(new Rule("ANOMALY_TAG", 100, 100, "HIGH", BigDecimal.valueOf(20),
                r -> r.getAnomalyTags() != null && !r.getAnomalyTags().isEmpty()));

        // Rule 5: 新用户 + 异常设备指纹（高风险）
        rules.add(new Rule("NEW_USER_ANOMALY", 80, 100, "HIGH", BigDecimal.valueOf(30),
                r -> {
                    if (r.getUserType() != null && r.getUserType() == 1) {
                        return r.getDeviceFingerprint() != null && r.getDeviceFingerprint().length() < 10;
                    }
                    return false;
                }));

        // Rule 6: 商家高频核销（中等风险）
        rules.add(new Rule("MERCHANT_HIGH_WRITEON", 40, 60, "MEDIUM", BigDecimal.valueOf(55),
                r -> {
                    BigDecimal writeoffFreq = r.getBehaviorFeatures() != null ? r.getBehaviorFeatures().get("writeoffFrequency") : null;
                    return writeoffFreq != null && writeoffFreq.compareTo(new BigDecimal("5")) > 0;
                }));

        // Rule 7: 默认保守评分
        rules.add(new Rule("DEFAULT", 30, 40, "MEDIUM", BigDecimal.valueOf(50), r -> true));
    }

    /**
     * 执行本地规则评估
     */
    public AiRiskControlDTO.Response evaluate(AiRiskControlDTO.Request request) {
        List<String> hitRules = new ArrayList<>();
        BigDecimal riskScore = BigDecimal.valueOf(50);
        String riskLevel = "MEDIUM";
        boolean blocked = false;

        for (Rule rule : rules) {
            if (rule.predicate.test(request)) {
                hitRules.add(rule.name);
                ruleHitCount.computeIfAbsent(rule.name, k -> new AtomicInteger(0)).incrementAndGet();
                riskScore = rule.score;
                riskLevel = rule.level;
                blocked = "HIGH".equals(riskLevel);
                break;
            }
        }

        return AiRiskControlDTO.Response.builder()
                .riskScore(riskScore)
                .riskLevel(riskLevel)
                .blocked(blocked)
                .hitRules(hitRules)
                .dimensionScores(request.getBehaviorFeatures())
                .fallback(true)
                .message("本地规则引擎评估: 命中规则=" + String.join(",", hitRules))
                .build();
    }

    /**
     * 获取规则命中率统计
     */
    public Map<String, Integer> getRuleHitStats() {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Rule rule : rules) {
            stats.put(rule.name, ruleHitCount.getOrDefault(rule.name, new AtomicInteger(0)).get());
        }
        return stats;
    }

    /**
     * 重置规则命中计数
     */
    public void resetStats() {
        ruleHitCount.clear();
    }

    /**
     * 添加自定义规则（运维热更新）
     */
    public void addRule(String name, String level, BigDecimal score, java.util.function.Predicate<AiRiskControlDTO.Request> predicate) {
        rules.add(0, new Rule(name, 0, 100, level, score, predicate));
        log.info("新增本地规则: name={} level={} score={}", name, level, score);
    }

    private static class Rule {
        final String name;
        final int minScore;
        final int maxScore;
        final String level;
        final BigDecimal score;
        final java.util.function.Predicate<AiRiskControlDTO.Request> predicate;

        Rule(String name, int minScore, int maxScore, String level, BigDecimal score,
             java.util.function.Predicate<AiRiskControlDTO.Request> predicate) {
            this.name = name;
            this.minScore = minScore;
            this.maxScore = maxScore;
            this.level = level;
            this.score = score;
            this.predicate = predicate;
        }
    }
}