package com.zcls.lsc.risk.rule;

import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AI 风控模型集成（抽象占位）。
 *
 * 实际部署时接入具体 AI 推理服务（HTTP/gRPC），此处提供同步调用接口与降级策略：
 *  - 当 AI 服务不可用时返回 aiFlag=false，由规则引擎兜底
 *  - AI 评分范围 [0, 1]，可叠加到规则总分
 */
@Service
public class AIService {

    /** AI 高风险阈值。 */
    public static final double AI_HIGH_RISK_THRESHOLD = 0.7;

    /**
     * 调用 AI 模型对用户行为进行风险评分。
     *
     * @param userId  用户ID
     * @param context 行为特征上下文
     * @return AI 评估结果（aiFlag + score + evidenceRef）
     */
    public AIResult evaluate(long userId, Map<String, Object> context) {
        // TODO: 替换为实际 AI 推理服务调用
        // 此处为占位实现：根据 context 中的 ai_risk_score 字段直接返回
        Object scoreObj = context.get("ai_risk_score");
        double score = scoreObj instanceof Number ? ((Number) scoreObj).doubleValue() : 0.0;
        boolean aiFlag = score >= AI_HIGH_RISK_THRESHOLD;
        String evidenceRef = aiFlag ? "ai_model_score=" + score : null;
        return new AIResult(aiFlag, score, evidenceRef);
    }

    /** AI 评估结果。 */
    public record AIResult(boolean aiFlag, double score, String evidenceRef) {}
}
